package com.telegramyou.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchHighlightTest {

    @Test
    fun `a blank query matches nothing`() {
        assertTrue(matchRanges("anything at all", "   ").isEmpty())
    }

    @Test
    fun `matching ignores case`() {
        assertEquals(listOf(0 until 5), matchRanges("Hello there", "hello"))
    }

    @Test
    fun `every occurrence is found`() {
        assertEquals(
            listOf(0 until 2, 7 until 9),
            matchRanges("on and on", "on")
        )
    }

    @Test
    fun `matches do not overlap`() {
        // "aa" in "aaaa" is two matches, not three: a highlight starting
        // inside another one has nothing to draw.
        assertEquals(listOf(0 until 2, 2 until 4), matchRanges("aaaa", "aa"))
    }

    @Test
    fun `a query longer than the text matches nothing`() {
        assertTrue(matchRanges("hi", "hello").isEmpty())
    }

    @Test
    fun `a short message is its own snippet`() {
        val result = snippet("hello there", "there")

        assertEquals("hello there", result.text)
        assertEquals(6, result.matchStart)
        assertEquals(5, result.matchLength)
    }

    @Test
    fun `a match late in a long message is brought into view`() {
        val text = "x".repeat(200) + "needle" + "y".repeat(200)

        val result = snippet(text, "needle")

        assertTrue("the match survives the trim", "needle" in result.text)
        assertTrue("both ends are marked as cut", result.text.startsWith("…"))
        assertTrue(result.text.endsWith("…"))
        assertEquals(
            "the offset still points at the match",
            "needle",
            result.text.substring(result.matchStart, result.matchStart + result.matchLength)
        )
    }

    @Test
    fun `a message with no match is simply truncated`() {
        val result = snippet("a".repeat(200), "zzz", length = 10)

        assertEquals(10, result.text.length)
        assertEquals(0, result.matchLength)
    }
}
