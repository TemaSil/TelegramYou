package com.telegramyou.app.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.telegramyou.app.navigation.Route
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.ChatMessage
import com.telegramyou.app.telegram.model.SharedMediaKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One tab's worth of a chat's shared media, as far as it has been paged. */
data class SharedMediaTab(
    val items: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    /** The server has nothing older: the first thing ever sent is here. */
    val isComplete: Boolean = false,
    /** Asked for at least once, so an empty list means there is none. */
    val isLoaded: Boolean = false
)

/** A file downloaded and waiting for the screen to hand to another app. */
data class FileToOpen(val path: String, val mime: String?, val name: String?)

data class SharedMediaUiState(
    val title: String = "",
    val tabs: Map<SharedMediaKind, SharedMediaTab> = emptyMap(),
    /** The file being fetched before it can be opened, by message id. */
    val openingFileId: Long? = null,
    val fileToOpen: FileToOpen? = null,
    val errorMessage: String? = null
) {
    fun tab(kind: SharedMediaKind): SharedMediaTab = tabs[kind] ?: SharedMediaTab()
}

/**
 * A chat's shared media, a tab per kind, each paged in as it is scrolled —
 * back to the first thing ever sent there, where the grid before it stopped
 * at sixty. A tab asks for its first page when it is first shown, not
 * before: most people open Media and never look at the rest.
 */
class SharedMediaViewModel(
    private val repository: TelegramRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val chatId: Long = requireNotNull(savedStateHandle[Route.Chat.ARG_CHAT_ID]) {
        "SharedMediaViewModel needs a ${Route.Chat.ARG_CHAT_ID} argument"
    }

    private val _uiState = MutableStateFlow(
        SharedMediaUiState(title = repository.chats.value.firstOrNull { it.id == chatId }?.title.orEmpty())
    )
    val uiState: StateFlow<SharedMediaUiState> = _uiState.asStateFlow()

    /** The tab has come into view: its first page, once. */
    fun onTabShown(kind: SharedMediaKind) {
        if (_uiState.value.tab(kind).isLoaded || _uiState.value.tab(kind).isLoading) return
        load(kind)
    }

    /** Scrolled near the end of the tab: the page before the oldest shown. */
    fun onNearEnd(kind: SharedMediaKind) {
        val tab = _uiState.value.tab(kind)
        if (tab.isLoading || tab.isComplete || !tab.isLoaded) return
        load(kind)
    }

    /** Every visit again, for Media: what arrived while the screen was closed. */
    fun refresh(kind: SharedMediaKind) {
        _uiState.update { it.copy(tabs = it.tabs - kind) }
        load(kind)
    }

    private fun load(kind: SharedMediaKind) {
        val before = _uiState.value.tab(kind).items.lastOrNull()?.id ?: 0L
        setTab(kind) { it.copy(isLoading = true) }
        viewModelScope.launch {
            val page = runCatching { repository.sharedMedia(chatId, kind, before, PAGE) }.getOrDefault(emptyList())
            setTab(kind) { tab ->
                val known = tab.items.mapTo(HashSet()) { it.id }
                tab.copy(
                    items = tab.items + page.filter { it.id !in known },
                    isLoading = false,
                    isLoaded = true,
                    // A short page is the last one.
                    isComplete = page.size < PAGE
                )
            }
        }
    }

    private fun setTab(kind: SharedMediaKind, change: (SharedMediaTab) -> SharedMediaTab) =
        _uiState.update { it.copy(tabs = it.tabs + (kind to change(it.tab(kind)))) }

    /**
     * A file tapped: opened at once when it is already on the phone, fetched
     * first when it is not — its row showing the wait — and then handed to
     * the screen, which knows how to reach another app.
     */
    fun onFileTapped(message: ChatMessage) {
        val ready = message.documentPath
        if (ready != null) {
            _uiState.update { it.copy(fileToOpen = FileToOpen(ready, message.mimeType, message.fileName)) }
            return
        }
        val fileId = message.documentFileId ?: run {
            _uiState.update { it.copy(errorMessage = "This file is not on the phone yet") }
            return
        }
        if (_uiState.value.openingFileId != null) return
        _uiState.update { it.copy(openingFileId = message.id) }
        viewModelScope.launch {
            val outcome = runCatching { repository.downloadToList(message.chatId, message.id, fileId) }
                .getOrDefault(com.telegramyou.app.telegram.model.DownloadOutcome.Failed)
            val path = (outcome as? com.telegramyou.app.telegram.model.DownloadOutcome.Done)?.path
            _uiState.update {
                if (path == null) {
                    // Paused or cancelled in Downloads is not a failure.
                    it.copy(
                        openingFileId = null,
                        errorMessage = if (outcome == com.telegramyou.app.telegram.model.DownloadOutcome.Failed) {
                            "Could not download ${message.fileName ?: "the file"}"
                        } else {
                            it.errorMessage
                        }
                    )
                } else {
                    it.copy(openingFileId = null, fileToOpen = FileToOpen(path, message.mimeType, message.fileName))
                }
            }
            // Kept on the row, so a second tap opens it straight away.
            if (path != null) {
                setTab(SharedMediaKind.Files) { tab ->
                    tab.copy(items = tab.items.map { if (it.id == message.id) it.copy(documentPath = path) else it })
                }
            }
        }
    }

    fun onFileOpened() = _uiState.update { it.copy(fileToOpen = null) }

    fun onFileRefused(name: String?) = _uiState.update {
        it.copy(fileToOpen = null, errorMessage = "No app on this phone opens ${name ?: "this file"}")
    }

    fun onErrorShown() = _uiState.update { it.copy(errorMessage = null) }

    private companion object {
        const val PAGE = 50
    }
}
