package com.telegramyou.app.ui.music

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.ChatMessage
import com.telegramyou.app.telegram.model.CollectionKind
import com.telegramyou.app.telegram.model.LibraryChat
import com.telegramyou.app.telegram.model.LibraryCollection
import com.telegramyou.app.telegram.model.LibraryTrack
import com.telegramyou.app.telegram.model.MusicLibrary
import com.telegramyou.app.telegram.model.buildLibrary
import com.telegramyou.app.ui.chat.formatDuration
import com.telegramyou.app.ui.icons.Symbols
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LibraryUiState(
    val library: MusicLibrary = MusicLibrary(),
    val isLoading: Boolean = true
)

/**
 * The music library's state: every chat's music, fetched through the same
 * global search My music pages through — all of it this time, up to a
 * limit — and laid out by `buildLibrary` in :core.
 */
class MusicLibraryViewModel(private val repository: TelegramRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val found = mutableListOf<ChatMessage>()
            var cursor: String? = ""
            var pages = 0
            while (cursor != null && pages < MAX_PAGES) {
                val from: String = cursor
                val (page, next) = runCatching { repository.allMusic("", from, PAGE) }
                    .getOrDefault(emptyList<ChatMessage>() to null)
                found += page
                pages++
                cursor = next?.takeIf { it.isNotEmpty() && page.isNotEmpty() }
            }
            val chats = repository.chats.value.associate {
                it.id to LibraryChat(it.title, isChannel = it.isChannel, isSaved = it.isSavedMessages)
            }
            val library = withContext(Dispatchers.Default) { buildLibrary(found, chats) }
            _uiState.update { it.copy(library = library, isLoading = false) }
        }
    }

    private companion object {
        const val PAGE = 100
        /** A thousand tracks: a library, not an archive of everything. */
        const val MAX_PAGES = 10
    }
}

/** What the library's rows and pages ask of the player. */
class LibraryActions(
    val onPlay: (title: String, tracks: List<ChatMessage>, start: ChatMessage?, shuffle: Boolean) -> Unit = { _, _, _, _ -> },
    val onLineUp: (ChatMessage, Boolean) -> Unit = { _, _ -> },
    val onOpenChat: (Long) -> Unit = {}
)

private val LIBRARY_TABS = listOf("For you", "Playlists", "Albums", "Artists", "Tracks")

