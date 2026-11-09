package me.henneke.wearauthn.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.support.wearable.activity.WearableActivity
import androidx.annotation.MainThread

private const val EXTRA_CONFIRM_DEVICE_CREDENTIAL_RECEIVER =
    "me.henneke.wearauthn.common.EXTRA_CONFIRM_DEVICE_CREDENTIAL_RECEIVER"

private const val REQUEST_CONFIRM_CREDENTIAL = 5
private const val STATE_LAUNCHED = "launched"

class ConfirmDeviceCredentialActivity : WearableActivity() {

    private var launched = false
    private var resultSent = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        current = this
        launched = savedInstanceState?.getBoolean(STATE_LAUNCHED) == true
        if (dismissPending) {
            dismissPending = false
            returnResult(Activity.RESULT_CANCELED)
        }
    }

    override fun onDestroy() {
        if (current === this)
            current = null
        if (isFinishing && !resultSent)
            sendResult(Activity.RESULT_CANCELED)
        super.onDestroy()
    }

    // The credential screen is launched only once this activity is resumed: started while the
    // screen is still off, it is dismissed right away and reports a cancellation.
    override fun onResume() {
        super.onResume()
        if (launched)
            return
        launched = true
        val keyguardManager = getSystemService(KeyguardManager::class.java)
        val launchIntent = keyguardManager?.createConfirmDeviceCredentialIntent(null, null)
        if (launchIntent == null) {
            returnResult(Activity.RESULT_CANCELED)
            return
        }
        startActivityForResult(launchIntent, REQUEST_CONFIRM_CREDENTIAL)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_LAUNCHED, launched)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CONFIRM_CREDENTIAL)
            returnResult(resultCode)
    }

    private fun sendResult(resultCode: Int) {
        resultSent = true
        intent.getParcelableExtra<ResultReceiver>(EXTRA_CONFIRM_DEVICE_CREDENTIAL_RECEIVER)
            ?.send(resultCode, Bundle())
    }

    private fun returnResult(resultCode: Int) {
        sendResult(resultCode)
        finish()
    }

    companion object {
        // Main thread only.
        private var current: ConfirmDeviceCredentialActivity? = null
        private var dismissPending = false

        /** Shows the credential screen and sends its result code to [receiver]. */
        @MainThread
        fun start(context: Context, receiver: ResultReceiver) {
            // A dismissal left over from a launch that never appeared must not close this one.
            dismissPending = false
            context.startActivity(
                Intent(context, ConfirmDeviceCredentialActivity::class.java).apply {
                    putExtra(EXTRA_CONFIRM_DEVICE_CREDENTIAL_RECEIVER, receiver)
                    // Starting with Android P (due to a bug, Oreo is excepted),
                    // FLAG_ACTIVITY_NEW_TASK needs to be set when launching an activity
                    // from a non-activity context.
                    // https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-wear-9.0.0_r14/core/java/android/app/ContextImpl.java#907
                    if (context !is Activity)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
        }

        /** Closes the credential screen of a request that was abandoned. */
        fun dismiss() {
            Handler(Looper.getMainLooper()).post {
                val activity = current?.takeUnless { it.isFinishing }
                if (activity == null) {
                    dismissPending = true
                } else {
                    activity.finishActivity(REQUEST_CONFIRM_CREDENTIAL)
                    activity.returnResult(Activity.RESULT_CANCELED)
                }
            }
        }
    }
}
