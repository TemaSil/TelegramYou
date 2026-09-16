package com.telegramyou.app.ui.chat

/**
 * A running or finished recording, as a clock.
 *
 * `m:ss` rather than `mm:ss`: a voice message is usually seconds long, and
 * "00:07" pads a number that needs no padding. Past an hour it grows to
 * `h:mm:ss` rather than counting to 90 minutes, because "90:00" is a duration
 * nobody reads correctly at a glance.
 */
fun formatDuration(totalSeconds: Long): String {
    val seconds = totalSeconds.coerceAtLeast(0)
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainder = seconds % 60
    return if (hours > 0) {
        "$hours:${minutes.pad()}:${remainder.pad()}"
    } else {
        "$minutes:${remainder.pad()}"
    }
}

private fun Long.pad(): String = toString().padStart(2, '0')
