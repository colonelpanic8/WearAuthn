package me.henneke.wearauthn.bthid

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import me.henneke.wearauthn.R

/**
 * Keeps the app at foreground-service importance while the HID Device app is registered.
 *
 * The Bluetooth stack unregisters a HID Device app as soon as its UID drops below
 * foreground-service importance, which removes the SDP record mid-pairing and drops the
 * connection whenever the screen turns off.
 */
class HidForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_hid),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_key)
            .setContentTitle(getString(R.string.notification_hid_title))
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL_ID = "hid"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, HidForegroundService::class.java)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, HidForegroundService::class.java))
        }
    }
}
