package com.telegramyou.app.telegram.messages

import com.telegramyou.app.telegram.model.AttachmentDraft

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
}
