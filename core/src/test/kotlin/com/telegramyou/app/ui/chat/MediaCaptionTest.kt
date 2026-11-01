package com.telegramyou.app.ui.chat

import com.telegramyou.app.telegram.model.AudioContent
import com.telegramyou.app.telegram.model.ChatMessage
import com.telegramyou.app.telegram.model.MessageContentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaCaptionTest {

    @Test
    fun `the words standing in for a caption are not one`() {
        for (marker in listOf("Video", "🎬 Video", "🎬 Видео", "Photo", "🖼 Photo", "🖼 Фото", "GIF", " ", "")) {
            assertFalse("\"$marker\" was taken for a caption", isMediaCaption(marker))
        }
    }

    @Test
    fun `what somebody wrote is`() {
        assertTrue(isMediaCaption("Sunset at the bay"))
        assertTrue(isMediaCaption("Video of the talk"))
        assertTrue(isMediaCaption("🎬"))
    }

    private fun message(text: String, type: MessageContentType) =
        ChatMessage(id = 1, chatId = 1, text = text, isOutgoing = true, timeLabel = "", contentType = type)

    @Test
    fun `no kind of message draws the list's word as its caption`() {
        assertEquals("", writtenCaption(message("🎬 Видео", MessageContentType.Video)))
        assertEquals("", writtenCaption(message("Photo", MessageContentType.Photo)))
        assertEquals("", writtenCaption(message("GIF", MessageContentType.Animation)))
        assertEquals("", writtenCaption(message("🎤 Voice", MessageContentType.Voice)))
        assertEquals("", writtenCaption(message("Video message", MessageContentType.VideoNote)))
        assertEquals("", writtenCaption(message("🎵 Разум", MessageContentType.Audio).copy(audio = AudioContent(title = "Разум"))))
        assertEquals("", writtenCaption(message("🎵 Аудио", MessageContentType.Audio)))
        assertEquals("", writtenCaption(message("plan.pdf", MessageContentType.Document).copy(fileName = "plan.pdf")))
        assertEquals("", writtenCaption(message("📎 plan.pdf", MessageContentType.Document).copy(fileName = "plan.pdf")))
    }

    @Test
    fun `a caption somebody wrote stays, whatever the kind`() {
        assertEquals("Our trip", writtenCaption(message("Our trip", MessageContentType.Video)))
        assertEquals("For the drive", writtenCaption(message("For the drive", MessageContentType.Audio).copy(audio = AudioContent(title = "Разум"))))
        assertEquals("Sign page 2", writtenCaption(message("Sign page 2", MessageContentType.Document).copy(fileName = "plan.pdf")))
    }
}
