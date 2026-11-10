package me.henneke.wearauthn.ui

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.wear.compose.material3.Dialog
import androidx.wear.compose.material3.SuccessConfirmationDialog
import androidx.wear.compose.material3.ConfirmationDialogDefaults
import androidx.wear.compose.material3.confirmationDialogCurvedText
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.henneke.wearauthn.R
import me.henneke.wearauthn.fido.context.AuthenticatorContext
import me.henneke.wearauthn.ui.reset.ResetAskContent
import me.henneke.wearauthn.ui.reset.ResetBusyScreen
import me.henneke.wearauthn.ui.reset.ResetConfirmScreen
import me.henneke.wearauthn.ui.theme.WearAuthnTheme
import kotlin.coroutines.CoroutineContext

const val EXTRA_MANAGE_SPACE_RECEIVER = "me.henneke.wearauthn.common.EXTRA_MANAGE_SPACE_RECEIVER"

/** Asks twice before deleting every credential, for a client's reset request. */
@ExperimentalUnsignedTypes
class ManageSpaceActivity : ComponentActivity(), CoroutineScope {

    private enum class Step { Ask, Confirm, Deleting, Done }

    // Never cancelled: a deletion that has started has to finish and report its real outcome.
    override val coroutineContext: CoroutineContext = Dispatchers.Default + SupervisorJob()

    private var step by mutableStateOf(Step.Ask)
    private var resultSent = false

    private val deleting: Boolean
        get() = step == Step.Deleting || step == Step.Done

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        current = this
        setContent {
            WearAuthnTheme {
                BackHandler(enabled = !deleting) { returnResult(Activity.RESULT_CANCELED) }
                when (step) {
                    Step.Ask, Step.Confirm -> ResetConfirmScreen(onConfirm = ::deleteAllData)
                    Step.Deleting, Step.Done -> ResetBusyScreen()
                }
                Dialog(
                    visible = step == Step.Ask,
                    onDismissRequest = { returnResult(Activity.RESULT_CANCELED) },
                ) {
                    LaunchedEffect(Unit) { wink(this@ManageSpaceActivity) }
                    ResetAskContent(
                        onConfirm = { step = Step.Confirm },
                        onDismiss = { returnResult(Activity.RESULT_CANCELED) },
                    )
                }
                val message = stringResource(R.string.message_deleted_all_data)
                val style = ConfirmationDialogDefaults.curvedTextStyle
                SuccessConfirmationDialog(
                    visible = step == Step.Done,
                    onDismissRequest = ::finish,
                    curvedText = { confirmationDialogCurvedText(message, style) },
                )
            }
        }
    }

    override fun onDestroy() {
        if (current === this)
            current = null
        if (isFinishing && !deleting)
            sendResult(Activity.RESULT_CANCELED)
        super.onDestroy()
    }

    private fun deleteAllData() {
        if (deleting)
            return
        step = Step.Deleting
        launch {
            AuthenticatorContext.deleteAllData(this@ManageSpaceActivity)
            withContext(Dispatchers.Main) {
                sendResult(Activity.RESULT_OK)
                step = Step.Done
            }
        }
    }

    private fun sendResult(resultCode: Int) {
        if (resultSent)
            return
        resultSent = true
        intent.getParcelableExtra<ResultReceiver>(EXTRA_MANAGE_SPACE_RECEIVER)
            ?.send(resultCode, Bundle())
    }

    private fun returnResult(resultCode: Int) {
        sendResult(resultCode)
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
