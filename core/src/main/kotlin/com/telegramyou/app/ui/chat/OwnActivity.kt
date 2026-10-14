package com.telegramyou.app.ui.chat

/**
 * What this account is doing in a chat, for the other side's "typing…"
 * (2.2). The client has shown other people typing since 1.x and never told
 * anyone it was: this is the half that tells.
 */
enum class OwnActivity { Typing, RecordingVoice, RecordingVideoNote, None }

/**
 * When to tell Telegram again.
 *
 * A chat action lasts about six seconds on the other side and then lapses,
 * so one that goes on — a long message being written, a voice message held
 * — has to be said again before then; said on every keystroke it would be a
 * request per letter. So: at once when the activity changes, again once
 * [repeatAfterMs] has passed while it carries on, and a cancel only when
 * something was said and has stopped — never for a field that was empty all
 * along.
 *
 * A message going out ends "typing…" on Telegram's side by itself, which
 * is what [sent] is for: forget, without saying anything.
 */
class OwnActivityPacer(private val repeatAfterMs: Long = REPEAT_AFTER_MS) {

    private var said = OwnActivity.None
    private var saidAt = 0L

    /** What to tell Telegram now that [activity] is going on, or null for nothing. */
    fun update(activity: OwnActivity, nowMs: Long): OwnActivity? {
        if (activity == OwnActivity.None) {
            if (said == OwnActivity.None) return null
            said = OwnActivity.None
            return OwnActivity.None
        }
        if (activity == said && nowMs - saidAt < repeatAfterMs) return null
        said = activity
        saidAt = nowMs
        return activity
    }

    /** A message went: Telegram has ended the activity itself. */
    fun sent() {
        said = OwnActivity.None
    }

    companion object {
        /** Under the six seconds an action lasts, with room for the request to arrive. */
        const val REPEAT_AFTER_MS = 4_500L
    }
}
