package me.henneke.wearauthn.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.tooling.preview.devices.WearDevices
import me.henneke.wearauthn.R
import me.henneke.wearauthn.ui.PIXEL_WATCH_3_45MM
import me.henneke.wearauthn.ui.WatchPreview
import me.henneke.wearauthn.ui.WatchPreviewFrame
import me.henneke.wearauthn.ui.components.Hero
import me.henneke.wearauthn.ui.components.HeroTone
import me.henneke.wearauthn.ui.components.ListButton
import me.henneke.wearauthn.ui.components.RowStyle
import me.henneke.wearauthn.ui.components.ScreenList
import me.henneke.wearauthn.ui.components.SectionHeader
import me.henneke.wearauthn.ui.components.boldArgument

@Composable
fun HomeScreen(
    state: HomeState,
    watchName: String,
    onAllowBluetooth: () -> Unit,
    onTurnOnBluetooth: () -> Unit,
    onHostClick: (Host) -> Unit,
    onPair: () -> Unit,
    onOpenNfcSettings: () -> Unit,
    onOpenPasskeys: () -> Unit,
    onOpenSettings: () -> Unit,
    listState: TransformingLazyColumnState = rememberTransformingLazyColumnState(),
) {
    val status = state.status()
    val pairing = status is HomeStatus.Pairing
    LaunchedEffect(pairing) {
        if (pairing) listState.animateScrollToItem(0)
    }
    ScreenList(state = listState) { spec ->
        item { HomeHero(status, watchName) }
        when (status) {
            HomeStatus.NeedsPermission ->
                item { HeroAction(stringResource(R.string.action_allow), spec, onAllowBluetooth) }
            HomeStatus.BluetoothOff ->
                item { HeroAction(stringResource(R.string.action_turn_on), spec, onTurnOnBluetooth) }
            else -> {}
        }
        val bluetooth = state.bluetooth as? BluetoothState.On
        if (bluetooth != null) {
            if (bluetooth.hosts.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.home_computers), spec) }
                for (host in bluetooth.hosts) {
                    item(key = host.address) {
                        HostRow(host, bluetooth.appRegistered, spec, onClick = { onHostClick(host) })
                    }
                }
            }
            item {
                ListButton(
                    label = stringResource(R.string.home_pair),
                    spec = spec,
                    onClick = onPair,
                    icon = painterResource(R.drawable.ic_bluetooth),
                    secondaryLabel = stringResource(R.string.home_pair_visible).takeIf { pairing },
                    style = if (bluetooth.hosts.isEmpty()) RowStyle.Primary else RowStyle.Outlined,
                    enabled = !pairing,
                )
            }
        }
        if (state.nfc == NfcState.Off) {
            item {
                ListButton(
                    label = stringResource(R.string.home_nfc_off),
                    spec = spec,
                    onClick = onOpenNfcSettings,
                    icon = painterResource(R.drawable.ic_nfc),
                    secondaryLabel = stringResource(R.string.home_nfc_off_text),
                )
            }
        }
        item {
            ListButton(
                label = stringResource(R.string.passkeys_title),
                spec = spec,
                onClick = onOpenPasskeys,
                modifier = Modifier.padding(top = 8.dp),
                icon = painterResource(R.drawable.ic_key),
                secondaryLabel = passkeysLabel(state.passkeys),
            )
        }
        item {
            ListButton(
                label = stringResource(R.string.settings_title),
                spec = spec,
                onClick = onOpenSettings,
                icon = painterResource(R.drawable.ic_settings),
            )
        }
    }
}

@Composable
private fun HomeHero(status: HomeStatus, watchName: String) {
    AnimatedContent(
        targetState = status,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        contentKey = { it::class },
        label = "hero",
    ) { status ->
        val bluetooth = painterResource(R.drawable.ic_bluetooth)
        val key = painterResource(R.drawable.ic_key)
        when (status) {
            HomeStatus.NeedsPermission -> Hero(
                bluetooth,
                stringResource(R.string.home_needs_permission_title),
                AnnotatedString(stringResource(R.string.home_needs_permission_text)),
                HeroTone.Warn,
            )
            HomeStatus.BluetoothOff -> Hero(
                bluetooth,
                stringResource(R.string.home_bluetooth_off_title),
                AnnotatedString(stringResource(R.string.home_bluetooth_off_text)),
                HeroTone.Warn,
            )
            HomeStatus.BluetoothStarting -> Hero(
                bluetooth,
                stringResource(R.string.home_turning_on_title),
                null,
                HeroTone.Calm,
                busy = true,
            )
            is HomeStatus.Pairing -> Hero(
                bluetooth,
                stringResource(
                    R.string.home_pairing_title,
                    "%d:%02d".format(status.secondsLeft / 60, status.secondsLeft % 60),
                ),
                boldArgument(stringResource(R.string.home_pairing_text), watchName),
                HeroTone.Live,
            )
            is HomeStatus.Attached -> Hero(
                key,
                stringResource(R.string.status_bluetooth_connected),
                AnnotatedString(status.host.name),
                HeroTone.Live,
            )
            is HomeStatus.Attaching -> Hero(
                key,
                stringResource(R.string.status_bluetooth_connecting),
                AnnotatedString(status.host.name),
                HeroTone.Calm,
                busy = true,
            )
            HomeStatus.Ready -> Hero(
                key,
                stringResource(R.string.home_ready_title),
                AnnotatedString(stringResource(R.string.home_ready_text)),
                HeroTone.Calm,
            )
            HomeStatus.NoComputers -> Hero(
                key,
                stringResource(R.string.home_no_computers_title),
                AnnotatedString(stringResource(R.string.home_no_computers_text)),
                HeroTone.Calm,
            )
        }
    }
}

