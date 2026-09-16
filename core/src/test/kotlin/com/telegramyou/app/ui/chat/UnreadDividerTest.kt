package com.telegramyou.app.ui.chat

import com.telegramyou.app.telegram.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnreadDividerTest {

    private fun message(id: Long, outgoing: Boolean = false) = ChatMessage(
        id = id,
        chatId = 1,
        text = "m$id",
        isOutgoing = outgoing,
        timeLabel = ""
    )

    @Test
    fun `nothing unread draws no line`() {
        assertNull(unreadDividerIndex(listOf(message(1), message(2)), unreadCount = 0))
    }

    @Test
    fun `the line sits above the first unread message`() {
        val messages = listOf(message(1), message(2), message(3), message(4))

        assertEquals(2, unreadDividerIndex(messages, unreadCount = 2))
    }

    @Test
    fun `our own messages are not counted`() {
        // Telegram's unread count never includes them, and counting them would
        // push the line up past messages read long ago.
        val messages = listOf(
            message(1),
            message(2),
            message(3, outgoing = true),
            message(4)
        )

        assertEquals(3, unreadDividerIndex(messages, unreadCount = 1))
    }

    @Test
    fun `a window that is entirely unread draws no line`() {
        // A line above the first message says only that the conversation has
        // a beginning.
        assertNull(unreadDividerIndex(listOf(message(1), message(2)), unreadCount = 2))
    }

    @Test
    fun `a count reaching past the loaded window draws no line`() {
        assertNull(unreadDividerIndex(listOf(message(1), message(2)), unreadCount = 9))
    }

    @Test
    fun `an empty conversation draws no line`() {
        assertNull(unreadDividerIndex(emptyList(), unreadCount = 3))
    }
}
