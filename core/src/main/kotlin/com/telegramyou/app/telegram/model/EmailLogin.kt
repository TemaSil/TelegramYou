package com.telegramyou.app.telegram.model

/**
 * Giving up on the login email, when the code went there and the mailbox is
 * gone. Telegram lets the account fall back to a code by SMS, after a wait
 * it chooses — a week, typically; none with Premium.
 */
sealed interface EmailReset {
    /** A reset can be asked for; it takes effect [waitSeconds] later. */
    data class Available(val waitSeconds: Int) : EmailReset

    /** A reset was asked for and lands in [resetInSeconds]. */
    data class Pending(val resetInSeconds: Int) : EmailReset
}

/** What the reset button says, which is also what it will do. */
fun emailResetLabel(reset: EmailReset): String = when (reset) {
    is EmailReset.Available ->
        if (reset.waitSeconds <= 0) "Reset email" else "Reset email (takes ${durationLabel(reset.waitSeconds)})"
    is EmailReset.Pending ->
        if (reset.resetInSeconds <= 0) "Reset email now" else "Email resets in ${durationLabel(reset.resetInSeconds)}"
}

/**
 * A wait in the largest unit that fits it whole, rounded up — "7 days",
 * "3 hours", "1 minute" — since a reset that says "6 days" and lands on
 * the seventh reads as a broken promise.
 */
fun durationLabel(seconds: Int): String {
    val (amount, unit) = when {
        seconds >= DAY -> ceilDiv(seconds, DAY) to "day"
        seconds >= HOUR -> ceilDiv(seconds, HOUR) to "hour"
        else -> ceilDiv(seconds.coerceAtLeast(1), MINUTE) to "minute"
    }
    return if (amount == 1) "1 $unit" else "$amount ${unit}s"
}

/**
 * Whether [value] could be an email address: something, an at, and a
 * domain with a dot in it, no spaces. Enough to keep the button from
 * sending a typo; the server is the one that knows.
 */
fun isPlausibleEmail(value: String): Boolean {
    val email = value.trim()
    if (email.any(Char::isWhitespace)) return false
    val at = email.lastIndexOf('@')
    if (at <= 0 || at != email.indexOf('@')) return false
    val domain = email.substring(at + 1)
    val dot = domain.lastIndexOf('.')
    return dot > 0 && dot < domain.length - 1
}

private fun ceilDiv(a: Int, b: Int): Int = (a + b - 1) / b

private const val MINUTE = 60
private const val HOUR = 60 * MINUTE
private const val DAY = 24 * HOUR
