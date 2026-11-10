package me.henneke.wearauthn.ui.main

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcManager
import android.os.SystemClock
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import java.text.DateFormat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.henneke.wearauthn.LogLevel
import me.henneke.wearauthn.Logging
import me.henneke.wearauthn.R
import me.henneke.wearauthn.bthid.HidDataSender
import me.henneke.wearauthn.bthid.HidDeviceProfile
import me.henneke.wearauthn.bthid.canUseAuthenticatorViaBluetooth
import me.henneke.wearauthn.bthid.identifier
import me.henneke.wearauthn.fido.context.AuthenticatorContext
import me.henneke.wearauthn.fido.context.WebAuthnCredential
import me.henneke.wearauthn.fido.context.armUserVerificationFuse
import me.henneke.wearauthn.fido.context.getUserVerificationState
import me.henneke.wearauthn.isDeveloperModeEnabled
import me.henneke.wearauthn.service.AuthenticatorService
import me.henneke.wearauthn.service.SessionTimeout
import me.henneke.wearauthn.ui.bluetoothAdapter
import me.henneke.wearauthn.ui.defaultSharedPreferences
import me.henneke.wearauthn.ui.hasBluetoothPermissions

/**
 * Holds what the app's own screens show. Observes Bluetooth while the activity is started and
 * re-reads everything when it resumes, which also picks up NFC and screen lock changes made in
 * the system settings.
 */
