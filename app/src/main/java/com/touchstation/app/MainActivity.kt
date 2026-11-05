package com.touchstation.app

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.widget.Toast
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

private val ScreenGrey = Color(0xFF333333)
private val PanelGrey = Color(0xFF424242)
private val DarkGrey = Color(0xFF242424)
private val White = Color(0xFFFFFFFF)
private val SoftWhite = Color(0xFFE5E5E5)

private data class CoreChoice(val value: String, val label: String = value)

private data class CoreOption(
    val category: String,
    val title: String,
    val description: String,
    val key: String,
    val choices: List<CoreChoice>,
    val defaultValue: String
)

private fun choice(value: String, label: String = value) = CoreChoice(value, label)

private val coreOptions = listOf(
    CoreOption("SYSTEM & COMPATIBILITY", "Console region", "Auto detects the region from the game. Forcing the wrong region can change game speed.", "pcsx_rearmed_region", listOf(choice("auto", "Auto"), choice("NTSC"), choice("PAL")), "auto"),
    CoreOption("SYSTEM & COMPATIBILITY", "BIOS selection", "Use a compatible BIOS from your system folder, or choose HLE when a BIOS is unavailable.", "pcsx_rearmed_bios", listOf(choice("auto", "Auto / external BIOS"), choice("HLE", "HLE BIOS")), "auto"),
    CoreOption("SYSTEM & COMPATIBILITY", "BIOS boot logo", "Shows the BIOS startup logo. Some titles are less compatible when this is enabled.", "pcsx_rearmed_show_bios_bootlogo", listOf(choice("disabled", "Off"), choice("enabled", "On"), choice("enabled_no_pcsx", "On, no branding")), "disabled"),
    CoreOption("SYSTEM & COMPATIBILITY", "Dynamic recompiler", "Uses native CPU code generation for higher speed. Disable only when troubleshooting compatibility.", "pcsx_rearmed_drc", listOf(choice("enabled", "Enabled"), choice("disabled", "Disabled / interpreter")), "enabled"),
    CoreOption("SYSTEM & COMPATIBILITY", "PS1 CPU clock", "Auto is recommended. Manual clock values can cause timing errors, slow motion or game hangs.", "pcsx_rearmed_psxclock", listOf(choice("auto", "Auto"), choice("57", "57%"), choice("70", "70%"), choice("80", "80%"), choice("90", "90%"), choice("100", "100%")), "auto"),
    CoreOption("SYSTEM & COMPATIBILITY", "Instruction cache emulation", "Improves accuracy for a few games. Support is partial with ARM dynamic recompilers.", "pcsx_rearmed_icache_emulation", listOf(choice("enabled", "Enabled"), choice("disabled", "Disabled")), "enabled"),

    CoreOption("DISC & CD AUDIO", "CD read-ahead", "Reads disc sectors ahead to reduce loading stalls. Higher values use more memory.", "pcsx_rearmed_cd_readahead", listOf(choice("12"), choice("16"), choice("32"), choice("64"), choice("128")), "12"),
    CoreOption("DISC & CD AUDIO", "CD audio tracks", "Enable CD-DA music tracks. Disable only if a game has audio-related performance issues.", "pcsx_rearmed_nocdaudio", listOf(choice("enabled", "Enabled"), choice("disabled", "Disabled")), "enabled"),
    CoreOption("DISC & CD AUDIO", "XA audio decoding", "Enable XA streamed audio used by many PS1 games and FMV sequences.", "pcsx_rearmed_noxadecoding", listOf(choice("enabled", "Enabled"), choice("disabled", "Disabled")), "enabled"),

    CoreOption("GRAPHICS", "Frameskip", "Skips frames to reduce audio crackling on slower devices. Visual motion becomes less smooth.", "pcsx_rearmed_frameskip_type", listOf(choice("disabled", "Off"), choice("auto", "Auto"), choice("auto_threshold", "Auto threshold"), choice("fixed_interval", "Fixed interval")), "disabled"),
    CoreOption("GRAPHICS", "Frameskip threshold", "Used only with Auto threshold frameskip. A lower value skips less often.", "pcsx_rearmed_frameskip_threshold", listOf(choice("15", "15%"), choice("24", "24%"), choice("33", "33%"), choice("45", "45%"), choice("60", "60%"), choice("75", "75%")), "33"),
    CoreOption("GRAPHICS", "Dithering", "Reproduces the PlayStation's original color dithering pattern.", "pcsx_rearmed_dithering", listOf(choice("disabled", "Off"), choice("enabled", "On"), choice("force", "Force")), "enabled"),
    CoreOption("GRAPHICS", "Threaded rendering", "Moves supported GPU work to another thread. Auto lets the core choose.", "pcsx_rearmed_gpu_thread_rendering", listOf(choice("auto", "Auto"), choice("disabled", "Disabled"), choice("enabled", "Enabled")), "auto"),
    CoreOption("GRAPHICS", "Enhanced resolution", "Renders supported 3D games at a higher internal resolution. This costs performance.", "pcsx_rearmed_neon_enhancement_enable", listOf(choice("disabled", "Off"), choice("enabled", "On")), "disabled"),
    CoreOption("GRAPHICS", "32-bit color output", "Improves color depth in some scenes but increases CPU and memory bandwidth use.", "pcsx_rearmed_rgb32_output", listOf(choice("disabled", "Off"), choice("enabled", "On")), "disabled"),
    CoreOption("GRAPHICS", "High-resolution downscaling", "Downscales 480i/512i output to 320x240. Useful only on some displays.", "pcsx_rearmed_scale_hires", listOf(choice("disabled", "Off"), choice("enabled", "On")), "disabled"),
    CoreOption("GRAPHICS", "GPU linked-list accuracy", "Auto-detects when slower, more accurate linked-list processing is needed.", "pcsx_rearmed_gpu_slow_llists", listOf(choice("auto", "Auto"), choice("disabled", "Disabled"), choice("enabled", "Enabled")), "auto"),
    CoreOption("GRAPHICS", "Internal FPS display", "Requests the core's internal frame-rate overlay. Frontend notifications may be required.", "pcsx_rearmed_display_fps_v2", listOf(choice("disabled", "Off"), choice("enabled", "On"), choice("extra", "Extended")), "disabled"),

    CoreOption("AUDIO", "SPU reverb", "Emulates the PlayStation sound processor's reverb effect. Disable to reduce audio processing.", "pcsx_rearmed_spu_reverb", listOf(choice("enabled", "Enabled"), choice("disabled", "Disabled")), "enabled"),
    CoreOption("AUDIO", "Sound interpolation", "Gaussian is closest to original hardware. Simple is lighter; cubic is more demanding.", "pcsx_rearmed_spu_interpolation", listOf(choice("simple", "Simple"), choice("gaussian", "Gaussian"), choice("cubic", "Cubic"), choice("off", "Off")), "simple"),
    CoreOption("AUDIO", "Threaded SPU", "Runs the sound processor on another thread. Disable it if a game develops audio glitches.", "pcsx_rearmed_spu_thread", listOf(choice("disabled", "Disabled"), choice("enabled", "Enabled")), "disabled"),

    CoreOption("INPUT & MULTIPLAYER", "Multitap", "Enables extra virtual controller ports for compatible five- or eight-player games.", "pcsx_rearmed_multitap", listOf(choice("disabled", "Off"), choice("port 1", "Port 1"), choice("port 2", "Port 2"), choice("ports 1 and 2", "Ports 1 and 2")), "disabled"),
    CoreOption("INPUT & MULTIPLAYER", "Analog stick bounds", "Square bounds can improve diagonal response with some physical controllers.", "pcsx_rearmed_analog_axis_modifier", listOf(choice("circle", "Circle"), choice("square", "Square")), "square"),
    CoreOption("INPUT & MULTIPLAYER", "DualShock vibration", "Enables rumble for compatible gamepads when the DualShock controller mode is selected.", "pcsx_rearmed_vibration", listOf(choice("enabled", "Enabled"), choice("disabled", "Disabled")), "enabled"),

    CoreOption("MEMORY CARDS", "Memory card slot 1", "Choose the save format used for card 1. Game Code files are useful for moving saves between emulators.", "pcsx_rearmed_memcard1", listOf(choice("libretro", "Libretro default"), choice("serial", "Game code / serial"), choice("shared", "Shared file"), choice("none", "Disabled")), "libretro"),
    CoreOption("MEMORY CARDS", "Memory card slot 2", "Choose the save format used for the second virtual memory card.", "pcsx_rearmed_memcard2", listOf(choice("serial", "Game code / serial"), choice("shared", "Shared file"), choice("none", "Disabled")), "shared")
)

