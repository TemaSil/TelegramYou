package com.telegramyou.app.ui.chat

/**
 * A photo edited before it is sent (2.2): turned by quarters, and cropped.
 * Kept here, on the JVM, because each step has an edge — a corner dragged
 * past the opposite one, a frame pushed off the picture, a crop that has to
 * turn with the picture under it — that is easy to get wrong in a gesture.
 *
 * Everything is in fractions of the picture as it stands after turning, so
 * the frame means the same thing at any size it is drawn.
 */
data class PhotoEdit(
    /** Quarter turns to the left, 0 to 3. */
    val quarterTurns: Int = 0,
    val crop: CropRect = CropRect()
) {
    /** Nothing done to it: the photo can go as it was picked. */
    val isNone: Boolean get() = quarterTurns == 0 && crop.isWhole

    /** A quarter turn to the left, the frame turning with the picture. */
    fun turnedLeft(): PhotoEdit = PhotoEdit((quarterTurns + 1) % 4, crop.turnedLeft())
}

/** The part kept, as fractions of the picture's width and height. */
data class CropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    /** The whole picture: nothing cropped. */
    val isWhole: Boolean get() = left <= 0f && top <= 0f && right >= 1f && bottom >= 1f

    /** The same part of a picture turned a quarter to the left. */
    fun turnedLeft(): CropRect = CropRect(top, 1f - right, bottom, 1f - left)

    /**
     * Moved by [dx], [dy] at [handle]: a corner pulls its two sides, never
     * past [MIN_CROP] of the opposite ones; the inside moves the whole frame,
     * kept on the picture.
     */
    fun dragged(handle: CropHandle, dx: Float, dy: Float): CropRect = when (handle) {
        CropHandle.Inside -> {
            val x = dx.coerceIn(-left, 1f - right)
            val y = dy.coerceIn(-top, 1f - bottom)
            CropRect(left + x, top + y, right + x, bottom + y)
        }
        CropHandle.TopLeft -> copy(
            left = (left + dx).coerceIn(0f, right - MIN_CROP),
            top = (top + dy).coerceIn(0f, bottom - MIN_CROP)
        )
        CropHandle.TopRight -> copy(
            right = (right + dx).coerceIn(left + MIN_CROP, 1f),
            top = (top + dy).coerceIn(0f, bottom - MIN_CROP)
        )
        CropHandle.BottomLeft -> copy(
            left = (left + dx).coerceIn(0f, right - MIN_CROP),
            bottom = (bottom + dy).coerceIn(top + MIN_CROP, 1f)
        )
        CropHandle.BottomRight -> copy(
            right = (right + dx).coerceIn(left + MIN_CROP, 1f),
            bottom = (bottom + dy).coerceIn(top + MIN_CROP, 1f)
        )
    }

    /**
     * The handle a finger at [x], [y] takes: a corner within [reachX],
     * [reachY] of it, the nearest if two are; otherwise the inside, if it
     * is inside; otherwise nothing.
     */
    fun handleAt(x: Float, y: Float, reachX: Float, reachY: Float): CropHandle? {
        val corners = listOf(
            CropHandle.TopLeft to (left to top),
            CropHandle.TopRight to (right to top),
            CropHandle.BottomLeft to (left to bottom),
            CropHandle.BottomRight to (right to bottom)
        )
        val near = corners
            .filter { (_, at) -> kotlin.math.abs(x - at.first) <= reachX && kotlin.math.abs(y - at.second) <= reachY }
            .minByOrNull { (_, at) ->
                val ex = (x - at.first) / reachX
                val ey = (y - at.second) / reachY
                ex * ex + ey * ey
            }
        if (near != null) return near.first
        return if (x in left..right && y in top..bottom) CropHandle.Inside else null
    }

    /** In pixels of a [width] × [height] picture: left, top, width, height — never empty. */
    fun inPixels(width: Int, height: Int): IntArray {
        val x = (left * width).toInt().coerceIn(0, width - 1)
        val y = (top * height).toInt().coerceIn(0, height - 1)
        val w = ((right - left) * width).toInt().coerceIn(1, width - x)
        val h = ((bottom - top) * height).toInt().coerceIn(1, height - y)
        return intArrayOf(x, y, w, h)
    }

    companion object {
        /** The least a frame can be, of each side: small enough to crop to a face, not to nothing. */
        const val MIN_CROP = 0.1f
    }
}

enum class CropHandle { TopLeft, TopRight, BottomLeft, BottomRight, Inside }
