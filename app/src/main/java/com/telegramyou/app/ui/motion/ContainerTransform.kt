package com.telegramyou.app.ui.motion

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
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
 * Moved by springs rather than by a duration and a curve: the bounds on
 * the standard scheme's slow spatial spring, the two screens' contents
 * fading across on the theme's effects spec. Slow because the container
 * ends as the whole screen, and Material gives full-screen movement the
 * slow speed. Standard rather than the theme's expressive, because the
 * expressive spring overshoots, and a container the size of the display
 * overshooting is the whole screen swelling past its edges and back — on a
 * phone that read as the conversation shaking, and for a day the chat was
 * opened by sliding it in from the side instead. It was the spring, not the
 * pattern; the owner wanted the pattern back.
 *
 * [shape] clips the container while it travels. [isScreen] is the
 * full-screen side, which is scaled rather than laid out again each frame. A no-op wherever there is
 * no navigation transition to ride on — previews, tests, the tablet's two
 * panes.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.containerTransform(key: Any, shape: Shape, isScreen: Boolean = false): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val animated = LocalNavAnimatedScope.current ?: return this
    val bounds = ContainerSpring
    val fade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    return with(shared) {
        this@containerTransform.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animated,
            enter = fadeIn(fade),
            exit = fadeOut(fade),
            boundsTransform = BoundsTransform { _, _ -> bounds },
            // The small side — a row, a circle — is laid out again at each
            // size, which costs nothing. The screen side is not: laying out a
            // whole conversation sixty times a second was what made opening a
            // chat stutter. It is laid out once, at full size, and scaled
            // into the growing container instead — a preview of itself.
            resizeMode = if (isScreen) {
                SharedTransitionScope.ResizeMode.scaleToBounds()
            } else {
                SharedTransitionScope.ResizeMode.RemeasureToBounds
            },
            clipInOverlayDuringTransition = OverlayClip(shape)
        )
    }
}

/** The standard scheme's slow spatial spring: barely overshoots; see above. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val ContainerSpring = MotionScheme.standard().slowSpatialSpec<Rect>()

/** The key a chat's row and its conversation share. */
fun chatContainerKey(chatId: Long): String = "chat-$chatId"

/** The key a story's circle and its viewer share. */
fun storyContainerKey(storyId: Long): String = "story-$storyId"
