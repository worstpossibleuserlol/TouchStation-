# TouchStation PS3 — 1.0.1 Alpha

Android PS3 emulator-engine development build in the existing TouchStation repository. The PS1 application remains in :app. This edition builds from :ps3-app on branch ps3-1.0.1-alpha.

## New in 1.0.1 Alpha

- Exact supplied TouchStation PS3 artwork is the app's launcher icon: legacy mipmap resources and Android adaptive-icon resources both reference the uploaded mark.
- PS3 system software update import (.PUP) using Android's document picker and a streaming private-storage copy.
- PUP container checks: PS3 SCEUF magic, fixed header fields, segment-count bounds, minimum segment/digest table size, header/data lengths and actual file length.
- SHA-256 fingerprint for the imported PUP. The importer does not decrypt PUP payloads, verify Sony cryptographic signatures, install firmware or make firmware services available to the emulator.
- PPU sc host-call trap for the documented TouchStation development-demo ABI.
- PPU guest program requests an RSX clear through the FIFO/method processor. The RSX reference path outputs an RGBA frame, which JNI returns to Android for fullscreen display.
- PPU guest program requests SPU execution. The SPU interpreter's WRCH audio mailbox forwards signed 16-bit samples into a bounded PCM queue; JNI exposes the 48 kHz samples and Android plays them with AudioTrack.
- The game display shows only the output frame when the demo succeeds. No touchscreen buttons, virtual pad, or menu controls are drawn over the image.
- Native end-to-end regression test asserts PPU guest calls, RSX frame color/size, SPU sample count and RSX method writes.
- Android unit tests cover valid and malformed PS3 PUP headers.
- CI verifies the Android package contains an application icon before publishing the APK.

## What the end-to-end milestone means

The bundled development guest runs actual instructions in the current PPU and SPU interpreter subsets. Its PPU program uses a TouchStation-specific test host-call ABI to request:
1. An RSX FIFO clear/scissor command. The RSX reference path creates an RGBA frame.
2. An SPU audio workload. SPU WRCH output enters a PCM queue and is sent to Android AudioTrack.

This is a real, testable PPU -> RSX frame and PPU -> SPU -> PCM audio vertical slice. It is not a general PS3 title runtime. The host-call ABI is explicitly not the PS3 LV2 syscall ABI, and the demo guest is not a retail or third-party commercial game.

## Remaining PS3 emulator work

The RSX implementation is still a CPU-based clear/scissor reference path. There is no PS3 shader translation, vertex/index processing, texture sampling, depth/blend pipeline, GPU command scheduling, RSX DMA memory mapping or Vulkan/OpenGL 3D renderer.

The SPU implementation is a limited instruction subset. It does not include full SPU ISA coverage, MFC, DMA lists, mailboxes/interrupts, or a complete scheduler and synchronization model.

The PPU implementation is a limited PPC64 interpreter and guarded big-endian ELF64 PT_LOAD mapper. It does not implement full VMX/AltiVec, exceptions, MMU, process/thread services, or the complete PS3 syscall ABI.

PS3 firmware is imported as a user-provided .PUP package. The importer validates the container layout and stores the original file with a SHA-256 fingerprint. It does not decrypt or install firmware, validate Sony signatures, or implement LV1/LV2 services. Firmware import alone does not enable retail game boot.

Therefore, 1.0.1 completes a development vertical-slice milestone, not a playable retail PS3 emulator milestone. Encrypted retail SELF, ISO/disc boot and commercial-game compatibility remain unsupported.

## Firmware import

Open System & diagnostics -> Import PS3 system software (.PUP) and select a PS3 update package that you are legally entitled to use. TouchStation copies it to its private files/ps3-firmware/PS3UPDAT.PUP directory and reports header fields, file size and SHA-256 prefix. The original PUP remains on-device; it is not embedded in the APK or uploaded to the repository.

The format checks follow the documented PS3 PUP header and the open-source RPCS3 PUP header/parser layout:
- [PS3 PUP structure reference](https://www.psdevwiki.com/ps3/Playstation_Update_Package_%28PUP%29)
- [RPCS3 PUP header](https://github.com/RPCS3/rpcs3/blob/master/rpcs3/Loader/PUP.h)
- [RPCS3 PUP parser](https://github.com/RPCS3/rpcs3/blob/master/rpcs3/Loader/PUP.cpp)

## Run the vertical-slice demo

1. Install the Alpha APK.
2. Open System & diagnostics.
3. Tap Run PPU -> SPU -> RSX video + audio demo.
4. The immersive screen displays the RSX-generated frame and plays the PCM samples emitted by the SPU guest workload.

The demo is deterministic test content, not a game. A physical controller is still needed for the intended controller-only game workflow; the demo itself does not draw touch controls.

## Build and tests

Requirements: JDK 17, Android SDK platform 35, Android NDK 27.2.12479018, CMake 3.22.1 and Gradle 8.9.

Native engine regression suite:

~~~bash
cmake -S ps3-app/src/main/cpp -B build/engine -DBUILD_TESTING=ON
cmake --build build/engine --parallel
ctest --test-dir build/engine --output-on-failure
~~~

Android unit tests and APK:

~~~bash
gradle :ps3-app:testDebugUnitTest :ps3-app:assembleDebug
~~~

APK output: ps3-app/build/outputs/apk/debug/ps3-app-debug.apk.

GitHub Actions runs native PPU/SPU/RSX/guest-pipeline tests, Android PUP parser unit tests, Gradle APK packaging and a launcher-icon check before publishing the prerelease.

## Firmware and game files

No Sony firmware, BIOS, encryption keys or commercial games are bundled. Use only system software and content you are legally entitled to use.
