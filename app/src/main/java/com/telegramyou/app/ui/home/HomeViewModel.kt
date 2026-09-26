package com.telegramyou.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.ChatPreview
import com.telegramyou.app.telegram.model.MessageHit
import com.telegramyou.app.telegram.model.PostSearch
import com.telegramyou.app.telegram.model.SearchChats
import com.telegramyou.app.telegram.model.SearchScope
import com.telegramyou.app.telegram.model.mergeChatResults
import com.telegramyou.app.settings.InMemoryQueryHistory
import com.telegramyou.app.settings.QueryHistory
import com.telegramyou.app.telegram.model.StoryItem
import com.telegramyou.app.telegram.model.TelegramUser
import com.telegramyou.app.ui.profile.ProfileDraft
import com.telegramyou.app.ui.profile.ProfileField
import com.telegramyou.app.ui.profile.ProfileProblem
import com.telegramyou.app.ui.profile.canSaveProfile
import com.telegramyou.app.ui.profile.profileChanges
import com.telegramyou.app.ui.profile.profileDraftOf
import com.telegramyou.app.ui.profile.validateProfile
import com.telegramyou.app.ui.failureText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
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
    /**
     * The main list: everything not in the archive, under the chosen folder.
     *
     * Filtered here rather than in the screen so that there is one answer to
     * "which chats are on screen" — the row-drawing code never learns that
     * folders exist.
     */
    val chats: List<ChatPreview> = emptyList(),
    /**
     * The tabs above the list, empty for an account with no folders.
     *
     * Empty means no strip at all rather than a lone "All": see `folderTabs`.
     */
    val folderTabs: List<FolderTab> = emptyList(),
    /** Which tab is chosen; null is All. */
    val selectedFolderId: Int? = null,
    /** How many chats have something unread, per tab, in the tabs' order. */
    val folderUnread: List<Int> = emptyList(),
    /**
     * Every tab's list, in the tabs' order — [chats] is one of them.
     *
     * All of them rather than only the chosen one, because the folders are
     * pages you swipe between: the neighbour is on screen for the length of
     * the swipe, and a page that only filled in once it had settled would
     * slide in blank.
     */
    val folderChats: List<List<ChatPreview>> = emptyList(),
    val archivedChats: List<ChatPreview> = emptyList(),
    /**
     * What the archive entry row says, or null when there is no row.
     *
     * Computed in :core with tests rather than in the screen, so a caller
     * cannot draw an entry for an empty archive by forgetting to check.
     *
     * Kept whichever folder is chosen, and drawn on the All page only. The
     * archive is a different list, not a chat that could be in "People" —
     * but All is still a page beside the chosen one, visible for the length
     * of a swipe, and it has to arrive with its row already in place.
     */
    val archiveSummary: String? = null,
    val stories: List<StoryItem> = emptyList(),
    val isRefreshing: Boolean = false,
    val search: SearchState = SearchState(),
    val profile: ProfileUiState = ProfileUiState(),
    val compose: ComposeState = ComposeState(),
    /**
     * What the last refused request has to say — a mute, a pin, an archive —
     * for a snackbar; null once shown. See ChatUiState.errorMessage for why
     * this exists at all.
     */
    val errorMessage: String? = null
)

/**
 * The contact picker behind the pencil.
 *
 * [openChatId] is a one-shot: the screen navigates to it and calls back to
 * clear it. Without that a rotation would reopen the conversation, because
 * the state that caused the navigation would still be there.
 */
data class ComposeState(
    val sheetOpen: Boolean = false,
    val contacts: List<TelegramUser> = emptyList(),
    val isLoading: Boolean = false,
    val openChatId: Long? = null
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
    /** The tab under the field, once something is typed. */
    val scope: SearchScope = SearchScope.All,
    /** Chats by name: the account's own first, then public ones. */
    val chats: SearchChats = SearchChats(),
    val messages: List<MessageHit> = emptyList(),
    val isSearching: Boolean = false,
    /**
     * Public posts for [query], or null until they have been asked for.
     *
     * Asked for only on purpose — the Posts tab's button or the keyboard's
     * search key — never while typing: Telegram allows a few free post
     * searches a day, and a debounce would spend one on every pause.
     */
    val posts: PostSearch? = null,
    val isSearchingPosts: Boolean = false,
    // What the empty field shows: the search section's own front page.
    val topPeople: List<ChatPreview> = emptyList(),
    val recentChats: List<ChatPreview> = emptyList(),
    val recentQueries: List<String> = emptyList(),
    val recommended: List<ChatPreview> = emptyList()
) {
    /** The account's own chats that matched, in the server's order. */
    val results: List<ChatPreview> get() = chats.mine

    /** Used to decide whether the screen may say nothing was found. */
    val isEmpty: Boolean get() = chats.isEmpty && messages.isEmpty()

    /** What the current tab shows of the chats found. */
    val visibleChats: SearchChats get() = chats.filteredBy(scope)
}

