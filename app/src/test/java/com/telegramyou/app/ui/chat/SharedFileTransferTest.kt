package com.telegramyou.app.ui.chat

import androidx.lifecycle.SavedStateHandle
import com.telegramyou.app.navigation.Route
import com.telegramyou.app.telegram.FakeTelegramClient
import com.telegramyou.app.telegram.TelegramClient
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class SharedFileTransferTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun main() = Dispatchers.setMain(dispatcher)
    @After fun reset() = Dispatchers.resetMain()

    @Test fun `pausing a profile file keeps the download and resuming opens it without an error`() = runTest(dispatcher) {
        var entry = DownloadEntry(33, 1, 7, "guide.pdf", 1000, downloadedBytes = 300)
        val interrupted = CompletableDeferred<DownloadOutcome>()
        var attempts = 0
        val client = object : TelegramClient by FakeTelegramClient() {
            override suspend fun fileDownloads() = listOf(entry)
            override suspend fun setDownloadPaused(fileId: Int, paused: Boolean) {
                assertEquals(33, fileId)
                entry = entry.copy(isPaused = paused)
                if (paused) interrupted.complete(DownloadOutcome.Stopped)
            }
            override suspend fun downloadToList(chatId: Long, messageId: Long, fileId: Int): DownloadOutcome {
                attempts++
                return if (attempts == 1) interrupted.await() else DownloadOutcome.Done("/cache/guide.pdf")
            }
        }
        val vm = SharedMediaViewModel(TelegramRepository(client, dispatcher), SavedStateHandle(mapOf(Route.Chat.ARG_CHAT_ID to 1L)))
        val message = ChatMessage(7, 1, "guide.pdf", false, "", contentType = MessageContentType.Document,
            fileName = "guide.pdf", documentFileId = 33, mimeType = "application/pdf")
        vm.onFileTapped(message)
        runCurrent()
        assertEquals(7L, vm.uiState.value.openingFileId)
        vm.onFilePaused(message)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.downloads.getValue(33).isPaused)
        assertNull(vm.uiState.value.openingFileId)
        assertNull(vm.uiState.value.errorMessage)
        vm.onFileResumed(message)
        advanceUntilIdle()
        assertEquals(2, attempts)
        assertFalse(vm.uiState.value.downloads.getValue(33).isPaused)
        assertEquals("/cache/guide.pdf", vm.uiState.value.fileToOpen?.path)
        assertNull(vm.uiState.value.errorMessage)
    }
}
