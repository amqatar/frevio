package com.frevio.player.prototype

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frevio.player.shared.ContinueItem

@Composable
fun MainScreen(state: AppState, tab: Tab) {
    Row(Modifier.fillMaxSize()) {
        NavRail(state, tab)
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (tab) {
                Tab.HOME -> HomeContent(state)
                Tab.SEARCH -> SearchContent(state)
                Tab.MOVIES -> GridContent(state, "Movies", state.visible(Mock.movies))
                Tab.SERIES -> GridContent(state, "Series", state.visible(Mock.series))
                Tab.LIBRARY -> GridContent(state, "Library · ${state.current.name}", state.current.library.map(Mock::title), empty = "Add titles with the + button on any details page.")
            }
        }
    }
}

@Composable
private fun NavRail(state: AppState, tab: Tab) {
    Column(
        Modifier.width(56.dp).fillMaxHeight().background(C.Bg).padding(vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FocusBox({ state.reset(Screen.Profiles) }, Modifier.size(28.dp), CircleShape, background = state.current.color) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { T(state.current.name.take(1), 11.sp, Color.White, FontWeight.Bold) }
        }
        Spacer(Modifier.height(10.dp))
        listOf(Tab.SEARCH to Icons.Search, Tab.HOME to Icons.Home, Tab.MOVIES to Icons.Movie, Tab.SERIES to Icons.Series, Tab.LIBRARY to Icons.Library).forEach { (t, icon) ->
            val active = t == tab
            FocusBox({ state.replace(Screen.Main(t)) }, Modifier.size(32.dp), RoundedCornerShape(9.dp), background = if (active) C.Raised else Color.Transparent, focusedBackground = C.Raised) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(icon, if (active) C.Accent else C.Muted, 15.dp) }
            }
        }
        Spacer(Modifier.weight(1f))
        FocusBox({ state.go(Screen.Settings()) }, Modifier.size(32.dp), RoundedCornerShape(9.dp), focusedBackground = C.Raised) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Settings, C.Muted, 15.dp) }
        }
    }
}

@Composable
private fun HomeContent(state: AppState) {
    val profile = state.current
    val cw = profile.continueRow().filter { !profile.kids || Mock.title(it.titleId).kids }
    val catalogs = profile.addons.flatMap { a -> a.catalogs.map { it to a.name } }
    val featured = state.visible(Mock.series).firstOrNull() ?: state.visible(Mock.movies).firstOrNull()
    val heroFocus = rememberInitialFocus(profile.id)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (featured != null) item { Hero(state, featured, heroFocus) }
        if (cw.isNotEmpty()) item {
            CatalogRow(title = "Continue watching") {
                items(cw, key = { it.titleId }) { ContinueCard(state, it) }
            }
        }
        val recs = state.recommendations()
        if (state.aiActive()) {
            if (recs.isNotEmpty()) item(key = "ai") {
                CatalogRow(title = "Recommended for you  ✦ AI") { items(recs, key = { "ai-" + it.first.id }) { (t, why) -> RecommendationCard(state, t, why, ai = true) } }
            }
        } else if (recs.isNotEmpty()) item(key = "byw") {
            // No key yet (or AI off): the no-AI fallback row, with a nudge to add a key.
            CatalogRow(title = "Because you watched") {
                if (state.aiKeyLast4 == null && profile.setting("ai.recommendations") == "On") item(key = "add-key") { AddKeyCard(state) }
                items(recs, key = { "byw-" + it.first.id }) { (t, why) -> RecommendationCard(state, t, why, ai = false) }
            }
        }
        item(key = "moods") { CatalogRow(title = "Browse by mood") { items(Shelves.moods, key = { it.name }) { ShelfCard(state, it) } } }
        catalogs.forEach { (catalog, addon) ->
            val titles = state.visible(Mock.all).filter { it.catalog == catalog }
            if (titles.isNotEmpty()) item(key = catalog) {
                CatalogRow(title = "$catalog · $addon") { items(titles, key = { it.id }) { PosterCard(state, it) } }
            }
        }
        item(key = "genres") { CatalogRow(title = "Browse by genre") { items(Shelves.genres, key = { it.name }) { ShelfCard(state, it) } } }
        if (catalogs.isEmpty()) item {
            Box(Modifier.padding(36.dp)) { T("No catalog addons for this profile. Add one in Settings › Addons.", 12.sp, C.Muted) }
        }
    }
}

