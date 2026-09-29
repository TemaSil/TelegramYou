package com.telegramyou.app.ui.chat

import androidx.compose.runtime.remember
import com.telegramyou.app.ui.icons.Symbols
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.size
import com.telegramyou.app.telegram.model.VideoContent
import androidx.compose.material3.LinearProgressIndicator
import com.telegramyou.app.ui.media.FileTransfer
import com.telegramyou.app.ui.media.transferProgress
import com.telegramyou.app.telegram.model.ChatMessage
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.telegramyou.app.telegram.model.MessageContentType
import com.telegramyou.app.telegram.model.SharedMediaKind
import com.telegramyou.app.telegram.model.fileExtension
import com.telegramyou.app.telegram.model.firstLink
import com.telegramyou.app.telegram.model.linkHost
import kotlinx.coroutines.launch

/**
 * A chat's shared media, a tab each as the official client has them —
 * Media, Files, Music, Voice, Links, GIFs — swiped between or picked from
 * Material's scrollable tab row. Every tab pages back to the first thing
 * ever sent (SharedMediaViewModel), and each is drawn the way its content is
 * read: pictures in a grid of squares, everything else as a list of rows.
 *
 * The grid is adaptive rather than a fixed column count: three on a phone,
 * more on anything wider, without this screen deciding what device it is on.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChatMediaScreen(
    title: String,
    state: SharedMediaUiState,
    onBack: () -> Unit,
    onTabShown: (SharedMediaKind) -> Unit,
    onNearEnd: (SharedMediaKind) -> Unit,
    onOpen: (ChatMessage) -> Unit,
    /** Non-null while one of these is open full screen. */
    viewingPhoto: ChatMessage? = null,
    onPhotoClosed: () -> Unit = {},
    /** Non-null while a video from the grid is playing. */
    viewingVideo: ChatMessage? = null,
    onVideoClosed: () -> Unit = {},
    /** Files in flight, by id, so a tile can show what it is waiting for. */
    transfers: Map<Int, FileTransfer> = emptyMap(),
    /** A photo or video swiped to in the gallery; what it needs is fetched. */
    onGalleryPage: (ChatMessage) -> Unit = {},
    onFileTapped: (ChatMessage) -> Unit = {},
    onFileOpened: () -> Unit = {},
    onFileRefused: (String?) -> Unit = {},
    onErrorShown: () -> Unit = {},
    /** The voice note or track playing, and how far through it is. */
    playingId: Long? = null,
    loadingId: Long? = null,
    progress: Float = 0f,
    onPlayToggled: (ChatMessage) -> Unit = {}
) {
    val kinds = SharedMediaKind.entries
    val pager = rememberPagerState { kinds.size }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val host = remember { SnackbarHostState() }
    // The tab in view asks for its first page; the others wait to be seen.
    LaunchedEffect(pager.currentPage) { onTabShown(kinds[pager.currentPage]) }
    state.fileToOpen?.let { file ->
        LaunchedEffect(file) {
            if (MediaActions.openFile(context, file.path, file.mime, file.name)) onFileOpened() else onFileRefused(file.name)
        }
    }
    state.errorMessage?.let { message ->
        LaunchedEffect(message) {
            host.showSnackbar(message)
            onErrorShown()
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(host) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "Shared media",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Symbols.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
                PrimaryScrollableTabRow(selectedTabIndex = pager.currentPage, edgePadding = 8.dp) {
                    kinds.forEachIndexed { index, kind ->
                        Tab(
                            selected = pager.currentPage == index,
                            onClick = { scope.launch { pager.animateScrollToPage(index) } },
                            text = { Text(kind.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        HorizontalPager(
            state = pager,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            // Beside the one in view only: a tab is a network request.
            beyondViewportPageCount = 0
        ) { page ->
            val kind = kinds[page]
            val tab = state.tab(kind)
            when {
                !tab.isLoaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                tab.items.isEmpty() -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        kind.emptyText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                kind == SharedMediaKind.Media || kind == SharedMediaKind.Gifs -> {
                    val grid = rememberLazyGridState()
                    NearEnd(grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index, tab.items.size) { onNearEnd(kind) }
                    LazyVerticalGrid(
                        state = grid,
                        columns = GridCells.Adaptive(minSize = 112.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(tab.items, key = { it.id }) { message ->
                            MediaTile(
                                message = message,
                                transfer = message.transferId()?.let { transfers[it] },
                                onClick = { onOpen(message) }
                            )
                        }
                    }
                }
                else -> {
                    val list = rememberLazyListState()
                    NearEnd(list.layoutInfo.visibleItemsInfo.lastOrNull()?.index, tab.items.size) { onNearEnd(kind) }
                    LazyColumn(
                        state = list,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(tab.items, key = { it.id }) { message ->
                            when (kind) {
                                SharedMediaKind.Files -> FileRow(
                                    message = message,
                                    opening = state.openingFileId == message.id,
                                    onClick = { onFileTapped(message) }
                                )
                                SharedMediaKind.Links -> LinkRow(message)
                                else -> PlayableRow(
                                    message = message,
                                    playing = playingId == message.id,
                                    loading = loadingId == message.id,
                                    progress = if (playingId == message.id) progress else 0f,
                                    onToggle = { onPlayToggled(message) }
                                )
                            }
                        }
                        if (tab.isLoading) {
                            item(key = "more") {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    LoadingIndicator(Modifier.size(32.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // The conversation's gallery, not a second one: opened from the
        // grid, it pages through the grid's photos and videos the same way.
        (viewingPhoto ?: viewingVideo)?.let { opened ->
            val media = state.tab(SharedMediaKind.Media).items + state.tab(SharedMediaKind.Gifs).items
            val gallery = remember(media, opened) { galleryOf(media, opened) }
            MediaGallery(
                items = gallery,
                startId = opened.id,
                transfers = transfers,
                onPage = onGalleryPage,
                onDismiss = {
                    onPhotoClosed()
                    onVideoClosed()
                }
            )
        }
    }
}

/** Asks for the next page once the last row shown is within a few of the end. */
@Composable
private fun NearEnd(lastVisible: Int?, count: Int, onNearEnd: () -> Unit) {
    val near = lastVisible != null && lastVisible >= count - NEAR_END_ROWS
    LaunchedEffect(near, count) { if (near) onNearEnd() }
}

private const val NEAR_END_ROWS = 6

/** Who sent it and when, the line every row of a tab ends on. */
private fun sentLine(message: ChatMessage): String =
    listOfNotNull(
        (if (message.isOutgoing) "You" else message.senderName)?.takeIf { it.isNotBlank() },
        message.timeLabel.takeIf { it.isNotBlank() }
    ).joinToString(" · ")

/**
 * A file: its extension on a tonal square, as the official client marks
 * one, the name and size, and who sent it. Tapping opens it in whichever app
 * reads it, fetching it first — the row shows the wait.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FileRow(message: ChatMessage, opening: Boolean, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(message.fileName ?: "File", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                listOfNotNull(message.fileSizeLabel, sentLine(message).takeIf { it.isNotBlank() }).joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val extension = fileExtension(message.fileName)
                    if (extension != null) {
                        Text(extension, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Symbols.Description, contentDescription = null)
                    }
                }
            }
        },
        trailingContent = if (opening) {
            { LoadingIndicator(Modifier.size(28.dp)) }
        } else if (message.documentPath == null && message.documentFileId != null) {
            { Icon(Symbols.Download, contentDescription = "Download ${message.fileName ?: "file"}") }
        } else {
            null
        }
    )
}

/**
 * A link: its card's title when Telegram made one, its site otherwise, the
 * address under it; tapping opens it in the browser.
 */
@Composable
private fun LinkRow(message: ChatMessage) {
    val uriHandler = LocalUriHandler.current
    val link = message.linkPreview?.url ?: firstLink(message.text) ?: return
    val host = linkHost(link)
    ListItem(
        modifier = Modifier.clickable {
            runCatching { uriHandler.openUri(if ("://" in link) link else "https://$link") }
        },
        headlineContent = {
            Text(
                message.linkPreview?.title?.takeIf { it.isNotBlank() } ?: host,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Column {
                Text(link, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sentLine(message), style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        },
        leadingContent = {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        host.firstOrNull()?.uppercase() ?: "#",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    )
}

/**
 * A track, a voice note or a round video message: a play button that is
 * the row's own state, what it is, and while it plays how far through.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlayableRow(
    message: ChatMessage,
    playing: Boolean,
    loading: Boolean,
    progress: Float,
    onToggle: () -> Unit
) {
    val audio = message.audio
    val title = when {
        audio != null -> audio.displayTitle
        message.contentType == MessageContentType.VideoNote -> "Video message"
        else -> "Voice message"
    }
    val details = listOfNotNull(
        audio?.performer?.takeIf { it.isNotBlank() },
        audio?.durationSeconds?.takeIf { it > 0 }?.let { formatDuration(it.toLong()) }
            ?: message.video?.durationSeconds?.takeIf { it > 0 }?.let { formatDuration(it.toLong()) },
        sentLine(message).takeIf { it.isNotBlank() }
    ).joinToString(" · ")
    ListItem(
        modifier = Modifier.clickable(onClick = onToggle),
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                Text(details, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (playing) {
                    LinearWavyProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    )
                }
            }
        },
        leadingContent = {
            FilledTonalIconButton(onClick = onToggle, modifier = Modifier.size(48.dp)) {
                when {
                    loading -> LoadingIndicator(Modifier.size(28.dp))
                    playing -> Icon(Symbols.PauseFilled, contentDescription = "Pause")
                    else -> Icon(Symbols.PlayArrowFilled, contentDescription = "Play $title")
                }
            }
        }
    )
}

/** A video's square in the grid: its poster, with a play badge over it. */
@Composable
private fun VideoTile(
    video: VideoContent,
    caption: String,
    transfer: FileTransfer?
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .semantics(mergeDescendants = true) {
                contentDescription = if (caption.isBlank()) "Video" else "Video, $caption"
            }
    ) {
        video.thumbPath?.let { poster ->
            AsyncImage(
                model = poster,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (transfer == null) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.45f),
                contentColor = Color.White,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Symbols.PlayArrowFilled,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        } else {
            // A bar in the play button's place while the file is coming: the
            // tile is small, and both at once is two things fighting for the
            // same forty points.
            val progress = transferProgress(transfer)
            if (progress == null) {
                LinearProgressIndicator(
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                )
            } else {
                LinearProgressIndicator(
                    progress = { progress },
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Which file a tile is waiting for, if any.
 *
 * A photo has one; a video has two and the one that matters is whichever is
 * moving — the poster arrives on sight, the video only when asked for.
 */
private fun ChatMessage.transferId(): Int? =
    video?.fileId ?: video?.thumbFileId ?: photoFileId

/**
 * One square.
 *
 * Draws a placeholder rather than nothing when the file has not arrived,
 * which is the normal state for anything not scrolled past recently: a tile
 * that appeared only once its bytes did would make the grid rearrange itself
 * while being read.
 */
@Composable
internal fun MediaTile(
    message: ChatMessage,
    transfer: FileTransfer?,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick)
    ) {
        // A video tile is the poster with a play badge on it. Without the
        // badge the grid would claim a still photo and then start playing
        // when it was tapped, which is the kind of surprise a list of
        // thumbnails should never contain.
        message.video?.let { video ->
            VideoTile(video = video, caption = message.text, transfer = transfer)
            return@Surface
        }
        val path = message.photoPath
        if (path.isNullOrBlank()) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Symbols.Image,
                    contentDescription = message.text.ifBlank { "Photo" },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            AsyncImage(
                model = path,
                contentDescription = message.text.ifBlank { "Photo" },
                // Crop, not Fit: a square tile showing a letterboxed photo is
                // mostly container, and the grid is for finding a picture
                // rather than for looking at one.
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
