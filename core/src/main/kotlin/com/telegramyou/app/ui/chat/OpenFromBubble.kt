package com.telegramyou.app.ui.chat

/**
 * The geometry of a photo or video opening out of its bubble and closing
 * back into it (1.8): plain numbers, so it can be checked without a screen.
 *
 * Full screen, the picture is *fitted* — all of it, letterboxed. In the
 * bubble it is *cropped* to the bubble's shape. The full-screen page is
 * therefore scaled, as a whole, so that its fitted picture just covers the
 * bubble, moved so the centres meet, and clipped to the bubble: at that
 * moment it is pixel for pixel what the bubble shows. Progress 0 is there,
 * 1 is full screen, and everything between is a straight blend — the
 * spring that drives progress is what gives it Expressive's feel.
 */
data class MediaFrame(val left: Float, val top: Float, val width: Float, val height: Float) {
    val centerX: Float get() = left + width / 2f
    val centerY: Float get() = top + height / 2f
}

/** A picture of [aspect] (width over height) fitted whole, centred, in [width] × [height]. */
fun fitted(aspect: Float, width: Float, height: Float): MediaFrame {
    val safe = if (aspect > 0f) aspect else 1f
    return if (safe > width / height) {
        val h = width / safe
        MediaFrame(0f, (height - h) / 2f, width, h)
    } else {
        val w = height * safe
        MediaFrame((width - w) / 2f, 0f, w, height)
    }
}

/**
 * How much smaller the full-screen page must be for its fitted picture to
 * cover [origin] exactly as a centre crop would.
 */
fun coverScale(aspect: Float, origin: MediaFrame, width: Float, height: Float): Float {
    val fit = fitted(aspect, width, height)
    return maxOf(origin.width / fit.width, origin.height / fit.height)
}

/** The straight blend from [from] at 0 to [to] at 1; past either end it carries on. */
fun blend(from: Float, to: Float, progress: Float): Float = from + (to - from) * progress

/** The clip between [origin] at 0 and the whole [width] × [height] at 1, never inside out. */
fun clipAt(origin: MediaFrame, width: Float, height: Float, progress: Float): MediaFrame = MediaFrame(
    left = blend(origin.left, 0f, progress),
    top = blend(origin.top, 0f, progress),
    width = blend(origin.width, width, progress).coerceAtLeast(0f),
    height = blend(origin.height, height, progress).coerceAtLeast(0f)
)
