package me.henneke.wearauthn.ui.prompt

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.tooling.preview.devices.WearDevices
import me.henneke.wearauthn.R
import me.henneke.wearauthn.fido.context.PromptContent
import me.henneke.wearauthn.ui.DialogPreviewFrame
import me.henneke.wearauthn.ui.PIXEL_WATCH_3_45MM
import me.henneke.wearauthn.ui.WatchPreview
import me.henneke.wearauthn.ui.components.ListButton
import me.henneke.wearauthn.ui.components.breakableHost

@Composable
fun ApproveScreen(
    content: PromptContent,
    remaining: () -> Float,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
) {
    DecisionScaffold(remaining, approveEnabled = true, onApprove = onApprove, onDeny = onDeny) {
        item {
            Icon(
                painterResource(R.drawable.ic_key),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp).size(22.dp),
            )
        }
        if (content.site != null) {
            item { Secondary(content.action) }
            item { Site(content.site) }
            val siteName = content.siteName
            if (siteName != null && !siteName.equals(content.site, ignoreCase = true)) {
                item { Secondary("“$siteName”") }
            }
        } else {
            item {
                Text(
                    content.action,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (content.account != null) {
            item { Secondary(stringResource(R.string.prompt_as_account, content.account)) }
        }
        for (note in content.notes) {
            item {
                Text(
                    note,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Shows [text] exactly as the site sent it. The line breaks the layout puts in are part of what
 * the user approves, so Approve waits until they are known.
 */
@Composable
fun TransactionScreen(
    site: String,
    text: String,
    remaining: () -> Float,
    onApprove: (lineBreaks: List<Int>) -> Unit,
    onDeny: () -> Unit,
) {
    var lineBreaks by remember(text) { mutableStateOf<List<Int>?>(null) }
    DecisionScaffold(
        remaining,
        approveEnabled = lineBreaks != null,
        onApprove = { lineBreaks?.let(onApprove) },
        onDeny = onDeny,
    ) {
        item { Site(site, maxFontSize = 18.sp) }
        item {
            Text(
                text,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                onTextLayout = { layout ->
                    lineBreaks = (0 until layout.lineCount - 1).map { layout.getLineEnd(it) }
                },
            )
        }
    }
}

@Composable
fun ChooseAccountScreen(site: String?, accounts: List<AccountChoice>, onChoose: (Int) -> Unit) {
    val state = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = state) { padding ->
        TransformingLazyColumn(state = state, contentPadding = padding) {
            item {
                Secondary(
                    stringResource(
                        if (site != null) R.string.prompt_choose_account_for
                        else R.string.prompt_choose_account
                    )
                )
            }
            if (site != null) {
                item { Site(site, maxFontSize = 18.sp) }
            }
            for ((index, account) in accounts.withIndex()) {
                item {
                    ListButton(
                        label = account.title,
                        spec = spec,
                        onClick = { onChoose(index) },
                        icon = painterResource(R.drawable.ic_person),
                        secondaryLabel = account.subtitle,
                    )
                }
            }
        }
    }
}

/**
 * Scrolling content above Deny and Approve, which stay put at the bottom, inside a ring that
 * drains as the time to answer runs out.
 */
@Composable
private fun DecisionScaffold(
    remaining: () -> Float,
    approveEnabled: Boolean,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    content: TransformingLazyColumnScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val buttonHeight = 52.dp
        val bottomInset = maxHeight * 0.1f
        val contentPadding = PaddingValues(
            start = maxWidth * 0.12f,
            end = maxWidth * 0.12f,
            top = maxHeight * 0.1f,
            bottom = bottomInset + buttonHeight + 4.dp,
        )
        val state = rememberTransformingLazyColumnState()
        ScreenScaffold(scrollState = state, scrollIndicator = null) {
            TransformingLazyColumn(
                state = state,
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                content = content,
            )
        }
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .height(bottomInset + buttonHeight + 12.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.25f to MaterialTheme.colorScheme.background,
                    )
                )
        )
        DecisionButtons(
            approveEnabled,
            onApprove,
            onDeny,
            denyWidth = maxWidth * 0.26f,
            approveWidth = maxWidth * 0.4f,
            height = buttonHeight,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomInset),
        )
        CircularProgressIndicator(
            progress = remaining,
            modifier = Modifier.fillMaxSize().padding(3.dp),
            colors = ProgressIndicatorDefaults.colors(
                trackColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
            strokeWidth = 5.dp,
        )
    }
}

@Composable
private fun DecisionButtons(
    approveEnabled: Boolean,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    denyWidth: Dp,
    approveWidth: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalIconButton(onClick = onDeny, modifier = Modifier.size(denyWidth, height)) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.generic_deny))
        }
        FilledIconButton(
            onClick = onApprove,
            modifier = Modifier.size(approveWidth, height),
            enabled = approveEnabled,
        ) {
            Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.generic_accept))
        }
    }
}

