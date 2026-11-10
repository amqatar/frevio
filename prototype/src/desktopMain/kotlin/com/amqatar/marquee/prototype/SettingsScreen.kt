package com.amqatar.marquee.prototype

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val sections = listOf("Profile", "Playback", "Audio", "Subtitles", "Addons", "Integrations", "Devices & account", "About & updates")

private val choices = mapOf(
    "audio.fallback" to listOf("Automatic", "Always PCM", "Always Dolby Digital 5.1", "Stereo"),
    "audio.dtsToAc3" to listOf("On", "Off"),
    "audio.preferPassthrough" to listOf("On", "Off"),
    "audio.language" to listOf("English", "Original", "Arabic", "Spanish"),
    "audio.night" to listOf("Off", "On"),
    "play.frameRate" to listOf("Match content", "Seamless only", "Off"),
    "play.autoNext" to listOf("On", "Off"),
    "play.skipIntro" to listOf("Ask", "Always", "Never"),
    "play.engine" to listOf("Automatic", "Media3 only", "mpv only"),
    "subs.language" to listOf("Off", "English", "Arabic", "Forced only"),
    "subs.size" to listOf("Medium", "Large", "Small"),
    "subs.background" to listOf("Shadow", "Box", "None"),
    "meta.tmdb" to listOf("On", "Off"),
    "meta.language" to listOf("English", "Arabic", "Original"),
    "meta.ratings" to listOf("IMDb, RT, Metacritic, Letterboxd", "IMDb, RT", "IMDb only"),
    "ai.recommendations" to listOf("On", "Off"),
)

