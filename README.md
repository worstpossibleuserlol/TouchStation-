# TouchStation

TouchStation is an Android emulator frontend project with separate experimental editions.

## Existing PS1 edition

The :app module is the existing TouchStation PlayStation 1 frontend. It bundles the PCSX-ReARMed libretro core in its existing build workflow. See the main README content and THIRD_PARTY_LICENSES/ for its core and licensing details.

## TouchStation PS3 — 1.0.0 Pre-Alpha

The experimental PS3 edition is developed separately in the ps3-pre-alpha branch under :ps3-app.

It currently includes:
- Android game library import and saved settings.
- Bluetooth pairing hand-off to Android Settings and connected wired / Bluetooth gamepad detection.
- No on-screen controls; the intended gameplay input is an external controller.
- Layout and crop settings, including per-game overrides.
- A small native PowerPC 64-bit PPU interpreter prototype and an in-app native smoke test.

**It is not yet a playable PS3 emulator.** It does not boot PS3 firmware or commercial games. SPU, RSX, firmware / hypervisor services, storage emulation, audio, and complete controller mapping are not implemented. Many graphics and optimization settings are saved profiles for future runtime components and are labelled accordingly.

Read README-PS3-PRE-ALPHA.md for build instructions and the exact implementation status.

No firmware, BIOS, encryption keys or commercial games are bundled. Use only files you are legally entitled to use.
