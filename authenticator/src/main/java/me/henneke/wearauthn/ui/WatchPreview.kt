package me.henneke.wearauthn.ui

import androidx.compose.ui.tooling.preview.Preview

/** Pixel Watch 3, 45 mm: 456x456 px at 320 dpi. */
const val PIXEL_WATCH_3_45MM = "spec:width=456px,height=456px,dpi=320,isRound=true"

@Preview(device = PIXEL_WATCH_3_45MM, showBackground = true, backgroundColor = 0xFF000000)
annotation class WatchPreview
