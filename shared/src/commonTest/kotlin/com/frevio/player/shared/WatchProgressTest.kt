package com.frevio.player.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WatchProgressTest {
    private val min = 60_000L
    private fun e(title: String, video: String, pos: Long, dur: Long = 50 * min, at: Long) = ProgressEntry(title, video, pos, dur, at)
    private val next = mapOf("s:1:1" to "s:1:2", "s:1:2" to "s:1:3")

    @Test fun oneItemPerTitleLatestEpisodeWins() {
        val row = continueWatching(listOf(e("s", "s:1:1", 20 * min, at = 1), e("s", "s:1:2", 10 * min, at = 5)))
        assertEquals(listOf("s:1:2"), row.map { it.videoId })
        assertEquals(10 * min, row.single().resumeAtMs)
    }

    @Test fun barelyStartedAndFinishedMoviesAreHidden() {
        val row = continueWatching(listOf(e("m1", "m1", 10_000, at = 1), e("m2", "m2", 49 * min, at = 2)))
        assertTrue(row.isEmpty())
    }

    @Test fun finishedEpisodeOffersTheNextOne() {
        val row = continueWatching(listOf(e("s", "s:1:1", 49 * min, at = 3))) { _, v -> next[v] }
        assertEquals("s:1:2", row.single().videoId)
        assertEquals(false, row.single().inProgress)
        assertEquals(0, row.single().resumeAtMs)
    }

    @Test fun nextAlreadyWatchedIsNotOffered() {
        val row = continueWatching(listOf(e("s", "s:1:2", 49 * min, at = 1), e("s", "s:1:1", 49 * min, at = 3))) { _, v -> next[v] }
        assertTrue(row.isEmpty())
    }

    @Test fun newestFirst() {
        val row = continueWatching(listOf(e("a", "a", 5 * min, at = 1), e("b", "b", 5 * min, at = 9)))
        assertEquals(listOf("b", "a"), row.map { it.titleId })
    }

    @Test fun resumePoint() {
        assertEquals(0, null.resumeAtMs())
        assertEquals(0, e("m", "m", 10_000, at = 1).resumeAtMs())
        assertEquals(5 * min, e("m", "m", 5 * min, at = 1).resumeAtMs())
        assertEquals(0, e("m", "m", 49 * min, at = 1).resumeAtMs())
    }
}
