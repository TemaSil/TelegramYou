package com.telegramyou.app.telegram.demo

import com.telegramyou.app.telegram.TelegramClient
import com.telegramyou.app.telegram.model.AttachmentDraft
import com.telegramyou.app.telegram.model.AuthState
import com.telegramyou.app.telegram.model.AuthUiState
import com.telegramyou.app.telegram.model.ChatDetail
import com.telegramyou.app.telegram.model.ChatMessage
import com.telegramyou.app.telegram.model.MessageHit
import com.telegramyou.app.telegram.model.MessageReaction
import com.telegramyou.app.telegram.model.ChatPreview
import com.telegramyou.app.telegram.model.MessageContentType
import com.telegramyou.app.telegram.model.StoryItem
import com.telegramyou.app.telegram.model.TelegramUser
import com.telegramyou.app.telegram.model.toggleReaction as applyReaction
import com.telegramyou.app.ui.chat.formatDuration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
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

    // extraBufferCapacity so an emit never suspends: this flow is written to
    // from a timer that must not be held up by a slow subscriber, and there
    // is no sensible backpressure answer for "a message arrived".
    private val _incomingMessages = MutableSharedFlow<ChatMessage>(extraBufferCapacity = 64)
    override val incomingMessages: SharedFlow<ChatMessage> = _incomingMessages.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var chatter: Job? = null

    override fun start() {
        _authState.value = AuthUiState(state = AuthState.WaitPhoneNumber, isLoading = false)
        seedMessages()
    }

    override fun shutdown() {
        chatter?.cancel()
        chatter = null
    }

    /**
     * A demo chat that says something every so often.
     *
     * Without this there is nothing to notify about offline, and notification
     * work would be unverifiable without a live account — which is exactly
     * the position this backend exists to avoid. It starts once the person is
     * signed in, not at construction, so the login screen is quiet.
     */
    private fun startDemoChatter() {
        if (chatter != null) return
        chatter = scope.launch {
            var index = 0
            while (isActive) {
                delay(DEMO_CHATTER_INTERVAL_MS)
                val chat = _chats.value.firstOrNull { !it.isMuted } ?: continue
                val line = DEMO_CHATTER_LINES[index % DEMO_CHATTER_LINES.size]
                index++
                appendIncoming(chat.id, chat.title, line)
            }
        }
    }

    /** A message from the other side: stored, counted and announced. */
    private fun appendIncoming(chatId: Long, senderName: String, text: String) {
        val msg = ChatMessage(
            id = messageId.incrementAndGet(),
            chatId = chatId,
            text = text,
            isOutgoing = false,
            timeLabel = demoTimeFormat.format(Date()),
            date = System.currentTimeMillis() / 1000,
            senderName = senderName,
            isRead = false,
            contentType = MessageContentType.Text
        )
        chatMessages.getOrPut(chatId) { mutableListOf() }.add(msg)
        _chats.update { list ->
            list.map { chat ->
                if (chat.id == chatId) {
                    chat.copy(
                        lastMessage = text,
                        timestampLabel = msg.timeLabel,
                        unreadCount = chat.unreadCount + 1
                    )
                } else {
                    chat
                }
            }
        }
        _incomingMessages.tryEmit(msg)
    }

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
        startDemoChatter()
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
        startDemoChatter()
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

    override suspend fun searchChats(query: String, limit: Int): List<ChatPreview> {
        if (query.isBlank()) return emptyList()
        delay(140)
        // Title and last message both, because searching for a phrase you
        // remember from a conversation is the common case, not searching for
        // a name you already know.
        return _chats.value
            .filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.lastMessage.contains(query, ignoreCase = true)
            }
            .take(limit)
    }

    override suspend fun searchMessages(query: String, limit: Int): List<MessageHit> {
        if (query.isBlank()) return emptyList()
        delay(160)
        val byId = _chats.value.associateBy { it.id }
        return chatMessages.entries
            .flatMap { (chatId, messages) ->
                val chat = byId[chatId] ?: return@flatMap emptyList()
                messages
                    .filter { it.text.contains(query, ignoreCase = true) }
                    .map { MessageHit(chat = chat, message = it) }
            }
            .sortedByDescending { it.message.date }
            .take(limit)
    }

    override suspend fun setChatMuted(chatId: Long, muted: Boolean) {
        delay(80)
        _chats.update { list ->
            list.map { if (it.id == chatId) it.copy(isMuted = muted) else it }
        }
    }

    override suspend fun setChatPinned(chatId: Long, pinned: Boolean) {
        delay(80)
        _chats.update { list ->
            val changed = list.map {
                if (it.id == chatId) it.copy(isPinned = pinned) else it
            }
            // Pinned chats are drawn as their own group, and the group is taken
            // from the list's own order — so a chat that has just been pinned
            // has to move to where that group is, or it would appear to have
            // done nothing.
            changed.sortedByDescending { it.isPinned }
        }
    }

    override suspend fun markChatRead(chatId: Long) {
        delay(60)
        _chats.update { list ->
            list.map { if (it.id == chatId) it.copy(unreadCount = 0) else it }
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
            isTyping = chatId == 2L,
            // One chat has something pinned, so the bar is visible offline.
            pinnedMessage = if (chatId == 1L) messages.firstOrNull() else null,
            members = if (chat.isGroup) demoMembers else emptyList()
        )
    }

    /**
     * Who is in the demo group.
     *
     * Deliberately longer than the cluster draws, and deliberately including
     * people who never speak: the whole point of a member list is that it is
     * not the list of who has been talking, and a demo where the two happen to
     * match would hide the difference this was built to fix.
     */
    private val demoMembers = listOf(
        TelegramUser(id = 11, firstName = "Lina", lastName = "Park"),
        TelegramUser(id = 12, firstName = "Artem", lastName = "S"),
        TelegramUser(id = 13, firstName = "Kotlin", lastName = "Night"),
        TelegramUser(id = 14, firstName = "Nadia", lastName = "Orlova"),
        TelegramUser(id = 15, firstName = "Pavel", lastName = "Gromov"),
        TelegramUser(id = 16, firstName = "Sasha", lastName = "Vetrov"),
        TelegramUser(id = 17, firstName = "Mira", lastName = "Solano")
    )

    /**
     * Demo mode has no history behind what it seeds, so this always reports
     * the end of the conversation. It exists so the paging path is exercised
     * offline: the screen asks, gets nothing, and stops asking — which is the
     * behaviour worth checking without an account.
     */
    override suspend fun loadOlderMessages(
        chatId: Long,
        beforeMessageId: Long,
        limit: Int
    ): List<ChatMessage> {
        delay(220)
        return emptyList()
    }

    override suspend fun searchChatMessages(
        chatId: Long,
        query: String,
        limit: Int
    ): List<ChatMessage> {
        delay(120)
        if (query.isBlank()) return emptyList()
        return chatMessages[chatId].orEmpty()
            .filter { it.text.contains(query, ignoreCase = true) }
            .takeLast(limit)
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
            is AttachmentDraft.Voice -> {
                appendOutgoing(
                    chatId = chatId,
                    text = formatDuration(draft.durationSeconds.toLong()),
                    type = MessageContentType.Voice,
                    mediaEmoji = "🎤",
                    replyToId = replyToId,
                    // The file the recorder just wrote, so a voice message
                    // made in demo mode plays back offline — with the shape of
                    // what was actually said, measured while recording.
                    voicePath = draft.path,
                    waveform = draft.waveform
                )
            }
            is AttachmentDraft.Photos -> {
                draft.uris.forEachIndexed { index, uri ->
                    appendOutgoing(
                        chatId = chatId,
                        text = caption.ifBlank { "Photo" },
                        type = MessageContentType.Photo,
                        mediaEmoji = "🖼️",
                        replyToId = replyToId.takeIf { index == 0 },
                        // The Uri the picker returned. Coil opens a content://
                        // as readily as a file, so a photo picked in demo mode
                        // is actually drawn rather than described.
                        photoPath = uri
                    )
                }
            }
        }
    }

    override suspend fun forwardMessages(
        fromChatId: Long,
        messageIds: List<Long>,
        toChatId: Long
    ) {
        delay(160)
        val source = chatMessages[fromChatId].orEmpty().filter { it.id in messageIds }
        val target = chatMessages.getOrPut(toChatId) { mutableListOf() }
        source.forEach { original ->
            // A forward is a new message in the target chat, outgoing because
            // we are the one sending it, and without the original's reactions
            // or delivery state — none of which travel with a forward.
            target += original.copy(
                id = messageId.incrementAndGet(),
                chatId = toChatId,
                isOutgoing = true,
                reactions = emptyList(),
                isRead = false,
                canBeEdited = false,
                canBeDeletedForEveryone = true
            )
        }
    }

    override suspend fun deleteMessage(
        chatId: Long,
        messageId: Long,
        forEveryone: Boolean
    ) {
        delay(80)
        chatMessages[chatId]?.removeAll { it.id == messageId }
    }

    override suspend fun editMessage(chatId: Long, messageId: Long, text: String) {
        delay(80)
        val bucket = chatMessages[chatId] ?: return
        val index = bucket.indexOfFirst { it.id == messageId }
        if (index == -1) return
        bucket[index] = bucket[index].copy(text = text, isEdited = true)
    }

    /**
     * Applies the same arithmetic the screen already applied optimistically,
     * so a reload does not contradict what the tap drew. Imported under
     * another name because this class has a toggleReaction of its own.
     */
    override suspend fun toggleReaction(chatId: Long, messageId: Long, emoji: String) {
        delay(120)
        val bucket = chatMessages[chatId] ?: return
        val index = bucket.indexOfFirst { it.id == messageId }
        if (index == -1) return
        val message = bucket[index]
        bucket[index] = message.copy(reactions = applyReaction(message.reactions, emoji))
    }

    /**
     * Telegram's own default set, in its order. Demo mode has no chat
     * restrictions to honour, so every chat offers all of them.
     */
    override suspend fun availableReactions(chatId: Long): List<String> = DEMO_REACTIONS

    /**
     * Demo mode holds no remote files, so there is never anything to fetch.
     *
     * A recording made here is already a path on this device and arrives on
     * the message itself — which is why playing back your own voice message
     * works offline, and why nothing else does.
     */
    override suspend fun downloadFile(fileId: Int): String? = null

    override suspend fun markStorySeen(storyId: Long) {
        _stories.update { list ->
            list.map { if (it.id == storyId) it.copy(hasUnseen = false) else it }
        }
    }

    override suspend fun logout() {
        delay(200)
        _authState.value = AuthUiState(state = AuthState.WaitPhoneNumber)
    }

    // The profile calls. Each one edits the account held in _authState, which
    // is the only copy the demo backend has — there is no server behind it to
    // disagree, so refreshMe has nothing to fetch and says so rather than
    // pretending to do work.
    //
    // The delays are not decoration. They are what makes the save button's
    // disabled-while-saving state and the progress it shows visible at all
    // when the demo build is the one being looked at, which it usually is.

    override suspend fun setName(firstName: String, lastName: String) {
        delay(250)
        updateMe { it.copy(firstName = firstName, lastName = lastName) }
    }

    override suspend fun setBio(bio: String) {
        delay(200)
        updateMe { it.copy(bio = bio) }
    }

    override suspend fun setUsername(username: String) {
        delay(250)
        // The one refusal worth having offline: a username has to be unique
        // across Telegram, and a screen that has never once seen that error
        // is a screen whose error path has never been looked at.
        if (username.equals("telegram", ignoreCase = true)) {
            throw IllegalStateException("Username is already taken")
        }
        updateMe { it.copy(username = username.ifBlank { null }) }
    }

    override suspend fun refreshMe() = Unit

    private fun updateMe(edit: (TelegramUser) -> TelegramUser) {
        _authState.update { state ->
            state.me?.let { state.copy(me = edit(it)) } ?: state
        }
    }

    private fun appendOutgoing(
        chatId: Long,
        text: String,
        type: MessageContentType,
        fileName: String? = null,
        fileSizeLabel: String? = null,
        mediaEmoji: String? = null,
        replyToId: Long? = null,
        voicePath: String? = null,
        waveform: List<Int> = emptyList(),
        photoPath: String? = null
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
            replyToSender = quoted?.senderName,
            canBeEdited = true,
            canBeDeletedForSelf = true,
            canBeDeletedForEveryone = true,
            voicePath = voicePath,
            waveform = waveform,
            photoPath = photoPath
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
            demoMessage(2, 1, "Material 3 Expressive: MaterialExpressiveTheme, the stock motion scheme, a real LoadingIndicator. On the alpha, since no stable release exposes any of it.", false, today + 60, "Material Design", reactions = listOf(MessageReaction("🔥", count = 12), MessageReaction("👍", count = 4, isChosen = true))),
            demoMessage(3, 1, "Attach files from the composer. Stories sit on top of the chat list.", false, today + 120, "Material Design"),
            demoMessage(4, 1, "Looks sharp. Let’s keep the teal identity.", true, today + 660, isRead = true, reactions = listOf(MessageReaction("❤️", count = 1)))
        )
        chatMessages[2] = mutableListOf(
            demoMessage(10, 2, "Did you try the expressive loading indicator?", false, yesterday, "Lina Park"),
            demoMessage(11, 2, "Yes — and the split send button feels great.", true, yesterday + 180, isRead = true),
            demoMessage(12, 2, "Sending a voice note next 🎧", false, today + 300, "Lina Park")
        )
        // A group that behaves like one: several people, because the header
        // draws a cluster of whoever is talking, and a "group" where one
        // person says everything shows a single avatar — which is to say it
        // shows nothing of what a group looks like. Demo mode exists to make
        // the interface visible without an account, and that has to include
        // the parts which only appear with more than one person in the room.
        chatMessages[3] = mutableListOf(
            demoMessage(20, 3, "Drop assets in the thread", false, now - 2 * day, "Maya"),
            demoMessage(21, 3, "brand-kit.zip", false, now - 2 * day + 30, "Maya", contentType = MessageContentType.Document, fileName = "brand-kit.zip", fileSizeLabel = "4.8 MB"),
            demoMessage(22, 3, "Got them. The tonal palette is the part I want to steal.", false, now - day - 4 * 60 * 60, "Ivan"),
            demoMessage(23, 3, "Shapes too — every avatar up there is a different one.", false, now - day - 3 * 60 * 60, "Noor", reactions = listOf(MessageReaction("🔥", count = 3))),
            demoMessage(24, 3, "That is the shape library doing its job.", true, now - day - 2 * 60 * 60, isRead = true),
            demoMessage(25, 3, "Figma dump is in #files now", false, today - 90 * 60, "Sasha"),
            demoMessage(26, 3, "Reviewing tonight 👀", false, today - 40 * 60, "Ivan")
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
        fileSizeLabel: String? = null,
        reactions: List<MessageReaction> = emptyList()
    ) = ChatMessage(
        id = id,
        chatId = chatId,
        text = text,
        isOutgoing = isOutgoing,
        timeLabel = demoTimeFormat.format(Date(date * 1000L)),
        date = date,
        senderName = senderName,
        senderId = senderName?.hashCode()?.toLong(),
        canBeEdited = isOutgoing,
        canBeDeletedForSelf = true,
        canBeDeletedForEveryone = isOutgoing,
        isRead = isRead,
        contentType = contentType,
        fileName = fileName,
        fileSizeLabel = fileSizeLabel,
        reactions = reactions
    )

    /** Telegram's default reaction set, in its order. */
private val DEMO_REACTIONS = listOf("👍", "👎", "❤️", "🔥", "🎉", "😁", "🤔", "😢")

/**
 * How often the demo chat says something.
 *
 * Long enough not to be a nuisance while someone is looking at the interface,
 * short enough that a notification arrives within one emulator test.
 *
 * `val` rather than `const val`: everything from DEMO_REACTIONS down is
 * inside the class body, whatever the indentation suggests, and const is
 * only allowed at the top level or in an object.
 */
private val DEMO_CHATTER_INTERVAL_MS = 25_000L

private val DEMO_CHATTER_LINES = listOf(
    "Did the ButtonGroup land?",
    "The shade should show this one.",
    "Tapping this ought to open the right chat.",
    "Muting me should stop these."
)

private val demoTimeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
}
