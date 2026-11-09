package me.henneke.wearauthn.ui.main

enum class DeviceKind { Computer, Phone, Watch, Audio, Other }

enum class Link { Detached, Attaching, Attached, Detaching }

data class Host(val address: String, val name: String, val kind: DeviceKind, val link: Link)

sealed interface BluetoothState {
    data object NeedsPermission : BluetoothState

    /** Also stands for a watch without a Bluetooth adapter. */
    data object Off : BluetoothState

    data object TurningOn : BluetoothState

    data class On(
        val hosts: List<Host>,
        val appRegistered: Boolean,
        val visibleSecondsLeft: Int?,
    ) : BluetoothState
}

enum class NfcState { Absent, Off, On }

sealed interface PasskeysState {
    data class Active(val count: Int) : PasskeysState

    data object Available : PasskeysState

    data object NeedsScreenLock : PasskeysState

    data class NeedsReset(val count: Int) : PasskeysState
}

data class HomeState(
    val bluetooth: BluetoothState,
    val nfc: NfcState,
    val passkeys: PasskeysState,
)

sealed interface HomeStatus {
    data object NeedsPermission : HomeStatus

    data object BluetoothOff : HomeStatus

    data object BluetoothStarting : HomeStatus

    data class Pairing(val secondsLeft: Int) : HomeStatus

    data class Attached(val host: Host) : HomeStatus

    data class Attaching(val host: Host) : HomeStatus

    data object Ready : HomeStatus

    data object NoComputers : HomeStatus
}

fun HomeState.status(): HomeStatus =
    when (val bluetooth = bluetooth) {
        BluetoothState.NeedsPermission -> HomeStatus.NeedsPermission
        BluetoothState.Off -> HomeStatus.BluetoothOff
        BluetoothState.TurningOn -> HomeStatus.BluetoothStarting
        is BluetoothState.On -> {
            val attached = bluetooth.hosts.firstOrNull { it.link == Link.Attached }
            val attaching = bluetooth.hosts.firstOrNull { it.link == Link.Attaching }
            when {
                bluetooth.visibleSecondsLeft != null ->
                    HomeStatus.Pairing(bluetooth.visibleSecondsLeft)
                attached != null -> HomeStatus.Attached(attached)
                attaching != null -> HomeStatus.Attaching(attaching)
                bluetooth.hosts.isNotEmpty() -> HomeStatus.Ready
                else -> HomeStatus.NoComputers
            }
        }
    }
