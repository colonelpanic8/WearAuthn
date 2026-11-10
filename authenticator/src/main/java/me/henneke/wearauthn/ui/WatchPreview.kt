package me.henneke.wearauthn.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material3.AppScaffold
import me.henneke.wearauthn.ui.theme.WearAuthnTheme

/** Pixel Watch 3, 45 mm: 456x456 px at 320 dpi. */
const val PIXEL_WATCH_3_45MM = "spec:width=456px,height=456px,dpi=320,isRound=true"

@Preview(device = PIXEL_WATCH_3_45MM, showBackground = true, backgroundColor = 0xFF000000)
annotation class WatchPreview

/** The app's theme and scaffold around a screen, as the activities set them up. */
@Composable
fun WatchPreviewFrame(content: @Composable () -> Unit) {
    WearAuthnTheme { AppScaffold { content() } }
}

/** A dialog, which covers the app's scaffold and its time text. */
@Composable
fun DialogPreviewFrame(content: @Composable () -> Unit) {
    WearAuthnTheme { content() }
}
