package com.telegramyou.app.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConversationsTest {

    @Test
    fun `a chat's id goes there and back`() {
        for (chatId in listOf(1L, 777000L, -1001234567890L)) {
            assertEquals(chatId, chatIdOfConversation(conversationId(chatId)))
        }
    }

    @Test
    fun `other shortcuts name no chat`() {
        assertNull(chatIdOfConversation(null))
        assertNull(chatIdOfConversation(""))
        assertNull(chatIdOfConversation("compose"))
        assertNull(chatIdOfConversation("chat_"))
        assertNull(chatIdOfConversation("chat_abc"))
    }

    @Test
    fun `initials are the first two words' capitals`() {
        assertEquals("LP", avatarInitials("Lina Park"))
        assertEquals("TN", avatarInitials("TelegramYou News Chat"))
        assertEquals("M", avatarInitials("material"))
        assertEquals("?", avatarInitials("   "))
    }
}
