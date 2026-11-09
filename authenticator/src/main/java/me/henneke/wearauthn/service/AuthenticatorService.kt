package me.henneke.wearauthn.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
 * One authenticator session, started by opening the app. While it runs, the Bluetooth HID Device
 * app is registered, phones can connect over RFCOMM, and confirmation prompts show on top of
 * whatever is on screen. It stops after the configured idle time or from its notification.
 *
 * Running as a foreground service is also what keeps the HID registration alive: the Bluetooth
 * stack unregisters HID Device apps whose UID drops below foreground-service importance.
 */
@ExperimentalUnsignedTypes
class AuthenticatorService : Service(), CoroutineScope {

    override val coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.Main

    private lateinit var authenticatorContext: HidAuthenticatorContext
    private lateinit var transactionManager: TransactionManager
    private var rfcommAuthenticator: RfcommAuthenticator? = null

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
            transactionManager.handleReport(data) { reports ->
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
        transactionManager = TransactionManager(authenticatorContext)
        HidDataSender.addListener(profileListener)
        HidDataSender.start(this, ioListener)
        bluetoothAdapter?.let { adapter ->
            // Phones ask for one credential at a time and only talk to the watch.
            val rfcommContext = HidAuthenticatorContext(this, confirmsAbsentCredentials = false)
            rfcommAuthenticator =
                RfcommAuthenticator(adapter, rfcommContext, this, ::recordActivity).also {
                    it.start()
                }
        }
        watchIdleTime()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            i { "Turned off from the notification" }
            stopSelf()
            return START_NOT_STICKY
        }
        recordActivity()
        if (!HidDataSender.hasTarget)
            connectToLastHost()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        HidDataSender.removeListener(profileListener)
        rfcommAuthenticator?.stop()
        HidDataSender.stop()
        transactionManager.close()
        cancel()
        super.onDestroy()
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
                stopSelf()
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

    private fun startForegroundWithNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_hid),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_key)
            .setContentTitle(getString(R.string.notification_hid_title))
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, AuthenticatorActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .addAction(
                0,
                getString(R.string.notification_action_turn_off),
                PendingIntent.getService(
                    this,
                    0,
                    Intent(this, AuthenticatorService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        )
    }

    companion object : Logging {
        override val TAG = "AuthenticatorService"

        private const val CHANNEL_ID = "hid"
        private const val NOTIFICATION_ID = 1
        private const val PREF_LAST_HOST = "last_hid_host"
        private const val ACTION_STOP = "me.henneke.wearauthn.action.STOP_SESSION"
        private const val MAX_IDLE_CHECK_INTERVAL_MS = 60_000L

        fun start(context: Context) {
            if (!context.hasBluetoothPermissions)
                return
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, AuthenticatorService::class.java)
                )
            } catch (e: IllegalStateException) {
                i { "Cannot start the authenticator service right now: ${e.message}" }
            }
        }
    }
}
