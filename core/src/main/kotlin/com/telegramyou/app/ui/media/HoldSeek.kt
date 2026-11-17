package com.telegramyou.app.ui.media

/** The middle stays available for ordinary taps and gallery gestures. */
fun holdSeekDirection(x: Float, width: Float): Int = when {
    !x.isFinite() || !width.isFinite() || width <= 0 -> 0
    x < width * 0.35f -> -1
    x > width * 0.65f -> 1
    else -> 0
}

/** Drag farther towards the held edge to increase temporal seeking speed. */
fun holdSeekSpeed(startX: Float, x: Float, width: Float, direction: Int): Int {
    if (!startX.isFinite() || !x.isFinite() || !width.isFinite() || width <= 0) return 2
    val outward = ((x - startX) * direction / width).coerceAtLeast(0f)
    return when { outward >= 0.15f -> 16; outward >= 0.08f -> 8; outward >= 0.025f -> 4; else -> 2 }
}

fun heldSeekTarget(positionMs: Long, durationMs: Long, elapsedMs: Long, direction: Int, speed: Int): Long {
    if (durationMs <= 0) return positionMs.coerceAtLeast(0)
    val delta = elapsedMs.coerceIn(0, 1_000).toDouble() * speed.coerceIn(2, 16) * direction.coerceIn(-1, 1)
    return (positionMs.toDouble() + delta).coerceIn(0.0, durationMs.toDouble()).toLong()
}