@Composable
fun SettingsScreen(state: AppState, section: String) {
    val first = rememberInitialFocus(section)
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(240.dp).fillMaxHeight().padding(start = 48.dp, top = 36.dp, end = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) { Icon(Icons.Back, C.Muted, 10.dp); T("Esc to go back", 10.sp, C.Muted) }
            T("Settings", 26.sp, weight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))
            sections.forEach { s ->
                val on = s == section
                FocusBox({ state.replace(Screen.Settings(s)) }, Modifier.fillMaxWidth().height(30.dp), RoundedCornerShape(7.dp), 1.02f,
                    focusRequester = if (on) first else null, background = if (on) C.Raised else Color.Transparent, focusedBackground = C.Raised) {
                    T(s, 11.sp, if (on) C.Accent else C.Soft, FontWeight.SemiBold, Modifier.padding(horizontal = 11.dp))
                }
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(C.Raised))
        LazyColumn(Modifier.weight(1f).fillMaxHeight(), contentPadding = PaddingValues(horizontal = 48.dp, vertical = 36.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            item {
                T(section, 20.sp, weight = FontWeight.Bold)
                T(if (section in listOf("Devices & account", "About & updates")) "This TV" else "For profile ${state.current.name} · synced to your account", 10.sp, C.Muted, modifier = Modifier.padding(top = 3.dp, bottom = 8.dp))
            }
            when (section) {
                "Profile" -> {
                    val p = state.current
                    item { ActionRow("Name", "Rename on the TV or from the phone app", p.name) { p.name = if (p.name.endsWith("*")) p.name.dropLast(1) else p.name + "*"; state.say("Renamed (prototype: toggles a *)") } }
                    item { ActionRow("PIN lock", "Asks for a 4-digit PIN when this profile is picked", if (p.pin != null) "On (${p.pin})" else "Off") { p.pin = if (p.pin == null) "1234" else null } }
                    item { ActionRow("Kids profile", "Only titles marked for kids, in every catalog", if (p.kids) "On" else "Off") { p.kids = !p.kids } }
                    item { ActionRow("Delete profile", "Removes its library, progress and settings from your account", "") {
                        if (state.profiles.size == 1) state.say("An account needs at least one profile")
                        else { state.profiles.remove(p); state.current = state.profiles.first(); state.reset(Screen.Profiles); state.say("Profile deleted") }
                    } }
                }
                "Playback" -> {
                    item { ChoiceRow(state, "play.frameRate", "Match frame rate", "Switches the TV's refresh rate to the video's (23.976, 24, 25, 50…) to stop judder") }
                    item { ChoiceRow(state, "play.autoNext", "Play next episode automatically", "Countdown at the end credits") }
                    item { ChoiceRow(state, "play.skipIntro", "Skip intro", "When the stream provides intro markers") }
                    item { ChoiceRow(state, "play.engine", "Player engine", "Automatic: Media3 first, mpv when a file won't play") }
                }
                "Audio" -> {
                    item { SoundSystemCard() }
                    item { ChoiceRow(state, "audio.fallback", "When the soundbar can't play a format", "Order tried: as encoded, multichannel PCM, Dolby Digital 5.1, stereo") }
                    item { ChoiceRow(state, "audio.dtsToAc3", "Convert DTS to Dolby Digital 5.1", "Keeps surround on soundbars without DTS. Small loss in detail") }
                    item { ChoiceRow(state, "audio.preferPassthrough", "Prefer a track the soundbar plays as encoded", "Picks Dolby Atmos (DD+) over TrueHD when a file has both") }
                    item { ChoiceRow(state, "audio.language", "Preferred audio language", "Falls back to the original language") }
                    item { ChoiceRow(state, "audio.night", "Night mode", "Compresses loud scenes. Decoded audio only") }
                }
                "Subtitles" -> {
                    item { ChoiceRow(state, "subs.language", "Default subtitles", "Embedded first, then subtitle addons") }
                    item { ChoiceRow(state, "subs.size", "Size", "") }
                    item { ChoiceRow(state, "subs.background", "Background", "") }
                }
                "Addons" -> {
                    val list = state.current.addons
                    list.forEachIndexed { i, a ->
                        item(key = a.id) {
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(C.Panel).border(1.dp, C.Line, RoundedCornerShape(8.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(Modifier.size(28.dp).clip(RoundedCornerShape(7.dp)).background(C.Raised), contentAlignment = Alignment.Center) { T(a.name.trim('[').take(1), 12.sp, weight = FontWeight.Bold) }
                                Column(Modifier.weight(1f)) {
                                    T(a.name, 12.sp, weight = FontWeight.SemiBold)
                                    T(a.provides + if (a.catalogs.isNotEmpty()) " · rows: " + a.catalogs.joinToString() else "", 9.sp, C.Muted)
                                }
                                IconButton(Icons.Up, i > 0) { list.add(i - 1, list.removeAt(i)) }
                                IconButton(Icons.Down, i < list.lastIndex) { list.add(i + 1, list.removeAt(i)) }
                                IconButton(Icons.Trash, true) { list.removeAt(i); state.say("${a.name} removed for ${state.current.name}: its rows and streams are gone") }
                            }
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                            PillButton("Install addon (QR to phone)", { state.say("Shows a QR code; paste the addon link on your phone") }, icon = Icons.Plus)
                            PillButton("Reset to defaults", { list.clear(); list.addAll(defaultAddons); state.say("Addons reset") })
                        }
                        T("Order matters: the first addon with details for a title is used, and home rows follow this order.", 9.sp, C.Muted, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                "Integrations" -> {
                    val p = state.current
                    item {
                        ActionRow(
                            "Simkl",
                            if (p.simklUser != null) "Tracking ${p.name}'s watching · last sync ${p.simklLastSync ?: "never"}" else "Track what ${p.name} watches, import history into Continue watching",
                            if (p.simklUser != null) "Connected as ${p.simklUser}" else "Connect",
                        ) {
                            if (p.simklUser != null) { p.simklUser = null; p.simklLastSync = null; state.say("Simkl disconnected for ${p.name}") }
                            else state.go(Screen.SimklPin(p.id))
                        }
                    }
                    if (p.simklUser != null) item { ActionRow("Sync now", "Pulls Simkl history and watchlist into this profile", "") { p.simklLastSync = "just now"; state.say("Synced with Simkl (prototype)") } }
                    item { ChoiceRow(state, "meta.tmdb", "Enriched metadata (TMDB)", "Backdrops, logos, cast photos, trailers, localized text") }
                    item { ChoiceRow(state, "meta.language", "Metadata language", "Titles and synopses where TMDB has a translation") }
                    item { ChoiceRow(state, "meta.ratings", "Ratings on title pages", "From MDBList: IMDb, Rotten Tomatoes, Metacritic, Letterboxd") }
                    item {
                        ActionRow(
                            "Claude API key",
                            if (state.aiKeyLast4 != null) "Your key powers AI picks for every profile; usage bills to your Anthropic account" else "Needed for AI recommendations. Added from your phone; stored encrypted on the server",
                            if (state.aiKeyLast4 != null) "Set ••••${state.aiKeyLast4}" else "Add key",
                        ) { state.go(Screen.AiKey) }
                    }
                    if (state.aiKeyLast4 != null) item { ActionRow("Remove Claude key", "Deletes it from the server; AI picks stop", "") { state.aiKeyLast4 = null; state.say("Claude key removed") } }
                    item { ChoiceRow(state, "ai.recommendations", "AI recommendations for ${state.current.name}", if (state.aiKeyLast4 == null) "Needs a Claude API key. Until then: Because you watched rows" else "A Recommended for you row with a reason per pick. Off: Because you watched rows") }
                    if (state.current.notInterested.isNotEmpty()) item { ActionRow("Not interested list", "${state.current.notInterested.size} titles hidden from recommendations", "Clear") { state.current.notInterested.clear(); state.say("Not interested list cleared") } }
                    item { T("API keys for TMDB, MDBList, Simkl and Claude are held by the Marquee server, never on this TV.", 9.sp, C.Muted, modifier = Modifier.padding(top = 6.dp)) }
                }
                "Devices & account" -> {
                    item { ActionRow("Account", if (state.signedIn) "Profiles, library and progress sync to every device" else "Not signed in: everything stays on this TV", if (state.signedIn) "[account email]" else "Sign in") {
                        if (state.signedIn) { state.signedIn = false; state.say("Signed out (prototype)") } else state.go(Screen.SignIn)
                    } }
                    item { ActionRow("Sign in with your phone", "QR code and short code, no typing on the TV", "") { state.go(Screen.SignIn) } }
                    item { ActionRow("Always ask who's watching", "Off: open straight into the last profile used on this TV", if (state.alwaysAsk) "On" else "Off") { state.alwaysAsk = !state.alwaysAsk } }
                    item { ActionRow("Linked devices", "TCL C6K (this TV) · [Phone] · revoke any", "2") { state.say("Device list from GET /v1/devices") } }
                }
                "About & updates" -> {
                    item { ActionRow("Version", "Updates download from your release server and install over this one", "1.0.0 prototype") { state.say("You're up to date (prototype)") } }
                    item { ActionRow("Check for updates", "GET /v1/app/latest", "") { state.say("You're up to date (prototype)") } }
                    item { ActionRow("Player diagnostics", "Decoder, HDR, dropped frames and audio path overlay", "Off") { state.say("Toggles the overlay from the player test app") } }
                    item { ActionRow("Data sources", "This product uses the TMDB API but is not endorsed or certified by TMDB. Ratings via MDBList. Tracking via Simkl. Recommendations generated with Claude.", "TMDB") { state.say("TMDB logo shown here in the real app (required attribution)") } }
                    item { T("IMAGE CREDITS", 8.sp, C.Muted, FontWeight.SemiBold, Modifier.padding(top = 10.dp)) }
                    if (Art.credits.isEmpty()) item { T("No artwork bundled in this build: placeholders shown.", 9.sp, C.Muted) }
                    Art.credits.forEach { c -> item { T("${c.file} · ${c.author} · ${c.license}", 8.sp, C.Muted, maxLines = 2) } }
                    item { T("Images of Blender Foundation open movies from Wikimedia Commons, used under their stated licences.", 8.sp, C.Faint) }
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(state: AppState, key: String, label: String, help: String) {
    val p = state.current
    val value = p.setting(key)
    ActionRow(label, help, value) {
        val opts = choices.getValue(key)
        p.settings[key] = opts[(opts.indexOf(value) + 1) % opts.size]
    }
}

@Composable
private fun ActionRow(label: String, help: String, value: String, onClick: () -> Unit) {
    FocusBox(onClick, Modifier.fillMaxWidth(), RoundedCornerShape(8.dp), 1.01f, background = C.Panel, focusedBackground = C.Raised) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                T(label, 11.sp, weight = FontWeight.SemiBold)
                if (help.isNotEmpty()) T(help, 9.sp, C.Muted)
            }
            if (value.isNotEmpty()) T(value, 10.sp, if (value == "Off") C.Muted else C.Text, FontWeight.SemiBold)
            Icon(Icons.Chevron, C.Faint, 10.dp)
        }
    }
}

@Composable
private fun IconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    FocusBox(onClick, Modifier.size(26.dp), CircleShape, enabled = enabled, background = C.Raised) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(icon, if (enabled) C.Soft else C.LineStrong, 12.dp) }
    }
}

@Composable
private fun SoundSystemCard() {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.Panel).border(1.dp, C.Line, RoundedCornerShape(10.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            T("Soundbar on HDMI eARC", 12.sp, weight = FontWeight.SemiBold); T("detected now · up to 8 channels · sample data", 9.sp, C.Muted)
        }
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("Dolby Digital" to true, "Dolby Digital Plus" to true, "Dolby Atmos (DD+)" to true, "TrueHD" to false, "DTS" to false, "DTS-HD MA" to false, "DTS:X" to false, "PCM 7.1" to true).forEach { (f, ok) ->
                Chip((if (ok) "✓ " else "✗ ") + f, if (ok) Color(0xFF9FE3AE) else C.Muted, if (ok) Color(0xFF1F3326) else C.Raised)
            }
        }
        T("✓ sent untouched · ✗ needs a fallback below. Real values come from the player test results.", 9.sp, C.Muted)
    }
}
