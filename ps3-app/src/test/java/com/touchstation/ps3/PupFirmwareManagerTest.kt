package com.touchstation.ps3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PupFirmwareManagerTest {
    private fun validHeader(): ByteArray {
        val bytes = ByteArray(0x30)
        val magic = byteArrayOf(0x53, 0x43, 0x45, 0x55, 0x46, 0, 0, 0)
        magic.copyInto(bytes, 0)
        putBeLong(bytes, 0x08, 1L)                 // package version field
        putBeLong(bytes, 0x10, 0x0004009000000000L) // image-version field
        putBeLong(bytes, 0x18, 1L)                 // one segment
        putBeLong(bytes, 0x20, 0x90L)              // header + tables + digest/padding
        putBeLong(bytes, 0x28, 0x10L)              // data bytes
        return bytes
    }

    private fun putBeLong(bytes: ByteArray, offset: Int, value: Long) {
        for (index in 0 until 8) {
            bytes[offset + index] = (value ushr (56 - index * 8)).toByte()
        }
    }

    @Test
    fun parsesPs3PupV1Header() {
        val parsed = PupFirmwareManager.parseHeader(validHeader(), 0xa0L)
        assertEquals(1L, parsed.packageVersion)
        assertEquals(0x0004009000000000L, parsed.imageVersion)
        assertEquals(1L, parsed.segmentCount)
        assertEquals(0x90L, parsed.headerLength)
        assertEquals(0x10L, parsed.dataLength)
    }

    @Test
    fun rejectsWrongMagic() {
        val header = validHeader()
        header[0] = 0
        assertThrows(IllegalArgumentException::class.java) {
            PupFirmwareManager.parseHeader(header, 0xa0L)
        }
    }

    @Test
    fun rejectsInconsistentFileLength() {
        assertThrows(IllegalArgumentException::class.java) {
            PupFirmwareManager.parseHeader(validHeader(), 0xa1L)
        }
    }

    @Test
    fun rejectsImpossibleSegmentTable() {
        val header = validHeader()
        putBeLong(header, 0x18, 4096L)
        assertThrows(IllegalArgumentException::class.java) {
            PupFirmwareManager.parseHeader(header, 0xa0L)
        }
    }
}
