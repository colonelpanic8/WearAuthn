package me.henneke.wearauthn.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.support.wearable.activity.WearableActivity

const val EXTRA_CONFIRM_DEVICE_CREDENTIAL_RECEIVER =
    "me.henneke.wearauthn.common.EXTRA_CONFIRM_DEVICE_CREDENTIAL_RECEIVER"

private const val REQUEST_CONFIRM_CREDENTIAL = 5
private const val STATE_LAUNCHED = "launched"

class ConfirmDeviceCredentialActivity : WearableActivity() {

    private var launched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        current = this
        launched = savedInstanceState?.getBoolean(STATE_LAUNCHED) == true
    }

    override fun onDestroy() {
        if (current === this)
            current = null
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

    private fun returnResult(resultCode: Int) {
        intent.getParcelableExtra<ResultReceiver>(EXTRA_CONFIRM_DEVICE_CREDENTIAL_RECEIVER)
            ?.send(resultCode, Bundle())
        finish()
    }

    companion object {
        private var current: ConfirmDeviceCredentialActivity? = null

        /** Closes the credential screen of a request that was abandoned. */
        fun dismiss() {
            Handler(Looper.getMainLooper()).post {
                current?.run {
                    finishActivity(REQUEST_CONFIRM_CREDENTIAL)
                    returnResult(Activity.RESULT_CANCELED)
                }
            }
        }
    }
}
