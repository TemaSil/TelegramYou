package com.telegramyou.app.telegram.model

data class TelegramUser(
    val id: Long,
    val firstName: String,
    val lastName: String = "",
    val username: String? = null,
    val phoneNumber: String? = null,
    val avatarColor: Long = id,
    val isPremium: Boolean = false
) {
    val displayName: String
        get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
            .ifBlank { username ?: "User" }

    val initials: String
        get() = displayName
            .split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .map { it.first().uppercaseChar() }
            .joinToString("")
            .ifBlank { "?" }
}

enum class AuthState {
    Bootstrapping,
    WaitPhoneNumber,
    WaitCode,
    WaitPassword,
    Ready,
    Closed,
    Error
}

data class AuthUiState(
    val state: AuthState = AuthState.Bootstrapping,
    val phoneNumber: String = "",
    val codeHint: String = "",
    val errorMessage: String? = null,
    val isLoading: Boolean = false,
    val me: TelegramUser? = null
)

data class ChatPreview(
    val id: Long,
    val title: String,
    val lastMessage: String,
    val timestampLabel: String,
    val unreadCount: Int = 0,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val isOnline: Boolean = false,
    val isChannel: Boolean = false,
    val isGroup: Boolean = false,
    val avatarColor: Long = id,
    val hasUnreadMention: Boolean = false
)

data class StoryItem(
    val id: Long,
    val authorName: String,
    val isOwn: Boolean = false,
    val hasUnseen: Boolean = true,
    val avatarColor: Long = id,
    val previewEmoji: String = "✨",
    val caption: String = ""
)

enum class MessageContentType {
    Text,
    Photo,
    Video,
    Document,
    Voice,
    Sticker
}

data class ChatMessage(
    val id: Long,
    val chatId: Long,
    val text: String,
    val isOutgoing: Boolean,
    val timeLabel: String,
    /**
     * When the message was sent, in epoch seconds.
     *
     * [timeLabel] is already formatted and cannot be grouped by: telling
     * whether two messages fall on the same day, or close enough together to
     * belong to one run, needs the instant itself.
     */
    val date: Long = 0L,
    val senderName: String? = null,
    /** Author, so a group avatar keeps one colour per person. */
    val senderId: Long? = null,
    /**
     * The message this one answers.
     *
     * The quoted text and author are carried alongside the id because the
     * original may not be in the loaded window — a reply to something from
     * last week has to render without it.
     */
    val replyToId: Long? = null,
    val replyToText: String? = null,
    val replyToSender: String? = null,
    /**
     * What Telegram permits on this message, which is not the same as whether
     * it is ours: a group admin can delete anyone's, and an old message may be
     * past the edit window. The menu offers only what the server allows, so a
     * tap cannot fail on a rule the UI knew about.
     */
    val canBeEdited: Boolean = false,
    val canBeDeletedForSelf: Boolean = false,
    val canBeDeletedForEveryone: Boolean = false,
    /** Telegram marks an edited message; hiding that would be dishonest. */
    val isEdited: Boolean = false,
    val isRead: Boolean = false,
    val contentType: MessageContentType = MessageContentType.Text,
    val fileName: String? = null,
    val fileSizeLabel: String? = null,
    val mediaEmoji: String? = null,
    /**
     * In the server's order, which is by popularity — not ours to re-sort.
     * Empty for the overwhelming majority of messages, so the chip row costs
     * nothing where there is nothing to show.
     */
    val reactions: List<MessageReaction> = emptyList(),
    /**
     * Where a voice note's audio is, once it is on this device.
     *
     * Null while it is still only on Telegram's servers. A voice message can
     * be shown, and its length read, long before the file arrives — so the
     * bubble draws either way and only the play button waits on this.
     */
    val voicePath: String? = null,
    /**
     * TDLib's id for that file, which is what a download is asked for by.
     *
     * Separate from [voicePath] because one exists before the other: the id
     * comes with the message, the path only after the bytes do.
     */
    val voiceFileId: Int? = null,
    /**
     * The voice note's own picture of itself, as 5-bit samples.
     *
     * Empty when Telegram sent none, or when it is a format this does not
     * read — the bubble then draws a flat row rather than nothing, so a voice
     * message is the same shape whether or not its waveform arrived.
     */
    val waveform: List<Int> = emptyList()
)

/**
 * One emoji on a message, with how many people chose it.
 *
 * [isChosen] is about us specifically, not about whether anyone reacted: the
 * chip is filled when we are one of the [count], and that is the only way to
 * tell "3 people liked this" from "3 people including me".
 */
data class MessageReaction(
    val emoji: String,
    val count: Int,
    val isChosen: Boolean = false
)

data class ChatDetail(
    val chat: ChatPreview,
    val messages: List<ChatMessage>,
    val memberCountLabel: String? = null,
    val isTyping: Boolean = false,
    /**
     * The chat's pinned message, if it has one.
     *
     * A whole message rather than an id: what is pinned is usually old, so it
     * is rarely in the loaded window, and a bar showing "pinned message" with
     * nothing in it would say less than no bar at all.
     */
    val pinnedMessage: ChatMessage? = null
)

/**
 * A message found by searching, with the conversation it belongs to.
 *
 * Global search spans every chat, so a hit without its chat is unreadable:
 * the same sentence means different things depending on who said it where.
 */
data class MessageHit(
    val chat: ChatPreview,
    val message: ChatMessage
)

sealed interface AttachmentDraft {
    data class Files(val uris: List<String>, val names: List<String>) : AttachmentDraft
    data class Photos(val uris: List<String>) : AttachmentDraft

    /**
     * A recording made in the composer.
     *
     * [path] is a real file in the app's cache rather than a Uri, for the same
     * reason the pickers copy what they return: TDLib opens a filesystem path.
     * [durationSeconds] travels with it because the file's own header is not
     * read anywhere, and a voice message with no length is a bubble that says
     * nothing about what tapping it costs.
     */
    data class Voice(
        val path: String,
        val durationSeconds: Int,
        /**
         * Amplitudes measured while recording, as 5-bit samples.
         *
         * Captured rather than derived: reading them back out of an encoded
         * Opus file would mean decoding it, and the recorder already has the
         * numbers as it writes.
         */
        val waveform: List<Int> = emptyList()
    ) : AttachmentDraft
}
