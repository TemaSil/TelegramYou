package com.telegramyou.app.telegram

import com.telegramyou.app.telegram.model.AttachmentDraft
import com.telegramyou.app.telegram.model.AuthUiState
import com.telegramyou.app.telegram.model.ChatDetail
import com.telegramyou.app.telegram.model.ChatPreview
import com.telegramyou.app.telegram.model.StoryItem
import kotlinx.coroutines.flow.StateFlow

interface TelegramClient {
    val authState: StateFlow<AuthUiState>
    val chats: StateFlow<List<ChatPreview>>
    val stories: StateFlow<List<StoryItem>>

    fun start()
    fun shutdown()

    suspend fun submitPhoneNumber(phone: String)
    suspend fun submitCode(code: String)
    suspend fun submitPassword(password: String)
    suspend fun resendCode()

    suspend fun refreshChats()
    suspend fun openChat(chatId: Long): ChatDetail
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
     * which Telegram only permits within a window and only where
     * [ChatMessage.canBeDeletedForEveryone] says so.
     */
    suspend fun deleteMessage(chatId: Long, messageId: Long, forEveryone: Boolean)

    suspend fun editMessage(chatId: Long, messageId: Long, text: String)

    suspend fun markStorySeen(storyId: Long)
    suspend fun logout()
}
