package me.henneke.wearauthn.complication

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.support.wearable.complications.ComplicationProviderService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.wear.compose.material3.AppScaffold
import me.henneke.wearauthn.bthid.canUseAuthenticator
import me.henneke.wearauthn.bthid.identifier
import me.henneke.wearauthn.ui.ShortcutPickerScreen
import me.henneke.wearauthn.ui.bluetoothAdapter
import me.henneke.wearauthn.ui.hasBluetoothPermissions
import me.henneke.wearauthn.ui.main.Host
import me.henneke.wearauthn.ui.main.Link
import me.henneke.wearauthn.ui.main.kind
import me.henneke.wearauthn.ui.theme.WearAuthnTheme

class ComplicationConfigActivity : ComponentActivity() {

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val complicationId =
            intent.extras?.getInt(ComplicationProviderService.EXTRA_CONFIG_COMPLICATION_ID, -1)
                ?.takeIf { it != -1 }
        if (complicationId == null) {
            finish()
            return
        }
        val hosts = (if (hasBluetoothPermissions) bluetoothAdapter?.bondedDevices.orEmpty() else emptySet())
            .filter { it.canUseAuthenticator }
            .map { Host(it.address, it.identifier, it.kind, Link.Detached) }
            .sortedBy { it.name.lowercase() }
        setContent {
            WearAuthnTheme {
                AppScaffold {
                    ShortcutPickerScreen(hosts) { host ->
                        ShortcutComplicationProviderService.setDeviceShortcut(
                            this@ComplicationConfigActivity,
                            complicationId,
                            host.address
                        )
                        setResult(Activity.RESULT_OK)
                        finish()
                    }
                }
            }
        }
    }
}
