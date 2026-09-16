package com.telegramyou.app.ui.chat

import com.telegramyou.app.telegram.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageSelectionTest {

    private fun message(
        id: Long,
        text: String = "m$id",
        sender: String? = null,
        deletableForSelf: Boolean = true,
        deletableForEveryone: Boolean = false
    ) = ChatMessage(
        id = id,
        chatId = 1,
        text = text,
        isOutgoing = false,
        timeLabel = "",
        senderName = sender,
        canBeDeletedForSelf = deletableForSelf,
        canBeDeletedForEveryone = deletableForEveryone
    )

    @Test
    fun `an empty selection is not active`() {
        assertFalse(MessageSelection().isActive)
    }

    @Test
    fun `toggling twice returns to nothing selected`() {
        val once = MessageSelection().toggle(7)
        assertTrue(7L in once)
        assertEquals(1, once.count)

        val twice = once.toggle(7)
        assertFalse(twice.isActive)
    }

    @Test
    fun `deselecting the last message ends the selection`() {
        val selection = MessageSelection().toggle(1).toggle(2)
        assertTrue(selection.isActive)

        assertFalse(selection.toggle(1).toggle(2).isActive)
    }

    @Test
    fun `a message that disappeared is dropped from the selection`() {
        val selection = MessageSelection().toggle(1).toggle(2)

        // Someone else deleted 2 while it was selected.
        val kept = selection.retaining(listOf(message(1)))

        assertEquals(setOf(1L), kept.ids)
    }

    @Test
    fun `delete for everyone needs every message to allow it`() {
        val actions = selectionActions(
            listOf(
                message(1, deletableForEveryone = true),
                message(2, deletableForEveryone = false)
            )
        )

        assertFalse("one message that cannot be withdrawn disables it",
            actions.canDeleteForEveryone)
        assertTrue(actions.canDeleteForSelf)
    }

    @Test
    fun `an empty selection offers nothing`() {
        assertEquals(SelectionActions(), selectionActions(emptyList()))
    }

    @Test
    fun `copy is offered when any message has text`() {
        val actions = selectionActions(listOf(message(1, text = ""), message(2)))

        assertTrue(actions.canCopy)
    }

    @Test
    fun `copy is not offered for a selection with no text at all`() {
        val actions = selectionActions(listOf(message(1, text = ""), message(2, text = "")))

        assertFalse(actions.canCopy)
    }

    @Test
    fun `copied text keeps the order and names the senders`() {
        val text = copyText(
            listOf(
                message(1, text = "first", sender = "Lina"),
                message(2, text = "", sender = "Lina"),
                message(3, text = "second", sender = null)
            )
        )

        assertEquals("Lina: first\nsecond", text)
    }
}
