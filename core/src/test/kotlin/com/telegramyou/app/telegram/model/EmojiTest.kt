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

    @Test
    fun `picked emoji are placed in order, in UTF-16 units`() {
        val picked = listOf(PickedEmoji("🦄", 7L), PickedEmoji("✨", 9L))
        val placed = placePickedEmoji("ok 🦄 and ✨", picked)
        assertEquals(listOf(PlacedEmoji(3, 2, 7L), PlacedEmoji(10, 1, 9L)), placed)
    }

    @Test
    fun `the same emoji twice takes each in turn, and a deleted one is skipped`() {
        val picked = listOf(PickedEmoji("🦄", 1L), PickedEmoji("🦄", 2L), PickedEmoji("🌈", 3L))
        assertEquals(
            listOf(PlacedEmoji(0, 2, 1L), PlacedEmoji(2, 2, 2L)),
            placePickedEmoji("🦄🦄", picked)
        )
    }
}
