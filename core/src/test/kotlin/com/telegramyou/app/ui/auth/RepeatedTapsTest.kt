package com.telegramyou.app.ui.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepeatedTapsTest {

    @Test
    fun `ten quick taps complete a run, and only the tenth says so`() {
        val taps = RepeatedTaps(required = 10)
        val answers = (0 until 10).map { taps.tap(it * 200L) }
        assertEquals(List(9) { false } + true, answers)
    }

    @Test
    fun `a pause starts the count again`() {
        val taps = RepeatedTaps(required = 3, gapMillis = 500)
        assertFalse(taps.tap(0))
        assertFalse(taps.tap(300))
        assertFalse("too late: this is the first of a new run", taps.tap(2_000))
        assertFalse(taps.tap(2_200))
        assertTrue(taps.tap(2_400))
    }

    @Test
    fun `after a completed run the next one needs all its taps`() {
        val taps = RepeatedTaps(required = 2)
        assertFalse(taps.tap(0))
        assertTrue(taps.tap(100))
        assertFalse(taps.tap(200))
        assertTrue(taps.tap(300))
    }
}
