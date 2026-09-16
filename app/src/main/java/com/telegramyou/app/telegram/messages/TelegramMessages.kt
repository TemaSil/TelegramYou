package com.telegramyou.app.telegram.messages

import com.telegramyou.app.telegram.model.AttachmentDraft
import com.telegramyou.app.telegram.model.ChatMessage
import com.telegramyou.app.telegram.model.MessageHit

/** What can be done to a message once a conversation is open. */
interface TelegramMessages {
    /** [replyToId] answers an existing message, or null for a fresh one. */
    suspend fun sendText(chatId: Long, text: String, replyToId: Long? = null)

    suspend fun sendAttachment(
        chatId: Long,
        draft: AttachmentDraft,
        caption: String = "",
        replyToId: Long? = null
    )

    /**
     * Removes a message. [forEveryone] withdraws it for the other side too,
     * which Telegram only permits within a window and only where the
     * message's own `canBeDeletedForEveryone` says so.
     */
    suspend fun deleteMessage(chatId: Long, messageId: Long, forEveryone: Boolean)

    suspend fun editMessage(chatId: Long, messageId: Long, text: String)

    /**
     * The page of messages immediately older than [beforeMessageId], in
     * chronological order like every other list here.
     *
     * An empty result means the conversation has no more history, and the
     * caller should stop asking. That is the only signal there is — there is
     * no total to compare against — so a caller that ignores it will spin.
     */
    suspend fun loadOlderMessages(
        chatId: Long,
        beforeMessageId: Long,
        limit: Int = 50
    ): List<ChatMessage>

    /**
     * Adds or withdraws our reaction on a message.
     *
     * One call for both directions, because Telegram has no separate
     * "unreact": choosing what is already chosen removes it. The caller is
     * expected to have updated its own copy already — see `toggleReaction` in
     * :core — since this returns nothing and the round trip is long enough to
     * see.
     */
    suspend fun toggleReaction(chatId: Long, messageId: Long, emoji: String)

    /**
     * The emoji this chat permits, in the order to offer them.
     *
     * Not a constant: a group can be restricted to a handful of reactions, or
     * to none at all, and offering one the server will refuse is a tap that
     * fails for a reason the UI could have known.
     */
    suspend fun availableReactions(chatId: Long): List<String>

    /**
     * Messages matching [query] inside one conversation, newest first.
     *
     * Separate from [searchMessages] rather than a chat id on it: Telegram
     * serves the two from different calls, and a global search narrowed
     * afterwards would page through every chat to fill one.
     */
    suspend fun searchChatMessages(
        chatId: Long,
        query: String,
        limit: Int = 50
    ): List<ChatMessage>

    /**
     * Messages matching [query] across every conversation, newest first.
     *
     * Blank returns nothing, for the same reason [TelegramChats.searchChats]
     * does: an empty field is not a request for the whole history.
     */
    suspend fun searchMessages(query: String, limit: Int = 30): List<MessageHit>
}
