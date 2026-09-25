package com.telegramyou.app.notifications

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * How one chat notifies, as far as a person can change it: whether it does
 * at all and until when, whether the words show in the shade, and whether it
 * makes a sound.
 *
 * [mutedUntil] is an instant in epoch seconds rather than Telegram's "for N
 * seconds from now", so the same value means the same thing tomorrow: 0 is
 * not muted, [MUTED_FOREVER] is until turned back on.
 */
data class ChatNotificationSettings(
    val mutedUntil: Long = 0L,
    val showPreview: Boolean = true,
    val sound: Boolean = true
) {
    fun isMuted(nowSeconds: Long): Boolean = mutedUntil > nowSeconds

    /** How many seconds Telegram's `mute_for` should say, from [nowSeconds]. */
    fun muteForSeconds(nowSeconds: Long): Long = when {
        mutedUntil >= MUTED_FOREVER -> MUTED_FOREVER
        mutedUntil > nowSeconds -> mutedUntil - nowSeconds
        else -> 0L
    }

    companion object {
        /** Muted with no end: Telegram's own "forever" is about a century. */
        const val MUTED_FOREVER = Int.MAX_VALUE.toLong()

        /** Telegram's `mute_for`, read back into an instant. */
        fun mutedUntil(muteForSeconds: Long, nowSeconds: Long): Long = when {
            muteForSeconds <= 0 -> 0L
            // Anything beyond a year is "forever" in every client there is.
            muteForSeconds > YEAR_SECONDS -> MUTED_FOREVER
            else -> nowSeconds + muteForSeconds
        }
    }
}

/** The durations "Mute for…" offers — Telegram's own four. */
enum class MuteDuration(val seconds: Long, val label: String) {
    OneHour(3_600, "1 hour"),
    EightHours(8 * 3_600, "8 hours"),
    TwoDays(2 * 86_400, "2 days"),
    Forever(ChatNotificationSettings.MUTED_FOREVER, "Until turned back on");

    fun until(nowSeconds: Long): Long =
        if (this == Forever) ChatNotificationSettings.MUTED_FOREVER else nowSeconds + seconds
}

/**
 * The line under "Notifications": "On", "Off", or "Off until 18:40" — and
 * with the day, "Off until Fri 09:00", once the end is not today.
 */
fun notificationStatusLabel(
    settings: ChatNotificationSettings,
    nowSeconds: Long,
    zone: ZoneId,
    locale: Locale = Locale.getDefault()
): String {
    if (!settings.isMuted(nowSeconds)) return "On"
    if (settings.mutedUntil >= ChatNotificationSettings.MUTED_FOREVER) return "Off"
    val end = Instant.ofEpochSecond(settings.mutedUntil).atZone(zone)
    val today = Instant.ofEpochSecond(nowSeconds).atZone(zone).toLocalDate()
    val pattern = if (end.toLocalDate() == today) "HH:mm" else "EEE HH:mm"
    return "Off until " + end.format(DateTimeFormatter.ofPattern(pattern, locale))
}

private const val YEAR_SECONDS = 366L * 86_400
