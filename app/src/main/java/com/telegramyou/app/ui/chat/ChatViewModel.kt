package com.telegramyou.app.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.telegramyou.app.navigation.Route
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.AttachmentDraft
import com.telegramyou.app.telegram.model.ChatDetail
import com.telegramyou.app.telegram.model.ChatMessage
import com.telegramyou.app.telegram.model.ChatPreview
import com.telegramyou.app.telegram.model.toggleReaction
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Searching inside one conversation.
 *
 * [results] are whole messages rather than ids: a hit may be older than
 * anything loaded, and the result row has to render without the conversation
 * having reached it.
 */
data class ChatSearchState(
    val isOpen: Boolean = false,
    val query: String = "",
    val results: List<ChatMessage> = emptyList(),
    val isSearching: Boolean = false
) {
    /** A query that found nothing, as opposed to one not yet typed. */
    val isEmpty: Boolean get() = query.isNotBlank() && !isSearching && results.isEmpty()
}

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
    val pendingDelete: ChatMessage? = null,
    /** Older messages fetched by scrolling, oldest first, ahead of [detail]. */
    val olderMessages: List<ChatMessage> = emptyList(),
    val isLoadingOlder: Boolean = false,
    /**
     * False once the client has answered a request with nothing, which is the
     * only way it says a conversation has no more history.
     */
    val hasMoreOlder: Boolean = true,
    /** Non-null while the reaction picker is open, naming what it reacts to. */
    val reactingTo: ChatMessage? = null,
    /** What this chat permits, fetched once — see TelegramMessages. */
    val availableReactions: List<String> = emptyList(),
    /** Empty until someone chooses Select; non-empty puts the toolbar up. */
    val selection: MessageSelection = MessageSelection(),
    /** True while the confirmation for deleting the selection is on screen. */
    val confirmingSelectionDelete: Boolean = false,
    /** Search inside this conversation; see ChatSearchState. */
    val search: ChatSearchState = ChatSearchState(),
    /** True while the "what would you like to attach" sheet is up. */
    val attachmentSheetOpen: Boolean = false,
    /** True while the chat picker for forwarding a selection is up. */
    val forwardSheetOpen: Boolean = false,
    /** Somewhere to forward to; every chat but this one. */
    val forwardTargets: List<ChatPreview> = emptyList()
) {
    val messages: List<ChatMessage> get() = olderMessages + detail?.messages.orEmpty()

    val selectedMessages: List<ChatMessage> get() = messages.filter { it.id in selection }

    /**
     * What the toolbar may offer, computed rather than stored: it is a
     * function of the selection and the messages, and storing it means two
     * things that can disagree. Named apart from the `selectionActions`
     * function it calls so neither reads as the other.
     */
    val availableActions: SelectionActions get() = selectionActions(selectedMessages)
}

