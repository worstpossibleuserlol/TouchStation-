package com.touchstation.ps3

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.input.InputManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF080A0E)
private val Panel = Color(0xFF141922)
private val Panel2 = Color(0xFF1B2330)
private val Blue = Color(0xFF2377F5)
private val Muted = Color(0xFF9AA6B7)
private val Good = Color(0xFF47C99A)

private enum class Tab(val title: String) {
    LIBRARY("Library"), CONTROLLERS("Controllers"), SETTINGS("Settings"), SYSTEM("System")
}

private data class GameEntry(val uri: String, val title: String)
private data class ControllerEntry(val id: Int, val name: String, val descriptor: String)

class MainActivity : ComponentActivity() {
    private var lastControllerInput by mutableStateOf("No controller input received")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(8, 10, 14)
        window.navigationBarColor = android.graphics.Color.rgb(8, 10, 14)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Blue,
                secondary = Color(0xFF54A1FF),
                background = Ink,
                surface = Panel,
                onBackground = Color.White,
                onSurface = Color.White
            )) {
                TouchStationApp(
                    preferences = getSharedPreferences("touchstation_ps3", Context.MODE_PRIVATE),
                    lastControllerInput = lastControllerInput
                )
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            lastControllerInput = "${event.device?.name ?: "Controller"}: ${KeyEvent.keyCodeToString(event.keyCode)}"
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if ((event.source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK &&
            event.action == MotionEvent.ACTION_MOVE) {
            lastControllerInput = "${event.device?.name ?: "Controller"}: analog input received"
        }
        return super.onGenericMotionEvent(event)
    }
}

@Composable
private fun TouchStationApp(preferences: SharedPreferences, lastControllerInput: String) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(Tab.LIBRARY) }
    var games by remember { mutableStateOf(loadGames(preferences)) }
    var selectedGame by remember { mutableStateOf<GameEntry?>(null) }
    var controllerList by remember { mutableStateOf(readControllers(context)) }
    var ppuResult by remember { mutableStateOf("Native PPU · SPU · RSX diagnostics have not run yet.") }
    var firmwareStatus by remember {
        mutableStateOf(preferences.getString("ps3_firmware_status", "No PS3 system software imported.") ?: "No PS3 system software imported.")
    }

    val importFirmware = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            firmwareStatus = "Copying and validating PS3UPDAT.PUP…"
            val mainHandler = Handler(Looper.getMainLooper())
            Thread({
                val outcome = runCatching { PupFirmwareManager.importAndValidate(context, uri) }
                mainHandler.post {
                    outcome.fold(
                        onSuccess = { info ->
                            val status = "PS3 PUP container validated · image ${info.displayVersion()} · ${info.segmentCount} segments · ${info.totalBytes / (1024 * 1024)} MiB · SHA-256 ${info.sha256.take(16)}…"
                            preferences.edit()
                                .putString("ps3_firmware_path", info.storedFile.absolutePath)
                                .putString("ps3_firmware_sha256", info.sha256)
                                .putString("ps3_firmware_status", status)
                                .apply()
                            firmwareStatus = status
                        },
                        onFailure = { error ->
                            firmwareStatus = "Import rejected: ${error.message ?: error.javaClass.simpleName}"
                        }
                    )
                }
            }, "TouchStation-PUP-import").start()
        }
    }

    val importGame = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) { /* Some document providers do not offer persistable grants. */ }
            val title = queryDisplayName(context, uri) ?: uri.lastPathSegment ?: "Imported PS3 file"
            val updated = games.filterNot { it.uri == uri.toString() } + GameEntry(uri.toString(), title)
            games = updated
            saveGames(preferences, updated)
        }
    }

    val inputManager = remember { context.getSystemService(Context.INPUT_SERVICE) as InputManager }
    DisposableEffect(inputManager) {
        val listener = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) { controllerList = readControllers(context) }
            override fun onInputDeviceRemoved(deviceId: Int) { controllerList = readControllers(context) }
            override fun onInputDeviceChanged(deviceId: Int) { controllerList = readControllers(context) }
        }
        inputManager.registerInputDeviceListener(listener, null)
        onDispose { inputManager.unregisterInputDeviceListener(listener) }
    }
    LaunchedEffect(activeTab) { controllerList = readControllers(context) }

    Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
        Column(Modifier.fillMaxSize()) {
            TopBrandHeader()
            when (activeTab) {
                Tab.LIBRARY -> LibraryPage(
                    games = games,
                    onImport = { importGame.launch(arrayOf("*/*")) },
                    onSelect = { selectedGame = it },
                    controllerCount = controllerList.size
                )
                Tab.CONTROLLERS -> ControllersPage(
                    controllers = controllerList,
                    lastInput = lastControllerInput,
                    onPair = {
                        runCatching { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                    },
                    onRefresh = { controllerList = readControllers(context) }
                )
                Tab.SETTINGS -> SettingsPage(preferences)
                Tab.SYSTEM -> SystemPage(
                    ppuResult = ppuResult,
                    onRunSelfTest = { ppuResult = PpuRuntime.selfTest() },
                    controllerCount = controllerList.size,
                    firmwareStatus = firmwareStatus,
                    onImportFirmware = { importFirmware.launch(arrayOf("*/*")) },
                    onRunPipelineDemo = {
                        context.startActivity(
                            Intent(context, GameDisplayActivity::class.java)
                                .putExtra("run_pipeline_demo", true)
                        )
                    }
                )
            }
            NavigationBar(containerColor = Panel) {
                Tab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = activeTab == tab,
                        onClick = { activeTab = tab },
                        icon = { Text(when (tab) {
                            Tab.LIBRARY -> "▦"
                            Tab.CONTROLLERS -> "⌘"
                            Tab.SETTINGS -> "⚙"
                            Tab.SYSTEM -> "◉"
                        }, fontSize = 18.sp) },
                        label = { Text(tab.title) }
                    )
                }
            }
        }
    }

    selectedGame?.let { game ->
        GameProfileDialog(
            game = game,
            preferences = preferences,
            controllerReady = controllerList.isNotEmpty(),
            onBoot = {
                context.startActivity(Intent(context, GameDisplayActivity::class.java).putExtra("game_uri", game.uri))
                selectedGame = null
            },
            onDismiss = { selectedGame = null },
            onRemove = {
                games = games.filterNot { it.uri == game.uri }
                saveGames(preferences, games)
                selectedGame = null
            }
        )
    }
}