@Composable
private fun Secondary(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/** The site, as large as fits: it wraps after dots and shrinks rather than being cut off. */
@Composable
private fun Site(site: String, maxFontSize: TextUnit = 24.sp) {
    BasicText(
        breakableHost(site),
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.titleLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        ),
        maxLines = 3,
        autoSize = HostAutoSize(minFontSize = 12.sp, maxFontSize = maxFontSize),
    )
}

/**
 * The largest size at which a [breakableHost] fits and every line ends after a dot. It takes a
 * third line only once two would need a size below [TWO_LINE_MIN_SP], so that a long host leaves
 * room for the account under it.
 */
private data class HostAutoSize(val minFontSize: TextUnit, val maxFontSize: TextUnit) : TextAutoSize {
    override fun TextAutoSizeLayoutScope.getFontSize(
        constraints: Constraints,
        text: AnnotatedString,
    ): TextUnit {
        fun fits(size: Float, maxLines: Int): Boolean {
            val layout = performLayout(constraints, text, size.sp)
            val breaksAfterDots = (0 until layout.lineCount - 1).all { line ->
                text.getOrNull(layout.getLineEnd(line) - 1) == '\u200B'
            }
            return !layout.hasVisualOverflow && breaksAfterDots && layout.lineCount <= maxLines
        }

        fun largest(floor: Float, maxLines: Int): Float? =
            generateSequence(maxFontSize.value) { it - 1f }
                .takeWhile { it >= floor }
                .firstOrNull { fits(it, maxLines) }

        val size = largest(maxOf(TWO_LINE_MIN_SP, minFontSize.value), maxLines = 2)
            ?: largest(minFontSize.value, maxLines = 3)
            ?: minFontSize.value
        return size.sp
    }
}

private const val TWO_LINE_MIN_SP = 16f

@Composable
private fun SignInPreview() = DialogPreviewFrame {
    ApproveScreen(
        PromptContent(
            action = stringResource(R.string.prompt_action_sign_in_to),
            site = "github.com",
            siteName = null,
            account = "colonelpanic8",
            notes = emptyList(),
        ),
        remaining = { 2f / 3 },
        onApprove = {},
        onDeny = {},
    )
}

@WatchPreview
@Preview(device = WearDevices.SMALL_ROUND, showBackground = true, backgroundColor = 0xFF000000, name = "small")
@Preview(device = PIXEL_WATCH_3_45MM, showBackground = true, backgroundColor = 0xFF000000, fontScale = 1.24f, name = "large-font")
@Preview(device = PIXEL_WATCH_3_45MM, showBackground = true, backgroundColor = 0xFF000000, locale = "de", name = "de")
@Composable
fun PromptSignIn() = SignInPreview()

@WatchPreview
@Composable
fun PromptSignInNotes() = DialogPreviewFrame {
    ApproveScreen(
        PromptContent(
            action = stringResource(R.string.prompt_action_sign_in_to),
            site = "login.microsoftonline.com",
            siteName = "Microsoft",
            account = "ivan@example.com",
            notes = listOf(
                stringResource(R.string.prompt_note_authenticate_user_verification),
                stringResource(R.string.prompt_note_authenticate_resident_key),
            ),
        ),
        remaining = { 2f / 3 },
        onApprove = {},
        onDeny = {},
    )
}

@WatchPreview
@Composable
fun PromptRegister() = DialogPreviewFrame {
    ApproveScreen(
        PromptContent(
            action = stringResource(R.string.prompt_action_register_with),
            site = "github.com",
            siteName = "GitHub",
            account = "colonelpanic8",
            notes = emptyList(),
        ),
        remaining = { 2f / 3 },
        onApprove = {},
        onDeny = {},
    )
}

@WatchPreview
@Composable
fun PromptNoSite() = DialogPreviewFrame {
    ApproveScreen(
        PromptContent(
            action = stringResource(R.string.prompt_platform_get_touch),
            site = null,
            siteName = null,
            account = null,
            notes = emptyList(),
        ),
        remaining = { 2f / 3 },
        onApprove = {},
        onDeny = {},
    )
}

@WatchPreview
@Composable
fun PromptTransaction() = DialogPreviewFrame {
    TransactionScreen(
        site = "github.com",
        text = "Transfer ownership of colonelpanic8/dotfiles to railbird. " +
            "Collaborators keep their access. This cannot be undone from the watch.",
        remaining = { 2f / 3 },
        onApprove = {},
        onDeny = {},
    )
}

@WatchPreview
@Composable
fun PromptChooseAccount() = DialogPreviewFrame {
    ChooseAccountScreen(
        site = "login.microsoftonline.com",
        accounts = listOf(
            AccountChoice("Ivan Malison", "ivan@example.com"),
            AccountChoice("colonelpanic8", "Created Nov 3, 2026"),
            AccountChoice("Account #3", "Created Sep 30, 2026"),
        ),
        onChoose = {},
    )
}
