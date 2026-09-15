package com.telegramyou.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush

/**
 * A scrim over the screen while something is in flight.
 *
 * The indicator is Material's own [CircularProgressIndicator]. An earlier
 * version drew a wavy ring by hand on a Canvas — sixty-four line segments
 * around a sine-modulated radius — standing in for Expressive's
 * `LoadingIndicator`. That substitute is gone, but the real thing did not
 * replace it: `LoadingIndicator` is not public in any stable material3, only
 * in the 1.5.0 alphas. A stock component that exists beats a hand-drawn
 * imitation of one that does not.
 */
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
            CircularProgressIndicator()
        }
    }
}
