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

private val ScreenGrey = Color(0xFF999999)
private val PanelGrey = Color(0xFF858585)
private val DarkGrey = Color(0xFF626262)
private val White = Color(0xFFFFFFFF)
private val SoftWhite = Color(0xFFF1F1F1)

private data class CoreOption(
    val title: String,
    val description: String,
    val key: String,
    val choices: List<String>,
    val defaultValue: String
)

private val coreOptions = listOf(
    CoreOption("Dynamic recompiler", "Uses native CPU code generation for higher speed. Interpreter mode is slower but can help diagnose compatibility problems.", "pcsx_rearmed_drc", listOf("enabled", "disabled"), "enabled"),
    CoreOption("CPU clock", "Leave on Auto for normal timing. Manual values can cause game speed or compatibility issues.", "pcsx_rearmed_psxclock", listOf("Auto", "70", "80", "90", "100"), "Auto"),
    CoreOption("Console region", "Auto follows the game region. NTSC and PAL can be forced for testing.", "pcsx_rearmed_region", listOf("Auto", "NTSC", "PAL"), "Auto"),
    CoreOption("CD read-ahead", "Reads disc sectors ahead to reduce loading stalls. Larger values use more memory.", "pcsx_rearmed_cd_readahead", listOf("12", "16", "32", "64", "128"), "12"),
    CoreOption("Frameskip", "Can reduce audio crackling on slower devices, at the cost of visual smoothness.", "pcsx_rearmed_frameskip_type", listOf("disabled", "Auto", "Auto (Threshold)", "Fixed Interval"), "disabled"),
    CoreOption("Dithering", "Controls the PlayStation color dithering effect.", "pcsx_rearmed_dithering", listOf("disabled", "enabled", "Force"), "enabled"),
    CoreOption("Threaded rendering", "Runs supported graphics work on a separate thread. Auto lets the core decide.", "pcsx_rearmed_gpu_thread_rendering", listOf("Auto", "disabled", "enabled"), "Auto"),
    CoreOption("Enhanced resolution", "Renders at a higher internal resolution on supported NEON builds. It can reduce performance.", "pcsx_rearmed_neon_enhancement_enable", listOf("disabled", "enabled"), "disabled"),
    CoreOption("32-bit color output", "Improves color depth in some modes but increases memory bandwidth use.", "pcsx_rearmed_rgb32_output", listOf("disabled", "enabled"), "disabled"),
    CoreOption("Memory card slot 2", "Choose whether the second virtual memory card is shared, per-game, or disabled.", "pcsx_rearmed_memcard2", listOf("Shared Between All Games", "Game Code (Serial)", "No Memory Card"), "Shared Between All Games")
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
                    val value = prefs.getString(option.key, option.defaultValue) ?: option.defaultValue
                    CoreBridge.setOption(option.key, value)
                }
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
                                        prefs.edit().clear().apply()
                                        coreOptions.forEach { CoreBridge.setOption(it.key, it.defaultValue) }
                                        status = "Core settings restored to defaults."
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
            KeyEvent.KEYCODE_BUTTON_A -> 0
            KeyEvent.KEYCODE_BUTTON_B -> 8
            KeyEvent.KEYCODE_BUTTON_X -> 1
            KeyEvent.KEYCODE_BUTTON_Y -> 9
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
        Text("These options are passed to PCSX-ReARMed through its libretro interface.", color = SoftWhite, fontSize = 12.sp)
        if (!coreAvailable) {
            Panel { Text("The native core did not load. Core options will be applied after a valid build is installed.", color = White, fontSize = 12.sp) }
        }
        Text("CPU & COMPATIBILITY", color = White, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        coreOptions.take(5).forEach { CoreOptionCard(it, prefs) }
        Text("GRAPHICS", color = White, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        coreOptions.drop(5).take(4).forEach { CoreOptionCard(it, prefs) }
        Text("SAVE DATA", color = White, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        CoreOptionCard(coreOptions.last(), prefs)
        Panel {
            Text("LICENSE & SOURCE", color = White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text("PCSX-ReARMed is GPL-2.0. Source and license details are listed in the project README.", color = SoftWhite, fontSize = 11.sp)
        }
        SecondaryAction("RESTORE DEFAULT CORE SETTINGS", onReset)
    }
}

@Composable
private fun CoreOptionCard(option: CoreOption, prefs: android.content.SharedPreferences) {
    var selected by remember(option.key) {
        mutableStateOf(prefs.getString(option.key, option.defaultValue) ?: option.defaultValue)
    }
    val index = option.choices.indexOf(selected).let { if (it < 0) 0 else it }
    Panel {
        Text(option.title, color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(option.description, color = SoftWhite, fontSize = 11.sp)
        Spacer(Modifier.height(7.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(selected, color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            SecondaryAction("CHANGE") {
                selected = option.choices[(index + 1) % option.choices.size]
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
    val stateFile = remember { File(stateDirectory, gameFile.nameWithoutExtension.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".state") }

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
            SecondaryAction(if (pausedUi) "RESUME" else "PAUSE") {
                pausedUi = !pausedUi
                paused.set(pausedUi)
            }
            SecondaryAction("RESET") { CoreBridge.reset() }
            SecondaryAction("SAVE STATE") {
                val ok = CoreBridge.saveState(stateFile.absolutePath)
                Toast.makeText(context, if (ok) "Save state created" else "Save state failed", Toast.LENGTH_SHORT).show()
            }
            SecondaryAction("LOAD STATE") {
                val ok = stateFile.exists() && CoreBridge.loadState(stateFile.absolutePath)
                Toast.makeText(context, if (ok) "Save state loaded" else "No valid save state", Toast.LENGTH_SHORT).show()
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
                    PadButton("△", 9)
                    PadButton("○", 8)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    PadButton("□", 1)
                    PadButton("×", 0)
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
