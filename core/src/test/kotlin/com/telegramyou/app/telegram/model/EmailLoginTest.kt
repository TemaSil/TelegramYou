package com.telegramyou.app.telegram.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailLoginTest {

    @Test
    fun `a wait is said in the largest whole unit, rounded up`() {
        assertEquals("7 days", durationLabel(7 * 86_400))
        assertEquals("7 days", durationLabel(6 * 86_400 + 1))
        assertEquals("1 day", durationLabel(86_400))
        assertEquals("3 hours", durationLabel(2 * 3_600 + 60))
        assertEquals("1 hour", durationLabel(3_600))
        assertEquals("2 minutes", durationLabel(61))
        assertEquals("1 minute", durationLabel(0))
    }

    @Test
    fun `the reset button says what it will do`() {
        assertEquals("Reset email", emailResetLabel(EmailReset.Available(0)))
        assertEquals("Reset email (takes 7 days)", emailResetLabel(EmailReset.Available(7 * 86_400)))
        assertEquals("Email resets in 2 days", emailResetLabel(EmailReset.Pending(36 * 3_600)))
        assertEquals("Reset email now", emailResetLabel(EmailReset.Pending(0)))
    }

    @Test
    fun `an address needs a name, one at and a dotted domain`() {
        assertTrue(isPlausibleEmail("me@example.org"))
        assertTrue(isPlausibleEmail("  first.last+tag@mail.example.co.uk "))
        assertFalse(isPlausibleEmail(""))
        assertFalse(isPlausibleEmail("me@example"))
        assertFalse(isPlausibleEmail("@example.org"))
        assertFalse(isPlausibleEmail("me@example."))
        assertFalse(isPlausibleEmail("me@@example.org"))
        assertFalse(isPlausibleEmail("me@exa mple.org"))
    }
}
