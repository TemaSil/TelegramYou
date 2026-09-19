package com.telegramyou.app.telegram

import com.telegramyou.app.telegram.model.AttachmentDraft
import com.telegramyou.app.telegram.model.AuthUiState
import com.telegramyou.app.telegram.model.ChatDetail
import com.telegramyou.app.telegram.model.ChatFolder
import com.telegramyou.app.telegram.model.ChatPreview
import com.telegramyou.app.telegram.model.StoryItem
import kotlinx.coroutines.flow.StateFlow

class TelegramRepository(
    private val client: TelegramClient
) : TelegramClient by client {

    fun observeAuth(): StateFlow<AuthUiState> = authState
    fun observeChats(): StateFlow<List<ChatPreview>> = chats
    fun observeStories(): StateFlow<List<StoryItem>> = stories
    fun observeFolders(): StateFlow<List<ChatFolder>> = folders

    // deleteMessage and editMessage arrive through the `by client` delegation
    // above; redeclaring them here only shadowed the interface.

    suspend fun sendMessage(
        chatId: Long,
        text: String,
        attachment: AttachmentDraft? = null,
        replyToId: Long? = null
    ) {
        if (attachment != null) {
            client.sendAttachment(chatId, attachment, text, replyToId)
        } else if (text.isNotBlank()) {
            client.sendText(chatId, text, replyToId)
        }
    }
}
