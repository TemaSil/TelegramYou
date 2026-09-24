package com.telegramyou.app.telegram.model

data class TelegramUser(
    val id: Long,
    val firstName: String,
    val lastName: String = "",
    val username: String? = null,
    val phoneNumber: String? = null,
    val avatarColor: Long = id,
    val isPremium: Boolean = false,
    /**
     * The "about" text on an account.
     *
     * A String rather than a String? and empty rather than null, because
     * nothing distinguishes an account with no bio from one whose bio was
     * cleared, and a nullable field would have every reader deciding that
     * again. It arrives from `getUserFullInfo`, not `getMe` — a separate call,
     * which is why it is empty until that one answers.
     */
    val bio: String = ""
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
    /** Someone in it is typing right now; the avatar morphs while this holds. */
    val isTyping: Boolean = false,
    val isChannel: Boolean = false,
    val isGroup: Boolean = false,
    val avatarColor: Long = id,
    val hasUnreadMention: Boolean = false,
    /**
     * In the archive rather than the main list.
     *
     * A flag on the chat rather than a second list, because every screen that
     * draws a chat draws the same row and only the filter differs — and
     * because a chat moves between the two, so two lists would mean keeping
     * them in step.
     */
    val isArchived: Boolean = false,
    /**
     * The folders this chat is in, by folder id.
     *
     * A set rather than one id: Telegram's folders are filters, and a chat
     * can be in several of them at once — or, most commonly, in none, which
     * is why this is empty by default and why an account with no folders
     * needs nothing here.
     */
    val folderIds: Set<Int> = emptySet()
)

/**
 * One of the account's chat folders.
 *
 * Only what a tab needs. TDLib's folder carries the rules that decide its
 * membership — include these chat types, exclude those — and none of that
 * belongs on this side: the server applies them and says which chats are in,
 * which is what a client would have to trust anyway.
 */
data class ChatFolder(
    val id: Int,
    val title: String,
    /**
     * TDLib's own icon name, such as "Work" or "Cat", or blank.
     *
     * Kept verbatim rather than mapped to a Material icon here: the mapping
     * is a UI decision, and this is the model both backends fill in.
     */
    val iconName: String = ""
)

/**
 * What an invite link leads to, read before joining.
 *
 * Telegram shows this rather than joining on the tap, and so does this client:
 * a link is a stranger's word about where it goes, and the name and the size
 * of the room are what a person needs to decide whether to walk in.
 *
 * [joinedChatId] is set when this account is already a member — the button
 * then opens the chat rather than asking to join something already joined.
 */
data class InviteLinkPreview(
    val link: String,
    val title: String,
    val memberCount: Int,
    val isChannel: Boolean = false,
    val joinedChatId: Long? = null,
    val avatarColor: Long = title.hashCode().toLong()
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

/**
 * What Telegram made of a link somebody sent.
 *
 * The server does the fetching and the parsing — a client that went to the
 * page itself would leak who is reading what to every site anyone links, and
 * would show a different card to each person in the conversation.
 *
 * Every field but [url] can be missing, and usually some are: a link to a
 * bare file has a site name and nothing else. A card with only a URL in it is
 * worth less than the link already in the text, so [hasContent] is what
 * decides whether to draw one.
 */
data class LinkPreview(
    val url: String,
    val siteName: String = "",
    val title: String = "",
    val description: String = "",
    /** The preview image, once it is on this device. */
    val photoPath: String? = null
) {
    val hasContent: Boolean
        get() = siteName.isNotBlank() || title.isNotBlank() || description.isNotBlank()
}

/**
 * A video message, with everything the bubble needs before the bytes arrive.
 *
 * Its own type rather than six more fields on [ChatMessage]: a photo needs a
 * path and a shape, a video needs those plus a poster, a duration and a
 * second file behind the first, and flattening all of that into the message
 * would leave the reader working out which fields belong together.
 *
 * Both files can be absent, and usually are at first. Telegram sends the
 * dimensions and the duration with the message and the pixels later, which is
 * exactly why the poster is separate from the video: the poster is small
 * enough to fetch on sight, the video is not.
 */
data class VideoContent(
    /** How long it runs, in seconds; 0 when the server did not say. */
    val durationSeconds: Int = 0,
    /** Width over height, so the bubble can reserve the right space. */
    val aspect: Float = 16f / 9f,
    /** The poster frame on this device, once it is here. */
    val thumbPath: String? = null,
    /** TDLib's id for the poster, which a download is asked for by. */
    val thumbFileId: Int? = null,
    /** The video itself on this device, once it is here. */
    val path: String? = null,
    /** TDLib's id for the video file. */
    val fileId: Int? = null
)

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
     * The card for a link in [text], when Telegram has one.
     *
     * Null for every message without a link and for most with one: the server
     * only builds a preview for pages it can read.
     */
    val linkPreview: LinkPreview? = null,
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
    val waveform: List<Int> = emptyList(),
    /**
     * A photo's file on this device, once it is here.
     *
     * Null while it is still only on Telegram's servers, which is the normal
     * state for a message that has just scrolled into view.
     */
    val photoPath: String? = null,
    /** TDLib's id for that file, which a download is asked for by. */
    val photoFileId: Int? = null,
    /**
     * The photo's shape, as width divided by height.
     *
     * Carried because the bubble has to reserve the right space before the
     * bytes arrive. Without it every photo would open as a square and then
     * jump, which in a list means everything below it jumps too.
     */
    val photoAspect: Float = 1f,
    /**
     * The video, when this message is one.
     *
     * Null for everything else, which is almost every message — see
     * [VideoContent] for why it is a type of its own.
     */
    val video: VideoContent? = null
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
    val pinnedMessage: ChatMessage? = null,
    /**
     * Who is in the group, as the server lists them.
     *
     * Empty for a chat with one other person, for a channel, and whenever the
     * call to fetch them failed — the conversation opens either way, and the
     * header falls back to whoever has written in the loaded window. That
     * fallback is what this replaces: it showed who was talking rather than
     * who was present, and anybody quiet was missing from it.
     */
    val members: List<TelegramUser> = emptyList()
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
