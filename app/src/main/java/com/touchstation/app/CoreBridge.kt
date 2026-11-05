package com.touchstation.app

import android.view.Surface

/**
 * Small JNI frontend for the bundled PCSX-ReARMed libretro core.
 * The core binary is built from the pinned upstream source in the Android workflow.
 */
object CoreBridge {
    @Volatile
    private var librariesLoaded = false

    @Synchronized
    fun initialize(systemDirectory: String, saveDirectory: String): Boolean {
        return try {
            if (!librariesLoaded) {
                System.loadLibrary("pcsx_rearmed")
                System.loadLibrary("touchstation_jni")
                librariesLoaded = true
            }
            nativeInitialize(systemDirectory, saveDirectory)
        } catch (_: UnsatisfiedLinkError) {
            false
        } catch (_: RuntimeException) {
            false
        }
    }

    fun loadGame(path: String): Boolean = nativeLoadGame(path)
    fun runFrame() = nativeRunFrame()
    fun setSurface(surface: Surface?) = nativeSetSurface(surface)
    fun setButton(id: Int, pressed: Boolean) = nativeSetButton(id, pressed)
    fun readAudio(buffer: ShortArray, maxFrames: Int): Int = nativeReadAudio(buffer, maxFrames)
    fun sampleRate(): Int = nativeGetSampleRate().coerceIn(22050, 96000)
    fun setOption(key: String, value: String) = nativeSetOption(key, value)
    fun reset() = nativeReset()
    fun saveState(path: String): Boolean = nativeSaveState(path)
    fun loadState(path: String): Boolean = nativeLoadState(path)
    fun stop() = nativeStop()

    private external fun nativeInitialize(systemDirectory: String, saveDirectory: String): Boolean
    private external fun nativeLoadGame(path: String): Boolean
    private external fun nativeRunFrame()
    private external fun nativeSetSurface(surface: Surface?)
    private external fun nativeSetButton(id: Int, pressed: Boolean)
    private external fun nativeReadAudio(buffer: ShortArray, maxFrames: Int): Int
    private external fun nativeGetSampleRate(): Int
    private external fun nativeSetOption(key: String, value: String)
    private external fun nativeReset()
    private external fun nativeSaveState(path: String): Boolean
    private external fun nativeLoadState(path: String): Boolean
    private external fun nativeStop()
}
