package com.telegramyou.app.telegram.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveformTest {

    @Test
    fun `an empty waveform unpacks to nothing`() {
        assertTrue(unpackWaveform(ByteArray(0)).isEmpty())
    }

    @Test
    fun `the first sample is the top five bits of the first byte`() {
        // 0b11111_000 — a full-height bar followed by the start of a silent one.
        val bytes = byteArrayOf(0xF8.toByte())
        assertEquals(listOf(31), unpackWaveform(bytes))
    }

    @Test
    fun `a sample straddling a byte boundary is read across it`() {
        // 0b00000_111, 0b11_000000 — the second sample's bits are split 3 and
        // 2 across the two bytes, and together they are 31.
        val bytes = byteArrayOf(0x07, 0xC0.toByte())
        assertEquals(listOf(0, 31, 0), unpackWaveform(bytes))
    }

    @Test
    fun `packing and unpacking round-trips`() {
        val samples = listOf(0, 1, 7, 15, 16, 30, 31, 3, 12, 25, 8)

        assertEquals(samples, unpackWaveform(packWaveform(samples)).take(samples.size))
    }

    @Test
    fun `trailing bits too few for a sample are dropped, not padded`() {
        // Three samples fill 15 bits; the two bits left in the second byte are
        // the remainder of a byte, not a quiet moment at the end.
        val packed = packWaveform(listOf(31, 31, 31))

        assertEquals(2, packed.size)
        assertEquals(listOf(31, 31, 31), unpackWaveform(packed))
    }

    @Test
    fun `a value past the five-bit range is clamped rather than refused`() {
        assertEquals(listOf(31), unpackWaveform(packWaveform(listOf(9_000))))
    }

    @Test
    fun `bars reach the end of a recording that does not divide evenly`() {
        // Ten samples into three bars: the last bar has to include the last
        // sample however the division falls.
        val samples = listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 31)

        val bars = waveformBars(samples, bars = 3)

        assertEquals(3, bars.size)
        assertTrue("the loud end is drawn", bars.last() > 0f)
    }

    @Test
    fun `bars are normalised against the loudest sample, not against 31`() {
        // A quiet recording drawn against the theoretical maximum is a flat
        // line, and the shape is the thing a waveform is for.
        val quiet = listOf(1, 2, 3, 4)

        val bars = waveformBars(quiet, bars = 4)

        assertEquals(1f, bars.max(), 0.0001f)
        assertTrue(bars.first() < bars.last())
    }

    @Test
    fun `a message with no waveform draws a flat row rather than nothing`() {
        val bars = waveformBars(emptyList(), bars = 5)

        assertEquals(5, bars.size)
        assertTrue(bars.all { it == 0f })
    }

    @Test
    fun `asking for no bars answers with none`() {
        assertTrue(waveformBars(listOf(1, 2, 3), bars = 0).isEmpty())
    }
}
