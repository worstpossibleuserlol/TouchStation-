package com.touchstation.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
            var message by remember {
                mutableStateOf("Choose a PS1 game image to add it to your library.")
            }

            val gamePicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    try {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: SecurityException) {
                        // Some document providers do not offer persistable permissions.
                    }
                    selectedGame = uri
                    message = "Game file selected. The PS1 emulation core is not integrated yet."
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
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "TOUCHSTATION",
                            color = cyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 3.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Play classics.\nAnywhere.",
                            color = Color.White,
                            fontSize = 34.sp,
                            lineHeight = 40.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "A new home for your PlayStation 1 library.",
                            color = Color(0xFFAFBED0),
                            fontSize = 15.sp
                        )
                        Spacer(Modifier.height(30.dp))

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = panel,
                            shape = RoundedCornerShape(22.dp)
                        ) {
                            Column(Modifier.padding(20.dp)) {
                                Text(
                                    text = "GAME LIBRARY",
                                    color = cyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 2.sp
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = selectedGame?.lastPathSegment ?: "No game selected",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = message,
                                    color = Color(0xFFAFBED0),
                                    fontSize = 13.sp
                                )
                                Spacer(Modifier.height(18.dp))
                                Button(
                                    onClick = {
                                        gamePicker.launch(
                                            arrayOf(
                                                "application/octet-stream",
                                                "application/x-cue",
                                                "application/x-iso9660-image",
                                                "*/*"
                                            )
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = cyan,
                                        contentColor = navy
                                    )
                                ) {
                                    Text("ADD GAME FILE", fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        message = "Emulation core integration is still in development. This preview cannot run games yet."
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF263B55),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text("START GAME")
                                }
                            }
                        }

                        Spacer(Modifier.height(24.dp))
                        Text(
                            text = "DEVICE TARGET",
                            color = cyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("ARM64", color = Color.White, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(12.dp))
                            Text("ARM32", color = Color.White, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(12.dp))
                            Text("ANDROID 8+", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "TOUCHSTATION 0.1.0 ALPHA  •  FRONTEND PREVIEW",
                            color = Color(0xFF71849B),
                            fontSize = 10.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}
