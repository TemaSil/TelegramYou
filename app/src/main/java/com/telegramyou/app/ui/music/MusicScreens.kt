package com.telegramyou.app.ui.music

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
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
    val audioSession: () -> Int = { 0 }
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

/**
 * The mini player: a strip under a screen's top bar while something plays,
 * as the official client has one — the track, play and pause, the next one,
 * and how far through it is as Expressive's wavy line. Tapping it opens the
 * full player. It goes when the music is stopped.
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
        TrackTheme(track, state.coverSeed) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(onClick = onOpen)
                    .semantics { contentDescription = "Now playing: ${track.title}" }
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 2.dp)
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
                    LinearWavyProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
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
private fun PlayPauseButton(state: NowPlaying, onToggle: () -> Unit, size: Int) {
    val corner by animateDpAsState(
        targetValue = if (state.isPlaying) (size / 3.2f).dp else (size / 2f).dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "playShape"
    )
    FilledIconButton(
        onClick = onToggle,
        shape = RoundedCornerShape(corner),
        modifier = Modifier.size(size.dp)
    ) {
        when {
            state.isLoading -> LoadingIndicator(Modifier.size((size * 0.6f).dp), color = MaterialTheme.colorScheme.onPrimary)
            state.isPlaying -> Icon(Symbols.PauseFilled, contentDescription = "Pause", modifier = Modifier.size((size * 0.45f).dp))
            else -> Icon(Symbols.PlayArrowFilled, contentDescription = "Play", modifier = Modifier.size((size * 0.45f).dp))
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
    onOpenChat: (Long) -> Unit = actions.onOpenChat
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
    TrackTheme(track, state.coverSeed) {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
          Box(Modifier.fillMaxSize()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = onBack) { Icon(Symbols.KeyboardArrowDown, contentDescription = "Close player") }
                    Text(
                        state.queue.sourceTitle.ifBlank { "Music" },
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { queueOpen = true }) { Icon(Symbols.QueueMusic, contentDescription = "Queue") }
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
                            DropdownMenuItem(
                                text = { Text("Sleep timer") },
                                leadingIcon = { Icon(Symbols.Bedtime, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    sleepOpen = true
                                }
                            )
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
                val inset by animateDpAsState(
                    targetValue = if (state.isPlaying) 0.dp else 20.dp,
                    animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
                    label = "coverInset"
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp)
                        .aspectRatio(1f)
                        .padding(inset),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(36.dp))
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
                Text(
                    track.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Text(
                    listOf(track.performer, track.senderName.takeIf { it.isNotBlank() }?.let { "sent by $it" })
                        .filter { !it.isNullOrBlank() }
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(20.dp))
                Seeker(state, actions.onSeek)
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    FilledTonalIconButton(onClick = actions.onPrevious, modifier = Modifier.size(64.dp)) {
                        Icon(Symbols.SkipPrevious, contentDescription = "Previous track", modifier = Modifier.size(32.dp))
                    }
                    PlayPauseButton(state, actions.onToggle, size = 96)
                    FilledTonalIconButton(onClick = actions.onNext, modifier = Modifier.size(64.dp)) {
                        Icon(Symbols.SkipNext, contentDescription = "Next track", modifier = Modifier.size(32.dp))
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OrderButton(state.queue.order, actions.onOrder)
                    IconButton(
                        onClick = actions.onRepeat,
                        colors = if (state.queue.repeat != RepeatMode.Off) IconButtonDefaults.filledTonalIconButtonColors() else IconButtonDefaults.iconButtonColors()
                    ) {
                        Icon(
                            if (state.queue.repeat == RepeatMode.One) Symbols.RepeatOne else Symbols.Repeat,
                            contentDescription = state.queue.repeat.label
                        )
                    }
                    TextButton(onClick = actions.onSpeed) {
                        Text(speedLabel(state.speed), modifier = Modifier.semantics { contentDescription = "Speed ${speedLabel(state.speed)}" })
                    }
                }
                if (state.sleep != SleepTimer.Off) {
                    AssistChip(
                        onClick = { sleepOpen = true },
                        label = {
                            Text(
                                if (state.sleep == SleepTimer.EndOfTrack) "Stops after this track"
                                else "Sleep timer: ${state.sleep.label}"
                            )
                        },
                        leadingIcon = { Icon(Symbols.Bedtime, contentDescription = null, modifier = Modifier.size(18.dp)) }
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
private fun OrderButton(order: QueueOrder, onOrder: (QueueOrder) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { open = true },
            colors = if (order != QueueOrder.Listed) IconButtonDefaults.filledTonalIconButtonColors() else IconButtonDefaults.iconButtonColors()
        ) { Icon(orderIcon(order), contentDescription = "Order: ${order.label}") }
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
