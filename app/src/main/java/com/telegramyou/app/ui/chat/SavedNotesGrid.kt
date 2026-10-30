package com.telegramyou.app.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.telegramyou.app.i18n.tr
import com.telegramyou.app.telegram.model.ChatMessage
import com.telegramyou.app.telegram.model.MessageContentType
import com.telegramyou.app.ui.icons.Symbols

/**
 * Saved Messages as notes (2.9), laid out the way Google Keep lays out its
 * own: cards of their own heights in a staggered grid, newest first, a
 * picture across the top of the ones that have one, the first line of a
 * longer note as its title, and the tags at the foot. A note carrying a
 * tag takes one of the theme's container colours by it, as Keep's notes
 * are coloured — so notes tagged alike look alike.
 *
 * Stock Material: LazyVerticalStaggeredGrid, Card, ModalBottomSheet,
 * ListItem. The composer stays where it is: what is typed there is a new
 * note.
 */
@OptIn(ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SavedNotesGrid(
    messages: List<ChatMessage>,
    hasMoreOlder: Boolean,
    onLoadOlder: () -> Unit,
    contentPadding: PaddingValues,
    onOpen: (ChatMessage) -> Unit,
    onPhotoVisible: (ChatMessage) -> Unit,
    modifier: Modifier = Modifier
) {
    val newestFirst = remember(messages) { messages.asReversed() }
    val grid = rememberLazyStaggeredGridState()
    val nearEnd by remember {
        derivedStateOf {
            val last = grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= grid.layoutInfo.totalItemsCount - LOAD_AHEAD
        }
    }
    LaunchedEffect(nearEnd, hasMoreOlder, messages.size) {
        if (nearEnd && hasMoreOlder) onLoadOlder()
    }
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(NOTE_MIN_WIDTH),
        state = grid,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp,
        modifier = modifier.fillMaxSize()
    ) {
        items(newestFirst, key = { it.id }) { message ->
            NoteCard(
                message = message,
                onOpen = { onOpen(message) },
                onPhotoVisible = { onPhotoVisible(message) },
                modifier = Modifier.animateItem(
                    fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                    placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec()
                )
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(
    message: ChatMessage,
    onOpen: () -> Unit,
    onPhotoVisible: () -> Unit,
    modifier: Modifier = Modifier
) {
    val body = noteBody(message)
    val tone = noteTone(message)
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = tone.first, contentColor = tone.second),
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(onClick = onOpen, onLongClick = onOpen)
    ) {
        if (noteHasPicture(message)) NotePicture(message, onPhotoVisible)
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            message.forwardedFrom?.let { from ->
                Text(
                    tr("From $from", "От $from"),
                    style = MaterialTheme.typography.labelMedium,
                    color = tone.second.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
            }
            if (body.isNotEmpty()) {
                val title = body.substringBefore('\n').takeIf { '\n' in body }
                if (title != null) {
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    if (title != null) body.substringAfter('\n').trim() else body,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = NOTE_LINES,
                    overflow = TextOverflow.Ellipsis
                )
            }
            noteAttachment(message)?.let { line ->
                if (body.isNotEmpty()) Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(attachmentIcon(message), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(line, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                message.reactions.take(3).forEach { reaction ->
                    ReactionGlyph(reaction.emoji, size = 16.dp, animate = false)
                    Spacer(Modifier.width(4.dp))
                }
                Spacer(Modifier.weight(1f))
                Text(
                    message.timeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = tone.second.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun NotePicture(message: ChatMessage, onPhotoVisible: () -> Unit) {
    val path = message.photoPath ?: message.video?.thumbPath
    val mini = message.photoMini ?: message.video?.mini
    val aspect = (if (message.contentType == MessageContentType.Photo) message.photoAspect else message.video?.aspect ?: 1f)
        .coerceIn(0.6f, 1.6f)
    LaunchedEffect(path) { if (path == null && message.contentType == MessageContentType.Photo) onPhotoVisible() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
    ) {
        mini?.let { BlurredMini(it, Modifier.fillMaxSize()) }
        if (path != null && !message.hasSpoiler) {
            AsyncImage(model = path, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        if (message.video != null && message.contentType != MessageContentType.Photo) {
            Icon(
                Symbols.PlayArrowFilled,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp)
            )
        }
    }
}

/** A note's colour: the card's own surface, or — tagged — a container colour chosen by the tag. */
@Composable
private fun noteTone(message: ChatMessage): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    val tag = message.reactions.firstOrNull()?.emoji ?: return scheme.surfaceContainerHigh to scheme.onSurface
    return when (Math.floorMod(tag.hashCode(), 3)) {
        0 -> scheme.primaryContainer to scheme.onPrimaryContainer
        1 -> scheme.secondaryContainer to scheme.onSecondaryContainer
        else -> scheme.tertiaryContainer to scheme.onTertiaryContainer
    }
}

private fun attachmentIcon(message: ChatMessage): ImageVector = when (message.contentType) {
    MessageContentType.Voice -> Symbols.Mic
    MessageContentType.Audio -> Symbols.MusicNote
    MessageContentType.Location -> Symbols.LocationOn
    MessageContentType.Contact -> Symbols.Person
    MessageContentType.Poll -> Symbols.Poll
    else -> Symbols.Description
}

/**
 * A note opened: all of it, and what can be done with it, in a sheet from
 * the bottom — the way a Keep note opens to be read whole.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoteSheet(
    message: ChatMessage,
    onDismiss: () -> Unit,
    onShowInChat: () -> Unit,
    onOpenPicture: () -> Unit,
    onCopy: () -> Unit,
    onEdit: (() -> Unit)?,
    onTag: () -> Unit,
    onDelete: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
        ) {
            if (noteHasPicture(message)) {
                Box(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .clip(MaterialTheme.shapes.large)
                        .combinedClickable(onClick = onOpenPicture)
                ) { NotePicture(message, onPhotoVisible = {}) }
                Spacer(Modifier.height(12.dp))
            }
            val body = noteBody(message)
            if (body.isNotEmpty()) {
                SelectionContainer {
                    Text(body, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 24.dp))
                }
            }
            noteAttachment(message)?.let { line ->
                Text(line, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            }
            Text(
                message.timeLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            Spacer(Modifier.height(8.dp))
            NoteAction(Symbols.Chat, tr("Show in chat", "Показать в чате"), onShowInChat)
            if (body.isNotEmpty()) NoteAction(Symbols.ContentCopy, tr("Copy", "Копировать"), onCopy)
            onEdit?.let { NoteAction(Symbols.Edit, tr("Edit", "Изменить"), it) }
            NoteAction(Symbols.Tag, tr("Tag", "Тег"), onTag)
            NoteAction(Symbols.Delete, tr("Delete", "Удалить"), onDelete)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun NoteAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick)
    )
}

/** Narrowest a column of notes goes: two on a phone, more on a tablet. */
private val NOTE_MIN_WIDTH = 160.dp

/** Lines of a note shown on its card; the sheet shows the rest. */
private const val NOTE_LINES = 10

/** How near the end of the loaded notes the older ones are asked for. */
private const val LOAD_AHEAD = 6
