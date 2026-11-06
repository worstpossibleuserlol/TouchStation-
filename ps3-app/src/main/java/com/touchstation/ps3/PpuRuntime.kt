package com.touchstation.ps3

/** JNI access to the experimental PPU, SPU, RSX and homebrew output pipeline. */
object PpuRuntime {
    @Volatile private var loaded = false

    @Synchronized
    private fun ensureLoaded() {
        if (!loaded) {
            System.loadLibrary("touchstation_ps3")
            loaded = true
        }
    }

    @Synchronized
    fun selfTest(): String {
        return try {
            ensureLoaded()
            nativeSelfTest()
        } catch (error: UnsatisfiedLinkError) {
            "Native emulator library unavailable: ${error.message ?: "link error"}"
        } catch (error: RuntimeException) {
            "Native diagnostics failed safely: ${error.message ?: "runtime error"}"
        }
    }

    @Synchronized
    fun bootPpc64Elf(bytes: ByteArray): String {
        return try {
            ensureLoaded()
            nativeBootPpc64Elf(bytes)
        } catch (error: UnsatisfiedLinkError) {
            "Native PPU library unavailable: ${error.message ?: "link error"}"
        } catch (error: RuntimeException) {
            "ELF boot attempt failed safely: ${error.message ?: "runtime error"}"
        }
    }

    @Synchronized
    fun runPipelineFrame(width: Int, height: Int): ByteArray {
        ensureLoaded()
        require(width in 1..2048 && height in 1..2048) { "Frame dimensions are outside the safe range." }
        return nativeRunPipelineFrame(width, height)
    }

    @Synchronized
    fun runPipelineAudio(): ShortArray {
        ensureLoaded()
        return nativeRunPipelineAudio()
    }

    private external fun nativeSelfTest(): String
    private external fun nativeBootPpc64Elf(image: ByteArray): String
    private external fun nativeRunPipelineFrame(width: Int, height: Int): ByteArray
    private external fun nativeRunPipelineAudio(): ShortArray
}
