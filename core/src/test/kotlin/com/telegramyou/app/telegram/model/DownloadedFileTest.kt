package com.telegramyou.app.telegram.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadedFileTest {

    private fun message(block: ChatMessage.() -> ChatMessage) =
        ChatMessage(id = 1, chatId = 1, text = "", isOutgoing = false, timeLabel = "").block()

    @Test
    fun `a file on the phone answers its id`() {
        assertEquals(7, message { copy(documentFileId = 7, documentPath = "/data/doc.pdf") }.downloadedFileId())
        assertEquals(8, message { copy(audio = AudioContent(title = "t", fileId = 8, path = "/data/a.mp3")) }.downloadedFileId())
        assertEquals(9, message { copy(video = VideoContent(fileId = 9, path = "/data/v.mp4")) }.downloadedFileId())
        assertEquals(10, message { copy(voiceFileId = 10, voicePath = "/data/v.ogg") }.downloadedFileId())
        assertEquals(11, message { copy(photoFileId = 11, photoPath = "/data/p.jpg") }.downloadedFileId())
    }

    @Test
    fun `nothing to free when nothing is downloaded`() {
        assertNull(message { this }.downloadedFileId())
        assertNull(message { copy(documentFileId = 7) }.downloadedFileId())
        assertNull(message { copy(video = VideoContent(fileId = 9)) }.downloadedFileId())
        // A picture still behind a content:// Uri is the picker's, not a
        // download of ours.
        assertNull(message { copy(photoFileId = 11, photoPath = "content://media/1") }.downloadedFileId())
    }
}
