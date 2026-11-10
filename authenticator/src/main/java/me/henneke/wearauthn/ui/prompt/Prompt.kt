package me.henneke.wearauthn.ui.prompt

import me.henneke.wearauthn.fido.context.PromptContent

data class AccountChoice(val title: String, val subtitle: String?)

enum class Approval { Approved, Denied, TimedOut }

/**
 * A question for the user. [timedOut] is the answer when they give none in time, [dismissed] the
 * one when they back out.
 */
sealed interface Prompt<R> {
    val timeoutMs: Long
    val timedOut: R
    val dismissed: R

    data class Approve(val content: PromptContent, override val timeoutMs: Long) : Prompt<Approval> {
        override val timedOut = Approval.TimedOut
        override val dismissed = Approval.Denied
    }

    /** Answered with the line breaks the text was shown with, or null if denied. */
    data class Transaction(val site: String, val text: String, override val timeoutMs: Long) :
        Prompt<List<Int>?> {
        override val timedOut: List<Int>? = null
        override val dismissed: List<Int>? = null
    }

    /** Answered with the index of the chosen account, or null. */
    data class ChooseAccount(
        val site: String,
        val accounts: List<AccountChoice>,
        override val timeoutMs: Long,
    ) : Prompt<Int?> {
        override val timedOut: Int? = null
        override val dismissed: Int? = null
    }
}
