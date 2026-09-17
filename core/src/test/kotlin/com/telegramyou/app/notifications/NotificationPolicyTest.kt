package com.telegramyou.app.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPolicyTest {

    private fun message(
        chatId: Long = 1L,
        messageId: Long = 100L,
        senderName: String? = "Ada",
        text: String = "hello",
        timestampMillis: Long = 1_000L,
        isOutgoing: Boolean = false,
        isChatMuted: Boolean = false
    ) = NotifiableMessage(
        chatId = chatId,
        messageId = messageId,
        chatTitle = "Chat $chatId",
        senderName = senderName,
        text = text,
        timestampMillis = timestampMillis,
        isOutgoing = isOutgoing,
        isChatMuted = isChatMuted
    )

    @Test
    fun `an ordinary incoming message notifies`() {
        val decision = decideNotification(message(), NotificationContext())
        assertTrue(decision is NotificationDecision.Notify)
    }

    @Test
    fun `a message this device sent never notifies`() {
        // It comes back down the update stream like any other, and notifying
        // yourself for your own message is the most visible bug of the lot.
        val decision = decideNotification(message(isOutgoing = true), NotificationContext())
        assertEquals(
            NotificationDecision.Suppress(SuppressionReason.OwnMessage),
            decision
        )
    }

    @Test
    fun `a muted chat stays silent`() {
        val decision = decideNotification(message(isChatMuted = true), NotificationContext())
        assertEquals(
            NotificationDecision.Suppress(SuppressionReason.ChatMuted),
            decision
        )
    }

    @Test
    fun `mute outranks the chat being open`() {
        // Both would suppress; the reason has to be the standing instruction
        // rather than the incidental one, or the logs mislead.
        val decision = decideNotification(
            message(isChatMuted = true),
            NotificationContext(openChatId = 1L, isAppInForeground = true)
        )
        assertEquals(
            NotificationDecision.Suppress(SuppressionReason.ChatMuted),
            decision
        )
    }

    @Test
    fun `the chat on screen does not notify`() {
        val decision = decideNotification(
            message(chatId = 7L),
            NotificationContext(openChatId = 7L, isAppInForeground = true)
        )
        assertEquals(
            NotificationDecision.Suppress(SuppressionReason.ChatIsOpen),
            decision
        )
    }

    @Test
    fun `a different chat notifies while one is open`() {
        val decision = decideNotification(
            message(chatId = 8L),
            NotificationContext(openChatId = 7L, isAppInForeground = true)
        )
        assertTrue(decision is NotificationDecision.Notify)
    }

    @Test
    fun `a chat left open in the background still notifies`() {
        // This is the case that decides whether the app works closed at all.
        val decision = decideNotification(
            message(chatId = 7L),
            NotificationContext(openChatId = 7L, isAppInForeground = false)
        )
        assertTrue(decision is NotificationDecision.Notify)
    }

    @Test
    fun `a message already shown does not notify again`() {
        val decision = decideNotification(
            message(messageId = 55L),
            NotificationContext(alreadyNotified = setOf(55L))
        )
        assertEquals(
            NotificationDecision.Suppress(SuppressionReason.AlreadyNotified),
            decision
        )
    }

    @Test
    fun `messages of one chat become a single entry`() {
        val grouped = groupForShade(
            listOf(
                message(chatId = 1L, messageId = 1L, timestampMillis = 10L),
                message(chatId = 1L, messageId = 2L, timestampMillis = 20L)
            )
        )
        assertEquals(1, grouped.size)
        assertEquals(2, grouped.single().count)
    }

    @Test
    fun `within a chat the oldest message comes first`() {
        val grouped = groupForShade(
            listOf(
                message(chatId = 1L, messageId = 2L, timestampMillis = 20L),
                message(chatId = 1L, messageId = 1L, timestampMillis = 10L)
            )
        )
        assertEquals(listOf(1L, 2L), grouped.single().messages.map { it.messageId })
    }

    @Test
    fun `the chat that spoke most recently is posted last`() {
        val grouped = groupForShade(
            listOf(
                message(chatId = 1L, messageId = 1L, timestampMillis = 30L),
                message(chatId = 2L, messageId = 2L, timestampMillis = 10L)
            )
        )
        assertEquals(listOf(2L, 1L), grouped.map { it.chatId })
    }

    @Test
    fun `an empty list groups to nothing`() {
        assertEquals(emptyList<ChatNotification>(), groupForShade(emptyList()))
    }

    @Test
    fun `a summary names the sender when there is one`() {
        assertEquals("Ada: hello", summaryLine(message()))
    }

    @Test
    fun `a summary without a sender is just the text`() {
        // A one-to-one chat: the sender is the chat, so repeating the name
        // spends a line saying nothing.
        assertEquals("hello", summaryLine(message(senderName = null)))
    }

    @Test
    fun `a blank sender counts as no sender`() {
        assertEquals("hello", summaryLine(message(senderName = "   ")))
    }
}