class ChatViewModel(
    private val repository: TelegramRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    /**
     * Read from the saved state rather than passed in, so the chat being
     * shown survives process death along with everything else here.
     */
    private val chatId: Long = requireNotNull(savedStateHandle[Route.Chat.ARG_CHAT_ID]) {
        "ChatViewModel needs a ${Route.Chat.ARG_CHAT_ID} argument"
    }

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        reload()
        viewModelScope.launch {
            // Forwarding needs somewhere to forward to, and the chat list is
            // already a flow the repository keeps current — asking for it per
            // tap would put a request between the button and the sheet.
            repository.observeChats().collect { chats ->
                _uiState.update { state ->
                    // This chat is excluded: forwarding a message into the
                    // conversation it came from is a copy of itself.
                    state.copy(forwardTargets = chats.filter { it.id != chatId })
                }
            }
        }
    }

    // ── composing ────────────────────────────────────────────────────────

    fun onDraftChange(text: String) = _uiState.update { it.copy(draft = text) }

    fun onAttachmentSheetOpenChange(open: Boolean) =
        _uiState.update { it.copy(attachmentSheetOpen = open) }

    fun onAttachmentPicked(draft: AttachmentDraft) =
        // Closing here rather than where the picker is launched: the sheet has
        // to stay up while the system picker is in front of it, or coming back
        // from a cancelled pick lands on the composer with nothing explained.
        _uiState.update { it.copy(pendingAttachment = draft, attachmentSheetOpen = false) }

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

    // ── reacting ─────────────────────────────────────────────────────────

    fun onReactionsRequested(message: ChatMessage) =
        _uiState.update { it.copy(reactingTo = message) }

    fun onReactionPickerDismissed() = _uiState.update { it.copy(reactingTo = null) }

    /**
     * Redraws the row immediately and tells the server afterwards.
     *
     * No reload follows. The optimistic result is what the backends apply to
     * their own copy as well, so re-opening the chat agrees with it; reloading
     * here would replace a correct row with an identical one and flicker for
     * nothing.
     */
    fun onReactionToggled(message: ChatMessage, emoji: String) {
        _uiState.update { state ->
            state.copy(reactingTo = null).mapMessage(message.id) {
                it.copy(reactions = toggleReaction(it.reactions, emoji))
            }
        }
        viewModelScope.launch {
            repository.toggleReaction(chatId, message.id, emoji)
        }
    }

    /**
     * Rewrites one message wherever it happens to live.
     *
     * A message is in [ChatUiState.detail] or in [ChatUiState.olderMessages]
     * depending on whether it arrived with the chat or was paged in, and a
     * caller that has one in hand has no reason to know which.
     */
    private fun ChatUiState.mapMessage(
        messageId: Long,
        transform: (ChatMessage) -> ChatMessage
    ): ChatUiState = copy(
        // let rather than ?.copy(detail.messages): a safe call smart-casts its
        // receiver, not the same property named again in the arguments.
        detail = detail?.let { chat ->
            chat.copy(
                messages = chat.messages.map {
                    if (it.id == messageId) transform(it) else it
                }
            )
        },
        olderMessages = olderMessages.map { if (it.id == messageId) transform(it) else it }
    )

    // ── searching ────────────────────────────────────────────────────────

    /** Cancelled on each keystroke, which is what makes the delay a debounce. */
    private var searchJob: Job? = null

    fun onSearchOpenChange(open: Boolean) {
        searchJob?.cancel()
        // Closing clears it: a query left behind would still be filtering the
        // next time the field is opened, with nothing on screen saying so.
        _uiState.update {
            it.copy(search = if (open) ChatSearchState(isOpen = true) else ChatSearchState())
        }
    }

    fun onSearchQueryChange(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update {
                it.copy(
                    search = it.search.copy(
                        query = query,
                        results = emptyList(),
                        isSearching = false
                    )
                )
            }
            return
        }
        _uiState.update { it.copy(search = it.search.copy(query = query, isSearching = true)) }
        searchJob = viewModelScope.launch {
            // Long enough that typing a word is one request rather than five,
            // short enough that it does not feel like waiting. Same figure as
            // the chat list's search, for the same reason.
            delay(SEARCH_DEBOUNCE_MS)
            val hits = repository.searchChatMessages(chatId, query)
            _uiState.update {
                it.copy(search = it.search.copy(results = hits, isSearching = false))
            }
        }
    }

    // ── selecting ────────────────────────────────────────────────────────

    /**
     * Adds or removes one message.
     *
     * The same call starts a selection and ends it: the first message chosen
     * raises the toolbar and deselecting the last one puts it away, so there
     * is no separate mode to enter or leave.
     */
    fun onSelectionToggled(message: ChatMessage) =
        _uiState.update { it.copy(selection = it.selection.toggle(message.id)) }

    fun onSelectionCleared() =
        _uiState.update { it.copy(selection = it.selection.cleared()) }

    fun onForwardRequested() =
        _uiState.update { it.copy(forwardSheetOpen = true) }

    fun onForwardDismissed() =
        _uiState.update { it.copy(forwardSheetOpen = false) }

    /**
     * Sends the selection on, then puts both the sheet and the selection away.
     *
     * The conversation is not reloaded: the messages went somewhere else, and
     * nothing about this one changed.
     */
    fun onForwardTo(target: ChatPreview) {
        val ids = _uiState.value.selection.ids.toList()
        if (ids.isEmpty()) return
        _uiState.update {
            it.copy(forwardSheetOpen = false, selection = it.selection.cleared())
        }
        viewModelScope.launch {
            repository.forwardMessages(chatId, ids, target.id)
        }
    }

    fun onSelectionDeleteRequested() =
        _uiState.update { it.copy(confirmingSelectionDelete = true) }

    fun onSelectionDeleteDismissed() =
        _uiState.update { it.copy(confirmingSelectionDelete = false) }

    /**
     * Deletes everything selected, then clears it.
     *
     * One request per message: TDLib takes a list, but the client interface
     * takes one id, and widening it for this would push the batching into both
     * backends for the sake of one caller.
     */
    fun onSelectionDeleted(forEveryone: Boolean) {
        val targets = _uiState.value.selectedMessages
        if (targets.isEmpty()) return
        _uiState.update {
            it.copy(selection = it.selection.cleared(), confirmingSelectionDelete = false)
        }
        viewModelScope.launch {
            targets.forEach { repository.deleteMessage(chatId, it.id, forEveryone) }
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

    /**
     * Asks for the page before the oldest message on screen.
     *
     * Guarded on both flags: a list scrolled to the top fires this on every
     * frame, and without the guard that is a request per frame — and then a
     * request per frame forever once the history runs out.
     */
    fun onLoadOlder() {
        val state = _uiState.value
        if (state.isLoadingOlder || !state.hasMoreOlder) return
        val oldest = state.messages.firstOrNull() ?: return

        _uiState.update { it.copy(isLoadingOlder = true) }
        viewModelScope.launch {
            val older = repository.loadOlderMessages(chatId, oldest.id)
            _uiState.update { current ->
                current.copy(
                    olderMessages = older + current.olderMessages,
                    isLoadingOlder = false,
                    hasMoreOlder = older.isNotEmpty()
                )
            }
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
    }

    private fun reload() {
        viewModelScope.launch {
            val detail = repository.openChat(chatId)
            // Asked for once per load rather than per tap: a chat's permitted
            // reactions do not change while it is open, and the picker has to
            // open without waiting for a request.
            val reactions = repository.availableReactions(chatId)
            // The window from openChat is fresh, so anything paged in before
            // it is discarded rather than left to duplicate or contradict it.
            _uiState.update {
                val reloaded = it.copy(
                    detail = detail,
                    olderMessages = emptyList(),
                    hasMoreOlder = true,
                    availableReactions = reactions
                )
                // A selected message can be gone by the time the chat reloads —
                // deleted here or by someone else. Keeping its id would leave
                // the toolbar counting something that cannot be acted on.
                reloaded.copy(selection = it.selection.retaining(reloaded.messages))
            }
        }
    }
}
