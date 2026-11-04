# TouchStation

TouchStation is an Android frontend prototype for a planned PlayStation 1 emulator. It targets ARM64 and ARM32 Android devices, including phones using MediaTek Dimensity processors.

## Current status

**Version:** 0.1.1 Alpha  
**Status:** Android frontend prototype; PS1 emulation core not yet integrated.

The app currently provides a native Android library screen and document pickers for user-provided game images and BIOS files. Selecting a file does not start emulation yet.

## Build

The GitHub Actions workflow **Build TouchStation debug APK** builds a debug APK when manually started from the Actions tab or when relevant source files are pushed to the main branch.

The artifact is named `TouchStation-debug-APK`. Download the artifact ZIP from the completed workflow run and extract `app-debug.apk`.

## Android targets

- Minimum Android version: Android 8.0 (API 26)
- Target SDK: 35
- ABIs: `arm64-v8a`, `armeabi-v7a`
- Application ID: `com.touchstation.app`

## BIOS and game files

TouchStation does not include copyrighted PlayStation BIOS firmware or commercial games. Select only files you are legally entitled to use. BIOS selection is currently a frontend feature; BIOS loading by an emulator core is not implemented yet.

## Planned native core

A compatible open-source PS1 core still needs to be integrated with its license notices, Android NDK build, JNI bridge, video output, audio output, controller input, and save-state handling. No PS1 games can be run in this alpha.
