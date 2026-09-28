package com.telegramyou.app.ui.people

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.telegramyou.app.navigation.Route
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.PersonProfile
import com.telegramyou.app.telegram.model.TelegramUser
import com.telegramyou.app.telegram.model.firstNameOf
import com.telegramyou.app.ui.failureText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The three screens about other people share one way of failing — a line in
 * a snackbar, said once — and one way of leaving for a chat: an id the
 * screen navigates to and then clears, so a rotation cannot repeat it.
 */
private suspend fun <S> MutableStateFlow<S>.attempt(
    failure: String,
    withMessage: S.(String) -> S,
    request: suspend () -> Unit
): Boolean = try {
    request()
    true
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    update { it.withMessage(failureText(failure, e.message)) }
    false
}

// ── somebody's profile ───────────────────────────────────────────────────

data class PersonUiState(
    val profile: PersonProfile? = null,
    val isLoading: Boolean = true,
    /** The chat to open, once "Send message" has one; cleared on arrival. */
    val openChatId: Long? = null,
    val message: String? = null
)

/**
 * Somebody by user id — a group member tapped, a blocked person — rather
 * than by chat: they may have no chat with this account at all, and
 * opening their profile should not make one.
 */
class PersonViewModel(
    private val repository: TelegramRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val userId: Long = checkNotNull(savedStateHandle[Route.Person.ARG_USER_ID])

    private val _uiState = MutableStateFlow(PersonUiState())
    val uiState: StateFlow<PersonUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            var profile: PersonProfile? = null
            _uiState.attempt("Could not load the profile", { copy(message = it) }) {
                profile = repository.person(userId)
            }
            _uiState.update { it.copy(profile = profile, isLoading = false) }
        }
    }

    fun onBlockedChange(blocked: Boolean) {
        viewModelScope.launch {
            val done = _uiState.attempt(
                if (blocked) "Could not block" else "Could not unblock",
                { copy(message = it) }
            ) { repository.setBlocked(userId, blocked) }
            if (done) _uiState.update { it.copy(profile = it.profile?.copy(isBlocked = blocked)) }
        }
    }

    /** Their chat, made if there is none yet — only now, when asked for. */
    fun onSendMessage() {
        viewModelScope.launch {
            var chatId: Long? = null
            _uiState.attempt("Could not open the chat", { copy(message = it) }) {
                chatId = repository.openPrivateChat(userId)
            }
            _uiState.update { it.copy(openChatId = chatId) }
        }
    }

    fun onChatOpened() = _uiState.update { it.copy(openChatId = null) }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }
}

// ── the block list ───────────────────────────────────────────────────────

data class BlockedUiState(
    val people: List<TelegramUser> = emptyList(),
    val isLoading: Boolean = true,
    val message: String? = null
)

class BlockedViewModel(private val repository: TelegramRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(BlockedUiState())
    val uiState: StateFlow<BlockedUiState> = _uiState.asStateFlow()

    /**
     * On every visit rather than once: a profile opened from this list can
     * unblock its person, and coming back to find them still here would be
     * the list disagreeing with the account.
     */
    fun refresh() {
        viewModelScope.launch {
            var people = emptyList<TelegramUser>()
            _uiState.attempt("Could not load blocked users", { copy(message = it) }) {
                people = repository.blockedPeople()
            }
            _uiState.update { it.copy(people = people, isLoading = false) }
        }
    }

    fun onUnblock(user: TelegramUser) {
        viewModelScope.launch {
            val done = _uiState.attempt("Could not unblock", { copy(message = it) }) {
                repository.setBlocked(user.id, blocked = false)
            }
            if (done) {
                _uiState.update { state ->
                    state.copy(
                        people = state.people.filterNot { it.id == user.id },
                        message = "${firstNameOf(user.displayName)} is unblocked"
                    )
                }
            }
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }
}

// ── contacts ─────────────────────────────────────────────────────────────

/** The add-contact dialog's fields, while it is up. */
data class ContactDraft(
    val phone: String = "+",
    val firstName: String = "",
    val lastName: String = ""
) {
    /** Enough to ask Telegram: a number of plausible length and a name. */
    val isComplete: Boolean
        get() = phone.count(Char::isDigit) >= MIN_PHONE_DIGITS && firstName.isNotBlank()

    private companion object {
        const val MIN_PHONE_DIGITS = 7
    }
}

data class ContactsUiState(
    val contacts: List<TelegramUser> = emptyList(),
    val isLoading: Boolean = true,
    /** Non-null while the add dialog is up. */
    val draft: ContactDraft? = null,
    val isSaving: Boolean = false,
    val openChatId: Long? = null,
    val message: String? = null
)

class ContactsViewModel(private val repository: TelegramRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ContactsUiState())
    val uiState: StateFlow<ContactsUiState> = _uiState.asStateFlow()

    init {
        reload()
    }

    private fun reload() {
        viewModelScope.launch {
            var contacts = emptyList<TelegramUser>()
            _uiState.attempt("Could not load contacts", { copy(message = it) }) {
                contacts = repository.contacts()
            }
            _uiState.update { it.copy(contacts = contacts, isLoading = false) }
        }
    }

    fun onContactClick(userId: Long) {
        viewModelScope.launch {
            var chatId: Long? = null
            _uiState.attempt("Could not open the chat", { copy(message = it) }) {
                chatId = repository.openPrivateChat(userId)
            }
            _uiState.update { it.copy(openChatId = chatId) }
        }
    }

    fun onAddRequested() = _uiState.update { it.copy(draft = ContactDraft()) }

    /**
     * A change to one field, applied to the draft as it is now rather than
     * a whole draft from the screen: three fields each sending their copy
     * of the draft lose each other's edits when they arrive faster than a
     * recomposition — which the emulator's test did, keeping only the last.
     */
    fun onDraftChange(change: (ContactDraft) -> ContactDraft) =
        _uiState.update { it.copy(draft = it.draft?.let(change)) }

    fun onAddDismissed() = _uiState.update { it.copy(draft = null, isSaving = false) }

    /**
     * Adds them and opens the chat with them — what somebody adding a
     * contact nearly always wants next. A number nobody has signed up with
     * keeps the dialog up and says so.
     */
    fun onAddConfirmed() {
        val draft = _uiState.value.draft ?: return
        if (!draft.isComplete || _uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            var userId: Long? = null
            val done = _uiState.attempt("Could not add the contact", { copy(message = it) }) {
                userId = repository.addContact(draft.phone, draft.firstName, draft.lastName)
            }
            val added = userId
            when {
                !done -> _uiState.update { it.copy(isSaving = false) }
                added == null -> _uiState.update {
                    it.copy(isSaving = false, message = "${draft.phone} is not on Telegram")
                }
                else -> {
                    _uiState.update { it.copy(draft = null, isSaving = false) }
                    reload()
                    onContactClick(added)
                }
            }
        }
    }

    fun onChatOpened() = _uiState.update { it.copy(openChatId = null) }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }
}
