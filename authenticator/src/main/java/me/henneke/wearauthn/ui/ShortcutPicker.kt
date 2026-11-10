package me.henneke.wearauthn.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.henneke.wearauthn.R
import me.henneke.wearauthn.ui.components.ListButton
import me.henneke.wearauthn.ui.components.RowStyle
import me.henneke.wearauthn.ui.components.ScreenList
import me.henneke.wearauthn.ui.components.ScreenTitle
import me.henneke.wearauthn.ui.main.DeviceKind
import me.henneke.wearauthn.ui.main.Host
import me.henneke.wearauthn.ui.main.Link
import me.henneke.wearauthn.ui.main.icon

/** Picks the device a watch face shortcut attaches to. */
@Composable
fun ShortcutPickerScreen(hosts: List<Host>, onPick: (Host) -> Unit) {
    ScreenList { spec ->
        item { ScreenTitle(stringResource(R.string.preference_screen_title_shortcut_picker), spec) }
        for (host in hosts) {
            item(key = host.address) {
                ListButton(
                    label = host.name,
                    spec = spec,
                    onClick = { onPick(host) },
                    icon = host.kind.icon(),
                    style = RowStyle.Action,
                )
            }
        }
    }
}

@WatchPreview
@Composable
fun ShortcutPicker() = WatchPreviewFrame {
    ShortcutPickerScreen(
        hosts = listOf(
            Host("00:11:22:33:44:55", "ThinkPad X1", DeviceKind.Computer, Link.Detached),
            Host("66:77:88:99:AA:BB", "ryzen-shine", DeviceKind.Computer, Link.Detached),
        ),
        onPick = {},
    )
}
