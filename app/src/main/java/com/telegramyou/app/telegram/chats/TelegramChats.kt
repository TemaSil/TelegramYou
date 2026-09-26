package com.telegramyou.app.telegram.chats

import com.telegramyou.app.notifications.ChatNotificationSettings
import com.telegramyou.app.telegram.model.ChatDetail
import com.telegramyou.app.telegram.model.ChatFolder
import com.telegramyou.app.telegram.model.ChatPreview
import com.telegramyou.app.telegram.model.InviteLinkPreview
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
     * Asks for the next chats of a list — the main one for a null
     * [folderId], otherwise that folder — and answers whether there may be
     * more.
     *
     * [refreshChats] loads one page of each list. An account with more chats
     * than that had no way to reach the rest; this is what the list calls as
     * it nears its end. What arrives comes through [chats] as usual.
     */
    suspend fun loadMoreChats(folderId: Int?): Boolean

    /**
     * Everything a conversation screen needs. Returns a fixed window of
     * recent messages today; paging is the next thing this owes.
     */
    suspend fun openChat(chatId: Long): ChatDetail

    /**
     * A screen showing [chatId] has appeared, and [releaseChat] says it has
     * gone.
     *
     * Telegram wants to know which chats are open — in supergroups and
     * channels some updates only arrive for open ones — and a chat is open
     * from three screens here, each with its own state holder: the
     * conversation, its media grid and its info screen. So this is counted,
     * once per holder rather than once per fetch, and the chat closes when
     * the last holder goes. Not suspending: the release is called as a
     * holder is torn down, with nothing left to wait on.
     */
    fun retainChat(chatId: Long)

    fun releaseChat(chatId: Long)

    /**
     * Chats matching [query], best matches first.
     *
     * A blank query returns nothing rather than everything: an empty search
     * field is not a request to list the world, and the chat list is already
     * on screen behind it.
     */
    suspend fun searchChats(query: String, limit: Int = 30): List<ChatPreview>

    /**
     * Public chats — channels, groups, bots — whose name or username matches
     * [query] and that this account is not in. What makes search a way to
     * find Telegram rather than only one's own chats.
     */
    suspend fun searchPublicChats(query: String): List<ChatPreview>

    /**
     * The chat behind an @username, for a mention tapped in a message; null
     * when there is none.
     */
    suspend fun chatByUsername(username: String): Long?

    /** The people this account writes to most, for the top of search. */
    suspend fun topPeople(limit: Int = 12): List<ChatPreview>

    /**
     * Chats opened from search, newest first — Telegram's own list, kept on
     * the account, so it is the same on every device.
     */
    suspend fun recentlyFoundChats(): List<ChatPreview>
    suspend fun addRecentlyFoundChat(chatId: Long)
    suspend fun removeRecentlyFoundChat(chatId: Long)
    suspend fun clearRecentlyFoundChats()

    /** Channels Telegram suggests to this account; empty when it has none. */
    suspend fun recommendedChannels(): List<ChatPreview>

    /**
     * Silences a chat, or stops silencing it.
     *
     * [muted] rather than a toggle, so the caller sends what it drew: a toggle
     * computed on the far side can disagree with the row the finger was on
     * when two updates arrive close together.
     */
    suspend fun setChatMuted(chatId: Long, muted: Boolean)

    /**
     * All of a chat's notification settings at once — the whole object, the
     * way TDLib takes it, so one change cannot reset the others.
     */
    suspend fun setChatNotifications(chatId: Long, settings: ChatNotificationSettings)

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
     * anybody reaches for it. The open conversation calls it too, whenever a
     * message it shows is newer than what it last marked, and only while it
     * is actually on screen: see ChatViewModel.onSeen.
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
     * The chat's primary invite link, or null when there is none to show.
     *
     * Null rather than an empty string, and null is the common answer: the
     * server hands the link to members who may invite and to nobody else, so
     * a screen that always drew a row for it would draw an empty one for
     * most people in most groups.
     */
    suspend fun chatInviteLink(chatId: Long): String?

    /**
     * Leaves a group or channel.
     *
     * The chat leaves the list on its own, through the same update that any
     * other client's change arrives by — nothing here removes it by hand.
     */
    suspend fun leaveChat(chatId: Long)

    /**
     * Makes a group with [memberIds] in it and answers with its chat id.
     *
     * The id rather than the chat, because the caller's next move is to open
     * it — and the chat list learns about it the same way it learns about
     * everything else, through the backend's own updates.
     */
    suspend fun createGroup(title: String, memberIds: List<Long>): Long

    /** Makes a channel and answers with its chat id. */
    suspend fun createChannel(title: String, description: String): Long

    /**
     * What an invite link leads to, or null when it leads nowhere.
     *
     * Null covers expired, revoked and never-existed alike: the server does
     * not distinguish them usefully, and neither does anybody holding one.
     */
    suspend fun checkInviteLink(link: String): InviteLinkPreview?

    /** Joins through an invite link and answers with the chat's id. */
    suspend fun joinByInviteLink(link: String): Long

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
