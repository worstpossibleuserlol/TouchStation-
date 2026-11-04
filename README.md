# TouchStation

TouchStation is an Android frontend prototype for a planned PlayStation 1 emulator. It targets ARM64 and ARM32 Android devices, including phones using MediaTek Dimensity processors.

## Current status

**Version:** 0.1.0 Alpha  
**Status:** Frontend prototype only

The current build provides a native Android interface and a document picker for user-provided game images. It does **not** yet emulate or run PlayStation games. A PS1 emulation core, audio/video emulation, BIOS integration, and JNI bridge still need to be implemented.

## Build

The GitHub Actions workflow **Build TouchStation debug APK** builds a debug APK when manually started from the Actions tab or when relevant source files are pushed to the main branch.

The artifact is named `TouchStation-debug-APK`. Download the artifact ZIP from the completed workflow run and extract `app-debug.apk`.

## Android targets

- Minimum Android version: Android 8.0 (API 26)
- Target SDK: 35
- ABIs: `arm64-v8a`, `armeabi-v7a`
- Application ID: `com.touchstation.app`

## Game files and BIOS

Only use game images and BIOS files that you are legally entitled to use. BIOS support is not implemented in this frontend preview.

## Native core

The `app/src/main/cpp` directory documents the planned native integration point. Any future emulator core must be integrated with its license and notices respected.
