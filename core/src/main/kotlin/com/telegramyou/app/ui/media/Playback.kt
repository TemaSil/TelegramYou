package com.telegramyou.app.ui.media

/**
 * The arithmetic behind a video's progress bar.
 *
 * Small, and here rather than in the player because it is the part that is
 * easy to get quietly wrong: a duration the player does not know yet is -1,
 * not 0, and dividing by it gives a slider that sits slightly to the left of
 * where it should and never reaches the end.
 */

/** What a player reports for a duration it has not worked out yet. */
const val DURATION_UNKNOWN = -1L

/**
 * How far through, as 0..1, or null when there is nothing to show.
 *
 * Null rather than zero for an unknown duration: zero is a real position —
 * the start — and a bar sitting at the start says the video is ready when in
 * fact nothing is known about it yet. The caller draws an indeterminate bar
 * for null, which is the honest shape.
 */
fun playbackProgress(positionMs: Long, durationMs: Long): Float? {
    if (durationMs <= 0L) return null
    return (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}

/**
 * Where a drag on the bar lands, in milliseconds.
 *
 * Clamped at both ends because a slider hands back exactly 0f and exactly 1f,
 * and seeking to the very last millisecond of a file ends playback instead of
 * showing its last frame.
 */
fun seekTarget(fraction: Float, durationMs: Long): Long {
    if (durationMs <= 0L) return 0L
    val clamped = fraction.coerceIn(0f, 1f)
    return (clamped * durationMs).toLong().coerceIn(0L, durationMs)
}

/**
 * The label under the bar: how far through, out of how long.
 *
 * An unknown duration shows the position alone rather than "0:07 / 0:00",
 * which reads as a broken file rather than as one still being measured.
 */
fun playbackLabel(
    positionMs: Long,
    durationMs: Long,
    format: (Long) -> String
): String {
    val position = format(positionMs / 1000)
    if (durationMs <= 0L) return position
    return "$position / ${format(durationMs / 1000)}"
}
