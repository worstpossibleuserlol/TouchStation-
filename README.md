# TouchStation

TouchStation is an Android PlayStation 1 emulator frontend. Version **1.0.1 Alpha** bundles the PCSX-ReARMed libretro core for ARM64, ARM32, x86-64 and x86 Android devices.

## Version 1.0.1 Alpha

- Bundled PCSX-ReARMed PS1 core (CPU, GPU, CD-ROM and SPU emulation).
- Android NDK builds for `arm64-v8a`, `armeabi-v7a`, `x86_64` and `x86`.
- Native libretro bridge for video frames, stereo audio, controller input, core options and save states.
- Slate `#111923` interface with muted teal `#63C6B2` accents, a refreshed game library and redesigned in-game quick menu/controller layout.
- Game file import and whole-disc-folder import for CUE/BIN and multi-disc layouts.
- External BIOS import into the app's private system directory.
- On-screen directional pad and PlayStation face buttons.
- In-game pause, reset, save-state and load-state controls, with ten rotating save slots and a landscape console menu.
- Controller mode switching between Digital, Analog and DualShock from the in-game menu.
- Settings wired to PCSX-ReARMed options: dynamic recompiler, CPU clock, region, CD read-ahead, frameskip, dithering, threaded rendering, enhanced resolution, RGB32 output and memory card slot 2.
- Memory-card data stored in the app's private save directory.

## Upstream core and license

TouchStation bundles **PCSX-ReARMed** from:

- Source: https://github.com/notaz/pcsx_rearmed
- Pinned source commit: `7838055e892061f3f5775ff9f6bb3d3d9c7fe742`
- License: GNU General Public License version 2 (GPL-2.0)
- License text: `THIRD_PARTY_LICENSES/PCSX-ReARMed-COPYING`

The upstream source and its submodules are fetched at the pinned revision by the Android workflow. The PCSX-ReARMed core is a third-party project; TouchStation does not claim authorship of that core. Review the upstream source and license before redistributing modified builds.

## BIOS and games

No PlayStation BIOS or commercial game is included. The app lets you import a BIOS file and game-disc files that you are legally entitled to use. Some games need the full disc folder so the CUE sheet can find its BIN tracks and related files.

PCSX-ReARMed can fall back to its built-in HLE BIOS when an external BIOS is unavailable, but the libretro documentation recommends a compatible user-provided BIOS for better compatibility. Supported content includes CUE, BIN, ISO, IMG, CHD, PBP, M3U, MDF, TOC and other core-supported formats.

## Android targets

- Minimum Android: Android 8.0 (API 26)
- Target SDK: 35
- ABIs: `arm64-v8a`, `armeabi-v7a`
- Application ID: `com.touchstation.app`
- Version code: `101`
- Version name: `1.0.1 Alpha`

## Build

The GitHub Actions workflow builds the pinned PCSX-ReARMed core with the Android NDK for all four Android ABIs, packages the native libraries, and then builds the debug APK.

A local build requires Android SDK 35, Android NDK `27.2.12479018`, CMake `3.22.1`, Gradle, Git, and the PCSX-ReARMed source checked out with its submodules at the pinned commit. Place the source at `third_party/pcsx_rearmed` before running `gradle assembleDebug`.

The universal APK contains the four ARM and x86 core libraries. Its actual size is reported by the build; TouchStation does not add filler data just to inflate the package.

## Experimental PS3 edition

TouchStation PS3 1.0.1 Alpha is developed separately in the ps3-1.0.1-alpha branch under the :ps3-app module.

It adds PS3 PUP container import/validation, a native PPU guest-call to RSX RGBA-frame demo, a PPU-triggered SPU WRCH-to-PCM audio demo, fullscreen output without touch controls, PUP parser tests, and the uploaded square logo as the Android launcher icon.

**This is a development vertical slice, not a complete or retail-game-compatible PS3 emulator.** The RSX path is still a CPU clear/scissor reference, SPU is a limited instruction subset without MFC/DMA, PPU is a limited PPC64 interpreter, and the guest-call ABI is a TouchStation demo ABI rather than PS3 LV2. PUP import stores and validates the user-supplied container; it does not decrypt or install firmware. Retail SELF/ISO boot and commercial-game compatibility are not implemented.

Read [README-PS3-1.0.1-ALPHA.md](README-PS3-1.0.1-ALPHA.md) for implementation details and limits. The PS1 application remains separate.

No Sony firmware, BIOS, encryption keys or commercial games are bundled. Use only files you are legally entitled to use.
