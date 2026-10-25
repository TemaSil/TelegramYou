package com.telegramyou.app.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.max

/**
 * A change of theme, revealed rather than cut to: what the screen looked
 * like is held as a picture over the app while the new colours spread out
 * of a circle from where it was asked for — Android's own circular reveal.
 * Every colour on screen switching in one frame, the status bar's icons
 * with them, read as a jolt.
 */
@Stable
class ThemeReveal internal constructor(
    private val scope: CoroutineScope,
    private val layer: GraphicsLayer,
    private val still: State<Boolean>
) {
    /** The screen as it was, while the new theme spreads under it. */
    internal var cover by mutableStateOf<ImageBitmap?>(null)
        private set
    internal var origin by mutableStateOf(Offset.Zero)
        private set
    internal val radius = Animatable(0f)
    private var running: Job? = null

    /**
     * [change] the theme, the new one spreading out from [from], a point
     * in the window. With Less motion, or one already spreading, at once.
     */
    fun switch(from: Offset, change: () -> Unit) {
        if (still.value || running?.isActive == true) {
            change()
            return
        }
        running = scope.launch {
            val picture = runCatching { layer.toImageBitmap() }.getOrNull()
            if (picture == null) {
                change()
                return@launch
            }
            origin = from
            radius.snapTo(0f)
            cover = picture
            change()
            val size = layer.size
            val far = listOf(
                Offset(0f, 0f),
                Offset(size.width.toFloat(), 0f),
                Offset(0f, size.height.toFloat()),
                Offset(size.width.toFloat(), size.height.toFloat())
            ).maxOf { corner -> hypot(corner.x - from.x, corner.y - from.y) }
            try {
                radius.animateTo(max(far, 1f), tween(REVEAL_MILLIS, easing = Emphasized))
            } finally {
                cover = null
            }
        }
    }
}

/** The reveal for whatever asks to change the theme; null outside the app. */
val LocalThemeReveal = staticCompositionLocalOf<ThemeReveal?> { null }

/**
 * Holds the whole window's picture for [ThemeReveal], drawing it once more
 * through a layer so there is always a last frame to cover with.
 */
@Composable
fun ThemeRevealHost(reduceMotion: Boolean, content: @Composable () -> Unit) {
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val still = rememberUpdatedState(reduceMotion)
    val reveal = remember(layer, scope) { ThemeReveal(scope, layer, still) }
    Box(
        Modifier
            .fillMaxSize()
            .drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
                val picture = reveal.cover ?: return@drawWithContent
                val hole = Path().apply { addOval(Rect(reveal.origin, reveal.radius.value)) }
                clipPath(hole, ClipOp.Difference) { drawImage(picture) }
            }
    ) {
        CompositionLocalProvider(LocalThemeReveal provides reveal, content = content)
    }
}

private const val REVEAL_MILLIS = 600
/** Material's emphasized decelerate: quick out of the button, settling at the edges. */
private val Emphasized = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
