package com.touchstation.ps3

/** JNI entry point for the experimental PowerPC 64-bit PPU interpreter. */
object PpuRuntime {
    @Volatile private var loaded = false
    @Synchronized
    fun selfTest(): String {
        return try {
            if (!loaded) {
                System.loadLibrary("touchstation_ps3")
                loaded = true
            }
            nativeSelfTest()
        } catch (error: UnsatisfiedLinkError) {
            "Native PPU library unavailable: ${error.message ?: "link error"}"
        } catch (error: RuntimeException) {
            "PPU self-test failed safely: ${error.message ?: "runtime error"}"
        }
    }
    private external fun nativeSelfTest(): String
}