@Composable
private fun Hero(state: AppState, t: Title, focus: FocusRequester) {
    Box(Modifier.fillMaxWidth().height(300.dp)) {
        Box(Modifier.fillMaxSize().background(t.tint.copy(alpha = 0.9f)))
        ArtImage(t.backdropArt, Modifier.fillMaxSize())
        if (!Art.has(t.backdropArt)) Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.TopEnd) { T("BACKDROP", 8.sp, C.Faint) }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to C.Bg, 0.45f to C.Bg.copy(alpha = 0.85f), 1f to C.Bg.copy(alpha = 0.1f))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to C.Bg)))
        Column(Modifier.align(Alignment.BottomStart).padding(start = 36.dp, bottom = 22.dp).width(430.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            T("FEATURED", 9.sp, C.Accent, FontWeight.SemiBold)
            T(t.name, 40.sp, weight = FontWeight.Bold)
            T(listOf(t.year, if (t.kind == Kind.SERIES) "${t.seasons.size} seasons" else "${t.minutes / 60} h ${t.minutes % 60} min", "★ ${t.rating}", t.genres.joinToString()).joinToString("  ·  "), 11.sp, C.Soft)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { Tag("4K"); Tag("Dolby Vision"); Tag("Atmos") }
            T(t.synopsis, 11.sp, C.Soft, maxLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val video = if (t.kind == Kind.SERIES) t.episodes.first().id else t.id
                PillButton(if (t.kind == Kind.SERIES) "Play S1 E1" else "Play", { state.go(Screen.Streams(t.id, video)) }, primary = true, icon = Icons.Play, focusRequester = focus)
                PillButton("Details", { state.go(Screen.Details(t.id)) })
                LibraryButton(state, t)
            }
        }
    }
}

@Composable
fun LibraryButton(state: AppState, t: Title) {
    val inLib = t.id in state.current.library
    PillButton(if (inLib) "In library" else "", {
        if (inLib) state.current.library.remove(t.id) else state.current.library.add(t.id)
        state.say(if (inLib) "Removed from ${state.current.name}'s library" else "Added to ${state.current.name}'s library")
    }, icon = if (inLib) Icons.Check else Icons.Plus)
}

