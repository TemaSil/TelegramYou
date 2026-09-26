package com.telegramyou.app.ui.motion

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * The shared-transition layout around the whole navigation graph, for the
 * screens inside it that open out of something on the screen before.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/** The navigation destination's own enter-and-exit, which a shared transition runs on. */
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** A chat row's corners, which the conversation opens out of and closes back into. */
val ChatContainerShape: Shape = RoundedCornerShape(20.dp)

/**
 * A story circle's corners: half of its 64dp, so it is a circle at the size
 * it starts from, and the same 32dp at full screen — the corners of the
 * phone, near enough — rather than a pill the height of the screen.
 */
val StoryContainerShape: Shape = RoundedCornerShape(32.dp)

/**
 * Material's container transform: this element and the one sharing [key]
 * on the next screen are one container, which grows from here into there,
 * and shrinks back on the way out — including under the finger during a
 * predictive back gesture.
 *
 * Moved by springs rather than by a duration and a curve, the two screens'
 * contents fading across on the theme's effects spec. The bounds move on
 * [bounds]: the standard scheme's slow spatial spring by default, which
 * barely overshoots and is the story viewer's, or [ChatContainerSpring] for
 * a chat.
 *
 * [shape] clips the container while it travels. [isScreen] is the
 * full-screen side, which is scaled rather than laid out again each frame —
 * see the resize mode below. A no-op wherever there is no navigation
 * transition to ride on — previews, tests, the tablet's two panes.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.containerTransform(
    key: Any,
    shape: Shape,
    isScreen: Boolean = false,
    bounds: FiniteAnimationSpec<Rect> = ContainerSpring
): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val animated = LocalNavAnimatedScope.current ?: return this
    val fade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    return with(shared) {
        this@containerTransform.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animated,
            // The crossfade happens at the small end, both ways. Opening,
            // the row gives way at once and the screen's picture does the
            // growing. Closing has to be the mirror of that: the screen's
            // picture shrinks whole and the row only shows through as it
            // lands. With one quick fade both ways the screen vanished in
            // the first fifth of a second, and what shrank for the rest of
            // the way was the row, laid out again at every size — avatar and
            // name sliding about in a box the height of the display. That
            // was the close that still read as jerky.
            enter = if (isScreen) fadeIn(fade) else fadeIn(LateFade),
            exit = if (isScreen) fadeOut(LateFade) else fadeOut(fade),
            boundsTransform = BoundsTransform { _, _ -> bounds },
            // The small side — a row, a circle — is laid out again at each
            // size, which costs nothing. The screen side is not: laid out
            // again sixty times a second, a conversation re-wrapped every line
            // on every frame, which is what read as the whole of it shaking
            // in the first version. It is laid out once, at full size and with
            // its messages already there (TelegramRepository.warmChat), and
            // scaled into the growing container — a picture of itself that
            // opens whole. Scaled to the container's width and pinned to its
            // top, so what shows through the row at the start is the screen's
            // own header, the avatar and the name the row was showing.
            resizeMode = if (isScreen) {
                SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.FillWidth, Alignment.TopCenter)
            } else {
                SharedTransitionScope.ResizeMode.RemeasureToBounds
            },
            clipInOverlayDuringTransition = OverlayClip(shape)
        )
    }
}

/**
 * How a chat opens out of its row: at the pace of the first version of this
 * — the theme's expressive spatial spring, stiffness 380 — with its bounce
 * taken out, on the owner's word. A screen-sized container that overshoots
 * swells past the display's edges and back, and that bounce was the part
 * that did not belong here; the pace was.
 */
val ChatContainerSpring: FiniteAnimationSpec<Rect> =
    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 380f)

/** The standard scheme's slow spatial spring: barely overshoots. The story viewer's. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val ContainerSpring = MotionScheme.standard().slowSpatialSpec<Rect>()

/**
 * The crossfade at the end of a close: held until the container has all
 * but reached the row or circle, then quick. A tween because a spring has no
 * delay; the bounds spring has settled to within a few percent by then.
 */
private val LateFade: FiniteAnimationSpec<Float> = tween(durationMillis = 120, delayMillis = 200)

/** The key a chat's row and its conversation share. */
fun chatContainerKey(chatId: Long): String = "chat-$chatId"

/** The key a story's circle and its viewer share. */
fun storyContainerKey(storyId: Long): String = "story-$storyId"
