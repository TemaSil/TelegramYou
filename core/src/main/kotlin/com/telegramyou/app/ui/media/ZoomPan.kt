package com.telegramyou.app.ui.media

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Where a zoomed photo is, and how big.
 *
 * [offsetX] and [offsetY] are in pixels, measured from the centred position.
 * At [scale] 1 they are both zero and stay there: a photo that exactly fills
 * its viewport has nowhere to go, and letting it drift would put an edge on
 * screen with nothing behind it.
 */
data class Zoom(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f
) {
    val isZoomed: Boolean get() = scale > 1f
}

/** What a double tap zooms to, and what a pinch may not exceed. */
const val DOUBLE_TAP_SCALE = 2.5f
const val MAX_SCALE = 5f
const val MIN_SCALE = 1f

/**
 * Applies a pinch and a drag, and keeps the result inside its frame.
 *
 * In `:core` with tests because none of this shows up in a screenshot. An
 * image that can be dragged off screen, or that keeps a stale offset after
 * being zoomed back out, looks exactly like one that cannot until somebody
 * does it — and the arithmetic that prevents both is two lines that are easy
 * to write backwards.
 *
 * The bound is half the overhang: at scale s the content is s times the
 * viewport, so (s - 1) of a viewport sticks out, half on each side. Moving
 * further than that would show a gap.
 */
fun zoomAfterGesture(
    current: Zoom,
    scaleChange: Float,
    panX: Float,
    panY: Float,
    viewportWidth: Float,
    viewportHeight: Float
): Zoom {
    val scale = (current.scale * scaleChange).coerceIn(MIN_SCALE, MAX_SCALE)
    // Snapping back to fully zoomed out drops the offset with it, rather than
    // leaving the photo centred-but-not-quite the next time it is opened.
    if (scale <= MIN_SCALE) return Zoom()
    return Zoom(
        scale = scale,
        offsetX = clampOffset(current.offsetX + panX, scale, viewportWidth),
        offsetY = clampOffset(current.offsetY + panY, scale, viewportHeight)
    )
}

/** Double tap: all the way out if zoomed at all, otherwise part of the way in. */
fun zoomToggled(current: Zoom): Zoom =
    if (current.isZoomed) Zoom() else Zoom(scale = DOUBLE_TAP_SCALE)

/**
 * How far a vertical drag has taken the photo towards being dismissed, 0 to 1.
 *
 * Drives the scrim and the photo's own scale together, so letting go halfway
 * is visibly halfway rather than a state the gesture cannot show. Absolute,
 * because dragging up and dragging down both mean the same thing here.
 */
fun dismissProgress(dragY: Float, viewportHeight: Float): Float {
    if (viewportHeight <= 0f) return 0f
    return min(1f, abs(dragY) / (viewportHeight * DISMISS_FRACTION))
}

/** Whether letting go here should close the photo. */
fun shouldDismiss(dragY: Float, viewportHeight: Float): Boolean =
    dismissProgress(dragY, viewportHeight) >= 1f

/** What letting go of a vertical drag on a playing video does (2.8.1). */
enum class VideoDrag { Dismiss, OpenCaption, CloseCaption, Stay }

/**
 * A video is thrown away by a drag, as a photo is (2.8.1) — and a caption
 * cut short is pulled up the same way it is tapped open. An open caption is
 * pushed back down before the video can go: it is being read.
 */
fun videoDragOutcome(dragY: Float, viewportHeight: Float, captionOpen: Boolean, captionLong: Boolean): VideoDrag {
    if (viewportHeight <= 0f) return VideoDrag.Stay
    val pulled = abs(dragY) >= viewportHeight * CAPTION_PULL_FRACTION
    return when {
        captionOpen -> if (dragY > 0f && pulled) VideoDrag.CloseCaption else VideoDrag.Stay
        dragY < 0f && captionLong -> if (pulled) VideoDrag.OpenCaption else VideoDrag.Stay
        shouldDismiss(dragY, viewportHeight) -> VideoDrag.Dismiss
        else -> VideoDrag.Stay
    }
}

/** Whether a drag this way moves the video itself, rather than its caption. */
fun videoDragMoves(dragY: Float, captionOpen: Boolean, captionLong: Boolean): Boolean =
    !captionOpen && !(dragY < 0f && captionLong)

/** A short pull: the caption follows a flick, not a deliberate drag. */
private const val CAPTION_PULL_FRACTION = 0.06f

/**
 * A fifth of the screen.
 *
 * Short enough to be an easy flick and long enough that scrolling a caption
 * or missing a pinch does not throw the photo away.
 */
private const val DISMISS_FRACTION = 0.2f

private fun clampOffset(offset: Float, scale: Float, viewport: Float): Float {
    val overhang = max(0f, viewport * (scale - 1f) / 2f)
    return offset.coerceIn(-overhang, overhang)
}
