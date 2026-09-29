package com.telegramyou.app.telegram.model

import org.junit.Assert.assertEquals
import org.junit.Test

class EmojiTest {

    @Test
    fun `one to three emoji are jumbo`() {
        assertEquals(1, jumboEmojiCount("🎉"))
        assertEquals(2, jumboEmojiCount("🔥🔥"))
        assertEquals(3, jumboEmojiCount(" 😂👍❤️ "))
    }

    @Test
    fun `joined, toned and flagged emoji count once`() {
        assertEquals(1, jumboEmojiCount("👨‍👩‍👧‍👦"))
        assertEquals(1, jumboEmojiCount("👍🏽"))
        assertEquals(1, jumboEmojiCount("🇺🇦"))
        assertEquals(2, jumboEmojiCount("🇯🇵🇫🇷"))
        assertEquals(1, jumboEmojiCount("1️⃣"))
        assertEquals(1, jumboEmojiCount("🏳️‍🌈"))
    }

    @Test
    fun `words, digits, spaces between and four emoji are not`() {
        assertEquals(0, jumboEmojiCount(""))
        assertEquals(0, jumboEmojiCount("ok 👍"))
        assertEquals(0, jumboEmojiCount("1"))
        assertEquals(0, jumboEmojiCount("🎉 🎉"))
        assertEquals(0, jumboEmojiCount("😀😀😀😀"))
    }
}
