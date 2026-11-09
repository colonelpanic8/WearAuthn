{
  description = "Use a Wear OS watch as a FIDO2 security key via Bluetooth and NFC";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = {
    self,
    nixpkgs,
    flake-utils,
  }:
    flake-utils.lib.eachDefaultSystem (system: let
      pkgs = import nixpkgs {
        inherit system;
        config = {
          allowUnfree = true;
          android_sdk.accept_license = true;
        };
      };
      buildToolsVersion = "35.0.0";
      androidComposition = pkgs.androidenv.composeAndroidPackages {
        buildToolsVersions = [buildToolsVersion];
        platformVersions = ["34" "35"];
        includeEmulator = false;
        includeSources = false;
        includeSystemImages = false;
        includeNDK = false;
        useGoogleAPIs = false;
      };
      androidHome = "${androidComposition.androidsdk}/libexec/android-sdk";
      jdk = pkgs.jdk17;
      androidShell = pkgs.mkShell {
        packages = with pkgs; [
          alejandra
          androidComposition.androidsdk
          gh
          git
          jdk
          just
          libfido2
        ];
        ANDROID_HOME = androidHome;
        ANDROID_SDK_ROOT = androidHome;
        JAVA_HOME = jdk.home;
        GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${androidHome}/build-tools/${buildToolsVersion}/aapt2";
        shellHook = ''
          export PATH=${androidHome}/build-tools/${buildToolsVersion}:${androidHome}/platform-tools:$PATH
        '';
      };
    in {
      packages.default = pkgs.writeShellApplication {
        name = "wearauthn-build";
        runtimeInputs = [jdk];
        text = ''
          export ANDROID_HOME=${androidHome}
          exec ./gradlew :authenticator:assembleNightlyDebug "$@"
        '';
      };
      devShells = {
        default = androidShell;
        android = androidShell;
      };
      formatter = pkgs.alejandra;
    });
}
