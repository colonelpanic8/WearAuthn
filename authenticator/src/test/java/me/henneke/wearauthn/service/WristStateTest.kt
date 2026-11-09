package me.henneke.wearauthn.service

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

class WristStateTest : StringSpec({

    "A watch that starts out off the wrist has not been taken off" {
        WristState().report(onWrist = false) shouldBe false
    }

    "Taking off a worn watch is reported once" {
        val wrist = WristState()
        wrist.report(onWrist = true) shouldBe false
        wrist.report(onWrist = false) shouldBe true
        wrist.report(onWrist = false) shouldBe false
    }

    "A watch put on after starting off the wrist can be taken off" {
        val wrist = WristState()
        wrist.report(onWrist = false) shouldBe false
        wrist.report(onWrist = true) shouldBe false
        wrist.report(onWrist = false) shouldBe true
    }
})