@Composable
private fun TopBrandHeader() {
    Column(
        Modifier.fillMaxWidth().background(Ink).padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Touch", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.2).sp)
            Text("Station", color = Blue, fontSize = 27.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.2).sp)
        }
        Spacer(Modifier.height(3.dp))
        Box(Modifier.fillMaxWidth().height(3.dp).background(Blue, RoundedCornerShape(4.dp)))
        Text("PS3", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Medium, letterSpacing = 8.sp)
        Text("1.0.1 ALPHA", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
    }
}

@Composable
private fun PageHeading(title: String, subtitle: String) {
    Column(Modifier.padding(bottom = 14.dp)) {
        Text(title, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(subtitle, fontSize = 12.sp, color = Muted)
    }
}

@Composable
private fun ColumnScope.LibraryPage(
    games: List<GameEntry>,
    onImport: () -> Unit,
    onSelect: (GameEntry) -> Unit,
    controllerCount: Int
) {
    Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        PageHeading("Game library", "Import a homebrew ELF to try the experimental PPU loader")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusPill("$controllerCount controller${if (controllerCount == 1) "" else "s"}", if (controllerCount > 0) Good else Muted)
            StatusPill("PPU prototype", Blue)
        }
        Spacer(Modifier.height(14.dp))
        Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text("＋  Import game file") }
        Spacer(Modifier.height(12.dp))
        if (games.isEmpty()) {
            EmptyPanel("Your library is empty", "Import a legally obtained .iso, .elf, .self or supported PS3 game file. The pre-alpha stores your library entries; game booting is not implemented yet.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(games, key = { it.uri }) { game ->
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(game) },
                        colors = CardDefaults.outlinedCardColors(containerColor = Panel),
                        border = BorderStroke(1.dp, Panel2)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = Blue.copy(alpha = 0.14f), shape = RoundedCornerShape(10.dp), modifier = Modifier.size(42.dp)) {
                                Box(contentAlignment = Alignment.Center) { Text("PS3", color = Blue, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                            }
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(game.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Tap for per-game profile", color = Muted, fontSize = 11.sp)
                            }
                            Text("›", color = Muted, fontSize = 25.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        InfoPanel("Boot support", "The experimental boot path accepts plain big-endian ELF64 PowerPC64 homebrew files only. Retail SELF, encrypted executables, ISO/disc boot and firmware services are not supported.")
    }
}

@Composable
private fun ColumnScope.ControllersPage(
    controllers: List<ControllerEntry>,
    lastInput: String,
    onPair: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
        PageHeading("Controller setup", "External controller only · no touch overlay")
        InfoPanel("Bluetooth pairing", "Pair a DualShock, DualSense or compatible Bluetooth gamepad in Android system settings. Return here after pairing; Android delivers the controller as a standard game input device.")
        Spacer(Modifier.height(10.dp))
        Button(onClick = onPair, modifier = Modifier.fillMaxWidth()) { Text("Open Bluetooth settings") }
        OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("Refresh connected controllers") }
        Spacer(Modifier.height(16.dp))
        Text("CONNECTED GAMEPADS", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(8.dp))
        if (controllers.isEmpty()) {
            EmptyPanel("No gamepad detected", "Connect a Bluetooth or USB controller. TouchStation does not create on-screen buttons.")
        } else {
            controllers.forEach { controller ->
                OutlinedCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.outlinedCardColors(containerColor = Panel)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(controller.name, fontWeight = FontWeight.SemiBold)
                        Text(controller.descriptor, color = Muted, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        StatusPill("Input device #${controller.id}", Good)
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("LAST INPUT", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Text(lastInput, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(12.dp))
        InfoPanel("Input mapping", "The Android frontend detects physical gamepad buttons and analog motion. Mapping those events to the PS3 pad and the guest runtime is not wired until the emulator core grows a controller subsystem.")
    }
}

@Composable
private fun ColumnScope.SettingsPage(preferences: SharedPreferences) {
    Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        PageHeading("Emulation settings", "Profiles are saved locally on this device")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SettingsGroup("CPU · PPU", "Initial CPU configuration. Only the interpreter subset is implemented.") {
                    ChoiceSetting(preferences, "ppu_mode", "PPU execution mode", "Interpreter (safe prototype)", listOf("Interpreter (safe prototype)", "LLVM JIT (not available)"))
                    ChoiceSetting(preferences, "ppu_threads", "PPU worker threads", "Auto", listOf("Auto", "1", "2", "4"))
                    ChoiceSetting(preferences, "ppu_accuracy", "CPU accuracy", "Accurate", listOf("Accurate", "Balanced", "Fast"))
                    ToggleSetting(preferences, "ppu_debug", "PPU instruction logging", false, "Extra logging can affect performance.")
                }
            }
            item {
                SettingsGroup("SPU · CELL", "A small native SPU instruction subset now has 128 vector registers and 256 KiB local store. MFC/DMA and channel I/O are not implemented.") {
                    ChoiceSetting(preferences, "spu_decoder", "SPU decoder", "Interpreter (subset)", listOf("Interpreter (subset)", "LLVM recompiler (planned)"))
                    ChoiceSetting(preferences, "spu_threads", "SPU thread count", "Auto", listOf("Auto", "1", "2", "4", "6"))
                    ChoiceSetting(preferences, "spu_block_size", "SPU block size", "Safe", listOf("Safe", "Balanced", "Large"))
                    ToggleSetting(preferences, "spu_loop_detection", "SPU loop detection", true, "Reserved for the future scheduler; not active in this interpreter subset.")
                }
            }
            item {
                SettingsGroup("GPU · RSX", "RSX FIFO packet parsing, method registers and a CPU clear/scissor reference path are implemented. Full 3D rendering, shaders and Vulkan/OpenGL presentation remain incomplete.") {
                    ChoiceSetting(preferences, "gpu_backend", "Renderer", "CPU reference (prototype)", listOf("CPU reference (prototype)", "Vulkan (planned)", "OpenGL (planned)"))
                    ChoiceSetting(preferences, "resolution_scale", "Resolution scale", "100%", listOf("25%", "50%", "75%", "100%", "125%", "150%", "200%"))
                    ChoiceSetting(preferences, "shader_quality", "Shader precision", "Auto", listOf("Auto", "Strict", "Balanced", "Fast"))
                    ToggleSetting(preferences, "shader_cache", "Shader cache", true, "Preference saved; shader compilation is not implemented.")
                    ToggleSetting(preferences, "async_shaders", "Asynchronous shader compilation", true, "Planned; no RSX shader compiler is included.")
                    ToggleSetting(preferences, "vsync", "Vertical synchronization", true)
                    ChoiceSetting(preferences, "texture_filter", "Texture filtering", "Auto", listOf("Auto", "Nearest", "Linear", "Anisotropic 2x", "Anisotropic 4x", "Anisotropic 8x"))
                }
            }
            item {
                SettingsGroup("Display · layout & crop", "Choose the viewport fit and crop profile for when the RSX frame output is connected.") {
                    ChoiceSetting(preferences, "game_layout", "Game layout", "Keep aspect ratio", listOf("Keep aspect ratio", "Stretch to full screen", "Fit 4:3", "Fit 16:9", "Fill screen (crop)", "Integer scale", "Native resolution"))
                    ChoiceSetting(preferences, "game_rotation", "Screen orientation", "Landscape", listOf("Landscape", "Auto-rotate"))
                    ToggleSetting(preferences, "crop_enabled", "Enable display crop", false, "Crop is applied to the display viewport, not the source game file.")
                    SliderSetting(preferences, "crop_left", "Crop left", 0f, 20f, "%")
                    SliderSetting(preferences, "crop_right", "Crop right", 0f, 20f, "%")
                    SliderSetting(preferences, "crop_top", "Crop top", 0f, 20f, "%")
                    SliderSetting(preferences, "crop_bottom", "Crop bottom", 0f, 20f, "%")
                    ToggleSetting(preferences, "hide_status_bar", "Hide system UI in gameplay", true)
                }
            }
            item {
                SettingsGroup("Audio", "Audio backend and mixing profiles for a future SPU audio pipeline.") {
                    ChoiceSetting(preferences, "audio_backend", "Audio output", "Android low-latency", listOf("Android low-latency", "OpenSL ES", "AAudio"))
                    ChoiceSetting(preferences, "audio_latency", "Audio latency", "Low", listOf("Very low", "Low", "Balanced", "High"))
                    SliderSetting(preferences, "audio_volume", "Volume", 100f, 100f, "%", min = 0f, max = 100f)
                    ToggleSetting(preferences, "audio_time_stretch", "Time-stretch audio", false)
                    ToggleSetting(preferences, "audio_sync", "Synchronize audio", true)
                }
            }
            item {
                SettingsGroup("Performance · optimization", "These preferences are stored; optimization effects require their corresponding runtime systems.") {
                    ChoiceSetting(preferences, "frame_limit", "Frame limit", "Auto", listOf("Auto", "30 FPS", "60 FPS", "Unlimited"))
                    ToggleSetting(preferences, "threaded_presentation", "Threaded presentation", true)
                    ToggleSetting(preferences, "background_precompile", "Precompile shaders in background", true)
                    ChoiceSetting(preferences, "cpu_priority", "Emulation scheduling", "Balanced", listOf("Power saver", "Balanced", "Performance"))
                    ChoiceSetting(preferences, "cache_size", "Disk cache budget", "1 GB", listOf("256 MB", "512 MB", "1 GB", "2 GB", "4 GB"))
                    ToggleSetting(preferences, "fast_memory", "Fast memory mode", false, "Experimental setting; inactive until memory translation is implemented.")
                }
            }
            item {
                SettingsGroup("Controller", "External controller support only. No touchscreen button layout is included.") {
                    ToggleSetting(preferences, "controller_required", "Require a connected controller", true)
                    ToggleSetting(preferences, "controller_rumble", "Controller vibration", true)
                    SliderSetting(preferences, "controller_deadzone", "Analog deadzone", 12f, 30f, "%", min = 0f, max = 30f)
                    ToggleSetting(preferences, "invert_x", "Invert right-stick X", false)
                    ToggleSetting(preferences, "invert_y", "Invert right-stick Y", false)
                }
            }
            item {
                SettingsGroup("Storage & system", "Library references and preferences remain on this device.") {
                    ChoiceSetting(preferences, "state_slots", "Save-state slots", "10", listOf("3", "5", "10", "20"))
                    ToggleSetting(preferences, "auto_save_config", "Save settings automatically", true)
                    ToggleSetting(preferences, "confirm_exit", "Confirm before exiting", true)
                    ChoiceSetting(preferences, "log_level", "Log level", "Warning", listOf("Error", "Warning", "Info", "Debug"))
                }
            }
            item {
                InfoPanel("Important", "Settings that say planned, prototype or inactive are saved as preferences but do not change PS3 gameplay yet. This build does not contain a complete PPU, SPU, RSX, firmware, hypervisor or game-boot implementation.")
            }
        }
    }
}

