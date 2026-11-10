package com.amqatar.marquee.prototype

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.delay

/** TV layouts are designed for a 960 x 540 dp screen; the window scales that to any size. */
const val TV_W = 960f
const val TV_H = 540f

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Marquee prototype · arrows move · Enter selects · Esc goes back",
        state = rememberWindowState(size = DpSize(1280.dp, 760.dp)),
    ) {
        App(remember { AppState() })
    }
}

@Composable
fun App(state: AppState) {
    BoxWithConstraints(Modifier.fillMaxSize().background(C.Bg), contentAlignment = Alignment.Center) {
        val outer = LocalDensity.current
        val scale = minOf(constraints.maxWidth / TV_W, constraints.maxHeight / TV_H)
        CompositionLocalProvider(LocalDensity provides Density(scale, outer.fontScale)) {
            val focus = LocalFocusManager.current
            Box(
                Modifier.requiredSize(TV_W.dp, TV_H.dp).background(C.Bg).onKeyEvent { e ->
                    // A TV remote's D-pad, on a keyboard. Keys reach here only if the focused
                    // element didn't use them (text fields keep Left/Right for the cursor).
                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (e.key) {
                        Key.DirectionLeft -> focus.moveFocus(FocusDirection.Left)
                        Key.DirectionRight -> focus.moveFocus(FocusDirection.Right)
                        Key.DirectionUp -> focus.moveFocus(FocusDirection.Up)
                        Key.DirectionDown -> focus.moveFocus(FocusDirection.Down)
                        Key.Escape, Key.Backspace -> { state.back(); true }
                        else -> false
                    }
                },
            ) {
                Router(state)
                Toast(state)
            }
        }
    }
}

@Composable
fun Router(state: AppState) {
    when (val s = state.screen) {
        Screen.Profiles -> ProfilesScreen(state)
        is Screen.Pin -> PinScreen(state, s.profileId)
        is Screen.Main -> MainScreen(state, s.tab)
        is Screen.Details -> DetailsScreen(state, s.titleId)
        is Screen.Streams -> StreamsScreen(state, s.titleId, s.videoId)
        is Screen.Player -> PlayerScreen(state, s.titleId, s.videoId, s.stream)
        is Screen.Settings -> SettingsScreen(state, s.section)
        Screen.SignIn -> SignInScreen(state)
    }
}

@Composable
private fun Toast(state: AppState) {
    val msg = state.toast ?: return
    LaunchedEffect(msg) { delay(2600); state.toast = null }
    Box(Modifier.fillMaxSize().padding(bottom = 24.dp), contentAlignment = Alignment.BottomCenter) {
        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(C.Raised).padding(horizontal = 16.dp, vertical = 10.dp)) {
            T(msg, 11.sp)
        }
    }
}
