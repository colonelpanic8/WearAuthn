package me.henneke.wearauthn.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.toRoborazziComposeOptions
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import sergio.sastre.composable.preview.scanner.android.AndroidComposablePreviewScanner
import sergio.sastre.composable.preview.scanner.android.AndroidPreviewInfo
import sergio.sastre.composable.preview.scanner.android.screenshotid.AndroidPreviewScreenshotIdBuilder
import sergio.sastre.composable.preview.scanner.core.preview.ComposablePreview

/**
 * Renders every @Preview under me.henneke.wearauthn.ui to authenticator/build/screenshots.
 * Run it with `just screenshots`; plain test runs exclude it.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class PreviewScreenshots(private val preview: ComposablePreview<AndroidPreviewInfo>) {

    @Test
    fun render() {
        val name = AndroidPreviewScreenshotIdBuilder(preview)
            .ignoreClassName()
            .ignoreIdFor("device")
            .ignoreIdFor("showBackground")
            .ignoreIdFor("backgroundColor")
            .build()
        captureRoboImage(
            filePath = "build/screenshots/$name.png",
            roborazziComposeOptions = preview.toRoborazziComposeOptions(),
        ) {
            // The watch face is round; paint the corners so the clip is visible.
            Box(Modifier.fillMaxSize().background(Color(0xFF3C3C3C))) {
                Box(Modifier.fillMaxSize().clip(CircleShape).background(Color.Black)) {
                    CompositionLocalProvider(LocalInspectionMode provides true) { preview() }
                }
            }
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun previews(): List<ComposablePreview<AndroidPreviewInfo>> =
            AndroidComposablePreviewScanner()
                .scanPackageTrees("me.henneke.wearauthn.ui")
                .getPreviews()
    }
}
