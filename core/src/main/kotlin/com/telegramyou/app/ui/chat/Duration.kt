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

/**
 * Whether a voice message's [text] is a caption someone wrote, rather than
 * what stands in for one: the "🎤 Voice" a message without a caption is
 * worded as in the chat list, or the length the demo gives it. Only a real
 * caption is shown on the bubble, under the waveform.
 */
fun isVoiceCaption(text: String): Boolean {
    val trimmed = text.trim()
    return trimmed.isNotEmpty() && trimmed !in VOICE_PLACEHOLDERS && !DURATION_ONLY.matches(trimmed)
}

/** How a voice message without a caption is worded, in both languages. */
val VOICE_PLACEHOLDERS = setOf("🎤 Voice", "🎤 Голосовое")

private val DURATION_ONLY = Regex("""\d+:\d{2}(:\d{2})?""")

/**
 * What a voice bubble says beside its waveform: the length, from
 * [durationSeconds] or from a [text] that is only a length; blank otherwise
 * — never "🎤 Voice", which says nothing the bubble does not already show.
 */
fun voiceLabel(text: String, durationSeconds: Int): String = when {
    durationSeconds > 0 -> formatDuration(durationSeconds.toLong())
    DURATION_ONLY.matches(text.trim()) -> text.trim()
    else -> ""
}