/**
 * The music library, an experiment under For geeks: a music app's layout
 * over every chat's music, with the social half no music app has — who
 * sent each track and where, and what people in the chats are sending.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MusicLibraryScreen(
    state: LibraryUiState,
    actions: LibraryActions,
    onBack: () -> Unit,
    musicBar: @Composable () -> Unit = {}
) {
    var openKey by rememberSaveable { mutableStateOf<String?>(null) }
    val open = openKey?.let { state.library.collection(it) }
    BackHandler(enabled = open != null) { openKey = null }
    val host = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val lineUp: (ChatMessage, Boolean) -> Unit = { message, first ->
        actions.onLineUp(message, first)
        scope.launch { host.showSnackbar(if (first) "Plays next" else "Added to the queue") }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(open?.title ?: "Music library", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { if (open != null) openKey = null else onBack() }) {
                        Icon(Symbols.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (open == null && !state.library.isEmpty) {
                        IconButton(onClick = {
                            actions.onPlay("Music library", state.library.tracks.map { it.message }, null, true)
                        }) { Icon(Symbols.Shuffle, contentDescription = "Shuffle everything") }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(host) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            musicBar()
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                state.library.isEmpty -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No music in any chat yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                open != null -> CollectionPage(open, actions, lineUp)
                else -> LibraryTabs(state.library, actions, lineUp, onOpen = { openKey = it.key })
            }
        }
    }
}

@Composable
private fun LibraryTabs(
    library: MusicLibrary,
    actions: LibraryActions,
    lineUp: (ChatMessage, Boolean) -> Unit,
    onOpen: (LibraryCollection) -> Unit
) {
    val pager = rememberPagerState { LIBRARY_TABS.size }
    val scope = rememberCoroutineScope()
    PrimaryScrollableTabRow(
        selectedTabIndex = pager.currentPage,
        edgePadding = 8.dp,
        minTabWidth = 0.dp
    ) {
        LIBRARY_TABS.forEachIndexed { index, label ->
            Tab(
                selected = pager.currentPage == index,
                onClick = { scope.launch { pager.animateScrollToPage(index) } },
                text = { Text(label) }
            )
        }
    }
    // Pages from the top: a pager centres a page shorter than itself, and
    // For you sat halfway down an empty screen.
    HorizontalPager(state = pager, verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxSize()) { page ->
        when (page) {
            0 -> ForYou(library, actions, lineUp)
            1 -> CollectionGrid(library.playlists, "No chat has more than one track yet", onOpen)
            2 -> CollectionGrid(library.albums, "Albums are tracks posted together, and there are none yet", onOpen)
            3 -> CollectionGrid(library.artists, "No track names its performer yet", onOpen)
            else -> TrackList(library.tracks, "Music library", actions, lineUp)
        }
    }
}

/**
 * The library's front page: what has just arrived, on Material's carousel,
 * and what people in the chats have been sending, with who and where.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForYou(library: MusicLibrary, actions: LibraryActions, lineUp: (ChatMessage, Boolean) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "latest-header") { Header("Just arrived") }
        item(key = "latest") {
            val latest = library.latest
            val carousel = rememberCarouselState { latest.size }
            HorizontalMultiBrowseCarousel(
                state = carousel,
                preferredItemWidth = 176.dp,
                itemSpacing = 8.dp,
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(208.dp)
            ) { index ->
                val item = latest[index]
                TrackTheme(item.track) {
                    Box(
                        modifier = Modifier
                            .height(208.dp)
                            .maskClip(MaterialTheme.shapes.extraLarge)
                            .clickable {
                                actions.onPlay("Just arrived", latest.map { it.message }, item.message, false)
                            }
                    ) {
                        Cover(item.track, size = 208, corner = 0)
                        Column(
                            Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Text(
                                item.track.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                item.track.performer.ifBlank { item.chatTitle },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        item(key = "people-header") { Header("From your chats") }
        if (library.fromPeople.isEmpty()) {
            item(key = "people-none") {
                Text(
                    "Music people send you in chats and groups shows here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
        items(library.fromPeople, key = { "p${it.message.chatId}:${it.message.id}" }) { item ->
            TrackRow(item, onPlay = {
                actions.onPlay("From your chats", library.fromPeople.map { it.message }, item.message, false)
            }, lineUp = lineUp, onOpenChat = { actions.onOpenChat(item.message.chatId) })
        }
    }
}

@Composable
private fun Header(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)
    )
}

/** Albums, artists or playlists as tiles, each in its own colour. */
@Composable
private fun CollectionGrid(collections: List<LibraryCollection>, emptyText: String, onOpen: (LibraryCollection) -> Unit) {
    if (collections.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(152.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(collections, key = { it.key }) { collection ->
            Column(
                Modifier
                    .clickable { onOpen(collection) }
            ) {
                val cover = collection.cover ?: collection.tracks.first().track
                TrackTheme(cover) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                    ) {
                        Cover(
                            cover,
                            size = 152,
                            corner = if (collection.kind == CollectionKind.Artist) 76 else 16
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(collection.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    collection.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TrackList(tracks: List<LibraryTrack>, title: String, actions: LibraryActions, lineUp: (ChatMessage, Boolean) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        items(tracks, key = { "t${it.message.chatId}:${it.message.id}" }) { item ->
            TrackRow(
                item,
                onPlay = { actions.onPlay(title, tracks.map { it.message }, item.message, false) },
                lineUp = lineUp,
                onOpenChat = { actions.onOpenChat(item.message.chatId) }
            )
        }
    }
}

/** An album, an artist or a playlist: its cover, Play and Shuffle, and its tracks with where each came from. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CollectionPage(collection: LibraryCollection, actions: LibraryActions, lineUp: (ChatMessage, Boolean) -> Unit) {
    val messages = collection.tracks.map { it.message }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "head") {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                val cover = collection.cover ?: collection.tracks.first().track
                TrackTheme(cover) {
                    Cover(cover, size = 200, corner = if (collection.kind == CollectionKind.Artist) 100 else 16)
                }
                Spacer(Modifier.height(16.dp))
                Text(collection.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Text(
                    collection.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { actions.onPlay(collection.title, messages, null, false) }) {
                        Icon(Symbols.PlayArrowFilled, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Play")
                    }
                    FilledTonalButton(onClick = { actions.onPlay(collection.title, messages, null, true) }) {
                        Icon(Symbols.Shuffle, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Shuffle")
                    }
                }
            }
        }
        items(collection.tracks, key = { "c${it.message.chatId}:${it.message.id}" }) { item ->
            TrackRow(
                item,
                onPlay = { actions.onPlay(collection.title, messages, item.message, false) },
                lineUp = lineUp,
                onOpenChat = { actions.onOpenChat(item.message.chatId) }
            )
        }
    }
}

/** A track: its cover, title, performer and length, and who sent it where; the menu lines it up or opens its chat. */
@Composable
private fun TrackRow(item: LibraryTrack, onPlay: () -> Unit, lineUp: (ChatMessage, Boolean) -> Unit, onOpenChat: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    ListItem(
        modifier = Modifier.clickable(onClick = onPlay),
        headlineContent = { Text(item.track.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                listOf(
                    item.track.performer,
                    item.track.durationSeconds.takeIf { it > 0 }?.let { formatDuration(it.toLong()) },
                    item.provenance
                ).filter { !it.isNullOrBlank() }.joinToString(" · "),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = { TrackTheme(item.track) { Cover(item.track, size = 48, corner = 12) } },
        trailingContent = {
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Symbols.MoreVert, contentDescription = "More for ${item.track.title}")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Play next") },
                        leadingIcon = { Icon(Symbols.SkipNext, contentDescription = null) },
                        onClick = {
                            menu = false
                            lineUp(item.message, true)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to queue") },
                        leadingIcon = { Icon(Symbols.QueueMusic, contentDescription = null) },
                        onClick = {
                            menu = false
                            lineUp(item.message, false)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Open chat") },
                        leadingIcon = { Icon(Symbols.Chat, contentDescription = null) },
                        onClick = {
                            menu = false
                            onOpenChat()
                        }
                    )
                }
            }
        }
    )
}
