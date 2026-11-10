package me.henneke.wearauthn.ui

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP
import android.os.PowerManager.FULL_WAKE_LOCK
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.henneke.wearauthn.R
import me.henneke.wearauthn.ui.prompt.Approval
import me.henneke.wearauthn.ui.prompt.ApproveScreen
import me.henneke.wearauthn.ui.prompt.ChooseAccountScreen
import me.henneke.wearauthn.ui.prompt.Prompt
import me.henneke.wearauthn.ui.prompt.TransactionScreen
import me.henneke.wearauthn.ui.theme.WearAuthnTheme

/**
 * Shows the questions that requests arriving in the background put to the user.
 *
 * Starting it from the background is only allowed once the app holds the "display over other
 * apps" permission, so it is also offered through a full-screen notification that the user can
 * tap.
 *
 * On a locked watch it waits behind the lock screen. Approving a request takes the owner, and a
 * locked watch may be off the wrist.
 */
class PromptActivity : ComponentActivity() {

    private class Pending<R>(val prompt: Prompt<R>) {
        val deadline = SystemClock.uptimeMillis() + prompt.timeoutMs
        val answer = CompletableDeferred<R>()

        @Suppress("UNCHECKED_CAST")
        fun answer(value: Any?) {
            answer.complete(value as R)
        }
    }

    private val prompts = mutableStateListOf<Pending<*>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(getSystemService(KeyguardManager::class.java)?.isDeviceLocked != true)
        setTurnScreenOn(true)
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        if (users == 0) {
            finish()
            return
        }
        setContent {
            WearAuthnTheme {
                prompts.lastOrNull()?.let { PromptHost(it) }
            }
        }
        current = this
        pending?.complete(this)
        pending = null
    }

    override fun onDestroy() {
        if (current === this)
            current = null
        // Swiping the activity away answers whatever it was asking.
        if (isFinishing) {
            for (prompt in prompts.toList())
                prompt.answer(prompt.prompt.dismissed)
        }
        super.onDestroy()
    }

    /** Puts [prompt] on screen above any earlier one and waits for the answer. */
    suspend fun <R> ask(prompt: Prompt<R>): R {
        val entry = Pending(prompt)
        prompts += entry
        return try {
            withTimeoutOrNull(prompt.timeoutMs) { entry.answer.await() } ?: prompt.timedOut
        } finally {
            prompts -= entry
        }
    }

    @Composable
    private fun PromptHost(entry: Pending<*>) {
        val context = LocalContext.current
        DisposableEffect(entry) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            wink(context)
            @Suppress("DEPRECATION")
            val wakeLock = context.powerManager
                ?.newWakeLock(FULL_WAKE_LOCK or ACQUIRE_CAUSES_WAKEUP, "WearAuthn:WakeForPrompt")
                ?.apply { acquire(entry.prompt.timeoutMs) }
            onDispose {
                wakeLock?.takeIf { it.isHeld }?.release()
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        BackHandler { entry.answer(entry.prompt.dismissed) }
        val remaining = remainingFraction(entry)
        when (val prompt = entry.prompt) {
            is Prompt.Approve -> ApproveScreen(
                prompt.content,
                remaining,
                onApprove = { entry.answer(Approval.Approved) },
                onDeny = { entry.answer(Approval.Denied) },
            )
            is Prompt.Transaction -> TransactionScreen(
                prompt.site,
                prompt.text,
                remaining,
                onApprove = { entry.answer(it) },
                onDeny = { entry.answer(null) },
            )
            is Prompt.ChooseAccount ->
                ChooseAccountScreen(prompt.site, prompt.accounts, onChoose = { entry.answer(it) })
        }
    }

    @Composable
    private fun remainingFraction(entry: Pending<*>): () -> Float {
        var now by remember(entry) { mutableLongStateOf(SystemClock.uptimeMillis()) }
        LaunchedEffect(entry) {
            while (now < entry.deadline) {
                withFrameMillis { now = SystemClock.uptimeMillis() }
            }
        }
        return { ((entry.deadline - now).toFloat() / entry.prompt.timeoutMs).coerceIn(0f, 1f) }
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
        suspend fun <T> use(context: Context, block: suspend (PromptActivity) -> T): T? =
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
