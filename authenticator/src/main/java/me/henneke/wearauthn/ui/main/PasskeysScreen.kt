package me.henneke.wearauthn.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AlertDialogContent
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import me.henneke.wearauthn.R
import me.henneke.wearauthn.ui.DialogPreviewFrame
import me.henneke.wearauthn.ui.WatchPreview
import me.henneke.wearauthn.ui.WatchPreviewFrame
import me.henneke.wearauthn.ui.components.ListButton
import me.henneke.wearauthn.ui.components.ListText
import me.henneke.wearauthn.ui.components.ScreenList
import me.henneke.wearauthn.ui.components.ScreenTitle
import me.henneke.wearauthn.ui.components.SectionHeader
import me.henneke.wearauthn.ui.components.breakableHost

data class PasskeyItem(
    val id: Int,
    val title: String,
    val subtitle: String?,
    val displayName: String?,
    val userName: String?,
    val created: String?,
)

data class PasskeySite(val site: String, val passkeys: List<PasskeyItem>)

@Composable
fun PasskeysScreen(
    state: PasskeysState,
    sites: List<PasskeySite>,
    onTurnOn: () -> Unit,
    onPasskeyClick: (PasskeySite, PasskeyItem) -> Unit,
) {
    ScreenList { spec ->
        item { ScreenTitle(stringResource(R.string.passkeys_title), spec) }
        when (state) {
            is PasskeysState.Available -> {
                item {
                    ListText(stringResource(R.string.preference_single_factor_mode_summary_available), spec)
                }
                item {
                    Box(
                        Modifier.fillMaxWidth().transformedHeight(this, spec),
                        contentAlignment = Alignment.Center,
                    ) {
                        Button(
                            onClick = onTurnOn,
                            modifier = Modifier.padding(top = 4.dp),
                            transformation = SurfaceTransformation(spec),
                            label = { Text(stringResource(R.string.action_turn_on)) },
                        )
                    }
                }
            }
            is PasskeysState.NeedsScreenLock -> item {
                ListText(stringResource(R.string.preference_single_factor_mode_summary_enable_lock), spec)
            }
            is PasskeysState.NeedsReset -> item {
                ListText(stringResource(R.string.passkeys_needs_reset), spec)
            }
            is PasskeysState.Active -> if (sites.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.passkeys_empty_title),
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                }
                item { ListText(stringResource(R.string.passkeys_empty_text), spec) }
            }
        }
        if (state is PasskeysState.Active || state is PasskeysState.NeedsReset) {
            for (site in sites) {
                item(key = "site:${site.site}") { SectionHeader(breakableHost(site.site), spec) }
                for (passkey in site.passkeys) {
                    item(key = passkey.id) {
                        ListButton(
                            label = passkey.title,
                            spec = spec,
                            onClick = { onPasskeyClick(site, passkey) },
                            icon = painterResource(R.drawable.ic_person),
                            secondaryLabel = passkey.subtitle,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PasskeyDetailContent(site: String, passkey: PasskeyItem, onDelete: () -> Unit) {
    val spec = rememberTransformationSpec()
    val details = listOfNotNull(
        passkey.displayName,
        passkey.userName,
        passkey.created?.let { stringResource(R.string.passkey_created, it) },
    )
    AlertDialogContent(
        title = { Text(breakableHost(site)) },
        transformationSpec = spec,
        icon = { Icon(painterResource(R.drawable.ic_person), contentDescription = null) },
        text = { Text(details.joinToString("\n"), textAlign = TextAlign.Center) },
    ) {
        item {
            Button(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    iconColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
                transformation = SurfaceTransformation(spec),
                icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                label = { Text(stringResource(R.string.button_delete)) },
            )
        }
    }
}

@Composable
fun PasskeyDeleteConfirmContent(
    site: String,
    passkey: PasskeyItem,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialogContent(
        confirmButton = {
            AlertDialogDefaults.ConfirmButton(
                onClick = onConfirm,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.button_delete))
            }
        },
        title = { Text(stringResource(R.string.passkey_delete_title)) },
        dismissButton = { AlertDialogDefaults.DismissButton(onClick = onDismiss) },
        transformationSpec = rememberTransformationSpec(),
        text = { Text("${passkey.title}\n${breakableHost(site)}", textAlign = TextAlign.Center) },
    )
}

@Composable
fun PasswordlessConfirmContent(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialogContent(
        confirmButton = { AlertDialogDefaults.ConfirmButton(onClick = onConfirm) },
        title = { Text(stringResource(R.string.prompt_single_factor_mode_title)) },
        dismissButton = { AlertDialogDefaults.DismissButton(onClick = onDismiss) },
        transformationSpec = rememberTransformationSpec(),
        text = { Text(stringResource(R.string.prompt_single_factor_mode_message)) },
    )
}

private val previewSites = listOf(
    PasskeySite(
        "github.com",
        listOf(PasskeyItem(0, "colonelpanic8", "Created Nov 3, 2026", null, "colonelpanic8", "Nov 3, 2026")),
    ),
    PasskeySite(
        "login.microsoftonline.com",
        listOf(
            PasskeyItem(1, "Ivan Malison", "ivan@example.com", "Ivan Malison", "ivan@example.com", "Oct 12, 2026"),
            PasskeyItem(2, "Account #2", "Created Sep 30, 2026", null, null, "Sep 30, 2026"),
        ),
    ),
)

@WatchPreview
@Composable
fun PasskeysList() = WatchPreviewFrame {
    PasskeysScreen(PasskeysState.Active(3), previewSites, onTurnOn = {}, onPasskeyClick = { _, _ -> })
}

@WatchPreview
@Composable
fun PasskeysEmpty() = WatchPreviewFrame {
    PasskeysScreen(PasskeysState.Active(0), emptyList(), onTurnOn = {}, onPasskeyClick = { _, _ -> })
}

@WatchPreview
@Composable
fun PasskeysAvailable() = WatchPreviewFrame {
    PasskeysScreen(PasskeysState.Available, emptyList(), onTurnOn = {}, onPasskeyClick = { _, _ -> })
}

@WatchPreview
@Composable
fun PasskeysNeedsScreenLock() = WatchPreviewFrame {
    PasskeysScreen(PasskeysState.NeedsScreenLock, emptyList(), onTurnOn = {}, onPasskeyClick = { _, _ -> })
}

@WatchPreview
@Composable
fun PasskeysNeedsReset() = WatchPreviewFrame {
    PasskeysScreen(PasskeysState.NeedsReset(3), previewSites, onTurnOn = {}, onPasskeyClick = { _, _ -> })
}

@WatchPreview
@Composable
fun PasskeyDetail() = DialogPreviewFrame {
    PasskeyDetailContent(previewSites[1].site, previewSites[1].passkeys[0], onDelete = {})
}

@WatchPreview
@Composable
fun PasskeyDeleteConfirm() = DialogPreviewFrame {
    PasskeyDeleteConfirmContent(
        previewSites[0].site,
        previewSites[0].passkeys[0],
        onConfirm = {},
        onDismiss = {},
    )
}

@WatchPreview
@Composable
fun PasswordlessConfirm() = DialogPreviewFrame {
    PasswordlessConfirmContent(onConfirm = {}, onDismiss = {})
}
