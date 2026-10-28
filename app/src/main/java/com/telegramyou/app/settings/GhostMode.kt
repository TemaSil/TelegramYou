package com.telegramyou.app.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    /** Stories watched without their posters seeing it, always — the switch on the Ghost mode screen. */
    @Volatile
    var hideStoryViews = false
        private set

    private val _storiesUnseenUntil = MutableStateFlow(0L)

    /**
     * Until when, in wall-clock milliseconds, stories are watched unseen for
     * a while — turned on from the eye in a story, for [STORIES_UNSEEN_MINUTES]
     * — or 0. A while rather than for good, because whom to be seen by is a
     * choice made story by story: a friend's, seen; anyone else's, not.
     */
    val storiesUnseenUntil: StateFlow<Long> = _storiesUnseenUntil.asStateFlow()

    /** Whether a story watched now is kept from its poster. */
    fun storiesUnseen(now: Long = System.currentTimeMillis()): Boolean =
        hideStoryViews || now < _storiesUnseenUntil.value

    fun watchStoriesUnseen(now: Long = System.currentTimeMillis()) {
        _storiesUnseenUntil.value = now + STORIES_UNSEEN_MINUTES * 60_000L
    }

    fun stopWatchingUnseen() {
        _storiesUnseenUntil.value = 0L
    }

    fun set(settings: GeekSettings) {
        hideTyping = settings.ghostTyping
        hideReading = settings.ghostReading
        hideOnline = settings.ghostOnline
        hideStoryViews = settings.ghostStories
    }

    /** How long the eye in a story keeps them unseen. */
    const val STORIES_UNSEEN_MINUTES = 15
}
