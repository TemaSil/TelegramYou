package com.telegramyou.app.ui.music

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.MutableFloatState
import android.content.Intent
import android.media.audiofx.AudioEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import com.telegramyou.app.telegram.model.SleepTimer
import java.io.File
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Shape
import com.telegramyou.app.ui.motion.LocalReduceMotion
import androidx.compose.foundation.basicMarquee
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.telegramyou.app.music.NowPlaying
import com.telegramyou.app.telegram.model.QueueOrder
import com.telegramyou.app.telegram.model.RepeatMode
import com.telegramyou.app.telegram.model.Track
import com.telegramyou.app.ui.chat.formatDuration
import com.telegramyou.app.ui.icons.Symbols
import com.telegramyou.app.ui.theme.schemeFromSeed
import com.telegramyou.app.ui.theme.toColorScheme
import kotlinx.coroutines.launch

/** What the player's buttons do, gathered so the screens take one thing. */
class MusicActions(
    val onToggle: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onPrevious: () -> Unit = {},
    val onSeek: (Float) -> Unit = {},
    val onOrder: (QueueOrder) -> Unit = {},
    val onRepeat: () -> Unit = {},
    val onSpeed: () -> Unit = {},
    val onPlayAt: (Int) -> Unit = {},
    val onLoadMore: () -> Unit = {},
    val onStop: () -> Unit = {},
    val onSleep: (SleepTimer) -> Unit = {},
    val onSave: () -> Unit = {},
    /** An Up next track taken out, by its place there. */
    val onRemoveUpNext: (Int) -> Unit = {},
    /** A reaction onto the playing track's own message, where its sender sees it. */
    val onReact: (String) -> Unit = {},
    /** The chat the playing track is in. */
    val onOpenChat: (Long) -> Unit = {},
    /** Saved Messages' music as the queue; see MusicPlayer.playSaved. */
    val onPlaySaved: (Long) -> Unit = {},
    val onDownloadAll: () -> Unit = {},
    val onNoticeShown: () -> Unit = {},
    /** The player's audio session, for the platform's equaliser panel. */
    val audioSession: () -> Int = { 0 },
    /** The beat being heard now, 0 to 1, for the cover; see AudioPulse. */
    val pulse: () -> Float = { 0f },
    /** Whether anyone is looking at the cover move: the analysis runs only then. */
    val onPulseWatched: (Boolean) -> Unit = {}
)

/**
 * A track's own colours: Material You from a seed, the way the app's accent
 * is built — from the cover when there is one (step 3), from the track's
 * name until then, so each track has a colour and it is always the same one.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TrackTheme(track: Track?, coverSeed: Int? = null, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val seed = remember(track?.messageId, coverSeed) {
        coverSeed ?: run {
            val name = (track?.performer.orEmpty() + track?.title.orEmpty()).hashCode()
            // A hue from the name, at the saturation and lightness a seed needs.
            android.graphics.Color.HSVToColor(floatArrayOf(((name ushr 1) % 360).toFloat(), 0.55f, 0.75f))
        }
    }
    val scheme = remember(seed, dark) { schemeFromSeed(seed, dark).toColorScheme(dark) }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MaterialTheme.motionScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content
    )
}

/** The mini player's corners. */
private val PlayerContainerShape: Shape = RoundedCornerShape(20.dp)

