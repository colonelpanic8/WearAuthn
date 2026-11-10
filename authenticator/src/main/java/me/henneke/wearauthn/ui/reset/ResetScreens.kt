package me.henneke.wearauthn.ui.reset

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.LocalReduceMotion
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AlertDialogContent
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ConfirmationDialogDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SuccessConfirmationDialogContent
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.confirmationDialogCurvedText
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import me.henneke.wearauthn.R
import me.henneke.wearauthn.ui.DialogPreviewFrame
import me.henneke.wearauthn.ui.WatchPreview
import me.henneke.wearauthn.ui.components.BusyIndicator
import me.henneke.wearauthn.ui.components.ListText
import me.henneke.wearauthn.ui.components.ScreenList

/** The first question: a dialog with the destructive answer on the right. */
@Composable
fun ResetAskContent(onConfirm: () -> Unit, onDismiss: () -> Unit) {
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
        title = { Text(stringResource(R.string.reset_ask_title)) },
        dismissButton = { AlertDialogDefaults.DismissButton(onClick = onDismiss) },
        transformationSpec = rememberTransformationSpec(),
        text = { Text(stringResource(R.string.reset_ask_text)) },
    )
}

/**
 * The second question, shaped unlike the first so that a double tap cannot answer both: the
 * button only comes into view after scrolling past the warning.
 */
@Composable
fun ResetConfirmScreen(
    onConfirm: () -> Unit,
    listState: TransformingLazyColumnState = rememberTransformingLazyColumnState(),
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    ScreenList(
        state = listState,
        edgeButton = {
            EdgeButton(
                onClick = onConfirm,
                buttonSize = EdgeButtonSize.Medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(stringResource(R.string.reset_confirm_button), textAlign = TextAlign.Center)
            }
        },
    ) { spec ->
        item {
            Text(
                stringResource(R.string.reset_confirm_title),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
        item { ListText(stringResource(R.string.reset_confirm_text), spec) }
        item { Spacer(Modifier.height(screenHeight / 2)) }
    }
}

@Composable
fun ResetBusyScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        BusyIndicator(Modifier.size(48.dp))
    }
}

@Composable
fun ResetDoneContent() {
    val message = stringResource(R.string.message_deleted_all_data)
    val style = ConfirmationDialogDefaults.curvedTextStyle
    SuccessConfirmationDialogContent(curvedText = { confirmationDialogCurvedText(message, style) })
}

@WatchPreview
@Composable
fun ResetAsk() = DialogPreviewFrame { ResetAskContent(onConfirm = {}, onDismiss = {}) }

@WatchPreview
@Composable
fun ResetConfirm() = DialogPreviewFrame {
    ResetConfirmScreen(onConfirm = {}, listState = rememberTransformingLazyColumnState(initialAnchorItemIndex = 2))
}

@WatchPreview
@Composable
fun ResetDone() = DialogPreviewFrame {
    // Reduced motion shows the dialog in its settled state rather than its first frame.
    CompositionLocalProvider(LocalReduceMotion provides true) { ResetDoneContent() }
}
