package me.henneke.wearauthn.bthid.api28

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothProfile
import android.os.Build
import androidx.annotation.MainThread
import me.henneke.wearauthn.bthid.HidDeviceProfile
import me.henneke.wearauthn.bthid.canUseAuthenticatorViaBluetooth

/** Wrapper for BluetoothHidDevice profile that manages paired HID Host devices.  */
@TargetApi(Build.VERSION_CODES.P)
@SuppressLint("MissingPermission")
class HidDeviceProfile28(bluetoothAdapter: BluetoothAdapter) : HidDeviceProfile(bluetoothAdapter) {

    private val hidDevice: BluetoothHidDevice?
        get() = service as? BluetoothHidDevice

    override val connectedDevices: List<BluetoothDevice>
        get() = hidDevice?.connectedDevices ?: emptyList()
    override val profileId = BluetoothProfile.HID_DEVICE

    override fun getConnectionState(device: BluetoothDevice): Int {
        return hidDevice?.getConnectionState(device) ?: BluetoothProfile.STATE_DISCONNECTED
    }

    override fun connect(device: BluetoothDevice) {
        hidDevice?.run {
            if (device.canUseAuthenticatorViaBluetooth) {
                connect(device)
            }
        }
    }

    override fun disconnect(device: BluetoothDevice) {
        hidDevice?.run {
            if (device.canUseAuthenticatorViaBluetooth) {
                disconnect(device)
            }
        }
    }

    @MainThread
    override fun getDevicesMatchingConnectionStates(states: IntArray): List<BluetoothDevice> {
        return hidDevice?.getDevicesMatchingConnectionStates(states) ?: emptyList()
    }
}
