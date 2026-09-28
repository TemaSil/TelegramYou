package com.telegramyou.app.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceTest {

    @Test
    fun `following the system means following it both ways`() {
        assertTrue(isDark(ThemeChoice.System, systemIsDark = true))
        assertFalse(isDark(ThemeChoice.System, systemIsDark = false))
    }

    @Test
    fun `an explicit choice overrides the system`() {
        // The version of this that reads `choice == Dark || systemIsDark`
        // looks right and ignores exactly this case.
        assertFalse(isDark(ThemeChoice.Light, systemIsDark = true))
        assertTrue(isDark(ThemeChoice.Dark, systemIsDark = false))
    }

    @Test
    fun `dynamic colour needs Android 12`() {
        assertFalse(dynamicColorAvailable(30))
        assertTrue(dynamicColorAvailable(31))
        assertTrue(dynamicColorAvailable(36))
    }

    @Test
    fun `the defaults are system theme and dynamic colour on`() {
        val defaults = AppearanceSettings()

        assertTrue(defaults.dynamicColor)
        assertFalse(isDark(defaults.theme, systemIsDark = false))
        assertTrue(isDark(defaults.theme, systemIsDark = true))
    }

    @Test
    fun `the chat's own look starts as it always was`() {
        val defaults = AppearanceSettings()
        assertEquals(ChatWallpaper.Gradient, defaults.chatWallpaper)
        assertEquals(OutgoingTone.Accent, defaults.outgoingTone)
        assertEquals(20, defaults.bubbleCorners)
        assertEquals(1f, defaults.messageTextScale)
    }

    @Test
    fun `bubble corners settle on even stops inside the range`() {
        assertEquals(20, BubbleCorners.settle(20.4f))
        assertEquals(22, BubbleCorners.settle(21.2f))
        assertEquals(BubbleCorners.MIN, BubbleCorners.settle(0f))
        assertEquals(BubbleCorners.MAX, BubbleCorners.settle(99f))
        assertEquals(9, BubbleCorners.SLIDER_STEPS)
    }

    @Test
    fun `the rest of the look starts as it always was`() {
        val defaults = AppearanceSettings()
        assertTrue(defaults.shapedAvatars)
        assertFalse(defaults.twoLinePreviews)
        assertEquals(AppIcon.Teal, defaults.appIcon)
        assertFalse(defaults.reduceMotion)
    }

    @Test
    fun `the icon's glyph goes light only where the dark one cannot be read`() {
        assertFalse(AppIcon.Teal.lightGlyph)
        assertFalse(AppIcon.Amber.lightGlyph)
        assertFalse(AppIcon.Orange.lightGlyph)
        assertTrue(AppIcon.Violet.lightGlyph)
        AppIcon.entries.forEach { icon ->
            val ink = if (icon.lightGlyph) 0xFFFFFFFF.toInt() else AppIcon.ICON_INK
            assertTrue("${icon.label} glyph contrast", contrast(icon.background, ink) >= 3.0)
        }
    }

    @Test
    fun `an unknown icon is teal`() {
        assertEquals(AppIcon.Teal, AppIcon.from(null))
        assertEquals(AppIcon.Pink, AppIcon.from("Pink"))
        assertEquals(AppIcon.Teal, AppIcon.from("Plaid"))
    }
}
