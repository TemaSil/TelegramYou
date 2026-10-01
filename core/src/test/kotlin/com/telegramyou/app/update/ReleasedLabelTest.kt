package com.telegramyou.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ReleasedLabelTest {

    private val zone = ZoneId.of("Europe/Moscow")
    private fun at(year: Int, month: Int, day: Int, hour: Int = 12) =
        ZonedDateTime.of(year, month, day, hour, 0, 0, 0, zone).toEpochSecond()

    private val now = at(2026, 10, 1, 13)

    @Test
    fun `today and yesterday by the calendar, not by 24 hours`() {
        assertEquals("Released today", releasedLabel(at(2026, 10, 1, 0), now, zone))
        assertEquals("Released yesterday", releasedLabel(at(2026, 9, 30, 23), now, zone))
    }

    @Test
    fun `a date within the year, and with it beyond`() {
        assertEquals("Released 24 September", releasedLabel(at(2026, 9, 24), now, zone))
        assertEquals("Released 30 December 2025", releasedLabel(at(2025, 12, 30), now, zone))
    }

    @Test
    fun `GitHub's timestamp is read, and nonsense is not`() {
        assertEquals(1790832253L, publishedSecondsOf("2026-10-01T05:24:13Z"))
        assertNull(publishedSecondsOf(null))
        assertNull(publishedSecondsOf(""))
        assertNull(publishedSecondsOf("yesterday"))
    }
}
