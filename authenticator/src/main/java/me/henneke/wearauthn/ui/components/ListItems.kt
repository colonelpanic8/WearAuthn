package me.henneke.wearauthn.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight

/**
 * How loudly a row asks to be tapped. Each screen has at most one [Primary] row; rows that act
 * are louder than rows that only open another screen.
 */
enum class RowStyle { Primary, Action, Navigate, Outlined }

/** A scrolling screen: the scaffold, its list, and the transformation every row shares. */
@Composable
fun ScreenList(
    modifier: Modifier = Modifier,
    state: TransformingLazyColumnState = rememberTransformingLazyColumnState(),
    edgeButton: (@Composable BoxScope.() -> Unit)? = null,
    content: TransformingLazyColumnScope.(TransformationSpec) -> Unit,
) {
    val spec = rememberTransformationSpec()
    val list: @Composable BoxScope.(PaddingValues) -> Unit =
        { padding ->
            TransformingLazyColumn(state = state, contentPadding = padding) { content(spec) }
        }
    if (edgeButton == null) {
        ScreenScaffold(scrollState = state, modifier = modifier, content = list)
    } else {
        ScreenScaffold(
            scrollState = state,
            edgeButton = edgeButton,
            modifier = modifier,
            content = list,
        )
    }
}

@Composable
fun TransformingLazyColumnItemScope.ScreenTitle(text: String, spec: TransformationSpec) {
    ListHeader(
        modifier = Modifier.transformedHeight(this, spec),
        transformation = SurfaceTransformation(spec),
    ) {
        Text(text, textAlign = TextAlign.Center)
    }
}

/** A small label above a group of rows; lighter than a [ListHeader], which suits screen titles. */
@Composable
fun TransformingLazyColumnItemScope.SectionHeader(text: String, spec: TransformationSpec) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec)
            .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
fun TransformingLazyColumnItemScope.ListButton(
    label: String,
    spec: TransformationSpec,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    secondaryLabel: String? = null,
    style: RowStyle = RowStyle.Navigate,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().transformedHeight(this, spec),
        enabled = enabled,
        colors = when (style) {
            RowStyle.Primary -> ButtonDefaults.buttonColors()
            RowStyle.Action -> ButtonDefaults.filledVariantButtonColors()
            RowStyle.Navigate -> ButtonDefaults.filledTonalButtonColors()
            RowStyle.Outlined -> ButtonDefaults.outlinedButtonColors()
        },
        border = if (style == RowStyle.Outlined) {
            ButtonDefaults.outlinedButtonBorder(enabled)
        } else {
            null
        },
        transformation = SurfaceTransformation(spec),
        icon = icon?.let { { Icon(it, contentDescription = null) } },
        secondaryLabel = secondaryLabel?.let { { Text(it) } },
        label = { Text(label) },
    )
}

/** Plain text inside a list, for explanations between rows. */
@Composable
fun TransformingLazyColumnItemScope.ListText(
    text: String,
    spec: TransformationSpec,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().transformedHeight(this, spec)) {
        Text(
            text,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
