package com.telegramyou.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ComposerTextTest {

    @Test
    fun `backspace takes a letter`() {
        assertEquals("Hell", dropLastGrapheme("Hello"))
        assertEquals("", dropLastGrapheme("a"))
        assertEquals("", dropLastGrapheme(""))
    }

    @Test
    fun `backspace takes a whole emoji, never half of one`() {
        assertEquals("Hi ", dropLastGrapheme("Hi 😀"))
        assertEquals("👍", dropLastGrapheme("👍😂"))
    }
}
