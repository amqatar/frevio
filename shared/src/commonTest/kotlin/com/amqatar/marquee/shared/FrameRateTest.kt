package com.amqatar.marquee.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FrameRateTest {
    private val m60 = DisplayMode(1, 3840, 2160, 60f)
    private val modes = listOf(
        m60,
        DisplayMode(2, 3840, 2160, 50f),
        DisplayMode(3, 3840, 2160, 23.976f),
        DisplayMode(4, 3840, 2160, 24f),
        DisplayMode(5, 3840, 2160, 120f),
        DisplayMode(6, 1920, 1080, 25f),
    )

    @Test fun cadence() {
        assertTrue(isCadenceMatch(120f, 24f))
        assertTrue(isCadenceMatch(59.94f, 29.97f))
        assertFalse(isCadenceMatch(24f, 23.976f))
        assertFalse(isCadenceMatch(60f, 25f))
    }

    @Test fun film23976NeedsItsOwnRate() = assertEquals(3, pickDisplayMode(23.976f, m60, modes)?.id)

    @Test fun film24PicksLowestMultiple() = assertEquals(4, pickDisplayMode(24f, m60, modes)?.id)

    @Test fun pal25StaysAtCurrentResolution() = assertEquals(2, pickDisplayMode(25f, m60, modes)?.id)

    @Test fun noSwitchWhenAlreadyMatching() = assertNull(pickDisplayMode(30f, m60, modes))
}