@Composable
private fun TransformingLazyColumnItemScope.HeroAction(
    label: String,
    spec: TransformationSpec,
    onClick: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().transformedHeight(this, spec), contentAlignment = Alignment.Center) {
        Button(
            onClick = onClick,
            modifier = Modifier.padding(top = 4.dp),
            transformation = SurfaceTransformation(spec),
            label = { Text(label) },
        )
    }
}

@Composable
private fun TransformingLazyColumnItemScope.HostRow(
    host: Host,
    appRegistered: Boolean,
    spec: TransformationSpec,
    onClick: () -> Unit,
) {
    ListButton(
        label = host.name,
        spec = spec,
        onClick = onClick,
        icon = host.kind.icon(),
        secondaryLabel = when (host.link) {
            Link.Attached -> stringResource(R.string.status_bluetooth_connected)
            Link.Attaching -> stringResource(R.string.status_bluetooth_connecting)
            Link.Detached, Link.Detaching -> null
        },
        style = if (host.link == Link.Attached) RowStyle.Primary else RowStyle.Action,
        enabled = appRegistered && (host.link == Link.Attached || host.link == Link.Detached),
    )
}

@Composable
fun DeviceKind.icon(): Painter =
    painterResource(
        when (this) {
            DeviceKind.Computer -> R.drawable.ic_computer
            DeviceKind.Phone -> R.drawable.ic_phone
            DeviceKind.Watch -> R.drawable.ic_watch
            DeviceKind.Audio -> R.drawable.ic_headset
            DeviceKind.Other -> R.drawable.ic_bluetooth
        }
    )

@Composable
private fun passkeysLabel(passkeys: PasskeysState): String =
    when (passkeys) {
        is PasskeysState.Active ->
            if (passkeys.count == 0) stringResource(R.string.home_passkeys_none)
            else pluralStringResource(R.plurals.home_passkeys_count, passkeys.count, passkeys.count)
        else -> stringResource(R.string.generic_off)
    }

private val thinkPad = Host("00:11:22:33:44:55", "ThinkPad X1", DeviceKind.Computer, Link.Detached)
private val ryzen = Host("66:77:88:99:AA:BB", "ryzen-shine", DeviceKind.Computer, Link.Detached)

private fun previewState(
    bluetooth: BluetoothState,
    nfc: NfcState = NfcState.On,
    passkeys: PasskeysState = PasskeysState.Active(3),
) = HomeState(bluetooth, nfc, passkeys)

private fun on(vararg hosts: Host, visibleSecondsLeft: Int? = null) =
    BluetoothState.On(hosts.toList(), appRegistered = true, visibleSecondsLeft = visibleSecondsLeft)

@Composable
private fun HomePreview(state: HomeState, listState: TransformingLazyColumnState? = null) =
    WatchPreviewFrame {
        HomeScreen(
            state = state,
            watchName = "Pixel Watch 3",
            onAllowBluetooth = {},
            onTurnOnBluetooth = {},
            onHostClick = {},
            onPair = {},
            onOpenNfcSettings = {},
            onOpenPasskeys = {},
            onOpenSettings = {},
            listState = listState ?: rememberTransformingLazyColumnState(),
        )
    }

@WatchPreview
@Preview(device = WearDevices.SMALL_ROUND, showBackground = true, backgroundColor = 0xFF000000, name = "small")
@Preview(device = PIXEL_WATCH_3_45MM, showBackground = true, backgroundColor = 0xFF000000, fontScale = 1.24f, name = "large-font")
@Preview(device = PIXEL_WATCH_3_45MM, showBackground = true, backgroundColor = 0xFF000000, locale = "de", name = "de")
@Composable
fun HomeAttached() = HomePreview(previewState(on(thinkPad.copy(link = Link.Attached), ryzen)))

@WatchPreview
@Composable
fun HomeAttaching() = HomePreview(previewState(on(thinkPad.copy(link = Link.Attaching), ryzen)))

@WatchPreview
@Composable
fun HomeReady() = HomePreview(previewState(on(thinkPad, ryzen)))

@WatchPreview
@Composable
fun HomeNoComputers() = HomePreview(previewState(on(), passkeys = PasskeysState.Available))

@WatchPreview
@Composable
fun HomeBluetoothOff() = HomePreview(previewState(BluetoothState.Off))

@WatchPreview
@Composable
fun HomeNeedsPermission() = HomePreview(previewState(BluetoothState.NeedsPermission))

@WatchPreview
@Composable
fun HomeTurningOn() = HomePreview(previewState(BluetoothState.TurningOn))

@WatchPreview
@Composable
fun HomePairing() = HomePreview(previewState(on(thinkPad, ryzen, visibleSecondsLeft = 52)))

@WatchPreview
@Composable
fun HomeScrolled() = HomePreview(
    previewState(on(thinkPad.copy(link = Link.Attached), ryzen), nfc = NfcState.Off),
    rememberTransformingLazyColumnState(initialAnchorItemIndex = 5, initialAnchorItemScrollOffset = 30),
)
