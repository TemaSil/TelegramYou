package com.telegramyou.app.ui.chat

import com.telegramyou.app.telegram.model.ChatMessage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Deciding how a thread breaks up: which messages belong to one run, and where
 * a day ends.
 *
 * Kept out of the composable so it can be tested on the JVM. The rules read as
 * obvious and are not — a run ends on a five-minute gap, a missing date has to
 * degrade to "separate" rather than silently grouping everything, and "Today"
 * depends on when the question is asked.
 *
 * Everything here is public rather than `internal`. In :app that distinction
 * was free; here it is not, because `internal` means "this module" and ChatScreen
 * is in another one. Nothing in :core can be internal and still be used.
 */

/** A run ends when more than this separates two messages from the same side. */
private const val RUN_GAP_SECONDS = 5 * 60

/**
 * True when [message] opens a new day relative to [previous].
 *
 * The first message of a thread always does. A message with no date cannot
 * start one, because there is nothing to label the separator with.
 */
fun startsNewDay(previous: ChatMessage?, message: ChatMessage): Boolean {
    if (message.date <= 0L) return false
    if (previous == null) return true
    return !sameDay(previous.date, message.date)
}

/** True when two epoch-second instants fall on the same calendar day. */
fun sameDay(a: Long, b: Long): Boolean {
    val first = Calendar.getInstance().apply { timeInMillis = a * 1000L }
    val second = Calendar.getInstance().apply { timeInMillis = b * 1000L }
    return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
        first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
}

/**
 * True when [message] is the last of its run: [next] comes from the other
 * side, sits more than [RUN_GAP_SECONDS] later, or does not exist.
 *
 * A message without a date ends its run rather than joining one — grouping on
 * an unknown instant would collapse unrelated messages together.
 */
fun endsRun(message: ChatMessage?, next: ChatMessage?): Boolean {
    if (message == null || next == null) return true
    if (message.isOutgoing != next.isOutgoing) return true
    if (message.date <= 0L || next.date <= 0L) return true
    return next.date - message.date > RUN_GAP_SECONDS
}

/**
 * The label on a day separator: "Today", "Yesterday", or the date.
 *
 * [now] is a parameter so the result does not depend on the wall clock when
 * this is under test.
 */
fun dayLabel(
    date: Long,
    now: Long = System.currentTimeMillis() / 1000,
    locale: Locale = Locale.getDefault()
): String {
    if (date <= 0L) return ""
    return when {
        sameDay(date, now) -> "Today"
        sameDay(date, now - 24 * 60 * 60) -> "Yesterday"
        else -> SimpleDateFormat("d MMMM", locale).format(Date(date * 1000L))
    }
}
