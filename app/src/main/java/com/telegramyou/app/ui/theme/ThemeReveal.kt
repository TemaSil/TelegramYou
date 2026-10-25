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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
    private val still: State<Boolean>,
    private val dark: State<Boolean>
) {
    /** The screen as it was, while the new theme spreads under it. */
    internal var cover by mutableStateOf<ImageBitmap?>(null)
        private set
    internal var origin by mutableStateOf(Offset.Zero)
        private set
    internal val radius = Animatable(0f)
    /** Where the host sits on the screen; origins come in screen terms. */
    internal var hostOnScreen = Offset.Zero
    private var running: Job? = null

    /**
     * [change] the theme, the new one spreading out from [fromOnScreen] —
     * a point on the screen, so a menu's item, in a window of its own,
     * gives it as well as a button does. [settle] is time for something
     * on its way out first, a menu closing, so that it is not caught in the
     * picture or seen taking the new colours. With Less motion, or one
     * already spreading, at once.
     */
    fun switch(fromOnScreen: Offset, settle: Long = 0, change: () -> Unit) {
        if (still.value || running?.isActive == true) {
            change()
            return
        }
        running = scope.launch {
            if (settle > 0) delay(settle)
            val picture = runCatching { layer.toImageBitmap() }.getOrNull()
            if (picture == null) {
                change()
                return@launch
            }
            val from = fromOnScreen - hostOnScreen
            val wasDark = dark.value
            origin = from
            radius.snapTo(0f)
            cover = picture
            try {
                change()
                // The circle opens only once the new theme is drawn under
                // the picture: the setting takes a frame or two to come
                // back round, and a circle started at once had grown out of
                // sight before there was anything new inside it.
                val turned = withTimeoutOrNull(TURN_WAIT_MILLIS) {
                    snapshotFlow { dark.value }.first { it != wasDark }
                }
                if (turned == null) return@launch // System, already as it was
                withFrameNanos {}
                withFrameNanos {}
                val size = layer.size
                val far = listOf(
                    Offset(0f, 0f),
                    Offset(size.width.toFloat(), 0f),
                    Offset(0f, size.height.toFloat()),
                    Offset(size.width.toFloat(), size.height.toFloat())
                ).maxOf { corner -> hypot(corner.x - from.x, corner.y - from.y) }
                radius.animateTo(max(far, 1f), tween(REVEAL_MILLIS, easing = Standard))
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
fun ThemeRevealHost(reduceMotion: Boolean, darkTheme: Boolean, content: @Composable () -> Unit) {
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val still = rememberUpdatedState(reduceMotion)
    val dark = rememberUpdatedState(darkTheme)
    val reveal = remember(layer, scope) { ThemeReveal(scope, layer, still, dark) }
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { reveal.hostOnScreen = it.positionOnScreen() }
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

private const val REVEAL_MILLIS = 650
/**
 * Material's standard curve: the circle is seen leaving the button. The
 * emphasized decelerate it had first covered most of the screen in a few
 * frames, so the reveal read as coming from the middle.
 */
private val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
/** How long to wait for the new theme before deciding there is none. */
private const val TURN_WAIT_MILLIS = 600L

/** Where this item's centre is on the screen, for [ThemeReveal.switch]. */
fun androidx.compose.ui.layout.LayoutCoordinates.centreOnScreen(): Offset =
    positionOnScreen() + Offset(size.width / 2f, size.height / 2f)
