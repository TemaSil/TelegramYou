package com.telegramyou.app.telegram.chats

import com.telegramyou.app.telegram.model.ChatDetail
import com.telegramyou.app.telegram.model.ChatFolder
import com.telegramyou.app.telegram.model.ChatPreview
import com.telegramyou.app.telegram.model.TelegramUser
import kotlinx.coroutines.flow.StateFlow

/** The list of conversations, and opening one. */
interface TelegramChats {
    val chats: StateFlow<List<ChatPreview>>

    /**
     * The account's chat folders, in the order the account put them.
     *
     * Empty for most accounts, and empty is the normal case rather than a
     * failure: folders are made by hand, and a client that assumes at least
     * one draws a tab strip that filters nothing.
     *
     * Which chats are in a folder is not here. It is on the chat, as
     * [ChatPreview.folderIds], because a chat can be in several and the list
     * is one list either way — folders filter it rather than replace it.
     */
    val folders: StateFlow<List<ChatFolder>>

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

    /**
     * Moves a chat into the archive, or back out of it.
     *
     * The archive is a chat list of its own rather than a flag on the chat,
     * which is why this is a move: a chat is in exactly one of the two.
     */
    suspend fun setChatArchived(chatId: Long, archived: Boolean)

    /**
     * The account's own contacts, for starting a conversation.
     *
     * Contacts rather than "everybody you have ever spoken to": the chat list
     * already holds the second, and a picker that repeats it is a longer way
     * to reach what is behind the button.
     */
    suspend fun contacts(): List<TelegramUser>

    /**
     * The private chat with [userId], creating it if there is not one yet.
     *
     * Returns its id, because the caller's next move is to navigate there and
     * the chat may not have existed a moment ago.
     */
    suspend fun openPrivateChat(userId: Long): Long
}
