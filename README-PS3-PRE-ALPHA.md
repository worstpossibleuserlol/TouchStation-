# TouchStation PS3 — 1.0.0 Pre-Alpha

TouchStation PS3 is a separate Android frontend experiment in the existing TouchStation repository. The PS1 edition remains in :app; this PS3 pre-alpha is built from :ps3-app and on branch ps3-pre-alpha.

## What is in this build

- A separate Android application target named TouchStation PS3 (com.touchstation.ps3).
- A game-library screen that imports and remembers user-selected document URIs. It does not distribute firmware or games.
- Bluetooth pairing hand-off to Android Settings. Android exposes paired Bluetooth and wired gamepads through InputManager; TouchStation does not scan for Bluetooth devices or collect Bluetooth addresses.
- Connected controller enumeration and basic Android key / joystick event observation. There are no touchscreen controls or fake controller overlays.
- A control-free, immersive landscape game-output surface. The screen explicitly reports that the renderer is not connected; it does not pretend to run a game.
- Persistent configuration groups for PPU, SPU, RSX, display, crop, audio, performance, controller and storage preferences.
- Per-game layout, resolution and crop override preferences.
- A native C++17 PPU interpreter prototype with a small, explicit subset of big-endian PowerPC instructions, bounds-checked guest memory, an instruction budget and a native smoke test.
- A diagnostics page that runs the native PPU self-test from the Android app.

## Important compatibility statement

This is NOT a playable PS3 emulator. It does not boot PS3 firmware or commercial games. The PPU interpreter is a learning and engineering foundation, not a complete Cell PPU implementation. The following major pieces are not implemented: full PowerPC 64-bit ISA coverage, VMX/AltiVec, exceptions and privilege levels, MMU/address translation, LV1/LV2 hypervisors, syscall and firmware services, SPU execution, RSX GPU command processing, audio, storage / optical-disc emulation, save-state integration, and PS3 controller mapping into a guest runtime.

Settings marked planned, prototype or inactive are stored in Android preferences but cannot affect game performance before the matching runtime exists. Renderer selection such as Vulkan/OpenGL is configuration only; no RSX renderer is included. The 1.0.0 Pre-Alpha version does not include game controls because controller use is intended to be external only.

## Display layout and crop

Global display profiles include Keep aspect ratio, Stretch to full screen, Fit 4:3, Fit 16:9, Fill screen (crop), Integer scale and Native resolution. The crop controls store independent left, right, top and bottom percentages. Per-game profiles can override layout, resolution scale and crop behavior. The viewport settings are persisted now; they become visually active after a real frame renderer is integrated.

## Bluetooth controller setup

1. Open Controllers.
2. Select Open Bluetooth settings.
3. Pair the controller in Android's own Bluetooth screen.
4. Return to TouchStation PS3 and refresh connected controllers.

USB gamepads are enumerated the same way. Actual PS3 guest-pad mapping is not yet connected to the PPU prototype.

## Native PPU prototype

Implemented instructions:

- D-form integer operations: addi, addis, ori, oris, xori, andi. (integer result and CR0 update), cmpwi, lwz, stw.
- X-form integer operations: add, subf, or, xor, and.
- Unconditional branch b with relative / absolute and link behavior.
- Big-endian 8-, 32- and 64-bit memory read / write helpers.
- Safe stop on unsupported opcodes, unaligned fetches and out-of-bounds memory access.

This list is intentionally narrow. Unsupported instructions stop the prototype with a diagnostic instead of being silently approximated.

## Build

Requirements: JDK 17, Android SDK platform 35, Android NDK 27.2.12479018, CMake 3.22.1, and Gradle 8.9.

Run portable native tests on a desktop host:

~~~bash
cmake -S ps3-app/src/main/cpp -B build/ppu -DBUILD_TESTING=ON
cmake --build build/ppu --parallel
ctest --test-dir build/ppu --output-on-failure
~~~

Build the Android pre-alpha APK:

~~~bash
gradle :ps3-app:assembleDebug
~~~

APK output: ps3-app/build/outputs/apk/debug/ps3-app-debug.apk.

GitHub Actions runs the native PPU test and Android debug build for this branch. A prerelease workflow attaches the resulting APK when the branch build succeeds.

## Package size

No artificial padding or filler assets are added to reach a file-size target. The repository contains source code, tests, app resources, build configuration and documentation. Any APK size is the real size produced by the Android build. A larger binary would not make an emulator more capable.

## Firmware and game files

No Sony firmware, BIOS, game disc, encryption keys or copyrighted game content are bundled. Use only firmware and game files that you are legally entitled to use.
