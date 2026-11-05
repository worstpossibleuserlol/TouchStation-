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
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
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

private val ScreenGrey = Color(0xFF111923)
private val PanelGrey = Color(0xFF1D2936)
private val DarkGrey = Color(0xFF263646)
private val White = Color(0xFFF2F5F7)
private val SoftWhite = Color(0xFFB5C2CC)
private val Accent = Color(0xFF63C6B2)
private val AccentDark = Color(0xFF173D3A)
private val BorderGrey = Color(0xFF354656)

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
            var drawerOpen by remember { mutableStateOf(false) }
            var searchExpanded by remember { mutableStateOf(false) }
            var searchQuery by remember { mutableStateOf("") }
            var gridView by remember { mutableStateOf(false) }
            var libraryFilter by remember { mutableStateOf("All Games") }
            var gameLibrary by remember { mutableStateOf(scanImportedGames()) }
            var playerGame by remember { mutableStateOf<String?>(null) }
            var coreAvailable by remember { mutableStateOf(coreReady) }

            DisposableEffect(playerGame) {
                if (playerGame != null) {
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                } else {
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                }
                onDispose {
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                }
            }
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
                                gameLibrary = scanImportedGames()
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
                                gameLibrary = scanImportedGames()
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
                        Box(modifier = Modifier.fillMaxSize().background(ScreenGrey)) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    SecondaryAction("☰") { drawerOpen = true }
                                    if (searchExpanded) {
                                        BasicTextField(
                                            value = searchQuery,
                                            onValueChange = { searchQuery = it },
                                            modifier = Modifier.weight(1f).padding(horizontal = 7.dp),
                                            singleLine = true,
                                            textStyle = TextStyle(color = White, fontSize = 16.sp),
                                            decorationBox = { innerTextField ->
                                                Box {
                                                    if (searchQuery.isBlank()) Text("Search games…", color = SoftWhite, fontSize = 16.sp)
                                                    innerTextField()
                                                }
                                            }
                                        )
                                    } else {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("All Games", color = White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                                            Text("TOUCHSTATION  •  1.0.1 ALPHA", color = SoftWhite, fontSize = 9.sp, letterSpacing = 1.1.sp)
                                        }
                                    }
                                    SecondaryAction(if (searchExpanded) "CLOSE" else "SEARCH") {
                                        searchExpanded = !searchExpanded
                                        if (!searchExpanded) searchQuery = ""
                                    }
                                    SecondaryAction(if (gridView) "LIST" else "GRID") { gridView = !gridView }
                                }

                                if (selectedTab == "Library") {
                                    LibraryScreen(
                                        games = gameLibrary,
                                        selectedGame = selectedGame,
                                        selectedBios = selectedBios,
                                        searchQuery = searchQuery,
                                        libraryFilter = libraryFilter,
                                        gridView = gridView,
                                        status = status,
                                        coreAvailable = coreAvailable,
                                        onSelectGame = { file ->
                                            selectedGame = file.absolutePath
                                            prefs.edit().putString("game_path", file.absolutePath).apply()
                                            status = "Selected ${file.name}."
                                        },
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

                                Text(
                                    if (coreAvailable) "PCSX-REARMED CORE READY" else "NATIVE CORE UNAVAILABLE",
                                    color = if (coreAvailable) Accent else SoftWhite,
                                    fontSize = 9.sp,
                                    letterSpacing = 0.8.sp
                                )
                            }

                            if (drawerOpen) {
                                Box(
                                    modifier = Modifier.fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.58f))
                                        .clickable { drawerOpen = false }
                                )
                                Surface(
                                    modifier = Modifier.align(Alignment.CenterStart)
                                        .fillMaxHeight()
                                        .fillMaxWidth(0.82f),
                                    color = PanelGrey,
                                    shape = RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp),
                                    border = BorderStroke(1.dp, BorderGrey)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                                            .padding(horizontal = 18.dp, vertical = 20.dp),
                                        verticalArrangement = Arrangement.spacedBy(7.dp)
                                    ) {
                                        Text("TOUCHSTATION", color = White, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
                                        Text("GAME LIBRARY", color = Accent, fontSize = 10.sp, letterSpacing = 1.3.sp)
                                        DrawerItem("All Games", libraryFilter == "All Games") {
                                            libraryFilter = "All Games"; selectedTab = "Library"; drawerOpen = false
                                        }
                                        DrawerItem("Discs", libraryFilter == "Discs") {
                                            libraryFilter = "Discs"; selectedTab = "Library"; drawerOpen = false
                                        }
                                        DrawerItem("Playlists", libraryFilter == "Playlists") {
                                            libraryFilter = "Playlists"; selectedTab = "Library"; drawerOpen = false
                                        }
                                        DrawerItem("Executables", libraryFilter == "Executables") {
                                            libraryFilter = "Executables"; selectedTab = "Library"; drawerOpen = false
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Surface(Modifier.fillMaxWidth().height(1.dp), color = BorderGrey) {}
                                        DrawerItem("Start File") {
                                            drawerOpen = false
                                            gameFilePicker.launch(arrayOf("*/*"))
                                        }
                                        DrawerItem("Start BIOS") {
                                            drawerOpen = false
                                            biosPicker.launch(arrayOf("*/*"))
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Surface(Modifier.fillMaxWidth().height(1.dp), color = BorderGrey) {}
                                        DrawerItem("App Settings") {
                                            selectedTab = "Settings"; drawerOpen = false
                                        }
                                        DrawerItem("Controller Settings") {
                                            selectedTab = "Settings"; drawerOpen = false
                                        }
                                        DrawerItem("Reset Settings") {
                                            prefs.edit().apply {
                                                coreOptions.forEach { remove(it.key) }
                                                remove("controller_mode")
                                            }.apply()
                                            coreOptions.forEach { CoreBridge.setOption(it.key, it.defaultValue) }
                                            CoreBridge.setControllerMode(0)
                                            status = "Default settings restored. Imported games and BIOS were kept."
                                            drawerOpen = false
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Surface(Modifier.fillMaxWidth().height(1.dp), color = BorderGrey) {}
                                        DrawerItem("Scan for New Games") {
                                            gameLibrary = scanImportedGames()
                                            status = "Scanned TouchStation's imported game folder."
                                            drawerOpen = false
                                        }
                                        DrawerItem("Rescan All Games") {
                                            gameLibrary = scanImportedGames()
                                            status = "Game library refreshed."
                                            drawerOpen = false
                                        }
                                        DrawerItem("Memory Card Data") {
                                            selectedTab = "Settings"
                                            status = "Memory cards are stored in the app's private save directory."
                                            drawerOpen = false
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Text("VERSION 1.0.1 ALPHA", color = SoftWhite, fontSize = 10.sp, letterSpacing = 1.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun scanImportedGames(): List<File> {
        val supportedLaunchFiles = setOf("cue", "iso", "img", "chd", "pbp", "m3u", "mdf", "toc", "ccd", "cbn", "exe")
        val root = File(filesDir, "games")
        if (!root.exists()) return emptyList()
        return root.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in supportedLaunchFiles }
            .sortedBy { it.name.lowercase() }
            .toList()
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
            containerColor = if (selected) Accent else PanelGrey,
            contentColor = if (selected) ScreenGrey else White
        )
    ) { Text(label, fontWeight = FontWeight.Bold) }
}

@Composable
private fun LibraryScreen(
    games: List<File>,
    selectedGame: String?,
    selectedBios: String?,
    searchQuery: String,
    libraryFilter: String,
    gridView: Boolean,
    status: String,
    coreAvailable: Boolean,
    onSelectGame: (File) -> Unit,
    onAddGame: () -> Unit,
    onImportFolder: () -> Unit,
    onSelectBios: () -> Unit,
    onStart: () -> Unit
) {
    val filteredGames = games.filter { file ->
        val extension = file.extension.lowercase()
        val typeMatches = when (libraryFilter) {
            "Discs" -> extension in setOf("cue", "bin", "iso", "img", "chd", "pbp", "mdf", "toc", "ccd", "cbn")
            "Playlists" -> extension == "m3u"
            "Executables" -> extension == "exe"
            else -> true
        }
        typeMatches && file.name.contains(searchQuery, ignoreCase = true)
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(libraryFilter, color = White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                Text("${filteredGames.size} imported item(s)", color = SoftWhite, fontSize = 12.sp)
            }
            Surface(color = AccentDark, shape = RoundedCornerShape(50)) {
                Text("PS1", color = Accent, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp))
            }
        }

        Panel {
            Text("CURRENT GAME", color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
            Spacer(Modifier.height(7.dp))
            Text(selectedGame?.let { File(it).name } ?: "No game selected", color = White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(if (selectedGame == null) "Choose a title below or import a disc image." else "Ready to launch with the bundled PCSX-ReARMed core.", color = SoftWhite, fontSize = 11.sp)
            Spacer(Modifier.height(10.dp))
            PrimaryAction("START GAME", onStart, enabled = coreAvailable && !selectedGame.isNullOrBlank())
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                SecondaryAction("ADD FILE") { onAddGame() }
                SecondaryAction("IMPORT FOLDER") { onImportFolder() }
            }
        }

        Text("YOUR COLLECTION", color = SoftWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        if (filteredGames.isEmpty()) {
            Panel {
                Text("No games found here yet", color = White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (games.isEmpty()) "Import a CUE, CHD, ISO, PBP or M3U file. For CUE/BIN games, import the complete disc folder."
                    else "No imported files match this filter or search.",
                    color = SoftWhite,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(6.dp))
                SecondaryAction("IMPORT GAME FILE") { onAddGame() }
            }
        } else if (gridView) {
            filteredGames.chunked(2).forEach { pair ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { file ->
                        GameLibraryItem(
                            file = file,
                            selected = file.absolutePath == selectedGame,
                            grid = true,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectGame(file) }
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        } else {
            filteredGames.forEach { file ->
                GameLibraryItem(
                    file = file,
                    selected = file.absolutePath == selectedGame,
                    grid = false,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onSelectGame(file) }
                )
            }
        }

        Panel {
            Text("BIOS FIRMWARE", color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
            Spacer(Modifier.height(6.dp))
            Text(selectedBios ?: "No external BIOS imported", color = White, fontSize = 13.sp)
            Text("No BIOS or commercial game is bundled. The core can use its HLE fallback when a compatible BIOS is absent.", color = SoftWhite, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            SecondaryAction("SELECT BIOS FILE") { onSelectBios() }
        }

        Panel {
            Text("CORE STATUS", color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
            Spacer(Modifier.height(5.dp))
            Text(if (coreAvailable) "PCSX-ReARMed is ready" else "Native core unavailable", color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(status, color = SoftWhite, fontSize = 11.sp)
        }
    }
}

@Composable
private fun GameLibraryItem(
    file: File,
    selected: Boolean,
    grid: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = if (selected) AccentDark else PanelGrey,
        shape = RoundedCornerShape(13.dp),
        border = BorderStroke(1.dp, if (selected) Accent else BorderGrey)
    ) {
        if (grid) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("◉", color = Accent, fontSize = 24.sp)
                Text(file.nameWithoutExtension, color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                Text("${file.extension.uppercase()}  •  ${(file.length() / (1024 * 1024)).coerceAtLeast(1)} MB", color = SoftWhite, fontSize = 9.sp)
                Text(if (selected) "SELECTED" else "TAP TO SELECT", color = if (selected) Accent else SoftWhite, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(color = if (selected) Accent else DarkGrey, shape = RoundedCornerShape(50)) {
                    Text("◉", color = if (selected) DarkGrey else Accent, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(file.nameWithoutExtension, color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text("${file.extension.uppercase()}  •  ${(file.length() / (1024 * 1024)).coerceAtLeast(1)} MB", color = SoftWhite, fontSize = 10.sp)
                }
                Text(if (selected) "✓" else "›", color = Accent, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DrawerItem(label: String, selected: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(if (selected) AccentDark else Color.Transparent, RoundedCornerShape(11.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (selected) "◉" else "○", color = Accent, fontSize = 16.sp)
        Spacer(Modifier.size(12.dp))
        Text(label, color = if (selected) White else SoftWhite, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
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
            Text(category, color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
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
    var menuOpen by remember { mutableStateOf(false) }
    var controllerMode by remember { mutableStateOf(0) }
    val controllerLabels = listOf("Digital", "Analog", "DualShock")
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

    fun pauseGame() {
        pausedUi = true
        paused.set(true)
    }

    fun resumeGame() {
        pausedUi = false
        paused.set(false)
        menuOpen = false
    }

    fun saveCurrentState() {
        val ok = CoreBridge.saveState(stateFile.absolutePath)
        Toast.makeText(context, if (ok) "Saved slot $stateSlot" else "Save state failed", Toast.LENGTH_SHORT).show()
    }

    fun loadCurrentState() {
        val ok = stateFile.exists() && CoreBridge.loadState(stateFile.absolutePath)
        Toast.makeText(context, if (ok) "Loaded slot $stateSlot" else "Slot $stateSlot is empty or invalid", Toast.LENGTH_SHORT).show()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF080E14))) {
        Box(
            modifier = Modifier.fillMaxHeight(0.70f).aspectRatio(4f / 3f)
                .align(Alignment.Center)
                .background(Color.Black, RoundedCornerShape(8.dp))
                .border(1.dp, BorderGrey, RoundedCornerShape(8.dp))
        ) {
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
                modifier = Modifier.fillMaxSize()
            )
            if (pausedUi && !menuOpen) {
                Surface(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(0xDD101923),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Accent)
                ) {
                    Text("PAUSED", color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SecondaryAction("MENU") {
                pauseGame()
                menuOpen = true
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(gameFile.nameWithoutExtension, color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("PCSX-ReARMed  •  4:3 DISPLAY", color = SoftWhite, fontSize = 9.sp, letterSpacing = 0.7.sp)
            }
            SecondaryAction("SLOT $stateSlot") { stateSlot = if (stateSlot >= 10) 1 else stateSlot + 1 }
            SecondaryAction("EXIT") { onExit() }
        }

        Row(
            modifier = Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp, top = 58.dp, bottom = 58.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                PadButton("▲", 4)
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    PadButton("◀", 6)
                    PadButton("▼", 5)
                    PadButton("▶", 7)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PadButton("△", 1)
                    PadButton("○", 0)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PadButton("□", 9)
                    PadButton("×", 8)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PadButton("L2", 12)
            PadButton("L1", 10)
            PadButton("SELECT", 2)
            PadButton("START", 3)
            PadButton("R1", 11)
            PadButton("R2", 13)
            SecondaryAction(if (pausedUi) "RESUME" else "PAUSE") {
                if (pausedUi) resumeGame() else pauseGame()
            }
        }

        if (menuOpen) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.68f))
                    .clickable { menuOpen = false; resumeGame() }
            )
            Surface(
                modifier = Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(0.78f),
                color = PanelGrey,
                shape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
                border = BorderStroke(1.dp, BorderGrey)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("CONSOLE MENU", color = White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text(gameFile.name, color = SoftWhite, fontSize = 11.sp, maxLines = 2)
                    Spacer(Modifier.height(5.dp))
                    DrawerItem("Resume Game") { resumeGame() }
                    DrawerItem("Save State  •  Slot $stateSlot") { saveCurrentState() }
                    DrawerItem("Load State  •  Slot $stateSlot") { loadCurrentState() }
                    DrawerItem("Next Save Slot") {
                        stateSlot = if (stateSlot >= 10) 1 else stateSlot + 1
                    }
                    DrawerItem("Controller Mode  •  ${controllerLabels[controllerMode]}") {
                        controllerMode = (controllerMode + 1) % controllerLabels.size
                        CoreBridge.setControllerMode(controllerMode)
                    }
                    DrawerItem("Reset Console") {
                        CoreBridge.reset()
                        Toast.makeText(context, "Console reset", Toast.LENGTH_SHORT).show()
                    }
                    Spacer(Modifier.height(5.dp))
                    Surface(Modifier.fillMaxWidth().height(1.dp), color = BorderGrey) {}
                    DrawerItem("Exit Game") { onExit() }
                }
            }
        }
    }
}

@Composable
private fun PadButton(label: String, id: Int) {
    Box(
        modifier = Modifier
            .size(width = if (label.length > 3) 52.dp else 48.dp, height = 42.dp)
            .background(PanelGrey, RoundedCornerShape(12.dp))
            .border(1.dp, Accent.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
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
        border = BorderStroke(1.dp, BorderGrey)
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
        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = ScreenGrey, disabledContainerColor = PanelGrey, disabledContentColor = SoftWhite)
    ) { Text(label, fontWeight = FontWeight.Bold) }
}

@Composable
private fun SecondaryAction(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(9.dp),
        colors = ButtonDefaults.buttonColors(containerColor = DarkGrey, contentColor = Accent)
    ) { Text(label, fontWeight = FontWeight.Bold, fontSize = 10.sp) }
}
