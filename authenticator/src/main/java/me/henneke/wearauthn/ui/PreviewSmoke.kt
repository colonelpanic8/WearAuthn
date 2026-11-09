package me.henneke.wearauthn.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AlertDialogContent
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TitleCard
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController

private val SmokeColors = ColorScheme(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF284A86),
    onPrimaryContainer = Color(0xFFD3E3FD),
)

@Composable
private fun SmokeTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = SmokeColors, content = content)

@Composable
private fun SmokeList() {
    AppScaffold {
        val listState = rememberTransformingLazyColumnState()
        val spec = rememberTransformationSpec()
        ScreenScaffold(
            scrollState = listState,
            edgeButton = {
                EdgeButton(onClick = {}, buttonSize = EdgeButtonSize.Medium) { Text("Pair") }
            },
        ) { contentPadding ->
            TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
                item {
                    ListHeader(
                        modifier = Modifier.transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                    ) {
                        Text("WearAuthn")
                    }
                }
                item {
                    Button(
                        onClick = {},
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                        icon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                        secondaryLabel = { Text("Bluetooth") },
                        label = { Text("Security key") },
                    )
                }
                item {
                    FilledTonalButton(
                        onClick = {},
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        label = { Text("Settings") },
                    )
                }
                item {
                    SwitchButton(
                        checked = true,
                        onCheckedChange = {},
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                        label = { Text("NFC") },
                    )
                }
                item {
                    TitleCard(
                        onClick = {},
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                        title = { Text("github.com") },
                        subtitle = { Text("alice") },
                    ) {
                        Text("Last used today")
                    }
                }
                item {
                    CircularProgressIndicator(progress = { 0.6f }, modifier = Modifier.size(48.dp))
                }
            }
        }
    }
}

@WatchPreview
@Composable
fun SmokeListPreview() = SmokeTheme { SmokeList() }

@WatchPreview
@Composable
fun SmokeAlertDialogPreview() = SmokeTheme {
    AlertDialogContent(
        icon = { Icon(Icons.Filled.Lock, contentDescription = null) },
        title = { Text("Sign in?") },
        text = { Text("github.com wants to use this security key.") },
        confirmButton = { AlertDialogDefaults.ConfirmButton(onClick = {}) },
        dismissButton = { AlertDialogDefaults.DismissButton(onClick = {}) },
    )
}

@WatchPreview
@Composable
fun SmokeNavHostPreview() = SmokeTheme {
    val navController = rememberSwipeDismissableNavController()
    SwipeDismissableNavHost(navController = navController, startDestination = "list") {
        composable("list") { SmokeList() }
    }
}
