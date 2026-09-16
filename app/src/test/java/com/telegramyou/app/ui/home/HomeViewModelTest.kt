package com.telegramyou.app.ui.home

import com.telegramyou.app.telegram.FakeTelegramClient
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.ChatMessage
import com.telegramyou.app.telegram.model.ChatPreview
import com.telegramyou.app.telegram.model.MessageHit
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
}