@Composable
private fun ColumnScope.SystemPage(
    ppuResult: String,
    onRunSelfTest: () -> Unit,
    controllerCount: Int,
    firmwareStatus: String,
    onImportFirmware: () -> Unit,
    onRunPipelineDemo: () -> Unit
) {
    Column(Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
        PageHeading("System & diagnostics", "PPU · SPU · RSX · firmware · audio")
        InfoPanel("Runtime status", "PPU: PPC64 interpreter subset + guarded ELF64 loader\nSPU: vector interpreter subset + WRCH PCM channel\nRSX: FIFO/method parser + RGBA clear/scissor frame path\nPipeline: custom homebrew bridge demo; not the PS3 LV2 ABI\nNot implemented: full PS3 ISA, MFC/DMA, shaders, 3D renderer, LV1/LV2 and retail game boot")
        Spacer(Modifier.height(12.dp))
        SettingsGroup("PS3 system software", "Import your own official PS3 update package (.PUP). This validates the container and stores the original file; it does not decrypt or install firmware.") {
            Text(firmwareStatus, color = if (firmwareStatus.startsWith("PS3 PUP container validated")) Good else Muted, fontSize = 11.sp)
            Button(onClick = onImportFirmware, modifier = Modifier.fillMaxWidth()) {
                Text("Import PS3 system software (.PUP)")
            }
        }
        Spacer(Modifier.height(12.dp))
        InfoPanel("Connected controllers", "$controllerCount physical gamepad input device(s) currently detected by Android.")
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRunPipelineDemo, modifier = Modifier.fillMaxWidth()) {
            Text("Run PPU → SPU → RSX video + audio demo")
        }
        OutlinedButton(onClick = onRunSelfTest, modifier = Modifier.fillMaxWidth()) {
            Text("Run native engine diagnostics")
        }
        Spacer(Modifier.height(8.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Panel2), modifier = Modifier.fillMaxWidth()) {
            Text(ppuResult, color = if (ppuResult.contains("FAIL")) Color(0xFFFFC36E) else if (ppuResult.contains("PASS")) Good else Muted,
                fontSize = 12.sp, modifier = Modifier.padding(14.dp))
        }
        Spacer(Modifier.height(12.dp))
        InfoPanel("Build identity", "TouchStation PS3 · 1.0.1 Alpha\nAndroid · C++17 native guest-output prototype\nNo Sony firmware, keys or commercial games are bundled.")
    }
}

