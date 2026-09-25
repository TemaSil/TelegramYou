package com.telegramyou.app.ui.chat

import android.graphics.Matrix
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer

/**
 * A video playing inside a message — a GIF on its loop, a round video
 * message when tapped. Media3's player on a TextureView, as the full-screen
 * player and the story viewer use: a TextureView takes the clip of the shape
 * it sits in, which a SurfaceView would punch straight through.
 *
 * Cropped to fill rather than stretched: a TextureView scales the frame to
 * its own size whatever the video's shape, so the frame is scaled back by the
 * difference and centred, the way ContentScale.Crop treats a picture.
 *
 * One player per message on screen. The list disposes a row that scrolls
 * away, and the player with it; and it stops while the app is in the
 * background, where playing would only spend battery on a screen nobody sees.
 */
@Composable
fun InlineVideo(
    path: String,
    playing: Boolean,
    muted: Boolean,
    loop: Boolean,
    modifier: Modifier = Modifier,
    onEnded: () -> Unit = {}
) {
    val context = LocalContext.current
    val ended = rememberUpdatedState(onEnded)
    val player = remember(path) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(path))
            repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            volume = if (muted) 0f else 1f
            prepare()
        }
    }
    val holder = remember(path) { TextureHolder() }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                holder.video = videoSize
                holder.fit()
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    // Back to the first frame, so the circle rests on the
                    // start of the message rather than on its last frame.
                    player.seekTo(0)
                    player.playWhenReady = false
                    ended.value()
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LifecycleResumeEffect(player, playing) {
        player.playWhenReady = playing
        onPauseOrDispose { player.playWhenReady = false }
    }
    AndroidView(
        factory = { ctx ->
            TextureView(ctx).also { view ->
                holder.view = view
                view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> holder.fit() }
                player.setVideoTextureView(view)
            }
        },
        modifier = modifier
    )
}

/** The view and the video's size, which cropping needs both of. */
private class TextureHolder {
    var view: TextureView? = null
    var video: VideoSize? = null

    fun fit() {
        val view = view ?: return
        val video = video ?: return
        val width = view.width.toFloat()
        val height = view.height.toFloat()
        if (width == 0f || height == 0f || video.width == 0 || video.height == 0) return
        val videoAspect = video.width * video.pixelWidthHeightRatio / video.height
        val viewAspect = width / height
        val matrix = Matrix()
        if (videoAspect > viewAspect) {
            matrix.setScale(videoAspect / viewAspect, 1f, width / 2, height / 2)
        } else {
            matrix.setScale(1f, viewAspect / videoAspect, width / 2, height / 2)
        }
        view.setTransform(matrix)
    }
}
