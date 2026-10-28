package com.telegramyou.app.settings

/**
 * Ghost mode (2.8), from For geeks — what this account does not tell the
 * people it talks to: that it is typing, that it has read what they sent,
 * that it is online. Read where those are said, in the conversation and
 * the app, which is why it is kept here, process-wide, as AutoTranslate is.
 *
 * Telegram still reads a chat as read once a message is sent in it, as it
 * does for every client: an answer is a reply to what came before.
 */
object GhostMode {
    @Volatile
    var hideTyping = false
        private set

    @Volatile
    var hideReading = false
        private set

    @Volatile
    var hideOnline = false
        private set

    fun set(settings: GeekSettings) {
        hideTyping = settings.ghostTyping
        hideReading = settings.ghostReading
        hideOnline = settings.ghostOnline
    }
}
