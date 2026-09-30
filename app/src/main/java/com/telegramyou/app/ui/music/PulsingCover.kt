package com.telegramyou.app.ui.music

import androidx.compose.animation.core.animate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.asComposePath
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import com.telegramyou.app.ui.motion.LocalReduceMotion

/**
 * The player's cover, a rounded square that breathes on the beat into a
 * cookie — the scalloped square of Material's shape library, the family the
 * avatars morph through — and back. On unless switched off in Appearance.
 *
 * Its first version rippled the edges with a wave that also crept round
 * them, and on a phone that read as twitching. This is a morph between two
 * shapes instead, shallow, rising with a hit and settling slowly after it:
 * the artwork stays whole, and the shape says the music is playing. Still
 * with Less motion on, and settling back while paused rather than stopping
 * mid-breath.
 *
 * The shape is set on the layer rather than through clip(): the beat is read
 * every frame, and reading it in the layer's block redraws the layer without
 * recomposing the screen around it.
 */
@Composable
fun Modifier.pulsingCover(
    corner: Dp,
    playing: Boolean,
    enabled: Boolean,
    beat: () -> Float,
    onWatched: (Boolean) -> Unit
): Modifier {
    val moving = enabled && !LocalReduceMotion.current
    val level = remember { mutableFloatStateOf(0f) }
    val morph = remember { CoverMorph() }
    DisposableEffect(moving) {
        onWatched(moving)
        onDispose { onWatched(false) }
    }
    LaunchedEffect(moving, playing) {
        if (moving && playing) follow(beat, level) else settle(level)
    }
    return graphicsLayer {
        shape = if (level.floatValue <= 0f) RoundedCornerShape(corner) else MorphShape(morph.shape, level.floatValue)
        clip = true
    }
}

/** Frame by frame: quick to rise with a hit, slow to settle after it. */
private suspend fun follow(beat: () -> Float, level: MutableFloatState) {
    while (true) {
        withFrameNanos {
            val target = beat().coerceIn(0f, 1f)
            val rate = if (target > level.floatValue) RISE else SETTLE
            level.floatValue += (target - level.floatValue) * rate
        }
    }
}

private suspend fun settle(level: MutableFloatState) {
    if (level.floatValue == 0f) return
    animate(level.floatValue, 0f) { value, _ -> level.floatValue = value }
}

/**
 * The rounded square and the cookie, as one morph. Built from the same
 * outline at two depths (scallopedSquare in :core): corners rounded to
 * about the cover's 16 dp in both, the scallops rounded only in the cookie.
 */
private class CoverMorph {
    val shape: Morph = Morph(polygon(0f), polygon(COOKIE_DEPTH))

    private fun polygon(depth: Float): RoundedPolygon {
        val vertices = scallopedSquare(SCALLOPS, depth)
        val perEdge = SCALLOPS * 2
        val rounding = List(vertices.size / 2) { v ->
            when {
                v % perEdge == 0 -> CornerRounding(CORNER, smoothing = 1f)
                depth > 0f -> CornerRounding(SCALLOP, smoothing = 1f)
                else -> CornerRounding.Unrounded
            }
        }
        return RoundedPolygon(vertices = vertices, perVertexRounding = rounding).normalized()
    }
}

/** One moment of the morph, scaled from its unit square to the cover. */
private class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress)
        path.transform(android.graphics.Matrix().apply { setScale(size.width, size.height) })
        return Outline.Generic(path.asComposePath())
    }
}

/** Three scallops a side: enough to read as a cookie, few enough to stay soft. */
private const val SCALLOPS = 3
/** How deep the scallops go at the biggest hit, of the square's half-width. */
private const val COOKIE_DEPTH = 0.07f
/** The corners, of the half-width: about 16 dp on a phone's cover. */
private const val CORNER = 0.09f
private const val SCALLOP = 0.16f
private const val RISE = 0.35f
private const val SETTLE = 0.07f
