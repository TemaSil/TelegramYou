package com.telegramyou.app.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniThumbnailTest {

    @Test
    fun `a flat picture stays flat, edges included`() {
        val teal = 0xFF1EE2A8.toInt()
        val flat = IntArray(40 * 30) { teal }
        assertArrayEquals(flat, MiniThumbnail.blur(flat, 40, 30))
    }

    @Test
    fun `a bright point spreads into its neighbours and fades`() {
        val black = 0xFF000000.toInt()
        val pixels = IntArray(9 * 9) { black }
        pixels[4 * 9 + 4] = 0xFFFFFFFF.toInt()
        val blurred = MiniThumbnail.blur(pixels, 9, 9, radius = 1, passes = 1)
        val centre = blurred[4 * 9 + 4] and 0xFF
        val beside = blurred[4 * 9 + 5] and 0xFF
        val far = blurred[0] and 0xFF
        assertTrue("the point dims", centre in 1 until 255)
        assertEquals("its neighbour takes as much", centre, beside)
        assertEquals("the corner is untouched", 0, far)
        assertEquals("still opaque", 0xFF, blurred[4 * 9 + 5] ushr 24)
    }

    @Test
    fun `the input is left alone`() {
        val pixels = IntArray(4) { it * 0x00101010 or (0xFF shl 24) }
        val before = pixels.copyOf()
        MiniThumbnail.blur(pixels, 2, 2)
        assertArrayEquals(before, pixels)
    }
}
