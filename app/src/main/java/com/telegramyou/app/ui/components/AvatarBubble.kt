package com.telegramyou.app.ui.components

import com.telegramyou.app.ui.icons.Symbols
import androidx.compose.material3.Icon
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.telegramyou.app.ui.theme.avatarColor
import java.io.File

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
    /**
     * They are typing. The outline morphs through Material's shapes while
     * this holds and returns to [shape] when it stops; see typingShape.
     */
    typing: Boolean = false,
    ring: Boolean = false,
    ringSeen: Boolean = false,
    /**
     * The person's or the chat's own picture, once it is on this device.
     * Drawn over the initials, which stay underneath as what shows while it
     * loads and for everyone who never set one.
     */
    photoPath: String? = null,
    /**
     * Saved Messages: a bookmark on the theme's primary instead of initials,
     * the way every Telegram client marks the chat with oneself.
     */
    savedMessages: Boolean = false,
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
    // Always through typingShape, so stopping has somewhere to animate from.
    val outline = typingShape(seed, typing, shape)
    val base = if (savedMessages) MaterialTheme.colorScheme.primary else avatarColor(seed)
    val initials = title
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "?" }

    // Two boxes, and the outer one deliberately does not clip. The avatar's
    // own outline belongs to the shape below; the online dot sits beside it
    // rather than inside it, because a `clip(shape)` cuts everything in the
    // box to that silhouette — which trimmed the dot to a crescent on a
    // circle and swallowed it whole the day the chat list started drawing
    // clovers.
    Box(
        modifier = modifier.scale(scale).size(size),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .then(
                    if (ring) {
                        // Two states, two flat colours: the accent for a
                        // story not yet seen, grey for one that has been.
                        // The unseen ring swept primary into secondary into
                        // tertiary once, and the seen one was a fade of
                        // grey into lighter grey — gradients where a state
                        // was meant.
                        //
                        // And a gap inside the ring, the page's colour
                        // between it and the picture, as Telegram draws it:
                        // pressed against a coloured avatar the ring read
                        // as a second colour of the avatar itself.
                        Modifier
                            .border(
                                width = RING_WIDTH,
                                color = if (ringSeen) {
                                    MaterialTheme.colorScheme.outlineVariant
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                shape = outline
                            )
                            .padding(RING_WIDTH + RING_GAP)
                    } else Modifier
                )
                .clip(outline)
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
            if (savedMessages) {
                Icon(
                    Symbols.BookmarkFilled,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(size * 0.5f)
                )
            } else {
                Text(
                    text = initials,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value / 3.2f).sp
                )
            }
            if (photoPath != null && !savedMessages) {
                AsyncImage(
                    model = File(photoPath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    // The whole clipped box. The ring and its gap are outside
                    // it already (the padding before the clip); a further
                    // inset here, left from before the gap, showed a band of
                    // the placeholder's colour round every story's picture.
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (showOnline) {
            // In the corner of the box rather than of the shape, with a ring
            // of background around it: wherever the outline happens to run
            // underneath, the dot stays a whole dot and stays legible.
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

/** The story ring's stroke, and the gap between it and the picture. */
private val RING_WIDTH = 2.5.dp
private val RING_GAP = 2.dp
