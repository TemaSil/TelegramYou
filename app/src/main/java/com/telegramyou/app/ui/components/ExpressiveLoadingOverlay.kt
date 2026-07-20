package com.telegramyou.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun ExpressiveLoadingOverlay(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.92f),
        exit = fadeOut() + scaleOut(targetScale = 1.05f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.scrim.copy(alpha = 0.28f),
                            MaterialTheme.colorScheme.scrim.copy(alpha = 0.12f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            WavyProgressRing()
        }
    }
}

/** Custom wavy ring — Expressive-style loading without Material3 alpha APIs. */
@Composable
fun WavyProgressRing(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "wavy")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier.size(56.dp)) {
        val stroke = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
        val radius = size.minDimension / 2f - stroke.width
        val steps = 64
        for (i in 0 until steps) {
            val t0 = i / steps.toFloat()
            val t1 = (i + 1) / steps.toFloat()
            val a0 = t0 * 2f * PI.toFloat() + phase
            val a1 = t1 * 2f * PI.toFloat() + phase
            val wobble0 = 1f + 0.08f * sin(a0 * 3f)
            val wobble1 = 1f + 0.08f * sin(a1 * 3f)
            val p0 = Offset(
                center.x + radius * wobble0 * kotlin.math.cos(a0),
                center.y + radius * wobble0 * kotlin.math.sin(a0)
            )
            val p1 = Offset(
                center.x + radius * wobble1 * kotlin.math.cos(a1),
                center.y + radius * wobble1 * kotlin.math.sin(a1)
            )
            drawLine(color = color, start = p0, end = p1, strokeWidth = stroke.width, cap = StrokeCap.Round)
        }
    }
}
