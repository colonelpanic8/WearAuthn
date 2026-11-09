package me.henneke.wearauthn.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.dynamicColorScheme

/** Seeded from the launcher icon: its blue as primary, its orange as tertiary. */
val FallbackColorScheme = ColorScheme(
    primary = Color(0xFFBDC2FF),
    primaryDim = Color(0xFFA3AAF2),
    primaryContainer = Color(0xFF2B3594),
    onPrimary = Color(0xFF0E1673),
    onPrimaryContainer = Color(0xFFDFE0FF),
    secondary = Color(0xFFC4C5DD),
    secondaryDim = Color(0xFFA9AAC1),
    secondaryContainer = Color(0xFF44465C),
    onSecondary = Color(0xFF2D2F42),
    onSecondaryContainer = Color(0xFFE0E1F9),
    tertiary = Color(0xFFFFB86E),
    tertiaryDim = Color(0xFFF0A050),
    tertiaryContainer = Color(0xFF6B3B00),
    onTertiary = Color(0xFF4A2800),
    onTertiaryContainer = Color(0xFFFFDCBD),
    surfaceContainerLow = Color(0xFF15161C),
    surfaceContainer = Color(0xFF202129),
    surfaceContainerHigh = Color(0xFF2B2C35),
    onSurface = Color(0xFFE4E1EC),
    onSurfaceVariant = Color(0xFFC6C5D2),
    outline = Color(0xFF8F8F9C),
    outlineVariant = Color(0xFF46464F),
)

@Composable
fun WearAuthnTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val inPreview = LocalInspectionMode.current
    val colorScheme = remember(context, inPreview) {
        (if (inPreview) null else dynamicColorScheme(context)) ?: FallbackColorScheme
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