class MainActivity : ComponentActivity() {
    private lateinit var systemDirectory: File
    private lateinit var saveDirectory: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        systemDirectory = File(filesDir, "system").apply { mkdirs() }
        saveDirectory = File(filesDir, "saves").apply { mkdirs() }
        File(filesDir, "games").mkdirs()

        val coreReady = CoreBridge.initialize(systemDirectory.absolutePath, saveDirectory.absolutePath)

        setContent {
            val prefs = remember { getSharedPreferences("touchstation_settings", Activity.MODE_PRIVATE) }
            var selectedGame by remember { mutableStateOf(prefs.getString("game_path", null)) }
            var selectedBios by remember { mutableStateOf(prefs.getString("bios_name", null)) }
            var status by remember { mutableStateOf(if (coreReady) "PCSX-ReARMed core ready." else "Native core failed to load. Check the build output.") }
            var selectedTab by remember { mutableStateOf("Library") }
            var playerGame by remember { mutableStateOf<String?>(null) }
            var coreAvailable by remember { mutableStateOf(coreReady) }

            LaunchedEffect(Unit) {
                coreOptions.forEach { option ->
                    val allowed = option.choices.map { it.value }
                    val saved = prefs.getString(option.key, option.defaultValue) ?: option.defaultValue
                    val value = saved.takeIf { it in allowed } ?: option.defaultValue
                    CoreBridge.setOption(option.key, value)
                }
                val controllerMode = prefs.getInt("controller_mode", 0).coerceIn(0, 2)
                CoreBridge.setControllerMode(controllerMode)
            }

            val gameFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) {
                    runCatching { importSingleGame(uri) }
                        .onSuccess { file ->
                            if (file != null) {
                                selectedGame = file.absolutePath
                                prefs.edit().putString("game_path", file.absolutePath).apply()
                                status = if (file.extension.equals("cue", true) || file.extension.equals("m3u", true)) {
                                    "Disc list imported. Import its whole folder when it references other tracks."
                                } else {
                                    "Imported ${file.name}. Ready to launch."
                                }
                            }
                        }
                        .onFailure { status = "Import failed: ${it.message ?: "cannot read file"}" }
                }
            }

            val gameFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                if (uri != null) {
                    runCatching { importGameFolder(uri) }
                        .onSuccess { file ->
                            if (file != null) {
                                selectedGame = file.absolutePath
                                prefs.edit().putString("game_path", file.absolutePath).apply()
                                status = "Game folder imported. Selected ${file.name}."
                            } else {
                                status = "No supported PlayStation disc image was found in that folder."
                            }
                        }
                        .onFailure { status = "Folder import failed: ${it.message ?: "cannot read folder"}" }
                }
            }

            val biosPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) {
                    runCatching { importBios(uri) }
                        .onSuccess { file ->
                            selectedBios = file.name
                            prefs.edit().putString("bios_name", file.name).apply()
                            status = "BIOS copied to TouchStation's system folder."
                        }
                        .onFailure { status = "BIOS import failed: ${it.message ?: "cannot read file"}" }
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
                    if (playerGame != null) {
                        EmulatorScreen(
                            gamePath = playerGame!!,
                            saveDirectory = saveDirectory,
                            onExit = {
                                playerGame = null
                                status = "Emulation stopped."
                            }
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("TOUCHSTATION", color = White, fontSize = 19.sp, fontWeight = FontWeight.Black, letterSpacing = 1.3.sp)
                                    Text("PLAYSTATION 1 EMULATOR", color = SoftWhite, fontSize = 9.sp, letterSpacing = 1.4.sp)
                                }
                                Text("1.00", color = White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                TopTab("Library", selectedTab == "Library", Modifier.weight(1f)) { selectedTab = "Library" }
                                TopTab("Settings", selectedTab == "Settings", Modifier.weight(1f)) { selectedTab = "Settings" }
                            }
                            Spacer(Modifier.height(12.dp))
                            if (selectedTab == "Library") {
                                LibraryScreen(
                                    selectedGame = selectedGame,
                                    selectedBios = selectedBios,
                                    status = status,
                                    coreAvailable = coreAvailable,
                                    onAddGame = { gameFilePicker.launch(arrayOf("*/*")) },
                                    onImportFolder = { gameFolderPicker.launch(null) },
                                    onSelectBios = { biosPicker.launch(arrayOf("*/*")) },
                                    onStart = {
                                        val path = selectedGame
                                        if (path.isNullOrBlank()) {
                                            status = "Import a game first."
                                        } else if (!coreAvailable) {
                                            status = "PCSX-ReARMed is unavailable in this build."
                                        } else {
                                            val ready = CoreBridge.initialize(systemDirectory.absolutePath, saveDirectory.absolutePath)
                                            val loaded = ready && CoreBridge.loadGame(path)
                                            if (loaded) playerGame = path else status = "The core could not load this file. Check the format and disc files."
                                        }
                                    }
                                )
                            } else {
                                SettingsScreen(
                                    prefs = prefs,
                                    coreAvailable = coreAvailable,
                                    onReset = {
                                        prefs.edit().apply {
                                            coreOptions.forEach { remove(it.key) }
                                            remove("controller_mode")
                                        }.apply()
                                        coreOptions.forEach { CoreBridge.setOption(it.key, it.defaultValue) }
                                        CoreBridge.setControllerMode(0)
                                        status = "Core settings restored. Game library and BIOS selection were kept."
                                    }
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                if (coreAvailable) "TOUCHSTATION 1.00 ALPHA  •  PCSX-REARMED" else "TOUCHSTATION 1.00 ALPHA  •  CORE UNAVAILABLE",
                                color = SoftWhite, fontSize = 9.sp, letterSpacing = 0.7.sp
                            )
                        }
                    }
                }
            }
        }
    }

    private fun displayName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "imported-file"
    }

    private fun safeName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._ ()-]"), "_").ifBlank { "imported-file" }

    private fun copyUri(uri: Uri, target: File) {
        contentResolver.openInputStream(uri)?.use { input ->
            target.parentFile?.mkdirs()
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("The selected file could not be opened.")
    }

    private fun importSingleGame(uri: Uri): File? {
        val name = safeName(displayName(uri))
        val ext = name.substringAfterLast('.', "").lowercase()
        val supported = setOf("cue", "bin", "iso", "img", "chd", "pbp", "m3u", "mdf", "toc", "ccd", "cbn", "exe")
        require(ext in supported) { "Unsupported file type: .$ext" }
        val target = File(File(filesDir, "games"), name)
        copyUri(uri, target)
        return target
    }

    private fun importBios(uri: Uri): File {
        val original = safeName(displayName(uri))
        val lower = original.lowercase()
        val name = if (lower.startsWith("scph") || lower.startsWith("psxon")) original else "scph1001.bin"
        val target = File(systemDirectory, name)
        copyUri(uri, target)
        return target
    }

    private fun importGameFolder(uri: Uri): File? {
        val tree = DocumentFile.fromTreeUri(this, uri) ?: error("Cannot access that folder.")
        val rootName = safeName(tree.name ?: "PlayStation Game")
        val destination = File(File(filesDir, "games"), rootName).apply { mkdirs() }
        val supported = setOf("cue", "bin", "iso", "img", "chd", "pbp", "m3u", "mdf", "toc", "ccd", "cbn", "exe", "sbi", "sub")
        var copied = 0

        fun copyNode(node: DocumentFile, parent: File) {
            val name = safeName(node.name ?: return)
            if (node.isDirectory) {
                val folder = File(parent, name).apply { mkdirs() }
                node.listFiles().forEach { copyNode(it, folder) }
            } else {
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext in supported) {
                    copyUri(node.uri, File(parent, name))
                    copied++
                }
            }
        }

        tree.listFiles().forEach { copyNode(it, destination) }
        if (copied == 0) return null
        val candidates = destination.walkTopDown().filter { it.isFile }.toList()
        val preferred = listOf("m3u", "cue", "chd", "pbp", "iso", "img", "mdf", "ccd", "bin", "cbn", "exe")
        return preferred.asSequence().mapNotNull { ext -> candidates.firstOrNull { it.extension.equals(ext, true) } }.firstOrNull()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val button = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> 4
            KeyEvent.KEYCODE_DPAD_DOWN -> 5
            KeyEvent.KEYCODE_DPAD_LEFT -> 6
            KeyEvent.KEYCODE_DPAD_RIGHT -> 7
            KeyEvent.KEYCODE_BUTTON_A -> 8
            KeyEvent.KEYCODE_BUTTON_B -> 0
            KeyEvent.KEYCODE_BUTTON_X -> 9
            KeyEvent.KEYCODE_BUTTON_Y -> 1
            KeyEvent.KEYCODE_BUTTON_L1 -> 10
            KeyEvent.KEYCODE_BUTTON_R1 -> 11
            KeyEvent.KEYCODE_BUTTON_L2 -> 12
            KeyEvent.KEYCODE_BUTTON_R2 -> 13
            KeyEvent.KEYCODE_BUTTON_SELECT -> 2
            KeyEvent.KEYCODE_BUTTON_START -> 3
            KeyEvent.KEYCODE_BUTTON_THUMBL -> 14
            KeyEvent.KEYCODE_BUTTON_THUMBR -> 15
            else -> null
        }
        if (button != null && (event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP)) {
            CoreBridge.setButton(button, event.action == KeyEvent.ACTION_DOWN)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.isFromSource(android.view.InputDevice.SOURCE_JOYSTICK) && event.action == MotionEvent.ACTION_MOVE) {
            fun axis(value: Float): Int {
                val adjusted = if (kotlin.math.abs(value) < 0.12f) 0f else value.coerceIn(-1f, 1f)
                return (adjusted * 32767f).toInt()
            }
            CoreBridge.setAnalog(0, 0, axis(event.getAxisValue(MotionEvent.AXIS_X)))
            CoreBridge.setAnalog(0, 1, axis(event.getAxisValue(MotionEvent.AXIS_Y)))
            CoreBridge.setAnalog(1, 0, axis(event.getAxisValue(MotionEvent.AXIS_Z)))
            CoreBridge.setAnalog(1, 1, axis(event.getAxisValue(MotionEvent.AXIS_RZ)))
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onDestroy() {
        CoreBridge.stop()
        super.onDestroy()
    }
}

