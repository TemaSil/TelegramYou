package com.telegramyou.app.ui.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomPanTest {

    private val width = 1000f
    private val height = 2000f

    private fun gesture(
        from: Zoom,
        scaleChange: Float = 1f,
        panX: Float = 0f,
        panY: Float = 0f
    ) = zoomAfterGesture(from, scaleChange, panX, panY, width, height)

    @Test
    fun `a photo starts unzoomed and centred`() {
        val zoom = Zoom()
        assertEquals(1f, zoom.scale, 0f)
        assertEquals(0f, zoom.offsetX, 0f)
        assertFalse(zoom.isZoomed)
    }

    @Test
    fun `pinching in multiplies the scale`() {
        assertEquals(2f, gesture(Zoom(), scaleChange = 2f).scale, 0f)
    }

    @Test
    fun `scale cannot go past the maximum`() {
        assertEquals(MAX_SCALE, gesture(Zoom(scale = 4f), scaleChange = 10f).scale, 0f)
    }

    @Test
    fun `pinching out below one snaps back and drops the offset`() {
        // Not merely clamped to 1: an offset kept from a zoomed state would
        // leave the photo off-centre with nothing to move it back.
        val result = gesture(Zoom(scale = 2f, offsetX = 300f, offsetY = -200f), scaleChange = 0.1f)
        assertEquals(Zoom(), result)
    }

    @Test
    fun `panning does nothing at all while fully zoomed out`() {
        val result = gesture(Zoom(), panX = 500f, panY = 500f)
        assertEquals(0f, result.offsetX, 0f)
        assertEquals(0f, result.offsetY, 0f)
    }

    @Test
    fun `panning inside the overhang moves the photo`() {
        // At scale 2 the content is one viewport wider than the frame, so it
        // can move half of one in each direction: 500 here.
        val result = gesture(Zoom(scale = 2f), panX = 100f, panY = -200f)
        assertEquals(100f, result.offsetX, 0f)
        assertEquals(-200f, result.offsetY, 0f)
    }

    @Test
    fun `panning past the edge stops at it`() {
        val result = gesture(Zoom(scale = 2f), panX = 9_000f, panY = 9_000f)
        assertEquals(500f, result.offsetX, 0f)
        assertEquals(1000f, result.offsetY, 0f)
    }

    @Test
    fun `the far edge is bounded too`() {
        val result = gesture(Zoom(scale = 2f), panX = -9_000f)
        assertEquals(-500f, result.offsetX, 0f)
    }

    @Test
    fun `zooming out re-clamps an offset that is now too large`() {
        // Legal at scale 4, off the edge at scale 2 — which is the case a
        // clamp applied only while panning would miss.
        val result = gesture(Zoom(scale = 4f, offsetX = 1_400f), scaleChange = 0.5f)
        assertEquals(2f, result.scale, 0f)
        assertEquals(500f, result.offsetX, 0f)
    }

    @Test
    fun `double tap zooms in from rest and all the way out from anywhere`() {
        assertEquals(DOUBLE_TAP_SCALE, zoomToggled(Zoom()).scale, 0f)
        assertEquals(Zoom(), zoomToggled(Zoom(scale = 3.4f, offsetX = 120f)))
    }

    @Test
    fun `a short drag is only part of the way to dismissing`() {
        // A fifth of the height is the whole gesture, so a tenth is half.
        assertEquals(0.5f, dismissProgress(200f, height), 0.001f)
        assertFalse(shouldDismiss(200f, height))
    }

    @Test
    fun `dragging far enough dismisses`() {
        assertTrue(shouldDismiss(400f, height))
        assertEquals(1f, dismissProgress(400f, height), 0f)
    }

    @Test
    fun `dragging up counts the same as dragging down`() {
        assertEquals(dismissProgress(300f, height), dismissProgress(-300f, height), 0f)
        assertTrue(shouldDismiss(-400f, height))
    }

    @Test
    fun `progress never exceeds one`() {
        assertEquals(1f, dismissProgress(50_000f, height), 0f)
    }

    @Test
    fun `a viewport with no height reports no progress`() {
        // Reachable on the first frame, before anything has been measured.
        assertEquals(0f, dismissProgress(100f, 0f), 0f)
        assertFalse(shouldDismiss(100f, 0f))
    }
}
