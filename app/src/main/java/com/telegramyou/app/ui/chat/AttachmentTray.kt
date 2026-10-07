package com.telegramyou.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.telegramyou.app.telegram.model.AttachmentDraft
import com.telegramyou.app.ui.icons.Symbols
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * What is about to go, just above the composer (2.0): each photo as a small
 * picture, each file as a tile with its name, and a tile at the end for
 * more, up to Telegram's ten. A long press picks one up to drag to its
 * place in the album. Inside the field, where the pictures are large, a
 * cross sits on each to take it out; in the small strip over the capsule
 * the crosses would cover the pictures, so there they come up only with a
 * long press and a tap puts them away — both on the owner's word. The text
 * typed meanwhile goes with them as the caption.
 *
 * It replaced a translucent chip that said "3 photo(s)", which showed
 * nothing of what was in it and could only throw all of it away at once.
 */
@Composable
internal fun AttachmentTray(
    draft: AttachmentDraft,
    onRemove: (Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onAddMore: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Inside the field rather than over it (2.0): the field grows up round
     * the pictures, larger and portrait, the caption typed under them — as
     * in the reference the owner sent. Over the capsule composer it stays a
     * strip above.
     */
    inField: Boolean = false
) {
    val tileWidth = if (inField) FIELD_TILE_WIDTH else TILE
    val tileHeight = if (inField) FIELD_TILE_HEIGHT else TILE
    val items: List<Pair<String, String?>> = when (draft) {
        is AttachmentDraft.Photos -> draft.uris.map { it to null }
        is AttachmentDraft.Files -> draft.uris.zip(draft.names)
        else -> return
    }
    val latest by rememberUpdatedState(items.map { it.first })
    val haptics = LocalHapticFeedback.current
    val step = with(LocalDensity.current) { (tileWidth + TILE_GAP).toPx() }
    // The one being dragged, by its uri, and how far from its place it is.
    // It keeps both after the finger lifts, while it settles into its place.
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    // The finger still on it: what lifts it.
    var held by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val motion = MaterialTheme.motionScheme
    // The crosses, up since a long press.
    var editing by remember { mutableStateOf(false) }
    // Let go: it comes down and eases into its place rather than jumping
    // there, staying above its neighbours until it has landed.
    fun settle(uri: String) {
        held = false
        val from = dragOffset
        settling = scope.launch {
            animate(from, 0f, animationSpec = motion.defaultSpatialSpec()) { value, _ -> dragOffset = value }
            if (dragging == uri) dragging = null
        }
    }

    // A LazyRow rather than a scrolling Row, for animateItem (2.0.1): the
    // neighbours slide aside as one is dragged past them, a picture taken
    // out fades while the rest close up, and one added fades in — where
    // they used to jump.
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(TILE_GAP),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(
            start = if (inField) 8.dp else PLAIN_GUTTER,
            end = if (inField) 8.dp else PLAIN_GUTTER,
            top = if (inField) 8.dp else 6.dp,
            bottom = if (inField) 2.dp else 6.dp
        ),
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Attachments, ${items.size}" }
    ) {
        itemsIndexed(items, key = { _, item -> item.first }) { index, (uri, name) ->
            val lifted = dragging == uri
            val lift by animateFloatAsState(
                targetValue = if (lifted && held) 1.08f else 1f,
                animationSpec = motion.fastSpatialSpec(),
                label = "trayLift"
            )
            Box(
                modifier = Modifier
                    // Not on the one under the finger: its place changes as
                    // it passes a neighbour and the offset is taken back by
                    // the same step, so animating that place too would pull
                    // it away from the finger.
                    .then(
                        if (lifted) {
                            Modifier
                        } else {
                            Modifier.animateItem(
                                fadeInSpec = motion.defaultEffectsSpec(),
                                placementSpec = motion.defaultSpatialSpec(),
                                fadeOutSpec = motion.fastEffectsSpec()
                            )
                        }
                    )
                    .size(width = tileWidth, height = tileHeight)
                    .zIndex(if (lifted) 1f else 0f)
                    .graphicsLayer {
                        translationX = if (lifted) dragOffset else 0f
                        scaleX = lift
                        scaleY = lift
                    }
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .pointerInput(uri) {
                        detectTapGestures(onTap = { editing = false })
                    }
                    .pointerInput(uri) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                settling?.cancel()
                                editing = true
                                dragging = uri
                                dragOffset = 0f
                                held = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount.x
                                // Past half a tile, it changes places with
                                // the neighbour, and the offset is taken
                                // back by a tile so it stays under the finger.
                                val at = latest.indexOf(uri)
                                if (dragOffset > step / 2 && at < latest.lastIndex) {
                                    onMove(at, at + 1)
                                    dragOffset -= step
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                } else if (dragOffset < -step / 2 && at > 0) {
                                    onMove(at, at - 1)
                                    dragOffset += step
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                }
                            },
                            onDragEnd = { settle(uri) },
                            onDragCancel = { settle(uri) }
                        )
                    }
            ) {
                if (name == null) {
                    AsyncImage(
                        model = uri,
                        contentDescription = "Photo ${index + 1}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp)
                    ) {
                        Icon(Symbols.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            name,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                // The cross: on the picture, in its corner, small enough
                // to leave the picture readable and big enough to hit —
                // and only after a long press.
                if (editing || inField) Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable(onClickLabel = "Remove") {
                            // Nothing on one already leaving, still fading.
                            val at = latest.indexOf(uri)
                            if (at >= 0) onRemove(at)
                        }
                        .semantics { contentDescription = "Remove attachment" }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Symbols.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        if (items.size < ALBUM_LIMIT) {
            item(key = ADD_MORE_KEY) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .animateItem(
                            fadeInSpec = motion.defaultEffectsSpec(),
                            placementSpec = motion.defaultSpatialSpec(),
                            fadeOutSpec = motion.fastEffectsSpec()
                        )
                        .size(width = tileWidth, height = tileHeight)
                        .clip(MaterialTheme.shapes.large)
                        .clickable(onClick = onAddMore)
                        .semantics { contentDescription = "Add more" }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Symbols.Add, contentDescription = null)
                    }
                }
            }
        }
    }
}

/** The add-more tile's key, apart from every uri. */
private const val ADD_MORE_KEY = "tray:add-more"

/** A tile in the tray, and the space between two. */
private val TILE = 76.dp
private val TILE_GAP = 8.dp

/** Inside the field the pictures are larger, and taller than wide, as in the reference. */
private val FIELD_TILE_WIDTH = 96.dp
private val FIELD_TILE_HEIGHT = 112.dp