@ExperimentalUnsignedTypes
class HomeViewModel(application: Application) :
    AndroidViewModel(application), DefaultLifecycleObserver {

    private val context: Context
        get() = getApplication()

    private var hidDeviceProfile: HidDeviceProfile? = null
    private var visibleUntil: Long? = null
    private var visibilityTicker: Job? = null
    private var started = false
    private var credentials: Map<Int, WebAuthnCredential> = emptyMap()

    private val _home = MutableStateFlow(readHome())
    val home: StateFlow<HomeState> = _home.asStateFlow()

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<SettingsState> = _settings.asStateFlow()

    private val _passkeys = MutableStateFlow<List<PasskeySite>>(emptyList())
    val passkeys: StateFlow<List<PasskeySite>> = _passkeys.asStateFlow()

    val watchName: String
        @SuppressLint("MissingPermission")
        get() = context.bluetoothAdapter?.takeIf { context.hasBluetoothPermissions }?.name.orEmpty()

    private val profileListener = object : HidDataSender.ProfileListener {
        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) = post()
        override fun onAppStatusChanged(registered: Boolean) = post()
        override fun onServiceStateChanged(proxy: BluetoothProfile?) = post()

        private fun post() {
            viewModelScope.launch { refreshBluetooth() }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refreshBluetooth()
    }

    override fun onStart(owner: LifecycleOwner) {
        started = true
        context.registerReceiver(receiver, IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_CLASS_CHANGED)
            addAction(BluetoothDevice.ACTION_NAME_CHANGED)
        })
        observeHidProfile()
    }

    override fun onResume(owner: LifecycleOwner) = refresh()

    override fun onStop(owner: LifecycleOwner) {
        started = false
        context.unregisterReceiver(receiver)
        if (hidDeviceProfile != null) {
            HidDataSender.removeListener(profileListener)
            hidDeviceProfile = null
        }
        visibilityTicker?.cancel()
    }

    fun refresh() {
        observeHidProfile()
        _home.value = readHome()
        _settings.value = readSettings()
        scheduleVisibilityTick()
    }

    /** [resultCode] of the discoverability request is the granted duration in seconds. */
    fun onDiscoverableResult(resultCode: Int) {
        if (resultCode > 0)
            visibleUntil = SystemClock.elapsedRealtime() + resultCode * 1000L
        refreshBluetooth()
    }

    @SuppressLint("MissingPermission")
    fun toggle(host: Host) {
        val device = context.bluetoothAdapter?.getRemoteDevice(host.address) ?: return
        HidDataSender.requestConnect(if (host.link == Link.Attached) null else device)
        refreshBluetooth()
    }

    fun setSessionTimeout(timeout: SessionTimeout) {
        context.defaultSharedPreferences.edit {
            putString(context.getString(R.string.preference_session_timeout_key), timeout.preferenceValue)
        }
        _settings.value = readSettings()
    }

    fun setOnlyWhileWorn(enabled: Boolean) {
        context.defaultSharedPreferences.edit {
            putBoolean(context.getString(R.string.preference_end_session_off_wrist_key), enabled)
        }
        _settings.value = readSettings()
    }

    fun setPhoneAlwaysReady(enabled: Boolean) {
        context.defaultSharedPreferences.edit(commit = true) {
            putBoolean(context.getString(R.string.preference_phone_always_ready_key), enabled)
        }
        AuthenticatorService.applyPhoneSetting(context)
        _settings.value = readSettings()
    }

    fun setLogLevel(level: LogLevel) {
        context.defaultSharedPreferences.edit {
            putString(context.getString(R.string.preference_log_level_key), level.name)
        }
        Logging.init(context, level.name)
        _settings.value = readSettings()
    }

    fun turnOnPasswordless() {
        armUserVerificationFuse(context)
        refresh()
    }

    /** Reads the passkeys with their account names, which needs a recent screen lock check. */
    fun loadPasskeys() {
        val sites = mutableListOf<PasskeySite>()
        val byId = mutableMapOf<Int, WebAuthnCredential>()
        val dateFormat = DateFormat.getDateInstance()
        for ((site, siteCredentials) in AuthenticatorContext.getAllResidentCredentials(context)) {
            if (siteCredentials.isEmpty())
                continue
            sites += PasskeySite(site, siteCredentials.mapIndexed { index, credential ->
                credential.unlockUserInfoIfNecessary()
                val (title, subtitle) = credential.getTwoLineInfo(index + 1)
                val id = byId.size
                byId[id] = credential
                PasskeyItem(
                    id = id,
                    title = title,
                    subtitle = subtitle,
                    displayName = credential.userDisplayName.takeUnless { it.isNullOrBlank() },
                    userName = credential.userName.takeUnless { it.isNullOrBlank() },
                    created = credential.creationDate?.let { dateFormat.format(it) },
                )
            })
        }
        credentials = byId
        _passkeys.value = sites
    }

    fun deletePasskey(passkey: PasskeyItem) {
        val credential = credentials[passkey.id] ?: return
        AuthenticatorContext.deleteResidentCredential(context, credential)
        loadPasskeys()
        refresh()
    }

    private fun observeHidProfile() {
        if (started && hidDeviceProfile == null && context.hasBluetoothPermissions &&
            context.bluetoothAdapter != null
        ) {
            hidDeviceProfile = HidDataSender.addListener(profileListener)
        }
    }

    private fun refreshBluetooth() {
        _home.value = _home.value.copy(bluetooth = readBluetooth())
        scheduleVisibilityTick()
    }

    private fun scheduleVisibilityTick() {
        val pairing = (_home.value.bluetooth as? BluetoothState.On)?.visibleSecondsLeft != null
        if (!started || !pairing || visibilityTicker?.isActive == true)
            return
        visibilityTicker = viewModelScope.launch {
            while (true) {
                delay(1_000)
                refreshBluetooth()
                if ((_home.value.bluetooth as? BluetoothState.On)?.visibleSecondsLeft == null)
                    break
            }
        }
    }

    private fun readHome() = HomeState(readBluetooth(), readNfc(), readPasskeys())

    @SuppressLint("MissingPermission")
    private fun readBluetooth(): BluetoothState {
        if (!context.hasBluetoothPermissions)
            return BluetoothState.NeedsPermission
        val adapter = context.bluetoothAdapter ?: return BluetoothState.Off
        return when (adapter.state) {
            BluetoothAdapter.STATE_ON -> BluetoothState.On(
                hosts = adapter.bondedDevices
                    .filter { it.canUseAuthenticatorViaBluetooth }
                    .map { device ->
                        Host(device.address, device.identifier, device.kind, linkOf(device))
                    }
                    .sortedBy { it.name.lowercase() },
                appRegistered = HidDataSender.isAppRegistered,
                visibleSecondsLeft = visibleSecondsLeft(adapter),
            )
            BluetoothAdapter.STATE_TURNING_ON -> BluetoothState.TurningOn
            else -> BluetoothState.Off
        }
    }

    @SuppressLint("MissingPermission")
    private fun visibleSecondsLeft(adapter: BluetoothAdapter): Int? {
        if (adapter.scanMode != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            visibleUntil = null
            return null
        }
        val now = SystemClock.elapsedRealtime()
        val until = visibleUntil ?: (now + DISCOVERABLE_SECONDS * 1000L).also { visibleUntil = it }
        return ((until - now + 999) / 1000).coerceAtLeast(0).toInt()
    }

    private fun linkOf(device: BluetoothDevice): Link =
        when (hidDeviceProfile?.getConnectionState(device)) {
            BluetoothProfile.STATE_CONNECTED -> Link.Attached
            BluetoothProfile.STATE_CONNECTING -> Link.Attaching
            BluetoothProfile.STATE_DISCONNECTING -> Link.Detaching
            else -> Link.Detached
        }

    private fun readNfc(): NfcState =
        when (context.getSystemService(NfcManager::class.java)?.defaultAdapter?.isEnabled) {
            true -> NfcState.On
            false -> NfcState.Off
            null -> NfcState.Absent
        }

    private fun readPasskeys(): PasskeysState {
        val count by lazy {
            AuthenticatorContext.getAllResidentCredentials(context).values.sumOf { it.size }
        }
        return when (getUserVerificationState(context)) {
            true -> PasskeysState.Active(count)
            null -> PasskeysState.NeedsReset(count)
            false ->
                if (AuthenticatorContext.isScreenLockEnabled(context)) PasskeysState.Available
                else PasskeysState.NeedsScreenLock
        }
    }

    private fun readSettings(): SettingsState {
        val prefs = context.defaultSharedPreferences
        return SettingsState(
            sessionTimeout = SessionTimeout.fromPreference(
                prefs.getString(context.getString(R.string.preference_session_timeout_key), null)
            ),
            onlyWhileWorn = prefs.getBoolean(
                context.getString(R.string.preference_end_session_off_wrist_key),
                context.resources.getBoolean(R.bool.default_end_session_off_wrist)
            ),
            phoneAlwaysReady = AuthenticatorService.isAlwaysReadyForPhone(context),
            logLevel = if (context.isDeveloperModeEnabled) {
                LogLevel.safeValueOf(
                    prefs.getString(
                        context.getString(R.string.preference_log_level_key),
                        LogLevel.Disabled.name
                    )!!
                )
            } else {
                null
            },
        )
    }

    companion object {
        const val DISCOVERABLE_SECONDS = 60
    }
}

internal val BluetoothDevice.kind: DeviceKind
    @SuppressLint("MissingPermission")
    get() = when (bluetoothClass?.majorDeviceClass) {
        BluetoothClass.Device.Major.COMPUTER -> DeviceKind.Computer
        BluetoothClass.Device.Major.PHONE -> DeviceKind.Phone
        BluetoothClass.Device.Major.WEARABLE -> DeviceKind.Watch
        BluetoothClass.Device.Major.AUDIO_VIDEO -> DeviceKind.Audio
        else -> DeviceKind.Other
    }
