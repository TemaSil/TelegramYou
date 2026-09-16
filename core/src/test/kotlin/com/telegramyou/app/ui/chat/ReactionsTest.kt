package com.telegramyou.app.ui.chat

import com.telegramyou.app.telegram.model.MessageReaction
import org.junit.Assert.assertEquals
import org.junit.Test

class ReactionsTest {

    @Test
    fun `first reaction on a bare message creates the chip`() {
        val result = toggleReaction(emptyList(), "👍")

        assertEquals(listOf(MessageReaction("👍", count = 1, isChosen = true)), result)
    }

    @Test
    fun `joining an existing reaction increments it`() {
        val existing = listOf(MessageReaction("👍", count = 3, isChosen = false))

        val result = toggleReaction(existing, "👍")

        assertEquals(listOf(MessageReaction("👍", count = 4, isChosen = true)), result)
    }

    @Test
    fun `tapping our own reaction takes it back`() {
        val existing = listOf(MessageReaction("👍", count = 4, isChosen = true))

        val result = toggleReaction(existing, "👍")

        assertEquals(listOf(MessageReaction("👍", count = 3, isChosen = false)), result)
    }

    @Test
    fun `withdrawing the only reaction removes the chip entirely`() {
        val existing = listOf(MessageReaction("👍", count = 1, isChosen = true))

        assertEquals(emptyList<MessageReaction>(), toggleReaction(existing, "👍"))
    }

    @Test
    fun `choosing a second emoji moves the choice rather than adding one`() {
        val existing = listOf(
            MessageReaction("👍", count = 2, isChosen = true),
            MessageReaction("🔥", count = 5, isChosen = false)
        )

        val result = toggleReaction(existing, "🔥")

        assertEquals(
            listOf(
                MessageReaction("👍", count = 1, isChosen = false),
                MessageReaction("🔥", count = 6, isChosen = true)
            ),
            result
        )
    }

    @Test
    fun `moving off a reaction we were alone on drops it`() {
        val existing = listOf(
            MessageReaction("👍", count = 1, isChosen = true),
            MessageReaction("🔥", count = 5, isChosen = false)
        )

        val result = toggleReaction(existing, "🔥")

        assertEquals(listOf(MessageReaction("🔥", count = 6, isChosen = true)), result)
    }

    @Test
    fun `a new emoji lands at the end, not in sorted position`() {
        val existing = listOf(
            MessageReaction("🔥", count = 9, isChosen = false),
            MessageReaction("👍", count = 4, isChosen = false)
        )

        val result = toggleReaction(existing, "❤️")

        assertEquals(
            listOf(
                MessageReaction("🔥", count = 9, isChosen = false),
                MessageReaction("👍", count = 4, isChosen = false),
                MessageReaction("❤️", count = 1, isChosen = true)
            ),
            result
        )
    }

    @Test
    fun `reactions we never chose are left alone`() {
        val existing = listOf(
            MessageReaction("🔥", count = 9, isChosen = false),
            MessageReaction("👍", count = 4, isChosen = false)
        )

        val result = toggleReaction(existing, "👍")

        assertEquals(
            listOf(
                MessageReaction("🔥", count = 9, isChosen = false),
                MessageReaction("👍", count = 5, isChosen = true)
            ),
            result
        )
    }
}
