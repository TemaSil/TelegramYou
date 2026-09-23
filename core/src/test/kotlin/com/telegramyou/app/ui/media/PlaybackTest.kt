package com.telegramyou.app.ui.media

import com.telegramyou.app.ui.chat.formatDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackTest {

    @Test
    fun `progress is the position over the duration`() {
        assertEquals(0.5f, playbackProgress(2_000, 4_000)!!, 0.0001f)
        assertEquals(0f, playbackProgress(0, 4_000)!!, 0.0001f)
        assertEquals(1f, playbackProgress(4_000, 4_000)!!, 0.0001f)
    }

    @Test
    fun `an unknown duration has no progress to report`() {
        assertNull(playbackProgress(700, DURATION_UNKNOWN))
        assertNull(playbackProgress(700, 0))
    }

    @Test
    fun `a position past the end still reads as the end`() {
        // Players overshoot by a frame or two around the end of a file, and a
        // bar that ran off its own track would be the visible result.
        assertEquals(1f, playbackProgress(4_100, 4_000)!!, 0.0001f)
    }

    @Test
    fun `a drag lands where the fraction says`() {
        assertEquals(2_000L, seekTarget(0.5f, 4_000))
        assertEquals(0L, seekTarget(0f, 4_000))
        assertEquals(4_000L, seekTarget(1f, 4_000))
    }

    @Test
    fun `a drag on a file of unknown length goes to the start`() {
        assertEquals(0L, seekTarget(0.5f, DURATION_UNKNOWN))
    }

    @Test
    fun `a fraction outside the track is clamped rather than trusted`() {
        assertEquals(0L, seekTarget(-0.2f, 4_000))
        assertEquals(4_000L, seekTarget(1.4f, 4_000))
    }

    @Test
    fun `the label is position out of duration`() {
        assertEquals("0:07 / 1:00", playbackLabel(7_400, 60_000, ::formatDuration))
    }

    @Test
    fun `an unknown duration shows the position alone`() {
        assertEquals("0:07", playbackLabel(7_400, DURATION_UNKNOWN, ::formatDuration))
    }
}
