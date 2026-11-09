package me.henneke.wearauthn.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.henneke.wearauthn.Logging
import me.henneke.wearauthn.R
import me.henneke.wearauthn.bthid.HidDataSender
import me.henneke.wearauthn.bthid.HidIntrDataListener
import me.henneke.wearauthn.bthid.InputHostWrapper
import me.henneke.wearauthn.fido.hid.TransactionManager
import me.henneke.wearauthn.fido.rfcomm.RfcommAuthenticator
import me.henneke.wearauthn.i
import me.henneke.wearauthn.ui.bluetoothAdapter
import me.henneke.wearauthn.ui.defaultSharedPreferences
import me.henneke.wearauthn.ui.hasBluetoothPermissions
import me.henneke.wearauthn.ui.main.AuthenticatorActivity
import me.henneke.wearauthn.ui.main.HidAuthenticatorContext
import kotlin.coroutines.CoroutineContext

/**
 * Serves requests in the background. While it runs, phones can connect over RFCOMM and
 * confirmation prompts show on top of whatever is on screen.
 *
 * Opening the app also arms a session for computers: the Bluetooth HID Device app is registered
 * and the last host is attached once. The session ends after the configured idle time or from
 * the notification. The service then stops too, unless it is set to stay ready for phones.
 * Listening for a phone costs no radio time, since the phone is connected anyway. Paging a
 * computer and holding a link to it does.
 *
 * Running as a foreground service is also what keeps the HID registration alive: the Bluetooth
 * stack unregisters HID Device apps whose UID drops below foreground-service importance.
 */
@ExperimentalUnsignedTypes
class AuthenticatorService : Service(), CoroutineScope {

    override val coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.Main

    private lateinit var authenticatorContext: HidAuthenticatorContext
    private var rfcommAuthenticator: RfcommAuthenticator? = null

    /** Non-null while a session for computers is armed. */
    @Volatile
    private var hidTransactions: TransactionManager? = null
    private var idleWatch: Job? = null
    private var lastStartId = 0

    @Volatile
    private var lastActivityAt = SystemClock.elapsedRealtime()

    private val ioListener = object : HidIntrDataListener {
        override fun onIntrData(
            device: BluetoothDevice,
            reportId: Byte,
            data: ByteArray,
            host: InputHostWrapper
        ) {
            recordActivity()
            hidTransactions?.handleReport(data) { reports ->
                for (report in reports)
                    host.sendReport(device, reportId.toInt(), report)
            }
        }
    }

    private val profileListener = object : HidDataSender.ProfileListener {
        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            if (state == BluetoothProfile.STATE_CONNECTED) {
                i { "HID connected to ${device.address}" }
                lastHostAddress = device.address
            }
        }

        override fun onAppStatusChanged(registered: Boolean) {}

