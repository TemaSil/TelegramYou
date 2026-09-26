package com.telegramyou.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import com.telegramyou.app.ui.avatars.avatarShapeIndex

/**
 * The outline of someone who is typing: their own shape, melting through
 * three others and back, turning slowly, for as long as they type.
 *
 * Material's loading indicator is a shape morphing while it turns, and this
 * borrows its language for the one thing a chat list waits on — somebody
 * writing. It starts and ends on the person's own shape (see [personShape]),
 * so when they stop the avatar is theirs again rather than wherever the
 * cycle had got to. With shapes switched off in Appearance it starts from the
 * circle, and still moves: the motion is the signal, not the shapes.
 *
 * Each step eases in and out, so the outline dwells on each shape before
 * leaving it — a steady linear morph reads as a wobble rather than as a
 * sequence of shapes.
 */
@Composable
fun typingShape(seed: Long, typing: Boolean, rest: Shape): Shape {
    val start = if (LocalShapedAvatars.current) avatarShapeIndex(seed, SHAPE_COUNT) else 0
    // Lazily: every avatar on screen comes through here, and matching two
    // polygons for a Morph is not free. Only someone typing pays for it.
    val morphs = remember(start) { lazy { TypingMorphs(start) } }

    val phase = remember { Animatable(0f) }
    val turn = remember { Animatable(0f) }
    val back = remember { Animatable(0f) }
    // Which stop the way home starts from; -1 while not on the way home.
    var homeFrom by remember { mutableIntStateOf(-1) }
    var moving by remember { mutableStateOf(typing) }

    LaunchedEffect(typing) {
        if (typing) {
            moving = true
            homeFrom = -1
            back.snapTo(0f)
            coroutineScope {
                launch {
                    while (true) {
                        phase.animateTo(
                            STOPS.toFloat(),
                            tween(((STOPS - phase.value) * STEP_MILLIS).toInt().coerceAtLeast(1), easing = LinearEasing)
                        )
                        phase.snapTo(0f)
                    }
                }
                launch {
                    while (true) {
                        turn.animateTo(
                            360f,
                            tween(((1f - turn.value / 360f) * TURN_MILLIS).toInt().coerceAtLeast(1), easing = LinearEasing)
                        )
                        turn.snapTo(0f)
                    }
                }
            }
        } else if (moving) {
            // It used to snap straight back to the person's shape from
            // wherever the cycle had got to — mid-morph and turned — which
            // read as a jump. Now the current step finishes, that stop melts
            // into the person's own shape, and the turn eases round to where
            // it started, together.
            coroutineScope {
                launch {
                    turn.animateTo(if (turn.value == 0f) 0f else 360f, tween(SETTLE_MILLIS, easing = FastOutSlowInEasing))
                }
                launch {
                    val next = kotlin.math.ceil(phase.value)
                    phase.animateTo(next, tween(((next - phase.value) * STEP_MILLIS).toInt().coerceAtLeast(1), easing = LinearEasing))
                    val stop = next.toInt() % STOPS
                    if (stop != 0) {
                        homeFrom = stop
                        back.animateTo(1f, tween(RETURN_MILLIS, easing = FastOutSlowInEasing))
                    }
                }
            }
            phase.snapTo(0f)
            turn.snapTo(0f)
            back.snapTo(0f)
            homeFrom = -1
            moving = false
        }
    }
    if (!moving) return rest
    val from = homeFrom
    if (from >= 0) return MorphShape(morphs.value.home[from], back.value, turn.value)
    val step = phase.value.toInt().coerceIn(0, STOPS - 1)
    val progress = FastOutSlowInEasing.transform(phase.value - step)
    return MorphShape(morphs.value.cycle[step], progress, turn.value)
}

/**
 * The morphs a typing avatar moves through: [cycle] from each stop to the
 * next, and [home] straight from each stop back to the first, for when the
 * typing stops mid-cycle.
 */
private class TypingMorphs(start: Int) {
    private val stops = List(STOPS) { step -> materialPolygon((start + step * 3) % SHAPE_COUNT) }
    val cycle = stops.indices.map { step -> Morph(stops[step], stops[(step + 1) % STOPS]) }
    val home = stops.map { Morph(it, stops[0]) }
}

/**
 * Entry [startIndex] of the shape set, morphing through three others and
 * back and turning, for as long as it is composed. [typingShape] is this at
 * a typing pace; the login screen's mark is it at a resting one.
 */
@Composable
fun cyclingShape(
    startIndex: Int,
    stepMillis: Int = STEP_MILLIS,
    turnMillis: Int = TURN_MILLIS
): Shape {
    val start = startIndex.mod(SHAPE_COUNT)
    // Three steps away each time, so neighbouring stops are never two
    // shapes that look alike.
    val morphs = remember(start) {
        val stops = List(STOPS) { step -> materialPolygon((start + step * 3) % SHAPE_COUNT) }
        stops.indices.map { step -> Morph(stops[step], stops[(step + 1) % STOPS]) }
    }
    val transition = rememberInfiniteTransition(label = "typing")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = STOPS.toFloat(),
        animationSpec = infiniteRepeatable(tween(STOPS * stepMillis, easing = LinearEasing)),
        label = "typingPhase"
    )
    val turn by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(turnMillis, easing = LinearEasing)),
        label = "typingTurn"
    )
    val step = phase.toInt().coerceAtMost(STOPS - 1)
    val progress = FastOutSlowInEasing.transform(phase - step)
    return MorphShape(morphs[step], progress, turn)
}

/** One moment of a morph, turned by [degrees], scaled to whatever it clips. */
private class MorphShape(
    private val morph: Morph,
    private val progress: Float,
    private val degrees: Float
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        // The polygons live in the unit square; turned about its centre, then
        // stretched to the avatar.
        val path = morph.toPath(progress)
        val matrix = android.graphics.Matrix().apply {
            setRotate(degrees, 0.5f, 0.5f)
            postScale(size.width, size.height)
        }
        path.transform(matrix)
        return Outline.Generic(path.asComposePath())
    }
}

private const val STOPS = 4
private const val STEP_MILLIS = 650
private const val TURN_MILLIS = 6_000
private const val SETTLE_MILLIS = 900
private const val RETURN_MILLIS = 500
