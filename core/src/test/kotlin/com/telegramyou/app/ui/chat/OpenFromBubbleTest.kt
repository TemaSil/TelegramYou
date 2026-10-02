package com.telegramyou.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class OpenFromBubbleTest {

    @Test
    fun `a wide photo is fitted across the width, centred`() {
        assertEquals(MediaFrame(0f, 700f, 1000f, 500f), fitted(2f, 1000f, 1900f))
    }

    @Test
    fun `a tall photo is fitted down the height, centred`() {
        assertEquals(MediaFrame(250f, 0f, 500f, 1000f), fitted(0.5f, 1000f, 1000f))
    }

    @Test
    fun `the page shrinks until its photo covers the bubble`() {
        // A square photo full screen is 1000 wide; a 400 x 200 bubble crops
        // it, so the photo has to be 400 wide to cover it.
        val bubble = MediaFrame(100f, 900f, 400f, 200f)
        assertEquals(0.4f, coverScale(1f, bubble, 1000f, 2000f), 0.0001f)
    }

    @Test
    fun `the clip runs from the bubble to the whole screen`() {
        val bubble = MediaFrame(100f, 900f, 400f, 200f)
        assertEquals(bubble, clipAt(bubble, 1000f, 2000f, 0f))
        assertEquals(MediaFrame(0f, 0f, 1000f, 2000f), clipAt(bubble, 1000f, 2000f, 1f))
        assertEquals(MediaFrame(50f, 450f, 700f, 1100f), clipAt(bubble, 1000f, 2000f, 0.5f))
    }

    @Test
    fun `a spring overshooting below the bubble never turns the clip inside out`() {
        val clip = clipAt(MediaFrame(100f, 900f, 10f, 10f), 1000f, 2000f, -0.5f)
        assertEquals(0f, clip.width, 0f)
        assertEquals(0f, clip.height, 0f)
    }
}
