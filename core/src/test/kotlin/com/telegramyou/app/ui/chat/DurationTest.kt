package com.telegramyou.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationTest {

    @Test
    fun `seconds are padded and minutes are not`() {
        assertEquals("0:07", formatDuration(7))
        assertEquals("1:05", formatDuration(65))
    }

    @Test
    fun `a whole minute is not shown as sixty seconds`() {
        assertEquals("1:00", formatDuration(60))
    }

    @Test
    fun `past an hour it grows rather than counting past sixty minutes`() {
        // "90:00" is a duration nobody reads correctly at a glance.
        assertEquals("1:30:00", formatDuration(90 * 60))
    }

    @Test
    fun `nothing recorded yet reads as zero`() {
        assertEquals("0:00", formatDuration(0))
    }

    @Test
    fun `a negative duration cannot be rendered and is clamped`() {
        assertEquals("0:00", formatDuration(-5))
    }
}
