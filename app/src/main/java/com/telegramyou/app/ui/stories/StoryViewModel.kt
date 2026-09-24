package com.telegramyou.app.ui.stories

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.telegramyou.app.navigation.Route
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.StoryFrame
import com.telegramyou.app.telegram.model.StoryItem
import com.telegramyou.app.telegram.model.startIndex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One circle of the rail being watched: its stories, which one is up, and
 * where each one's picture or video is once it has arrived.
 *
 * [isLoading] is what keeps the viewer open while the stories are fetched.
 * The first version had only "the story, or null", started at null, and the
 * screen closed itself on the null before anything had been looked up — so no
 * live story could ever be opened.
 */
data class StoryUiState(
    val isLoading: Boolean = true,
    val story: StoryItem? = null,
    val frames: List<StoryFrame> = emptyList(),
    val index: Int = 0,
    /**
     * Downloaded media by story id; a frame is ready once it is here. Empty
     * for one whose download failed.
     */
    val paths: Map<Int, String> = emptyMap()
) {
    val frame: StoryFrame? get() = frames.getOrNull(index)

    /** Nothing to show — expired, deleted, or never there. */
    val isGone: Boolean get() = !isLoading && (story == null || frames.isEmpty())

    /** The current story can start its clock: its media is here, or it has none. */
    val isFrameReady: Boolean
        get() = frame?.let { it.fileId == null || paths.containsKey(it.id) } == true
}

class StoryViewModel(
    private val repository: TelegramRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val storyId: Long = requireNotNull(savedStateHandle[Route.Story.ARG_STORY_ID]) {
        "StoryViewModel needs a ${Route.Story.ARG_STORY_ID} argument"
    }

    private val _uiState = MutableStateFlow(StoryUiState())
    val uiState: StateFlow<StoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val story = repository.observeStories().value.firstOrNull { it.id == storyId }
            val frames = try {
                if (story == null) emptyList() else repository.storyFrames(storyId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            val paths = frames.mapNotNull { f -> f.localPath?.let { f.id to it } }.toMap()
            _uiState.value = StoryUiState(
                isLoading = false,
                story = story,
                frames = frames,
                index = frames.startIndex(),
                paths = paths
            )
            fetchAround(frames.startIndex())
        }
    }

    /** The next story, or false at the end — where the viewer closes. */
    fun next(): Boolean {
        val state = _uiState.value
        if (state.index + 1 >= state.frames.size) return false
        _uiState.update { it.copy(index = it.index + 1) }
        fetchAround(state.index + 1)
        return true
    }

    /** The one before; at the first, it starts that one again. */
    fun previous() {
        val state = _uiState.value
        if (state.index == 0) return
        _uiState.update { it.copy(index = it.index - 1) }
        fetchAround(state.index - 1)
    }

    /** The story on screen has been shown to its end, or tapped past. */
    fun markSeen() {
        val frame = _uiState.value.frame ?: return
        viewModelScope.launch {
            try {
                repository.markStorySeen(storyId, frame.id)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // A view that did not register costs nothing worth a message.
            }
        }
    }

    private val fetching = mutableSetOf<Int>()

    /** This story's media, and the next one's, so tapping on does not wait. */
    private fun fetchAround(index: Int) {
        val frames = _uiState.value.frames
        listOfNotNull(frames.getOrNull(index), frames.getOrNull(index + 1)).forEach { frame ->
            val fileId = frame.fileId ?: return@forEach
            if (_uiState.value.paths.containsKey(frame.id) || !fetching.add(frame.id)) return@forEach
            viewModelScope.launch {
                val path = try {
                    repository.downloadFile(fileId)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                fetching.remove(frame.id)
                // An empty path for one that could not be fetched: the story
                // is then shown by its caption and moves on, rather than
                // holding the viewer on a spinner for ever.
                _uiState.update { it.copy(paths = it.paths + (frame.id to path.orEmpty())) }
            }
        }
    }
}
