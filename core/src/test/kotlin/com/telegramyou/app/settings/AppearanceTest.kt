package com.telegramyou.app.settings

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
}
