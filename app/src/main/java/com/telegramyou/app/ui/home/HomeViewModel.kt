package com.telegramyou.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.ChatPreview
import com.telegramyou.app.telegram.model.MessageHit
import com.telegramyou.app.telegram.model.StoryItem
import com.telegramyou.app.telegram.model.TelegramUser
import com.telegramyou.app.ui.profile.ProfileDraft
import com.telegramyou.app.ui.profile.ProfileField
import com.telegramyou.app.ui.profile.ProfileProblem
import com.telegramyou.app.ui.profile.canSaveProfile
import com.telegramyou.app.ui.profile.profileChanges
import com.telegramyou.app.ui.profile.profileDraftOf
import com.telegramyou.app.ui.profile.validateProfile
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
    val search: SearchState = SearchState(),
    val profile: ProfileUiState = ProfileUiState()
)

/**
 * The profile tab's form, as the screen should draw it.
 *
 * Everything here is derived rather than stored, which is the point: the
 * screen asks no questions about whether a field is valid or whether saving is
 * worth offering, and there is no second copy of that reasoning in a
 * composable to drift from the one in `:core`.
 *
 * [draft] is the account itself until something has been typed. That is why
 * editing needs no "start editing" step and no separate screen — the fields
 * are the profile.
 */
data class ProfileUiState(
    val draft: ProfileDraft = ProfileDraft(),
    val problems: List<ProfileProblem> = emptyList(),
    val canSave: Boolean = false,
    val isSaving: Boolean = false,
    /** What the server said when it refused, verbatim. */
    val errorMessage: String? = null,
    /** Set once a save has gone through, so the screen can acknowledge it. */
    val savedCount: Int = 0
) {
    fun problemFor(field: ProfileField): String? =
        problems.firstOrNull { it.field == field }?.message
}

/** What the view model holds; [ProfileUiState] is what it hands out. */
private data class ProfileEditing(
    /** Null until something is typed, so the form follows the account. */
    val edits: ProfileDraft? = null,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val savedCount: Int = 0
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

    private val profileEditing = MutableStateFlow(ProfileEditing())

    // Two combines rather than one of six flows: the six-argument overload
    // hands back an Array<Any?> and every field would be read out of it by
    // index and cast. Chaining keeps both halves typed.
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
    }.combine(profileEditing) { home, editing ->
        home.copy(profile = profileState(home.me, editing))
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

    /**
     * Silences a chat, or stops silencing it.
     *
     * Nothing is updated here. The chat list is a flow the client owns, and
     * the row redraws when the client says so — writing an optimistic copy
     * into a list that is about to be replaced would flicker rather than feel
     * faster.
     */
    fun onMutedChange(chatId: Long, muted: Boolean) {
        viewModelScope.launch { repository.setChatMuted(chatId, muted) }
    }

    // ── profile ──────────────────────────────────────────────────────────

    /**
     * Builds the form from the account and whatever has been typed over it.
     *
     * With nothing typed the draft *is* the account, so opening the tab shows
     * the current values in editable fields and the save button is off because
     * nothing differs — not because some "editing" flag has not been set.
     */
    private fun profileState(me: TelegramUser?, editing: ProfileEditing): ProfileUiState {
        if (me == null) {
            return ProfileUiState(isSaving = editing.isSaving, errorMessage = editing.errorMessage)
        }
        val draft = editing.edits ?: profileDraftOf(me)
        return ProfileUiState(
            draft = draft,
            problems = validateProfile(draft),
            canSave = canSaveProfile(me, draft) && !editing.isSaving,
            isSaving = editing.isSaving,
            errorMessage = editing.errorMessage,
            savedCount = editing.savedCount
        )
    }

    fun onProfileDraftChange(draft: ProfileDraft) {
        // The previous error goes with the next keystroke. Leaving "username is
        // already taken" under a field being retyped says it about the new text,
        // which nothing has checked.
        profileEditing.update { it.copy(edits = draft, errorMessage = null) }
    }

    /**
     * Sends what changed, then re-reads the account.
     *
     * Only the changed fields, because TDLib takes the three separately and a
     * field sent back unchanged is a round trip that can fail for no reason.
     * The edits are dropped afterwards rather than kept: the form then follows
     * the account again, so what is on screen is what the server accepted
     * rather than what was typed at it.
     */
    fun saveProfile() {
        val me = repository.observeAuth().value.me ?: return
        val draft = profileEditing.value.edits ?: return
        if (!canSaveProfile(me, draft)) return

        viewModelScope.launch {
            profileEditing.update { it.copy(isSaving = true, errorMessage = null) }
            try {
                val changed = profileChanges(me, draft)
                // A name is one call for both halves, so either half changing
                // sends both — which is also what keeps them consistent.
                if (ProfileField.FirstName in changed || ProfileField.LastName in changed) {
                    repository.setName(draft.firstName.trim(), draft.lastName.trim())
                }
                if (ProfileField.Bio in changed) {
                    repository.setBio(draft.bio.trim())
                }
                // Last, deliberately. It is the one that gets refused, and a
                // refusal after the others have landed leaves the account in a
                // state the screen can still explain.
                if (ProfileField.Username in changed) {
                    repository.setUsername(draft.username.trim())
                }
                repository.refreshMe()
                profileEditing.update {
                    ProfileEditing(savedCount = it.savedCount + 1)
                }
            } catch (e: Throwable) {
                profileEditing.update {
                    it.copy(
                        isSaving = false,
                        // The server's own words. A sentence invented here
                        // would have to guess which of the three calls failed.
                        errorMessage = e.message ?: "Could not save your profile"
                    )
                }
            }
        }
    }

    /** Called once the screen has shown the failure, so it is not shown twice. */
    fun onProfileErrorShown() {
        profileEditing.update { it.copy(errorMessage = null) }
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
