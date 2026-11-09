package me.henneke.wearauthn.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.support.wearable.view.AcceptDenyDialog
import androidx.fragment.app.FragmentActivity
import androidx.wear.activity.ConfirmationActivity
import kotlinx.coroutines.*
import me.henneke.wearauthn.R
import me.henneke.wearauthn.fido.context.AuthenticatorContext
import kotlin.coroutines.CoroutineContext

const val EXTRA_MANAGE_SPACE_RECEIVER = "me.henneke.wearauthn.common.EXTRA_MANAGE_SPACE_RECEIVER"

@ExperimentalUnsignedTypes
class ManageSpaceActivity : FragmentActivity(), CoroutineScope {

    // Never cancelled: a deletion that has started has to finish and report its real outcome.
    override val coroutineContext: CoroutineContext = Dispatchers.Default + SupervisorJob()

    private var deleting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_space)
        current = this
    }

    override fun onDestroy() {
        if (current === this)
            current = null
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        AcceptDenyDialog(this).apply {
            setTitle(R.string.app_family_name)
            setMessage(getText(R.string.prompt_delete_all_data_first_step_message))
            setPositiveButton { _, _ ->
                // We ask again with the buttons in a different place to prevent
                // accidental deletions.
                AcceptDenyDialog(this@ManageSpaceActivity).apply {
                    setTitle(R.string.app_family_name)
                    setMessage(getText(R.string.prompt_delete_all_data_second_step_message))
                    setPositiveButton { _, _ -> deleteAllData() }
                    setNegativeButton { _, _ -> returnResult(Activity.RESULT_CANCELED) }
                    setOnCancelListener { returnResult(Activity.RESULT_CANCELED) }
                }.show()
            }
            setNegativeButton { _, _ -> returnResult(Activity.RESULT_CANCELED) }
            setOnCancelListener { returnResult(Activity.RESULT_CANCELED) }
            setOnShowListener { wink(context) }
        }.show()
    }

    private fun deleteAllData() {
        deleting = true
        launch {
            AuthenticatorContext.deleteAllData(this@ManageSpaceActivity)
            withContext(Dispatchers.Main) {
                startActivity(
                    Intent(
                        this@ManageSpaceActivity,
                        ConfirmationActivity::class.java
                    ).apply {
                        putExtra(
                            ConfirmationActivity.EXTRA_ANIMATION_TYPE,
                            ConfirmationActivity.SUCCESS_ANIMATION
                        )
                        putExtra(
                            ConfirmationActivity.EXTRA_MESSAGE,
                            getString(R.string.message_deleted_all_data)
                        )
                    })
                returnResult(Activity.RESULT_OK)
            }
        }
    }

    private fun returnResult(resultCode: Int) {
        intent.getParcelableExtra<ResultReceiver>(EXTRA_MANAGE_SPACE_RECEIVER)
            ?.send(resultCode, Bundle())
        finish()
    }

    companion object {
        // Main thread only.
        private var current: ManageSpaceActivity? = null

        /**
         * Closes the screen of a reset request that was abandoned, so that confirming it later
         * cannot delete everything behind the client's back.
         */
        fun dismiss() {
            Handler(Looper.getMainLooper()).post {
                current?.takeUnless { it.deleting }?.returnResult(Activity.RESULT_CANCELED)
            }
        }
    }
}