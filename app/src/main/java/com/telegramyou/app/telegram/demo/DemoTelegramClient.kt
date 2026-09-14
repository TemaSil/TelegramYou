package com.telegramyou.app.telegram.demo

import com.telegramyou.app.telegram.TelegramClient
import com.telegramyou.app.telegram.model.AttachmentDraft
import com.telegramyou.app.telegram.model.AuthState
import com.telegramyou.app.telegram.model.AuthUiState
import com.telegramyou.app.telegram.model.ChatDetail
import com.telegramyou.app.telegram.model.ChatMessage
import com.telegramyou.app.telegram.model.ChatPreview
import com.telegramyou.app.telegram.model.MessageContentType
import com.telegramyou.app.telegram.model.StoryItem
import com.telegramyou.app.telegram.model.TelegramUser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/**
 * Fully interactive offline client for UI / Expressive motion development.
 * Swap to TdLibTelegramClient when TELEGRAM_API_ID / HASH are set in local.properties.
 */
class DemoTelegramClient : TelegramClient {
    private val messageId = AtomicLong(1_000)

    private val _authState = MutableStateFlow(
        AuthUiState(state = AuthState.Bootstrapping, isLoading = true)
    )
    override val authState: StateFlow<AuthUiState> = _authState.asStateFlow()

    private val _chats = MutableStateFlow(seedChats())
    override val chats: StateFlow<List<ChatPreview>> = _chats.asStateFlow()

    private val _stories = MutableStateFlow(seedStories())
    override val stories: StateFlow<List<StoryItem>> = _stories.asStateFlow()

    private val chatMessages = mutableMapOf<Long, MutableList<ChatMessage>>()

    override fun start() {
        _authState.value = AuthUiState(state = AuthState.WaitPhoneNumber, isLoading = false)
        seedMessages()
    }

    override fun shutdown() = Unit

    override suspend fun submitPhoneNumber(phone: String) {
        _authState.update {
            it.copy(isLoading = true, errorMessage = null, phoneNumber = phone)
        }
        delay(650)
        if (phone.filter { it.isDigit() }.length < 8) {
            _authState.update {
                it.copy(isLoading = false, errorMessage = "Enter a valid phone number")
            }
            return
        }
        _authState.update {
            it.copy(
                isLoading = false,
                state = AuthState.WaitCode,
                codeHint = "Code sent to $phone (demo: 12345)"
            )
        }
    }

    override suspend fun submitCode(code: String) {
        _authState.update { it.copy(isLoading = true, errorMessage = null) }
        delay(500)
        if (code.trim() != "12345") {
            _authState.update {
                it.copy(isLoading = false, errorMessage = "Wrong code. Demo code is 12345")
            }
            return
        }
        _authState.update {
            it.copy(
                isLoading = false,
                state = AuthState.Ready,
                me = TelegramUser(
                    id = 1,
                    firstName = "You",
                    lastName = "Expressive",
                    username = "telegramyou",
                    phoneNumber = it.phoneNumber,
                    isPremium = true
                )
            )
        }
    }

    override suspend fun submitPassword(password: String) {
        _authState.update { it.copy(isLoading = true, errorMessage = null) }
        delay(400)
        _authState.update {
            it.copy(
                isLoading = false,
                state = AuthState.Ready,
                me = TelegramUser(id = 1, firstName = "You", lastName = "Expressive")
            )
        }
    }

    override suspend fun resendCode() {
        delay(300)
        _authState.update {
            it.copy(codeHint = "New demo code sent: 12345", errorMessage = null)
        }
    }

    override suspend fun refreshChats() {
        delay(350)
        _chats.update { current ->
            current.mapIndexed { index, chat ->
                if (index == 0) chat.copy(lastMessage = "Synced with Material You pulse")
                else chat
            }
        }
    }

