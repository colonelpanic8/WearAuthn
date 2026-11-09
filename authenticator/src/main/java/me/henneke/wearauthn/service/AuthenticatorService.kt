package me.henneke.wearauthn.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.henneke.wearauthn.Logging
import me.henneke.wearauthn.R
import me.henneke.wearauthn.bthid.HidDataSender
import me.henneke.wearauthn.bthid.HidIntrDataListener
import me.henneke.wearauthn.bthid.InputHostWrapper
import me.henneke.wearauthn.fido.hid.TransactionManager
import me.henneke.wearauthn.fido.rfcomm.RfcommAuthenticator
import me.henneke.wearauthn.i
import me.henneke.wearauthn.ui.PromptActivity
import me.henneke.wearauthn.ui.bluetoothAdapter
import me.henneke.wearauthn.ui.hasBluetoothPermissions
import me.henneke.wearauthn.ui.defaultSharedPreferences
import me.henneke.wearauthn.ui.main.AuthenticatorActivity
import me.henneke.wearauthn.ui.main.HidAuthenticatorContext
import kotlin.coroutines.CoroutineContext

/**
 * Runs the authenticator in the background: it keeps the Bluetooth HID Device app registered,
 * reconnects to the last HID host, serves CTAP requests from that host and from phones over
 * RFCOMM, and shows confirmation prompts on top of whatever is on screen.
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
    private var reconnectJob: Job? = null
    private var hidConnected = false

    private val ioListener = object : HidIntrDataListener {
        override fun onIntrData(
            device: BluetoothDevice,
            reportId: Byte,
            data: ByteArray,
            host: InputHostWrapper
        ) {
            transactionManager.handleReport(data) { reports ->
                for (report in reports)
                    host.sendReport(device, reportId.toInt(), report)
            }
        }
    }

    private val profileListener = object : HidDataSender.ProfileListener {
        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            hidConnected = state == BluetoothProfile.STATE_CONNECTED
            if (hidConnected) {
                i { "HID connected to ${device.address}" }
                lastHostAddress = device.address
            }
        }

        override fun onAppStatusChanged(registered: Boolean) {
            if (registered)
                reconnectToLastHost()
        }

        override fun onServiceStateChanged(proxy: BluetoothProfile?) {}
    }

    private val aclReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
            if (device?.address == lastHostAddress)
                reconnectToLastHost()
        }
    }

    private var lastHostAddress: String?
        get() = defaultSharedPreferences.getString(PREF_LAST_HOST, null)
        set(value) = defaultSharedPreferences.edit().putString(PREF_LAST_HOST, value).apply()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotification()
        authenticatorContext = HidAuthenticatorContext(
            this,
            obtainActivity = { PromptActivity.obtain(this) },
            releaseActivity = { PromptActivity.release() },
        )
        transactionManager = TransactionManager(authenticatorContext)
        HidDataSender.register(this, profileListener, ioListener)
        bluetoothAdapter?.let { adapter ->
            // Phones ask for one credential at a time and only talk to the watch.
            val rfcommContext = HidAuthenticatorContext(
                this,
                obtainActivity = { PromptActivity.obtain(this) },
                releaseActivity = { PromptActivity.release() },
                confirmsAbsentCredentials = false,
            )
            rfcommAuthenticator = RfcommAuthenticator(adapter, rfcommContext, this).also {
                it.start()
            }
        }
        ContextCompat.registerReceiver(
            this,
            aclReceiver,
            IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED),
            ContextCompat.RECEIVER_EXPORTED
        )
        reconnectJob = launch {
            while (isActive) {
                delay(RECONNECT_INTERVAL_MS)
                if (!hidConnected)
                    reconnectToLastHost()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        unregisterReceiver(aclReceiver)
        rfcommAuthenticator?.stop()
        HidDataSender.unregister(profileListener, ioListener)
        cancel()
        super.onDestroy()
    }

    @SuppressLint("MissingPermission")
    private fun reconnectToLastHost() {
        if (hidConnected)
            return
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
        private const val RECONNECT_INTERVAL_MS = 60_000L

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
