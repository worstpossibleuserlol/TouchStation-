# TouchStation

TouchStation is an Android frontend prototype for a planned PlayStation 1 emulator. It targets ARM64 and ARM32 Android devices, including phones using MediaTek Dimensity processors.

## Version 0.99

- Native Android library and settings screens.
- Grey #999999 interface with white typography.
- Locally saved preferences for compatibility, graphics, audio, controls, system, save data, library and advanced settings.
- User-selected game image and BIOS file locations are remembered.
- Launcher artwork is the TouchStation grey-and-white wordmark.

## Emulator status

**This build is still a frontend prototype. A PS1 emulation core is not integrated.** The settings screens save preferences locally, but those preferences are not applied to game execution yet. Selecting a BIOS or game file does not start a game.

A complete PS1 emulator still needs a compatible emulation core and integration for:
- MIPS R3000A CPU execution, timing, interrupts and DMA.
- GPU command processing, texture/framebuffer handling, display timing and video output.
- SPU audio, CD-ROM controller, CD audio, and synchronization.
- BIOS loading, region selection and legal firmware handling.
- Game-disc parsing including track layouts, cue sheets, multi-disc playlists and supported compressed formats.
- Digital and DualShock input, touch controls, USB/Bluetooth gamepads, analog axes and vibration.
- Two memory-card ports, virtual memory-card files, save states and safe persistence.
- Per-game compatibility profiles, renderer backends, scaling, frame pacing and diagnostics.
- Android lifecycle handling, audio focus, storage permissions, thermal/performance behavior and device testing.

## Android targets

- Minimum Android version: Android 8.0 (API 26)
- Target SDK: 35
- ABIs: arm64-v8a, armeabi-v7a
- Application ID: com.touchstation.app

## Build

The GitHub Actions workflow Build TouchStation debug APK builds a debug APK when manually started or when relevant source files are pushed to the main branch. The artifact is named TouchStation-debug-APK.

TouchStation does not bundle commercial games or PlayStation BIOS firmware. Use only files you are legally entitled to use.
