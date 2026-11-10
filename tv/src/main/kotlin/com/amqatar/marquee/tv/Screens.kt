package com.amqatar.marquee.tv

import android.view.KeyEvent
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import com.amqatar.marquee.shared.DeviceReport
import com.amqatar.marquee.shared.TestMediaItem
import com.amqatar.marquee.shared.TestRun
import com.amqatar.marquee.shared.pickDisplayMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Accent = Color(0xFFE8B04B)
private val Muted = Color(0xFF9AA2B4)
private val Panel = Color(0xFF12151D)

private sealed interface Screen {
    data object Home : Screen
    data class Playing(val item: TestMediaItem, val engine: Engine) : Screen
    data class Verdict(val item: TestMediaItem, val diagnostics: Diagnostics, val startedAt: Long) : Screen
    data object Results : Screen
}

@Composable
fun PlayerTestApp(activity: MainActivity, baseFromIntent: String?) {
    val store = remember { ReportStore(activity) }
    var settings by remember {
        mutableStateOf(TestSettings.load(activity).let { if (!baseFromIntent.isNullOrBlank()) it.copy(baseUrl = baseFromIntent) else it })
    }
    var device by remember { mutableStateOf(DeviceProbe.report(activity)) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var items by remember { mutableStateOf<List<TestMediaItem>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun update(s: TestSettings) { settings = s; s.save(activity) }
    fun load() {
        loadError = null
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { MediaIndex.load(settings.baseUrl) } }
                .onSuccess { items = it; if (it.isEmpty()) loadError = "No media files found at ${settings.baseUrl}" }
                .onFailure { loadError = it.message ?: it.toString() }
        }
    }
    LaunchedEffect(Unit) { if (settings.baseUrl.isNotBlank()) load() }

    MaterialTheme(colorScheme = darkColorScheme(primary = Accent)) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0B0D12))) {
            when (val s = screen) {
                Screen.Home -> HomeScreen(
                    device = device,
                    settings = settings,
                    items = items,
                    loadError = loadError,
                    runCount = store.runs().size,
                    onSettings = ::update,
                    onLoad = { load() },
                    onRefreshDevice = { device = DeviceProbe.report(activity) },
                    onPlay = { item, engine -> screen = Screen.Playing(item, engine) },
                    onResults = { screen = Screen.Results },
                )
                is Screen.Playing -> PlayerScreen(activity, MediaIndex.resolve(settings.baseUrl, s.item.file), s.item, s.engine, settings) { diag, started ->
                    screen = Screen.Verdict(s.item, diag, started)
                }
                is Screen.Verdict -> VerdictScreen(s.item, s.diagnostics) { verdict ->
                    if (verdict != null) {
                        val d = s.diagnostics
                        store.append(
                            device,
                            TestRun(
                                item = s.item.file, engine = d.engine, startedAt = s.startedAt,
                                videoDecoder = d.videoDecoder, videoHardware = d.videoHardware, videoFormat = d.videoFormat,
                                hdr = d.hdr, droppedFrames = d.droppedFrames, audioDecoder = d.audioDecoder,
                                audioOutput = d.audioOutput, displayMode = d.displayMode, verdict = verdict, error = d.error,
                            ),
                        )
                    }
                    screen = Screen.Home
                }
                Screen.Results -> ResultsScreen(store, device) { screen = Screen.Home }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    device: DeviceReport,
    settings: TestSettings,
    items: List<TestMediaItem>,
    loadError: String?,
    runCount: Int,
    onSettings: (TestSettings) -> Unit,
    onLoad: () -> Unit,
    onRefreshDevice: () -> Unit,
    onPlay: (TestMediaItem, Engine) -> Unit,
    onResults: () -> Unit,
) {
    var url by remember(settings.baseUrl) { mutableStateOf(settings.baseUrl) }
    Row(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 27.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        DevicePanel(device, onRefreshDevice, Modifier.width(380.dp).fillMaxHeight())
        LazyColumn(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("Marquee player test", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Text("Serve the test-media folder over HTTP on your network and enter its address.", color = Muted, fontSize = 14.sp)
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BasicTextField(
                        value = url,
                        onValueChange = { url = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
                        cursorBrush = SolidColor(Accent),
                        modifier = Modifier.weight(1f).background(Panel, RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF2A3040), RoundedCornerShape(8.dp)).padding(12.dp),
                        decorationBox = { inner -> if (url.isEmpty()) Text("http://192.168.1.20:8000/", color = Muted) else inner() },
                    )
                    Button(onClick = { onSettings(settings.copy(baseUrl = MediaIndex.normalise(url))); onLoad() }) { Text("Load") }
                    OutlinedButton(onClick = onResults) { Text("Results ($runCount)") }
                }
                loadError?.let { Text(it, color = Color(0xFFEF6B6B), fontSize = 14.sp) }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onSettings(settings.copy(frameRate = settings.frameRate.next(FrameRateMode.entries))) }) {
                        Text("Frame rate: ${settings.frameRate.label}")
                    }
                    OutlinedButton(onClick = { onSettings(settings.copy(media3Decoders = settings.media3Decoders.next(Media3Decoders.entries))) }) {
                        Text("Media3: ${settings.media3Decoders.label}")
                    }
                    OutlinedButton(onClick = { onSettings(settings.copy(tunneling = !settings.tunneling)) }) {
                        Text("Tunneling: ${if (settings.tunneling) "On" else "Off"}")
                    }
                    OutlinedButton(onClick = { onSettings(settings.copy(mpvAudio = settings.mpvAudio.next(MpvAudio.entries))) }) {
                        Text("mpv audio: ${settings.mpvAudio.label}")
                    }
                }
            }
            items.groupBy { it.group }.forEach { (group, groupItems) ->
                item { Text(group, color = Accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp)) }
                items(groupItems, key = { it.file }) { item ->
                    Row(
                        Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(10.dp)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(item.title, color = Color.White, fontWeight = FontWeight.Medium)
                            val detail = listOfNotNull(item.video, item.audio).joinToString(" · ")
                            if (detail.isNotEmpty()) Text(detail, color = Muted, fontSize = 13.sp)
                            if (item.expect.isNotEmpty()) Text("Pass: ${item.expect}", color = Muted, fontSize = 13.sp)
                        }
                        Engine.entries.forEach { engine ->
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = { onPlay(item, engine) }) { Text(engine.label) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DevicePanel(d: DeviceReport, onRefresh: () -> Unit, modifier: Modifier) {
    LazyColumn(modifier.background(Panel, RoundedCornerShape(12.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            Text("${d.manufacturer} ${d.model}", color = Color.White, fontWeight = FontWeight.SemiBold)
            Line("Android ${d.androidVersion} (API ${d.sdk}), ${if (d.is64BitProcess) "64-bit" else "32-bit"} app process")
            Line("ABIs: ${d.abis.joinToString()}")
            Line("RAM: ${d.ramMb} MB")
            Header("Display")
            Line("${d.display.width}x${d.display.height} @ ${"%.3f".format(d.display.refreshRate)} Hz")
            Line("Rates: ${d.display.supportedRefreshRates.joinToString { "%.3f".format(it) }}")
            Line("HDR: ${d.display.hdrTypes.ifEmpty { listOf("none reported") }.joinToString()}")
            Header("Audio outputs")
            d.audioOutputs.forEach { o ->
                Line("${o.type}${if (o.name.isNotBlank()) " (${o.name})" else ""}: ${o.encodings.ifEmpty { listOf("PCM") }.joinToString()}; ch ${o.channelCounts.joinToString()}")
            }
            Line("Max PCM channels over HDMI: ${d.maxPcmChannels}")
            Header("Bitstream accepted now")
            if (d.directPlayback.isEmpty()) Line("Needs Android 10+ to check")
            d.directPlayback.forEach { (k, v) -> Line("${if (v) "✓" else "✗"} $k", if (v) Color(0xFF7BD88F) else Muted) }
            Spacer(Modifier.padding(6.dp))
            OutlinedButton(onClick = onRefresh) { Text("Re-check") }
        }
    }
}

@Composable private fun Header(t: String) = Text(t, color = Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))

@Composable private fun Line(t: String, color: Color = Muted) = Text(t, color = color, fontSize = 13.sp)

@Composable
private fun PlayerScreen(
    activity: MainActivity,
    url: String,
    item: TestMediaItem,
    engineType: Engine,
    settings: TestSettings,
    onFinish: (Diagnostics, Long) -> Unit,
) {
    val startedAt = remember { System.currentTimeMillis() }
    val created = remember {
        runCatching {
            when (engineType) {
                Engine.MEDIA3 -> Media3Engine(activity, url, settings)
                Engine.MPV_HW -> MpvEngine(activity, url, hardware = true, audio = settings.mpvAudio)
                Engine.MPV_SW -> MpvEngine(activity, url, hardware = false, audio = settings.mpvAudio)
            }
        }
    }
    val engine = created.getOrNull()
    if (engine == null) {
        val err = Diagnostics(engine = engineType.label, state = "Error", error = created.exceptionOrNull()?.toString())
        LaunchedEffect(Unit) { onFinish(err, startedAt) }
        return
    }
    val diag by engine.diagnostics.collectAsState()
    var overlay by remember { mutableStateOf(true) }
    var modeNote by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }

    DisposableEffect(engine) {
        onDispose {
            engine.release()
            if (settings.frameRate == FrameRateMode.SWITCH) activity.setDisplayMode(0)
        }
    }
    LaunchedEffect(engine) {
        focus.requestFocus()
        while (true) {
            (engine as? Media3Engine)?.tick()
            val m = DeviceProbe.currentMode(DeviceProbe.display(activity))
            modeNote = "${m.width}x${m.height} @ ${"%.3f".format(m.refreshRate)} Hz" + if (settings.frameRate == FrameRateMode.SWITCH) "" else " (${settings.frameRate.label})"
            delay(500)
        }
    }
    LaunchedEffect(diag.contentFps) {
        if (settings.frameRate == FrameRateMode.SWITCH && diag.contentFps > 0f) {
            val display = DeviceProbe.display(activity)
            pickDisplayMode(diag.contentFps, DeviceProbe.currentMode(display), DeviceProbe.displayModes(display))?.let { activity.setDisplayMode(it.id) }
        }
    }

    fun finish() = onFinish(diag.copy(displayMode = modeNote), startedAt)
    BackHandler { finish() }

    Box(
        Modifier.fillMaxSize().background(Color.Black).focusRequester(focus).focusable().onPreviewKeyEvent { e ->
            val k = e.nativeKeyEvent
            if (k.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
            when (k.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_SPACE -> { engine.togglePause(); true }
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> { engine.seekBy(-10); true }
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { engine.seekBy(10); true }
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_MENU -> { overlay = !overlay; true }
                else -> false
            }
        },
    ) {
        AndroidView(factory = { ctx -> SurfaceView(ctx).also(engine::attach) }, modifier = Modifier.fillMaxSize())
        if (overlay) DiagnosticsOverlay(item, diag, modeNote, Modifier.align(Alignment.TopStart).padding(32.dp))
    }
}

@Composable
private fun DiagnosticsOverlay(item: TestMediaItem, d: Diagnostics, mode: String?, modifier: Modifier) {
    val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = Color.White)
    Column(modifier.background(Color(0xCC000000), RoundedCornerShape(10.dp)).padding(16.dp).width(620.dp)) {
        Text(item.title, color = Accent, fontWeight = FontWeight.SemiBold)
        if (item.expect.isNotEmpty()) Text("Pass: ${item.expect}", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.padding(4.dp))
        fun row(k: String, v: String?) = "%-10s %s".format(k, v ?: "-")
        listOf(
            row("Engine", d.engine),
            row("State", "${d.state}  ${fmt(d.positionMs)} / ${fmt(d.durationMs)}"),
            row("Video", d.videoFormat),
            row("Decoder", d.videoDecoder?.let { "$it  [${if (d.videoHardware == true) "HARDWARE" else "SOFTWARE"}]" }),
            row("HDR", d.hdr),
            row("Dropped", d.droppedFrames.toString()),
            row("Display", mode),
            row("Audio in", d.audioInput),
            row("Audio out", d.audioOutput),
        ).forEach { Text(it, style = mono) }
        d.error?.let { Text(it, color = Color(0xFFEF6B6B), fontSize = 13.sp) }
        Text("OK pause · ◀ ▶ 10 s · ▲ hide · Back to rate", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

private fun fmt(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}

@Composable
private fun VerdictScreen(item: TestMediaItem, d: Diagnostics, onVerdict: (String?) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(Modifier.fillMaxSize().padding(48.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("How did it go?", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Text(item.title, color = Accent)
        if (item.expect.isNotEmpty()) Text("Pass means: ${item.expect}", color = Muted)
        Text("Check the soundbar's format indicator before choosing.", color = Muted, fontSize = 14.sp)
        listOfNotNull(d.videoDecoder?.let { "Video: $it" }, d.hdr?.let { "HDR: $it" }, d.audioOutput?.let { "Audio: $it" }, "Dropped frames: ${d.droppedFrames}", d.error)
            .forEach { Text(it, color = Muted, fontSize = 14.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 12.dp)) {
            Button(onClick = { onVerdict("pass") }, modifier = Modifier.focusRequester(focus)) { Text("Pass") }
            OutlinedButton(onClick = { onVerdict("video-problem") }) { Text("Video problem") }
            OutlinedButton(onClick = { onVerdict("audio-problem") }) { Text("Audio problem") }
            OutlinedButton(onClick = { onVerdict("wont-play") }) { Text("Won't play") }
            OutlinedButton(onClick = { onVerdict(null) }) { Text("Don't save") }
        }
    }
}

@Composable
private fun ResultsScreen(store: ReportStore, device: DeviceReport, onBack: () -> Unit) {
    var runs by remember { mutableStateOf(store.runs()) }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().padding(48.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Results", style = MaterialTheme.typography.headlineMedium, color = Color.White, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { store.clear(device); runs = emptyList() }) { Text("Clear") }
            Button(onClick = onBack) { Text("Back") }
        }
        Text("adb pull /sdcard/Android/data/com.amqatar.marquee.tv/files/report.json", color = Muted, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        LazyColumn(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(runs.reversed()) { r ->
                Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(8.dp)).padding(10.dp)) {
                    Text("${r.verdict.uppercase()}  ${r.item}", color = if (r.verdict == "pass") Color(0xFF7BD88F) else Color(0xFFEF6B6B))
                    Text(
                        listOfNotNull(r.engine, r.videoDecoder, r.hdr, r.audioOutput, "dropped ${r.droppedFrames}", r.displayMode, r.error).joinToString(" · "),
                        color = Muted, fontSize = 13.sp,
                    )
                }
            }
        }
    }
}
