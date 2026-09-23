package com.telegramyou.app.ui.chat

import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.telegramyou.app.telegram.model.VideoContent
import androidx.compose.material3.LinearProgressIndicator
import com.telegramyou.app.ui.media.FileTransfer
import com.telegramyou.app.ui.media.transferLabel
import com.telegramyou.app.ui.media.transferProgress
import com.telegramyou.app.ui.media.DURATION_UNKNOWN
import com.telegramyou.app.ui.media.playbackLabel
import com.telegramyou.app.ui.media.playbackProgress
import com.telegramyou.app.ui.media.seekTarget
import kotlinx.coroutines.delay

/** How often the bar catches up with the player while it is running. */
private const val PROGRESS_TICK_MS = 250L

/**
 * A video, full screen, with Material's own controls over it.
 *
 * Media3 plays it and draws nothing: the surface is a plain `SurfaceView`,
 * and the play button, the scrubber and the time are this app's. Media3 ships
 * a player view of its own, with its own look — using it would put a second
 * design language on the screen, which is the thing this client exists not to
 * do.
 *
 * A `Dialog` rather than a route, like the photo viewer next to it: opening a
 * video is a look at something, not a place to come back from.
 */
@Composable
fun VideoPlayerScreen(
    video: VideoContent,
    title: String,
    /** The file's transfer, while it is still coming. */
    transfer: FileTransfer? = null,
    onClose: () -> Unit
) {
    val path = video.path
    val context = LocalContext.current

    var isPlaying by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(DURATION_UNKNOWN) }
    // Held while a finger is on the scrubber: the player's own position keeps
    // arriving during a drag, and letting it win would drag the thumb back
    // out from under the finger.
    var scrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }

    // One player for the life of this dialog, released with it. A player left
    // running holds a codec, and codecs are a fixed and small number.
    val player = remember(path) {
        path?.let { source ->
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(source))
                prepare()
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_ONE
            }
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        player?.addListener(listener)
        onDispose {
            player?.removeListener(listener)
            player?.release()
        }
    }

    // Polled rather than listened for: a player announces state changes, not
    // every position it passes through, and a bar that only moved on state
    // changes would sit still for the length of the video.
    LaunchedEffect(player, isPlaying) {
        while (player != null) {
            duration = player.duration
            if (!scrubbing) position = player.currentPosition
            delay(PROGRESS_TICK_MS)
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (player == null) {
                // The file is still arriving. The dialog opens anyway rather
                // than waiting: a tap that appears to do nothing for ten
                // seconds reads as a broken button — and now it says how far
                // along it is, which is the difference between waiting and
                // wondering.
                if (transfer == null) {
                    CircularProgressIndicator(color = Color.White)
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(horizontal = 48.dp)
                    ) {
                        Text(
                            transferLabel(transfer),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
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
            } else {
                // TextureView rather than SurfaceView, and the reason is
                // visible in every screenshot: a SurfaceView gets a window of
                // its own, which inside a Dialog is a second window over the
                // first — it renders where nothing can photograph it, and on
                // some devices behind the dialog entirely. A TextureView is
                // an ordinary view in the same hierarchy, so it composites
                // with everything above it and shows up in a capture.
                AndroidView(
                    factory = { ctx -> TextureView(ctx).also(player::setVideoTextureView) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(video.aspect.coerceIn(0.4f, 2.5f))
                )
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White)
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (title.isNotBlank()) {
                    Text(
                        title,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledIconButton(
                        onClick = {
                            player ?: return@FilledIconButton
                            if (player.isPlaying) player.pause() else player.play()
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play"
                        )
                    }
                    Slider(
                        value = if (scrubbing) {
                            scrubFraction
                        } else {
                            playbackProgress(position, duration) ?: 0f
                        },
                        onValueChange = { value ->
                            scrubbing = true
                            scrubFraction = value
                        },
                        onValueChangeFinished = {
                            val target = seekTarget(scrubFraction, duration)
                            player?.seekTo(target)
                            position = target
                            scrubbing = false
                        },
                        enabled = player != null && duration > 0,
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .weight(1f)
                    )
                    Text(
                        playbackLabel(position, duration, ::formatDuration),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}
