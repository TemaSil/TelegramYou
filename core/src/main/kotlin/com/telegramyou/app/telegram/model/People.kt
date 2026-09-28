package com.telegramyou.app.telegram.model

/**
 * Somebody else, as their profile shows them.
 *
 * [user] carries the bio as well as the name, username and phone: TDLib
 * splits a person across `user` and `userFullInfo`, and the profile is the
 * one screen that needs both, so the backend joins them before it gets here.
 */
data class PersonProfile(
    val user: TelegramUser,
    /** In this account's contacts, so their number is theirs to show. */
    val isContact: Boolean,
    /** On this account's block list: they cannot write here or call. */
    val isBlocked: Boolean,
    val isBot: Boolean = false
)

/**
 * What the chat list's long-press menu offers for taking a chat away, and
 * how it words it.
 *
 * Telegram has three different things behind what looks like one action:
 * a private chat's history is deleted — for this account, or for both
 * people — while a group or channel is left, which is something else with
 * other consequences. Saved Messages is neither: it can be emptied, and
 * there is nothing to leave.
 */
data class ChatRemoval(
    /** "Clear history" belongs in the menu: private chats and Saved Messages. */
    val canClearHistory: Boolean,
    /** The menu's word for taking the chat away, or null when there is none. */
    val removeLabel: String?,
    /** Taking it away is leaving it, not deleting its history. */
    val leaves: Boolean,
    /**
     * Whether to offer "also for them": a private chat with a person can be
     * emptied on both sides, which is Telegram's can_be_deleted_for_all_users.
     */
    val offerForEveryone: Boolean
)

fun chatRemovalOf(chat: ChatPreview): ChatRemoval = when {
    chat.isChannel -> ChatRemoval(false, "Leave channel", leaves = true, offerForEveryone = false)
    chat.isGroup -> ChatRemoval(false, "Leave group", leaves = true, offerForEveryone = false)
    chat.isSavedMessages -> ChatRemoval(true, null, leaves = false, offerForEveryone = false)
    else -> ChatRemoval(true, "Delete chat", leaves = false, offerForEveryone = chat.canDeleteForEveryone)
}

/** "Lina" out of "Lina Park" — how the dialogs name the other side. */
fun firstNameOf(title: String): String = title.trim().substringBefore(' ').ifBlank { title }