class HomeViewModel(
    private val repository: TelegramRepository,
    /** What was typed into search before; this device's, not the account's. */
    private val queryHistory: QueryHistory = InMemoryQueryHistory(),
    /**
     * Where the list is filtered, split into folders and counted. Off the
     * main thread: it runs on every change to any chat, over every chat,
     * once per folder tab, and the frame being drawn should not wait on it.
     * Tests hand in their own dispatcher so they stay in step.
     */
    private val work: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    /** Set here rather than in the screen, so a rotation mid-refresh keeps it. */
    private val refreshing = MutableStateFlow(false)

    private val search = MutableStateFlow(SearchState())

    /** Cancelled on each keystroke, which is what makes the delay a debounce. */
    private var searchJob: Job? = null

    /** A post search in flight; see [SearchState.posts]. */
    private var postJob: Job? = null

    private val profileEditing = MutableStateFlow(ProfileEditing())

    private val compose = MutableStateFlow(ComposeState())

    /** The last refusal not yet shown; see [HomeUiState.errorMessage]. */
    private val failure = MutableStateFlow<String?>(null)

    /**
     * The chosen folder, null for All.
     *
     * Held here rather than in the screen so that switching tabs survives a
     * rotation, and because the filtering happens here too — the screen sends
     * the tap and draws what comes back.
     */
    private val folderSelection = MutableStateFlow<Int?>(null)

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
            chats = chats.filterNot { it.isArchived },
            archivedChats = chats.filter { it.isArchived },
            archiveSummary = archiveSummary(
                chats,
                isArchived = { it.isArchived },
                unreadCount = { it.unreadCount }
            ),
            stories = stories,
            isRefreshing = isRefreshing,
            search = searchState
        )
    }.combine(
        combine(repository.observeFolders(), folderSelection) { folders, requested ->
            folders to selectedFolder(folders, { it.id }, requested)
        }
    ) { home, (folders, selected) ->
        val tabs = folderTabs(folders, { it.id }, { it.title })
        home.copy(
            chats = chatsInFolder(home.chats, selected) { it.folderIds },
            folderTabs = tabs,
            selectedFolderId = selected,
            folderUnread = tabs.map { tab ->
                folderUnreadChats(home.chats, tab.id, { it.folderIds }, { it.unreadCount })
            },
            folderChats = tabs.map { tab -> chatsInFolder(home.chats, tab.id) { it.folderIds } }
        )
    }.combine(profileEditing) { home, editing ->
        home.copy(profile = profileState(home.me, editing))
    }.combine(compose) { home, composeState ->
        home.copy(compose = composeState)
    }.combine(failure) { home, message ->
        home.copy(errorMessage = message)
    }.flowOn(work).stateIn(
        scope = viewModelScope,
        // Kept briefly past the last subscriber: a rotation unsubscribes and
        // resubscribes, and rebuilding the list for that is wasted work.
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

    /**
     * Switches tabs.
     *
     * Nothing is cleared: the search field, the archive row and everything
     * else on the screen mean the same under any folder, and a tap that reset
     * them would be a tap that undid work.
     */
    fun onFolderSelected(folderId: Int?) {
        folderSelection.value = folderId
    }

    // ── search ───────────────────────────────────────────────────────────

    init {
        viewModelScope.launch {
            queryHistory.queries.collect { queries ->
                search.update { it.copy(recentQueries = queries) }
            }
        }
    }

    fun onSearchExpandedChange(expanded: Boolean) {
        searchJob?.cancel()
        postJob?.cancel()
        search.value = if (expanded) {
            search.value.copy(expanded = true)
        } else {
            // Closing clears it. A query left behind would silently filter the
            // list the next time the field is opened.
            SearchState(recentQueries = queryHistory.queries.value)
        }
        if (expanded) loadSearchFrontPage()
    }

    /**
     * The empty field's page: people, chats found before, channels to try.
     *
     * Fetched each time search opens rather than kept, because every part
     * of it changes as the account is used — and each is a local answer from
     * TDLib, except the recommendations, which may take a moment and simply
     * appear when they arrive.
     */
    private fun loadSearchFrontPage() {
        viewModelScope.launch {
            val people = runCatching { repository.topPeople() }.getOrDefault(emptyList())
            val recent = runCatching { repository.recentlyFoundChats() }.getOrDefault(emptyList())
            search.update { it.copy(topPeople = people, recentChats = recent) }
            val recommended = runCatching { repository.recommendedChannels() }.getOrDefault(emptyList())
            search.update { it.copy(recommended = recommended) }
        }
    }

    fun onSearchScopeChange(scope: SearchScope) {
        search.update { it.copy(scope = scope) }
    }

    /**
     * The keyboard's search key: the query is kept in the history, and on
     * the Posts tab it is what spends a post search.
     */
    fun onSearchSubmit() {
        val query = search.value.query
        if (query.isBlank()) return
        queryHistory.remember(query)
        if (search.value.scope == SearchScope.Posts) onSearchPosts()
    }

    /** Public posts for the current query; see [SearchState.posts]. */
    fun onSearchPosts() {
        val query = search.value.query
        if (query.isBlank()) return
        postJob?.cancel()
        search.update { it.copy(isSearchingPosts = true) }
        postJob = viewModelScope.launch {
            val found = runCatching { repository.searchPublicPosts(query) }.getOrDefault(PostSearch())
            search.update {
                // A query changed while this was on its way has moved on.
                if (it.query != query) it else it.copy(posts = found, isSearchingPosts = false)
            }
        }
    }

    /**
     * A result was opened. The chat joins Telegram's list of chats found,
     * and the words that found it join ours.
     */
    fun onSearchResultOpened(chatId: Long) {
        queryHistory.remember(search.value.query)
        viewModelScope.launch { runCatching { repository.addRecentlyFoundChat(chatId) } }
    }

    fun onRecentQueryPicked(query: String) = onSearchQueryChange(query)

    fun onRecentQueriesCleared() = queryHistory.clear()

    fun onRecentChatRemoved(chatId: Long) {
        search.update { state -> state.copy(recentChats = state.recentChats.filterNot { it.id == chatId }) }
        viewModelScope.launch { runCatching { repository.removeRecentlyFoundChat(chatId) } }
    }

    fun onRecentChatsCleared() {
        search.update { it.copy(recentChats = emptyList()) }
        viewModelScope.launch { runCatching { repository.clearRecentlyFoundChats() } }
    }

    fun onSearchQueryChange(query: String) {
        searchJob?.cancel()
        postJob?.cancel()
        if (query.isBlank()) {
            search.value = search.value.copy(
                query = query,
                chats = SearchChats(),
                messages = emptyList(),
                posts = null,
                isSearching = false,
                isSearchingPosts = false
            )
            return
        }
        search.value = search.value.copy(
            query = query,
            isSearching = true,
            posts = null,
            isSearchingPosts = false
        )
        searchJob = viewModelScope.launch {
            // Long enough that typing a word is one request rather than five,
            // short enough that it does not feel like waiting.
            delay(SEARCH_DEBOUNCE_MS)
            // Both halves of one search, so the screen never shows chats
            // while still waiting on messages and looks half-finished.
            val chats = repository.searchChats(query)
            // Public chats the account is not in, which is what makes this a
            // way to find Telegram and not only one's own list.
            val global = runCatching { repository.searchPublicChats(query) }.getOrDefault(emptyList())
            val messages = repository.searchMessages(query)
            search.value = search.value.copy(
                chats = mergeChatResults(chats, global),
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
        viewModelScope.launch { attempt("Could not sign out") { repository.logout() } }
    }

    /**
     * Runs one request and turns a refusal into [HomeUiState.errorMessage]
     * rather than an exception out of the scope, which is a crash.
     * Cancellation passes through: it is the screen leaving, not a failure.
     */
    private suspend fun attempt(action: String, request: suspend () -> Unit): Boolean =
        try {
            request()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failure.value = failureText(action, e.message)
            false
        }

    fun onErrorShown() {
        failure.value = null
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
        viewModelScope.launch {
            attempt(if (muted) "Could not mute" else "Could not unmute") {
                repository.setChatMuted(chatId, muted)
            }
        }
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

    /** A new profile photo, picked from the gallery. */
    fun onProfilePhotoPicked(uri: String) {
        viewModelScope.launch {
            attempt("Could not change the photo") { repository.setProfilePhoto(uri) }
        }
    }

    /** Called once the screen has shown the failure, so it is not shown twice. */
    fun onProfileErrorShown() {
        profileEditing.update { it.copy(errorMessage = null) }
    }

    /**
     * Pins a chat to the top of the list, or unpins it.
     *
     * Nothing is written here, for the reason [onMutedChange] gives: the list
     * is a flow the client owns. Pinning also reorders it, so an optimistic
     * copy would have to guess the new order as well as the new flag.
     */
    fun onPinnedChange(chatId: Long, pinned: Boolean) {
        viewModelScope.launch {
            attempt(if (pinned) "Could not pin" else "Could not unpin") {
                repository.setChatPinned(chatId, pinned)
            }
        }
    }

    /** Clears a chat's unread badge without opening it. */
    fun onMarkRead(chatId: Long) {
        viewModelScope.launch {
            attempt("Could not mark as read") { repository.markChatRead(chatId) }
        }
    }

    // ── composing ────────────────────────────────────────────────────────

    /**
     * Opens the contact picker and fetches what goes in it.
     *
     * Fetched on opening rather than held from startup: contacts change
     * rarely and are read once in a while, so keeping them current all the
     * time would be a subscription paying for a button nobody has pressed.
     */
    fun onComposeOpen() {
        compose.value = ComposeState(sheetOpen = true, isLoading = true)
        viewModelScope.launch {
            var list = emptyList<TelegramUser>()
            attempt("Could not load contacts") { list = repository.contacts() }
            compose.update { it.copy(contacts = list, isLoading = false) }
        }
    }

    fun onComposeDismiss() {
        compose.value = ComposeState()
    }

    /**
     * Opens the conversation with a contact, creating it if there is not one.
     *
     * The sheet closes on the answer rather than on the tap, so a slow create
     * does not leave the person looking at a chat list wondering whether the
     * tap registered.
     */
    fun onContactPicked(userId: Long) {
        viewModelScope.launch {
            compose.update { it.copy(isLoading = true) }
            var chatId: Long? = null
            attempt("Could not open the chat") { chatId = repository.openPrivateChat(userId) }
            compose.value = if (chatId != null) {
                ComposeState(openChatId = chatId)
            } else {
                compose.value.copy(isLoading = false)
            }
        }
    }

    /** Called once the screen has navigated, so a rotation does not repeat it. */
    /**
     * Saved Messages: Telegram's chat with oneself, reached the same way as a
     * chat with anyone — by the account's own user id — and navigated to
     * through the compose state like any chat the picker opens.
     */
    fun onOpenSavedMessages() {
        val me = repository.observeAuth().value.me ?: return
        onContactPicked(me.id)
    }

    fun onComposeNavigated() {
        compose.value = ComposeState()
    }

    /**
     * Moves a chat into the archive, or back out of it.
     *
     * Nothing is written here for the reason the other two give: the list is
     * a flow the client owns, and archiving changes which list a chat is in
     * rather than a field on it.
     */
    fun onArchivedChange(chatId: Long, archived: Boolean) {
        viewModelScope.launch {
            attempt(if (archived) "Could not archive" else "Could not unarchive") {
                repository.setChatArchived(chatId, archived)
            }
        }
    }

    /** Lists that answered "nothing more", by folder id; null is the main list. */
    private val exhaustedLists = mutableSetOf<Int?>()
    private var loadingMore = false

    /**
     * The list for [folderId] is near its end; fetch the next chats.
     *
     * Guarded twice, like paging older messages: a list resting at its end
     * reports that on every frame, and without the guards that is a request
     * per frame — forever, once the list has run out.
     */
    fun onListEndReached(folderId: Int?) {
        if (loadingMore || folderId in exhaustedLists) return
        loadingMore = true
        viewModelScope.launch {
            var more = false
            attempt("Could not load more chats") { more = repository.loadMoreChats(folderId) }
            if (!more) exhaustedLists += folderId
            loadingMore = false
        }
    }

    fun refresh() {
        // A refresh may bring new chats to the bottom of any list, so every
        // list is worth asking again.
        exhaustedLists.clear()
        viewModelScope.launch {
            refreshing.value = true
            try {
                attempt("Could not refresh") { repository.refreshChats() }
            } finally {
                refreshing.value = false
            }
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
    }
}
