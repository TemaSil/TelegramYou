package com.telegramyou.app.ui.newchat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.InviteLinkPreview
import com.telegramyou.app.telegram.model.TelegramUser
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The two ways a chat that is not in the list yet gets into it: making one,
 * and being let into somebody else's.
 *
 * One holder for both screens because they end the same way — with a chat id
 * to open — and share nothing else worth splitting over. Each screen gets its
 * own instance from its own back-stack entry, so a half-typed group name does
 * not follow anybody onto the join screen.
 */
data class NewChatUiState(
    val title: String = "",
    val description: String = "",
    val contacts: List<TelegramUser> = emptyList(),
    val selected: Set<Long> = emptySet(),
    val isLoadingContacts: Boolean = true,
    /** True while a create or a join is with the server. */
    val isWorking: Boolean = false,
    /** What the server said when it refused, verbatim. */
    val errorMessage: String? = null,
    /**
     * Set once there is a chat to go to. A one-shot: the screen navigates and
     * clears it, or a rotation would open the conversation a second time.
     */
    val openChatId: Long? = null,
    val link: String = "",
    val preview: InviteLinkPreview? = null,
    val isCheckingLink: Boolean = false,
    /** Under the link field: not a link at all, or a link to nowhere. */
    val linkProblem: String? = null
) {
    val titleProblem: String? get() = titleProblem(title)
    val canCreate: Boolean get() = canCreate(title, description) && !isWorking
}

class NewChatViewModel(
    private val repository: TelegramRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewChatUiState())
    val uiState: StateFlow<NewChatUiState> = _uiState.asStateFlow()

    /** Cancelled on each keystroke in the link field, which is the debounce. */
    private var linkJob: Job? = null

    init {
        viewModelScope.launch {
            val contacts = runCatching { repository.contacts() }.getOrDefault(emptyList())
            _uiState.update { it.copy(contacts = contacts, isLoadingContacts = false) }
        }
    }

    fun onTitleChange(title: String) = _uiState.update { it.copy(title = title) }

    fun onDescriptionChange(text: String) = _uiState.update { it.copy(description = text) }

    fun onMemberToggled(userId: Long) = _uiState.update { state ->
        state.copy(
            selected = if (userId in state.selected) {
                state.selected - userId
            } else {
                state.selected + userId
            }
        )
    }

    /**
     * Creates the group or channel the screen is for.
     *
     * Members in the order they appear in the list rather than the order they
     * were ticked: the server adds them one by one and the resulting "added"
     * lines in the conversation read as a list, not as a history of taps.
     */
    fun create(kind: NewChatKind) {
        val state = _uiState.value
        if (!state.canCreate) return
        _uiState.update { it.copy(isWorking = true, errorMessage = null) }
        viewModelScope.launch {
            val result = runCatching {
                when (kind) {
                    NewChatKind.Group -> repository.createGroup(
                        state.title,
                        state.contacts.map { it.id }.filter { it in state.selected }
                    )
                    NewChatKind.Channel -> repository.createChannel(
                        state.title,
                        state.description
                    )
                }
            }
            _uiState.update {
                it.copy(
                    isWorking = false,
                    openChatId = result.getOrNull(),
                    errorMessage = result.exceptionOrNull()?.message
                        ?: if (result.isFailure) "Could not create it" else null
                )
            }
        }
    }

    /**
     * The link as typed, checked once typing pauses.
     *
     * Parsed here first, so a half-pasted link or a username says what it is
     * straight away without a round trip, and only something shaped like an
     * invite reaches the server.
     */
    fun onLinkChange(text: String) {
        linkJob?.cancel()
        val canonical = canonicalInviteLink(text)
        _uiState.update {
            it.copy(
                link = text,
                preview = null,
                isCheckingLink = canonical != null,
                linkProblem = if (canonical == null && text.isNotBlank()) {
                    "Not an invite link — they look like t.me/+…"
                } else {
                    null
                }
            )
        }
        if (canonical == null) return
        linkJob = viewModelScope.launch {
            delay(LINK_DEBOUNCE_MS)
            val preview = runCatching { repository.checkInviteLink(canonical) }.getOrNull()
            _uiState.update {
                it.copy(
                    preview = preview,
                    isCheckingLink = false,
                    linkProblem = if (preview == null) {
                        "This link has expired or does not lead anywhere"
                    } else {
                        null
                    }
                )
            }
        }
    }

    /** Joins, or opens the chat if this account is already in it. */
    fun join() {
        val preview = _uiState.value.preview ?: return
        preview.joinedChatId?.let { id ->
            _uiState.update { it.copy(openChatId = id) }
            return
        }
        _uiState.update { it.copy(isWorking = true, errorMessage = null) }
        viewModelScope.launch {
            val result = runCatching { repository.joinByInviteLink(preview.link) }
            _uiState.update {
                it.copy(
                    isWorking = false,
                    openChatId = result.getOrNull(),
                    errorMessage = result.exceptionOrNull()?.message
                        ?: if (result.isFailure) "Could not join" else null
                )
            }
        }
    }

    fun onNavigated() = _uiState.update { it.copy(openChatId = null) }

    fun onErrorShown() = _uiState.update { it.copy(errorMessage = null) }

    private companion object {
        /** Long enough that a paste is one request, short enough not to feel like waiting. */
        const val LINK_DEBOUNCE_MS = 400L
    }
}
