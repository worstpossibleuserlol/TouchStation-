package com.touchstation.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navy = Color(0xFF081426)
            val panel = Color(0xFF12243B)
            val cyan = Color(0xFF55D6D0)
            var selectedGame by remember { mutableStateOf<Uri?>(null) }
            var selectedBios by remember { mutableStateOf<Uri?>(null) }
            var status by remember { mutableStateOf("Add a game image and your own BIOS file to prepare your library.") }

            fun keepPermission(uri: Uri) {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: SecurityException) {
                    // The provider may not support persistable document permissions.
                }
            }

            val gamePicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    keepPermission(uri)
                    selectedGame = uri
                    status = "Game selected. The PlayStation emulation core is not integrated in this alpha."
                }
            }

            val biosPicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    keepPermission(uri)
                    selectedBios = uri
                    status = "BIOS file selected. TouchStation does not include or distribute BIOS firmware."
                }
            }

            MaterialTheme(colorScheme = darkColorScheme(
                primary = cyan,
                background = navy,
                surface = panel,
                onPrimary = navy,
                onBackground = Color.White,
                onSurface = Color.White
            )) {
                Surface(modifier = Modifier.fillMaxSize(), color = navy) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 26.dp),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.Top
                    ) {
                        Text("TOUCHSTATION", color = cyan, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("Your PS1 library.\nOne place.", color = Color.White, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("A mobile-first PlayStation 1 emulator project.", color = Color(0xFFAFBED0), fontSize = 14.sp)
                        Spacer(Modifier.height(24.dp))

                        Surface(modifier = Modifier.fillMaxWidth(), color = panel, shape = RoundedCornerShape(20.dp)) {
                            Column(Modifier.padding(18.dp)) {
                                Text("GAME LIBRARY", color = cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                                Spacer(Modifier.height(10.dp))
                                Text(selectedGame?.lastPathSegment ?: "No game selected", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = { gamePicker.launch(arrayOf("*/*")) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = cyan, contentColor = navy)
                                ) { Text("ADD GAME FILE", fontWeight = FontWeight.Bold) }
                                Spacer(Modifier.height(18.dp))
                                Text("PLAYSTATION BIOS", color = cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                                Spacer(Modifier.height(10.dp))
                                Text(selectedBios?.lastPathSegment ?: "No BIOS selected", color = Color.White, fontSize = 15.sp)
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = { biosPicker.launch(arrayOf("*/*")) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF263B55), contentColor = Color.White)
                                ) { Text("SELECT MY BIOS FILE") }
                            }
                        }

                        Spacer(Modifier.height(18.dp))
                        Surface(modifier = Modifier.fillMaxWidth(), color = Color(0xFF102B36), shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.padding(16.dp)) {
                                Text("PROJECT STATUS", color = cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                                Spacer(Modifier.height(8.dp))
                                Text(status, color = Color(0xFFD4E1EC), fontSize = 13.sp)
                            }
                        }

                        Spacer(Modifier.height(20.dp))
                        Text("DEVICE TARGET", color = cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("ARM64  •  ARM32  •  ANDROID 8+", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text("Designed for modern mobile chipsets, including MediaTek Dimensity. Performance depends on the device and future emulator core.", color = Color(0xFFAFBED0), fontSize = 12.sp)
                        Spacer(Modifier.weight(1f))
                        Text("TOUCHSTATION 0.1.1 ALPHA  •  CORE INTEGRATION PENDING", color = Color(0xFF71849B), fontSize = 9.sp, letterSpacing = 1.sp)
                    }
                }
            }
        }
    }
}
