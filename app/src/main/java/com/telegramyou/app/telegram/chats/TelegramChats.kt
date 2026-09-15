package com.telegramyou.app.telegram.chats

import com.telegramyou.app.telegram.model.ChatDetail
import com.telegramyou.app.telegram.model.ChatPreview
import kotlinx.coroutines.flow.StateFlow

/** The list of conversations, and opening one. */
interface TelegramChats {
    val chats: StateFlow<List<ChatPreview>>

    suspend fun refreshChats()

    /**
     * Everything a conversation screen needs. Returns a fixed window of
     * recent messages today; paging is the next thing this owes.
     */
    suspend fun openChat(chatId: Long): ChatDetail
}
