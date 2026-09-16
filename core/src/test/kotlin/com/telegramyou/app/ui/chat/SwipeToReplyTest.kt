package com.telegramyou.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeToReplyTest {

    @Test
    fun `the bubble follows the finger up to the maximum`() {
        assertEquals(0f, swipeOffset(0f, maxOffset = 64f), 0.01f)
        assertEquals(30f, swipeOffset(30f, maxOffset = 64f), 0.01f)
        assertEquals(64f, swipeOffset(64f, maxOffset = 64f), 0.01f)
    }

    @Test
    fun `past the maximum it keeps moving, but slowly`() {
        // 64 of travel plus 40 more at a quarter rate.
        assertEquals(74f, swipeOffset(104f, maxOffset = 64f), 0.01f)
        assertTrue(
            "resistance must not become a wall",
            swipeOffset(200f, maxOffset = 64f) > swipeOffset(104f, maxOffset = 64f)
        )
    }

    @Test
    fun `dragging the wrong way does nothing at all`() {
        // Not a negative offset: a reply swipe goes one way, and a bubble that
        // slides both ways reads as two gestures.
        assertEquals(0f, swipeOffset(-10f, maxOffset = 64f), 0.01f)
        assertEquals(0f, swipeOffset(-200f, maxOffset = 64f), 0.01f)
    }

    @Test
    fun `the reply fires at the threshold, not before`() {
        assertFalse(shouldTriggerReply(47.9f, triggerAt = 48f))
        assertTrue("exactly at the mark counts", shouldTriggerReply(48f, triggerAt = 48f))
        assertTrue(shouldTriggerReply(100f, triggerAt = 48f))
    }

    @Test
    fun `the threshold is reachable before resistance starts`() {
        // If the trigger sat beyond the maximum, the gesture could only fire
        // through the damped part — which is the range that feels like the
        // bubble has stopped.
        assertTrue(SwipeToReply.TRIGGER_OFFSET_DP < SwipeToReply.MAX_OFFSET_DP)
        assertTrue(
            shouldTriggerReply(swipeOffset(SwipeToReply.TRIGGER_OFFSET_DP))
        )
    }
}
