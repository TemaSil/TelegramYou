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
    fun `without depth it is the rounded square, inside its bounds`() {
        val points = rippledSquare(100f, 16f, depth = 0f)
        for (i in points.indices step 2) {
            val x = points[i]
            val y = points[i + 1]
            assertTrue(x in -0.001f..100.001f && y in -0.001f..100.001f)
            val onFlat = x in 16f..84f || y in 16f..84f
            if (!onFlat) {
                // A corner: exactly the radius from its centre.
                val cx = if (x < 50) 16f else 84f
                val cy = if (y < 50) 16f else 84f
                assertEquals(16f, hypot(x - cx, y - cy), 0.01f)
            }
        }
    }

    @Test
    fun `ripples go in, never out, and no deeper than asked`() {
        val plain = rippledSquare(100f, 16f, depth = 0f)
        val rippled = rippledSquare(100f, 16f, depth = 6f, phase = 1.3f)
        assertEquals(plain.size, rippled.size)
        var deepest = 0f
        for (i in plain.indices) {
            val moved = abs(rippled[i] - plain[i])
            deepest = maxOf(deepest, moved)
            assertTrue(rippled[i] in -0.001f..100.001f)
        }
        assertTrue("deepest $deepest", deepest in 3f..6.001f)
    }

    @Test
    fun `corners keep their curve while the edges ripple`() {
        val plain = rippledSquare(100f, 16f, depth = 0f)
        val rippled = rippledSquare(100f, 16f, depth = 6f, phase = 0.7f)
        // Each side's corner samples come after its 40 edge samples.
        for (side in 0 until 4) {
            val start = (side * 48 + 40) * 2
            for (i in start until start + 16) assertEquals(plain[i], rippled[i], 0.0001f)
        }
    }
}
