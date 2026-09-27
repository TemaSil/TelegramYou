package com.telegramyou.app.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebmTest {

    /** Encoded the way Telegram's stickers are: VP9, yuva420p, alpha in BlockAdditional. */
    private val sticker = javaClass.getResourceAsStream("/video-sticker.webm")!!.readBytes()

    @Test
    fun `the track, every frame and its alpha`() {
        val video = parseWebm(sticker)!!
        assertEquals("V_VP9", video.codec)
        assertEquals(512, video.width)
        assertEquals(512, video.height)
        assertEquals(60, video.frames.size)
        assertTrue("every frame carries its transparency", video.frames.all { it.alpha != null })
        assertTrue(video.frames.first().key)
    }

    @Test
    fun `times start at zero and move at thirty a second`() {
        val frames = parseWebm(sticker)!!.frames
        assertEquals(0L, frames.first().timeMs)
        assertTrue(frames.zipWithNext().all { (a, b) -> b.timeMs > a.timeMs })
        assertTrue(frames.last().timeMs in 1900L..2000L)
    }

    @Test
    fun `not a webm`() {
        assertNull(parseWebm(ByteArray(64) { 7 }))
        assertNull(parseWebm(ByteArray(0)))
    }
}
