package com.telegramyou.app.ui.stories

import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import coil3.compose.AsyncImage
import com.telegramyou.app.telegram.model.StoryFrame
import com.telegramyou.app.telegram.model.StoryItem
import com.telegramyou.app.telegram.model.storyFrameMillis
import com.telegramyou.app.ui.components.AvatarBubble
import com.telegramyou.app.ui.format.storyAgeLabel
import com.telegramyou.app.ui.theme.avatarColor
import java.io.File

/**
 * A circle's stories, one after another, each for its own time.
 *
 * Tapping the left third goes back, anywhere else on; the last one ends the
 * viewing. Each story's clock starts only once its picture or video is here
 * — a timer running behind a spinner would move on from a story nobody saw.
 *
 * A story that has run its time, or been tapped past, is marked seen through
 * [onSeen]; one tapped back from is not, since it was left rather than seen.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StoryViewerScreen(
    story: StoryItem,
    state: StoryUiState,
    onSeen: () -> Unit,
    onNext: () -> Boolean,
    onPrevious: () -> Unit,
    onClose: () -> Unit
) {
    val frame = state.frame ?: return
    val path = state.paths[frame.id]?.takeIf { it.isNotBlank() } ?: frame.localPath
    var progress by remember { mutableFloatStateOf(0f) }

    fun forward() {
        onSeen()
        if (!onNext()) onClose()
    }

    // A clock, not an animation. An animation is scaled by the system's
    // animation setting, and with animations off — an accessibility choice,
    // and how CI's emulator runs — a five-second story lasted no time at all
    // and the viewer closed as it opened. Frames are counted in real time.
    LaunchedEffect(state.index, state.isFrameReady) {
        progress = 0f
        if (!state.isFrameReady) return@LaunchedEffect
        val total = storyFrameMillis(frame)
        val start = withFrameMillis { it }
        while (progress < 1f) {
            val now = withFrameMillis { it }
            progress = ((now - start).toFloat() / total).coerceAtMost(1f)
        }
        forward()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // indication = null in effect: the whole screen is the tap target,
            // and a ripple across a story is not feedback, it is a stain.
            .pointerInput(state.index) {
                detectTapGestures { offset ->
                    if (offset.x < size.width / 3f) onPrevious() else forward()
                }
            }
    ) {
        when {
            !state.isFrameReady -> LoadingIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
            path == null -> TextStory(story, frame)
            frame.isVideo -> StoryVideo(path, Modifier.fillMaxSize())
            else -> AsyncImage(
                model = File(path),
                contentDescription = frame.caption.ifBlank { "Story" },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // A scrim at the top and the bottom only, so white text stays legible
        // over a white photo without dimming the story itself.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)))
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // One segment per story: the ones before are full, the one on
            // screen fills, the ones after are empty.
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                state.frames.indices.forEach { index ->
                    LinearProgressIndicator(
                        progress = {
                            when {
                                index < state.index -> 1f
                                index == state.index -> progress
                                else -> 0f
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(50)),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.3f),
                        drawStopIndicator = {},
                        gapSize = 0.dp
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarBubble(
                    title = story.authorName,
                    seed = story.avatarColor,
                    size = 36.dp,
                    photoPath = story.photoPath
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(story.authorName, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                    val age = storyAgeLabel(frame.date, System.currentTimeMillis() / 1000)
                    if (age.isNotEmpty()) {
                        Text(
                            age,
                            color = Color.White.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }

        if (path != null && frame.caption.isNotBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                Text(
                    frame.caption,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** A story with nothing to show but words — the demo's, or a kind not played here. */
@Composable
private fun TextStory(story: StoryItem, frame: StoryFrame) {
    val base = avatarColor(story.avatarColor)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(base.copy(alpha = 0.95f), Color(0xFF0B1F1A), base.copy(alpha = 0.55f))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(story.previewEmoji, fontSize = 96.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                frame.caption.ifBlank { story.caption }.ifBlank { "Story" },
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}

/**
 * A video story, playing once with no controls — the viewer's own progress
 * is its scrubber. A TextureView for the reason VideoPlayerScreen gives: it
 * composites with what is drawn over it.
 */
@Composable
private fun StoryVideo(path: String, modifier: Modifier) {
    val context = LocalContext.current
    val player = remember(path) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(path))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = { ctx -> TextureView(ctx).also(player::setVideoTextureView) },
        modifier = modifier
    )
}