        override fun onServiceStateChanged(proxy: BluetoothProfile?) {}
    }

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            if (state == BluetoothAdapter.STATE_ON) {
                rfcommAuthenticator?.start()
            } else {
                i { "Bluetooth is no longer on; ending session" }
                rfcommAuthenticator?.stop()
                disarm()
            }
        }
    }

    private var lastHostAddress: String?
        get() = defaultSharedPreferences.getString(PREF_LAST_HOST, null)
        set(value) = defaultSharedPreferences.edit().putString(PREF_LAST_HOST, value).apply()

    private val sessionTimeout: SessionTimeout
        get() = SessionTimeout.fromPreference(
            defaultSharedPreferences.getString(getString(R.string.preference_session_timeout_key), null)
        )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotification()
        authenticatorContext = HidAuthenticatorContext(this)
        HidDataSender.addListener(profileListener)
        // Registered before the first listen, so that Bluetooth coming up meanwhile is not missed.
        registerReceiver(
            bluetoothStateReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        )
        bluetoothAdapter?.let { adapter ->
            // Phones ask for one credential at a time and only talk to the watch.
            val rfcommContext = HidAuthenticatorContext(this, confirmsAbsentCredentials = false)
            rfcommAuthenticator = RfcommAuthenticator(adapter, rfcommContext, this)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        if (bluetoothAdapter?.isEnabled == true)
            rfcommAuthenticator?.start()
        when (intent?.action) {
            ACTION_ARM -> arm()
            ACTION_STOP -> {
                i { "Turned off from the notification" }
                disarm()
            }
            else -> if (hidTransactions == null) stopUnlessAlwaysReadyForPhone()
        }
        // Only the phone listener comes back by itself; a session needs the user.
        return if (isAlwaysReadyForPhone(this)) START_STICKY else START_NOT_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(bluetoothStateReceiver)
        HidDataSender.removeListener(profileListener)
        rfcommAuthenticator?.stop()
        HidDataSender.stop()
        hidTransactions?.close()
        cancel()
        super.onDestroy()
    }

    private fun arm() {
        recordActivity()
        if (hidTransactions == null) {
            hidTransactions = TransactionManager(authenticatorContext)
            HidDataSender.start(this, ioListener)
            idleWatch = watchIdleTime()
            showNotification()
        }
        if (!HidDataSender.hasTarget)
            connectToLastHost()
    }

    private fun disarm() {
        idleWatch?.cancel()
        idleWatch = null
        HidDataSender.stop()
        hidTransactions?.close()
        hidTransactions = null
        stopUnlessAlwaysReadyForPhone()
    }

    private fun stopUnlessAlwaysReadyForPhone() {
        if (isAlwaysReadyForPhone(this))
            showNotification()
        else
            // Leaves the service running if the app was opened again in the meantime.
            stopSelfResult(lastStartId)
    }

    private fun recordActivity() {
        lastActivityAt = SystemClock.elapsedRealtime()
    }

    // delay() runs on uptime, which stops while the watch sleeps, so compare against
    // elapsedRealtime() and wake at least once a minute of uptime to notice.
    private fun watchIdleTime() = launch {
        while (true) {
            val timeoutMs = sessionTimeout.durationMs
            val remainingMs =
                if (timeoutMs == null) MAX_IDLE_CHECK_INTERVAL_MS
                else lastActivityAt + timeoutMs - SystemClock.elapsedRealtime()
            if (remainingMs <= 0) {
                i { "Idle for ${timeoutMs}ms; ending session" }
                disarm()
                return@launch
            }
            delay(remainingMs.coerceAtMost(MAX_IDLE_CHECK_INTERVAL_MS))
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToLastHost() {
        val address = lastHostAddress ?: return
        val device = bluetoothAdapter?.bondedDevices?.firstOrNull { it.address == address } ?: return
        HidDataSender.requestConnect(device)
    }

    private fun buildNotification(): Notification {
        val armed = hidTransactions != null
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_key)
            .setContentTitle(
                getString(
                    if (armed) R.string.notification_hid_title
                    else R.string.notification_phone_title
                )
            )
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, AuthenticatorActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
        if (armed)
            builder.addAction(
                0,
                getString(R.string.notification_action_turn_off),
                PendingIntent.getService(
                    this,
                    0,
                    Intent(this, AuthenticatorService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
        return builder.build()
    }

    private fun showNotification() {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification())
    }

    private fun startForegroundWithNotification() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_hid),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        )
    }

    companion object : Logging {
        override val TAG = "AuthenticatorService"

        private const val CHANNEL_ID = "hid"
        private const val NOTIFICATION_ID = 1
        private const val PREF_LAST_HOST = "last_hid_host"
        private const val ACTION_ARM = "me.henneke.wearauthn.action.ARM_SESSION"
        private const val ACTION_STOP = "me.henneke.wearauthn.action.STOP_SESSION"
        private const val MAX_IDLE_CHECK_INTERVAL_MS = 60_000L

        fun isAlwaysReadyForPhone(context: Context): Boolean =
            context.defaultSharedPreferences.getBoolean(
                context.getString(R.string.preference_phone_always_ready_key),
                context.resources.getBoolean(R.bool.default_phone_always_ready)
            )

        /** Arms a session for computers. Call when the user opens the app. */
        fun arm(context: Context) {
            if (context.bluetoothAdapter?.isEnabled == true)
                start(context, ACTION_ARM)
        }

        /**
         * Makes the service match the "always ready for phone" setting: it starts listening, or
         * stops unless a session is armed.
         */
        fun applyPhoneSetting(context: Context) = start(context, null)

        private fun start(context: Context, action: String?) {
            if (!context.hasBluetoothPermissions)
                return
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, AuthenticatorService::class.java).setAction(action)
                )
            } catch (e: IllegalStateException) {
                i { "Cannot start the authenticator service right now: ${e.message}" }
            }
        }
    }
}
