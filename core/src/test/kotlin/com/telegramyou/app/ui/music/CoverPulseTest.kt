package com.telegramyou.app.ui.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class CoverPulseTest {

    @Test
    fun `a steady sound is not a beat, however loud`() {
        val beat = BeatFollower()
        var last = 1f
        repeat(500) { last = beat.next(0.8f, 20f) }
        assertTrue("held at $last", last < 0.05f)
    }

    @Test
    fun `a hit after quiet is a full beat, and falls away`() {
        val beat = BeatFollower()
        repeat(100) { beat.next(0.05f, 20f) }
        val hit = beat.next(0.6f, 20f)
        assertEquals(1f, hit, 0.001f)
        var after = hit
        repeat(10) { after = beat.next(0.05f, 20f) }
        assertTrue("still $after after 200 ms", after in 0.2f..0.5f)
    }

    @Test
    fun `quiet music beats as fully as loud music`() {
        // A kick every 400 ms over a bed a fifth as loud, at two volumes.
        fun hits(volume: Float): Float {
            val beat = BeatFollower()
            var last = 0f
            repeat(20) {
                repeat(19) { beat.next(volume * 0.2f, 20f) }
                last = beat.next(volume, 20f)
            }
            return last
        }
        assertEquals(hits(0.9f), hits(0.1f), 0.02f)
        assertTrue(hits(0.1f) > 0.9f)
    }

    @Test
    fun `silence never beats`() {
        val beat = BeatFollower()
        repeat(200) { assertEquals(0f, beat.next(it % 7 * 0.0005f, 20f), 0.0001f) }
    }

    @Test
    fun `loudness of full scale and of nothing`() {
        assertEquals(1f, loudness16(ShortArray(64) { if (it % 2 == 0) Short.MAX_VALUE else Short.MIN_VALUE }), 0.001f)
        assertEquals(0f, loudness16(ShortArray(64)), 0f)
    }

    @Test
    fun `the square and the cookie are the same vertices at two depths`() {
        val square = scallopedSquare(3, depth = 0f)
        val cookie = scallopedSquare(3, depth = 0.1f)
        assertEquals(square.size, cookie.size)
        assertEquals(4 * 6 * 2, square.size)
        // Every vertex of the square is on its edge.
        for (i in square.indices step 2) {
            assertTrue(abs(square[i]) == 1f || abs(square[i + 1]) == 1f)
        }
    }

    @Test
    fun `corners and bulges stay put, dents go in by the depth`() {
        val square = scallopedSquare(3, depth = 0f)
        val cookie = scallopedSquare(3, depth = 0.1f)
        var moved = 0
        for (v in 0 until square.size / 2) {
            val dx = cookie[v * 2] - square[v * 2]
            val dy = cookie[v * 2 + 1] - square[v * 2 + 1]
            val distance = hypot(dx, dy)
            if (v % 6 % 2 == 1) {
                assertEquals(0.1f, distance, 0.0001f)
                moved++
            } else {
                assertEquals(0f, distance, 0f)
            }
            assertTrue(cookie[v * 2] in -1f..1f && cookie[v * 2 + 1] in -1f..1f)
        }
        assertEquals(12, moved)
        // The first vertex is the top-left corner.
        assertEquals(-1f, cookie[0], 0f)
        assertEquals(-1f, cookie[1], 0f)
    }
}