@Composable
private fun GameProfileDialog(
    game: GameEntry,
    preferences: SharedPreferences,
    controllerReady: Boolean,
    onBoot: () -> Unit,
    onDismiss: () -> Unit,
    onRemove: () -> Unit
) {
    val prefix = "game_${game.uri.hashCode()}_"
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text(game.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("PER-GAME OVERRIDES", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                ChoiceSetting(preferences, prefix + "layout", "Game layout", "Use global setting", listOf("Use global setting", "Keep aspect ratio", "Stretch to full screen", "Fit 4:3", "Fit 16:9", "Fill screen (crop)", "Integer scale"))
                ChoiceSetting(preferences, prefix + "resolution", "Resolution scale", "Use global setting", listOf("Use global setting", "50%", "75%", "100%", "150%", "200%"))
                ToggleSetting(preferences, prefix + "crop", "Override crop for this game", false)
                SliderSetting(preferences, prefix + "crop_left", "Game crop left", 0f, 20f, "%")
                SliderSetting(preferences, prefix + "crop_right", "Game crop right", 0f, 20f, "%")
                SliderSetting(preferences, prefix + "crop_top", "Game crop top", 0f, 20f, "%")
                SliderSetting(preferences, prefix + "crop_bottom", "Game crop bottom", 0f, 20f, "%")
                Text(if (controllerReady) "Controller detected." else "Connect a controller before launching.", color = if (controllerReady) Good else Muted, fontSize = 11.sp)
                Text("Experimental boot: unencrypted, big-endian PPC64 ELF only. Retail SELF, ISO/disc images and firmware boot are not supported.", color = Muted, fontSize = 11.sp)
            }
        },
        confirmButton = { Button(onClick = onBoot, enabled = controllerReady) { Text("Attempt ELF boot") } },
        dismissButton = { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onDismiss) { Text("Close") }
            OutlinedButton(onClick = onRemove) { Text("Remove") }
        } }
    )
}

