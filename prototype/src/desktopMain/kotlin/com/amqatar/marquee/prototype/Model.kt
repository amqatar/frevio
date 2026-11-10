package com.amqatar.marquee.prototype

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.amqatar.marquee.shared.ContinueItem
import com.amqatar.marquee.shared.ProgressEntry
import com.amqatar.marquee.shared.continueWatching

// ---------- mock catalog (what metadata addons would return) ----------

enum class Kind { MOVIE, SERIES }

data class Episode(val id: String, val season: Int, val number: Int, val title: String, val minutes: Int, val airsLater: Boolean = false)

data class Title(
    val id: String,
    val kind: Kind,
    val name: String,
    val year: String,
    val rating: String,
    val genres: List<String>,
    val minutes: Int,
    val tint: Color,
    val kids: Boolean = false,
    val catalog: String,
    val addon: String,
    val episodes: List<Episode> = emptyList(),
) {
    val seasons: List<Int> get() = episodes.map { it.season }.distinct().sorted()
    val synopsis: String get() = "Synopsis from the metadata addon for $name. Two to three lines on screen."

    /** Mock MDBList ratings (IMDb, Rotten Tomatoes critics/audience, Metacritic, Letterboxd). */
    val ratings: List<Pair<String, String>> get() {
        val n = id.drop(1).toInt()
        return listOf(
            "IMDb" to rating,
            "Rotten Tomatoes" to "${60 + n * 3 % 39}%",
            "Audience" to "${55 + n * 7 % 44}%",
            "Metacritic" to "${50 + n * 5 % 45}",
            "Letterboxd" to "${2 + n % 3}.${(n * 7) % 10}",
        )
    }
}

private val tints = listOf(0xFF2B3A55, 0xFF4A3046, 0xFF2F4A42, 0xFF4B3F2A, 0xFF33395A, 0xFF4A2F2F, 0xFF2C4452, 0xFF3F3A52).map { Color(it) }

object Mock {
    val movies: List<Title> = (1..14).map { i ->
        Title(
            id = "m$i", kind = Kind.MOVIE, name = "Sample Movie $i", year = "${2012 + i}", rating = "${6 + i % 4}.${i % 10}",
            genres = listOf(listOf("Drama", "Thriller"), listOf("Comedy"), listOf("Action", "Sci-Fi"), listOf("Animation", "Family"))[i % 4],
            minutes = 92 + i * 4, tint = tints[i % tints.size], kids = i % 4 == 3,
            catalog = if (i <= 9) "Popular movies" else "New releases", addon = if (i <= 9) "Cinemeta" else "[Addon B]",
        )
    }
    val series: List<Title> = (1..6).map { i ->
        val seasons = 2 + i % 2
        Title(
            id = "s$i", kind = Kind.SERIES, name = "Sample Series ${'A' + i - 1}", year = "${2017 + i}–", rating = "${7 + i % 3}.${(i * 3) % 10}",
            genres = listOf(listOf("Drama", "Mystery"), listOf("Comedy"), listOf("Crime", "Thriller"), listOf("Animation", "Family"))[i % 4],
            minutes = 48, tint = tints[(i + 3) % tints.size], kids = i % 4 == 3, catalog = "Popular series", addon = "Cinemeta",
            episodes = (1..seasons).flatMap { s ->
                (1..8).map { e -> Episode("s$i:$s:$e", s, e, "Episode $e title", 42 + (e * 3) % 15, airsLater = s == seasons && e == 8) }
            },
        )
    }
    val all: List<Title> = movies + series
    fun title(id: String) = all.first { it.id == id }
    fun episode(videoId: String): Episode? = series.firstNotNullOfOrNull { t -> t.episodes.firstOrNull { it.id == videoId } }
    fun nextEpisode(titleId: String, videoId: String): String? {
        val eps = title(titleId).episodes.filter { !it.airsLater }
        val i = eps.indexOfFirst { it.id == videoId }
        return if (i >= 0) eps.getOrNull(i + 1)?.id else null
    }
}

data class Addon(val id: String, val name: String, val provides: String, val catalogs: List<String>, val streams: Boolean)

val defaultAddons = listOf(
    Addon("cinemeta", "Cinemeta", "Catalogs, metadata", listOf("Popular movies", "Popular series"), streams = false),
    Addon("b", "[Addon B]", "Catalogs, streams", listOf("New releases"), streams = true),
    Addon("a", "[Addon A]", "Streams", emptyList(), streams = true),
    Addon("c", "[Addon C]", "Streams, subtitles", emptyList(), streams = true),
)

// Stream rows as the stream picker would rank them for the TCL C6K + soundbar.
enum class Playability { FULL, FALLBACK, NO }

data class StreamOption(val quality: String, val formats: String, val size: String, val note: String, val play: Playability, val addonId: String, val audio: String)

