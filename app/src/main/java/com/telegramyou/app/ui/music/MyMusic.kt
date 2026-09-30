package com.telegramyou.app.ui.music

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.telegramyou.app.music.NowPlaying
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.ChatMessage
import com.telegramyou.app.ui.chat.formatDuration
import com.telegramyou.app.ui.icons.Symbols
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MyMusicUiState(
    val query: String = "",
    val tracks: List<ChatMessage> = emptyList(),
    /** Where the next page starts; null once there is no more. */
    val cursor: String? = null,
    val isLoading: Boolean = false,
    val isLoaded: Boolean = false,
    /** Chat titles, for "from Material Sound" under each track. */
    val chatTitles: Map<Long, String> = emptyMap()
)

/**
 * "My music": every track anybody sent this account or it sent, from every
 * chat, newest first, searchable by title and performer — Telegram's own
 * search with its music filter, paged as it scrolls.
 */
class MyMusicViewModel(private val repository: TelegramRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(MyMusicUiState())
    val uiState: StateFlow<MyMusicUiState> = _uiState.asStateFlow()
    private var searching: Job? = null

    init {
        viewModelScope.launch {
            repository.chats.collect { chats ->
                _uiState.update { state -> state.copy(chatTitles = chats.associate { it.id to it.title }) }
            }
        }
        load(reset = true)
    }

    fun onQuery(query: String) {
        _uiState.update { it.copy(query = query) }
        searching?.cancel()
        searching = viewModelScope.launch {
            delay(SEARCH_PAUSE_MS)
            load(reset = true)
        }
    }

    fun onNearEnd() {
        val state = _uiState.value
        if (state.isLoading || state.cursor == null || !state.isLoaded) return
        load(reset = false)
    }

    private fun load(reset: Boolean) {
        val state = _uiState.value
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val (page, next) = runCatching {
                repository.allMusic(state.query, if (reset) "" else state.cursor.orEmpty(), PAGE)
            }.getOrDefault(emptyList<ChatMessage>() to null)
            _uiState.update {
                val tracks = if (reset) page else it.tracks + page.filter { m -> it.tracks.none { t -> t.id == m.id && t.chatId == m.chatId } }
                it.copy(tracks = tracks, cursor = next, isLoading = false, isLoaded = true)
            }
        }
    }

    private companion object {
        const val PAGE = 50
        const val SEARCH_PAUSE_MS = 300L
    }
}

/**
 * The screen: a search over every chat's music, and the tracks, each with
 * where it came from. Tapping one plays it with all of them as the queue.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MyMusicScreen(
    state: MyMusicUiState,
    nowPlaying: NowPlaying,
    onBack: () -> Unit,
    onQuery: (String) -> Unit,
    onNearEnd: () -> Unit,
    onPlay: (ChatMessage) -> Unit,
    musicBar: @Composable () -> Unit = {},
    /** A track into the player's Up next: first (true) or last. */
    onLineUp: (ChatMessage, Boolean) -> Unit = { _, _ -> },
    /** The music library, where For geeks has it on; null hides the way in. */
    onOpenLibrary: (() -> Unit)? = null
) {
    val host = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    Scaffold(
        snackbarHost = { SnackbarHost(host) },
        topBar = {
            TopAppBar(
                title = { Text("My music") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Symbols.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (onOpenLibrary != null) {
                        IconButton(onClick = onOpenLibrary) {
                            Icon(Symbols.LibraryMusic, contentDescription = "Music library")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            musicBar()
            // The same pill as search's own field, not a boxed text field:
            // one way of searching across the app.
            SearchBarDefaults.InputField(
                query = state.query,
                onQueryChange = onQuery,
                onSearch = {},
                expanded = false,
                onExpandedChange = {},
                placeholder = { Text("Search every chat's music") },
                leadingIcon = { Icon(Symbols.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(SearchBarDefaults.inputFieldShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            )
            when {
                !state.isLoaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                state.tracks.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (state.query.isBlank()) "No music in any chat yet" else "Nothing called that",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                else -> {
                    val list = rememberLazyListState()
                    val last = list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    LaunchedEffect(last, state.tracks.size) {
                        if (last >= state.tracks.size - 8) onNearEnd()
                    }
                    LazyColumn(state = list, contentPadding = PaddingValues(bottom = 16.dp)) {
                        items(state.tracks, key = { "${it.chatId}:${it.id}" }) { message ->
                            val audio = message.audio ?: return@items
                            val playing = nowPlaying.track?.messageId == message.id && nowPlaying.isPlaying
                            ListItem(
                                modifier = Modifier.clickable { onPlay(message) },
                                headlineContent = { Text(audio.displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = {
                                    Text(
                                        listOf(
                                            audio.performer,
                                            audio.durationSeconds.takeIf { it > 0 }?.let { formatDuration(it.toLong()) },
                                            state.chatTitles[message.chatId]?.let { "from $it" }
                                        ).filter { !it.isNullOrBlank() }.joinToString(" · "),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                leadingContent = {
                                    FilledTonalIconButton(onClick = { onPlay(message) }, modifier = Modifier.size(48.dp)) {
                                        Icon(
                                            if (playing) Symbols.PauseFilled else Symbols.PlayArrowFilled,
                                            contentDescription = if (playing) "Pause" else "Play ${audio.displayTitle}"
                                        )
                                    }
                                },
                                trailingContent = {
                                    var menu by remember { mutableStateOf(false) }
                                    Box {
                                        IconButton(onClick = { menu = true }) {
                                            Icon(Symbols.MoreVert, contentDescription = "More for ${audio.displayTitle}")
                                        }
                                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                            DropdownMenuItem(
                                                text = { Text("Play next") },
                                                leadingIcon = { Icon(Symbols.SkipNext, contentDescription = null) },
                                                onClick = {
                                                    menu = false
                                                    onLineUp(message, true)
                                                    scope.launch { host.showSnackbar("Plays next") }
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Add to queue") },
                                                leadingIcon = { Icon(Symbols.QueueMusic, contentDescription = null) },
                                                onClick = {
                                                    menu = false
                                                    onLineUp(message, false)
                                                    scope.launch { host.showSnackbar("Added to the queue") }
                                                }
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
