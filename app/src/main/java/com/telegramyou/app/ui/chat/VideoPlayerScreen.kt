package com.telegramyou.app.ui.chat

import androidx.compose.ui.layout.ContentScale
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.LoadingIndicator
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.telegramyou.app.TelegramYouApp
import com.telegramyou.app.media.TelegramFileDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import coil3.compose.AsyncImage
import com.telegramyou.app.ui.icons.Symbols
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
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import com.telegramyou.app.ui.media.PictureInPicture
import com.telegramyou.app.ui.media.playbackSpeedLabel
import com.telegramyou.app.ui.media.nextPlaybackSpeed
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
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
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        VideoPage(video = video, title = title, transfer = transfer, active = true, onClose = onClose)
    }
}

/**
 * The player's page: on its own in [VideoPlayerScreen], one of many in
 * MediaGallery. [active] is whether it is the page in view — the player
 * exists only then, and a page beside it shows the video's poster.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPage(
    video: VideoContent,
    title: String,
    transfer: FileTransfer? = null,
    active: Boolean,
    onClose: () -> Unit,
    /** Off in the gallery, which draws its own Close over every page. */
    showClose: Boolean = true,
    /**
     * Whether the controls are up, and how a tap on the picture flips it
     * (2.0): the gallery holds it, so its own Close and counter go with
     * them. Null on its own, where the page holds it itself.
     */
    chrome: Boolean? = null,
    onToggleChrome: (() -> Unit)? = null
) {
    var ownChrome by remember { mutableStateOf(true) }
    val chromeShown = chrome ?: ownChrome
    val toggleChrome by rememberUpdatedState(onToggleChrome ?: { ownChrome = !ownChrome })
    // The caption, two lines until it is tapped open — as the official
    // client does it; open, it scrolls, and the picture dims under it.
    var captionOpen by remember(title) { mutableStateOf(false) }
    var captionLong by remember(title) { mutableStateOf(false) }
    ImmersiveWhile(active && !chromeShown)
    val context = LocalContext.current
    val files = (context.applicationContext as TelegramYouApp).telegramRepository
    // On the phone, played from there; otherwise played as it downloads
    // (1.9), as the official client does, rather than after — a video used
    // to wait for its last byte before its first frame.
    val streamedId = video.fileId?.takeIf { video.path == null && files.canStream }
    val path = video.path ?: streamedId?.let { TelegramFileDataSource.uriOf(it).toString() }

    var isPlaying by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(DURATION_UNKNOWN) }
    // Held while a finger is on the scrubber: the player's own position keeps
    // arriving during a drag, and letting it win would drag the thumb back
    // out from under the finger.
    var scrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    // One speed for every video, kept while the app runs: a person who
    // watches at 1.5× wants the next one at 1.5× too.
    var speed by remember { mutableFloatStateOf(lastPlaybackSpeed) }
    // Waiting for bytes: while it streams, the player says so.
    var buffering by remember { mutableStateOf(false) }

    // One player for the life of this dialog, released with it. A player left
    // running holds a codec, and codecs are a fixed and small number.
    // Only on the page in view: a gallery composes its neighbours too, and a
    // player each would hold codecs for videos nobody is watching.
    val player = remember(path, active) {
        path?.takeIf { active }?.let { source ->
            ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(TelegramFileDataSource.Factory(context, files)))
                .build().apply {
                setMediaItem(MediaItem.fromUri(source))
                prepare()
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_ONE
            }
        }
    }

    LaunchedEffect(player, speed) { player?.setPlaybackSpeed(speed) }

    // Music pauses for a video watched full screen, and plays on after it is
    // closed; see AudioFocus.
    DisposableEffect(player) {
        val focus = player?.let { com.telegramyou.app.music.AudioFocus.pause() }
        onDispose { focus?.release() }
    }

    // Picture-in-picture: wanted while this page's video is playing, so
    // leaving the app keeps it going in a small window; in that window the
    // controls have no room and are left out.
    val inPip by PictureInPicture.active
    val pipAspect = remember(video.aspect) { PictureInPicture.aspectOf(video.aspect) }
    LaunchedEffect(player, isPlaying) {
        PictureInPicture.wanted.value = if (player != null && isPlaying) pipAspect else null
    }
    DisposableEffect(Unit) { onDispose { PictureInPicture.wanted.value = null } }
    // Sent away for good — the PiP window closed, or the app left without
    // one — it stops, rather than playing on where nobody can see it.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (!PictureInPicture.active.value) player?.pause()
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
            }
        }
        player?.addListener(listener)
        onDispose {
            player?.removeListener(listener)
            player?.release()
            // Closed before the end of it: the rest is not fetched for
            // nobody. What came stays, and plays from the phone next time.
            streamedId?.let { id -> streamScope.launch { files.stopStreaming(id) } }
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // One tap on the picture: every control away, and the system's
            // bars with them, so the video has the whole screen; another,
            // and they are back. An open caption closes first.
            .pointerInput(active) {
                detectTapGestures(onTap = {
                    if (captionOpen) captionOpen = false else toggleChrome()
                })
            },
        contentAlignment = Alignment.Center
    ) {
        if (player == null && !active) {
            // A neighbour in the gallery: its poster, until it is swiped to.
            video.thumbPath?.let { poster ->
                AsyncImage(
                    model = poster,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else if (player == null) {
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
            // The poster under the picture until the first frame covers it:
            // a TextureView with nothing in it yet draws nothing, and the
            // gallery opening out of the bubble would otherwise grow a black
            // rectangle where the bubble showed a picture (1.8).
            video.thumbPath?.let { poster ->
                AsyncImage(
                    model = poster,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(video.aspect.coerceIn(0.4f, 2.5f))
                )
            }
            AndroidView(
                factory = { ctx -> TextureView(ctx).also(player::setVideoTextureView) },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(video.aspect.coerceIn(0.4f, 2.5f))
            )
            if (buffering) LoadingIndicator()
        }

        // Read over a dimmed picture while the caption is open.
        AnimatedVisibility(
            visible = captionOpen && chromeShown && !inPip,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            modifier = Modifier.matchParentSize()
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = CAPTION_SCRIM)))
        }

        // In the PiP window only the picture: there is no room for controls.
        AnimatedVisibility(
            visible = !inPip && chromeShown,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
            modifier = Modifier.matchParentSize()
        ) {
          Box(Modifier.fillMaxSize()) {
            if (showClose) {
                FilledTonalIconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(12.dp)
                ) {
                    Icon(Symbols.Close, contentDescription = "Close")
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(12.dp)
            ) {
                // The speed, a tap a step: 1×, 1.5×, 2×, 0.5×. What it says
                // is the state, and there are only four of them — a tonal
                // button, as the close beside it.
                FilledTonalButton(
                    onClick = {
                        speed = nextPlaybackSpeed(speed)
                        lastPlaybackSpeed = speed
                    },
                    modifier = Modifier.semantics {
                        contentDescription = "Playback speed ${playbackSpeedLabel(speed)}"
                    }
                ) {
                    Text(playbackSpeedLabel(speed), style = MaterialTheme.typography.labelLarge)
                }
                // Into the small window now, rather than on leaving.
                if (player != null) {
                    FilledTonalIconButton(onClick = { PictureInPicture.enter(context, pipAspect) }) {
                        Icon(Symbols.PictureInPictureAlt, contentDescription = "Picture in picture")
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (title.isNotBlank()) {
                    Text(
                        title,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = if (captionOpen) Int.MAX_VALUE else CAPTION_LINES,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { layout -> if (!captionOpen) captionLong = layout.hasVisualOverflow },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = CAPTION_MAX)
                            .verticalScroll(rememberScrollState())
                            .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())
                            .clickable(
                                enabled = captionLong || captionOpen,
                                onClickLabel = if (captionOpen) "Show less" else "Show more"
                            ) { captionOpen = !captionOpen }
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                            .padding(end = 12.dp)
                            .weight(1f)
                    )
                    Text(
                        playbackLabel(position, duration, ::formatDuration),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                    // At the end of the row, where the thumb rests, on the
                    // owner's word; the scrubber takes the width before it.
                    FilledIconButton(
                        onClick = {
                            player ?: return@FilledIconButton
                            if (player.isPlaying) player.pause() else player.play()
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .size(48.dp)
                    ) {
                        Icon(
                            if (isPlaying) Symbols.PauseFilled else Symbols.PlayArrowFilled,
                            contentDescription = if (isPlaying) "Pause" else "Play"
                        )
                    }
                }
            }
          }
        }
    }
}

/**
 * The system's bars hidden while [hidden], swiped back in for a moment on
 * the edge as every video player does it, and shown again when it is not
 * or when the page goes. The page is in a dialog — a window of its own —
 * so it is that window's bars that are hidden.
 */
@Composable
private fun ImmersiveWhile(hidden: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, hidden) {
        val window = (view.parent as? DialogWindowProvider)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (hidden) {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

/** A caption's lines before it is tapped open. */
private const val CAPTION_LINES = 2

/** How far an open caption reaches up the screen before it scrolls instead. */
private val CAPTION_MAX = 320.dp

/** The picture under an open caption, dimmed so it can be read. */
private const val CAPTION_SCRIM = 0.55f

/** Where a stream is stopped from, after the page that played it has gone. */
private val streamScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** The speed the last video was left at, for the next one; see VideoPage. */
private var lastPlaybackSpeed = 1f
