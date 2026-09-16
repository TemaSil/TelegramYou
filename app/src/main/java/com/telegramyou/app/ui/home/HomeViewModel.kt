package com.telegramyou.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.ChatPreview
import com.telegramyou.app.telegram.model.MessageHit
import com.telegramyou.app.telegram.model.StoryItem
import com.telegramyou.app.telegram.model.TelegramUser
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State for the chat list.
 *
 * The screen renders this and nothing else: no repository, no coroutines, no
 * `remember { mutableStateOf }` holding anything a rotation would lose.
 */
data class HomeUiState(
    val me: TelegramUser? = null,
    val chats: List<ChatPreview> = emptyList(),
    val stories: List<StoryItem> = emptyList(),
    val isRefreshing: Boolean = false,
    val search: SearchState = SearchState()
)

/**
 * The search field above the chat list.
 *
 * [isSearching] is true only while a request is in flight, so an empty
 * [results] can be told apart from "nothing found yet" — otherwise a slow
 * query shows "no results" before it has looked.
 */
data class SearchState(
    val expanded: Boolean = false,
    val query: String = "",
    val results: List<ChatPreview> = emptyList(),
    val messages: List<MessageHit> = emptyList(),
    val isSearching: Boolean = false
) {
    /** Used to decide whether the screen may say nothing was found. */
    val isEmpty: Boolean get() = results.isEmpty() && messages.isEmpty()
}

class HomeViewModel(
    private val repository: TelegramRepository
) : ViewModel() {

    /** Set here rather than in the screen, so a rotation mid-refresh keeps it. */
    private val refreshing = MutableStateFlow(false)

    private val search = MutableStateFlow(SearchState())

    /** Cancelled on each keystroke, which is what makes the delay a debounce. */
    private var searchJob: Job? = null

    val uiState: StateFlow<HomeUiState> = combine(
        repository.observeAuth(),
        repository.observeChats(),
        repository.observeStories(),
        refreshing,
        search
    ) { auth, chats, stories, isRefreshing, searchState ->
        HomeUiState(
            me = auth.me,
            chats = chats,
            stories = stories,
            isRefreshing = isRefreshing,
            search = searchState
        )
    }.stateIn(
        scope = viewModelScope,
        // Kept briefly past the last subscriber: a rotation unsubscribes and
        // resubscribes, and rebuilding the list for that is wasted work.
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

    // ── search ───────────────────────────────────────────────────────────

    fun onSearchExpandedChange(expanded: Boolean) {
        searchJob?.cancel()
        search.value = if (expanded) {
            search.value.copy(expanded = true)
        } else {
            // Closing clears it. A query left behind would silently filter the
            // list the next time the field is opened.
            SearchState()
        }
    }

    fun onSearchQueryChange(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            search.value = search.value.copy(
                query = query,
                results = emptyList(),
                messages = emptyList(),
                isSearching = false
            )
            return
        }
        search.value = search.value.copy(query = query, isSearching = true)
        searchJob = viewModelScope.launch {
            // Long enough that typing a word is one request rather than five,
            // short enough that it does not feel like waiting.
            delay(SEARCH_DEBOUNCE_MS)
            // Both halves of one search, so the screen never shows chats
            // while still waiting on messages and looks half-finished.
            val chats = repository.searchChats(query)
            val messages = repository.searchMessages(query)
            search.value = search.value.copy(
                results = chats,
                messages = messages,
                isSearching = false
            )
        }
    }

    /**
     * Ends the session.
     *
     * Nothing navigates afterwards: the graph watches the auth state and moves
     * to the login screen when the client reports it, so a hand-written
     * navigation here would be a second answer that can disagree with the
     * first.
     */
    fun logout() {
        viewModelScope.launch { repository.logout() }
    }

    fun refresh() {
        viewModelScope.launch {
            refreshing.value = true
            try {
                repository.refreshChats()
            } finally {
                refreshing.value = false
            }
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
    }
}