@Composable
private fun CatalogRow(title: String, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        T(title, 13.sp, weight = FontWeight.SemiBold, modifier = Modifier.padding(start = 36.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 36.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun ContinueCard(state: AppState, item: ContinueItem) {
    val t = Mock.title(item.titleId)
    val ep = Mock.episode(item.videoId)
    val sub = when {
        ep != null && !item.inProgress -> "Next: S${ep.season} · E${ep.number}"
        ep != null -> "S${ep.season} · E${ep.number} · ${((1 - item.ratio) * ep.minutes).toInt()} min left"
        else -> "${((1 - item.ratio) * t.minutes).toInt()} min left"
    }
    Column(Modifier.width(176.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FocusBox({ state.go(Screen.Streams(t.id, item.videoId)) }, Modifier.fillMaxWidth().height(99.dp), background = t.tint) {
            ArtImage(t.backdropArt, Modifier.fillMaxSize())
            if (!Art.has(t.backdropArt)) Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.TopStart) { T("EPISODE STILL", 7.sp, C.Soft) }
            if (item.inProgress) Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.BottomCenter) { Progress(item.ratio, Modifier.fillMaxWidth()) }
        }
        T(t.name, 11.sp, weight = FontWeight.SemiBold, maxLines = 1)
        T(sub, 9.sp, C.Muted, maxLines = 1)
    }
}

@Composable
fun PosterCard(state: AppState, t: Title, focus: FocusRequester? = null) {
    Column(Modifier.width(100.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        FocusBox({ state.go(Screen.Details(t.id)) }, Modifier.width(100.dp).height(150.dp), focusRequester = focus, background = t.tint) {
            ArtImage(t.posterArt, Modifier.fillMaxSize())
            if (!Art.has(t.posterArt)) Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.BottomStart) { T(t.name, 10.sp, weight = FontWeight.SemiBold) }
            if (t.id in state.current.library) Box(Modifier.fillMaxSize().padding(6.dp), contentAlignment = Alignment.TopEnd) {
                Box(Modifier.size(16.dp).clip(CircleShape).background(C.Bg.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) { Icon(Icons.Check, C.Accent, 10.dp) }
            }
        }
        T(t.year, 9.sp, C.Muted)
    }
}

@Composable
private fun AddKeyCard(state: AppState) {
    Column(Modifier.width(150.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FocusBox({ state.go(Screen.AiKey) }, Modifier.width(150.dp).height(150.dp), background = C.Panel, focusedBackground = C.Raised) {
            Column(Modifier.fillMaxSize().border(1.dp, C.LineStrong, RoundedCornerShape(8.dp)).padding(12.dp), verticalArrangement = Arrangement.Center) {
                T("✦ AI picks", 13.sp, C.Accent, FontWeight.Bold)
                T("Add your Claude API key to get picks chosen for you, with a reason for each.", 9.sp, C.Soft)
                T("Add key ›", 10.sp, weight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
            }
        }
        T("Uses your own Anthropic account", 8.sp, C.Muted, maxLines = 2)
    }
}

@Composable
private fun RecommendationCard(state: AppState, t: Title, why: String, ai: Boolean) {
    Column(Modifier.width(100.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FocusBox({ state.go(Screen.Details(t.id)) }, Modifier.width(100.dp).height(150.dp), background = t.tint) {
            ArtImage(t.posterArt, Modifier.fillMaxSize())
            if (!Art.has(t.posterArt)) Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.BottomStart) { T(t.name, 10.sp, weight = FontWeight.SemiBold) }
            if (ai) Box(Modifier.fillMaxSize().padding(5.dp), contentAlignment = Alignment.TopStart) {
                Box(Modifier.clip(RoundedCornerShape(4.dp)).background(C.Bg.copy(alpha = 0.75f)).padding(horizontal = 4.dp, vertical = 1.dp)) { T("✦ AI", 7.sp, C.Accent, FontWeight.Bold) }
            }
        }
        T(t.name, 9.sp, weight = FontWeight.SemiBold, maxLines = 1)
        T(why, 8.sp, C.Muted, maxLines = 2)
    }
}

/** Original card design: colour field, kicker, large title, outline ring. No film stills. */
@Composable
private fun ShelfCard(state: AppState, shelf: Shelf) {
    val count = state.visible(Mock.all).count { t -> t.genres.any { it in shelf.genres } }
    FocusBox({ state.go(Screen.Collection(shelf.kind, shelf.name)) }, Modifier.width(176.dp).height(99.dp), background = shelf.color) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.align(Alignment.TopEnd).offset(x = 28.dp, y = (-34).dp).size(110.dp).border(12.dp, Color.White.copy(alpha = 0.08f), CircleShape))
            Column(Modifier.align(Alignment.BottomStart).padding(10.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                T(shelf.kind.uppercase(), 7.sp, Color.White.copy(alpha = 0.7f), FontWeight.SemiBold)
                T(shelf.name, 15.sp, Color.White, FontWeight.Bold, maxLines = 1)
                T(if (shelf.line.isNotEmpty()) "${shelf.line} · $count titles" else "$count titles", 8.sp, Color.White.copy(alpha = 0.75f), maxLines = 1)
            }
        }
    }
}

@Composable
fun CollectionScreen(state: AppState, shelf: Shelf) {
    val titles = state.visible(Mock.all).filter { t -> t.genres.any { it in shelf.genres } }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(80.dp).background(shelf.color)) {
            Column(Modifier.align(Alignment.BottomStart).padding(start = 36.dp, bottom = 12.dp)) {
                T(shelf.kind.uppercase() + " · " + shelf.genres.joinToString(), 8.sp, Color.White.copy(alpha = 0.75f), FontWeight.SemiBold)
                T(shelf.name, 24.sp, Color.White, FontWeight.Bold)
            }
            Box(Modifier.align(Alignment.TopEnd).padding(14.dp)) { T("Esc to go back", 9.sp, Color.White.copy(alpha = 0.7f)) }
        }
        Box(Modifier.weight(1f)) { GridContent(state, "", titles, empty = "No titles for this profile in ${shelf.name}.") }
    }
}

@Composable
private fun GridContent(state: AppState, title: String, titles: List<Title>, empty: String = "Nothing here for this profile.") {
    val first = rememberInitialFocus(title)
    Column(Modifier.fillMaxSize().padding(top = 28.dp)) {
        if (title.isNotEmpty()) T(title, 22.sp, weight = FontWeight.Bold, modifier = Modifier.padding(start = 36.dp, bottom = 10.dp))
        if (titles.isEmpty()) T(empty, 12.sp, C.Muted, modifier = Modifier.padding(start = 36.dp))
        LazyVerticalGrid(
            GridCells.Adaptive(100.dp), contentPadding = PaddingValues(horizontal = 36.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(titles, key = { it.id }) { t -> PosterCard(state, t, if (t == titles.first()) first else null) }
        }
    }
}

@Composable
private fun SearchContent(state: AppState) {
    var query by rememberSaveable { mutableStateOf("") }
    val field = rememberInitialFocus()
    val results = if (query.isBlank()) emptyList() else state.visible(Mock.all).filter { it.name.contains(query.trim(), ignoreCase = true) }
    Column(Modifier.fillMaxSize().padding(top = 28.dp)) {
        Box(
            Modifier.padding(horizontal = 36.dp).fillMaxWidth().height(40.dp).clip(RoundedCornerShape(10.dp)).background(C.Panel).border(1.dp, C.LineStrong, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                query, { query = it }, Modifier.fillMaxWidth().focusRequester(field), singleLine = true,
                textStyle = TextStyle(color = C.Text, fontSize = 15.sp), cursorBrush = SolidColor(C.Accent),
                decorationBox = { inner -> if (query.isEmpty()) T("Search movies and series (try \"movie 1\" or \"series\")", 15.sp, C.Faint); inner() },
            )
        }
        T(
            if (query.isBlank()) "Searches every catalog addon of this profile that supports search. ↓ to reach results." else "${results.size} results across ${state.current.addons.count { it.catalogs.isNotEmpty() }} addons",
            10.sp, C.Muted, modifier = Modifier.padding(start = 36.dp, top = 8.dp),
        )
        LazyVerticalGrid(
            GridCells.Adaptive(100.dp), contentPadding = PaddingValues(horizontal = 36.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
        ) { items(results, key = { it.id }) { PosterCard(state, it) } }
    }
}
