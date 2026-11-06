# TouchStation PS3 — 1.0.0 Alpha

Separate Android PS3 emulator-engine experiment in the existing TouchStation repository. The PS1 application remains in :app. This edition builds from :ps3-app on branch ps3-alpha.

## Included in Alpha

- Android application with the supplied square TouchStation PS3 logo used as the launcher icon.
- Library import through Android's document picker, with persisted URI permissions and per-game configuration.
- Bluetooth pairing hand-off to Android system settings and detection of connected Bluetooth/USB gamepads through Android InputManager.
- External controller input only. The in-game display has no virtual buttons or touchscreen control overlay.
- Immersive landscape game display, plus an experimental "Attempt ELF boot" action from a selected game profile.
- A native C++17 PowerPC64 PPU interpreter subset with bounds-checked big-endian guest memory.
- A guarded ELF64 big-endian PPC64 loader for PT_LOAD segments and an aligned executable entry point. It validates file ranges, image type, program-header structure, segment sizes, virtual addresses and executable entry point before mapping.
- A small SPU interpreter subset with 128 vector registers, 256 KiB local store, scalar/vector integer operations, selected branch and quadword load/store operations, and STOP handling.
- An RSX FIFO packet parser with incrementing/non-incrementing method writes, method-register state, basic draw/clear accounting, and a CPU clear/scissor reference path.
- Native regression tests for PPU arithmetic/memory/branching, ELF64 mapping and execution, SPU subset/local store, and RSX packet parsing/clear/scissor.
- Persistent CPU, SPU, RSX, renderer, resolution, display fit/crop, audio, performance, controller, storage and per-game settings.

## What the renderer and boot path actually do

The RSX component is a command/FIFO and method-state foundation plus a CPU reference implementation for a clear operation and scissor rectangle. It is **not** a complete RSX graphics renderer. It does not process PS3 shader programs, vertex formats, textures, render targets, blend/depth state, geometry pipelines, DMA command buffers or display flips, and it does not output live game frames through Vulkan/OpenGL.

The SPU component interprets a deliberately small instruction subset and provides a local store. It does not implement the complete SPU ISA, channels, MFC, DMA lists, mailboxes, interrupts, or integration with PPU scheduling.

The PPU component supports a limited instruction subset. The ELF loader can map eligible unencrypted big-endian ELF64 PPC64 homebrew segments and execute instructions that the subset recognises. It cannot run the complete ELF ABI or a game that relies on unsupported instructions or operating-system services. Encrypted SELF executables are rejected. ISO/disc-image boot is not available.

A real PS3 game requires much more: broad Cell CPU coverage (including VMX), SPU/MFC/DMA, RSX 3D rendering, memory mapping and protections, thread/synchronization primitives, storage and optical-disc handling, audio, controllers, and compatible firmware/hypervisor/system-call services. Those are not included in this Alpha. A successful ELF mapping or PPU test is not the same as a game reaching gameplay.

## Controller-only gameplay

1. Open Controllers.
2. Pair a controller using the Android Bluetooth settings shortcut, or connect a USB controller.
3. Confirm that Android lists the controller in TouchStation.
4. Import a supported homebrew ELF and choose "Attempt ELF boot".

If no physical controller is detected, the boot action is disabled. TouchStation does not add onscreen game controls. The game display is immersive and has no in-game control/menu overlay; this Alpha shows the boot/diagnostic result because a frame-producing game renderer is not yet integrated.

## Display and crop settings

Global display profiles include Keep aspect ratio, Stretch to full screen, Fit 4:3, Fit 16:9, Fill screen (crop), Integer scale and Native resolution. Independent crop percentages are saved for left, right, top and bottom. Per-game profiles can override layout, resolution and crop. These remain display preferences until a real frame output pipeline is connected.

## Build and tests

Requirements: JDK 17, Android SDK platform 35, Android NDK 27.2.12479018, CMake 3.22.1, and Gradle 8.9.

Native host tests:

~~~bash
cmake -S ps3-app/src/main/cpp -B build/engine -DBUILD_TESTING=ON
cmake --build build/engine --parallel
ctest --test-dir build/engine --output-on-failure
~~~

Android APK:

~~~bash
gradle :ps3-app:assembleDebug
~~~

APK output: ps3-app/build/outputs/apk/debug/ps3-app-debug.apk.

GitHub Actions runs the native PPU/SPU/RSX/ELF regression suite first, then builds the Android APK. The release workflow reports the real APK size and publishes a prerelease when both jobs pass.

## Firmware and game files

No Sony firmware, BIOS, encryption keys or commercial games are bundled. Use only firmware and content you are legally entitled to use.
