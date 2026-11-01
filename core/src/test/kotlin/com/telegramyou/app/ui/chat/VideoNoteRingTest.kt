package com.telegramyou.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoNoteRingTest {

    @Test
    fun `the ring runs clockwise from twelve o'clock`() {
        assertEquals(0f, ringFraction(0f, -10f), 0.001f)
        assertEquals(0.25f, ringFraction(10f, 0f), 0.001f)
        assertEquals(0.5f, ringFraction(0f, 10f), 0.001f)
        assertEquals(0.75f, ringFraction(-10f, 0f), 0.001f)
        assertEquals(0.875f, ringFraction(-10f, -10f), 0.001f)
    }

    @Test
    fun `only the edge winds it, the middle is left to scrolling`() {
        assertTrue(onRing(0f, -90f, radius = 100f))
        assertTrue(onRing(60f, 0f, radius = 100f))
        assertFalse(onRing(20f, 20f, radius = 100f))
        assertFalse(onRing(0f, 0f, radius = 0f))
    }

    @Test
    fun `the next circle after one is the next round video, not the next message`() {
        fun m(id: Long, type: com.telegramyou.app.telegram.model.MessageContentType) =
            com.telegramyou.app.telegram.model.ChatMessage(
                id = id, chatId = 1, text = "", isOutgoing = false, timeLabel = "", contentType = type,
                video = com.telegramyou.app.telegram.model.VideoContent()
            )
        val note = com.telegramyou.app.telegram.model.MessageContentType.VideoNote
        val text = com.telegramyou.app.telegram.model.MessageContentType.Text
        val messages = listOf(m(1, note), m(2, text), m(3, note), m(4, note))
        assertEquals(3L, nextVideoNote(messages, 1)?.id)
        assertEquals(4L, nextVideoNote(messages, 3)?.id)
        assertEquals(null, nextVideoNote(messages, 4))
    }
}
