package com.telegramyou.app.ui.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class TimeLabelsTest {

    private val zone = ZoneId.of("Europe/Moscow")
    private val locale = Locale.ENGLISH

    // Thursday 24 September 2026, 15:30 in Moscow.
    private val now = ZonedDateTime.of(2026, 9, 24, 15, 30, 0, 0, zone).toEpochSecond()

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0) =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toEpochSecond()

    private fun label(epoch: Long) = chatListTimeLabel(epoch, now, zone, locale)

    @Test
    fun `today is the time of day`() {
        assertEquals("09:05", label(at(2026, 9, 24, 9, 5)))
    }

    @Test
    fun `just after midnight yesterday is still yesterday`() {
        assertEquals("Yesterday", label(at(2026, 9, 23, 0, 1)))
    }

    @Test
    fun `within the week is the weekday`() {
        assertEquals("Mon", label(at(2026, 9, 21)))
    }

    @Test
    fun `earlier this year is day and month, before that the full date`() {
        assertEquals("3 Mar", label(at(2026, 3, 3)))
        assertEquals("31.12.25", label(at(2025, 12, 31)))
    }

    @Test
    fun `no date at all is no label`() {
        assertEquals("", label(0))
    }

    @Test
    fun `online is online only until it expires`() {
        assertTrue(Presence.Online(expiresAt = now + 30).isOnline(now))
        assertFalse(Presence.Online(expiresAt = now - 30).isOnline(now))
        assertFalse(Presence.Recently.isOnline(now))
    }

    @Test
    fun `last seen reads the way Telegram says it`() {
        fun seen(p: Presence) = presenceLabel(p, now, zone, locale)
        assertEquals("online", seen(Presence.Online(now + 60)))
        assertEquals("last seen just now", seen(Presence.Offline(now - 20)))
        assertEquals("last seen 1 minute ago", seen(Presence.Offline(now - 70)))
        assertEquals("last seen 45 minutes ago", seen(Presence.Offline(now - 45 * 60)))
        assertEquals("last seen at 09:05", seen(Presence.Offline(at(2026, 9, 24, 9, 5))))
        assertEquals(
            "last seen yesterday at 22:10",
            seen(Presence.Offline(at(2026, 9, 23, 22, 10)))
        )
        assertEquals("last seen 3 Mar", seen(Presence.Offline(at(2026, 3, 3))))
        assertEquals("last seen recently", seen(Presence.Recently))
        assertEquals("last seen a long time ago", seen(Presence.Unknown))
    }

    @Test
    fun `an online status read after it expired says when it ended`() {
        assertEquals(
            "last seen 2 minutes ago",
            presenceLabel(Presence.Online(expiresAt = now - 120), now, zone, locale)
        )
    }

    @Test
    fun `member counts are grouped and agree with their number`() {
        assertEquals("1 member", memberCountLabel(1, isChannel = false, locale = locale))
        assertEquals("1,284 members", memberCountLabel(1284, isChannel = false, locale = locale))
        assertEquals("12,000 subscribers", memberCountLabel(12000, isChannel = true, locale = locale))
        assertEquals("channel", memberCountLabel(0, isChannel = true, locale = locale))
    }
}
