package me.henneke.wearauthn.ui.main

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.transformedHeight
import me.henneke.wearauthn.R
import me.henneke.wearauthn.ui.WatchPreview
import me.henneke.wearauthn.ui.WatchPreviewFrame
import me.henneke.wearauthn.ui.components.ListButton
import me.henneke.wearauthn.ui.components.ListText
import me.henneke.wearauthn.ui.components.RowStyle
import me.henneke.wearauthn.ui.components.ScreenList
import me.henneke.wearauthn.ui.components.ScreenTitle
import me.henneke.wearauthn.ui.components.styledStringResource

enum class KeyStorage { Scanning, Hardware, Software }

data class AboutState(val version: String, val keyStorage: KeyStorage, val supportPurchased: Boolean)

@Composable
fun AboutScreen(
    state: AboutState,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenLicenses: () -> Unit,
    onSupport: () -> Unit,
) {
    ScreenList { spec ->
        item { ScreenTitle(stringResource(R.string.app_name), spec) }
        item { ListText(stringResource(R.string.about_version, state.version), spec) }
        item {
            ListText(
                stringResource(
                    when (state.keyStorage) {
                        KeyStorage.Scanning -> R.string.message_key_storage_type_unknown
                        KeyStorage.Hardware -> R.string.message_key_storage_type_hardware
                        KeyStorage.Software -> R.string.message_key_storage_type_software
                    }
                ),
                spec,
            )
        }
        item {
            Text(
                styledStringResource(R.string.about_how_to_use),
                modifier = Modifier.fillMaxWidth().transformedHeight(this, spec)
                    .padding(horizontal = LONG_TEXT_INSET, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        item {
            ListButton(
                label = stringResource(R.string.label_privacy),
                spec = spec,
                onClick = onOpenPrivacyPolicy,
            )
        }
        item {
            ListButton(
                label = stringResource(R.string.label_licenses),
                spec = spec,
                onClick = onOpenLicenses,
            )
        }
        item {
            ListButton(
                label = stringResource(
                    if (state.supportPurchased) R.string.preference_support_purchased_title
                    else R.string.preference_support_not_purchased_title
                ),
                spec = spec,
                onClick = onSupport,
                icon = painterResource(R.drawable.ic_launcher_outline),
                secondaryLabel = stringResource(
                    if (state.supportPurchased) R.string.preference_support_purchased_summary
                    else R.string.preference_support_not_purchased_summary
                ),
                style = RowStyle.Action,
            )
        }
    }
}

// Left-aligned paragraphs run into the round bezel at the list's usual inset.
private val LONG_TEXT_INSET = 30.dp

/** A title over long plain text, such as the privacy policy. */
@Composable
fun TextScreen(title: String, text: String) {
    ScreenList { spec ->
        item { ScreenTitle(title, spec) }
        item {
            Text(
                AnnotatedString(text),
                modifier = Modifier.fillMaxWidth().transformedHeight(this, spec)
                    .padding(horizontal = LONG_TEXT_INSET),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@WatchPreview
@Composable
fun About() = WatchPreviewFrame {
    AboutScreen(
        state = AboutState("0.9.19", KeyStorage.Hardware, supportPurchased = false),
        onOpenPrivacyPolicy = {},
        onOpenLicenses = {},
        onSupport = {},
    )
}

@WatchPreview
@Composable
fun TextScreen() = WatchPreviewFrame {
    val resources = LocalContext.current.resources
    TextScreen(
        title = stringResource(R.string.label_privacy),
        text = resources.openRawResource(R.raw.privacy_policy).bufferedReader().use { it.readText() },
    )
}
