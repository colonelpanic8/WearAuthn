package me.henneke.wearauthn.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.provider.Settings
import android.support.wearable.complications.ComplicationProviderService
import android.support.wearable.complications.ProviderUpdateRequester
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Dialog
import androidx.wear.compose.material3.OpenOnPhoneDialog
import androidx.wear.compose.material3.OpenOnPhoneDialogDefaults
import androidx.wear.compose.material3.openOnPhoneDialogCurvedText
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.henneke.wearauthn.BuildConfig
import me.henneke.wearauthn.Logging
import me.henneke.wearauthn.R
import me.henneke.wearauthn.bthid.HidDataSender
import me.henneke.wearauthn.complication.ShortcutComplicationProviderService
import me.henneke.wearauthn.fido.context.AuthenticatorContext
import me.henneke.wearauthn.fido.context.checkAllKeysInHardware
import me.henneke.wearauthn.i
import me.henneke.wearauthn.service.AuthenticatorService
import me.henneke.wearauthn.sync.UnlockComplicationListenerService
import me.henneke.wearauthn.ui.ConfirmDeviceCredentialActivity
import me.henneke.wearauthn.ui.bluetoothAdapter
import me.henneke.wearauthn.ui.hasBluetoothPermissions
import me.henneke.wearauthn.ui.openPhoneAppOrListing
import me.henneke.wearauthn.ui.showToast
import me.henneke.wearauthn.ui.theme.WearAuthnTheme

