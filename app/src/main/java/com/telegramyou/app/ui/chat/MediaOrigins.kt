package com.telegramyou.app.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize

/**
 * Where each photo and video of the open chat is on the screen, by message
 * id — what MediaGallery opens out of and closes back into (1.8).
 *
 * A plain map rather than state: it is read once, at the moment the gallery
 * opens or closes, and a bubble moving under a scrolling list should not
 * recompose anything. On the screen rather than in the window, because the
 * gallery is a dialog — a window of its own — and screen coordinates are
 * the ones the two share.
 */
data class MediaOrigin(val bounds: Rect, val cornerPx: Float)

class MediaOrigins {
    private val bounds = HashMap<Long, MediaOrigin>()

    operator fun get(messageId: Long): MediaOrigin? = bounds[messageId]

    internal fun put(messageId: Long, rect: Rect, cornerPx: Float) {
        bounds[messageId] = MediaOrigin(rect, cornerPx)
    }

    internal fun remove(messageId: Long) {
        bounds.remove(messageId)
    }
}

/** The current conversation or shared-media page's [MediaOrigins]. */
val LocalMediaOrigins = staticCompositionLocalOf<MediaOrigins?> { null }

/**
 * Reports this element as the place [messageId]'s photo or video sits, for
 * as long as it is composed: a bubble scrolled out of the list takes its
 * place with it, and the gallery then closes into a fade instead.
 */
@Composable
internal fun Modifier.mediaOrigin(messageId: Long?, corner: Dp = 16.dp): Modifier {
    val origins = LocalMediaOrigins.current
    if (origins == null || messageId == null) return this
    val cornerPx = with(LocalDensity.current) { corner.toPx() }
    DisposableEffect(origins, messageId) {
        onDispose { origins.remove(messageId) }
    }
    return onGloballyPositioned { coordinates ->
        val visible = coordinates.boundsInWindow()
        // Lazy containers also compose prefetched and partly clipped items.
        // A transition must not fly behind a toolbar or into an offscreen cell.
        if (visible.width + 0.5f < coordinates.size.width || visible.height + 0.5f < coordinates.size.height) {
            origins.remove(messageId)
        } else {
            origins.put(messageId, Rect(coordinates.positionOnScreen(), coordinates.size.toSize()), cornerPx)
        }
    }
}

/**
 * MediaGallery's way in and out, for one page: the geometry of
 * OpenFromBubble put into Compose's terms. [origin] is where the page's
 * bubble is on the screen, or null — Less motion, or a bubble scrolled out
 * of the list — and then the page fades and settles from slightly smaller
 * instead. [viewport] is the gallery's own window on the screen.
 */
internal class MediaTransition(origin: Rect?, private val aspect: Float, viewport: Rect?) {

    /** The bubble in the gallery window's coordinates. */
    private val local: MediaFrame? = if (origin == null || viewport == null) null else MediaFrame(
        origin.left - viewport.left,
        origin.top - viewport.top,
        origin.width,
        origin.height
    )

    fun scale(progress: Float, size: Size): Float {
        val bubble = local ?: return blend(FADE_FROM_SCALE, 1f, progress)
        return blend(coverScale(aspect, bubble, size.width, size.height), 1f, progress)
    }

    /** How far the page's centre is from the window's, as a layer translation. */
    fun shift(progress: Float, size: Size): Offset {
        val bubble = local ?: return Offset.Zero
        return Offset(
            blend(bubble.centerX, size.width / 2f, progress) - size.width / 2f,
            blend(bubble.centerY, size.height / 2f, progress) - size.height / 2f
        )
    }

    fun clipShape(progress: Float, size: Size, cornerPx: Float): Shape {
        val bubble = local ?: return RectangleShape
        val clip = clipAt(bubble, size.width, size.height, progress)
        val corner = blend(cornerPx, 0f, progress).coerceAtLeast(0f)
        return object : Shape {
            override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
                Outline.Rounded(
                    RoundRect(
                        left = clip.left,
                        top = clip.top,
                        right = clip.left + clip.width,
                        bottom = clip.top + clip.height,
                        cornerRadius = CornerRadius(corner)
                    )
                )
        }
    }

    /** Out of a bubble the picture is there from the first frame; otherwise it fades. */
    fun contentAlpha(progress: Float): Float =
        if (local != null) 1f else progress.coerceIn(0f, 1f)

    private companion object {
        const val FADE_FROM_SCALE = 0.92f
    }
}
