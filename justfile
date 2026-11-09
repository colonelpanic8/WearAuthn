set shell := ["bash", "-euo", "pipefail", "-c"]

default:
    @just --list

fmt:
    alejandra flake.nix

fmt-check:
    alejandra --check flake.nix

test:
    ./gradlew :authenticator:testNightlyDebugUnitTest

check: fmt-check test

build:
    ./gradlew :authenticator:assembleNightlyDebug

install:
    adb install -r authenticator/build/outputs/apk/nightly/debug/authenticator-nightly-debug.apk

logs:
    adb logcat --pid="$(adb shell pidof me.henneke.wearauthn.authenticator.nightly)"

# Render every @Preview under ui/ to authenticator/build/screenshots.
screenshots:
    ./gradlew :authenticator:testNightlyDebugUnitTest -Pscreenshots
    @ls authenticator/build/screenshots
