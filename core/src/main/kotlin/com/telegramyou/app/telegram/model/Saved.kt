package com.telegramyou.app.telegram.model

/**
 * Saved Messages by where they came from (2.7): Telegram files every saved
 * message under the chat it was forwarded from, and what was written there
 * under My notes. One of these is a heading in Saved Messages, opened as a
 * conversation of its own.
 */
data class SavedTopic(
    val id: Long,
    val kind: SavedTopicKind,
    /** The chat's title for [SavedTopicKind.FromChat]; blank otherwise, worded by the screen. */
    val title: String = "",
    /** The chat they were saved from, for [SavedTopicKind.FromChat]. */
    val chatId: Long? = null,
    /** The newest message under it, as the chat list words one. */
    val preview: String = "",
    val timeLabel: String = "",
    val isPinned: Boolean = false
)

enum class SavedTopicKind {
    /** Written in Saved Messages itself. */
    MyNotes,

    /** Forwarded from somebody who hides their name on forwards. */
    AuthorHidden,

    /** Forwarded from a chat, which [SavedTopic.chatId] names. */
    FromChat
}

/**
 * A tag on saved messages (2.7) — a reaction put on them, which in Saved
 * Messages Telegram files as a tag, with a name of the account's choosing
 * and how many messages carry it. Premium's on Telegram's side.
 */
data class SavedTag(
    /** The reaction, as [MessageReaction.emoji] keys one: an emoji, or [customReactionKey]. */
    val reaction: String,
    /** Its name, or blank for none given. */
    val label: String = "",
    val count: Int = 0
)

/** How long a tag's name may be: Telegram's limit. */
const val MAX_SAVED_TAG_LABEL = 12

/**
 * A shared folder's invite (2.7), as a t.me/addlist link describes it: the
 * folder's name, and the chats it would bring — those not joined yet, and
 * those already in the chat list.
 */
data class FolderInvite(
    val link: String,
    val name: String,
    /** The folder this account already has from the link, or null for a new one. */
    val folderId: Int? = null,
    val missingChats: List<ChatPreview> = emptyList(),
    val addedChats: List<ChatPreview> = emptyList()
)

/** Whether [text] is a shared folder's link, t.me/addlist/…, with or without its scheme. */
fun isFolderLink(text: String): Boolean =
    FOLDER_LINK.matches(text.trim())

private val FOLDER_LINK = Regex("""(?i)(https?://)?(www\.)?(t|telegram)\.me/addlist/[A-Za-z0-9_-]+/?""")
