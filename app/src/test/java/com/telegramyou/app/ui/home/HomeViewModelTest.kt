package com.telegramyou.app.ui.home

import com.telegramyou.app.telegram.FakeTelegramClient
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.ChatFolder
import com.telegramyou.app.telegram.model.ChatMessage
import com.telegramyou.app.telegram.model.ChatPreview
import com.telegramyou.app.telegram.model.PostSearch
import com.telegramyou.app.telegram.model.SearchScope
import com.telegramyou.app.telegram.model.MessageHit
import com.telegramyou.app.telegram.model.AuthUiState
import com.telegramyou.app.telegram.model.TelegramUser
import com.telegramyou.app.ui.profile.profileDraftOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun installDispatcher() = Dispatchers.setMain(dispatcher)

    @After
    fun removeDispatcher() = Dispatchers.resetMain()

    private fun chat(id: Long, title: String) =
        ChatPreview(id = id, title = title, lastMessage = "", timestampLabel = "")

    private fun message(id: Long, text: String) = ChatMessage(
        id = id,
        chatId = id,
        text = text,
        isOutgoing = false,
        timeLabel = ""
    )

    private fun TestScope.viewModel(): Pair<HomeViewModel, FakeTelegramClient> {
        val client = FakeTelegramClient(
            searchable = listOf(
                chat(1, "Material Design"),
                chat(2, "Lina Park"),
                chat(3, "Design crit")
            ),
            searchableMessages = listOf(
                MessageHit(chat(2, "Lina Park"), message(10, "the design is done")),
                MessageHit(chat(3, "Design crit"), message(11, "nothing to see"))
            )
        )
        val vm = HomeViewModel(TelegramRepository(client))
        // uiState is stateIn(WhileSubscribed): with nothing collecting it, it
        // reports its initial value forever, so reading .value in a test
        // measures the placeholder rather than the view model. The screen
        // subscribes; a test has to as well, or it asserts against a constant.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
        return vm to client
    }

    @Test
    fun `no folders means no tabs and every chat`() = runTest {
        val (vm, client) = viewModel()
        client.setChats(listOf(chat(1, "Ivan"), chat(2, "Noor")))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.folderTabs.isEmpty())
        assertEquals(2, vm.uiState.value.chats.size)
    }

    @Test
    fun `choosing a folder filters the list and All puts it back`() = runTest {
        val (vm, client) = viewModel()
        client.setFolders(listOf(ChatFolder(1, "Work"), ChatFolder(2, "People")))
        client.setChats(
            listOf(
                chat(1, "Ivan").copy(folderIds = setOf(1)),
                chat(2, "Noor").copy(folderIds = setOf(1, 2)),
                chat(3, "Nobody")
            )
        )
        advanceUntilIdle()

        assertEquals(
            listOf("All", "Work", "People"),
            vm.uiState.value.folderTabs.map { it.title }
        )

        vm.onFolderSelected(2)
        advanceUntilIdle()
        assertEquals(listOf("Noor"), vm.uiState.value.chats.map { it.title })

        vm.onFolderSelected(null)
        advanceUntilIdle()
        assertEquals(3, vm.uiState.value.chats.size)
    }

    @Test
    fun `a folder that disappears falls back to All rather than an empty list`() = runTest {
        val (vm, client) = viewModel()
        client.setFolders(listOf(ChatFolder(1, "Work")))
        client.setChats(listOf(chat(1, "Ivan").copy(folderIds = setOf(1))))
        vm.onFolderSelected(1)
        advanceUntilIdle()
        assertEquals(listOf("Ivan"), vm.uiState.value.chats.map { it.title })

        // Deleted on another device, which is the case that would otherwise
        // leave a tab selected that no longer exists and a list that cannot
        // be got back.
        client.setFolders(emptyList())
        advanceUntilIdle()

        assertNull(vm.uiState.value.selectedFolderId)
        assertTrue(vm.uiState.value.folderTabs.isEmpty())
        assertEquals(listOf("Ivan"), vm.uiState.value.chats.map { it.title })
    }

    @Test
    fun `a list that has run out is not asked again until a refresh`() = runTest {
        val (vm, client) = viewModel()
        val asks = { client.chatCalls.count { it == "loadMoreChats:null" } }

        client.hasMoreChats = true
        vm.onListEndReached(null)
        advanceUntilIdle()
        client.hasMoreChats = false
        vm.onListEndReached(null)
        advanceUntilIdle()
        vm.onListEndReached(null)
        advanceUntilIdle()
        assertEquals("twice, then it said there was no more", 2, asks())

        vm.refresh()
        advanceUntilIdle()
        vm.onListEndReached(null)
        advanceUntilIdle()
        assertEquals(3, asks())
    }

    @Test
    fun `a refused mute is said in a snackbar rather than thrown`() = runTest {
        val (vm, client) = viewModel()
        client.failWith = IllegalStateException("CHAT_NOT_MODIFIED")

        vm.onMutedChange(1, true)
        advanceUntilIdle()

        assertEquals("Could not mute: Chat not modified", vm.uiState.value.errorMessage)
        vm.onErrorShown()
        advanceUntilIdle()
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun `the archive row outlives choosing a folder`() = runTest {
        val (vm, client) = viewModel()
        client.setFolders(listOf(ChatFolder(1, "Work")))
        client.setChats(
            listOf(
                chat(1, "Ivan").copy(folderIds = setOf(1)),
                chat(2, "Parcels").copy(isArchived = true)
            )
        )
        advanceUntilIdle()
        assertEquals("1 chat", vm.uiState.value.archiveSummary)

        // The All page sits beside Work in the pager and is on screen for
        // the length of a swipe back to it, so its row cannot wait for the
        // selection to come home. Which page draws it is the screen's call.
        vm.onFolderSelected(1)
        advanceUntilIdle()
        assertEquals("1 chat", vm.uiState.value.archiveSummary)
        assertEquals(listOf("Ivan"), vm.uiState.value.folderChats[1].map { it.title })
    }

    @Test
    fun `the badge counts chats with something unread, per tab`() = runTest {
        val (vm, client) = viewModel()
        client.setFolders(listOf(ChatFolder(1, "Work"), ChatFolder(2, "People")))
        client.setChats(
            listOf(
                chat(1, "Ivan").copy(folderIds = setOf(1), unreadCount = 4),
                chat(2, "Noor").copy(folderIds = setOf(1)),
                chat(3, "Mum").copy(folderIds = setOf(2), unreadCount = 1)
            )
        )
        advanceUntilIdle()

        // All, Work, People — and All counts both, not the sum of the folders.
        assertEquals(listOf(2, 1, 1), vm.uiState.value.folderUnread)
    }

    @Test
    fun `typing a word searches once, not once per letter`() = runTest {
        val (vm, client) = viewModel()

        "Design".forEachIndexed { index, _ ->
            vm.onSearchQueryChange("Design".take(index + 1))
            advanceTimeBy(40)
        }
        advanceUntilIdle()

        assertEquals("each keystroke cancels the last request", 1, client.searchCount)
        assertEquals("Design", client.lastSearchQuery)
        assertEquals(
            listOf("Material Design", "Design crit"),
            vm.uiState.value.search.results.map { it.title }
        )
    }

    @Test
    fun `one search covers chats and message text both`() = runTest {
        val (vm, client) = viewModel()

        vm.onSearchQueryChange("design")
        advanceUntilIdle()

        val search = vm.uiState.value.search
        assertEquals(
            listOf("Material Design", "Design crit"),
            search.results.map { it.title }
        )
        assertEquals("the design is done", search.messages.single().message.text)
        assertEquals("one debounce, not one per half", 1, client.searchCount)
        assertEquals(1, client.messageSearchCount)
    }

    @Test
    fun `public chats come after the account's own, each once`() = runTest {
        val (vm, client) = viewModel()
        client.publicChats = listOf(chat(1, "Material Design"), chat(40, "Design Weekly").copy(isChannel = true))

        vm.onSearchQueryChange("design")
        advanceUntilIdle()

        val chats = vm.uiState.value.search.chats
        assertEquals(listOf("Material Design", "Design crit"), chats.mine.map { it.title })
        assertEquals(listOf("Design Weekly"), chats.global.map { it.title })
        vm.onSearchScopeChange(SearchScope.Channels)
        assertEquals(listOf("Design Weekly"), vm.uiState.value.search.visibleChats.global.map { it.title })
    }

    @Test
    fun `posts are searched when asked, not while typing`() = runTest {
        val (vm, client) = viewModel()
        client.postSearch = PostSearch(freeLeft = 4)

        vm.onSearchScopeChange(SearchScope.Posts)
        vm.onSearchQueryChange("design")
        advanceUntilIdle()
        assertEquals("typing spends no post search", 0, client.postSearchCount)
        assertNull(vm.uiState.value.search.posts)

        vm.onSearchSubmit()
        advanceUntilIdle()
        assertEquals(1, client.postSearchCount)
        assertEquals(4, vm.uiState.value.search.posts?.freeLeft)

        vm.onSearchQueryChange("designs")
        assertNull("a new query drops the old posts", vm.uiState.value.search.posts)
    }

    @Test
    fun `opening a result remembers the chat and the words`() = runTest {
        val (vm, client) = viewModel()
        vm.onSearchExpandedChange(true)
        vm.onSearchQueryChange("lina")
        advanceUntilIdle()

        vm.onSearchResultOpened(2)
        advanceUntilIdle()
        assertEquals(listOf(2L), client.recentlyFound.map { it.id })

        vm.onSearchExpandedChange(false)
        vm.onSearchExpandedChange(true)
        advanceUntilIdle()
        val search = vm.uiState.value.search
        assertEquals(listOf("lina"), search.recentQueries)
        assertEquals(listOf("Lina Park"), search.recentChats.map { it.title })

        vm.onRecentChatsCleared()
        vm.onRecentQueriesCleared()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.search.recentChats.isEmpty())
        assertTrue(vm.uiState.value.search.recentQueries.isEmpty())
        assertTrue(client.recentlyFound.isEmpty())
    }

    @Test
    fun `nothing found needs both halves to be empty`() = runTest {
        val (vm, _) = viewModel()

        // Matches no chat title, but does match a message.
        vm.onSearchQueryChange("is done")
        advanceUntilIdle()

        val search = vm.uiState.value.search
        assertTrue(search.results.isEmpty())
        assertFalse("a message hit is still a result", search.isEmpty)
    }

    @Test
    fun `an empty query asks nothing and clears what was found`() = runTest {
        val (vm, client) = viewModel()

        vm.onSearchQueryChange("Lina")
        advanceUntilIdle()
        assertEquals(1, client.searchCount)

        vm.onSearchQueryChange("")
        advanceUntilIdle()

        assertEquals("a blank field is not a request for everything", 1, client.searchCount)
        assertTrue(vm.uiState.value.search.isEmpty)
        assertFalse(vm.uiState.value.search.isSearching)
    }

    @Test
    fun `isSearching is true only while a request is in flight`() = runTest {
        val (vm, _) = viewModel()

        vm.onSearchQueryChange("Design")
        // runCurrent, not advanceUntilIdle: the state has to reach uiState
        // through combine, but advancing time would also fire the debounce
        // and finish the search this test is trying to observe mid-flight.
        runCurrent()

        // Inside the debounce: the request has not been made, but the screen
        // must not be claiming "nothing found" either.
        assertTrue(vm.uiState.value.search.isSearching)

        advanceUntilIdle()
        assertFalse(vm.uiState.value.search.isSearching)
    }

    @Test
    fun `closing search throws the query away`() = runTest {
        val (vm, _) = viewModel()

        vm.onSearchExpandedChange(true)
        vm.onSearchQueryChange("Design")
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.search.results.size)

        vm.onSearchExpandedChange(false)
        runCurrent()

        assertEquals("", vm.uiState.value.search.query)
        assertTrue(vm.uiState.value.search.isEmpty)
        assertFalse(vm.uiState.value.search.expanded)
    }

    // ── profile ──────────────────────────────────────────────────────────

    private fun signedIn(client: FakeTelegramClient) {
        client.mutableAuthState.value = AuthUiState(
            me = TelegramUser(
                id = 1,
                firstName = "You",
                lastName = "Expressive",
                username = "telegramyou",
                bio = "Material You"
            )
        )
    }

    @Test
    fun `only the fields that changed are sent, and the username goes last`() = runTest(dispatcher) {
        val client = FakeTelegramClient()
        signedIn(client)
        val viewModel = HomeViewModel(TelegramRepository(client))
        val collector = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val me = client.authState.value.me!!
        viewModel.onProfileDraftChange(
            profileDraftOf(me).copy(firstName = "Someone", username = "someone_else")
        )
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.profile.canSave)

        viewModel.saveProfile()
        advanceUntilIdle()

        // No setBio: the bio did not change, and a field sent back unchanged
        // is a round trip that can fail for no reason. The username is last so
        // that a refusal — the one this call actually gets — happens after the
        // rest has landed and can still be explained.
        assertEquals(
            listOf("setName:Someone|Expressive", "setUsername:someone_else", "refreshMe"),
            client.profileCalls
        )
        collector.cancel()
    }

    @Test
    fun `a refusal keeps what was typed and says what the server said`() = runTest(dispatcher) {
        val client = FakeTelegramClient()
        signedIn(client)
        client.profileError = "Username is already taken"
        val viewModel = HomeViewModel(TelegramRepository(client))
        val collector = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val me = client.authState.value.me!!
        viewModel.onProfileDraftChange(profileDraftOf(me).copy(username = "taken_name"))
        viewModel.saveProfile()
        advanceUntilIdle()

        val profile = viewModel.uiState.value.profile
        assertEquals("Username is already taken", profile.errorMessage)
        // The form still holds the attempt rather than snapping back, so the
        // next try is an edit instead of retyping it all.
        assertEquals("taken_name", profile.draft.username)
        assertFalse(profile.isSaving)
        collector.cancel()
    }

    @Test
    fun `an untouched form cannot be saved`() = runTest(dispatcher) {
        val client = FakeTelegramClient()
        signedIn(client)
        val viewModel = HomeViewModel(TelegramRepository(client))
        val collector = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        // The draft is the account until something is typed, so there is
        // nothing to send and the button is off without any "editing" flag.
        assertFalse(viewModel.uiState.value.profile.canSave)
        viewModel.saveProfile()
        advanceUntilIdle()
        assertEquals(emptyList<String>(), client.profileCalls)
        collector.cancel()
    }

}
