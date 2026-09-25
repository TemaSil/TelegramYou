package com.telegramyou.app.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale

class ChatNotificationSettingsTest {

    private val zone = ZoneOffset.UTC
    // Friday 25 September 2026, 12:00 UTC.
    private val now = LocalDateTime.of(2026, 9, 25, 12, 0).toEpochSecond(zone)

    @Test
    fun `muted until a time in the past is not muted`() {
        assertFalse(ChatNotificationSettings(mutedUntil = now - 1).isMuted(now))
        assertTrue(ChatNotificationSettings(mutedUntil = now + 1).isMuted(now))
        assertFalse(ChatNotificationSettings().isMuted(now))
    }

    @Test
    fun `Telegram's mute_for round-trips through an instant`() {
        val settings = ChatNotificationSettings(
            mutedUntil = ChatNotificationSettings.mutedUntil(3_600, now)
        )
        assertEquals(now + 3_600, settings.mutedUntil)
        assertEquals(3_600, settings.muteForSeconds(now))
        assertEquals(0, ChatNotificationSettings.mutedUntil(0, now))
        assertEquals(
            "a century is forever",
            ChatNotificationSettings.MUTED_FOREVER,
            ChatNotificationSettings.mutedUntil(2_000_000_000, now)
        )
    }

    @Test
    fun `durations end where they say`() {
        assertEquals(now + 8 * 3_600, MuteDuration.EightHours.until(now))
        assertEquals(ChatNotificationSettings.MUTED_FOREVER, MuteDuration.Forever.until(now))
    }

    @Test
    fun `the status says on, off, or until when`() {
        fun label(until: Long) =
            notificationStatusLabel(ChatNotificationSettings(mutedUntil = until), now, zone, Locale.ENGLISH)
        assertEquals("On", label(0))
        assertEquals("Off", label(ChatNotificationSettings.MUTED_FOREVER))
        assertEquals("Off until 13:00", label(now + 3_600))
        assertEquals("Off until Sun 12:00", label(now + 2 * 86_400))
    }
}