@Composable
private fun SettingsGroup(title: String, description: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(description, color = Muted, fontSize = 11.sp)
            content()
        }
    }
}

@Composable
private fun ChoiceSetting(
    preferences: SharedPreferences,
    key: String,
    title: String,
    defaultValue: String,
    options: List<String>
) {
    var value by remember(key) { mutableStateOf(preferences.getString(key, defaultValue) ?: defaultValue) }
    var expanded by remember(key) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), fontSize = 12.sp)
        Box {
            OutlinedButton(onClick = { expanded = true }) { Text(value, fontSize = 10.sp, maxLines = 1) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(text = { Text(option, fontSize = 12.sp) }, onClick = {
                        value = option
                        preferences.edit().putString(key, option).apply()
                        expanded = false
                    })
                }
            }
        }
    }
}

@Composable
private fun ToggleSetting(
    preferences: SharedPreferences,
    key: String,
    title: String,
    defaultValue: Boolean,
    description: String? = null
) {
    var checked by remember(key) { mutableStateOf(preferences.getBoolean(key, defaultValue)) }
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 12.sp)
            if (description != null) Text(description, color = Muted, fontSize = 10.sp)
        }
        Switch(checked = checked, onCheckedChange = {
            checked = it
            preferences.edit().putBoolean(key, it).apply()
        })
    }
}

