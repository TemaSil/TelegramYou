package com.telegramyou.app.ui.chat

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoEditTest {

    private fun assertRect(expected: CropRect, actual: CropRect) {
        assertEquals(expected.left, actual.left, 1e-5f)
        assertEquals(expected.top, actual.top, 1e-5f)
        assertEquals(expected.right, actual.right, 1e-5f)
        assertEquals(expected.bottom, actual.bottom, 1e-5f)
    }

    @Test
    fun `an untouched photo is no edit, a turn or a crop is one`() {
        assertTrue(PhotoEdit().isNone)
        assertFalse(PhotoEdit().turnedLeft().isNone)
        assertFalse(PhotoEdit(crop = CropRect(0.1f, 0f, 1f, 1f)).isNone)
    }

    @Test
    fun `four turns come back round, the frame with them`() {
        val start = PhotoEdit(crop = CropRect(0.1f, 0.2f, 0.6f, 0.9f))
        val turned = start.turnedLeft().turnedLeft().turnedLeft().turnedLeft()
        assertEquals(0, turned.quarterTurns)
        assertRect(start.crop, turned.crop)
    }

    @Test
    fun `a frame turns with the picture under it`() {
        // The top-left quarter of the picture, turned left, is its bottom-left.
        assertRect(CropRect(0f, 0.5f, 0.5f, 1f), CropRect(0f, 0f, 0.5f, 0.5f).turnedLeft())
    }

    @Test
    fun `a corner stops short of the opposite one and of the edge`() {
        val dragged = CropRect().dragged(CropHandle.TopLeft, 2f, -1f)
        assertRect(CropRect(1f - CropRect.MIN_CROP, 0f, 1f, 1f), dragged)
    }

    @Test
    fun `the frame moves whole and stays on the picture`() {
        val moved = CropRect(0.2f, 0.2f, 0.6f, 0.6f).dragged(CropHandle.Inside, 1f, -1f)
        assertRect(CropRect(0.6f, 0f, 1f, 0.4f), moved)
    }

    @Test
    fun `a finger takes the nearest corner, the inside, or nothing`() {
        val rect = CropRect(0.2f, 0.2f, 0.8f, 0.8f)
        assertEquals(CropHandle.BottomRight, rect.handleAt(0.78f, 0.82f, 0.05f, 0.05f))
        assertEquals(CropHandle.Inside, rect.handleAt(0.5f, 0.5f, 0.05f, 0.05f))
        assertNull(rect.handleAt(0.05f, 0.5f, 0.05f, 0.05f))
    }

    @Test
    fun `pixels are never empty and never past the picture`() {
        assertArrayEquals(intArrayOf(0, 0, 100, 50), CropRect().inPixels(100, 50))
        assertArrayEquals(intArrayOf(99, 49, 1, 1), CropRect(1f, 1f, 1f, 1f).inPixels(100, 50))
    }
}
