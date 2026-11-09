package me.henneke.wearauthn.service

import androidx.annotation.StringRes
import me.henneke.wearauthn.R

enum class SessionTimeout(
    val preferenceValue: String,
    val durationMs: Long?,
    @StringRes val label: Int,
) {
    FIVE_MINUTES("5", 5 * 60_000L, R.string.session_timeout_five_minutes),
    FIFTEEN_MINUTES("15", 15 * 60_000L, R.string.session_timeout_fifteen_minutes),
    ONE_HOUR("60", 60 * 60_000L, R.string.session_timeout_one_hour),
    UNTIL_TURNED_OFF("never", null, R.string.session_timeout_until_turned_off);

    companion object {
        val DEFAULT = FIFTEEN_MINUTES

        fun fromPreference(value: String?): SessionTimeout =
            entries.firstOrNull { it.preferenceValue == value } ?: DEFAULT
    }
}
