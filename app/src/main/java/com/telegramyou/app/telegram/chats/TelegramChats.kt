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

    /**
     * Chats matching [query], best matches first.
     *
     * A blank query returns nothing rather than everything: an empty search
     * field is not a request to list the world, and the chat list is already
     * on screen behind it.
     */
    suspend fun searchChats(query: String, limit: Int = 30): List<ChatPreview>

    /**
     * Silences a chat, or stops silencing it.
     *
     * [muted] rather than a toggle, so the caller sends what it drew: a toggle
     * computed on the far side can disagree with the row the finger was on
     * when two updates arrive close together.
     */
    suspend fun setChatMuted(chatId: Long, muted: Boolean)

    /**
     * Pins a chat to the top of the list, or unpins it.
     *
     * [pinned] rather than a toggle, for the reason [setChatMuted] gives: the
     * caller sends what it drew.
     */
    suspend fun setChatPinned(chatId: Long, pinned: Boolean)

    /**
     * Marks everything in a chat as read.
     *
     * Its own method rather than a side effect of opening one, because the
     * point of it is to clear a badge without going in — which is most of why
     * anybody reaches for it.
     */
    suspend fun markChatRead(chatId: Long)
}
