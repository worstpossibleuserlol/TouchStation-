package com.touchstation.app

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
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

private val ScreenGrey = Color(0xFF999999)
private val PanelGrey = Color(0xFF858585)
private val DarkGrey = Color(0xFF6F6F6F)
private val White = Color(0xFFFFFFFF)
private val SoftWhite = Color(0xFFF1F1F1)

private data class SettingGroup(val name: String, val description: String)

private val settingGroups = listOf(
    SettingGroup("Compatibility", "Per-game behavior, timing and accuracy"),
    SettingGroup("Graphics", "Renderer, resolution and display options"),
    SettingGroup("Audio", "Sound output, latency and SPU options"),
    SettingGroup("Controls", "Touch layout and external controllers"),
    SettingGroup("System", "BIOS, region, CPU and startup behavior"),
    SettingGroup("Save Data", "Memory cards and save-state preferences"),
    SettingGroup("Library", "Game formats and library behavior"),
    SettingGroup("Advanced", "Diagnostics and experimental preferences")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val prefs = remember { getSharedPreferences("touchstation_settings", Activity.MODE_PRIVATE) }
            var selectedGame by remember { mutableStateOf(prefs.getString("last_game_uri", null)?.let(Uri::parse)) }
            var selectedBios by remember { mutableStateOf(prefs.getString("bios_uri", null)?.let(Uri::parse)) }
            var status by remember {
                mutableStateOf(prefs.getString("library_status", "Your library is ready for game files.") ?: "Your library is ready for game files.")
            }
            var selectedTab by remember { mutableStateOf("Library") }
            var selectedSettingGroup by remember { mutableStateOf<String?>(null) }
            var resetVersion by remember { mutableStateOf(0) }

            fun keepPermission(uri: Uri) {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: SecurityException) {
                    // Some document providers do not offer persistable permissions.
                }
            }

            val gamePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) {
                    keepPermission(uri)
                    selectedGame = uri
                    val message = "Game file selected. The emulation core is not connected yet."
                    status = message
                    prefs.edit().putString("last_game_uri", uri.toString()).putString("library_status", message).apply()
                }
            }

            val biosPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) {
                    keepPermission(uri)
                    selectedBios = uri
                    val message = "BIOS selected. TouchStation does not include firmware."
                    status = message
                    prefs.edit().putString("bios_uri", uri.toString()).putString("library_status", message).apply()
                }
            }

            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = White,
                    onPrimary = DarkGrey,
                    background = ScreenGrey,
                    onBackground = White,
                    surface = PanelGrey,
                    onSurface = White,
                    secondary = SoftWhite,
                    onSecondary = DarkGrey
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = ScreenGrey) {
                    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("TOUCHSTATION", color = White, fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
                                Text("PLAYSTATION 1 FRONTEND", color = SoftWhite, fontSize = 9.sp, letterSpacing = 1.5.sp)
                            }
                            Text("0.99", color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(18.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TopTab("Library", selectedTab == "Library", Modifier.weight(1f)) {
                                selectedTab = "Library"
                                selectedSettingGroup = null
                            }
                            TopTab("Settings", selectedTab == "Settings", Modifier.weight(1f)) {
                                selectedTab = "Settings"
                                selectedSettingGroup = null
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            when (selectedTab) {
                                "Library" -> LibraryScreen(
                                    selectedGame, selectedBios, status,
                                    { gamePicker.launch(arrayOf("*/*")) },
                                    { biosPicker.launch(arrayOf("*/*")) }
                                )
                                else -> if (selectedSettingGroup == null) {
                                    SettingsHome(
                                        onOpenGroup = { selectedSettingGroup = it },
                                        onReset = {
                                            prefs.edit().clear().apply()
                                            selectedGame = null
                                            selectedBios = null
                                            status = "Settings restored. Select your files again when ready."
                                            resetVersion += 1
                                        }
                                    )
                                } else {
                                    SettingsDetail(
                                        selectedSettingGroup!!, prefs, resetVersion,
                                        { selectedSettingGroup = null }
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("TOUCHSTATION 0.99 ALPHA  •  EMULATION CORE NOT YET INTEGRATED", color = SoftWhite, fontSize = 9.sp, letterSpacing = 0.7.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun TopTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) White else DarkGrey,
            contentColor = if (selected) DarkGrey else White
        )
    ) { Text(label, fontWeight = FontWeight.Bold) }
}

@Composable
private fun LibraryScreen(
    selectedGame: Uri?,
    selectedBios: Uri?,
    status: String,
    onAddGame: () -> Unit,
    onSelectBios: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column {
            Text("Your games", color = White, fontSize = 29.sp, fontWeight = FontWeight.Bold)
            Text("Keep your PlayStation library in one place.", color = SoftWhite, fontSize = 13.sp)
        }
        Panel {
            Text("GAME LIBRARY", color = White, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
            Spacer(Modifier.height(10.dp))
            Text(selectedGame?.lastPathSegment?.let(Uri::decode) ?: "No game selected", color = White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            PrimaryAction("ADD GAME FILE", onAddGame)
            Spacer(Modifier.height(8.dp))
            Text("Common formats: BIN/CUE, ISO, IMG, CHD, PBP and M3U.", color = SoftWhite, fontSize = 11.sp)
        }
        Panel {
            Text("BIOS FIRMWARE", color = White, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
            Spacer(Modifier.height(10.dp))
            Text(selectedBios?.lastPathSegment?.let(Uri::decode) ?: "No BIOS selected", color = White, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            SecondaryAction("SELECT BIOS FILE", onSelectBios)
            Spacer(Modifier.height(8.dp))
            Text("Use firmware you are legally entitled to use. No BIOS is bundled.", color = SoftWhite, fontSize = 11.sp)
        }
        Panel {
            Text("PROJECT STATUS", color = White, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
            Spacer(Modifier.height(8.dp))
            Text(status, color = White, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Text("File selection and preferences work. Game execution, rendering and audio require an integrated PS1 core.", color = SoftWhite, fontSize = 11.sp)
        }
        Panel {
            Text("DEVICE TARGET", color = White, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
            Spacer(Modifier.height(8.dp))
            Text("ARM64  •  ARM32  •  ANDROID 8+", color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("Game performance will depend on the future core and device.", color = SoftWhite, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SettingsHome(onOpenGroup: (String) -> Unit, onReset: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column {
            Text("Settings", color = White, fontSize = 29.sp, fontWeight = FontWeight.Bold)
            Text("Configure TouchStation's frontend and planned emulator core.", color = SoftWhite, fontSize = 13.sp)
        }
        settingGroups.forEach { group ->
            Button(
                onClick = { onOpenGroup(group.name) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PanelGrey, contentColor = White),
                border = BorderStroke(1.dp, Color(0xFFAAAAAA))
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalAlignment = Alignment.Start) {
                    Text(group.name, color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(group.description, color = SoftWhite, fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        SecondaryAction("RESTORE DEFAULT SETTINGS", onReset)
    }
}

@Composable
private fun SettingsDetail(group: String, prefs: SharedPreferences, resetVersion: Int, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TextButton(onClick = onBack) { Text("‹  ALL SETTINGS", color = White, fontWeight = FontWeight.Bold) }
        Text(group, color = White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        when (group) {
            "Compatibility" -> {
                CoreNotice()
                PreferenceChoice("Compatibility profile", "Choose a default balance between speed and accuracy.", "compat_profile", listOf("Balanced", "Fast", "Accurate"), "Balanced", prefs, resetVersion)
                PreferenceChoice("CPU execution", "Interpreter is usually slower; dynamic recompilation requires core support.", "cpu_mode", listOf("Dynamic recompiler", "Interpreter"), "Dynamic recompiler", prefs, resetVersion)
                PreferenceChoice("Frame skip", "Skip rendering frames to reduce load; audio timing may still be affected.", "frame_skip", listOf("Off", "1", "2", "3"), "Off", prefs, resetVersion)
                PreferenceChoice("Aspect ratio", "Original PS1 presentation is generally 4:3.", "aspect_ratio", listOf("4:3", "16:9 stretch", "Auto"), "4:3", prefs, resetVersion)
                PreferenceToggle("PGXP geometry correction", "Reduce polygon wobble where supported by the core.", "pgxp", false, prefs, resetVersion)
                PreferenceToggle("Perspective-correct textures", "Improve texture mapping in supported renderers.", "perspective_textures", true, prefs, resetVersion)
                PreferenceToggle("Widescreen patches", "Use compatible community patches when available.", "widescreen_patches", false, prefs, resetVersion)
                PreferenceToggle("Disable interlacing", "Can reduce flicker in some titles but may alter effects.", "disable_interlacing", false, prefs, resetVersion)
                PreferenceToggle("Enable dithering", "Preserve the original color dithering effect.", "dithering", true, prefs, resetVersion)
                PreferenceToggle("Per-game compatibility overrides", "Allow individual game profiles to override global values.", "per_game_overrides", true, prefs, resetVersion)
                PreferenceToggle("Boot compatibility mode", "Prefer conservative timing for titles with timing issues.", "safe_boot", false, prefs, resetVersion)
            }
            "Graphics" -> {
                CoreNotice()
                PreferenceChoice("Renderer", "Select the graphics backend once a compatible core is connected.", "renderer", listOf("Auto", "OpenGL ES", "Vulkan", "Software"), "Auto", prefs, resetVersion)
                PreferenceChoice("Internal resolution", "Higher values can increase GPU load.", "resolution", listOf("Native", "2x", "3x", "4x", "6x"), "2x", prefs, resetVersion)
                PreferenceChoice("Texture filtering", "Nearest keeps the original pixel look; bilinear smooths edges.", "filtering", listOf("Nearest", "Bilinear"), "Nearest", prefs, resetVersion)
                PreferenceToggle("VSync", "Synchronize presentation to the display refresh rate.", "vsync", true, prefs, resetVersion)
                PreferenceToggle("Integer scaling", "Keep pixels evenly sized where the display allows it.", "integer_scaling", true, prefs, resetVersion)
                PreferenceToggle("Show FPS counter", "Display frame rate and frame time during gameplay.", "show_fps", false, prefs, resetVersion)
                PreferenceToggle("Show performance overlay", "Show renderer and frame-time diagnostics.", "performance_overlay", false, prefs, resetVersion)
                PreferenceToggle("PGXP depth buffer", "Optional geometry enhancement; can have compatibility trade-offs.", "pgxp_depth", false, prefs, resetVersion)
            }
            "Audio" -> {
                CoreNotice()
                PreferenceToggle("Enable audio", "Master audio output switch for the future core.", "audio_enabled", true, prefs, resetVersion)
                PreferenceChoice("Output sample rate", "Preferred output rate.", "sample_rate", listOf("Auto", "44.1 kHz", "48 kHz"), "Auto", prefs, resetVersion)
                PreferenceChoice("Audio latency", "Lower latency can be less stable on busy devices.", "audio_latency", listOf("Low", "Balanced", "Safe"), "Balanced", prefs, resetVersion)
                PreferenceChoice("SPU interpolation", "Interpolation quality for PlayStation sound voices.", "spu_interpolation", listOf("Gaussian", "Cubic", "Linear", "Nearest"), "Gaussian", prefs, resetVersion)
                PreferenceToggle("SPU reverb", "Enable the original sound processing reverb effect.", "spu_reverb", true, prefs, resetVersion)
                PreferenceToggle("CD audio", "Enable Red Book audio tracks where present.", "cd_audio", true, prefs, resetVersion)
                PreferenceToggle("Audio time stretching", "Try to preserve pitch if audio timing drifts.", "time_stretch", false, prefs, resetVersion)
            }
            "Controls" -> {
                CoreNotice()
                PreferenceChoice("Touch layout", "Choose a starting layout for the on-screen controller.", "touch_layout", listOf("Classic", "Compact", "Wide"), "Classic", prefs, resetVersion)
                PreferenceChoice("Pad mode", "Choose digital or analog-style touch controls.", "pad_mode", listOf("Digital", "DualShock analog"), "Digital", prefs, resetVersion)
                PreferenceSlider("Button size", "Scale of the on-screen buttons.", "button_size", 0.65f, 1.35f, 1.0f, "%", prefs, resetVersion)
                PreferenceSlider("Button opacity", "Visibility of touch controls during play.", "button_opacity", 0.25f, 1.0f, 0.75f, "%", prefs, resetVersion)
                PreferenceToggle("Vibration", "Use device vibration for supported controller feedback.", "vibration", true, prefs, resetVersion)
                PreferenceToggle("Left-handed layout", "Move primary face buttons to the opposite side.", "left_handed", false, prefs, resetVersion)
                PreferenceToggle("Show analog sticks", "Display virtual analog sticks in DualShock mode.", "show_analog", true, prefs, resetVersion)
                PreferenceToggle("Show controller labels", "Display button labels on touch controls.", "button_labels", true, prefs, resetVersion)
                PreferenceToggle("Enable physical controller input", "Prepare support for USB and Bluetooth gamepads.", "physical_controller", true, prefs, resetVersion)
            }
            "System" -> {
                CoreNotice()
                PreferenceChoice("Console region", "Auto-detect from game or select a region manually.", "region", listOf("Auto", "NTSC-U", "NTSC-J", "PAL"), "Auto", prefs, resetVersion)
                PreferenceChoice("BIOS preference", "Choose which user-provided firmware to use.", "bios_preference", listOf("Auto detect", "Selected BIOS"), "Auto detect", prefs, resetVersion)
                PreferenceToggle("Fast boot", "Skip non-essential startup steps when supported.", "fast_boot", false, prefs, resetVersion)
                PreferenceToggle("Boot BIOS screen", "Show the original system startup screen.", "bios_screen", false, prefs, resetVersion)
                PreferenceToggle("Multi-threaded rendering", "Allow background work on supported cores.", "multithread_rendering", true, prefs, resetVersion)
                PreferenceChoice("CPU clock preset", "Underclocking may help some games but can break timing.", "cpu_clock", listOf("100%", "75%", "50%", "Custom"), "100%", prefs, resetVersion)
                PreferenceToggle("Enable analog mode by default", "Start compatible games with DualShock analog input enabled.", "analog_default", false, prefs, resetVersion)
            }
            "Save Data" -> {
                CoreNotice()
                PreferenceChoice("Memory card slot 1", "Select the virtual card assigned to port 1.", "memcard_slot1", listOf("Card 1", "Card 2", "Per-game card"), "Card 1", prefs, resetVersion)
                PreferenceChoice("Memory card slot 2", "Select the virtual card assigned to port 2.", "memcard_slot2", listOf("Disabled", "Card 1", "Card 2", "Per-game card"), "Disabled", prefs, resetVersion)
                PreferenceToggle("Auto-save memory cards", "Write card changes automatically when supported.", "auto_save_cards", true, prefs, resetVersion)
                PreferenceToggle("Create per-game memory cards", "Keep separate card data for each game.", "per_game_cards", false, prefs, resetVersion)
                PreferenceToggle("Confirm before overwriting saves", "Ask before replacing existing save data.", "confirm_overwrite", true, prefs, resetVersion)
                PreferenceChoice("Save-state slots", "Number of quick-save slots to expose.", "save_slots", listOf("3", "5", "10", "20"), "10", prefs, resetVersion)
                PreferenceToggle("Auto-save state on exit", "Request a resume state when leaving a game.", "auto_state_exit", false, prefs, resetVersion)
                PreferenceToggle("Show save-state thumbnails", "Display preview images for save states.", "state_thumbnails", true, prefs, resetVersion)
            }
            "Library" -> {
                PreferenceChoice("Default game sorting", "Choose the order used in the game list.", "library_sort", listOf("Recently added", "Name A–Z", "Last played"), "Recently added", prefs, resetVersion)
                PreferenceToggle("Scan library on startup", "Look for new game files in saved folders.", "scan_startup", true, prefs, resetVersion)
                PreferenceToggle("Show unsupported files", "Keep files with unknown formats visible in the library.", "show_unsupported", false, prefs, resetVersion)
                PreferenceToggle("Group multi-disc games", "Group M3U playlists and related disc images.", "group_discs", true, prefs, resetVersion)
                PreferenceToggle("Remember last played game", "Keep the most recent game at the top of the library.", "remember_last_game", true, prefs, resetVersion)
                Panel {
                    Text("SUPPORTED FILE TYPES", color = White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(".cue  .bin  .iso  .img  .chd  .pbp  .m3u", color = White, fontSize = 13.sp)
                    Text("Actual format loading depends on the emulator core that is integrated later.", color = SoftWhite, fontSize = 11.sp)
                }
            }
            "Advanced" -> {
                CoreNotice()
                PreferenceChoice("Logging level", "Controls the detail level planned for core diagnostics.", "logging_level", listOf("Errors", "Warnings", "Info", "Debug"), "Warnings", prefs, resetVersion)
                PreferenceToggle("Write diagnostic log", "Save technical diagnostics locally for troubleshooting.", "diagnostic_log", false, prefs, resetVersion)
                PreferenceToggle("Show emulator statistics", "Expose CPU, GPU, memory and frame timing when available.", "emulator_stats", false, prefs, resetVersion)
                PreferenceToggle("Allow experimental options", "Show features that may be unstable or game-specific.", "experimental", false, prefs, resetVersion)
                PreferenceToggle("Pause when app loses focus", "Avoid running a game while another app is in front.", "pause_background", true, prefs, resetVersion)
                PreferenceToggle("Ask before closing a game", "Prevent accidental exits during play.", "confirm_exit", true, prefs, resetVersion)
            }
        }
    }
}

@Composable
private fun CoreNotice() {
    Panel {
        Text("CORE REQUIRED", color = White, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(5.dp))
        Text("These preferences are saved on this device. They will not change game behavior until a PS1 emulation core is integrated.", color = White, fontSize = 12.sp)
    }
}

@Composable
private fun PreferenceToggle(title: String, description: String, key: String, defaultValue: Boolean, prefs: SharedPreferences, resetVersion: Int) {
    var checked by remember(key, resetVersion) { mutableStateOf(prefs.getBoolean(key, defaultValue)) }
    Panel {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(description, color = SoftWhite, fontSize = 11.sp)
            }
            Switch(
                checked = checked,
                onCheckedChange = {
                    checked = it
                    prefs.edit().putBoolean(key, it).apply()
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = DarkGrey,
                    checkedTrackColor = White,
                    uncheckedThumbColor = White,
                    uncheckedTrackColor = DarkGrey,
                    uncheckedBorderColor = White
                )
            )
        }
    }
}

@Composable
private fun PreferenceChoice(title: String, description: String, key: String, options: List<String>, defaultValue: String, prefs: SharedPreferences, resetVersion: Int) {
    var selected by remember(key, resetVersion) { mutableStateOf(prefs.getString(key, defaultValue) ?: defaultValue) }
    val currentIndex = options.indexOf(selected).let { if (it < 0) 0 else it }
    Panel {
        Column {
            Text(title, color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = SoftWhite, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(selected, color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                SecondaryAction("CHANGE") {
                    selected = options[(currentIndex + 1) % options.size]
                    prefs.edit().putString(key, selected).apply()
                }
            }
        }
    }
}

@Composable
private fun PreferenceSlider(title: String, description: String, key: String, min: Float, max: Float, defaultValue: Float, suffix: String, prefs: SharedPreferences, resetVersion: Int) {
    var value by remember(key, resetVersion) { mutableStateOf(prefs.getFloat(key, defaultValue).coerceIn(min, max)) }
    Panel {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(String.format("%.0f", value * 100) + suffix, color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Text(description, color = SoftWhite, fontSize = 11.sp)
            Slider(value = value, onValueChange = {
                value = it
                prefs.edit().putFloat(key, it).apply()
            }, valueRange = min..max)
        }
    }
}

@Composable
private fun Panel(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), color = PanelGrey, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFFAAAAAA))) {
        Column(modifier = Modifier.padding(15.dp), content = content)
    }
}

@Composable
private fun PrimaryAction(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(11.dp), colors = ButtonDefaults.buttonColors(containerColor = White, contentColor = DarkGrey)) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SecondaryAction(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = DarkGrey, contentColor = White)) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 11.sp)
    }
}
