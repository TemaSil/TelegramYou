package com.telegramyou.app.ui.chat

import androidx.compose.ui.layout.layout
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.telegramyou.app.telegram.model.VideoContent
import com.telegramyou.app.telegram.model.waveformBars
import com.telegramyou.app.ui.media.FileTransfer
import com.telegramyou.app.ui.media.transferLabel
import com.telegramyou.app.ui.media.transferProgress
import com.telegramyou.app.ui.media.Zoom
import com.telegramyou.app.ui.media.dismissProgress
import com.telegramyou.app.ui.media.shouldDismiss
import com.telegramyou.app.ui.media.zoomAfterGesture
import com.telegramyou.app.ui.media.zoomToggled

// What a bubble holds when it is more than words: photos and their viewer, videos, GIFs, round video messages, voice notes and their waveform.

/**
 * One photo, full screen.
 *
 * A `Dialog` rather than a route: it is not somewhere the conversation has
 * navigated to, and back should return to the message rather than to whatever
 * the graph thinks came before. usePlatformDefaultWidth false is what lets it
 * reach the edges — without it a dialog is inset like an alert, which is not
 * what a photo wants.
 *
 * `ContentScale.Fit`, not Crop: the bubble crops to keep the list tidy, and
 * the whole point of opening it is to see the parts the bubble cut off.
 *
 * Pinch to zoom is not here yet. A photo that fills the screen is most of the
 * way to the thing, and a half-working gesture would be worse than none.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoViewer(
    path: String,
    caption: String,
    onDismiss: () -> Unit
) {
    // Where the photo is and how big, and how far a drag has taken it towards
    // being let go. Both are remembered per photo rather than hoisted: a
    // viewer that reopened at yesterday's zoom would be answering a question
    // nobody asked.
    var zoom by remember { mutableStateOf(Zoom()) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }

    val progress = dismissProgress(dragY, viewport.height.toFloat())
    // The scrim thins and the photo shrinks together, so letting go halfway is
    // visibly halfway rather than a state the gesture cannot show.
    val scrim = 0.92f * (1f - progress)
    val dismissScale = 1f - progress * 0.2f

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewport = it }
                // Its own scrim, because the photo is the content rather than
                // something sitting on a surface.
                .background(Color.Black.copy(alpha = scrim)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = path,
                contentDescription = caption.ifBlank { "Photo" },
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoom.scale * dismissScale
                        scaleY = zoom.scale * dismissScale
                        translationX = zoom.offsetX
                        translationY = zoom.offsetY + dragY
                    }
                    // Pinch and drag, in one gesture detector because they are
                    // one gesture: two fingers scale, one pans, and which is
                    // happening changes mid-stroke.
                    .pointerInput(path) {
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            zoom = zoomAfterGesture(
                                current = zoom,
                                scaleChange = gestureZoom,
                                panX = pan.x,
                                panY = pan.y,
                                viewportWidth = size.width.toFloat(),
                                viewportHeight = size.height.toFloat()
                            )
                        }
                    }
                    // Drag to dismiss, and only while zoomed out: once the
                    // photo is larger than the frame a vertical drag means
                    // "look further down", which is what the detector above
                    // is for.
                    .pointerInput(path, zoom.isZoomed) {
                        if (zoom.isZoomed) return@pointerInput
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (shouldDismiss(dragY, size.height.toFloat())) {
                                    onDismiss()
                                } else {
                                    dragY = 0f
                                }
                            },
                            onDragCancel = { dragY = 0f }
                        ) { _, delta -> dragY += delta }
                    }
                    .pointerInput(path) {
                        detectTapGestures(
                            // A tap on the photo closes it, as it always has.
                            // A tap while zoomed does not: the photo is being
                            // looked at, and a stray finger should not end
                            // that.
                            onTap = { if (!zoom.isZoomed) onDismiss() },
                            onDoubleTap = { zoom = zoomToggled(zoom) }
                        )
                    }
            )
            if (caption.isNotBlank() && caption != "Photo") {
                Text(
                    caption,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(24.dp)
                        // Out of the way of the photo itself once it is being
                        // examined, and back when it is not.
                        .alpha(if (zoom.isZoomed) 0f else 1f - progress)
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(8.dp)
                    .alpha(1f - progress)
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}

/**
 * A photo in a bubble.
 *
 * The space is reserved from the photo's own aspect ratio before any bytes
 * arrive, so the bubble does not change size when they do — in a list, one
 * bubble resizing moves everything below it, which is the difference between
 * a conversation loading and a conversation jumping.
 *
 * The download is asked for when this appears rather than on a tap, because a
 * photo is meant to be seen without being asked for. Coil takes it from there:
 * a cache, cancellation when the row scrolls away, and downsampling to the
 * size actually drawn.
 */
