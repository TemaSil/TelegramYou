package com.telegramyou.app.ui.media

import org.junit.Assert.assertEquals
import org.junit.Test

class HoldSeekTest {
    @Test fun `only the edges begin seeking`() {
        assertEquals(-1, holdSeekDirection(100f, 1000f))
        assertEquals(0, holdSeekDirection(500f, 1000f))
        assertEquals(1, holdSeekDirection(900f, 1000f))
        assertEquals(0, holdSeekDirection(Float.NaN, 0f))
    }

    @Test fun `outward movement accelerates in either direction`() {
        assertEquals(2, holdSeekSpeed(700f, 700f, 1000f, 1))
        assertEquals(4, holdSeekSpeed(700f, 730f, 1000f, 1))
        assertEquals(8, holdSeekSpeed(700f, 800f, 1000f, 1))
        assertEquals(16, holdSeekSpeed(300f, 100f, 1000f, -1))
        assertEquals(2, holdSeekSpeed(300f, 500f, 1000f, -1))
    }

    @Test fun `held seeking is bounded and ignores unknown duration`() {
        assertEquals(1400, heldSeekTarget(1000, 10000, 100, 1, 4))
        assertEquals(0, heldSeekTarget(100, 10000, 100, -1, 16))
        assertEquals(10000, heldSeekTarget(9900, 10000, 100, 1, 16))
        assertEquals(1000, heldSeekTarget(1000, -1, 100, 1, 16))
    }
}
