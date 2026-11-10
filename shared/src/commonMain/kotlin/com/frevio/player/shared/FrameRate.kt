package com.frevio.player.shared

import kotlin.math.abs
import kotlin.math.roundToInt

data class DisplayMode(val id: Int, val width: Int, val height: Int, val refreshRate: Float)

/** True when [refresh] shows every content frame for the same whole number of refreshes (no judder). */
fun isCadenceMatch(refresh: Float, contentFps: Float, tolerance: Float = 0.0005f): Boolean {
    if (contentFps <= 0f || refresh <= 0f) return false
    val k = (refresh / contentFps).roundToInt()
    if (k < 1) return false
    val target = k * contentFps
    return abs(refresh - target) / target < tolerance
}

/**
 * Display mode to switch to for judder-free playback, or null when no switch is needed or
 * possible. Keeps the current resolution, so the switch is a refresh-rate change only.
 * 23.976 fps content does not match 24 Hz (0.1% off, one judder every 42 s), so it needs 23.976 Hz.
 */
fun pickDisplayMode(contentFps: Float, current: DisplayMode, modes: List<DisplayMode>): DisplayMode? {
    if (contentFps <= 0f) return null
    if (isCadenceMatch(current.refreshRate, contentFps)) return null
    return modes
        .filter { it.width == current.width && it.height == current.height }
        .filter { isCadenceMatch(it.refreshRate, contentFps) }
        .minByOrNull { (it.refreshRate / contentFps).roundToInt() }
}
