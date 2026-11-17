package com.telegramyou.app.telegram

import com.telegramyou.app.telegram.model.DownloadOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TelegramRepositoryDownloadsTest {
    @Test fun `an immediately stopped download does not remain cached as the next attempt`() = runTest {
        var calls = 0
        val client = object : TelegramClient by FakeTelegramClient() {
            override suspend fun downloadToList(chatId: Long, messageId: Long, fileId: Int): DownloadOutcome =
                if (++calls == 1) DownloadOutcome.Stopped else DownloadOutcome.Done("ready.pdf")
        }
        val repository = TelegramRepository(client, UnconfinedTestDispatcher(testScheduler))
        assertEquals(DownloadOutcome.Stopped, repository.downloadToList(1, 7, 33))
        assertEquals(DownloadOutcome.Done("ready.pdf"), repository.downloadToList(1, 7, 33))
        assertEquals(2, calls)
        assertTrue(repository.listDownloads.value.isEmpty())
    }

    @Test fun `pause detaches the old waiter before an immediate resume`() = runTest {
        var calls = 0
        val pending = CompletableDeferred<DownloadOutcome>()
        val client = object : TelegramClient by FakeTelegramClient() {
            override suspend fun downloadToList(chatId: Long, messageId: Long, fileId: Int): DownloadOutcome =
                if (++calls == 1) pending.await() else DownloadOutcome.Done("ready.pdf")
        }
        val repository = TelegramRepository(client, StandardTestDispatcher(testScheduler))
        val old = async { repository.downloadToList(1, 7, 33) }
        runCurrent()
        repository.setDownloadPaused(33, true)
        val resumed = async { repository.downloadToList(1, 7, 33) }
        advanceUntilIdle()
        assertEquals(DownloadOutcome.Stopped, old.await())
        assertEquals(DownloadOutcome.Done("ready.pdf"), resumed.await())
        assertEquals(2, calls)
        assertTrue(repository.listDownloads.value.isEmpty())
    }
}
