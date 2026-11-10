package me.henneke.wearauthn.ui.main

import android.content.Context
import me.henneke.wearauthn.breakAt
import me.henneke.wearauthn.fido.context.AuthenticatorContext
import me.henneke.wearauthn.fido.context.AuthenticatorSpecialStatus
import me.henneke.wearauthn.fido.context.AuthenticatorStatus
import me.henneke.wearauthn.fido.context.RequestInfo
import me.henneke.wearauthn.fido.context.WebAuthnCredential
import me.henneke.wearauthn.fido.hid.HID_USER_PRESENCE_TIMEOUT_MS
import me.henneke.wearauthn.ui.PromptActivity
import me.henneke.wearauthn.ui.prompt.AccountChoice
import me.henneke.wearauthn.ui.prompt.Approval
import me.henneke.wearauthn.ui.prompt.Prompt

private const val CHOOSE_ACCOUNT_TIMEOUT_MS = 30_000L

@ExperimentalUnsignedTypes
class HidAuthenticatorContext(
    private val context: Context,
    override val confirmsAbsentCredentials: Boolean = true,
) : AuthenticatorContext(context, isHidTransport = true) {

    override fun notifyUser(info: RequestInfo) {
        // No-op for HID transport since we already asked for confirmation during
        // confirmWithUser
    }

    override fun handleSpecialStatus(specialStatus: AuthenticatorSpecialStatus) {
        // No-op for HID transport since we always ask for confirmation before encountering
        // a special status.
    }

    /**
     * Returns true if the user confirms the request, false if the user denies it explicitly and
     * null if the confirmation prompt times out or cannot be shown.
     */
    override suspend fun confirmRequestWithUser(info: RequestInfo): Boolean? =
        waitingForUser {
            when (ask(Prompt.Approve(info.prompt, HID_USER_PRESENCE_TIMEOUT_MS))) {
                Approval.Approved -> true
                Approval.Denied -> false
                Approval.TimedOut, null -> null
            }
        }

    override suspend fun confirmTransactionWithUser(rpId: String, prompt: String): String? =
        waitingForUser {
            ask(Prompt.Transaction(rpId, prompt, HID_USER_PRESENCE_TIMEOUT_MS))
                ?.let { lineBreaks -> prompt.breakAt(lineBreaks) }
        }

    override suspend fun chooseAccount(
        rpId: String,
        credentials: List<WebAuthnCredential>,
    ): WebAuthnCredential? {
        val accounts = credentials.mapIndexed { index, credential ->
            credential.getTwoLineInfo(index + 1).let { (title, subtitle) -> AccountChoice(title, subtitle) }
        }
        val index = ask(Prompt.ChooseAccount(rpId, accounts, CHOOSE_ACCOUNT_TIMEOUT_MS))
        return index?.let { credentials.getOrNull(it) }
    }

    private suspend fun <R> ask(prompt: Prompt<R>): R? =
        PromptActivity.use(context) { it.ask(prompt) }

    private suspend fun <T> waitingForUser(block: suspend () -> T): T =
        try {
            status = AuthenticatorStatus.WAITING_FOR_UP
            block()
        } finally {
            status = AuthenticatorStatus.PROCESSING
        }
}
