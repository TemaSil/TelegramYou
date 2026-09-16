package com.telegramyou.app.ui.chat

import androidx.lifecycle.SavedStateHandle
import com.telegramyou.app.navigation.Route
import com.telegramyou.app.telegram.FakeTelegramClient
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class ChatViewModelTest {

    @Before
    fun installDispatcher() {
        // viewModelScope is Dispatchers.Main, which the JVM has no notion of.
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun removeDispatcher() = Dispatchers.resetMain()

    private fun message(id: Long) = ChatMessage(
        id = id,
        chatId = CHAT_ID,
        text = "m$id",
        isOutgoing = false,
        timeLabel = "",
        date = id
    )

    private fun viewModel(
        window: List<ChatMessage>,
        vararg olderPages: List<ChatMessage>
    ): Pair<ChatViewModel, FakeTelegramClient> {
        val client = FakeTelegramClient(window, olderPages.toMutableList())
        val vm = ChatViewModel(
            repository = TelegramRepository(client),
            savedStateHandle = SavedStateHandle(mapOf(Route.Chat.ARG_CHAT_ID to CHAT_ID))
        )
        return vm to client
    }

    @Test
    fun `older pages arrive before the opening window, oldest first`() = runTest {
        val (vm, _) = viewModel(
            listOf(message(10), message(11)),
            listOf(message(8), message(9))
        )

        vm.onLoadOlder()

        assertEquals(listOf(8L, 9L, 10L, 11L), vm.uiState.value.messages.map { it.id })
    }

    @Test
    fun `each page is asked for from the oldest message held`() = runTest {
        val (vm, client) = viewModel(
            listOf(message(10)),
            listOf(message(8), message(9)),
            listOf(message(6), message(7))
        )

        vm.onLoadOlder()
        assertEquals(10L, client.lastLoadOlderBefore)

        vm.onLoadOlder()
        assertEquals("the second page starts from the page before it, not from the window",
            8L, client.lastLoadOlderBefore)
        assertEquals(listOf(6L, 7L, 8L, 9L, 10L), vm.uiState.value.messages.map { it.id })
    }

    @Test
    fun `an empty page ends the history and stops further requests`() = runTest {
        val (vm, client) = viewModel(listOf(message(10)))

        vm.onLoadOlder()
        assertFalse(vm.uiState.value.hasMoreOlder)
        assertEquals(1, client.loadOlderCount)

        // A list sitting at the top asks on every frame; the guard is the only
        // thing between that and a request per frame, forever.
        repeat(5) { vm.onLoadOlder() }
        assertEquals(1, client.loadOlderCount)
    }

    @Test
    fun `nothing is asked for before the first window arrives`() = runTest {
        val (vm, client) = viewModel(emptyList())

        vm.onLoadOlder()

        assertEquals("there is no oldest message to page back from", 0, client.loadOlderCount)
        assertTrue(vm.uiState.value.hasMoreOlder)
    }

    @Test
    fun `reloading after sending discards what was paged in`() = runTest {
        val (vm, _) = viewModel(
            listOf(message(10)),
            listOf(message(9))
        )
        vm.onLoadOlder()
        assertEquals(listOf(9L, 10L), vm.uiState.value.messages.map { it.id })

        // Sending reopens the chat, and that window is authoritative: keeping
        // the old pages around would duplicate or contradict it.
        vm.onDraftChange("hello")
        vm.onSend()

        assertEquals(listOf(10L), vm.uiState.value.messages.map { it.id })
        assertTrue("paging starts over rather than staying exhausted",
            vm.uiState.value.hasMoreOlder)
    }

    private companion object {
        const val CHAT_ID = 1L
    }
}
