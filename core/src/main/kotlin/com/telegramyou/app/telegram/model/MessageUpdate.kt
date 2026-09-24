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
}
