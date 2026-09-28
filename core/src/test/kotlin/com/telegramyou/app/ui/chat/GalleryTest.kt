package com.telegramyou.app.ui.chat

import com.telegramyou.app.telegram.model.ChatMessage
import com.telegramyou.app.telegram.model.MessageContentType
import com.telegramyou.app.telegram.model.VideoContent
import org.junit.Assert.assertEquals
import org.junit.Test

class GalleryTest {

    private fun message(id: Long, type: MessageContentType, date: Long = id) = ChatMessage(
        id = id, chatId = 1, text = "", isOutgoing = false, timeLabel = "", date = date,
        contentType = type,
        photoPath = if (type == MessageContentType.Photo) "/p$id.jpg" else null,
        video = if (type == MessageContentType.Video || type == MessageContentType.Animation) VideoContent() else null
    )

    @Test
    fun `photos and videos, oldest first, and nothing that plays in place`() {
        val messages = listOf(
            message(5, MessageContentType.Photo),
            message(3, MessageContentType.Text),
            message(4, MessageContentType.Animation),
            message(2, MessageContentType.Video),
            message(1, MessageContentType.Photo)
        )
        assertEquals(listOf(1L, 2L, 5L), galleryOf(messages, messages[0]).map { it.id })
    }

    @Test
    fun `a photo from outside the window is shown on its own`() {
        val outside = message(99, MessageContentType.Photo)
        assertEquals(listOf(outside), galleryOf(listOf(message(1, MessageContentType.Photo)), outside))
    }
}
