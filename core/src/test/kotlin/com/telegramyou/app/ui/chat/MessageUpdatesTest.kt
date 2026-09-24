package com.telegramyou.app.ui.chat

import com.telegramyou.app.telegram.model.ChatMessage
import com.telegramyou.app.telegram.model.MessageReaction
import com.telegramyou.app.telegram.model.MessageUpdate
import com.telegramyou.app.telegram.model.SendState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageUpdatesTest {

    private fun message(
        id: Long,
        text: String = "m$id",
        outgoing: Boolean = false,
        read: Boolean = false
    ) = ChatMessage(
        id = id,
        chatId = 1,
        text = text,
        isOutgoing = outgoing,
        timeLabel = "",
        isRead = read
    )

    private val window = listOf(message(1), message(2), message(3))

    @Test
    fun `a new message goes on the end, once`() {
        val added = MessageUpdate.Added(message(4))
        val once = window.applying(added)
        assertEquals(listOf(1L, 2L, 3L, 4L), once.map { it.id })
        assertEquals(once, once.applying(added))
    }

    @Test
    fun `a new message stays out of the history above the newest page`() {
        assertSame(window, window.applying(MessageUpdate.Added(message(4)), appendNew = false))
    }

    @Test
    fun `a confirmed send takes the place of its temporary copy`() {
        val sending = window + message(9_000_001, "hi", outgoing = true)
        val confirmed = sending.applying(
            MessageUpdate.Replaced(9_000_001, message(4, "hi", outgoing = true))
        )
        assertEquals(listOf(1L, 2L, 3L, 4L), confirmed.map { it.id })
    }

    @Test
    fun `a confirmation that arrives after the real id is not a duplicate`() {
        val both = window + message(9_000_001, "hi") + message(4, "hi")
        val confirmed = both.applying(MessageUpdate.Replaced(9_000_001, message(4, "hi")))
        assertEquals(listOf(1L, 2L, 3L, 4L), confirmed.map { it.id })
    }

    @Test
    fun `a refused send stays on screen, marked failed, under its new id`() {
        val sending = window + message(9_000_001, "pic", outgoing = true)
            .copy(sendState = SendState.Pending)
        val refused = message(9_000_002, "pic", outgoing = true).copy(sendState = SendState.Failed)
        val failed = sending.applying(MessageUpdate.SendFailed(9_000_001, refused, "FILE_PARTS_INVALID"))
        assertEquals(listOf(1L, 2L, 3L, 9_000_002L), failed.map { it.id })
        assertEquals(SendState.Failed, failed.last().sendState)
        assertEquals("repeated, it changes nothing", failed,
            failed.applying(MessageUpdate.SendFailed(9_000_001, refused, "FILE_PARTS_INVALID")))
    }

    @Test
    fun `deleting removes every named message and ignores the rest`() {
        val after = window.applying(MessageUpdate.Deleted(1, setOf(1, 3, 99)))
        assertEquals(listOf(2L), after.map { it.id })
        assertSame(window, window.applying(MessageUpdate.Deleted(1, setOf(99))))
    }

    @Test
    fun `an edit changes the text and marks it edited`() {
        val after = window.applying(MessageUpdate.Edited(1, 2, "fixed"))
        assertEquals("fixed", after[1].text)
        assertTrue(after[1].isEdited)
        assertEquals(after, after.applying(MessageUpdate.Edited(1, 2, "fixed")))
    }

    @Test
    fun `reactions are replaced by what the server says`() {
        val reactions = listOf(MessageReaction("🔥", 2, isChosen = true))
        val after = window.applying(MessageUpdate.ReactionsChanged(1, 3, reactions))
        assertEquals(reactions, after[2].reactions)
    }

    @Test
    fun `reading up to an id reads our messages at or below it and nothing else`() {
        val mixed = listOf(
            message(1, outgoing = true),
            message(2, outgoing = false),
            message(3, outgoing = true),
            message(4, outgoing = true)
        )
        val after = mixed.applying(MessageUpdate.ReadUpTo(1, lastReadId = 3))
        assertEquals(listOf(true, false, true, false), after.map { it.isRead })
        assertFalse(after[1].isRead)
    }

    @Test
    fun `an update about a message not in the window changes nothing`() {
        assertSame(window, window.applying(MessageUpdate.Edited(1, 42, "x")))
        assertSame(window, window.applying(MessageUpdate.Replaced(42, message(43))))
    }
}
