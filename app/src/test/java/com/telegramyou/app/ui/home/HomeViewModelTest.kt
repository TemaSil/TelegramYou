package com.telegramyou.app.ui.home

import com.telegramyou.app.telegram.FakeTelegramClient
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.ChatPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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

    private fun viewModel(): Pair<HomeViewModel, FakeTelegramClient> {
        val client = FakeTelegramClient(
            searchable = listOf(
                chat(1, "Material Design"),
                chat(2, "Lina Park"),
                chat(3, "Design crit")
            )
        )
        return HomeViewModel(TelegramRepository(client)) to client
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
    fun `an empty query asks nothing and clears what was found`() = runTest {
        val (vm, client) = viewModel()

        vm.onSearchQueryChange("Lina")
        advanceUntilIdle()
        assertEquals(1, client.searchCount)

        vm.onSearchQueryChange("")
        advanceUntilIdle()

        assertEquals("a blank field is not a request for everything", 1, client.searchCount)
        assertTrue(vm.uiState.value.search.results.isEmpty())
        assertFalse(vm.uiState.value.search.isSearching)
    }

    @Test
    fun `isSearching is true only while a request is in flight`() = runTest {
        val (vm, _) = viewModel()

        vm.onSearchQueryChange("Design")
        // Still inside the debounce: the request has not been made, but the
        // screen must not be claiming "nothing found" either.
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

        assertEquals("", vm.uiState.value.search.query)
        assertTrue(vm.uiState.value.search.results.isEmpty())
        assertFalse(vm.uiState.value.search.expanded)
    }
}
