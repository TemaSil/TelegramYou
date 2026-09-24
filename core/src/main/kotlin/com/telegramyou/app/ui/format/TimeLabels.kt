package com.telegramyou.app.ui.format

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * The time a chat row shows for its last message.
 *
 * What Telegram shows, and what a reader needs from a column of them: the
 * time of day for today, "Yesterday", the weekday within the last week, day
 * and month within the year, and the full date beyond that. The live client
 * used to print `HH:mm` whatever the day, so a message from March and one
 * from a minute ago looked equally recent.
 *
 * [nowSeconds] and [zone] are passed in rather than read, which is what lets
 * the tests pin both.
 */
fun chatListTimeLabel(
    epochSeconds: Long,
    nowSeconds: Long,
    zone: ZoneId,
    locale: Locale = Locale.getDefault()
): String {
    if (epochSeconds <= 0) return ""
    val moment = Instant.ofEpochSecond(epochSeconds).atZone(zone)
    val today = Instant.ofEpochSecond(nowSeconds).atZone(zone).toLocalDate()
    val day = moment.toLocalDate()
    val daysAgo = ChronoUnit.DAYS.between(day, today)
    return when {
        daysAgo <= 0L -> moment.format(DateTimeFormatter.ofPattern("HH:mm", locale))
        daysAgo == 1L -> "Yesterday"
        daysAgo < 7L -> moment.format(DateTimeFormatter.ofPattern("EEE", locale))
        day.year == today.year -> moment.format(DateTimeFormatter.ofPattern("d MMM", locale))
        else -> moment.format(DateTimeFormatter.ofPattern("dd.MM.yy", locale))
    }
}

/**
 * Where a person is, as far as Telegram will say.
 *
 * Mirrors TDLib's `UserStatus`. The vague ones — recently, within a week,
 * within a month — are what an account that hides its last-seen time shows
 * to everybody, and they are all the client is allowed to know.
 */
sealed interface Presence {
    /** Online until [expiresAt], in epoch seconds — then offline. */
    data class Online(val expiresAt: Long) : Presence
    data class Offline(val wasOnlineAt: Long) : Presence
    data object Recently : Presence
    data object WithinWeek : Presence
    data object WithinMonth : Presence
    /** No status at all: a bot, a deleted account, or one never seen. */
    data object Unknown : Presence
}

/**
 * Online now. An online status carries its own expiry, and one read after
 * that moment is stale rather than true.
 */
fun Presence.isOnline(nowSeconds: Long): Boolean =
    this is Presence.Online && expiresAt > nowSeconds

/** What a private chat's header says under the name. */
fun presenceLabel(
    presence: Presence,
    nowSeconds: Long,
    zone: ZoneId,
    locale: Locale = Locale.getDefault()
): String = when (presence) {
    is Presence.Online ->
        if (presence.isOnline(nowSeconds)) "online"
        else lastSeenAt(presence.expiresAt, nowSeconds, zone, locale)
    is Presence.Offline -> lastSeenAt(presence.wasOnlineAt, nowSeconds, zone, locale)
    Presence.Recently -> "last seen recently"
    Presence.WithinWeek -> "last seen within a week"
    Presence.WithinMonth -> "last seen within a month"
    Presence.Unknown -> "last seen a long time ago"
}

private fun lastSeenAt(
    epochSeconds: Long,
    nowSeconds: Long,
    zone: ZoneId,
    locale: Locale
): String {
    val ago = nowSeconds - epochSeconds
    if (ago < 60) return "last seen just now"
    if (ago < 3_600) {
        val minutes = ago / 60
        return "last seen $minutes minute${if (minutes == 1L) "" else "s"} ago"
    }
    val moment = Instant.ofEpochSecond(epochSeconds).atZone(zone)
    val today: LocalDate = Instant.ofEpochSecond(nowSeconds).atZone(zone).toLocalDate()
    val time = moment.format(DateTimeFormatter.ofPattern("HH:mm", locale))
    return when (ChronoUnit.DAYS.between(moment.toLocalDate(), today)) {
        0L -> "last seen at $time"
        1L -> "last seen yesterday at $time"
        else -> "last seen " + chatListTimeLabel(epochSeconds, nowSeconds, zone, locale)
    }
}

/**
 * What a group's or channel's header says under its name: "1 member",
 * "1,284 members", "12,000 subscribers". Grouped digits, because a five-digit
 * count without them has to be read twice. Zero means the server has not said,
 * and the header then names the kind of chat instead of claiming it is empty.
 */
fun memberCountLabel(count: Int, isChannel: Boolean, locale: Locale = Locale.getDefault()): String {
    if (count <= 0) return if (isChannel) "channel" else "group"
    val noun = when {
        isChannel -> if (count == 1) "subscriber" else "subscribers"
        else -> if (count == 1) "member" else "members"
    }
    return String.format(locale, "%,d %s", count, noun)
}

/**
 * How long ago a story went up, the way Telegram's viewer says it: "just now",
 * "12m", "5h". A story lives for a day, so hours are as far as it goes.
 */
fun storyAgeLabel(postedSeconds: Long, nowSeconds: Long): String {
    if (postedSeconds <= 0) return ""
    val minutes = (nowSeconds - postedSeconds).coerceAtLeast(0) / 60
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m"
        else -> "${minutes / 60}h"
    }
}
