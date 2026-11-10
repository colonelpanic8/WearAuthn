package me.henneke.wearauthn.ui.main

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import me.henneke.wearauthn.LogLevel
import me.henneke.wearauthn.R
import me.henneke.wearauthn.service.SessionTimeout
import me.henneke.wearauthn.ui.WatchPreview
import me.henneke.wearauthn.ui.WatchPreviewFrame
import me.henneke.wearauthn.ui.components.ListButton
import me.henneke.wearauthn.ui.components.ListText
import me.henneke.wearauthn.ui.components.ScreenList
import me.henneke.wearauthn.ui.components.ScreenTitle

/** [logLevel] is null unless developer options are on. */
data class SettingsState(
    val sessionTimeout: SessionTimeout,
    val onlyWhileWorn: Boolean,
    val phoneAlwaysReady: Boolean,
    val logLevel: LogLevel?,
)

@Composable
fun SettingsScreen(
    state: SettingsState,
    onOpenSessionTimeout: () -> Unit,
    onOnlyWhileWornChange: (Boolean) -> Unit,
    onPhoneAlwaysReadyChange: (Boolean) -> Unit,
    onOpenLogLevel: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    ScreenList { spec ->
        item { ScreenTitle(stringResource(R.string.settings_title), spec) }
        item {
            ListButton(
                label = stringResource(R.string.settings_stay_ready),
                spec = spec,
                onClick = onOpenSessionTimeout,
                icon = painterResource(R.drawable.ic_timer),
                secondaryLabel = stringResource(state.sessionTimeout.label),
            )
        }
        item {
            SettingSwitch(
                checked = state.onlyWhileWorn,
                onCheckedChange = onOnlyWhileWornChange,
                spec = spec,
                label = stringResource(R.string.preference_end_session_off_wrist_title),
                secondaryLabel = stringResource(
                    if (state.onlyWhileWorn) R.string.preference_end_session_off_wrist_summary_on
                    else R.string.preference_end_session_off_wrist_summary_off
                ),
            )
        }
        item {
            SettingSwitch(
                checked = state.phoneAlwaysReady,
                onCheckedChange = onPhoneAlwaysReadyChange,
                spec = spec,
                label = stringResource(R.string.settings_phone),
                secondaryLabel = stringResource(
                    if (state.phoneAlwaysReady) R.string.preference_phone_always_ready_summary_on
                    else R.string.preference_phone_always_ready_summary_off
                ),
            )
        }
        if (state.logLevel != null) {
            item {
                ListButton(
                    label = stringResource(R.string.preference_log_level_title),
                    spec = spec,
                    onClick = onOpenLogLevel,
                    icon = painterResource(R.drawable.ic_bug_report),
                    secondaryLabel = state.logLevel.name,
                )
            }
        }
        item {
            ListButton(
                label = stringResource(R.string.preference_about_title),
                spec = spec,
                onClick = onOpenAbout,
                icon = painterResource(R.drawable.ic_info),
            )
        }
    }
}

/** Without an icon, so that the label keeps to one line next to the switch. */
@Composable
private fun TransformingLazyColumnItemScope.SettingSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    spec: TransformationSpec,
    label: String,
    secondaryLabel: String,
) {
    SwitchButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
        transformation = SurfaceTransformation(spec),
        secondaryLabel = { Text(secondaryLabel) },
        label = { Text(label) },
    )
}

/** One radio row per option, with an optional note below them. */
@Composable
fun <T> PickerScreen(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    note: String? = null,
) {
    ScreenList { spec ->
        item { ScreenTitle(title, spec) }
        for (option in options) {
            item {
                RadioButton(
                    selected = option == selected,
                    onSelect = { onSelect(option) },
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    label = { Text(label(option)) },
                )
            }
        }
        if (note != null) {
            item { ListText(note, spec) }
        }
    }
}

@Composable
fun SessionTimeoutPickerScreen(selected: SessionTimeout, onSelect: (SessionTimeout) -> Unit) =
    PickerScreen(
        title = stringResource(R.string.preference_session_timeout_title),
        options = SessionTimeout.entries,
        selected = selected,
        label = { stringResource(it.label) },
        onSelect = onSelect,
    )

@Composable
fun LogLevelPickerScreen(selected: LogLevel, onSelect: (LogLevel) -> Unit) =
    PickerScreen(
        title = stringResource(R.string.preference_log_level_dialog_title),
        options = LogLevel.entries.asReversed(),
        selected = selected,
        label = { it.name },
        onSelect = onSelect,
        note = stringResource(R.string.preference_log_level_dialog_message),
    )

@WatchPreview
@Composable
fun Settings() = WatchPreviewFrame {
    SettingsScreen(
        state = SettingsState(
            SessionTimeout.FIFTEEN_MINUTES,
            onlyWhileWorn = true,
            phoneAlwaysReady = true,
            logLevel = LogLevel.Info,
        ),
        onOpenSessionTimeout = {},
        onOnlyWhileWornChange = {},
        onPhoneAlwaysReadyChange = {},
        onOpenLogLevel = {},
        onOpenAbout = {},
    )
}

@WatchPreview
@Composable
fun SessionTimeoutPicker() = WatchPreviewFrame {
    SessionTimeoutPickerScreen(SessionTimeout.FIFTEEN_MINUTES, onSelect = {})
}

@WatchPreview
@Composable
fun LogLevelPicker() = WatchPreviewFrame { LogLevelPickerScreen(LogLevel.Disabled, onSelect = {}) }
