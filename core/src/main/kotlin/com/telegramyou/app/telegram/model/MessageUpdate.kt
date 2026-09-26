package com.telegramyou.app.telegram.model

/**
 * Something that happened to a message in a chat, as the backend heard it.
 *
 * The conversation on screen is a window of messages fetched once; these are
 * what keep it true afterwards without fetching it again. Refetching was how
 * it used to stay current, and it threw away every page scrolled back through
 * and jumped the list each time something was sent.
 *
 * Every case names its chat, so a screen can take the ones for itself and
 * ignore the rest. Applied by `applying` in `ui/chat`, where they are tested.
 */
sealed interface MessageUpdate {
    val chatId: Long

    /** A new message, incoming or our own. */
    data class Added(val message: ChatMessage) : MessageUpdate {
        override val chatId: Long get() = message.chatId
    }

    /**
     * A message sent from here, confirmed by the server under its real id.
     *
     * TDLib announces an outgoing message the moment it is queued, under a
     * temporary id, and swaps the id once the server has it. A window still
     * holding the temporary one would aim every later edit, reply or delete
     * at a message the server has never heard of.
     */
    data class Replaced(val oldId: Long, val message: ChatMessage) : MessageUpdate {
        override val chatId: Long get() = message.chatId
    }

    /**
     * A message sent from here that the server refused — too big, a flood
     * wait, a chat this account may no longer post to.
     *
     * Like [Replaced], TDLib gives it a new id as it fails, and [message]
     * carries [SendState.Failed]. [error] is what the server said, for the
     * person who pressed send: a failure only the bubble shows is one nobody
     * notices.
     */
    data class SendFailed(
        val oldId: Long,
        val message: ChatMessage,
        val error: String
    ) : MessageUpdate {
        override val chatId: Long get() = message.chatId
    }

    data class Deleted(override val chatId: Long, val messageIds: Set<Long>) : MessageUpdate

    /** New text or caption, by anyone, here or on another device. */
    data class Edited(
        override val chatId: Long,
        val messageId: Long,
        val text: String
    ) : MessageUpdate

    data class ReactionsChanged(
        override val chatId: Long,
        val messageId: Long,
        val reactions: List<MessageReaction>
    ) : MessageUpdate

    /**
     * The other side has read everything we sent up to [lastReadId].
     *
     * An id rather than a list, because that is how Telegram keeps it: one
     * watermark per chat, and every outgoing message at or below it is read.
     */
    data class ReadUpTo(override val chatId: Long, val lastReadId: Long) : MessageUpdate

    /**
     * New votes on a poll — ours from another device, or anyone's.
     *
     * Its own case rather than an [Edited]: a vote changes no words, and a
     * poll marked "edited" every time someone answered would be wrong.
     */
    data class PollChanged(
        override val chatId: Long,
        val messageId: Long,
        val poll: PollContent
    ) : MessageUpdate

    /** A bot replaced the buttons under one of its messages. */
    data class ButtonsChanged(
        override val chatId: Long,
        val messageId: Long,
        val buttons: List<List<InlineButton>>
    ) : MessageUpdate
}
