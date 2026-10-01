package com.telegramyou.app.update

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * When a version came out, for the top of its What's new: "Released today",
 * "Released yesterday", "Released 1 October", and the year as well once it
 * is not this one. From the release's `published_at` on GitHub, which is
 * when its APK went up for everyone.
 *
 * Asked for by the owner (1.6.10): the notes said what a version brought
 * and not how fresh it was.
 */
fun releasedLabel(
    publishedSeconds: Long,
    nowSeconds: Long,
    zone: ZoneId,
    locale: Locale = Locale.ENGLISH
): String {
    val published = Instant.ofEpochSecond(publishedSeconds).atZone(zone).toLocalDate()
    val today = Instant.ofEpochSecond(nowSeconds).atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(published, today)
    val pattern = if (published.year == today.year) "d MMMM" else "d MMMM yyyy"
    return when (days) {
        0L -> "Released today"
        1L -> "Released yesterday"
        else -> "Released " + published.format(DateTimeFormatter.ofPattern(pattern, locale))
    }
}

/** GitHub's `published_at` — "2026-10-01T05:24:13Z" — in epoch seconds; null when absent or unreadable. */
fun publishedSecondsOf(iso: String?): Long? =
    iso?.takeIf { it.isNotBlank() }?.let { runCatching { Instant.parse(it).epochSecond }.getOrNull() }
