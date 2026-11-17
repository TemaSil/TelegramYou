package com.telegramyou.app.ui.music

import com.telegramyou.app.telegram.FakeTelegramClient
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.AudioContent
import com.telegramyou.app.telegram.model.ChatMessage
import com.telegramyou.app.telegram.model.MessageContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class MusicPagingTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun main() = Dispatchers.setMain(dispatcher)
    @After fun reset() = Dispatchers.resetMain()

    @Test fun `library includes music beyond the former thousand-track cutoff`() = runTest(dispatcher) {
        val client = FakeTelegramClient()
        client.musicReply = { _, cursor, limit ->
            val start = cursor.toIntOrNull() ?: 0
            val end = (start + limit).coerceAtMost(1101)
            (start until end).map { track(it.toLong()) } to end.takeIf { it < 1101 }?.toString()
        }
        val vm = library(client)
        advanceUntilIdle()
        assertEquals(1101, vm.uiState.value.library.tracks.size)
        assertEquals(12, client.musicCalls.size)
        assertFalse(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.error)
    }

    @Test fun `empty intermediate pages keep their cursor and distinct chats keep equal message ids`() = runTest(dispatcher) {
        val client = FakeTelegramClient()
        client.musicReply = { _, cursor, _ -> when (cursor) {
            "" -> listOf(track(1)) to "empty"
            "empty" -> emptyList<ChatMessage>() to "older"
            else -> listOf(track(1), track(1, chatId = 2)) to null
        } }
        val vm = library(client)
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.library.tracks.size)
        assertEquals(listOf("", "empty", "older"), client.musicCalls.map { it.second })
    }

    @Test fun `failed library page keeps the loaded tracks and retries the same cursor`() = runTest(dispatcher) {
        val client = FakeTelegramClient()
        var fail = true
        client.musicReply = { _, cursor, _ ->
            if (cursor.isEmpty()) listOf(track(1)) to "older"
            else { if (fail) error("offline"); listOf(track(2)) to null }
        }
        val vm = library(client)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.library.tracks.size)
        assertNotNull(vm.uiState.value.error)
        fail = false
        vm.onRetry()
        advanceUntilIdle()
        assertEquals(listOf("", "older", "older"), client.musicCalls.map { it.second })
        assertEquals(2, vm.uiState.value.library.tracks.size)
        assertNull(vm.uiState.value.error)
    }

    @Test fun `a repeated library cursor reports failure instead of looping or claiming completion`() = runTest(dispatcher) {
        val client = FakeTelegramClient()
        client.musicReply = { _, cursor, _ -> listOf(track(1)) to if (cursor.isEmpty()) "same" else cursor }
        val vm = library(client)
        advanceUntilIdle()
        assertEquals(2, client.musicCalls.size)
        assertNotNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test fun `music search ignores a cancelled old query and retains a paging failure for retry`() = runTest(dispatcher) {
        val client = FakeTelegramClient()
        var fail = true
        client.musicReply = { query, cursor, _ ->
            if (cursor.isNotBlank() && fail) error("offline")
            listOf(track(if (cursor.isBlank()) 1 else 2).copy(text = query)) to if (cursor.isBlank()) "older" else null
        }
        val vm = MyMusicViewModel(TelegramRepository(client))
        advanceUntilIdle()
        vm.onQuery("old")
        vm.onQuery("new")
        advanceUntilIdle()
        assertEquals("new", vm.uiState.value.tracks.single().text)
        vm.onNearEnd()
        advanceUntilIdle()
        assertEquals("older", vm.uiState.value.cursor)
        assertEquals(1, vm.uiState.value.tracks.size)
        assertNotNull(vm.uiState.value.error)
        fail = false
        vm.onRetry()
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.tracks.size)
        assertNull(vm.uiState.value.error)
    }

    private fun library(client: FakeTelegramClient) = MusicLibraryViewModel(TelegramRepository(client), workerDispatcher = dispatcher, fileDispatcher = dispatcher)
    private fun track(id: Long, chatId: Long = 1) = ChatMessage(id, chatId, "Track $id", false, "", contentType = MessageContentType.Audio,
        audio = AudioContent(title = "Track $chatId:$id", durationSeconds = 60, fileId = id.toInt()))
}
