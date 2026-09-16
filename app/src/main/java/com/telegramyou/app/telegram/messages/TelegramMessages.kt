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
     * Messages matching [query] across every conversation, newest first.
     *
     * Blank returns nothing, for the same reason [TelegramChats.searchChats]
     * does: an empty field is not a request for the whole history.
     */
    suspend fun searchMessages(query: String, limit: Int = 30): List<MessageHit>
}
