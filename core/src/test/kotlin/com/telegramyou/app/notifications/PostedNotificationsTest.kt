package com.telegramyou.app.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PostedNotificationsTest {

    // The store is one object for the process, so each test takes chat ids
    // of its own rather than depending on the order tests run in.
    private fun message(chatId: Long, id: Long) = NotifiableMessage(
        chatId = chatId,
        messageId = id,
        chatTitle = "Chat",
        senderName = null,
        text = "m$id",
        timestampMillis = id,
        isOutgoing = false,
        isChatMuted = false
    )

    @Test
    fun `the same message offered twice is posted once`() {
        val first = PostedNotifications.add(message(chatId = 101, id = 1), maxLines = 6)
        val again = PostedNotifications.add(message(chatId = 101, id = 1), maxLines = 6)

        assertEquals(listOf(1L), first?.map { it.messageId })
        assertNull("nothing new to post the second time", again)
        PostedNotifications.clear(101)
    }

    @Test
    fun `a chat keeps its last few lines`() {
        (1L..4L).forEach { PostedNotifications.add(message(chatId = 102, id = it), maxLines = 3) }
        val lines = PostedNotifications.add(message(chatId = 102, id = 5), maxLines = 3)

        assertEquals(listOf(3L, 4L, 5L), lines?.map { it.messageId })
        PostedNotifications.clear(102)
    }

    @Test
    fun `supergroups that differ only in their high digits get different ids`() {
        // Two ids 2^32 apart: toInt() would give both the same notification,
        // and the second group's messages would overwrite the first's.
        val a = -1_001_234_567_890L
        val b = a - (1L shl 32)
        assertEquals(a.toInt(), b.toInt())
        assertNotEquals(
            PostedNotifications.notificationId(a),
            PostedNotifications.notificationId(b)
        )
    }
}
