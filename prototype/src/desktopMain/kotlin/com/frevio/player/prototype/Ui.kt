package com.frevio.player.prototype

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Colours from the design canvas. Sizes are in TV dp: the screen is 960 x 540 dp. */
object C {
    val Bg = Color(0xFF0B0D12)
    val Panel = Color(0xFF12151D)
    val Raised = Color(0xFF1E2330)
    val Line = Color(0xFF2A3040)
    val LineStrong = Color(0xFF3A4256)
    val Text = Color(0xFFEEF0F5)
    val Soft = Color(0xFFC9CFDB)
    val Muted = Color(0xFFA3ABBD)
    val Faint = Color(0xFF8F98AB)
    val Accent = Color(0xFF9B6DFF)
    val Good = Color(0xFF7BD88F)
    val Warn = Color(0xFFF3C873)
    val Glass = Color(0x1FEEF0F5)
}

@Composable
fun T(
    text: String,
    size: TextUnit = 12.sp,
    color: Color = C.Text,
    weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) = BasicText(
    text,
    modifier,
    style = TextStyle(color = color, fontSize = size, fontWeight = weight, lineHeight = size * 1.35f),
    maxLines = maxLines,
    overflow = TextOverflow.Ellipsis,
)

/**
 * Anything the remote can land on. Focus = white ring + slight zoom, the TV convention.
 * Arrow keys move focus (Main.kt), Enter selects, Esc goes back.
 */
@Composable
fun FocusBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    focusedScale: Float = 1.05f,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    background: Color = Color.Transparent,
    focusedBackground: Color = background,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) focusedScale else 1f, tween(120))
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .border(if (focused) 2.dp else 0.dp, if (focused) C.Text else Color.Transparent, shape)
            .clip(shape)
            .background(if (focused) focusedBackground else background)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) { content(focused) }
}

/** Requests focus shortly after first composition (lazy lists need a frame). */
@Composable
fun rememberInitialFocus(key: Any? = Unit): FocusRequester {
    val fr = remember(key) { FocusRequester() }
    LaunchedEffect(key) {
        delay(60)
        runCatching { fr.requestFocus() }
    }
    return fr
}

@Composable
fun PillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    icon: ImageVector? = null,
    focusRequester: FocusRequester? = null,
    enabled: Boolean = true,
) {
    FocusBox(
        onClick, modifier.height(32.dp), RoundedCornerShape(8.dp), 1.04f, enabled, focusRequester,
        background = if (primary) C.Text else C.Glass,
        focusedBackground = if (primary) C.Text else Color(0x3DEEF0F5),
    ) {
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            val fg = if (primary) C.Bg else C.Text
            if (icon != null) {
                Icon(icon, fg, 12.dp)
                if (label.isNotEmpty()) Box(Modifier.size(6.dp))
            }
            if (label.isNotEmpty()) T(label, 11.sp, fg, if (primary) FontWeight.Bold else FontWeight.SemiBold)
        }
    }
}

@Composable
fun Chip(label: String, color: Color = C.Soft, bg: Color = C.Raised) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp)) {
        T(label, 9.sp, color, FontWeight.SemiBold)
    }
}

@Composable
fun Tag(label: String) {
    Box(Modifier.border(1.dp, C.LineStrong, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
        T(label, 8.sp, C.Text, FontWeight.SemiBold)
    }
}

@Composable
fun Progress(ratio: Double, modifier: Modifier = Modifier, height: Dp = 3.dp) {
    Box(modifier.height(height).clip(RoundedCornerShape(50)).background(Color(0x40EEF0F5))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(ratio.toFloat().coerceIn(0f, 1f)).background(C.Accent))
    }
}

@Composable
fun Icon(vector: ImageVector, tint: Color, size: Dp, modifier: Modifier = Modifier) =
    Image(vector, null, modifier.size(size), colorFilter = ColorFilter.tint(tint))

/** Stroke icons drawn from SVG path data, 24 x 24 grid. */
object Icons {
    private fun icon(name: String, vararg paths: String, filled: Boolean = false): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { d ->
                addPath(
                    addPathNodes(d),
                    fill = if (filled) SolidColor(Color.White) else null,
                    stroke = if (filled) null else SolidColor(Color.White),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val Home = icon("home", "M3 11l9-7 9 7v9a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z")
    val Search = icon("search", "M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0-14z", "M20 20l-3.5-3.5")
    val Movie = icon("movie", "M5 4h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z", "M7 4v16M17 4v16M3 9h4M17 9h4M3 15h4M17 15h4")
    val Series = icon("series", "M5 6h14a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z", "M8 2l4 4 4-4")
    val Library = icon("library", "M6 3h12v18l-6-4-6 4z")
    val Settings = icon("settings", "M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6z", "M12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9l2.1 2.1M17 17l2.1 2.1M4.9 19.1L7 17M17 7l2.1-2.1")
    val Play = icon("play", "M7 4l13 8-13 8z", filled = true)
    val Pause = icon("pause", "M6 4h4v16H6zM14 4h4v16h-4z", filled = true)
    val Plus = icon("plus", "M12 5v14M5 12h14")
    val Check = icon("check", "M5 12l5 5 9-10")
    val Lock = icon("lock", "M7 11h10a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2z", "M8 11V8a4 4 0 0 1 8 0v3")
    val Back = icon("back", "M15 5l-7 7 7 7")
    val Rewind = icon("rewind", "M4 12a8 8 0 1 0 3-6.2", "M4 4v4h4")
    val Forward = icon("forward", "M20 12a8 8 0 1 1-3-6.2", "M20 4v4h-4")
    val Chevron = icon("chevron", "M9 5l7 7-7 7")
    val Up = icon("up", "M5 15l7-7 7 7")
    val Down = icon("down", "M5 9l7 7 7-7")
    val Trash = icon("trash", "M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13")
}
