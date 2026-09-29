package com.telegramyou.app.telegram.model

import com.telegramyou.app.ui.media.FileTransfer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadsTest {

    private val mb = 1024L * 1024

    private fun entry(
        id: Int,
        added: Long = 0,
        completed: Long? = null,
        paused: Boolean = false,
        path: String? = null,
        downloaded: Long = 0
    ) = DownloadEntry(
        fileId = id, chatId = 1, messageId = id.toLong(), name = "file$id.zip", sizeBytes = 4 * mb,
        downloadedBytes = downloaded, chatTitle = "Design Circle", addedAt = added, completedAt = completed,
        isPaused = paused, path = path
    )

    @Test
    fun `every state says itself in words`() {
        val running = FileTransfer(1, mb, 4 * mb)
        assertEquals(DownloadState.Downloading, entry(1).state(running))
        assertEquals("1 MB of 4 MB", entry(1).statusLine(running))
        assertEquals(DownloadState.Paused, entry(1, paused = true, downloaded = mb).state(null))
        assertEquals("Paused · 1 MB of 4 MB", entry(1, paused = true, downloaded = mb).statusLine(null))
        assertEquals(DownloadState.Waiting, entry(1).state(null))
        assertEquals("Waiting for the network", entry(1).statusLine(null))
        assertEquals("4 MB · from Design Circle", entry(1, completed = 5, path = "/f").statusLine(null))
    }

    @Test
    fun `a finished file cleared from the phone stays, and says so`() {
        val gone = entry(1, completed = 5, path = null)
        assertEquals(DownloadState.Gone, gone.state(null))
        assertTrue(gone.statusLine(null).startsWith("Removed from the phone"))
    }

    @Test
    fun `an upload of the same file is not a download`() {
        assertEquals(DownloadState.Waiting, entry(1).state(FileTransfer(1, mb, 4 * mb, isUpload = true)))
    }

    @Test
    fun `the queue reads oldest first, the history newest first`() {
        val sections = sectionDownloads(
            listOf(
                entry(1, added = 30), entry(2, added = 10), entry(1, added = 30),
                entry(3, completed = 100, path = "/a"), entry(4, completed = 200, path = null)
            )
        )
        assertEquals(listOf(2, 1), sections.active.map { it.fileId })
        assertEquals(listOf(4, 3), sections.finished.map { it.fileId })
        // Only what is still on the phone would be freed.
        assertEquals(4 * mb, sections.finishedBytes)
        assertTrue(sections.anyRunning)
        assertFalse(sections.anyPaused)
    }

    @Test
    fun `progress comes from the transfer while there is one`() {
        assertEquals(0.25f, entry(1).progress(FileTransfer(1, mb, 4 * mb)))
        assertEquals(0.5f, entry(1, downloaded = 2 * mb).progress(null))
        assertNull(entry(1).copy(sizeBytes = 0).progress(null))
    }

    @Test
    fun `the notification counts files and bytes`() {
        assertEquals(
            "Downloading 2 files · 3 MB of 8 MB",
            downloadNotificationText(listOf(FileTransfer(1, mb, 4 * mb), FileTransfer(2, 2 * mb, 4 * mb)))
        )
        assertEquals("Downloading 1 file", downloadNotificationText(listOf(FileTransfer(1, 0, 0))))
    }
}
