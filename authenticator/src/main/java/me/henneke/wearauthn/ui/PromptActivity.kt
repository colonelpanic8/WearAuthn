package me.henneke.wearauthn.ui

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.henneke.wearauthn.R

/**
 * Blank host for the dialogs that requests arriving in the background need, since dialogs
 * require an activity window.
 *
 * Starting it from the background is only allowed once the app holds the "display over other
 * apps" permission, so it is also offered through a full-screen notification that the user can
 * tap.
 */
class PromptActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        if (users == 0) {
            finish()
            return
        }
        current = this
        pending?.complete(this)
        pending = null
    }

    override fun onDestroy() {
        if (current === this)
            current = null
        super.onDestroy()
    }

    companion object {
        private const val LAUNCH_TIMEOUT_MS = 30_000L
        private const val CHANNEL_ID = "prompt"
        private const val NOTIFICATION_ID = 2

        // Main thread only.
        private var current: PromptActivity? = null
        private var pending: CompletableDeferred<PromptActivity>? = null
        private var users = 0

        /**
         * Runs [block] on the main thread with the activity on screen, and finishes the activity
         * once no caller uses it any more. Returns null without running [block] if the activity
         * could not be shown in time.
         */
        suspend fun <T> use(context: Context, block: suspend (Activity) -> T): T? =
            withContext(Dispatchers.Main) {
                users++
                try {
                    val activity = current?.takeUnless { it.isFinishing || it.isDestroyed }
                        ?: withTimeoutOrNull(LAUNCH_TIMEOUT_MS) {
                            val launching = pending ?: CompletableDeferred<PromptActivity>().also {
                                pending = it
                                launch(context)
                            }
                            launching.await()
                        }
                    activity?.let { block(it) }
                } finally {
                    // No suspension here, so this also runs to completion when cancelled.
                    if (--users == 0) {
                        pending = null
                        context.getSystemService(NotificationManager::class.java)
                            .cancel(NOTIFICATION_ID)
                        current?.finish()
                        current = null
                    }
                }
            }

        private fun launch(context: Context) {
            val intent = Intent(context, PromptActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            val pendingIntent =
                PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_channel_prompt),
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
            manager.notify(
                NOTIFICATION_ID,
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_key)
                    .setContentTitle(context.getString(R.string.notification_prompt_title))
                    .setCategory(NotificationCompat.CATEGORY_CALL)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setContentIntent(pendingIntent)
                    .setFullScreenIntent(pendingIntent, true)
                    .setAutoCancel(true)
                    .build()
            )
            context.startActivity(intent)
        }
    }
}
