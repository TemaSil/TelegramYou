package com.telegramyou.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.ChatPreview
import com.telegramyou.app.telegram.model.StoryItem
import com.telegramyou.app.telegram.model.TelegramUser
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
    val isRefreshing: Boolean = false
)

class HomeViewModel(
    private val repository: TelegramRepository
) : ViewModel() {

    /** Set here rather than in the screen, so a rotation mid-refresh keeps it. */
    private val refreshing = MutableStateFlow(false)

    val uiState: StateFlow<HomeUiState> = combine(
        repository.observeAuth(),
        repository.observeChats(),
        repository.observeStories(),
        refreshing
    ) { auth, chats, stories, isRefreshing ->
        HomeUiState(
            me = auth.me,
            chats = chats,
            stories = stories,
            isRefreshing = isRefreshing
        )
    }.stateIn(
        scope = viewModelScope,
        // Kept briefly past the last subscriber: a rotation unsubscribes and
        // resubscribes, and rebuilding the list for that is wasted work.
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

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
}