    override suspend fun openChat(chatId: Long): ChatDetail {
        delay(180)
        val chat = _chats.value.first { it.id == chatId }
        val messages = chatMessages.getOrPut(chatId) { mutableListOf() }
        return ChatDetail(
            chat = chat,
            messages = messages.toList(),
            memberCountLabel = when {
                chat.isChannel -> "128K subscribers"
                chat.isGroup -> "42 members"
                else -> if (chat.isOnline) "online" else "last seen recently"
            },
            isTyping = chatId == 2L
        )
    }

    override suspend fun sendText(chatId: Long, text: String, replyToId: Long?) {
        delay(120)
        appendOutgoing(
            chatId = chatId,
            text = text,
            type = MessageContentType.Text,
            replyToId = replyToId
        )
    }

    override suspend fun sendAttachment(
        chatId: Long,
        draft: AttachmentDraft,
        caption: String,
        replyToId: Long?
    ) {
        delay(220)
        when (draft) {
            // Only the first of a batch quotes; the rest would repeat it.
            is AttachmentDraft.Files -> {
                draft.names.forEachIndexed { index, name ->
                    appendOutgoing(
                        chatId = chatId,
                        text = caption.ifBlank { name },
                        type = MessageContentType.Document,
                        fileName = name,
                        fileSizeLabel = "1.${index + 2} MB",
                        replyToId = replyToId.takeIf { index == 0 }
                    )
                }
            }
            is AttachmentDraft.Photos -> {
                draft.uris.forEachIndexed { index, _ ->
                    appendOutgoing(
                        chatId = chatId,
                        text = caption.ifBlank { "Photo" },
                        type = MessageContentType.Photo,
                        mediaEmoji = "🖼️",
                        replyToId = replyToId.takeIf { index == 0 }
                    )
                }
            }
        }
    }

    override suspend fun markStorySeen(storyId: Long) {
        _stories.update { list ->
            list.map { if (it.id == storyId) it.copy(hasUnseen = false) else it }
        }
    }

    override suspend fun logout() {
        delay(200)
        _authState.value = AuthUiState(state = AuthState.WaitPhoneNumber)
    }

    private fun appendOutgoing(
        chatId: Long,
        text: String,
        type: MessageContentType,
        fileName: String? = null,
        fileSizeLabel: String? = null,
        mediaEmoji: String? = null,
        replyToId: Long? = null
    ) {
        val quoted = replyToId?.let { id ->
            chatMessages[chatId]?.firstOrNull { it.id == id }
        }
        val msg = ChatMessage(
            id = messageId.incrementAndGet(),
            chatId = chatId,
            text = text,
            isOutgoing = true,
            timeLabel = demoTimeFormat.format(Date()),
            date = System.currentTimeMillis() / 1000,
            isRead = false,
            contentType = type,
            fileName = fileName,
            fileSizeLabel = fileSizeLabel,
            mediaEmoji = mediaEmoji,
            replyToId = replyToId,
            replyToText = quoted?.text,
            replyToSender = quoted?.senderName
        )
        val bucket = chatMessages.getOrPut(chatId) { mutableListOf() }
        bucket.add(msg)
        _chats.update { list ->
            list.map { chat ->
                if (chat.id == chatId) {
                    chat.copy(
                        lastMessage = when (type) {
                            MessageContentType.Document -> "📎 ${fileName ?: "File"}"
                            MessageContentType.Photo -> "🖼 Photo"
                            else -> text
                        },
                        timestampLabel = "now",
                        unreadCount = 0
                    )
                } else chat
            }.sortedByDescending { it.timestampLabel == "now" }
        }
    }