@Composable
private fun SliderSetting(
    preferences: SharedPreferences,
    key: String,
    title: String,
    defaultValue: Float,
    defaultMax: Float,
    suffix: String,
    min: Float = 0f,
    max: Float = defaultMax
) {
    var value by remember(key) { mutableStateOf(preferences.getFloat(key, defaultValue).coerceIn(min, max)) }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.weight(1f), fontSize = 12.sp)
            Text("${value.toInt()}$suffix", color = Muted, fontSize = 11.sp)
        }
        Slider(value = value, onValueChange = {
            value = it
            preferences.edit().putFloat(key, it).apply()
        }, valueRange = min..max)
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.13f), shape = RoundedCornerShape(50)) {
        Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
    }
}

@Composable
private fun EmptyPanel(title: String, message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("＋", color = Blue, fontSize = 30.sp)
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(message, color = Muted, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(top = 5.dp))
        }
    }
}

@Composable
private fun InfoPanel(title: String, message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel2), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Text(message, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

private fun readControllers(context: Context): List<ControllerEntry> {
    val manager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
    return manager.inputDeviceIds.asSequence().mapNotNull { id ->
        val device = InputDevice.getDevice(id) ?: return@mapNotNull null
        val gamepad = device.supportsSource(InputDevice.SOURCE_GAMEPAD) ||
            device.supportsSource(InputDevice.SOURCE_JOYSTICK) ||
            device.supportsSource(InputDevice.SOURCE_DPAD)
        if (!gamepad || device.isVirtual) return@mapNotNull null
        ControllerEntry(device.id, device.name, "Vendor ${device.vendorId} · Product ${device.productId} · Sources 0x${device.sources.toString(16)}")
    }.toList()
}

private fun loadGames(preferences: SharedPreferences): List<GameEntry> {
    return preferences.getStringSet("library_entries", emptySet()).orEmpty().mapNotNull { encoded ->
        val split = encoded.indexOf('|')
        if (split <= 0) null else GameEntry(encoded.substring(0, split), encoded.substring(split + 1))
    }.sortedBy { it.title.lowercase() }
}

private fun saveGames(preferences: SharedPreferences, games: List<GameEntry>) {
    preferences.edit().putStringSet("library_entries", games.map { "${it.uri}|${it.title}" }.toSet()).apply()
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
        }
    }.getOrNull()
}
