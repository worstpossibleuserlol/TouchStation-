package com.touchstation.ps3

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.ByteArrayOutputStream

/**
 * Immersive, controller-only guest display.
 * The surface has no touch controls or menu overlay. In this Alpha it reports the ELF boot result;
 * video is not presented until an RSX renderer and firmware services are integrated.
 */
class GameDisplayActivity : ComponentActivity() {
    private var bootStatus by mutableStateOf("Preparing the experimental PPU boot path…")

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

        setContent {
            MaterialTheme {
                Box(
                    Modifier.fillMaxSize().background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("TouchStation PS3", color = Color.White, fontSize = 22.sp)
                        Text(
                            bootStatus,
                            color = Color(0xFF9AA6B7),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        val uri = intent.getStringExtra("game_uri")?.let(Uri::parse)
        if (uri == null) {
            bootStatus = "No game file was selected."
            return
        }

        Thread({
            val result = try {
                val bytes = readSelectedFile(uri)
                PpuRuntime.bootPpc64Elf(bytes)
            } catch (error: Exception) {
                "Could not read selected file: ${error.message ?: error.javaClass.simpleName}"
            }
            runOnUiThread { bootStatus = result }
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
}
