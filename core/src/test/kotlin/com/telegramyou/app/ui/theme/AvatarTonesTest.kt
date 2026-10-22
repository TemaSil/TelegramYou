package com.telegramyou.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarTonesTest {

    private val red = 0xFFFF6B4A.toInt()
    private val blue = 0xFF6C8CFF.toInt()

    @Test
    fun `different hues stay different`() {
        assertNotEquals(avatarTones(red, dark = false).first, avatarTones(blue, dark = false).first)
        assertNotEquals(avatarTones(red, dark = true).first, avatarTones(blue, dark = true).first)
    }

    @Test
    fun `a light theme's avatar is light, a dark theme's dark`() {
        val light = luminance(avatarTones(red, dark = false).first)
        val dark = luminance(avatarTones(red, dark = true).first)
        assertTrue("light container $light", light > 0.5)
        assertTrue("dark container $dark", dark < 0.25)
    }

    @Test
    fun `the initials read on their container`() {
        listOf(false, true).forEach { dark ->
            val (container, initials) = avatarTones(blue, dark)
            assertTrue("contrast ${contrast(container, initials)}", contrast(container, initials) >= 4.5)
        }
    }

    @Test
    fun `fully opaque, no fade`() {
        assertEquals(0xFF, avatarTones(red, dark = false).first ushr 24)
    }

    private fun channel(c: Int): Double {
        val v = c / 255.0
        return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
    }

    private fun luminance(argb: Int): Double =
        0.2126 * channel(argb shr 16 and 0xFF) + 0.7152 * channel(argb shr 8 and 0xFF) + 0.0722 * channel(argb and 0xFF)

    private fun contrast(a: Int, b: Int): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }
}
