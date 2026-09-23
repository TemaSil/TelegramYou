package com.telegramyou.app.ui.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransfersTest {

    private fun transfer(done: Long, total: Long, upload: Boolean = false) =
        FileTransfer(fileId = 1, doneBytes = done, totalBytes = total, isUpload = upload)

    @Test
    fun `progress is bytes done over bytes expected`() {
        assertEquals(0.25f, transferProgress(transfer(256, 1024))!!, 0.0001f)
        assertEquals(1f, transferProgress(transfer(1024, 1024))!!, 0.0001f)
    }

    @Test
    fun `an unknown size has no progress to report`() {
        assertNull(transferProgress(transfer(512, 0)))
        assertNull(transferProgress(transfer(512, -1)))
    }

    @Test
    fun `more bytes than expected still reads as finished`() {
        // Servers revise expected_size as a transfer runs, and it can end up
        // below what actually arrived.
        assertEquals(1f, transferProgress(transfer(2048, 1024))!!, 0.0001f)
    }

    @Test
    fun `finished means all of the expected bytes are here`() {
        assertTrue(transfer(1024, 1024).isFinished)
        assertTrue(transfer(2048, 1024).isFinished)
        assertFalse(transfer(1023, 1024).isFinished)
        // Nothing is finished while the size is unknown, however many bytes
        // have arrived — there is nothing to have finished against.
        assertFalse(transfer(9999, 0).isFinished)
    }

    @Test
    fun `sizes read the way a file manager writes them`() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("512 B", formatBytes(512))
        assertEquals("1 KB", formatBytes(1024))
        assertEquals("1.5 KB", formatBytes(1536))
        assertEquals("9.8 MB", formatBytes(10_276_045))
        assertEquals("98 MB", formatBytes(102_760_448))
        assertEquals("1 GB", formatBytes(1024L * 1024 * 1024))
    }

    @Test
    fun `a negative size is not a size`() {
        assertEquals("0 B", formatBytes(-5))
    }

    @Test
    fun `the label says which way the bytes are going`() {
        assertEquals(
            "Downloading 256 B of 1 KB",
            transferLabel(transfer(256, 1024))
        )
        assertEquals(
            "Sending 256 B of 1 KB",
            transferLabel(transfer(256, 1024, upload = true))
        )
    }

    @Test
    fun `an unknown size drops the second half of the label`() {
        assertEquals("Downloading 256 B", transferLabel(transfer(256, 0)))
    }
}
