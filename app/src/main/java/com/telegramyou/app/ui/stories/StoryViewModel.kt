package com.telegramyou.app.ui.stories

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.telegramyou.app.navigation.Route
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.StoryItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The story being viewed, looked up by the id the route carries.
 *
 * A null [story] means it is gone — seen and dropped from the rail while the
 * viewer was open, say — and the screen closes itself rather than showing an
 * empty frame.
 */
data class StoryUiState(val story: StoryItem? = null)

class StoryViewModel(
    private val repository: TelegramRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val storyId: Long = requireNotNull(savedStateHandle[Route.Story.ARG_STORY_ID]) {
        "StoryViewModel needs a ${Route.Story.ARG_STORY_ID} argument"
    }

    val uiState: StateFlow<StoryUiState> = repository.observeStories()
        .map { stories -> StoryUiState(stories.firstOrNull { it.id == storyId }) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StoryUiState()
        )

    fun markSeen() {
        viewModelScope.launch { repository.markStorySeen(storyId) }
    }
}