/**
 * The mini player: a strip under a screen's top bar while something plays,
 * as the official client has one — the track, play and pause, the next one,
 * and how far through it is as a thin line along its bottom edge. Tapping it
 * opens the full player. It goes when the music is stopped.
 *
 * The line was Expressive's wavy indicator on a row of its own, which made
 * the strip a third taller over every chat for something a glance needs
 * only the length of; the wave stays in the full player, where the seek bar
 * is the point.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MiniPlayer(state: NowPlaying, actions: MusicActions, onOpen: () -> Unit) {
    val track = state.track
    AnimatedVisibility(
        visible = track != null,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        if (track == null) return@AnimatedVisibility
        // No swipe: one put the strip away in 1.6.8, and on a phone it
        // missed more than it landed. The cross is there for that.
        TrackTheme(track, state.coverSeed) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = PlayerContainerShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(PlayerContainerShape)
                    .clickable(onClick = onOpen)
                    .semantics { contentDescription = "Now playing: ${track.title}" }
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
                    ) {
                        Cover(track, size = 40, corner = 12)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                        ) {
                            Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                track.performer.ifBlank { state.queue.sourceTitle },
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        PlayPauseButton(state, actions.onToggle, size = 40)
                        IconButton(onClick = actions.onNext) { Icon(Symbols.SkipNext, contentDescription = "Next track") }
                        IconButton(onClick = actions.onStop) { Icon(Symbols.Close, contentDescription = "Stop music") }
                    }
                    // Flush with the bottom edge, whose rounding trims its ends.
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.12f),
                        strokeCap = StrokeCap.Butt,
                        gapSize = 0.dp,
                        drawStopIndicator = {}
                    )
                }
            }
        }
    }
}

/** The cover, or — without one yet — the note on the track's own colour. */
@Composable
internal fun Cover(track: Track, size: Int, corner: Int) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(corner.dp))
            .background(Brush.linearGradient(listOf(colors.primary, colors.tertiary))),
        contentAlignment = Alignment.Center
    ) {
        if (track.coverPath != null) {
            AsyncImage(model = File(track.coverPath), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(
                Symbols.MusicNote,
                contentDescription = null,
                tint = colors.onPrimary,
                modifier = Modifier.size((size * 0.45f).dp)
            )
        }
    }
}

