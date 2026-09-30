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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.telegramyou.app.ui.motion.LocalReduceMotion
import kotlin.math.PI

/**
 * The player's cover, clipped to a rounded square whose edges ripple a
 * little on the beat — the owner's idea for 1.6.4, and on unless switched off
 * in Appearance. Material's shapes move to say something is happening; here
 * what is happening is the music.
 *
 * Only the flat of each edge moves, a few dp inwards on a hit; the corners
 * keep their radius and the cover keeps its size, so at rest it is the plain
 * rounded square. The ripples drift slowly round the edge as well, so two
 * beats in a row do not look like the same frame twice. Still with Less
 * motion on, and still while paused: it settles back rather than stopping
 * mid-ripple.
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
    val drift = remember { mutableFloatStateOf(0f) }
    DisposableEffect(moving) {
        onWatched(moving)
        onDispose { onWatched(false) }
    }
    LaunchedEffect(moving, playing) {
        if (moving && playing) follow(beat, level, drift) else settle(level)
    }
    return graphicsLayer {
        val depth = level.floatValue * MAX_DEPTH.toPx()
        shape = RippledShape(corner.toPx(), depth, drift.floatValue)
        clip = true
    }
}

/** Frame by frame: the beat, eased a touch so a slice never reads as a jump. */
private suspend fun follow(beat: () -> Float, level: MutableFloatState, drift: MutableFloatState) {
    var last = 0L
    while (true) {
        withFrameNanos { now ->
            val dt = if (last == 0L) 16f else (now - last) / 1_000_000f
            last = now
            val target = beat().coerceIn(0f, 1f)
            // Up fast, down on the beat's own fall.
            val rate = if (target > level.floatValue) 0.6f else 0.25f
            level.floatValue += (target - level.floatValue) * rate
            drift.floatValue = ((drift.floatValue + dt / DRIFT_MS * 2 * PI.toFloat()) % (2 * PI.toFloat()))
        }
    }
}

private suspend fun settle(level: MutableFloatState) {
    if (level.floatValue == 0f) return
    animate(level.floatValue, 0f) { value, _ -> level.floatValue = value }
}

private class RippledShape(
    private val corner: Float,
    private val depth: Float,
    private val phase: Float
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val side = minOf(size.width, size.height)
        val points = rippledSquare(side, corner, depth, waves = WAVES, phase = phase)
        val path = Path()
        path.moveTo(points[0], points[1])
        for (i in 2 until points.size step 2) path.lineTo(points[i], points[i + 1])
        path.close()
        return Outline.Generic(path)
    }
}

/** How far in an edge goes on the biggest hit: "a little at the edges". */
private val MAX_DEPTH = 7.dp
private const val WAVES = 3
/** Once round the edge every eight seconds. */
private const val DRIFT_MS = 8_000f