val mockStreams = listOf(
    StreamOption("4K", "Dolby Vision · HEVC · Atmos (DD+)", "9.8 GB", "Best match: Dolby Vision to the TV, Atmos to the soundbar", Playability.FULL, "a", "Dolby Atmos (DD+) · bitstream"),
    StreamOption("4K", "HDR10 · HEVC · TrueHD Atmos", "41.2 GB", "TrueHD sent as 7.1 PCM (soundbar lacks TrueHD), no height channels", Playability.FALLBACK, "b", "TrueHD → 7.1 PCM"),
    StreamOption("4K", "HDR10 · HEVC · DTS-HD MA", "38.6 GB", "DTS converted to Dolby Digital 5.1", Playability.FALLBACK, "b", "DTS-HD → Dolby Digital 5.1"),
    StreamOption("1080p", "SDR · AV1 · E-AC-3 5.1", "2.1 GB", "Plays as encoded", Playability.FULL, "c", "Dolby Digital Plus 5.1 · bitstream"),
    StreamOption("4K", "HDR10 · HEVC · Atmos", "22.4 GB", "Torrent link: not supported without a streaming server", Playability.NO, "c", ""),
)

// ---------- per-profile state (what the API stores under /v1/profiles/{id}/…) ----------

class Profile(id: String, name: String, val color: Color, kids: Boolean = false, pin: String? = null) {
    val id = id
    var name by mutableStateOf(name)
    var kids by mutableStateOf(kids)
    var pin by mutableStateOf(pin)
    val library = mutableStateListOf<String>()
    val progress = mutableStateMapOf<String, ProgressEntry>()
    val settings = mutableStateMapOf<String, String>()
    val addons = mutableStateListOf<Addon>().apply { addAll(defaultAddons) }
    /** Simkl account linked to this profile (token held by the API server, never the TV). */
    var simklUser by mutableStateOf<String?>(null)
    var simklLastSync by mutableStateOf<String?>(null)

    fun continueRow(): List<ContinueItem> = continueWatching(progress.values) { t, v -> Mock.nextEpisode(t, v) }

    fun setting(key: String): String = settings[key] ?: SettingDefaults[key] ?: ""
}

sealed interface Screen {
    data object Profiles : Screen
    data class Pin(val profileId: String) : Screen
    data class Main(val tab: Tab) : Screen
    data class Details(val titleId: String) : Screen
    data class Streams(val titleId: String, val videoId: String) : Screen
    data class Player(val titleId: String, val videoId: String, val stream: StreamOption) : Screen
    data class Settings(val section: String = "Audio") : Screen
    data object SignIn : Screen
    data class SimklPin(val profileId: String) : Screen
}

enum class Tab(val label: String) { SEARCH("Search"), HOME("Home"), MOVIES("Movies"), SERIES("Series"), LIBRARY("Library") }

class AppState {
    val profiles = mutableStateListOf(
        Profile("p1", "Sam", Color(0xFF3B5B8C)).apply { seedProgress() },
        Profile("p2", "Alex", Color(0xFF7A4A73), pin = "1234"),
        Profile("p3", "Kids", Color(0xFF3E7A5C), kids = true),
    )
    var current by mutableStateOf(profiles[0])
    var signedIn by mutableStateOf(true)
    var alwaysAsk by mutableStateOf(true)
    var toast by mutableStateOf<String?>(null)

    private val stack = mutableStateListOf<Screen>(Screen.Profiles)
    val screen: Screen get() = stack.last()

    /** Screens with an open menu register here so Back closes the menu first. */
    val backInterceptors = mutableStateListOf<() -> Boolean>()

    fun go(s: Screen) { stack.add(s) }
    fun replace(s: Screen) { stack.removeAt(stack.lastIndex); stack.add(s) }
    fun reset(s: Screen) { stack.clear(); stack.add(s) }
    fun back() {
        if (backInterceptors.lastOrNull()?.invoke() == true) return
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun choose(p: Profile) {
        current = p
        reset(Screen.Main(Tab.HOME))
    }

    fun visible(titles: List<Title>): List<Title> {
        val catalogs = current.addons.flatMap { it.catalogs }.toSet()
        return titles.filter { it.catalog in catalogs && (!current.kids || it.kids) }
    }

    fun say(msg: String) { toast = msg }
}

private fun Profile.seedProgress() {
    val now = System.currentTimeMillis()
    val min = 60_000L
    progress["s1:2:4"] = ProgressEntry("s1", "s1:2:4", 30 * min, 48 * min, now - 1 * min)
    progress["m3"] = ProgressEntry("m3", "m3", 41 * min, 104 * min, now - 60 * min)
    progress["s2:1:6"] = ProgressEntry("s2", "s2:1:6", 47 * min, 48 * min, now - 120 * min)
    progress["m7"] = ProgressEntry("m7", "m7", 96 * min, 120 * min, now - 300 * min)
    listOf("s1:1:1", "s1:1:2", "s1:1:3").forEach { progress[it] = ProgressEntry("s1", it, 47 * min, 48 * min, now - 3000 * min) }
    library.addAll(listOf("s1", "m3", "m9"))
}

val SettingDefaults = mapOf(
    "audio.fallback" to "Automatic",
    "audio.dtsToAc3" to "On",
    "audio.preferPassthrough" to "On",
    "audio.language" to "English",
    "audio.night" to "Off",
    "play.frameRate" to "Match content",
    "play.autoNext" to "On",
    "play.skipIntro" to "Ask",
    "play.engine" to "Automatic",
    "subs.language" to "Off",
    "subs.size" to "Medium",
    "subs.background" to "Shadow",
    "meta.tmdb" to "On",
    "meta.language" to "English",
    "meta.ratings" to "IMDb, RT, Metacritic, Letterboxd",
)
