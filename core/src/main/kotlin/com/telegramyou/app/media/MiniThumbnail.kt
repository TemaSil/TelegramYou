package com.telegramyou.app.media

/**
 * The blur behind Telegram's minithumbnail: the tiny JPEG — some forty
 * pixels across — that TDLib sends inside a photo, video or GIF message, and
 * that every official client shows softened while the real file downloads.
 *
 * Done here, on the pixels, rather than with `Modifier.blur`: that is
 * Android 12 and up only, and blurring a picture of forty pixels by hand
 * costs next to nothing. A box blur run three times over is close enough to
 * a Gaussian that the eye cannot tell, and separable, so each pass is two
 * sweeps of a running sum.
 */
object MiniThumbnail {
    /** Enough to lose the JPEG's blocks on a forty-pixel picture. */
    const val RADIUS = 2
    const val PASSES = 3

    /**
     * [pixels], ARGB and row by row, [width] by [height], blurred: a new
     * array, the input left alone. Edges repeat their last pixel, so the
     * border does not darken.
     */
    fun blur(
        pixels: IntArray,
        width: Int,
        height: Int,
        radius: Int = RADIUS,
        passes: Int = PASSES
    ): IntArray {
        require(pixels.size == width * height) { "pixels do not match the size" }
        if (width == 0 || height == 0 || radius <= 0) return pixels.copyOf()
        var current = pixels.copyOf()
        val scratch = IntArray(pixels.size)
        repeat(passes) {
            sweep(current, scratch, width, height, radius, horizontal = true)
            sweep(scratch, current, width, height, radius, horizontal = false)
        }
        return current
    }

    /** One box blur along rows or columns, from [source] into [target]. */
    private fun sweep(
        source: IntArray,
        target: IntArray,
        width: Int,
        height: Int,
        radius: Int,
        horizontal: Boolean
    ) {
        val lines = if (horizontal) height else width
        val length = if (horizontal) width else height
        val window = radius * 2 + 1
        for (line in 0 until lines) {
            fun at(i: Int): Int {
                val clamped = i.coerceIn(0, length - 1)
                return if (horizontal) source[line * width + clamped] else source[clamped * width + line]
            }
            var a = 0
            var r = 0
            var g = 0
            var b = 0
            for (i in -radius..radius) {
                val p = at(i)
                a += p ushr 24
                r += (p shr 16) and 0xFF
                g += (p shr 8) and 0xFF
                b += p and 0xFF
            }
            for (i in 0 until length) {
                val out = ((a / window) shl 24) or ((r / window) shl 16) or ((g / window) shl 8) or (b / window)
                if (horizontal) target[line * width + i] = out else target[i * width + line] = out
                val leaving = at(i - radius)
                val entering = at(i + radius + 1)
                a += (entering ushr 24) - (leaving ushr 24)
                r += ((entering shr 16) and 0xFF) - ((leaving shr 16) and 0xFF)
                g += ((entering shr 8) and 0xFF) - ((leaving shr 8) and 0xFF)
                b += (entering and 0xFF) - (leaving and 0xFF)
            }
        }
    }
}