@Composable
private fun TopTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(43.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) White else DarkGrey,
            contentColor = if (selected) DarkGrey else White
        )
    ) { Text(label, fontWeight = FontWeight.Bold) }
}

@Composable
private fun LibraryScreen(
    selectedGame: String?,
    selectedBios: String?,
    status: String,
    coreAvailable: Boolean,
    onAddGame: () -> Unit,
    onImportFolder: () -> Unit,
    onSelectBios: () -> Unit,
    onStart: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Column {
            Text("Your games", color = White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Import disc images or a folder containing a CUE/BIN set.", color = SoftWhite, fontSize = 12.sp)
        }
        Panel {
            Text("GAME LIBRARY", color = White, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            Text(selectedGame?.let { File(it).name } ?: "No game imported", color = White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            PrimaryAction("START GAME", onStart, enabled = coreAvailable && selectedGame != null)
            Spacer(Modifier.height(7.dp))
            SecondaryAction("ADD GAME FILE", onAddGame)
            Spacer(Modifier.height(6.dp))
            SecondaryAction("IMPORT DISC FOLDER", onImportFolder)
            Spacer(Modifier.height(6.dp))
            Text("Supports CUE, BIN, ISO, IMG, CHD, PBP, M3U, MDF, TOC, CCD and more.", color = SoftWhite, fontSize = 11.sp)
        }
        Panel {
            Text("BIOS FIRMWARE", color = White, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(7.dp))
            Text(selectedBios ?: "No external BIOS imported", color = White, fontSize = 13.sp)
            Spacer(Modifier.height(7.dp))
            SecondaryAction("SELECT BIOS FILE", onSelectBios)
            Text("No BIOS or commercial game is bundled. The core can use HLE when a compatible BIOS is absent.", color = SoftWhite, fontSize = 11.sp)
        }
        Panel {
            Text("CORE STATUS", color = White, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(6.dp))
            Text(if (coreAvailable) "PCSX-ReARMed loaded • ARM native core" else "Native core unavailable", color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(status, color = SoftWhite, fontSize = 11.sp)
        }
        Panel {
            Text("SAVE DATA", color = White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Memory cards are stored in TouchStation's private save folder. Use in-game save points as usual.", color = SoftWhite, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SettingsScreen(
    prefs: android.content.SharedPreferences,
    coreAvailable: Boolean,
    onReset: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Settings", color = White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Core settings are passed to PCSX-ReARMed. Changes take effect as the core reads its options; some require a game reload.", color = SoftWhite, fontSize = 12.sp)
        if (!coreAvailable) {
            Panel { Text("The native core did not load. Core options are unavailable until the native libraries load successfully.", color = White, fontSize = 12.sp) }
        }
        ControllerModeCard(prefs)
        val categories = listOf("SYSTEM & COMPATIBILITY", "DISC & CD AUDIO", "GRAPHICS", "AUDIO", "INPUT & MULTIPLAYER", "MEMORY CARDS")
        categories.forEach { category ->
            Text(category, color = White, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            coreOptions.filter { it.category == category }.forEach { CoreOptionCard(it, prefs) }
        }
        Panel {
            Text("LICENSE & SOURCE", color = White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text("PCSX-ReARMed is GPL-2.0. The pinned source revision and full license are included in the project repository.", color = SoftWhite, fontSize = 11.sp)
        }
        SecondaryAction("RESTORE DEFAULT CORE SETTINGS", onReset)
    }
}

@Composable
private fun ControllerModeCard(prefs: android.content.SharedPreferences) {
    var selected by remember { mutableStateOf(prefs.getInt("controller_mode", 0).coerceIn(0, 2)) }
    val labels = listOf("Digital", "Analog", "DualShock")
    Panel {
        Text("Controller mode", color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text("Choose the emulated PS1 controller type. DualShock enables analog controls in compatible games.", color = SoftWhite, fontSize = 11.sp)
        Spacer(Modifier.height(7.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(labels[selected], color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            SecondaryAction("CHANGE") {
                selected = (selected + 1) % labels.size
                prefs.edit().putInt("controller_mode", selected).apply()
                CoreBridge.setControllerMode(selected)
            }
        }
    }
}

@Composable
private fun CoreOptionCard(option: CoreOption, prefs: android.content.SharedPreferences) {
    var selected by remember(option.key) {
        mutableStateOf(prefs.getString(option.key, option.defaultValue) ?: option.defaultValue)
    }
    val index = option.choices.indexOfFirst { it.value == selected }.let { if (it < 0) 0 else it }
    val selectedLabel = option.choices[index].label
    Panel {
        Text(option.title, color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(option.description, color = SoftWhite, fontSize = 11.sp)
        Spacer(Modifier.height(7.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(selectedLabel, color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            SecondaryAction("CHANGE") {
                selected = option.choices[(index + 1) % option.choices.size].value
                prefs.edit().putString(option.key, selected).apply()
                CoreBridge.setOption(option.key, selected)
            }
        }
    }
}

@Composable
private fun EmulatorScreen(gamePath: String, saveDirectory: File, onExit: () -> Unit) {
    val context = LocalContext.current
    val running = remember { AtomicBoolean(true) }
    val paused = remember { AtomicBoolean(false) }
    var pausedUi by remember { mutableStateOf(false) }
    val gameFile = File(gamePath)
    val stateDirectory = remember { File(saveDirectory, "states").apply { mkdirs() } }
    val gameStateKey = remember(gamePath) {
        gameFile.nameWithoutExtension.replace(Regex("[^A-Za-z0-9_-]"), "_")
    }
    var stateSlot by remember(gamePath) { mutableStateOf(1) }
    val stateFile = File(stateDirectory, "${gameStateKey}_slot${stateSlot}.state")

    DisposableEffect(gamePath) {
        val worker = Thread({
            var track: AudioTrack? = null
            try {
                val rate = CoreBridge.sampleRate()
                val minBuffer = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
                val audioBuffer = max(minBuffer, rate / 4 * 4)
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(rate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build()
                    )
                    .setBufferSizeInBytes(audioBuffer)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                track.play()
                val pcm = ShortArray(4096)
                while (running.get()) {
                    if (paused.get()) {
                        Thread.sleep(8)
                    } else {
                        CoreBridge.runFrame()
                        val frames = CoreBridge.readAudio(pcm, pcm.size / 2)
                        if (frames > 0) track.write(pcm, 0, frames * 2, AudioTrack.WRITE_BLOCKING)
                    }
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (error: Throwable) {
                android.util.Log.e("TouchStation", "Emulation loop stopped", error)
            } finally {
                try { track?.pause() } catch (_: Throwable) {}
                try { track?.flush() } catch (_: Throwable) {}
                try { track?.release() } catch (_: Throwable) {}
            }
        }, "TouchStation-Emulation").apply { priority = Thread.NORM_PRIORITY }
        worker.start()
        onDispose {
            running.set(false)
            worker.interrupt()
            try { worker.join(1500) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
            CoreBridge.setSurface(null)
            CoreBridge.stop()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(gameFile.name, color = White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("PCSX-ReARMed • 4:3 display", color = SoftWhite, fontSize = 10.sp)
            }
            SecondaryAction("EXIT") { onExit() }
        }
        AndroidView(
            factory = { viewContext ->
                SurfaceView(viewContext).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) { CoreBridge.setSurface(holder.surface) }
                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { CoreBridge.setSurface(holder.surface) }
                        override fun surfaceDestroyed(holder: SurfaceHolder) { CoreBridge.setSurface(null) }
                    })
                    isFocusable = true
                    isFocusableInTouchMode = true
                    requestFocus()
                }
            },
            modifier = Modifier.fillMaxWidth().weight(1f).background(Color.Black).border(1.dp, White, RoundedCornerShape(4.dp))
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("SAVE SLOT $stateSlot / 10", color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            SecondaryAction("NEXT SLOT") {
                stateSlot = if (stateSlot >= 10) 1 else stateSlot + 1
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SecondaryAction(if (pausedUi) "RESUME" else "PAUSE") {
                pausedUi = !pausedUi
                paused.set(pausedUi)
            }
            SecondaryAction("RESET") { CoreBridge.reset() }
            SecondaryAction("SAVE STATE") {
                val ok = CoreBridge.saveState(stateFile.absolutePath)
                Toast.makeText(context, if (ok) "Saved slot $stateSlot" else "Save state failed", Toast.LENGTH_SHORT).show()
            }
            SecondaryAction("LOAD STATE") {
                val ok = stateFile.exists() && CoreBridge.loadState(stateFile.absolutePath)
                Toast.makeText(context, if (ok) "Loaded slot $stateSlot" else "Slot $stateSlot is empty or invalid", Toast.LENGTH_SHORT).show()
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PadButton("▲", 4)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    PadButton("◀", 6)
                    PadButton("▼", 5)
                    PadButton("▶", 7)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    PadButton("△", 1)
                    PadButton("○", 0)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    PadButton("□", 9)
                    PadButton("×", 8)
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            PadButton("L1", 10)
            PadButton("L2", 12)
            PadButton("SELECT", 2)
            PadButton("START", 3)
            PadButton("R2", 13)
            PadButton("R1", 11)
        }
    }
}

@Composable
private fun PadButton(label: String, id: Int) {
    Box(
        modifier = Modifier
            .size(width = if (label.length > 3) 52.dp else 48.dp, height = 42.dp)
            .background(DarkGrey, RoundedCornerShape(8.dp))
            .border(1.dp, White.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
            .pointerInput(id) {
                detectTapGestures(onPress = {
                    CoreBridge.setButton(id, true)
                    try { tryAwaitRelease() } finally { CoreBridge.setButton(id, false) }
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Panel(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PanelGrey,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFAAAAAA))
    ) {
        Column(modifier = Modifier.padding(13.dp), content = content)
    }
}

@Composable
private fun PrimaryAction(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = White, contentColor = DarkGrey, disabledContainerColor = DarkGrey, disabledContentColor = SoftWhite)
    ) { Text(label, fontWeight = FontWeight.Bold) }
}

@Composable
private fun SecondaryAction(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(9.dp),
        colors = ButtonDefaults.buttonColors(containerColor = DarkGrey, contentColor = White)
    ) { Text(label, fontWeight = FontWeight.Bold, fontSize = 10.sp) }
}
