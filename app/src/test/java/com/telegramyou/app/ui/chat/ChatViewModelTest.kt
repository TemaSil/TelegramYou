package com.telegramyou.app.ui.chat

import androidx.lifecycle.SavedStateHandle
import com.telegramyou.app.navigation.Route
import com.telegramyou.app.telegram.FakeTelegramClient
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.ChatMessage
import com.telegramyou.app.telegram.model.MessageUpdate
import com.telegramyou.app.telegram.model.ChatPreview
import com.telegramyou.app.telegram.model.MessageReaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    /**
     * Held rather than created inline, so a test that has to advance virtual
     * time can share this scheduler. Without that, the debounce inside the
     * ViewModel runs on one clock and `advanceUntilIdle` moves another.
     */
    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun installDispatcher() {
        // viewModelScope is Dispatchers.Main, which the JVM has no notion of.
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun removeDispatcher() = Dispatchers.resetMain()

    private fun message(id: Long, text: String = "m$id") = ChatMessage(
        id = id,
        chatId = CHAT_ID,
        text = text,
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
    fun `a message that arrives is appended to the window`() = runTest {
        val (vm, client) = viewModel(listOf(message(10)))
        advanceUntilIdle()

        client.deliver(message(11, "arrived"))
        advanceUntilIdle()

        assertEquals(listOf(10L, 11L), vm.uiState.value.messages.map { it.id })
    }

    @Test
    fun `the same message arriving twice is drawn once`() = runTest {
        // TDLib repeats an update after a reconnect, and a duplicate message
        // is worse than a late one.
        val (vm, client) = viewModel(listOf(message(10)))
        advanceUntilIdle()

        client.deliver(message(11))
        client.deliver(message(11))
        advanceUntilIdle()

        assertEquals(listOf(10L, 11L), vm.uiState.value.messages.map { it.id })
    }

    @Test
    fun `a message for another chat is ignored`() = runTest {
        val (vm, client) = viewModel(listOf(message(10)))
        advanceUntilIdle()

        client.deliver(message(11).copy(chatId = CHAT_ID + 1))
        advanceUntilIdle()

        assertEquals(listOf(10L), vm.uiState.value.messages.map { it.id })
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
    fun `sending keeps what was paged in`() = runTest {
        val (vm, client) = viewModel(
            listOf(message(10)),
            listOf(message(9))
        )
        vm.onLoadOlder()
        val opened = client.openChatCount

        vm.onDraftChange("hello")
        vm.onSend()

        // Nothing is fetched again: the history scrolled back through is
        // still there, and the list has nothing to jump over.
        assertEquals(opened, client.openChatCount)
        assertEquals(listOf(9L, 10L), vm.uiState.value.messages.map { it.id })
        assertEquals(listOf("hello"), client.sentTexts)
        assertEquals("", vm.uiState.value.draft)

        // The message itself arrives the way every message does.
        client.deliver(message(11, "hello").copy(isOutgoing = true))
        assertEquals(listOf(9L, 10L, 11L), vm.uiState.value.messages.map { it.id })
    }

    @Test
    fun `a refused send puts the draft back and says why`() = runTest {
        val (vm, client) = viewModel(listOf(message(10)))
        client.failWith = TdLibLikeException("Too Many Requests: retry after 17")

        vm.onDraftChange("hello")
        vm.onReplyTo(message(10))
        vm.onSend()

        assertEquals("hello", vm.uiState.value.draft)
        assertEquals(10L, vm.uiState.value.replyTo?.id)
        assertEquals(
            "Could not send: Too Many Requests: retry after 17",
            vm.uiState.value.errorMessage
        )

        vm.onErrorShown()
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun `a confirmed send moves the message to its real id`() = runTest {
        val (vm, client) = viewModel(listOf(message(10)))
        client.deliver(message(9_000_001, "hi").copy(isOutgoing = true))
        vm.onReplyTo(message(9_000_001, "hi"))

        client.announce(MessageUpdate.Replaced(9_000_001, message(11, "hi")))

        assertEquals(listOf(10L, 11L), vm.uiState.value.messages.map { it.id })
        assertEquals(
            "the banner follows the message, or the reply would aim at nothing",
            11L,
            vm.uiState.value.replyTo?.id
        )
    }

    @Test
    fun `deleting takes the message off at once, and a refusal brings the window back`() =
        runTest {
            val (vm, client) = viewModel(listOf(message(10), message(11)))

            client.failWith = TdLibLikeException("MESSAGE_DELETE_FORBIDDEN")
            val opened = client.openChatCount
            vm.onDeleteConfirmed(message(11), forEveryone = true)

            assertEquals("the server's copy is fetched again", opened + 1, client.openChatCount)
            assertEquals(listOf(10L, 11L), vm.uiState.value.messages.map { it.id })
            assertEquals(
                "Could not delete: Message delete forbidden",
                vm.uiState.value.errorMessage
            )

            client.failWith = null
            vm.onDeleteConfirmed(message(11), forEveryone = true)
            assertEquals(listOf(10L), vm.uiState.value.messages.map { it.id })
        }

    @Test
    fun `reacting redraws the message before the client is told`() = runTest {
        val (vm, client) = viewModel(listOf(message(10), message(11)))

        vm.onReactionToggled(message(10), "🔥")

        val reacted = vm.uiState.value.messages.first { it.id == 10L }
        assertEquals(
            listOf(MessageReaction("🔥", count = 1, isChosen = true)),
            reacted.reactions
        )
        assertEquals(
            "the other message is left alone",
            emptyList<MessageReaction>(),
            vm.uiState.value.messages.first { it.id == 11L }.reactions
        )
        assertEquals(listOf(Triple(CHAT_ID, 10L, "🔥")), client.reactionCalls)
    }

    @Test
    fun `reacting to a message paged in updates it where it lives`() = runTest {
        val (vm, _) = viewModel(listOf(message(10)), listOf(message(9)))
        vm.onLoadOlder()

        vm.onReactionToggled(message(9), "👍")

        // The older page is a separate list from the opening window, and a
        // caller holding a message has no reason to know which one it is in.
        assertEquals(
            listOf(MessageReaction("👍", count = 1, isChosen = true)),
            vm.uiState.value.messages.first { it.id == 9L }.reactions
        )
    }

    @Test
    fun `picking a reaction closes the picker`() = runTest {
        val (vm, _) = viewModel(listOf(message(10)))

        vm.onReactionsRequested(message(10))
        assertEquals(10L, vm.uiState.value.reactingTo?.id)

        vm.onReactionToggled(message(10), "🔥")
        assertEquals(null, vm.uiState.value.reactingTo)
    }

    @Test
    fun `opening a chat asks what it permits`() = runTest {
        val (vm, _) = viewModel(listOf(message(10)))

        assertEquals(listOf("👍", "🔥"), vm.uiState.value.availableReactions)
    }

    @Test
    fun `selecting and deselecting raises and lowers the toolbar`() = runTest {
        val (vm, _) = viewModel(listOf(message(10), message(11)))

        vm.onSelectionToggled(message(10))
        assertTrue(vm.uiState.value.selection.isActive)
        assertEquals(listOf(10L), vm.uiState.value.selectedMessages.map { it.id })

        vm.onSelectionToggled(message(10))
        assertFalse(vm.uiState.value.selection.isActive)
    }

    @Test
    fun `deleting a selection removes every message in it`() = runTest {
        val (vm, client) = viewModel(listOf(message(10), message(11), message(12)))
        vm.onSelectionToggled(message(10))
        vm.onSelectionToggled(message(12))

        vm.onSelectionDeleted(forEveryone = false)

        assertEquals(listOf(10L, 12L), client.deletedIds)
        assertFalse("the toolbar goes as the messages do",
            vm.uiState.value.selection.isActive)
    }

    @Test
    fun `a chat on screen is marked read once per newer message`() = runTest {
        val (vm, client) = viewModel(listOf(message(10), message(11)))
        val reads = { client.chatCalls.count { it == "markChatRead:$CHAT_ID" } }

        vm.onSeen()
        vm.onSeen()
        assertEquals("once for what is on screen, however often it is looked at", 1, reads())

        client.deliver(message(12))
        vm.onSeen()
        assertEquals("again for the message that arrived", 2, reads())

        client.deliver(message(13).copy(isOutgoing = true))
        vm.onSeen()
        assertEquals("our own message is not something to read", 2, reads())
    }

    @Test
    fun `the chat is held open from the first moment`() = runTest {
        val (_, client) = viewModel(listOf(message(10)))

        assertEquals(1, client.chatCalls.count { it == "retainChat:$CHAT_ID" })
    }

    @Test
    fun `a selected message deleted elsewhere drops out of the selection`() = runTest {
        val (vm, client) = viewModel(listOf(message(10), message(11)))
        vm.onSelectionToggled(message(10))
        vm.onSelectionToggled(message(11))

        // Someone else deleted 11, and the server says so.
        client.announce(MessageUpdate.Deleted(CHAT_ID, setOf(11L)))

        assertEquals(setOf(10L), vm.uiState.value.selection.ids)
        assertEquals(listOf(10L), vm.uiState.value.messages.map { it.id })
    }

    @Test
    fun `an edit from another device is drawn where the message is`() = runTest {
        val (vm, client) = viewModel(listOf(message(10)), listOf(message(9)))
        vm.onLoadOlder()

        client.announce(MessageUpdate.Edited(CHAT_ID, 9, "fixed"))

        val edited = vm.uiState.value.messages.first { it.id == 9L }
        assertEquals("fixed", edited.text)
        assertTrue(edited.isEdited)
    }

    @Test
    fun `typing a word is one search, not five`() = runTest(mainDispatcher.scheduler) {
        val (vm, client) = viewModel(listOf(message(10, text = "needle here")))
        vm.onSearchOpenChange(true)

        "needle".forEachIndexed { index, _ ->
            vm.onSearchQueryChange("needle".take(index + 1))
        }
        // Each keystroke cancels the job the one before it started; only the
        // last survives the debounce.
        advanceUntilIdle()

        assertEquals(1, client.chatSearchCount)
        assertEquals(listOf(10L), vm.uiState.value.search.results.map { it.id })
    }

    @Test
    fun `closing search throws the query away`() = runTest(mainDispatcher.scheduler) {
        val (vm, _) = viewModel(listOf(message(10, text = "needle here")))
        vm.onSearchOpenChange(true)
        vm.onSearchQueryChange("needle")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.search.results.isNotEmpty())

        vm.onSearchOpenChange(false)

        // A query left behind would still be filtering the next time the
        // field is opened, with nothing on screen saying so.
        assertEquals("", vm.uiState.value.search.query)
        assertTrue(vm.uiState.value.search.results.isEmpty())
    }

    @Test
    fun `clearing the query stops searching without asking again`() = runTest(mainDispatcher.scheduler) {
        val (vm, client) = viewModel(listOf(message(10, text = "needle here")))
        vm.onSearchOpenChange(true)
        vm.onSearchQueryChange("needle")
        advanceUntilIdle()

        vm.onSearchQueryChange("")
        advanceUntilIdle()

        assertEquals("a blank field is not a request for everything",
            1, client.chatSearchCount)
        assertTrue(vm.uiState.value.search.results.isEmpty())
    }

    @Test
    fun `forwarding sends the selection on and clears it`() = runTest {
        val (vm, client) = viewModel(listOf(message(10), message(11)))
        client.setChats(
            listOf(
                ChatPreview(id = CHAT_ID, title = "this one", lastMessage = "", timestampLabel = ""),
                ChatPreview(id = 99, title = "somewhere else", lastMessage = "", timestampLabel = "")
            )
        )
        vm.onSelectionToggled(message(10))
        vm.onSelectionToggled(message(11))

        val target = vm.uiState.value.forwardTargets.single()
        assertEquals("the chat being read is not a place to forward to", 99L, target.id)

        vm.onForwardTo(target)

        assertEquals(Triple(CHAT_ID, listOf(10L, 11L), 99L), client.forwarded)
        assertFalse(vm.uiState.value.selection.isActive)
        assertFalse(vm.uiState.value.forwardSheetOpen)
    }

    @Test
    fun `a voice message with no file yet is fetched on the tap`() = runTest {
        val voice = message(10).copy(voiceFileId = 77)
        val (vm, client) = viewModel(listOf(voice))
        client.downloadedPath = "/cache/voice.ogg"

        vm.onVoiceToggled(voice)

        // Fetched on the tap, not on arrival in the window: a conversation of
        // voice notes would otherwise download every one of them to play none.
        // The path is kept so a second tap does not fetch it again.
        assertEquals(
            "/cache/voice.ogg",
            vm.uiState.value.messages.first { it.id == 10L }.voicePath
        )
        assertEquals(null, vm.uiState.value.loadingVoiceId)
    }

    @Test
    fun `a voice message with neither a file nor an id is left alone`() = runTest {
        val (vm, client) = viewModel(listOf(message(10)))

        vm.onVoiceToggled(message(10))

        assertEquals(null, client.downloadedPath)
        assertEquals(null, vm.uiState.value.loadingVoiceId)
        assertEquals(null, vm.uiState.value.playingVoiceId)
    }

    private companion object {
        const val CHAT_ID = 1L
    }
}

/**
 * What a refusal from TDLib looks like to the view model: an exception whose
 * message is the server's own words. The real TdLibException lives with the
 * TDLib client, which loads a native library these tests cannot.
 */
private class TdLibLikeException(message: String) : Exception(message)
