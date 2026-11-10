package com.amqatar.marquee.prototype

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amqatar.marquee.shared.ProgressEntry
import com.amqatar.marquee.shared.isWatched
import com.amqatar.marquee.shared.ratio
import com.amqatar.marquee.shared.resumeAtMs
import kotlinx.coroutines.delay

private fun Long.clock(): String {
    val s = this / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun DetailsScreen(state: AppState, titleId: String) {
    val t = Mock.title(titleId)
    val p = state.current
    val lastEp = t.episodes.mapNotNull { e -> p.progress[e.id]?.let { e to it } }.maxByOrNull { it.second.updatedAt }
    val resumeEp = lastEp?.let { (e, pr) -> if (pr.isWatched()) Mock.nextEpisode(t.id, e.id)?.let(Mock::episode) else e }
    var season by remember(titleId) { mutableIntStateOf(resumeEp?.season ?: t.seasons.firstOrNull() ?: 1) }
    val main = rememberInitialFocus(titleId)
    val movieProgress = p.progress[t.id]

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(if (t.kind == Kind.SERIES) 330.dp else 540.dp)) {
                Box(Modifier.fillMaxWidth().height(330.dp).background(t.tint))
                Box(Modifier.fillMaxWidth().height(330.dp).background(Brush.horizontalGradient(0f to C.Bg, 0.5f to C.Bg.copy(alpha = 0.88f), 1f to C.Bg.copy(alpha = 0.2f))))
                Box(Modifier.fillMaxWidth().height(330.dp).background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to C.Bg)))
                Column(Modifier.padding(start = 48.dp, top = 36.dp).width(470.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) { Icon(Icons.Back, C.Muted, 10.dp); T("Esc to go back", 10.sp, C.Muted) }
                    T(t.name, 38.sp, weight = FontWeight.Bold)
                    T(listOf(t.year, if (t.kind == Kind.SERIES) "${t.seasons.size} seasons" else "${t.minutes} min", "★ ${t.rating} IMDb").joinToString("  ·  "), 11.sp, C.Soft)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { t.genres.forEach { Chip(it) } }
                    if (p.setting("meta.tmdb") == "On") RatingsStrip(t, p.setting("meta.ratings"))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        when {
                            t.kind == Kind.MOVIE -> {
                                val resume = movieProgress.resumeAtMs()
                                PillButton(if (resume > 0) "Resume ${resume.clock()}" else "Play", { state.go(Screen.Streams(t.id, t.id)) }, primary = true, icon = Icons.Play, focusRequester = main)
                            }
                            resumeEp != null -> PillButton(
                                "${if (lastEp?.second?.isWatched() == true) "Next" else "Continue"} S${resumeEp.season} E${resumeEp.number}",
                                { state.go(Screen.Streams(t.id, resumeEp.id)) }, primary = true, icon = Icons.Play, focusRequester = main,
                            )
                            else -> PillButton("Play S1 E1", { state.go(Screen.Streams(t.id, t.episodes.first().id)) }, primary = true, icon = Icons.Play, focusRequester = main)
                        }
                        LibraryButton(state, t)
                        PillButton("Trailer", { state.say("Trailer opens YouTube (link from the metadata addon)") })
                        if (t.kind == Kind.MOVIE) PillButton(if (movieProgress?.isWatched() == true) "Mark unwatched" else "Mark watched", {
                            if (movieProgress?.isWatched() == true) p.progress.remove(t.id)
                            else p.progress[t.id] = ProgressEntry(t.id, t.id, t.minutes * 60_000L, t.minutes * 60_000L, System.currentTimeMillis())
                        })
                    }
                    if (t.kind == Kind.MOVIE && movieProgress != null && !movieProgress.isWatched()) Progress(movieProgress.ratio(), Modifier.width(160.dp))
                    T(t.synopsis, 11.sp, C.Soft, maxLines = 3)
                    if (p.setting("meta.tmdb") == "On") CastRow()
                    else T("Cast  [Name], [Name], [Name]  ·  Director  [Name]", 9.sp, C.Muted)
                    T("Best available  4K Dolby Vision · Atmos, from ${p.addons.count { it.streams }} stream addons", 9.sp, C.Muted)
                }
            }
        }
        if (t.kind == Kind.SERIES) {
            item {
                Row(Modifier.padding(start = 48.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    t.seasons.forEach { s ->
                        val on = s == season
                        FocusBox({ season = s }, Modifier.height(24.dp), RoundedCornerShape(50), background = if (on) C.Text else Color.Transparent, focusedBackground = if (on) C.Text else C.Raised) {
                            T("Season $s", 10.sp, if (on) C.Bg else C.Soft, FontWeight.SemiBold, Modifier.padding(horizontal = 12.dp))
                        }
                    }
                }
            }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 48.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(t.episodes.filter { it.season == season }, key = { it.id }) { e -> EpisodeCard(state, t, e) }
                }
            }
        }
    }
}

