package me.henneke.wearauthn.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

enum class HeroTone { Live, Calm, Warn }

/** The status block at the top of a screen: icon, a short title and one supporting line. */
@Composable
fun Hero(
    icon: Painter,
    title: String,
    supporting: AnnotatedString?,
    tone: HeroTone,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (tone) {
        HeroTone.Live -> colors.primary to colors.onPrimary
        HeroTone.Calm -> colors.primaryContainer to colors.onPrimaryContainer
        HeroTone.Warn -> colors.surfaceContainerHigh to colors.tertiary
    }
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(container),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(24.dp))
            }
            if (busy) {
                BusyIndicator(Modifier.size(60.dp))
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            textAlign = TextAlign.Center,
        )
        if (supporting != null) {
            Text(
                supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** An indeterminate spinner, drawn still in previews, which never settle on an endless animation. */
@Composable
fun BusyIndicator(modifier: Modifier = Modifier) {
    if (LocalInspectionMode.current) {
        CircularProgressIndicator(progress = { 0.3f }, modifier = modifier, strokeWidth = 3.dp)
    } else {
        CircularProgressIndicator(modifier, strokeWidth = 3.dp)
    }
}
