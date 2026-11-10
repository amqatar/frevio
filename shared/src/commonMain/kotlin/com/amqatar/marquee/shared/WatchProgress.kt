package com.amqatar.marquee.shared

import kotlinx.serialization.Serializable

/** Watched from this fraction on. */
const val WATCHED_RATIO = 0.92

/** Below this, a start doesn't count as "in progress". */
const val MIN_RESUME_MS = 30_000L

/** One video's position for one profile (a movie, or one episode of a series). */
@Serializable
data class ProgressEntry(
    val titleId: String,
    val videoId: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)

fun ProgressEntry.ratio(): Double = if (durationMs > 0) (positionMs.toDouble() / durationMs).coerceIn(0.0, 1.0) else 0.0

fun ProgressEntry.isWatched(): Boolean = ratio() >= WATCHED_RATIO

/** Where to resume, or 0 to start over (not started, barely started, or finished). */
fun ProgressEntry?.resumeAtMs(): Long =
    if (this == null || isWatched() || positionMs < MIN_RESUME_MS) 0 else positionMs

data class ContinueItem(
    val titleId: String,
    val videoId: String,
    /** False when the entry is the next episode after one just finished. */
    val inProgress: Boolean,
    val resumeAtMs: Long,
    val ratio: Double,
    val updatedAt: Long,
)

/**
 * The Continue watching row: one item per title, newest activity first.
 * - In progress (≥30 s, <92%): resume there.
 * - Latest video finished and [nextVideoId] knows a next one that isn't watched: offer it from the start.
 */
fun continueWatching(entries: Collection<ProgressEntry>, nextVideoId: (titleId: String, videoId: String) -> String? = { _, _ -> null }): List<ContinueItem> {
    val byVideo = entries.associateBy { it.videoId }
    return entries.groupBy { it.titleId }.mapNotNull { (titleId, list) ->
        val latest = list.maxBy { it.updatedAt }
        when {
            !latest.isWatched() && latest.positionMs >= MIN_RESUME_MS ->
                ContinueItem(titleId, latest.videoId, true, latest.positionMs, latest.ratio(), latest.updatedAt)
            latest.isWatched() -> nextVideoId(titleId, latest.videoId)
                ?.takeIf { byVideo[it]?.isWatched() != true }
                ?.let { ContinueItem(titleId, it, false, 0, 0.0, latest.updatedAt) }
            else -> null
        }
    }.sortedByDescending { it.updatedAt }
}