@Composable
internal fun PhotoMessage(
    bleedTop: Boolean,
    path: String?,
    aspect: Float,
    caption: String,
    outgoing: Boolean,
    transfer: FileTransfer?,
    onVisible: () -> Unit,
    onOpen: () -> Unit
) {
    LaunchedEffect(path) {
        if (path == null) onVisible()
    }
    Column {
        Box(
            modifier = Modifier
                // The whole width of the bubble, out past the padding the
                // words inside it keep: the photo is the bubble, with its
                // corners, and the caption and the time sit under it. It used
                // to be a smaller rounded picture inside the bubble, framed
                // by a band of bubble colour on every side.
                .bleed(horizontal = BUBBLE_PADDING_H, top = if (bleedTop) BUBBLE_PADDING_V else 0.dp)
                .fillMaxWidth()
                // Clamped: a panorama would otherwise be a sliver and a very
                // tall photo would fill the screen on its own.
                .aspectRatio(aspect.coerceIn(0.6f, 1.9f))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable(onClick = onOpen),
            contentAlignment = Alignment.Center
        ) {
            if (path == null) {
                // Only where nothing is known about the file. Once bytes are
                // moving the bar below says it better, and two spinners for
                // one wait is one too many.
                if (transfer == null) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                AsyncImage(
                    model = path,
                    contentDescription = caption.ifBlank { "Photo" },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            transfer?.let {
                TransferOverlay(
                    transfer = it,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                )
            }
        }
        // "Photo" is what a message with no caption is called, not something
        // the sender wrote, so it is not repeated under the picture.
        if (caption.isNotBlank() && caption != "Photo") {
            Spacer(Modifier.height(6.dp))
            Text(
                caption,
                color = if (outgoing) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * The bar over a file that is moving, and the line that says which way.
 *
 * `LinearProgressIndicator` in both of its forms: determinate once the size
 * is known, indeterminate before that — because a determinate bar at zero
 * claims a length the server has not given yet, and a bar that sits still is
 * indistinguishable from one that has stalled.
 *
 * On a scrim, because it is drawn over whatever the file will become: a
 * poster, a photo, the first frame of a video.
 */
@Composable
internal fun TransferOverlay(transfer: FileTransfer, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            transferLabel(transfer),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium
        )
        val progress = transferProgress(transfer)
        if (progress == null) {
            LinearProgressIndicator(
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            LinearProgressIndicator(
                progress = { progress },
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * A video message: its poster, its length, and the one control it needs.
 *
 * Built like [PhotoMessage] on purpose — same clamped aspect, same rounded
 * container, same reserved space before anything arrives — because a video in
 * a chat is a picture you can start, and making it a different shape would
 * say it was a different kind of thing.
 *
 * Two things sit over the poster: a play button in the middle, and the
 * duration in the corner. Both are drawn on scrims rather than straight onto
 * the frame, because a poster can be any colour and white on a white sky is
 * not a control.
 *
 * The poster is fetched on sight and the video is not. Scrolling past a
 * conversation should not pull down everything anyone ever sent.
 */
@Composable
internal fun VideoMessage(
    video: VideoContent,
    caption: String,
    outgoing: Boolean,
    bleedTop: Boolean,
    transfer: FileTransfer?,
    onPosterVisible: () -> Unit,
    onOpen: () -> Unit
) {
    LaunchedEffect(video.thumbPath) {
        if (video.thumbPath == null) onPosterVisible()
    }
    Column {
        val label = if (caption.isBlank() || caption == "Video") {
            "Video, ${formatDuration(video.durationSeconds.toLong())}"
        } else {
            "Video, $caption"
        }
        Box(
            modifier = Modifier
                // Out to the bubble's edges, as a photo is: the video is the
                // bubble, and its caption and time sit under it. It was a
                // smaller rounded frame inside a band of bubble colour.
                .bleed(horizontal = BUBBLE_PADDING_H, top = if (bleedTop) BUBBLE_PADDING_V else 0.dp)
                .fillMaxWidth()
                .aspectRatio(video.aspect.coerceIn(0.6f, 1.9f))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable(onClick = onOpen)
                // One description for the whole thing, on the part that is
                // tappable. The poster, the play button and the duration are
                // three nodes describing one object, and a screen reader
                // announcing all three in a row is how a photo of a cat
                // becomes "cat, play, nought colon eight".
                .semantics(mergeDescendants = true) { contentDescription = label },
            contentAlignment = Alignment.Center
        ) {
            video.thumbPath?.let { poster ->
                AsyncImage(
                    model = poster,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            // The button is there whether or not the poster is. A video with
            // no frame yet is still a video, and a bubble showing only a
            // spinner would look like a photo that failed.
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.45f),
                contentColor = Color.White,
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        // Described by the container above, which is what a
                        // reader announces and what a finger taps.
                        contentDescription = null,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            transfer?.let {
                TransferOverlay(
                    transfer = it,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                )
            }
            // The duration moves out of the way while a bar is there: they
            // want the same corner, and the bar is the one worth reading.
            if (video.durationSeconds > 0 && transfer == null) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = Color.Black.copy(alpha = 0.45f),
                    contentColor = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                ) {
                    Text(
                        formatDuration(video.durationSeconds.toLong()),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
        // As with a photo: "Video" is what an untitled one is called, not
        // something the sender wrote.
        if (caption.isNotBlank() && caption != "Video") {
            Spacer(Modifier.height(6.dp))
            Text(
                caption,
                color = if (outgoing) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * A GIF: Telegram's name for a short silent MP4. Out to the bubble's edges
 * like a photo, and playing on its own, looping, without sound — which is
 * what makes it a GIF rather than a video with a play button. Its poster
 * holds the space until the file is here, and the file is fetched on sight:
 * they are small, and a GIF that waits for a tap is a still picture.
 *
 * A tap opens it full screen, as a video.
 */
@Composable
internal fun AnimationMessage(
    gif: VideoContent,
    caption: String,
    outgoing: Boolean,
    bleedTop: Boolean,
    transfer: FileTransfer?,
    onVisible: () -> Unit,
    onOpen: () -> Unit
) {
    LaunchedEffect(gif.path, gif.thumbPath) {
        if (gif.path == null || gif.thumbPath == null) onVisible()
    }
    Column {
        Box(
            modifier = Modifier
                .bleed(horizontal = BUBBLE_PADDING_H, top = if (bleedTop) BUBBLE_PADDING_V else 0.dp)
                .fillMaxWidth()
                .aspectRatio(gif.aspect.coerceIn(0.6f, 1.9f))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable(onClick = onOpen)
                .semantics(mergeDescendants = true) {
                    contentDescription = if (caption.isBlank() || caption == "GIF") "GIF" else "GIF, $caption"
                },
            contentAlignment = Alignment.Center
        ) {
            gif.thumbPath?.let { poster ->
                AsyncImage(
                    model = poster,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            gif.path?.let { path ->
                InlineVideo(
                    path = path,
                    playing = true,
                    muted = true,
                    loop = true,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (gif.path == null && transfer == null) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            }
            transfer?.let {
                TransferOverlay(
                    transfer = it,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                )
            }
            // Said, as every client says it: a loop that plays by itself
            // could otherwise be taken for a video already running.
            Surface(
                shape = MaterialTheme.shapes.small,
                color = Color.Black.copy(alpha = 0.45f),
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            ) {
                Text(
                    "GIF",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        if (caption.isNotBlank() && caption != "GIF") {
            Spacer(Modifier.height(6.dp))
            Text(
                caption,
                color = if (outgoing) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * A round video message, standing on the conversation with no bubble round
 * it, as it does in every Telegram client. Its first frame rests in the
 * circle; a tap plays it in place, with sound, and a second tap pauses. It
 * goes back to the start when it ends.
 *
 * Fetched on sight, like a GIF: a video message is seconds long, and one that
 * had to download after the tap would answer the tap with a spinner.
 */
@Composable
internal fun VideoNoteMessage(
    note: VideoContent,
    transfer: FileTransfer?,
    onVisible: () -> Unit
) {
    LaunchedEffect(note.path, note.thumbPath) {
        if (note.path == null || note.thumbPath == null) onVisible()
    }
    // A tap while the file is still arriving is kept, not dropped: the
    // circle starts as soon as there is something to play.
    var playing by remember { mutableStateOf(false) }
    val duration = formatDuration(note.durationSeconds.toLong())
    val description = if (playing && note.path != null) "Video message, playing" else "Video message, $duration"
    Box(
        modifier = Modifier
            .size(VIDEO_NOTE_SIZE)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            // Read here, in composition, and handed over as a value: a state
            // read inside the semantics block does not bring it up to date,
            // so the circle played while TalkBack still heard its length.
            .semantics(mergeDescendants = true) { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        note.thumbPath?.let { poster ->
            AsyncImage(
                model = poster,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        note.path?.let { path ->
            InlineVideo(
                path = path,
                playing = playing,
                muted = false,
                loop = false,
                modifier = Modifier.fillMaxSize(),
                onEnded = { playing = false }
            )
        }
        when {
            note.path == null -> {
                val progress = transfer?.let(::transferProgress)
                if (progress != null) {
                    CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(40.dp))
                } else {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
            }
            // A Box, not a Surface: Material's Surface swallows touches so
            // nothing behind it gets them, and this one sits exactly where a
            // thumb aims — a tap on the play mark did nothing at all.
            !playing -> Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(52.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
        if (!playing && note.durationSeconds > 0) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = Color.Black.copy(alpha = 0.45f),
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp)
            ) {
                Text(
                    duration,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        // The tap catcher, over everything else in the circle. Once the
        // video is loaded its picture is an Android TextureView, and a real
        // View inside Compose takes the touches that land on it — so a tap
        // on a loaded circle did nothing, and only one made before the file
        // arrived ever started it. Drawn last, this sees the tap first.
        Box(
            Modifier
                .matchParentSize()
                .clickable { playing = !playing }
        )
    }
}

/** What the play button on a voice bubble is currently doing. */
enum class VoiceState { Idle, Loading, Playing }

/**
 * A voice message, with the one control it needs.
 *
 * Play and pause are the same button showing which of the two it is, because
 * the gesture is the same tap and a second button would have to be greyed out
 * half the time. A file still arriving gets a spinner in the button's place
 * rather than a play triangle that does nothing for two seconds.
 *
 * There is no waveform yet. Nothing captures amplitudes when recording and
 * TDLib's own waveform is not read here, so drawing one would be drawing a
 * shape that has nothing to do with the sound.
 */
@Composable
internal fun VoiceMessage(
    label: String,
    waveform: List<Int>,
    state: VoiceState,
    outgoing: Boolean,
    onToggle: () -> Unit,
    progress: Float,
    onSeek: (Float) -> Unit
) {
    val tint = if (outgoing) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.primary
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (state) {
            VoiceState.Loading -> CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = tint,
                modifier = Modifier
                    .size(24.dp)
                    .padding(2.dp)
            )
            else -> IconButton(onClick = onToggle, modifier = Modifier.size(24.dp)) {
                Icon(
                    if (state == VoiceState.Playing) Icons.Rounded.Pause
                    else Icons.Rounded.PlayArrow,
                    contentDescription = if (state == VoiceState.Playing) "Pause" else "Play",
                    tint = tint
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Waveform(
            bars = remember(waveform) { waveformBars(waveform, WAVEFORM_BARS) },
            color = tint,
            progress = progress,
            onSeek = onSeek,
            modifier = Modifier
                .width(120.dp)
                .height(28.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (outgoing) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

/** How many bars a voice bubble draws, whatever the recording's length. */
internal const val WAVEFORM_BARS = 28

/**
 * The shape of what was said.
 *
 * Drawn rather than composed, and this is the case CLAUDE.md keeps a place
 * for: Material has no component for a bar chart of amplitudes, and
 * twenty-eight Boxes with animated heights would be twenty-eight layout nodes
 * per bubble in a list that scrolls.
 *
 * A silent bar is still drawn, at a minimum height, because a row with gaps in
 * it reads as a broken picture rather than as a pause.
 */
@Composable
internal fun Waveform(
    bars: List<Float>,
    color: Color,
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.pointerInput(bars.size) {
            // Tapping the bars moves playback there, which is the only reason
            // to know where in the row the finger landed. A drag is left to
            // the list, so scrolling past a voice message still scrolls.
            detectTapGestures { offset ->
                onSeek((offset.x / size.width).coerceIn(0f, 1f))
            }
        }
    ) {
        if (bars.isEmpty()) return@Canvas
        val slot = size.width / bars.size
        // Roughly a third of each slot is the gap; the rest is the bar.
        val barWidth = slot * 0.6f
        val radius = barWidth / 2f
        // Measured in bars rather than pixels: a bar half-filled would be a
        // second way of showing the same thing, at a resolution nobody reads.
        val played = (bars.size * progress).toInt()
        bars.forEachIndexed { index, value ->
            val height = (size.height * value).coerceAtLeast(barWidth)
            val left = index * slot + (slot - barWidth) / 2f
            drawRoundRect(
                color = color,
                topLeft = Offset(left, (size.height - height) / 2f),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(radius, radius),
                // The part already heard is solid and the rest is faded,
                // rather than two colours: one of them would have to be picked
                // out of the scheme for a bubble that is already tinted, and
                // opacity says "behind you" in either palette.
                alpha = if (index < played) 1f else 0.4f
            )
        }
    }
}

/** A round video message's diameter: a little under the bubble's widest, as Telegram draws it. */
internal val VIDEO_NOTE_SIZE = 220.dp
