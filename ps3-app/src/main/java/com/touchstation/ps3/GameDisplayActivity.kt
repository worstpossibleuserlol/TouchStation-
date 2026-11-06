package com.touchstation.ps3

import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.ByteArrayOutputStream

/**
 * Immersive output surface. No touch controls, no menus and no overlays are drawn over a frame.
 * The pipeline demo is an actual PPU guest -> RSX RGBA frame and SPU WRCH -> Android PCM path.
 */
class GameDisplayActivity : ComponentActivity() {
    private var frameBitmap by mutableStateOf<Bitmap?>(null)
    private var status by mutableStateOf("Starting TouchStation guest…")
    private var audioTrack: AudioTrack? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )

        val layout = getSharedPreferences("touchstation_ps3", MODE_PRIVATE)
            .getString("game_layout", "Keep aspect ratio")
        val contentScale = when (layout) {
            "Stretch to full screen" -> ContentScale.FillBounds
            "Fill screen (crop)" -> ContentScale.Crop
            else -> ContentScale.Fit
        }

        setContent {
            MaterialTheme {
                Box(
                    Modifier.fillMaxSize().background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    val image = frameBitmap
                    if (image != null) {
                        Image(
                            bitmap = image.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = contentScale
                        )
                    } else {
                        Text(
                            status,
                            color = Color(0xFF9AA6B7),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        if (intent.getBooleanExtra("run_pipeline_demo", false)) {
            runPipelineDemo()
            return
        }

        val uri = intent.getStringExtra("game_uri")?.let(Uri::parse)
        if (uri == null) {
            status = "No game file was selected."
            return
        }
        runElfAttempt(uri)
    }

    private fun runPipelineDemo() {
        Thread({
            try {
                val width = 640
                val height = 360
                val rgba = PpuRuntime.runPipelineFrame(width, height)
                require(rgba.size == width * height * 4) {
                    "The native guest did not return a complete RGBA frame."
                }

                val pixels = IntArray(width * height)
                var source = 0
                for (index in pixels.indices) {
                    val red = rgba[source].toInt() and 0xff
                    val green = rgba[source + 1].toInt() and 0xff
                    val blue = rgba[source + 2].toInt() and 0xff
                    val alpha = rgba[source + 3].toInt() and 0xff
                    pixels[index] = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
                    source += 4
                }
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.setPixels(pixels, 0, width, 0, 0, width, height)

                val samples = PpuRuntime.runPipelineAudio()
                require(samples.size == 48000) { "The SPU PCM sink returned an incomplete audio buffer." }

                runOnUiThread { frameBitmap = bitmap }
                playPcm(samples)
            } catch (error: Exception) {
                runOnUiThread {
                    status = "Guest pipeline failed: ${error.message ?: error.javaClass.simpleName}"
                }
            }
        }, "TouchStation-guest-pipeline").start()
    }

    private fun playPcm(samples: ShortArray) {
        val minimum = AudioTrack.getMinBufferSize(
            48_000,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(48_000)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(minimum)
            .build()

        audioTrack = track
        track.play()
        var offset = 0
        while (offset < samples.size && !isFinishing) {
            val written = track.write(samples, offset, samples.size - offset, AudioTrack.WRITE_BLOCKING)
            if (written <= 0) break
            offset += written
        }
        // Let the final AudioTrack streaming buffer drain before stopping playback.
        try {
            Thread.sleep(100L)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        track.stopSafely()
    }

    private fun runElfAttempt(uri: Uri) {
        Thread({
            val result = try {
                val bytes = readSelectedFile(uri)
                PpuRuntime.bootPpc64Elf(bytes)
            } catch (error: Exception) {
                "Could not read selected file: ${error.message ?: error.javaClass.simpleName}"
            }
            runOnUiThread { status = result }
        }, "TouchStation-PPU-boot").start()
    }

    private fun readSelectedFile(uri: Uri): ByteArray {
        val maximumBytes = 64L * 1024L * 1024L
        val output = ByteArrayOutputStream()
        val input = contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Android could not open the selected document")
        input.use { stream ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                if (output.size().toLong() + count > maximumBytes)
                    throw IllegalArgumentException("ELF prototype import limit is 64 MiB")
                output.write(buffer, 0, count)
            }
        }
        return output.toByteArray()
    }

    override fun onDestroy() {
        audioTrack?.let { track ->
            runCatching { track.pause() }
            runCatching { track.flush() }
            runCatching { track.release() }
        }
        audioTrack = null
        super.onDestroy()
    }

    private fun AudioTrack.stopSafely() {
        runCatching { stop() }
    }
}
