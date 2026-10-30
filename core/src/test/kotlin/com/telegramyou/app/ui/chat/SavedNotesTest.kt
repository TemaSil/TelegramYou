package com.telegramyou.app.ui.chat

import com.telegramyou.app.telegram.model.ChatMessage
import com.telegramyou.app.telegram.model.MessageContentType
import com.telegramyou.app.telegram.model.VideoContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedNotesTest {

    private fun note(text: String, type: MessageContentType = MessageContentType.Text) =
        ChatMessage(id = 1, chatId = 1, text = text, isOutgoing = true, timeLabel = "", contentType = type)

    @Test
    fun `a note says what was written, never the marker for a bare photo`() {
        assertEquals("Buy milk", noteBody(note("  Buy milk ")))
        assertEquals("", noteBody(note("Photo", MessageContentType.Photo)))
        assertEquals("", noteBody(note("GIF", MessageContentType.Animation)))
        assertEquals("Sunset", noteBody(note("Sunset", MessageContentType.Photo)))
        assertEquals("", noteBody(note("🎤 Voice", MessageContentType.Voice).copy(mediaEmoji = "🎤")))
    }

    @Test
    fun `what a note holds besides its words is said in a line`() {
        assertEquals("Voice message · 0:12", noteAttachment(note("", MessageContentType.Voice).copy(voiceDurationSeconds = 12)))
        assertEquals("plan.pdf", noteAttachment(note("", MessageContentType.Document).copy(fileName = "plan.pdf")))
        assertNull(noteAttachment(note("hi")))
        assertNull(noteAttachment(note("", MessageContentType.Photo)))
    }

    @Test
    fun `pictures go at the top of a note`() {
        assertTrue(noteHasPicture(note("", MessageContentType.Photo)))
        assertTrue(noteHasPicture(note("", MessageContentType.Video).copy(video = VideoContent())))
        assertFalse(noteHasPicture(note("", MessageContentType.Video)))
        assertFalse(noteHasPicture(note("hi")))
    }
}
