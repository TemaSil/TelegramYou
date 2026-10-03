package com.telegramyou.app.telegram.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LongTextTest {

    private fun split(text: String, max: Int, entities: List<TextSpan<String>> = emptyList()) =
        splitLongText(text, entities, max)

    @Test
    fun `a text that fits is one part, untouched`() {
        val spans = listOf(TextSpan(0, 2, "bold"))
        assertEquals(listOf(TextPart("Hi there", spans)), split("Hi there", 4096, spans))
    }

    @Test
    fun `it cuts at the paragraph break before a line or a word`() {
        val text = "First paragraph here.\n\nSecond one, longer than the rest of it."
        val parts = split(text, 40)
        assertEquals("First paragraph here.", parts[0].text)
        assertTrue(parts[1].text.startsWith("Second one"))
    }

    @Test
    fun `without a break it cuts at a sentence, then at a space`() {
        assertEquals(
            listOf("One sentence.", "Two sentence here."),
            split("One sentence. Two sentence here.", 18).map { it.text }
        )
        assertEquals(listOf("alpha beta", "gamma"), split("alpha beta gamma", 12).map { it.text })
    }

    @Test
    fun `no part is longer than the limit, and nothing is lost but the breaks`() {
        val text = (1..400).joinToString(" ") { "word$it" }
        val parts = split(text, 100)
        assertTrue(parts.all { it.text.length <= 100 })
        assertEquals(text.split(" "), parts.flatMap { it.text.split(" ") })
    }

    @Test
    fun `a word longer than the limit is cut inside it`() {
        assertEquals(listOf("abcde", "fghij", "k"), split("abcdefghijk", 5).map { it.text })
    }

    @Test
    fun `an emoji is never cut in half`() {
        val text = "aaaa😀bbbb"
        val parts = split(text, 5)
        assertEquals("aaaa", parts[0].text)
        assertTrue(parts[1].text.startsWith("😀"))
    }

    @Test
    fun `formatting across a cut is kept on both sides`() {
        // "bold" spans "two three" across the cut after "one two".
        val text = "one two three four"
        val parts = split(text, 8, listOf(TextSpan(4, 9, "bold")))
        assertEquals("one two", parts[0].text)
        assertEquals(listOf(TextSpan(4, 3, "bold")), parts[0].entities)
        assertEquals("three", parts[1].text)
        assertEquals(listOf(TextSpan(0, 5, "bold")), parts[1].entities)
    }

    @Test
    fun `formatting wholly in a later part moves with it`() {
        val text = "one two three four"
        val parts = split(text, 8, listOf(TextSpan(14, 4, "italic")))
        assertEquals("four", parts.last().text)
        assertEquals(listOf(TextSpan(0, 4, "italic")), parts.last().entities)
    }
}
