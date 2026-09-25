package com.telegramyou.app.ui.motion

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
 * Moved by the theme's motion scheme rather than by a duration and a curve:
 * the bounds on the spatial spring everything else moves on, the two
 * screens' contents fading across on its effects spec. That is what makes
 * the pattern Expressive rather than the 300 milliseconds it used to be.
 *
 * [shape] clips the container while it travels. A no-op wherever there is
 * no navigation transition to ride on — previews, tests, the tablet's two
 * panes.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.containerTransform(key: Any, shape: Shape): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val animated = LocalNavAnimatedScope.current ?: return this
    val bounds = MaterialTheme.motionScheme.defaultSpatialSpec<Rect>()
    val fade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    return with(shared) {
        this@containerTransform.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animated,
            enter = fadeIn(fade),
            exit = fadeOut(fade),
            boundsTransform = BoundsTransform { _, _ -> bounds },
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            clipInOverlayDuringTransition = OverlayClip(shape)
        )
    }
}

/** The key a chat's row and its conversation share. */
fun chatContainerKey(chatId: Long): String = "chat-$chatId"

/** The key a story's circle and its viewer share. */
fun storyContainerKey(storyId: Long): String = "story-$storyId"