@ExperimentalUnsignedTypes
class AuthenticatorActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels()

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (hasBluetoothPermissions) {
                viewModel.refresh()
                AuthenticatorService.arm(this)
            } else {
                showToast(getString(R.string.status_bluetooth_permissions_required))
            }
        }

    private val enableBluetooth =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            viewModel.refresh()
        }

    private val makeDiscoverable =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            viewModel.onDiscoverableResult(result.resultCode)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(viewModel)
        if (savedInstanceState == null)
            attachFromShortcut(intent)
        setContent {
            WearAuthnTheme {
                AppScaffold { AuthenticatorNavHost() }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        attachFromShortcut(intent)
    }

    override fun onResume() {
        super.onResume()
        AuthenticatorService.arm(this)
        lifecycleScope.launch(Dispatchers.IO) {
            AuthenticatorContext.initAuthenticator(applicationContext)
            AuthenticatorContext.refreshCachedWebAuthnCredentialIfNecessary(applicationContext)
        }
    }

    /** Attaches to the device of a watch face shortcut, once per tap. */
    @SuppressLint("MissingPermission")
    private fun attachFromShortcut(intent: Intent) {
        if (!intent.hasExtra(EXTRA_DEVICE))
            return
        if (intent.hasExtra(ComplicationProviderService.EXTRA_COMPLICATION_ID)) {
            ProviderUpdateRequester(
                this,
                ComponentName(this, ShortcutComplicationProviderService::class.java)
            ).requestUpdateAll()
        }
        val device = intent.getParcelableExtra<BluetoothDevice>(EXTRA_DEVICE)
        intent.removeExtra(EXTRA_DEVICE)
        val bonded = if (hasBluetoothPermissions) bluetoothAdapter?.bondedDevices.orEmpty() else emptySet()
        if (device == null || device !in bonded) {
            i { "Shortcut device is no longer bonded" }
            return
        }
        HidDataSender.requestConnect(device)
    }

    private fun requestBluetoothPermissions() {
        requestPermissions.launch(
            listOfNotNull(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.POST_NOTIFICATIONS.takeIf {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                },
            ).toTypedArray()
        )
    }

    private fun startIfPresent(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            i { "No activity for ${intent.action}" }
        }
    }

    private fun confirmDeviceCredential(onConfirmed: () -> Unit) {
        ConfirmDeviceCredentialActivity.start(
            this,
            object : ResultReceiver(Handler(Looper.getMainLooper())) {
                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                    if (resultCode == Activity.RESULT_OK)
                        onConfirmed()
                }
            })
    }

    private fun readRaw(id: Int): String =
        resources.openRawResource(id).bufferedReader().use { it.readText() }

    @Composable
    private fun AuthenticatorNavHost() {
        val navController = rememberSwipeDismissableNavController()
        val home by viewModel.home.collectAsState()
        val settings by viewModel.settings.collectAsState()
        SwipeDismissableNavHost(navController = navController, startDestination = ROUTE_HOME) {
            composable(ROUTE_HOME) {
                HomeScreen(
                    state = home,
                    watchName = remember(home.bluetooth) { viewModel.watchName },
                    onAllowBluetooth = ::requestBluetoothPermissions,
                    onTurnOnBluetooth = {
                        enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                    },
                    onHostClick = viewModel::toggle,
                    onPair = {
                        makeDiscoverable.launch(
                            Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(
                                BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,
                                HomeViewModel.DISCOVERABLE_SECONDS
                            )
                        )
                    },
                    onOpenNfcSettings = { startIfPresent(Intent(Settings.ACTION_NFC_SETTINGS)) },
                    onOpenPasskeys = {
                        when (home.passkeys) {
                            is PasskeysState.Active, is PasskeysState.NeedsReset ->
                                confirmDeviceCredential { navController.navigate(ROUTE_PASSKEYS) }
                            else -> navController.navigate(ROUTE_PASSKEYS)
                        }
                    },
                    onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                )
            }
            composable(ROUTE_SETTINGS) {
                SettingsScreen(
                    state = settings,
                    onOpenSessionTimeout = { navController.navigate(ROUTE_SESSION_TIMEOUT) },
                    onOnlyWhileWornChange = viewModel::setOnlyWhileWorn,
                    onPhoneAlwaysReadyChange = viewModel::setPhoneAlwaysReady,
                    onOpenLogLevel = { navController.navigate(ROUTE_LOG_LEVEL) },
                    onOpenAbout = { navController.navigate(ROUTE_ABOUT) },
                )
            }
            composable(ROUTE_SESSION_TIMEOUT) {
                SessionTimeoutPickerScreen(settings.sessionTimeout) {
                    viewModel.setSessionTimeout(it)
                    navController.popBackStack()
                }
            }
            composable(ROUTE_LOG_LEVEL) {
                settings.logLevel?.let { level ->
                    LogLevelPickerScreen(level) {
                        viewModel.setLogLevel(it)
                        navController.popBackStack()
                    }
                }
            }
            composable(ROUTE_ABOUT) { AboutRoute(navigate = navController::navigate) }
            composable(ROUTE_PRIVACY) {
                TextScreen(stringResource(R.string.label_privacy), readRaw(R.raw.privacy_policy))
            }
            composable(ROUTE_LICENSES) {
                TextScreen(stringResource(R.string.label_licenses), readRaw(R.raw.licenses_text))
            }
            composable(ROUTE_PASSKEYS) { PasskeysRoute(home.passkeys) }
        }
    }

    @Composable
    private fun AboutRoute(navigate: (String) -> Unit) {
        val keyStorage by produceState(KeyStorage.Scanning) {
            value = withContext(Dispatchers.IO) {
                if (checkAllKeysInHardware()) KeyStorage.Hardware else KeyStorage.Software
            }
        }
        var openedOnPhone by remember { mutableStateOf(false) }
        AboutScreen(
            state = AboutState(
                version = BuildConfig.VERSION_NAME,
                keyStorage = keyStorage,
                supportPurchased = UnlockComplicationListenerService.isComplicationEnabled(this),
            ),
            onOpenPrivacyPolicy = { navigate(ROUTE_PRIVACY) },
            onOpenLicenses = { navigate(ROUTE_LICENSES) },
            onSupport = {
                lifecycleScope.launch {
                    openPhoneAppOrListing(this@AuthenticatorActivity)
                    openedOnPhone = true
                }
            },
        )
        val message = stringResource(R.string.message_continue_on_phone)
        val style = OpenOnPhoneDialogDefaults.curvedTextStyle
        OpenOnPhoneDialog(
            visible = openedOnPhone,
            onDismissRequest = { openedOnPhone = false },
            curvedText = { openOnPhoneDialogCurvedText(message, style) },
        )
    }

    @Composable
    private fun PasskeysRoute(state: PasskeysState) {
        val sites by viewModel.passkeys.collectAsState()
        var selected by remember { mutableStateOf<Pair<PasskeySite, PasskeyItem>?>(null) }
        var confirmingDelete by rememberSaveable { mutableStateOf(false) }
        var confirmingPasswordless by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(state) {
            if (state is PasskeysState.Active || state is PasskeysState.NeedsReset)
                viewModel.loadPasskeys()
        }
        PasskeysScreen(
            state = state,
            sites = sites,
            onTurnOn = { confirmingPasswordless = true },
            onPasskeyClick = { site, passkey -> selected = site to passkey },
        )
        val current = selected
        Dialog(
            visible = current != null && !confirmingDelete,
            onDismissRequest = { selected = null },
        ) {
            current?.let { (site, passkey) ->
                PasskeyDetailContent(site.site, passkey, onDelete = { confirmingDelete = true })
            }
        }
        Dialog(
            visible = current != null && confirmingDelete,
            onDismissRequest = { confirmingDelete = false },
        ) {
            current?.let { (site, passkey) ->
                PasskeyDeleteConfirmContent(
                    site.site,
                    passkey,
                    onConfirm = {
                        viewModel.deletePasskey(passkey)
                        confirmingDelete = false
                        selected = null
                    },
                    onDismiss = { confirmingDelete = false },
                )
            }
        }
        Dialog(
            visible = confirmingPasswordless,
            onDismissRequest = { confirmingPasswordless = false },
        ) {
            PasswordlessConfirmContent(
                onConfirm = {
                    confirmingPasswordless = false
                    confirmDeviceCredential { viewModel.turnOnPasswordless() }
                },
                onDismiss = { confirmingPasswordless = false },
            )
        }
    }

    companion object : Logging {
        override val TAG = "AuthenticatorActivity"

        const val EXTRA_DEVICE = "me.henneke.wearauthn.extra.DEVICE"

        private const val ROUTE_HOME = "home"
        private const val ROUTE_SETTINGS = "settings"
        private const val ROUTE_SESSION_TIMEOUT = "settings/session-timeout"
        private const val ROUTE_LOG_LEVEL = "settings/log-level"
        private const val ROUTE_ABOUT = "about"
        private const val ROUTE_PRIVACY = "about/privacy"
        private const val ROUTE_LICENSES = "about/licenses"
        private const val ROUTE_PASSKEYS = "passkeys"
    }
}
