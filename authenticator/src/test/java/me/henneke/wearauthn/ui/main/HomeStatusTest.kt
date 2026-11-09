package me.henneke.wearauthn.ui.main

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

class HomeStatusTest : StringSpec({

    val thinkPad = Host("00:11:22:33:44:55", "ThinkPad X1", DeviceKind.Computer, Link.Detached)
    val ryzen = Host("66:77:88:99:AA:BB", "ryzen-shine", DeviceKind.Computer, Link.Detached)

    fun home(bluetooth: BluetoothState) =
        HomeState(bluetooth, NfcState.On, PasskeysState.Available)

    fun on(vararg hosts: Host, visibleSecondsLeft: Int? = null) =
        home(BluetoothState.On(hosts.toList(), appRegistered = true, visibleSecondsLeft))

    "a blocked radio outranks everything else" {
        home(BluetoothState.NeedsPermission).status() shouldBe HomeStatus.NeedsPermission
        home(BluetoothState.Off).status() shouldBe HomeStatus.BluetoothOff
        home(BluetoothState.TurningOn).status() shouldBe HomeStatus.BluetoothStarting
    }

    "pairing outranks an attached computer" {
        on(thinkPad.copy(link = Link.Attached), visibleSecondsLeft = 52).status() shouldBe
            HomeStatus.Pairing(52)
    }

    "an attached computer outranks one that is attaching" {
        on(ryzen.copy(link = Link.Attaching), thinkPad.copy(link = Link.Attached)).status() shouldBe
            HomeStatus.Attached(thinkPad.copy(link = Link.Attached))
    }

    "an attaching computer outranks idle ones" {
        on(thinkPad, ryzen.copy(link = Link.Attaching)).status() shouldBe
            HomeStatus.Attaching(ryzen.copy(link = Link.Attaching))
    }

    "bonded computers without a link are ready, none at all are not" {
        on(thinkPad, ryzen.copy(link = Link.Detaching)).status() shouldBe HomeStatus.Ready
        on().status() shouldBe HomeStatus.NoComputers
    }
})