@Composable
private fun RatingsStrip(t: Title, show: String) {
    val wanted = when (show) {
        "IMDb only" -> setOf("IMDb")
        "IMDb, RT" -> setOf("IMDb", "Rotten Tomatoes", "Audience")
        else -> setOf("IMDb", "Rotten Tomatoes", "Audience", "Metacritic", "Letterboxd")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        t.ratings.filter { it.first in wanted }.forEach { (source, value) ->
            Row(
                Modifier.clip(RoundedCornerShape(5.dp)).background(C.Glass).padding(horizontal = 7.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                T(source, 8.sp, C.Muted, FontWeight.SemiBold)
                T(value, 9.sp, weight = FontWeight.Bold)
            }
        }
        T("via MDBList", 8.sp, C.Faint)
    }
}

@Composable
private fun CastRow() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf("[Actor]" to "[Role]", "[Actor]" to "[Role]", "[Actor]" to "[Role]", "[Director]" to "Director").forEachIndexed { i, (n, r) ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(20.dp).clip(CircleShape).background(listOf(C.Raised, Color(0xFF33395A), Color(0xFF2F4A42), Color(0xFF4B3F2A))[i]))
                Column { T(n, 8.sp, weight = FontWeight.SemiBold); T(r, 7.sp, C.Muted) }
            }
        }
        T("cast photos from TMDB", 8.sp, C.Faint)
    }
}

