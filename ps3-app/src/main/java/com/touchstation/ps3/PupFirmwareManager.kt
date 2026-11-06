package com.touchstation.ps3

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest

data class PupFirmwareInfo(
    val imageVersion: Long,
    val packageVersion: Long,
    val segmentCount: Long,
    val headerLength: Long,
    val dataLength: Long,
    val totalBytes: Long,
    val sha256: String,
    val storedFile: File
) {
    fun displayVersion(): String = "0x" + imageVersion.toString(16).uppercase().padStart(16, '0')
}

/**
 * Imports a user-provided PS3 system software update package (PUP).
 * This validates the PS3 PUP v1 container structure and stores the original file.
 * It does not decrypt firmware segments, verify Sony signatures, or install firmware.
 */
object PupFirmwareManager {
    private val magic = byteArrayOf(0x53, 0x43, 0x45, 0x55, 0x46, 0, 0, 0)
    private const val FIXED_HEADER = 0x30
    private const val MAX_SEGMENTS = 4096L
    private const val MAX_FILE_BYTES = 2L * 1024L * 1024L * 1024L

    data class Header(
        val packageVersion: Long,
        val imageVersion: Long,
        val segmentCount: Long,
        val headerLength: Long,
        val dataLength: Long
    )

    fun parseHeader(header: ByteArray, totalBytes: Long): Header {
        require(header.size >= FIXED_HEADER) { "PUP is too small to contain a PS3 header." }
        require(totalBytes >= FIXED_HEADER) { "PUP file is too small." }
        require(magic.indices.all { header[it] == magic[it] }) {
            "Not a PS3 PUP file. Expected the SCEUF signature."
        }

        val packageVersion = readBeLong(header, 0x08)
        val imageVersion = readBeLong(header, 0x10)
        val segmentCount = readBeLong(header, 0x18)
        val headerLength = readBeLong(header, 0x20)
        val dataLength = readBeLong(header, 0x28)

        require(segmentCount in 1L..MAX_SEGMENTS) { "PUP segment count is invalid." }
        val minimumHeaderLength = FIXED_HEADER.toLong() + segmentCount * 0x40L + 0x20L
        require(headerLength >= minimumHeaderLength) { "PUP segment tables do not fit inside the declared header." }
        require(headerLength <= totalBytes) { "PUP header length exceeds the file size." }
        require(dataLength == totalBytes - headerLength) { "PUP data length does not match the actual file size." }
        require(headerLength + dataLength == totalBytes) { "PUP length fields are inconsistent." }

        return Header(packageVersion, imageVersion, segmentCount, headerLength, dataLength)
    }

    fun importAndValidate(context: Context, uri: Uri): PupFirmwareInfo {
        val directory = File(context.filesDir, "ps3-firmware")
        check(directory.exists() || directory.mkdirs()) { "Could not create private firmware storage." }
        val temporary = File(directory, "PS3UPDAT.PUP.importing")
        val destination = File(directory, "PS3UPDAT.PUP")
        var total = 0L
        val digest = MessageDigest.getInstance("SHA-256")

        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("Android could not open the selected firmware file.")
            input.use { source ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        total += count.toLong()
                        require(total <= MAX_FILE_BYTES) { "Firmware file exceeds the 2 GiB import safety limit." }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }

            val headerBytes = ByteArray(FIXED_HEADER)
            RandomAccessFile(temporary, "r").use { file ->
                require(file.read(headerBytes) == FIXED_HEADER) { "Could not read the complete PUP header." }
            }
            val header = parseHeader(headerBytes, total)

            if (destination.exists()) check(destination.delete()) { "Could not replace the previous firmware import." }
            check(temporary.renameTo(destination)) { "Could not save the validated firmware file." }

            return PupFirmwareInfo(
                imageVersion = header.imageVersion,
                packageVersion = header.packageVersion,
                segmentCount = header.segmentCount,
                headerLength = header.headerLength,
                dataLength = header.dataLength,
                totalBytes = total,
                sha256 = digest.digest().joinToString("") { "%02x".format(it) },
                storedFile = destination
            )
        } catch (error: Exception) {
            temporary.delete()
            throw error
        }
    }

    private fun readBeLong(bytes: ByteArray, offset: Int): Long {
        require(offset >= 0 && offset <= bytes.size - 8) { "PUP header field is truncated." }
        var value = 0L
        for (index in 0 until 8) {
            value = (value shl 8) or (bytes[offset + index].toLong() and 0xffL)
        }
        require(value >= 0L) { "PUP contains an unsupported unsigned 64-bit length." }
        return value
    }
}
