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
    val isRead: Boolean = false,
    val contentType: MessageContentType = MessageContentType.Text,
    val fileName: String? = null,
    val fileSizeLabel: String? = null,
    val mediaEmoji: String? = null
)

data class ChatDetail(
    val chat: ChatPreview,
    val messages: List<ChatMessage>,
    val memberCountLabel: String? = null,
    val isTyping: Boolean = false
)

sealed interface AttachmentDraft {
    data class Files(val uris: List<String>, val names: List<String>) : AttachmentDraft
    data class Photos(val uris: List<String>) : AttachmentDraft
}
