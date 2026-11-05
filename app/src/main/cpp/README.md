# Native emulation core

TouchStation's Android frontend loads the bundled PCSX-ReARMed libretro core through `touchstation_jni.cpp`.

The Android workflow checks out the pinned upstream PCSX-ReARMed source, builds `libretro.so` with the Android NDK for `arm64-v8a` and `armeabi-v7a`, then packages the binaries as `libpcsx_rearmed.so`.

The JNI adapter provides libretro callbacks for video output, stereo audio, joypad and analog input, core options, memory directories, reset, and serialized save states. The upstream core supplies the PS1 CPU, GPU, CD-ROM, SPU, and peripheral emulation.

The core is distributed under GPL-2.0. See `THIRD_PARTY_LICENSES/PCSX-ReARMed-COPYING` and the upstream source at https://github.com/notaz/pcsx_rearmed. No BIOS firmware or commercial games are bundled.
