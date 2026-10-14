package com.telegramyou.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OwnActivityTest {

    @Test
    fun `typing is said once, then again only after a while`() {
        val pacer = OwnActivityPacer(repeatAfterMs = 4_500)
        assertEquals(OwnActivity.Typing, pacer.update(OwnActivity.Typing, 0))
        assertNull(pacer.update(OwnActivity.Typing, 1_000))
        assertNull(pacer.update(OwnActivity.Typing, 4_000))
        assertEquals(OwnActivity.Typing, pacer.update(OwnActivity.Typing, 4_600))
    }

    @Test
    fun `a change of activity is said at once`() {
        val pacer = OwnActivityPacer()
        pacer.update(OwnActivity.Typing, 0)
        assertEquals(OwnActivity.RecordingVoice, pacer.update(OwnActivity.RecordingVoice, 100))
    }

    @Test
    fun `stopping cancels only what was said`() {
        val pacer = OwnActivityPacer()
        assertNull(pacer.update(OwnActivity.None, 0))
        pacer.update(OwnActivity.Typing, 0)
        assertEquals(OwnActivity.None, pacer.update(OwnActivity.None, 500))
        assertNull(pacer.update(OwnActivity.None, 600))
    }

    @Test
    fun `a sent message ends it without a cancel`() {
        val pacer = OwnActivityPacer()
        pacer.update(OwnActivity.Typing, 0)
        pacer.sent()
        assertNull(pacer.update(OwnActivity.None, 100))
        assertEquals(OwnActivity.Typing, pacer.update(OwnActivity.Typing, 200))
    }
}
