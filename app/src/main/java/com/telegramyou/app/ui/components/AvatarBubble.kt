package com.telegramyou.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telegramyou.app.ui.theme.avatarColor

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AvatarBubble(
    title: String,
    seed: Long,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    /**
     * The outline. A circle unless a caller says otherwise — the cluster in a
     * group's header hands each member one of Material's shapes instead.
     */
    shape: Shape = CircleShape,
    showOnline: Boolean = false,
    ring: Boolean = false,
    ringSeen: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // The spring comes from the theme's motion scheme, not from numbers
    // chosen here. Press feedback is movement, so it is a spatial spec; fast,
    // because a touch response that lags reads as a dropped frame.
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "avatarScale"
    )
    val base = avatarColor(seed)
    val initials = title
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "?" }

    Box(
        modifier = modifier
            .scale(scale)
            .size(size)
            .then(
                if (ring) {
                    Modifier.border(
                        width = 2.5.dp,
                        brush = if (ringSeen) {
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                )
                            )
                        } else {
                            Brush.sweepGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary,
                                    MaterialTheme.colorScheme.tertiary,
                                    MaterialTheme.colorScheme.primary
                                )
                            )
                        },
                        shape = shape
                    )
                } else Modifier
            )
            .clip(shape)
            .background(
                Brush.linearGradient(listOf(base, base.copy(alpha = 0.75f)))
            )
            .then(
                if (onClick != null) {
                    // No `indication = null`. The scale below is extra, not a
                    // replacement: switching Material's own press feedback off
                    // leaves a tap with no state layer at all.
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = ripple(bounded = false),
                        onClick = onClick
                    )
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value / 3.2f).sp
        )
        if (showOnline) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.28f)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    .background(Color(0xFF2ED573), CircleShape)
            )
        }
    }
}
