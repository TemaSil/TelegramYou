package com.telegramyou.app.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.AttachmentDraft
import com.telegramyou.app.telegram.model.ChatDetail
import com.telegramyou.app.telegram.model.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State for one conversation.
 *
 * [replyTo] and [editing] are alternatives, never both at once — the composer
 * shows one banner, and choosing either cancels the other. [pendingDelete]
 * being non-null is what puts the confirmation dialog on screen.
 */
data class ChatUiState(
    val detail: ChatDetail? = null,
    val draft: String = "",
    val pendingAttachment: AttachmentDraft? = null,
    val replyTo: ChatMessage? = null,
    val editing: ChatMessage? = null,
    val pendingDelete: ChatMessage? = null
) {
    val messages: List<ChatMessage> get() = detail?.messages.orEmpty()
}

class ChatViewModel(
    private val repository: TelegramRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    /**
     * Read from the saved state rather than passed in, so the chat being
     * shown survives process death along with everything else here.
     */
    private val chatId: Long = requireNotNull(savedStateHandle["chatId"]) {
        "ChatViewModel needs a chatId argument"
    }

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        reload()
    }

    // ── composing ────────────────────────────────────────────────────────

    fun onDraftChange(text: String) = _uiState.update { it.copy(draft = text) }

    fun onAttachmentPicked(draft: AttachmentDraft) =
        _uiState.update { it.copy(pendingAttachment = draft) }

    fun onAttachmentCleared() = _uiState.update { it.copy(pendingAttachment = null) }

    /** Answering a message cancels an edit in progress, and vice versa. */
    fun onReplyTo(message: ChatMessage) =
        _uiState.update { it.copy(replyTo = message, editing = null) }

    fun onEdit(message: ChatMessage) =
        _uiState.update { it.copy(editing = message, replyTo = null, draft = message.text) }

    fun onComposerBannerCancelled() = _uiState.update {
        // Cancelling an edit throws the draft away too: it was the old text,
        // put there to be amended, not something the person typed.
        if (it.editing != null) it.copy(editing = null, replyTo = null, draft = "")
        else it.copy(replyTo = null)
    }

    fun onSend() {
        val state = _uiState.value
        val text = state.draft
        val attachment = state.pendingAttachment
        if (text.isBlank() && attachment == null) return
        val amending = state.editing?.id
        val answering = state.replyTo?.id

        viewModelScope.launch {
            if (amending != null) {
                repository.editMessage(chatId, amending, text)
            } else {
                repository.sendMessage(chatId, text, attachment, answering)
            }
            _uiState.update {
                it.copy(draft = "", pendingAttachment = null, replyTo = null, editing = null)
            }
            reload()
        }
    }

    // ── deleting ─────────────────────────────────────────────────────────

    fun onDeleteRequested(message: ChatMessage) =
        _uiState.update { it.copy(pendingDelete = message) }

    fun onDeleteDismissed() = _uiState.update { it.copy(pendingDelete = null) }

    fun onDeleteConfirmed(message: ChatMessage, forEveryone: Boolean) {
        _uiState.update { it.copy(pendingDelete = null) }
        viewModelScope.launch {
            repository.deleteMessage(chatId, message.id, forEveryone)
            _uiState.update { state ->
                // A banner pointing at a message that no longer exists would
                // send the next line into nothing.
                state.copy(
                    replyTo = state.replyTo?.takeIf { it.id != message.id },
                    editing = state.editing?.takeIf { it.id != message.id },
                    draft = if (state.editing?.id == message.id) "" else state.draft
                )
            }
            reload()
        }
    }

    private fun reload() {
        viewModelScope.launch {
            val detail = repository.openChat(chatId)
            _uiState.update { it.copy(detail = detail) }
        }
    }
}
