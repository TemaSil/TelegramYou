package com.telegramyou.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The three dots that mean somebody is writing.
 *
 * One of the four places this project draws by hand, and it is on the list for
 * a reason: Material has no component for it. The nearest thing,
 * `LoadingIndicator`, says "the app is busy", which is the opposite of what
 * this says — the app is idle and a person is not.
 *
 * Each dot rises and falls on the same wave, a third of a cycle apart, so the
 * motion reads as a travelling ripple rather than three things blinking. The
 * period is deliberately slow: this sits in an app bar under the chat's name
 * and has to be noticeable without pulling the eye off the conversation.
 */
@Composable
fun TypingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val transition = rememberInfiniteTransition(label = "typing")
    // One driver for all three dots. Three separate animations would drift
    // apart over a long conversation, and the phase offset below is what makes
    // them a wave instead of a chorus.
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_200),
            repeatMode = RepeatMode.Restart
        ),
        label = "typingPhase"
    )

    Canvas(modifier = modifier.size(width = 24.dp, height = 10.dp)) {
        val radius = size.height / 5f
        val spacing = size.width / 3f
        repeat(3) { index ->
            // Each dot is a third of a cycle behind the one before it.
            val local = (phase - index).mod(3f)
            // Up on the first third of its own cycle, down on the second, flat
            // for the rest — the pause is what keeps it from looking frantic.
            val lift = when {
                local < 0.5f -> local / 0.5f
                local < 1f -> (1f - local) / 0.5f
                else -> 0f
            }
            drawCircle(
                color = color,
                radius = radius,
                center = Offset(
                    x = spacing * (index + 0.5f),
                    y = size.height / 2f - lift * radius
                ),
                alpha = 0.5f + lift * 0.5f
            )
        }
    }
}
