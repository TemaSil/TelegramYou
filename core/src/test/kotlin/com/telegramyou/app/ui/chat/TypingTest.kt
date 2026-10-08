package com.telegramyou.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class TypingTest {

    @Test
    fun `a private chat says only that someone is typing`() {
        assertEquals("typing", typingLabel(emptyList()))
    }

    @Test
    fun `a group says who`() {
        assertEquals("Lina is typing", typingLabel(listOf("Lina")))
        assertEquals("Lina and Artem are typing", typingLabel(listOf("Lina", "Artem")))
        assertEquals("Lina and 2 others are typing", typingLabel(listOf("Lina", "Artem", "Noor")))
    }
}
