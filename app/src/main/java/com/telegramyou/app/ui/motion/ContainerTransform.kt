package com.telegramyou.app.ui.motion

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearOutSlowInEasing
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
import androidx.compose.runtime.staticCompositionLocalOf
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

/**
 * Appearance → Less motion. Read by the few places that move more than
 * they need to: the container transforms become the plain fade their
 * screens already have, a typing avatar holds its shape, and the theme's
 * springs lose their bounce (TelegramYouTheme).
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

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
 * Stories use the theme's spatial spring. Chat transitions use fixed
 * timing so the row becomes tappable as soon as the shorter move ends.
 *
 * [shape] clips the container while it travels. [isScreen] is the
 * full-screen side, which is scaled rather than laid out again each frame —
 * see the resize mode below. A no-op wherever there is no navigation
 * transition to ride on — previews, tests, the tablet's two panes — and
 * with Less motion on, where the screen's own fade is all there is.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.containerTransform(
    key: Any,
    shape: Shape,
    isScreen: Boolean = false,
    isChat: Boolean = false
): Modifier {
    if (LocalReduceMotion.current) return this
    val shared = LocalSharedTransitionScope.current ?: return this
    val animated = LocalNavAnimatedScope.current ?: return this
    val fade = if (isChat) ChatEarlyFade else MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val lateFade = if (isChat) ChatLateFade else LateFade
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
            enter = if (isScreen) fadeIn(fade) else fadeIn(lateFade),
            exit = if (isScreen) fadeOut(lateFade) else fadeOut(fade),
            // Under a finger, a duration rather than a spring. A predictive
            // back gesture seeks the transition — the share of the swipe
            // becomes the share of its total length — and a spring's length
            // runs until it is still to half a pixel, when it looked done a
            // good while before. So the first half of the swipe did nearly
            // all the shrinking and the rest crept, and the release crept
            // out the same way. A tween moves in step with the finger.
            boundsTransform = BoundsTransform { _, _ ->
                when {
                    isChat -> ChatBounds
                    animated.transition.isSeeking -> GestureBounds
                    else -> ContainerSpring
                }
            },
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

/** Chat bounds and fades finish together so the list becomes tappable promptly. */
internal const val ChatTransitionMillis = 220
private val ChatBounds: FiniteAnimationSpec<Rect> = tween(ChatTransitionMillis, easing = LinearOutSlowInEasing)
private val ChatEarlyFade: FiniteAnimationSpec<Float> = tween(110)
private val ChatLateFade: FiniteAnimationSpec<Float> = tween(80, delayMillis = ChatTransitionMillis - 80)

/** The standard scheme's slow spatial spring: barely overshoots. The story viewer's. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val ContainerSpring = MotionScheme.standard().slowSpatialSpec<Rect>()

/**
 * The container's movement while a back gesture drives it: as long as the
 * spring looks, not as long as it takes to settle, and easing out, so the
 * last of the swipe still visibly lands the chat in its row.
 */
private val GestureBounds: FiniteAnimationSpec<Rect> =
    tween(durationMillis = 350, easing = LinearOutSlowInEasing)

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
