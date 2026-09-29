package com.telegramyou.app.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockTest {

    private fun locked(pin: String, autoLock: AutoLock = AutoLock.Immediately): AppLockSettings {
        val salt = AppLock.newSalt()
        return AppLockSettings(pinHash = AppLock.hash(pin, salt), pinSalt = salt, autoLock = autoLock)
    }

    @Test
    fun `a PIN is four to six digits`() {
        assertTrue(AppLock.isValidPin("1234"))
        assertTrue(AppLock.isValidPin("123456"))
        assertFalse(AppLock.isValidPin("123"))
        assertFalse(AppLock.isValidPin("1234567"))
        assertFalse(AppLock.isValidPin("12a4"))
    }

    @Test
    fun `the PIN is checked against its hash, never kept`() {
        val settings = locked("2580")
        assertTrue(settings.enabled)
        assertTrue(AppLock.matches("2580", settings))
        assertFalse(AppLock.matches("2581", settings))
        assertFalse("2580" in settings.pinHash!!)
    }

    @Test
    fun `the same PIN under two salts hashes differently`() {
        assertNotEquals(locked("1111").pinHash, locked("1111").pinHash)
    }

    @Test
    fun `without a PIN nothing locks`() {
        assertFalse(AppLockSettings().enabled)
        assertFalse(AppLock.shouldLock(AppLockSettings(), leftAtMillis = null, nowMillis = 0))
    }

    @Test
    fun `a cold start always asks, a quick return asks only when it should`() {
        val oneMinute = locked("1234", AutoLock.OneMinute)
        assertTrue(AppLock.shouldLock(oneMinute, leftAtMillis = null, nowMillis = 0))
        assertFalse(AppLock.shouldLock(oneMinute, leftAtMillis = 0, nowMillis = 59_000))
        assertTrue(AppLock.shouldLock(oneMinute, leftAtMillis = 0, nowMillis = 60_000))
        assertTrue(AppLock.shouldLock(locked("1234"), leftAtMillis = 0, nowMillis = 1))
    }

    @Test
    fun `auto-lock choices keep their order`() {
        assertEquals(listOf(0L, 60L, 300L, 3600L), AutoLock.entries.map { it.seconds })
    }
}