/**
 * Play and pause as one button whose shape answers the state, Expressive's
 * way of saying what is happening: a circle while stopped, a rounded square
 * while playing, the corners moving between them on the motion scheme.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlayPauseButton(
    state: NowPlaying,
    onToggle: () -> Unit,
    size: Int,
    modifier: Modifier = Modifier.size(size.dp),
    interactionSource: MutableInteractionSource? = null
) {
    val corner by animateDpAsState(
        targetValue = if (state.isPlaying) (size / 3.2f).dp else (size / 2f).dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "playShape"
    )
    FilledIconButton(
        onClick = onToggle,
        shape = RoundedCornerShape(corner),
        interactionSource = interactionSource,
        modifier = modifier
    ) {
        when {
            state.isLoading -> LoadingIndicator(Modifier.size((size * 0.6f).dp), color = MaterialTheme.colorScheme.onPrimary)
            state.isPlaying -> Icon(Symbols.PauseFilled, contentDescription = "Pause", modifier = Modifier.size((size * 0.45f).dp))
            else -> Icon(Symbols.PlayArrowFilled, contentDescription = "Play", modifier = Modifier.size((size * 0.45f).dp))
        }
    }
}

/**
 * The full player as Material's modal bottom sheet, opened out from the
 * mini player straight to full height — the owner's call for 1.6.8, after
 * a screen of its own (1.6.6) and a slide from the foot (1.6.7) both read
 * as "some other screen". The sheet brings its own opening and closing:
 * the drag handle, a pull down from anywhere on it, Back, and the scrim.
 * In the track's own colours, as the screen was.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSheet(
    state: NowPlaying,
    actions: MusicActions,
    onDismiss: () -> Unit,
    onOpenChat: (Long) -> Unit,
    coverMoves: Boolean
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // How far the player has been pulled down, and the screen it is pulled
    // across. Held here, beside the scrim it dims, rather than in the
    // player.
    val pulled = remember { mutableFloatStateOf(0f) }
    var height by remember { mutableIntStateOf(0) }
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    // Closed by sliding on down from wherever it is — with the finger's own
    // speed when a pull let go of it — and only then taken off screen. It
    // was handed to the sheet's hide(), which set off from a standstill
    // over a distance the pull had already half covered: the player sped
    // out past the edge while the dimming lingered, and the owner saw it
    // end abruptly (1.7).
    val slideAway: (Float) -> Unit = { velocity ->
        scope.launch {
            animate(
                initialValue = pulled.floatValue,
                targetValue = height.toFloat().coerceAtLeast(pulled.floatValue),
                initialVelocity = velocity,
                animationSpec = spatial
            ) { value, _ -> pulled.floatValue = value }
            onDismiss()
        }
    }
    TrackTheme(state.track, state.coverSeed) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheet,
            // The player draws the sheet itself — its colour, its corners,
            // its handle — and moves it under a pull (PlayerScreen), so the
            // sheet's own drag is off and it reserves no insets: the
            // player's surface reaches the top of the screen and pads its
            // own content.
            sheetGesturesEnabled = false,
            containerColor = Color.Transparent,
            // The dimming too, drawn below, so it fades with the pull.
            scrimColor = Color.Transparent,
            dragHandle = null,
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
        ) {
            Box(Modifier.fillMaxSize().onSizeChanged { height = it.height }) {
                val scrim = BottomSheetDefaults.ScrimColor
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // Held still on screen while the sheet slides in
                            // or out under it, and lighter the further the
                            // player is down.
                            val offset = runCatching { sheet.requireOffset() }.getOrDefault(0f)
                            translationY = -offset
                            alpha = (1f - (offset + pulled.floatValue) / size.height.coerceAtLeast(1f)).coerceIn(0f, 1f)
                        }
                        .background(scrim)
                )
                PlayerScreen(
                    state,
                    actions,
                    onBack = { slideAway(0f) },
                    onOpenChat = { chatId ->
                        slideAway(0f)
                        onOpenChat(chatId)
                    },
                    coverMoves = coverMoves,
                    pullState = pulled,
                    onPulledAway = slideAway
                )
            }
        }
    }
}

/**
 * The full player: the cover large, the track, a wavy progress that is also
 * where the finger seeks, and the controls — previous, a play button that
 * changes shape as it plays, next — with the order, repeat and speed under
 * them. The track's own colours throughout. The queue is a sheet over it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayerScreen(
    state: NowPlaying,
    actions: MusicActions,
    onBack: () -> Unit,
    onOpenChat: (Long) -> Unit = actions.onOpenChat,
    /** The cover's edges moving on the beat; Appearance → Motion. */
    coverMoves: Boolean = true,
    /** How far it is pulled down; PlayerSheet holds it, for the scrim. */
    pullState: MutableFloatState = remember { mutableFloatStateOf(0f) },
    /** Let go far enough down, or flicked: slides away at this speed. */
    onPulledAway: (Float) -> Unit = { onBack() }
) {
    val track = state.track
    var queueOpen by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var sleepOpen by remember { mutableStateOf(false) }
    val host = remember { SnackbarHostState() }
    val context = LocalContext.current
    val equaliser = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    state.notice?.let { message ->
        val savedTo = state.savedTo
        LaunchedEffect(message) {
            // After a save, the library is a tap away: play from it.
            val result = host.showSnackbar(
                message,
                actionLabel = savedTo?.let { "Play saved" },
                // With an action a snackbar otherwise stays until dismissed.
                duration = SnackbarDuration.Long
            )
            actions.onNoticeShown()
            if (result == SnackbarResult.ActionPerformed && savedTo != null) actions.onPlaySaved(savedTo)
        }
    }
    // Inside a ModalBottomSheet (PlayerSheet), which draws the surface,
    // takes the status and navigation bars' insets, and is what a pull down
    // or Back closes — the gesture this screen used to do by hand.
    // The sheet's own drag is off (see PlayerSheet): this surface is the
    // sheet as it looks and as it moves. Pulled down, it follows the finger;
    // let go far enough down, or flicked, it closes — the sheet then hides
    // with its own animation — and short of that it springs back. The
    // sheet's drag, fed through nested scroll from content that scrolls
    // nothing, did not move for a pull on the emulator, twice; this one is
    // the pull 1.6.6 shipped and tested.
    var pulled by pullState
    val closeAfter = with(LocalDensity.current) { CLOSE_AFTER_DP.dp.toPx() }
    val pull = rememberDraggableState { delta -> pulled = (pulled + delta).coerceAtLeast(0f) }
    TrackTheme(track, state.coverSeed) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = BottomSheetDefaults.ExpandedShape,
            modifier = Modifier
                .fillMaxSize()
                // Outside the layer it moves, so the finger is read in a
                // frame that stays put.
                .draggable(
                    state = pull,
                    orientation = Orientation.Vertical,
                    onDragStopped = { velocity ->
                        if (pulled > closeAfter || velocity > CLOSE_FLING) {
                            onPulledAway(velocity)
                        } else {
                            animate(pulled, 0f) { value, _ -> pulled = value }
                        }
                    }
                )
                .graphicsLayer { translationY = pulled }
        ) {
          Box(Modifier.fillMaxSize()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
            ) {
                BottomSheetDefaults.DragHandle()
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    // No arrow down: the handle and a pull close it, and
                    // so does Back (the owner's ask, 1.7). The space it
                    // took is kept, so the title stays centred against the
                    // menu on the other side.
                    Spacer(Modifier.size(48.dp))
                    Text(
                        state.queue.sourceTitle.ifBlank { "Music" },
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Symbols.MoreVert, contentDescription = "More") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            // A reaction straight onto the message the track
                            // came in: whoever sent it sees it, without the
                            // chat being opened.
                            if (track != null && track.chatId > 0) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp)
                                ) {
                                    PLAYER_REACTIONS.forEach { emoji ->
                                        TextButton(onClick = {
                                            menuOpen = false
                                            actions.onReact(emoji)
                                        }) { Text(emoji, style = MaterialTheme.typography.titleLarge) }
                                    }
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Open chat") },
                                    leadingIcon = { Icon(Symbols.Chat, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        onOpenChat(track.chatId)
                                    }
                                )
                            }
                            // Out of the app, through Android's share sheet:
                            // the file when it is on the phone, otherwise who
                            // and what it is (1.7). Everyone has the player,
                            // so everyone has this — not only the library.
                            if (track != null) DropdownMenuItem(
                                text = { Text("Share") },
                                leadingIcon = { Icon(Symbols.Share, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    val extension = track.path?.substringAfterLast('.', "")?.takeIf { it.length in 1..5 }
                                    val mime = extension?.let {
                                        android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(it.lowercase())
                                    } ?: "audio/*"
                                    val label = listOf(track.performer, track.title).filter { it.isNotBlank() }.joinToString(" — ")
                                    com.telegramyou.app.ui.chat.MediaActions.share(
                                        context = context,
                                        path = track.path,
                                        mime = mime,
                                        name = label.ifBlank { "track" } + (extension?.let { ".$it" } ?: ""),
                                        text = label
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Save to Saved Messages") },
                                leadingIcon = { Icon(Symbols.Bookmark, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    actions.onSave()
                                }
                            )
                            if (state.queue.chatId > 0) {
                                DropdownMenuItem(
                                    text = { Text("Download all for offline") },
                                    leadingIcon = { Icon(Symbols.Download, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        actions.onDownloadAll()
                                    }
                                )
                            }
                            // The platform's equaliser panel, or whichever app
                            // provides one; offered only where there is one.
                            val panel = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                                .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, actions.audioSession())
                                .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                                .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                            if (panel.resolveActivity(context.packageManager) != null) {
                                DropdownMenuItem(
                                    text = { Text("Equaliser") },
                                    leadingIcon = { Icon(Symbols.Equalizer, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        equaliser.launch(panel)
                                    }
                                )
                            }
                        }
                    }
                }
                if (track == null) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text("Nothing is playing", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    return@Column
                }
                Spacer(Modifier.weight(0.4f))
                // The cover breathes with the music: a touch larger while it
                // plays, back when it stops, on the motion scheme's spring.
                // As a scale, not as padding: an expressive spring overshoots
                // its target, and padding springing to 0 went briefly below
                // it — "Padding must be non-negative", which closed the app
                // whenever a track started or paused (1.6.3 and 1.6.4). CI
                // runs with animations off and never saw it. A scale past 1
                // is just the bounce.
                val scale by animateFloatAsState(
                    targetValue = if (state.isPlaying) 1f else PAUSED_COVER_SCALE,
                    animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
                    label = "coverScale"
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp)
                        .aspectRatio(1f)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            // Square-cornered enough to read as a cover
                            // rather than a button: Material's large shape.
                            .pulsingCover(
                                corner = 16.dp,
                                playing = state.isPlaying,
                                enabled = coverMoves,
                                beat = actions.pulse,
                                onWatched = actions.onPulseWatched
                            )
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (track.coverPath != null) {
                            AsyncImage(
                                model = File(track.coverPath),
                                contentDescription = "Cover of ${track.title}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                Symbols.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(96.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(0.4f))
                // One line each, running across when too long for it: a title
                // on two lines pushed the controls down for that track only,
                // and they jumped from one track to the next. Held still and
                // cut short with Less motion.
                val still = LocalReduceMotion.current
                val running = if (still) Modifier else Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                Text(
                    track.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = if (still) TextOverflow.Ellipsis else TextOverflow.Clip,
                    textAlign = TextAlign.Center,
                    modifier = running
                )
                Text(
                    // "sent by" only when it says something: a channel's own
                    // name under its own performer's was the same word twice.
                    listOf(
                        track.performer,
                        track.senderName.takeIf { it.isNotBlank() && !it.equals(track.performer, ignoreCase = true) }
                            ?.let { "sent by $it" }
                    )
                        .filter { !it.isNullOrBlank() }
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = if (still) TextOverflow.Ellipsis else TextOverflow.Clip,
                    modifier = running
                )
                Spacer(Modifier.height(20.dp))
                Seeker(state, actions.onSeek)
                Spacer(Modifier.height(16.dp))
                // Material's own advice for a player's controls: the one
                // that matters breaks from the shapes around it. Previous and
                // next are squared-off; play is wider than both, full height,
                // and changes shape as it plays. An Expressive ButtonGroup
                // (1.6.8): the button under the finger widens and the others
                // give way. Every item weighted — see the profile's group
                // and ROADMAP, "ButtonGroup", for why that is not optional.
                ButtonGroup(
                    overflowIndicator = { menu -> ButtonGroupDefaults.OverflowIndicator(menuState = menu) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    customItem(
                        buttonGroupContent = {
                            val source = remember { MutableInteractionSource() }
                            FilledTonalIconButton(
                                onClick = actions.onPrevious,
                                shape = RoundedCornerShape(28.dp),
                                interactionSource = source,
                                modifier = Modifier
                                    .weight(1f)
                                    .animateWidth(source)
                                    .height(88.dp)
                            ) {
                                Icon(Symbols.SkipPrevious, contentDescription = "Previous track", modifier = Modifier.size(32.dp))
                            }
                        },
                        menuContent = { DropdownMenuItem(text = { Text("Previous track") }, onClick = actions.onPrevious) }
                    )
                    customItem(
                        buttonGroupContent = {
                            val source = remember { MutableInteractionSource() }
                            PlayPauseButton(
                                state,
                                actions.onToggle,
                                size = 88,
                                interactionSource = source,
                                modifier = Modifier
                                    .weight(1.6f)
                                    .animateWidth(source)
                                    .height(88.dp)
                            )
                        },
                        menuContent = {
                            DropdownMenuItem(text = { Text(if (state.isPlaying) "Pause" else "Play") }, onClick = actions.onToggle)
                        }
                    )
                    customItem(
                        buttonGroupContent = {
                            val source = remember { MutableInteractionSource() }
                            FilledTonalIconButton(
                                onClick = actions.onNext,
                                shape = RoundedCornerShape(28.dp),
                                interactionSource = source,
                                modifier = Modifier
                                    .weight(1f)
                                    .animateWidth(source)
                                    .height(88.dp)
                            ) {
                                Icon(Symbols.SkipNext, contentDescription = "Next track", modifier = Modifier.size(32.dp))
                            }
                        },
                        menuContent = { DropdownMenuItem(text = { Text("Next track") }, onClick = actions.onNext) }
                    )
                }
                Spacer(Modifier.height(12.dp))
                // How it plays, as pills in the surface's own tone: second to
                // the row above, and wide enough to hit without looking. A
                // ButtonGroup as the row above is, weighted the same way, so
                // a press widens here too.
                ButtonGroup(
                    overflowIndicator = { menu -> ButtonGroupDefaults.OverflowIndicator(menuState = menu) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    customItem(
                        buttonGroupContent = {
                            val source = remember { MutableInteractionSource() }
                            OrderButton(
                                state.queue.order,
                                actions.onOrder,
                                interactionSource = source,
                                modifier = Modifier
                                    .weight(ORDER_WEIGHT)
                                    .animateWidth(source)
                                    .height(56.dp)
                            )
                        },
                        menuContent = {
                            DropdownMenuItem(text = { Text("Order: ${state.queue.order.label}") }, onClick = {})
                        }
                    )
                    customItem(
                        buttonGroupContent = {
                            val source = remember { MutableInteractionSource() }
                            FilledTonalButton(
                                onClick = actions.onRepeat,
                                colors = pillColors(state.queue.repeat != RepeatMode.Off),
                                interactionSource = source,
                                modifier = Modifier
                                    .weight(1f)
                                    .animateWidth(source)
                                    .height(56.dp)
                            ) {
                                Icon(
                                    if (state.queue.repeat == RepeatMode.One) Symbols.RepeatOne else Symbols.Repeat,
                                    contentDescription = state.queue.repeat.label
                                )
                            }
                        },
                        menuContent = { DropdownMenuItem(text = { Text(state.queue.repeat.label) }, onClick = actions.onRepeat) }
                    )
                    customItem(
                        buttonGroupContent = {
                            val source = remember { MutableInteractionSource() }
                            FilledTonalButton(
                                onClick = actions.onSpeed,
                                colors = pillColors(state.speed != 1f),
                                interactionSource = source,
                                modifier = Modifier
                                    .weight(1f)
                                    .animateWidth(source)
                                    .height(56.dp)
                                    .semantics { contentDescription = "Speed ${speedLabel(state.speed)}" }
                            ) {
                                Text(speedLabel(state.speed), style = MaterialTheme.typography.titleMedium)
                            }
                        },
                        menuContent = {
                            DropdownMenuItem(text = { Text("Speed ${speedLabel(state.speed)}") }, onClick = actions.onSpeed)
                        }
                    )
                }
                Spacer(Modifier.height(16.dp))
                // The small things at the foot, as chips: the sleep timer on
                // the left, the queue on the right.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AssistChip(
                        onClick = { sleepOpen = true },
                        label = {
                            Text(
                                when (state.sleep) {
                                    SleepTimer.Off -> "Sleep timer"
                                    SleepTimer.EndOfTrack -> "Stops after this track"
                                    else -> "Sleep timer: ${state.sleep.label}"
                                }
                            )
                        },
                        leadingIcon = { Icon(Symbols.Bedtime, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    AssistChip(
                        onClick = { queueOpen = true },
                        label = { Text("Queue") },
                        leadingIcon = { Icon(Symbols.QueueMusic, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
                state.offline?.let { offline ->
                    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text(
                            "Downloading ${offline.done} of ${offline.total} for offline",
                            style = MaterialTheme.typography.labelMedium
                        )
                        LinearWavyProgressIndicator(
                            progress = { if (offline.total > 0) offline.done.toFloat() / offline.total else 0f },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        )
                    }
                }
                Spacer(Modifier.weight(0.2f))
            }
            SnackbarHost(host, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
          }
        }
        if (queueOpen) {
            QueueSheet(state, actions, onDismiss = { queueOpen = false })
        }
        if (sleepOpen) {
            AlertDialog(
                onDismissRequest = { sleepOpen = false },
                icon = { Icon(Symbols.Bedtime, contentDescription = null) },
                title = { Text("Sleep timer") },
                text = {
                    Column {
                        SleepTimer.entries.forEach { timer ->
                            ListItem(
                                headlineContent = { Text(timer.label) },
                                leadingContent = { RadioButton(selected = state.sleep == timer, onClick = null) },
                                modifier = Modifier.clickable {
                                    sleepOpen = false
                                    actions.onSleep(timer)
                                }
                            )
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { sleepOpen = false }) { Text("Close") } }
            )
        }
    }
}

/**
 * Which way the queue plays — in order, reversed, or shuffled — as a button
 * showing the current way and a menu of the three. Tonal when it is not
 * plain order, the way repeat is tonal when it is on.
 */
@Composable
private fun OrderButton(
    order: QueueOrder,
    onOrder: (QueueOrder) -> Unit,
    modifier: Modifier = Modifier.size(56.dp),
    interactionSource: MutableInteractionSource? = null
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        FilledTonalIconButton(
            onClick = { open = true },
            colors = if (order != QueueOrder.Listed) IconButtonDefaults.filledTonalIconButtonColors() else
                IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
            interactionSource = interactionSource,
            modifier = Modifier.fillMaxSize()
        ) {
            // Plain order shows the shuffle symbol, untinted, as players do:
            // an arrow down on its own read as "download". The arrows are in
            // the menu, beside their words.
            Icon(if (order == QueueOrder.Listed) Symbols.Shuffle else orderIcon(order), contentDescription = "Order: ${order.label}")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            QueueOrder.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    leadingIcon = { Icon(orderIcon(option), contentDescription = null) },
                    trailingIcon = if (option == order) {
                        { Icon(Symbols.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    onClick = {
                        open = false
                        onOrder(option)
                    }
                )
            }
        }
    }
}

private fun orderIcon(order: QueueOrder) = when (order) {
    QueueOrder.Listed -> Symbols.ArrowDownward
    QueueOrder.Reversed -> Symbols.ArrowUpward
    QueueOrder.Shuffled -> Symbols.Shuffle
}

private fun speedLabel(speed: Float): String =
    (if (speed == speed.toInt().toFloat()) speed.toInt().toString() else speed.toString()) + "×"

/**
 * Where the track is, and where it goes: Expressive's wavy line for what has
 * played, and the finger dragged along it to move — let go, and it seeks.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Seeker(state: NowPlaying, onSeek: (Float) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    var width by remember { mutableFloatStateOf(1f) }
    val shown = dragging ?: state.progress
    Column(Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .onSizeChangedWidth { width = it }
                .pointerSeek(
                    onDrag = { x -> dragging = (x / width).coerceIn(0f, 1f) },
                    onEnd = {
                        dragging?.let(onSeek)
                        dragging = null
                    }
                )
                .semantics { contentDescription = "Position in ${state.track?.title ?: "track"}" },
            contentAlignment = Alignment.Center
        ) {
            LinearWavyProgressIndicator(progress = { shown }, modifier = Modifier.fillMaxWidth())
        }
        Row(Modifier.fillMaxWidth()) {
            Text(formatDuration((state.durationMs * shown / 1000).toLong()), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            Text(formatDuration(state.durationMs / 1000), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * The queue: the chat's whole music, as a sheet over the player. The
 * brother's complaints are its rules — it is all of the chat's music, paged
 * in as it scrolls back to the first track ever posted; starting a track
 * never rebuilds it or moves it; and it stays open, a sheet rather than a
 * popup a stray tap closes. A search narrows it, and a button goes back to
 * what is playing when it has scrolled far away.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun QueueSheet(state: NowPlaying, actions: MusicActions, onDismiss: () -> Unit) {
    val list = rememberLazyListState(initialFirstVisibleItemIndex = state.queue.current.coerceAtLeast(0))
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    val tracks = state.queue.tracks
    val shown = remember(tracks, query) {
        tracks.withIndex().filter { (_, track) ->
            query.isBlank() ||
                track.title.contains(query, ignoreCase = true) ||
                track.performer.contains(query, ignoreCase = true)
        }
    }
    // Near the end of what is loaded: the next page, appended below.
    val last = list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
    LaunchedEffect(last, shown.size) {
        if (query.isBlank() && last >= shown.size - 8) actions.onLoadMore()
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Box {
            Column {
                Text(
                    buildString {
                        append(state.queue.sourceTitle.ifBlank { "Queue" })
                        append(" · ")
                        append(tracks.size)
                        append(if (state.queue.isComplete) " tracks" else "+ tracks")
                    },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )
                UpNext(state, actions)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search the queue") },
                    leadingIcon = { Icon(Symbols.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
                LazyColumn(state = list, modifier = Modifier.fillMaxWidth()) {
                    itemsIndexed(shown, key = { _, entry -> entry.value.messageId }) { _, entry ->
                        // Not while an Up next track plays: the queue only
                        // keeps its place there.
                        val playing = entry.index == state.queue.current && state.queue.interlude == null
                        ListItem(
                            modifier = Modifier.clickable { actions.onPlayAt(entry.index) },
                            colors = if (playing) {
                                ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                            } else {
                                ListItemDefaults.colors()
                            },
                            headlineContent = {
                                Text(
                                    entry.value.title,
                                    fontWeight = if (playing) FontWeight.SemiBold else null,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            supportingContent = {
                                Text(
                                    listOf(entry.value.performer, entry.value.durationSeconds.takeIf { it > 0 }?.let { formatDuration(it.toLong()) })
                                        .filter { !it.isNullOrBlank() }
                                        .joinToString(" · "),
                                    maxLines = 1
                                )
                            },
                            leadingContent = {
                                if (playing) {
                                    Icon(Symbols.GraphicEq, contentDescription = "Playing", tint = MaterialTheme.colorScheme.primary)
                                } else {
                                    Text("${entry.index + 1}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.widthIn(min = 24.dp))
                                }
                            }
                        )
                    }
                    if (state.isLoadingMore) {
                        item(key = "more") {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                LoadingIndicator(Modifier.size(32.dp))
                            }
                        }
                    }
                }
            }
            // Back to what is playing, once it has scrolled out of view.
            val current = shown.indexOfFirst { it.index == state.queue.current }
            val away = current >= 0 && list.layoutInfo.visibleItemsInfo.none { it.index == current }
            if (away) {
                SmallFloatingActionButton(
                    onClick = { scope.launch { list.animateScrollToItem(current) } },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .semantics { contentDescription = "Go to what is playing" }
                ) { Icon(Symbols.GraphicEq, contentDescription = null) }
            }
        }
    }
}

/**
 * Up next, over the queue: the track from it playing now, and what is lined
 * up after, each to take out again. Nothing at all while it is empty.
 */
@Composable
private fun UpNext(state: NowPlaying, actions: MusicActions) {
    val queue = state.queue
    if (queue.interlude == null && queue.upNext.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Up next",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
        )
        queue.interlude?.let { now ->
            ListItem(
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                headlineContent = { Text(now.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text(now.performer.ifBlank { "Playing" }, maxLines = 1) },
                leadingContent = { Icon(Symbols.GraphicEq, contentDescription = "Playing", tint = MaterialTheme.colorScheme.primary) }
            )
        }
        // A few, and how many more: Up next is a short list by nature, and
        // the queue under it is what the sheet is for.
        queue.upNext.take(UP_NEXT_SHOWN).forEachIndexed { position, track ->
            ListItem(
                headlineContent = { Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text(track.performer.ifBlank { track.senderName }, maxLines = 1) },
                leadingContent = { Icon(Symbols.QueueMusic, contentDescription = null) },
                trailingContent = {
                    IconButton(onClick = { actions.onRemoveUpNext(position) }) {
                        Icon(Symbols.Close, contentDescription = "Remove ${track.title} from Up next")
                    }
                }
            )
        }
        if (queue.upNext.size > UP_NEXT_SHOWN) {
            Text(
                "and ${queue.upNext.size - UP_NEXT_SHOWN} more",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )
        }
        HorizontalDivider(Modifier.padding(top = 4.dp))
    }
}

private const val UP_NEXT_SHOWN = 4

/** How far the player is pulled down before letting go closes it. */
private const val CLOSE_AFTER_DP = 120
/** A flick down faster than this, in px a second, closes it from anywhere. */
private const val CLOSE_FLING = 1_500f

/** The order button's share of the second row: about as wide as it is tall. */
private const val ORDER_WEIGHT = 0.45f

/** The cover while paused: what 20 dp in from each side was on a phone. */
private const val PAUSED_COVER_SCALE = 0.9f


/** A pill's colours: the secondary tone while its setting is on, the surface's while it is not. */
@Composable
private fun pillColors(on: Boolean) = if (on) {
    ButtonDefaults.filledTonalButtonColors()
} else {
    ButtonDefaults.filledTonalButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface
    )
}

/** The reactions the player offers: the few people give music. */
private val PLAYER_REACTIONS = listOf("❤️", "🔥", "👍", "😢")

/** The width the seeker was laid out at, for turning a touch into a fraction. */
private fun Modifier.onSizeChangedWidth(onWidth: (Float) -> Unit): Modifier =
    onSizeChanged { onWidth(it.width.toFloat().coerceAtLeast(1f)) }

/** A tap or a sideways drag along the seeker: where, then done. */
private fun Modifier.pointerSeek(onDrag: (Float) -> Unit, onEnd: () -> Unit): Modifier =
    pointerInput(Unit) {
        detectTapGestures(onTap = { offset ->
            onDrag(offset.x)
            onEnd()
        })
    }.pointerInput(Unit) {
        detectHorizontalDragGestures(
            onDragStart = { offset -> onDrag(offset.x) },
            onDragEnd = onEnd,
            onDragCancel = onEnd
        ) { change, _ -> onDrag(change.position.x) }
    }
