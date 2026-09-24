package com.telegramyou.app.ui.auth

/**
 * Counts taps that come in a run, the way Android's own "tap the build number
 * seven times" does: [required] of them, none more than [gapMillis] after the
 * one before. A pause starts the count again, so the mark on the login screen
 * cannot be tripped by ten idle taps spread over a minute.
 */
class RepeatedTaps(
    private val required: Int,
    private val gapMillis: Long = 1_000L
) {
    private var count = 0
    private var last = Long.MIN_VALUE

    /** True on the tap that completes a run; the count then starts over. */
    fun tap(nowMillis: Long): Boolean {
        count = if (last != Long.MIN_VALUE && nowMillis - last <= gapMillis) count + 1 else 1
        last = nowMillis
        if (count < required) return false
        count = 0
        last = Long.MIN_VALUE
        return true
    }
}