@Composable
private fun EpisodeCard(state: AppState, t: Title, e: Episode) {
    val pr = state.current.progress[e.id]
    Column(Modifier.width(170.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        FocusBox(
            { if (e.airsLater) state.say("Airs Friday. You'll see it here when it's out.") else state.go(Screen.Streams(t.id, e.id)) },
            Modifier.fillMaxWidth().height(96.dp), background = if (e.airsLater) C.Panel else t.tint,
        ) {
            if (pr?.isWatched() == true) Box(Modifier.fillMaxSize().padding(6.dp), contentAlignment = Alignment.TopEnd) {
                Box(Modifier.size(18.dp).clip(CircleShape).background(C.Bg.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) { Icon(Icons.Check, C.Accent, 11.dp) }
            }
            if (pr != null && !pr.isWatched()) Box(Modifier.fillMaxSize().padding(7.dp), contentAlignment = Alignment.BottomCenter) { Progress(pr.ratio(), Modifier.fillMaxWidth()) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { T("E${e.number}", 10.sp, C.Accent, FontWeight.SemiBold); T(e.title, 10.sp, weight = FontWeight.SemiBold, maxLines = 1) }
        T(if (e.airsLater) "Airs Fri 14 Nov" else if (pr != null && !pr.isWatched()) "${((1 - pr.ratio()) * e.minutes).toInt()} min left" else "${e.minutes} min", 9.sp, C.Muted)
    }
}

@Composable
fun StreamsScreen(state: AppState, titleId: String, videoId: String) {
    val t = Mock.title(titleId)
    val ep = Mock.episode(videoId)
    val enabled = state.current.addons.filter { it.streams }.map { it.id }.toSet()
    val streams = mockStreams.filter { it.addonId in enabled }
    var filter by remember { mutableStateOf("All") }
    val shown = streams.filter {
        when (filter) { "4K" -> it.quality == "4K"; "1080p" -> it.quality == "1080p"; "Plays fully" -> it.play == Playability.FULL; else -> true }
    }
    val first = rememberInitialFocus(filter)
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(t.tint))
        Box(Modifier.fillMaxSize().background(Color(0xC706070A)))
        Column(Modifier.padding(start = 48.dp, top = 60.dp)) { T(t.name, 38.sp, C.Text.copy(alpha = 0.5f), FontWeight.Bold) }
        Column(
            Modifier.align(Alignment.CenterEnd).width(410.dp).fillMaxHeight().background(C.Panel).border(1.dp, C.Line).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            T(t.name + (ep?.let { " · S${it.season} E${it.number}" } ?: ""), 9.sp, C.Muted)
            T("Choose a stream", 22.sp, weight = FontWeight.Bold)
            T("Ranked for TCL C6K + soundbar on eARC · ${streams.size} streams from ${enabled.size} addons", 9.sp, C.Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("All", "4K", "1080p", "Plays fully").forEach { f ->
                    val on = f == filter
                    FocusBox({ filter = f }, Modifier.height(22.dp), RoundedCornerShape(50), background = if (on) C.Text else Color.Transparent, focusedBackground = if (on) C.Text else C.Raised) {
                        T(f, 9.sp, if (on) C.Bg else C.Soft, FontWeight.SemiBold, Modifier.padding(horizontal = 10.dp))
                    }
                }
            }
            if (shown.isEmpty()) T(if (enabled.isEmpty()) "No stream addons for this profile. Add one in Settings › Addons." else "No streams match this filter.", 11.sp, C.Muted)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.weight(1f)) {
                items(shown) { s ->
                    val note = when (s.play) { Playability.FULL -> C.Good; Playability.FALLBACK -> C.Warn; Playability.NO -> C.Faint }
                    FocusBox(
                        { if (s.play == Playability.NO) state.say("Torrent streams need a streaming server; not supported") else state.go(Screen.Player(titleId, videoId, s)) },
                        Modifier.fillMaxWidth(), RoundedCornerShape(8.dp), 1.02f,
                        focusRequester = if (s == shown.first()) first else null, background = Color(0xFF161A23), focusedBackground = C.Raised,
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                T(s.quality, 12.sp, if (s.play == Playability.NO) C.Faint else C.Text, FontWeight.Bold)
                                T(s.formats, 10.sp, C.Soft, modifier = Modifier.weight(1f), maxLines = 1)
                                T(s.size, 9.sp, C.Muted)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Box(Modifier.size(5.dp).clip(CircleShape).background(note)); T(s.note, 9.sp, note, maxLines = 2)
                            }
                            T(defaultAddons.first { it.id == s.addonId }.name + if (s.play == Playability.NO) " · torrent" else " · direct link", 8.sp, C.Faint)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlayerScreen(state: AppState, titleId: String, videoId: String, stream: StreamOption) {
    val t = Mock.title(titleId)
    val ep = Mock.episode(videoId)
    val durationMs = (ep?.minutes ?: t.minutes) * 60_000L
    val profile = state.current
    var position by remember(videoId) { mutableLongStateOf(profile.progress[videoId].resumeAtMs()) }
    var playing by remember { mutableStateOf(true) }
    var overlay by remember { mutableStateOf(true) }
    var menu by remember { mutableStateOf(false) }
    var lastInput by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var audio by remember { mutableIntStateOf(0) }
    var subs by remember { mutableIntStateOf(1) }
    val root = remember { FocusRequester() }
    val menuFocus = remember { FocusRequester() }
    val next = ep?.let { Mock.nextEpisode(titleId, it.id) }

    fun save() { profile.progress[videoId] = ProgressEntry(titleId, videoId, position, durationMs, System.currentTimeMillis()) }

    LaunchedEffect(Unit) { delay(60); runCatching { root.requestFocus() } }
    LaunchedEffect(playing) { while (playing) { delay(250); position = (position + 250 * 24).coerceAtMost(durationMs); if (position >= durationMs) playing = false } }
    LaunchedEffect(lastInput, menu) { delay(5000); if (!menu && playing) overlay = false }
    LaunchedEffect(menu) { if (menu) { delay(60); runCatching { menuFocus.requestFocus() } } else runCatching { root.requestFocus() } }
    DisposableEffect(Unit) {
        val interceptor = { if (menu) { menu = false; true } else { save(); false } }
        state.backInterceptors.add(interceptor)
        onDispose { state.backInterceptors.remove(interceptor) }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black).focusRequester(root).focusable().onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown || menu) return@onPreviewKeyEvent false
            lastInput = System.currentTimeMillis()
            val wasHidden = !overlay
            overlay = true
            when (e.key) {
                Key.Enter, Key.Spacebar -> { if (!wasHidden) playing = !playing; true }
                Key.DirectionLeft -> { position = (position - 10_000).coerceAtLeast(0); true }
                Key.DirectionRight -> { position = (position + 10_000).coerceAtMost(durationMs); true }
                Key.DirectionUp, Key.DirectionDown -> { if (!wasHidden) menu = true; true }
                else -> false
            }
        },
    ) {
        Box(Modifier.fillMaxSize().background(Color(0xFF1B2434)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                T("SIMULATED PLAYBACK (24× speed)", 9.sp, C.Faint, FontWeight.SemiBold)
                T(stream.quality + " · " + stream.formats, 10.sp, C.Faint)
            }
        }
        if (overlay || menu) {
            Box(Modifier.fillMaxWidth().height(110.dp).background(Brush.verticalGradient(0f to Color(0xC0000000), 1f to Color.Transparent)))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color(0xE0000000))))
            Row(Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 28.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    T(t.name, 10.sp, C.Soft)
                    T(ep?.let { "S${it.season} E${it.number} · ${it.title}" } ?: t.name, 16.sp, weight = FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { if (profile.simklUser != null) Chip("● Scrobbling to Simkl", C.Good, C.Glass); Chip(stream.quality + if ("Dolby Vision" in stream.formats) " Dolby Vision" else " HDR10", bg = C.Glass); Chip(stream.audio.substringBefore(" ·").ifEmpty { "Audio" }, bg = C.Glass); Chip("23.976 Hz", bg = C.Glass) }
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 48.dp, vertical = 30.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Progress(position.toDouble() / durationMs, Modifier.fillMaxWidth(), 4.dp)
                Row { T(position.clock(), 10.sp, C.Soft); Spacer(Modifier.weight(1f)); T("−" + (durationMs - position).clock(), 10.sp, C.Soft) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Rewind, C.Text, 16.dp); Icon(if (playing) Icons.Pause else Icons.Play, C.Text, 22.dp); Icon(Icons.Forward, C.Text, 16.dp)
                    Spacer(Modifier.weight(1f))
                    T("Enter play/pause · ← → 10 s · ↑ audio & subtitles · Esc exit (saves progress)", 9.sp, C.Muted)
                    if (position >= durationMs - 120_000 && next != null) PillButton("Next episode", { save(); state.replace(Screen.Streams(titleId, next)) }, primary = true)
                }
            }
        }
        if (menu) {
            Row(
                Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = 120.dp).width(330.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xF512151D)).border(1.dp, C.Line, RoundedCornerShape(10.dp)).padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    T("AUDIO", 8.sp, C.Muted, FontWeight.SemiBold)
                    listOf(stream.audio.ifEmpty { "Original" }, "English · Dolby Digital 5.1", "Spanish · Dolby Digital 5.1").forEachIndexed { i, a ->
                        MenuItem(a, i == audio, if (i == 0) menuFocus else null) { audio = i; state.say("Audio: $a (saved for ${profile.name})") }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    T("SUBTITLES", 8.sp, C.Muted, FontWeight.SemiBold)
                    listOf("Off", "English · embedded", "English · [Addon C]", "Style…").forEachIndexed { i, s ->
                        MenuItem(s, i == subs, null) { if (i == 3) state.go(Screen.Settings("Subtitles")) else subs = i }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuItem(label: String, selected: Boolean, focus: FocusRequester?, onClick: () -> Unit) {
    FocusBox(onClick, Modifier.fillMaxWidth().height(26.dp), RoundedCornerShape(6.dp), 1.02f, focusRequester = focus, background = if (selected) C.Raised else Color.Transparent, focusedBackground = C.Raised) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.width(10.dp)) { if (selected) Icon(Icons.Check, C.Accent, 10.dp) }
            T(label, 9.sp, maxLines = 1)
        }
    }
}