    private fun seedChats(): List<ChatPreview> = listOf(
        ChatPreview(1, "Material Design", "Expressive motion is live ✨", "12:41", unreadCount = 3, isPinned = true, isOnline = true),
        ChatPreview(2, "Lina Park", "typing… wait, almost", "11:02", unreadCount = 1, isOnline = true, avatarColor = 22),
        ChatPreview(3, "Design Circle", "New Figma dump in #files", "Yesterday", isGroup = true, unreadCount = 18, avatarColor = 33),
        ChatPreview(4, "TelegramYou News", "M3 Expressive build notes", "Mon", isChannel = true, isMuted = true, avatarColor = 44),
        ChatPreview(5, "Artem", "Send me the apk?", "Sun", avatarColor = 55),
        ChatPreview(6, "Saved Messages", "Color tokens & springs", "Sat", isPinned = true, avatarColor = 66),
        ChatPreview(7, "Kotlin Night", "Compose BOM tips", "Fri", isGroup = true, avatarColor = 77),
        ChatPreview(8, "Mom", "Call me when free 💚", "Thu", unreadCount = 2, avatarColor = 88)
    )

    private fun seedStories(): List<StoryItem> = listOf(
        StoryItem(0, "My story", isOwn = true, hasUnseen = false, previewEmoji = "＋", caption = "Add"),
        StoryItem(101, "Lina", previewEmoji = "🌊", caption = "Morning swim"),
        StoryItem(102, "Circle", previewEmoji = "🎨", caption = "Palette drop"),
        StoryItem(103, "Artem", previewEmoji = "🚀", caption = "Ship it"),
        StoryItem(104, "News", previewEmoji = "📰", caption = "Update"),
        StoryItem(105, "Mom", previewEmoji = "🌿", caption = "Garden")
    )

    private fun seedMessages() {
        // Real instants rather than pre-baked labels: the conversation groups
        // messages into runs and draws a separator when the day changes, and
        // neither is possible from a string like "12:30". Spread across three
        // days so both behaviours are visible in demo mode.
        val day = 24 * 60 * 60L
        val now = System.currentTimeMillis() / 1000
        val today = now - 3 * 60 * 60
        val yesterday = now - day - 2 * 60 * 60

        chatMessages[1] = mutableListOf(
            demoMessage(1, 1, "Welcome to TelegramYou", false, today, "Material Design"),
            demoMessage(2, 1, "This build uses MaterialExpressiveTheme, springy FABs and vivid chat surfaces — no liquid glass.", false, today + 60, "Material Design"),
            demoMessage(3, 1, "Attach files from the composer. Stories sit on top of the chat list.", false, today + 120, "Material Design"),
            demoMessage(4, 1, "Looks sharp. Let’s keep the teal identity.", true, today + 660, isRead = true)
        )
        chatMessages[2] = mutableListOf(
            demoMessage(10, 2, "Did you try the expressive loading indicator?", false, yesterday, "Lina Park"),
            demoMessage(11, 2, "Yes — and the split send button feels great.", true, yesterday + 180, isRead = true),
            demoMessage(12, 2, "Sending a voice note next 🎧", false, today + 300, "Lina Park")
        )
        chatMessages[3] = mutableListOf(
            demoMessage(20, 3, "Drop assets in the thread", false, now - 2 * day, "Maya"),
            demoMessage(21, 3, "brand-kit.zip", false, now - 2 * day + 30, "Maya", contentType = MessageContentType.Document, fileName = "brand-kit.zip", fileSizeLabel = "4.8 MB")
        )
    }

    private fun demoMessage(
        id: Long,
        chatId: Long,
        text: String,
        isOutgoing: Boolean,
        date: Long,
        senderName: String? = null,
        isRead: Boolean = false,
        contentType: MessageContentType = MessageContentType.Text,
        fileName: String? = null,
        fileSizeLabel: String? = null
    ) = ChatMessage(
        id = id,
        chatId = chatId,
        text = text,
        isOutgoing = isOutgoing,
        timeLabel = demoTimeFormat.format(Date(date * 1000L)),
        date = date,
        senderName = senderName,
        senderId = senderName?.hashCode()?.toLong(),
        isRead = isRead,
        contentType = contentType,
        fileName = fileName,
        fileSizeLabel = fileSizeLabel
    )

    private val demoTimeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
}
