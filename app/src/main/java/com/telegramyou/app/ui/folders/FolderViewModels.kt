package com.telegramyou.app.ui.folders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.telegramyou.app.navigation.Route
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.ChatFolder
import com.telegramyou.app.telegram.model.ChatPreview
import com.telegramyou.app.telegram.model.FolderRules
import com.telegramyou.app.ui.failureText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ── the folder list ──────────────────────────────────────────────────────

/** One folder as the list shows it: its name and how many chats are in it. */
data class FolderRow(val folder: ChatFolder, val chatCount: Int)

data class FoldersUiState(
    val folders: List<FolderRow> = emptyList(),
    /** The folder a delete was asked for, while the dialog is up. */
    val deleting: ChatFolder? = null,
    val message: String? = null
)

/**
 * Settings → Chat folders: the account's folders in tab order, moved up and
 * down, and deleted. Adding and editing are the next screen's.
 */
class FoldersViewModel(private val repository: TelegramRepository) : ViewModel() {

    private val local = MutableStateFlow(FoldersUiState())

    val uiState: StateFlow<FoldersUiState> =
        combine(repository.folders, repository.observeChats(), local) { folders, chats, state ->
            state.copy(
                folders = folders.map { folder ->
                    FolderRow(folder, chats.count { folder.id in it.folderIds })
                }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoldersUiState())

    /** One place up ([by] -1) or down (+1); the tabs follow. */
    fun onMove(folderId: Int, by: Int) {
        val ids = repository.folders.value.map { it.id }.toMutableList()
        val from = ids.indexOf(folderId)
        val to = from + by
        if (from < 0 || to !in ids.indices) return
        ids.add(to, ids.removeAt(from))
        run("Could not move the folder") { repository.reorderFolders(ids) }
    }

    fun onDeleteRequested(folder: ChatFolder) = local.update { it.copy(deleting = folder) }

    fun onDeleteDismissed() = local.update { it.copy(deleting = null) }

    fun onDeleteConfirmed() {
        val folder = local.value.deleting ?: return
        local.update { it.copy(deleting = null) }
        run("Could not delete the folder") { repository.deleteFolder(folder.id) }
    }

    fun onMessageShown() = local.update { it.copy(message = null) }

    private fun run(failure: String, request: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                request()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(message = failureText(failure, e.message)) }
            }
        }
    }
}

// ── one folder ───────────────────────────────────────────────────────────

data class FolderEditUiState(
    val isNew: Boolean = true,
    val rules: FolderRules = FolderRules(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    /** Chats that can be added by hand: the main list, not the archive. */
    val chats: List<ChatPreview> = emptyList(),
    val pickerOpen: Boolean = false,
    val deleteAsked: Boolean = false,
    /** Saved or deleted; the screen goes back and clears it. */
    val done: Boolean = false,
    val message: String? = null
) {
    /** The chats added by hand, in the order they were added. */
    val includedChats: List<ChatPreview>
        get() = rules.includedChatIds.mapNotNull { id -> chats.firstOrNull { it.id == id } }
}

/**
 * A folder being made or changed. The rules live here until Save: nothing
 * reaches Telegram half-made, and backing out loses the edit, as it does in
 * every other Telegram client.
 */
class FolderEditViewModel(
    private val repository: TelegramRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val folderId: Int? =
        checkNotNull(savedStateHandle.get<Int>(Route.FolderEdit.ARG_FOLDER_ID)).takeIf { it != Route.FolderEdit.NEW }

    private val _uiState = MutableStateFlow(FolderEditUiState(isNew = folderId == null, isLoading = folderId != null))
    val uiState: StateFlow<FolderEditUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeChats().collect { chats ->
                _uiState.update { state -> state.copy(chats = chats.filterNot { it.isArchived }) }
            }
        }
        val id = folderId
        if (id != null) {
            viewModelScope.launch {
                try {
                    val rules = repository.folderRules(id)
                    _uiState.update { it.copy(rules = rules, isLoading = false) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(isLoading = false, message = failureText("Could not load the folder", e.message))
                    }
                }
            }
        }
    }

    /** The name, held to Telegram's limit as it is typed rather than refused on Save. */
    fun onNameChange(name: String) =
        _uiState.update { it.copy(rules = it.rules.copy(name = name.take(FolderRules.MAX_NAME))) }

    /**
     * One rule changed, applied to the rules as they are now — the same
     * reason as ContactsViewModel.onDraftChange: switches flipped faster than
     * a recomposition must not undo each other.
     */
    fun onRulesChange(change: (FolderRules) -> FolderRules) =
        _uiState.update { it.copy(rules = change(it.rules)) }

    fun onChatToggled(chatId: Long) = onRulesChange { it.toggleIncluded(chatId) }

    fun onPickerOpen() = _uiState.update { it.copy(pickerOpen = true) }

    fun onPickerDismiss() = _uiState.update { it.copy(pickerOpen = false) }

    fun onSave() {
        val state = _uiState.value
        if (!state.rules.canSave || state.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                repository.saveFolder(folderId, state.rules)
                _uiState.update { it.copy(isSaving = false, done = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSaving = false, message = failureText("Could not save the folder", e.message))
                }
            }
        }
    }

    fun onDeleteRequested() = _uiState.update { it.copy(deleteAsked = true) }

    fun onDeleteDismissed() = _uiState.update { it.copy(deleteAsked = false) }

    fun onDeleteConfirmed() {
        val id = folderId ?: return
        _uiState.update { it.copy(deleteAsked = false) }
        viewModelScope.launch {
            try {
                repository.deleteFolder(id)
                _uiState.update { it.copy(done = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(message = failureText("Could not delete the folder", e.message)) }
            }
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }
}
