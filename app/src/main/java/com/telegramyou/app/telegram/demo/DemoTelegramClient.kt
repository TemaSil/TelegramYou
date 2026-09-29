package com.telegramyou.app.telegram.demo

import com.telegramyou.app.telegram.model.ForumTopic
import com.telegramyou.app.telegram.model.SharedMediaKind
import com.telegramyou.app.telegram.model.DownloadEntry
import com.telegramyou.app.telegram.model.DownloadOutcome
import com.telegramyou.app.telegram.model.AdminRights
import com.telegramyou.app.telegram.model.JoinRequest
import com.telegramyou.app.telegram.model.adminTitle
import com.telegramyou.app.telegram.model.matchingMembers
import com.telegramyou.app.telegram.model.GroupManagement
import com.telegramyou.app.telegram.model.GroupMember
import com.telegramyou.app.telegram.model.GroupPermissions
import com.telegramyou.app.telegram.model.GroupRights
import com.telegramyou.app.telegram.model.InviteLink
import com.telegramyou.app.telegram.model.MemberAction
import com.telegramyou.app.telegram.model.MemberRole
import com.telegramyou.app.telegram.model.TOPIC_COLORS
import com.telegramyou.app.telegram.model.StoryAudience
import com.telegramyou.app.telegram.model.placePickedEmoji
import com.telegramyou.app.telegram.model.PickedEmoji
import com.telegramyou.app.telegram.model.LocationContent
import com.telegramyou.app.telegram.model.ContactContent
import com.telegramyou.app.telegram.model.ReactionOption
import com.telegramyou.app.telegram.model.customReactionKey
import com.telegramyou.app.telegram.model.StickerFormat
import com.telegramyou.app.notifications.ChatNotificationSettings
import com.telegramyou.app.BuildConfig
import com.telegramyou.app.R
import com.telegramyou.app.telegram.TelegramClient
import com.telegramyou.app.telegram.model.AttachmentDraft
import com.telegramyou.app.telegram.model.AuthState
import com.telegramyou.app.telegram.model.PersonProfile
import com.telegramyou.app.telegram.model.PrivacySetting
import com.telegramyou.app.telegram.model.PrivacyRules
import com.telegramyou.app.telegram.model.PrivacyException
import com.telegramyou.app.telegram.model.PrivacyAudience
import com.telegramyou.app.telegram.model.StorageUsage
import com.telegramyou.app.telegram.model.StorageSlice
import com.telegramyou.app.telegram.model.StorageKind
import com.telegramyou.app.telegram.model.DeviceKind
import com.telegramyou.app.telegram.model.ActiveSession
import com.telegramyou.app.telegram.model.EmailReset
import com.telegramyou.app.telegram.model.AuthUiState
import com.telegramyou.app.telegram.model.ChatDetail
import com.telegramyou.app.telegram.model.ChatFolder
import com.telegramyou.app.telegram.model.GifItem
import com.telegramyou.app.telegram.model.FolderRules
import com.telegramyou.app.telegram.model.ChatMessage
import com.telegramyou.app.telegram.model.MessageUpdate
import com.telegramyou.app.telegram.model.MessageHit
import com.telegramyou.app.telegram.model.PostSearch
import com.telegramyou.app.telegram.model.PollDraft
import com.telegramyou.app.telegram.model.EntityType
import com.telegramyou.app.telegram.model.TextEntity
import com.telegramyou.app.telegram.model.parseMarkdown
import com.telegramyou.app.telegram.model.AudioContent
import com.telegramyou.app.telegram.model.MessageReaction
import com.telegramyou.app.telegram.model.ChatPreview
import com.telegramyou.app.telegram.model.LinkPreview
import com.telegramyou.app.telegram.model.MessageContentType
import com.telegramyou.app.telegram.model.ProxyServer
import com.telegramyou.app.telegram.model.StickerContent
import com.telegramyou.app.telegram.model.StickerSetPreview
import com.telegramyou.app.telegram.model.StoryFrame
import com.telegramyou.app.telegram.model.StoryItem
import com.telegramyou.app.telegram.model.TelegramUser
import com.telegramyou.app.telegram.model.InviteLinkPreview
import com.telegramyou.app.telegram.model.VideoContent
import com.telegramyou.app.telegram.model.ButtonAction
import com.telegramyou.app.telegram.model.CallbackAnswer
import com.telegramyou.app.telegram.model.InlineButton
import com.telegramyou.app.telegram.model.PollContent
import com.telegramyou.app.telegram.model.PollOption
import com.telegramyou.app.telegram.model.ReplyKey
import com.telegramyou.app.telegram.model.ReplyKeyboard
import com.telegramyou.app.ui.media.FileTransfer
import com.telegramyou.app.telegram.model.toggleReaction as applyReaction
import com.telegramyou.app.ui.chat.formatDuration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.channels.Channel
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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import java.util.concurrent.atomic.AtomicLong

/**
 * Fully interactive offline client for UI / Expressive motion development.
 * Swap to TdLibTelegramClient when TELEGRAM_API_ID / HASH are set in local.properties.
 */
class DemoTelegramClient(
    /**
     * Start already signed in. The demo reached from the login screen does:
     * it is somewhere to look around, and making someone type a made-up
     * number and 12345 first would be a login screen for nothing. The demo
     * build starts signed out, because the UI test drives the login.
     */
    private val signedIn: Boolean = false
) : TelegramClient {
    private val messageId = AtomicLong(1_000)

    private val _authState = MutableStateFlow(
        AuthUiState(state = AuthState.Bootstrapping, isLoading = true)
    )
    override val authState: StateFlow<AuthUiState> = _authState.asStateFlow()

    /**
     * The seeded folders' ids.
     *
     * Numbers rather than an enum because that is what the server sends —
     * TDLib identifies a folder by an int — and a demo backend that used
     * something nicer would be modelling a Telegram that does not exist.
     *
     * Declared here, above the properties that read them, and that is not a
     * matter of taste. Kotlin initialises properties in declaration order, so
     * the same three lines at the foot of the class are still zero while
     * `seedChats()` and `seedFolders()` run — every folder came out with id 0,
     * every chat joined it, and all four tabs showed the same count. The
     * compiler says nothing about it; the emulator did.
     */
    private val DEMO_CUSTOM_EMOJI = 5_000_000_001L

    /** The id of the demo's custom-emoji set, and what is in it. */
    private val DEMO_EMOJI_SET = 90L
    private val DEMO_EMOJI_SET_EMOJI = "🦄 🌈 ✨ 💜 🪐 🎈 🍀 🔮"

    private val FOLDER_WORK = 1
    private val FOLDER_PEOPLE = 2
    private val FOLDER_NEWS = 3

    // Private chats with people can be emptied on both sides, as on Telegram;
    // bots, Saved Messages, groups and channels cannot.
    private val _chats = MutableStateFlow(
        seedChats().map { chat ->
            val withPerson = !chat.isGroup && !chat.isChannel && !chat.isSavedMessages && !chat.isBot
            if (withPerson) chat.copy(canDeleteForEveryone = true) else chat
        }
    )
    override val chats: StateFlow<List<ChatPreview>> = _chats.asStateFlow()

    // Seeded rather than empty, which is the interesting half: an account
    // with no folders draws no tab strip at all, and that case is already
    // visible every time the strip is absent. Three, because two tabs and
    // "All" fit across a phone and a fourth is what makes the strip scroll —
    // which is the layout worth looking at.
    private val _fileTransfers = MutableStateFlow<Map<Int, FileTransfer>>(emptyMap())
    override val fileTransfers: StateFlow<Map<Int, FileTransfer>> =
        _fileTransfers.asStateFlow()

    private val _folders = MutableStateFlow(seedFolders())
    override val folders: StateFlow<List<ChatFolder>> = _folders.asStateFlow()

    private val _stories = MutableStateFlow(seedStories())
    override val stories: StateFlow<List<StoryItem>> = _stories.asStateFlow()

    private val chatMessages = mutableMapOf<Long, MutableList<ChatMessage>>()

    // extraBufferCapacity so an emit never suspends: this flow is written to
    // from a timer that must not be held up by a slow subscriber, and there
    // is no sensible backpressure answer for "a message arrived".
    private val _incomingMessages = MutableSharedFlow<ChatMessage>(extraBufferCapacity = 64)
    override val incomingMessages: SharedFlow<ChatMessage> = _incomingMessages.asSharedFlow()

    private val _messageUpdates = MutableSharedFlow<MessageUpdate>(extraBufferCapacity = 64)
    override val messageUpdates: SharedFlow<MessageUpdate> = _messageUpdates.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var chatter: Job? = null

    override fun start() {
        _authState.value = AuthUiState(state = AuthState.WaitPhoneNumber, isLoading = false)
        seedMessages()
        if (signedIn) {
            _authState.value = AuthUiState(
                state = AuthState.Ready,
                me = TelegramUser(
                    id = 1,
                    firstName = "You",
                    lastName = "Expressive",
                    username = "telegramyou",
                    isPremium = true
                )
            )
            startDemoChatter()
        }
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
    private fun setTyping(chatId: Long, typing: Boolean) {
        _chats.update { list ->
            list.map { if (it.id == chatId) it.copy(isTyping = typing) else it }
        }
    }

    /** Nudges from [speakNow]; one waiting is as good as several. */
    private val nudges = Channel<Unit>(Channel.CONFLATED)

    /**
     * The next line now, rather than when the timer says — for the UI test,
     * whose notification checks otherwise sat out up to two intervals of
     * this chat's schedule. The schedule itself is unchanged: the rest of
     * the tests, and anyone using the demo, hear it at the usual pace.
     */
    fun speakNow() {
        nudges.trySend(Unit)
    }

    private fun startDemoChatter() {
        if (chatter != null) return
        chatter = scope.launch {
            var index = 0
            while (isActive) {
                withTimeoutOrNull(DEMO_CHATTER_INTERVAL_MS - DEMO_TYPING_MS) { nudges.receive() }
                // The seeded chat that talks, while it is unmuted — not
                // whichever chat happens to be first. A group created or
                // joined in the demo goes to the top of the list, and taking
                // "first unmuted" meant the newest group started chattering,
                // which is not what a new group does and broke every test
                // that listens for Material Design's notification.
                val chat = _chats.value.firstOrNull { it.id == CHATTY_CHAT_ID && !it.isMuted }
                    ?: _chats.value.firstOrNull { !it.isMuted }
                    ?: continue
                val line = DEMO_CHATTER_LINES[index % DEMO_CHATTER_LINES.size]
                index++
                // Typing first, as a person does: the avatar morphs for a few
                // seconds before each line lands, which is how the typing
                // shape can be seen without an account.
                setTyping(chat.id, true)
                delay(DEMO_TYPING_MS)
                setTyping(chat.id, false)
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
        _messageUpdates.tryEmit(MessageUpdate.Added(msg))
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
        // A number ending in five nines takes the other road in: Telegram
        // asking for a login email first, the way it does some new accounts.
        // It is here so the UI test can walk the email steps with no mailbox.
        if (phone.filter { it.isDigit() }.endsWith(DEMO_EMAIL_PHONE_SUFFIX)) {
            _authState.update {
                it.copy(isLoading = false, state = AuthState.WaitEmailAddress)
            }
            return
        }
        _authState.update {
            it.copy(
                isLoading = false,
                state = AuthState.WaitCode,
                codeHint = "Demo mode: the code is 12345",
                // What a real SMS looks like to the screen: five digits, and
                // another may be asked for after half a minute.
                codeLength = 5,
                canResend = true,
                resendAfterSeconds = 30,
                codeSentAtMillis = System.currentTimeMillis()
            )
        }
    }

    override suspend fun submitEmailAddress(email: String) {
        _authState.update { it.copy(isLoading = true, errorMessage = null) }
        delay(500)
        _authState.update {
            it.copy(
                isLoading = false,
                state = AuthState.WaitEmailCode,
                codeHint = "Demo mode: sent to ${maskedEmail(email)}, the code is 12345",
                codeLength = 5,
                canResend = true,
                resendAfterSeconds = 0,
                codeSentAtMillis = System.currentTimeMillis(),
                emailReset = EmailReset.Available(DEMO_EMAIL_RESET_WAIT)
            )
        }
    }

    override suspend fun submitEmailCode(code: String) = submitCode(code)

    /** A first press asks for the reset; a second, once it is pending, lands it. */
    override suspend fun resetEmail() {
        val reset = _authState.value.emailReset
        _authState.update {
            if (reset is EmailReset.Pending) {
                it.copy(
                    state = AuthState.WaitCode,
                    emailReset = null,
                    codeHint = "Demo mode: the code is 12345",
                    codeSentAtMillis = System.currentTimeMillis()
                )
            } else {
                it.copy(emailReset = EmailReset.Pending(DEMO_EMAIL_RESET_WAIT))
            }
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

    /**
     * A link that only looks like Telegram's, and a "scan" that happens by
     * itself a few seconds later — the demo has no other phone to scan with,
     * and the screen has to be seen to finish.
     */
    override suspend fun requestQrLogin() {
        _authState.update { it.copy(isLoading = true, errorMessage = null) }
        delay(300)
        _authState.update {
            it.copy(
                state = AuthState.WaitQrScan,
                isLoading = false,
                qrLink = "tg://login?token=demo-" + System.currentTimeMillis()
            )
        }
        delay(DEMO_QR_SCAN_MS)
        if (_authState.value.state != AuthState.WaitQrScan) return
        _authState.update {
            it.copy(
                state = AuthState.Ready,
                qrLink = null,
                me = TelegramUser(id = 1, firstName = "You", lastName = "Expressive", username = "telegramyou", isPremium = true)
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
                me = TelegramUser(id = 1, firstName = "You", lastName = "Expressive", username = "telegramyou", isPremium = true)
            )
        }
        startDemoChatter()
    }

    override suspend fun resendCode() {
        delay(300)
        _authState.update {
            it.copy(
                codeHint = "Demo mode: the code is still 12345",
                errorMessage = null,
                codeSentAtMillis = System.currentTimeMillis()
            )
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

    /**
     * Public chats this account is not in, for global search and the
     * recommendations. Not in [_chats]: finding one does not join it.
     */
    private val publicChats = listOf(
        ChatPreview(
            PUBLIC_CHANNEL_ID, DEMO_PUBLIC_CHANNEL, "Material 3 Expressive, in depth", "",
            isChannel = true, avatarColor = 131
        ),
        ChatPreview(
            PUBLIC_CHANNEL_ID + 1, "Android Developers", "Compose 1.12 is out", "",
            isChannel = true, avatarColor = 141
        ),
        ChatPreview(
            PUBLIC_CHANNEL_ID + 2, "Compose Community", "Ask anything about Compose", "",
            isGroup = true, avatarColor = 151
        ),
        ChatPreview(
            PUBLIC_CHANNEL_ID + 3, "Material Colour Bot", "Send a colour, get a palette", "",
            isBot = true, avatarColor = 161
        )
    )

    /** Chats opened from search, newest first, the way TDLib keeps them. */
    private val recentlyFound = mutableListOf(3L, BOT_CHAT_ID, 4L)

    /** Free post searches left today; the demo counts them down. */
    private var freePostSearches = 10

    override suspend fun searchPublicChats(query: String): List<ChatPreview> {
        if (query.isBlank()) return emptyList()
        delay(200)
        return publicChats.filter { it.title.contains(query, ignoreCase = true) }
    }

    override suspend fun chatByUsername(username: String): Long? {
        delay(80)
        // Every demo chat answers to its first name, lower-cased: @lina is
        // Lina Park.
        return _chats.value.firstOrNull { it.title.substringBefore(' ').equals(username, ignoreCase = true) }?.id
    }

    override suspend fun topPeople(limit: Int): List<ChatPreview> {
        delay(60)
        return _chats.value
            .filter { !it.isGroup && !it.isChannel && !it.isBot && !it.isArchived && it.title != "Saved Messages" }
            .take(limit)
    }

    override suspend fun recentlyFoundChats(): List<ChatPreview> {
        delay(60)
        val known = (_chats.value + publicChats).associateBy { it.id }
        return recentlyFound.mapNotNull { known[it] }
    }

    override suspend fun addRecentlyFoundChat(chatId: Long) {
        recentlyFound.remove(chatId)
        recentlyFound.add(0, chatId)
    }

    override suspend fun removeRecentlyFoundChat(chatId: Long) {
        recentlyFound.remove(chatId)
    }

    override suspend fun clearRecentlyFoundChats() = recentlyFound.clear()

    override suspend fun recommendedChannels(): List<ChatPreview> {
        delay(80)
        return publicChats.filter { it.isChannel }
    }

    override suspend fun searchPublicPosts(query: String, limit: Int): PostSearch {
        if (query.isBlank()) return PostSearch()
        delay(260)
        if (freePostSearches == 0) {
            return PostSearch(limitReached = true, freeLeft = 0, nextFreeInSeconds = 3 * 60 * 60)
        }
        freePostSearches -= 1
        val hits = publicChats.filter { it.isChannel }.flatMap { channel ->
            chatMessages[channel.id].orEmpty()
                .filter { it.text.contains(query, ignoreCase = true) }
                .map { MessageHit(chat = channel, message = it) }
        }
        return PostSearch(hits = hits.take(limit), freeLeft = freePostSearches)
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
        val current = _chats.value.firstOrNull { it.id == chatId }?.notifications ?: ChatNotificationSettings()
        setChatNotifications(
            chatId,
            current.copy(mutedUntil = if (muted) ChatNotificationSettings.MUTED_FOREVER else 0L)
        )
    }

    override suspend fun setChatNotifications(chatId: Long, settings: ChatNotificationSettings) {
        delay(80)
        val now = System.currentTimeMillis() / 1000
        _chats.update { list ->
            list.map {
                if (it.id == chatId) it.copy(notifications = settings, isMuted = settings.isMuted(now)) else it
            }
        }
    }

    override suspend fun contacts(): List<TelegramUser> {
        delay(150)
        return (demoContacts + addedContacts).sortedBy { it.displayName.lowercase() }
    }

    /** People added from the Contacts screen this session. */
    private val addedContacts = mutableListOf<TelegramUser>()

    /** Who is blocked, by user id; the demo's copy of Telegram's block list. */
    private val blockedIds = linkedSetOf<Long>()

    /**
     * Bios for the people the demo seeds, so a profile has something to say.
     * Keyed by first name, which is all the demo's identity amounts to.
     */
    private val demoBios = mapOf(
        "Lina" to "Designing calm interfaces. Swims at dawn 🌊",
        "Artem" to "Ships Android builds. Asks for the apk.",
        "Mom" to "💚",
        "Nadia" to "Motion and springs",
        "Build" to "Builds TelegramYou on every push"
    )

    /**
     * A person for a demo chat or contact: the same user every time they are
     * asked for, with a made-up but well-formed number and, for most, a
     * username — Mom has none, which is the case worth seeing.
     */
    private fun demoUser(base: TelegramUser): TelegramUser {
        val first = base.firstName
        return base.copy(
            username = base.username ?: if (first == "Mom") null
            else listOf(first, base.lastName).filter { it.isNotBlank() }.joinToString("_").lowercase(),
            phoneNumber = base.phoneNumber ?: ("1555010" + (base.id % 10000).toString().padStart(4, '0')),
            bio = base.bio.ifBlank { demoBios[first].orEmpty() }
        )
    }

    /** Everyone the demo knows of, by user id. */
    private fun demoPeople(): Map<Long, Pair<TelegramUser, Boolean>> {
        val known = (demoContacts + addedContacts + demoMembers).associateBy { it.id }
        val fromChats = _chats.value
            .filter { !it.isGroup && !it.isChannel && !it.isSavedMessages }
            .map { chat ->
                known.values.firstOrNull { it.displayName == chat.title }
                    ?: TelegramUser(
                        id = DEMO_PERSON_BASE + chat.id,
                        firstName = chat.title.substringBefore(' '),
                        lastName = chat.title.substringAfter(' ', ""),
                        avatarColor = chat.avatarColor,
                        photoPath = chat.photoPath
                    )
            }
        val bots = _chats.value.filter { it.isBot }.map { DEMO_PERSON_BASE + it.id }.toSet()
        return (known.values + fromChats).associate { user -> user.id to (demoUser(user) to (user.id in bots)) }
    }

    private fun profileOf(userId: Long): PersonProfile? {
        val (user, isBot) = demoPeople()[userId] ?: return null
        return PersonProfile(
            user = user,
            isContact = (demoContacts + addedContacts).any { it.id == userId },
            isBlocked = userId in blockedIds,
            isBot = isBot
        )
    }

    override suspend fun person(userId: Long): PersonProfile? {
        delay(120)
        return profileOf(userId)
    }

    override suspend fun personInChat(chatId: Long): PersonProfile? {
        delay(120)
        val chat = _chats.value.firstOrNull { it.id == chatId } ?: return null
        if (chat.isGroup || chat.isChannel || chat.isSavedMessages) return null
        val userId = demoPeople().values.firstOrNull { it.first.displayName == chat.title }?.first?.id
            ?: (DEMO_PERSON_BASE + chatId)
        return profileOf(userId)
    }

    override suspend fun setBlocked(userId: Long, blocked: Boolean) {
        delay(120)
        if (blocked) blockedIds += userId else blockedIds -= userId
    }

    override suspend fun blockedPeople(): List<TelegramUser> {
        delay(150)
        val people = demoPeople()
        return blockedIds.mapNotNull { people[it]?.first }
    }

    /**
     * Any well-formed number is "on Telegram" here, except one ending in 404,
     * which is the demo's way to show what happens when it is not.
     */
    override suspend fun addContact(phone: String, firstName: String, lastName: String): Long? {
        delay(300)
        val digits = phone.filter(Char::isDigit)
        if (digits.length < 8 || digits.endsWith("404")) return null
        (demoContacts + addedContacts).firstOrNull { it.phoneNumber?.filter(Char::isDigit) == digits }
            ?.let { return it.id }
        val user = TelegramUser(
            id = DEMO_ADDED_BASE + addedContacts.size,
            firstName = firstName.trim(),
            lastName = lastName.trim(),
            phoneNumber = digits
        )
        addedContacts += user
        return user.id
    }

    override suspend fun clearHistory(chatId: Long, forEveryone: Boolean) {
        delay(150)
        chatMessages[chatId]?.clear()
        _chats.update { list ->
            list.map { if (it.id == chatId) it.copy(lastMessage = "", unreadCount = 0, draft = "") else it }
        }
    }

    override suspend fun deleteChat(chatId: Long, forEveryone: Boolean) {
        delay(150)
        chatMessages.remove(chatId)
        _chats.update { list -> list.filterNot { it.id == chatId } }
    }

    override suspend fun openPrivateChat(userId: Long): Long {
        delay(200)
        // A chat with oneself is Saved Messages, as it is on Telegram.
        if (userId == _authState.value.me?.id) {
            _chats.value.firstOrNull { it.title == "Saved Messages" }?.let { return it.id }
        }
        val contact = (demoContacts + addedContacts + demoMembers).firstOrNull { it.id == userId }
            ?: demoPeople()[userId]?.first
            ?: return 1L
        // An existing conversation with that person if there is one, so the
        // picker lands where the chat list would have. Matching on the title
        // is the demo backend's whole idea of identity; a real one has user
        // ids on both sides of this.
        _chats.value.firstOrNull { it.title == contact.displayName }?.let {
            return it.id
        }
        // Otherwise a new, empty conversation, which is the case worth having
        // offline: it is the only way to see what this screen does when there
        // is nothing to show yet.
        val id = (_chats.value.maxOfOrNull { it.id } ?: 0L) + 1
        _chats.update { list ->
            list + ChatPreview(
                id = id,
                title = contact.displayName,
                lastMessage = "",
                timestampLabel = "now",
                avatarColor = contact.id,
                canDeleteForEveryone = true
            )
        }
        chatMessages[id] = mutableListOf()
        return id
    }

    /**
     * People to start a conversation with.
     *
     * Deliberately not the same set as the chat list: two of these have no
     * conversation yet, which is the case the picker exists for and the only
     * way to see an empty chat offline.
     */
    private val demoContacts = listOf(
        TelegramUser(id = 11, firstName = "Lina", lastName = "Park"),
        TelegramUser(id = 12, firstName = "Artem", lastName = "S"),
        TelegramUser(id = 21, firstName = "Dasha", lastName = "Nikitina"),
        TelegramUser(id = 22, firstName = "Ilya", lastName = "Voronov"),
        TelegramUser(id = 14, firstName = "Nadia", lastName = "Orlova")
    ).sortedBy { it.displayName.lowercase() }

    override suspend fun chatMedia(chatId: Long, limit: Int): List<ChatMessage> {
        delay(200)
        return chatMessages[chatId]
            .orEmpty()
            // Video as well as photos: the server's own filter for this
            // screen is photo-and-video, so a demo that showed only photos
            // would be modelling a grid Telegram does not have.
            .filter {
                it.contentType == MessageContentType.Photo ||
                    it.contentType == MessageContentType.Video
            }
            .asReversed()
            .take(limit)
    }

    override suspend fun setChatArchived(chatId: Long, archived: Boolean) {
        delay(120)
        _chats.update { list ->
            list.map {
                if (it.id == chatId) {
                    // Archiving unpins: a pinned chat in the archive would
                    // still draw its own group at the top of a screen nobody
                    // is looking at, and Telegram drops the pin too.
                    it.copy(isArchived = archived, isPinned = it.isPinned && !archived)
                } else {
                    it
                }
            }
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

    /** Every demo chat is already in memory; there is never a next page. */
    override suspend fun loadMoreChats(folderId: Int?): Boolean = false

    // Nothing to track: the demo keeps every chat in memory anyway.
    override fun retainChat(chatId: Long) = Unit
    override fun releaseChat(chatId: Long) = Unit

    override suspend fun openChat(chatId: Long): ChatDetail {
        delay(180)
        // A public chat found by search is readable before it is joined.
        val chat = _chats.value.firstOrNull { it.id == chatId }
            ?: publicChats.first { it.id == chatId }
        chatMessages.getOrPut(chatId) { mutableListOf() }
        val messages = messagesIn(chatId)
        return ChatDetail(
            chat = chat,
            // The latest window, the way TDLib opens a chat; the rest pages
            // in on scrolling, or around a search hit.
            messages = messages.takeLast(DEMO_WINDOW),
            memberCountLabel = when {
                chat.isChannel -> "128K subscribers"
                chat.isGroup -> "42 members"
                chat.isSavedMessages -> null
                else -> if (chat.isOnline) "online" else "last seen recently"
            },
            isTyping = chatId == 2L,
            // One chat has something pinned, so the bar is visible offline.
            pinnedMessage = if (chatId == 1L) messages.firstOrNull() else null,
            members = if (chat.isGroup) demoMembers else emptyList()
        )
    }

    // ── running a group ─────────────────────────────────────────────────

    /** The topic each chat's conversation screen is on; see setOpenTopic. */
    private val openTopics = mutableMapOf<Long, Int>()

    override fun setOpenTopic(chatId: Long, topicId: Int?) {
        if (topicId == null) openTopics.remove(chatId) else openTopics[chatId] = topicId
    }

    override fun closeOpenTopic(chatId: Long, topicId: Int) {
        if (openTopics[chatId] == topicId) openTopics.remove(chatId)
    }

    /** A chat's messages, or the open topic's when its screen is on one. */
    private fun messagesIn(chatId: Long): List<ChatMessage> {
        val all = chatMessages[chatId].orEmpty()
        val topic = openTopics[chatId] ?: return all
        return all.filter { it.topicId == topic }
    }

    /**
     * Where everybody stands, by group. The account owns Design Circle, so
     * running a group can be tried offline; in Kotlin Night it is a plain
     * member and sees nothing to run.
     */
    private val demoRoles = mutableMapOf(
        3L to mutableMapOf(1L to MemberRole.Owner, 14L to MemberRole.Admin, 17L to MemberRole.Restricted),
        7L to mutableMapOf(12L to MemberRole.Owner),
        FORUM_CHAT_ID to mutableMapOf(1L to MemberRole.Owner)
    )

    /** Titles the demo's admins go by, by group and person. */
    private val demoTitles = mutableMapOf((3L to 14L) to "Motion")

    /** Admins' rights where they were chosen, by group and person. */
    private val demoAdminRights = mutableMapOf<Pair<Long, Long>, AdminRights>()

    /** People let in through a join request, by group. */
    private val joinedMembers = mutableMapOf<Long, MutableList<TelegramUser>>()

    /** Who is asking to join, by group: two at Design Circle. */
    private val demoJoinRequests = mutableMapOf(
        3L to mutableListOf(
            JoinRequest(TelegramUser(id = 31, firstName = "Ilya", lastName = "Brand"), 0, "Motion designer, Tbilisi"),
            JoinRequest(TelegramUser(id = 32, firstName = "Zoe", lastName = "Hart"), 0)
        )
    )

    /** Drafts kept in each of the forum's topics. */
    private val topicDrafts = mutableMapOf<Int, String>()

    /** People taken out of a group here, who stop being listed in it. */
    private val removedMembers = mutableMapOf<Long, MutableSet<Long>>()

    private val demoPermissions = mutableMapOf<Long, GroupPermissions>()

    override suspend fun groupManagement(chatId: Long): GroupManagement? {
        delay(150)
        val chat = _chats.value.firstOrNull { it.id == chatId } ?: return null
        if (!chat.isGroup) return null
        val roles = demoRoles.getOrPut(chatId) { mutableMapOf() }
        val me = _authState.value.me ?: TelegramUser(id = 1, firstName = "You")
        val people = listOf(me) + demoMembers + joinedMembers[chatId].orEmpty()
        val gone = removedMembers[chatId].orEmpty()
        val members = people.filter { it.id !in gone }.map { user ->
            val role = roles[user.id] ?: MemberRole.Member
            GroupMember(
                user = user,
                role = role,
                title = demoTitles[chatId to user.id].orEmpty(),
                // The owner may change every admin; nobody else here may.
                canBeEdited = roles[1L] == MemberRole.Owner,
                adminRights = if (role == MemberRole.Admin) demoAdminRights[chatId to user.id] ?: AdminRights() else null
            )
        }
        return GroupManagement(
            rights = if (roles[1L] == MemberRole.Owner) GroupRights.All else GroupRights.None,
            members = members,
            permissions = demoPermissions[chatId] ?: GroupPermissions(),
            isForum = chat.isForum
        )
    }

    override suspend fun applyMemberAction(chatId: Long, userId: Long, action: MemberAction) {
        delay(200)
        val roles = demoRoles.getOrPut(chatId) { mutableMapOf() }
        when (action) {
            MemberAction.MakeAdmin, MemberAction.EditAdmin -> roles[userId] = MemberRole.Admin
            MemberAction.RemoveAdmin, MemberAction.Unrestrict -> roles.remove(userId)
            MemberAction.Restrict -> roles[userId] = MemberRole.Restricted
            MemberAction.Remove -> {
                roles.remove(userId)
                removedMembers.getOrPut(chatId) { mutableSetOf() } += userId
            }
        }
    }

    override suspend fun promoteMember(chatId: Long, userId: Long, rights: AdminRights, title: String) {
        delay(200)
        demoRoles.getOrPut(chatId) { mutableMapOf() }[userId] = MemberRole.Admin
        demoAdminRights[chatId to userId] = rights
        val cut = adminTitle(title)
        if (cut.isEmpty()) demoTitles.remove(chatId to userId) else demoTitles[chatId to userId] = cut
    }

    override suspend fun searchGroupMembers(chatId: Long, query: String): List<GroupMember> {
        val everybody = groupManagement(chatId)?.members.orEmpty()
        return matchingMembers(everybody, query)
    }

    override suspend fun joinRequests(chatId: Long): List<JoinRequest> {
        delay(150)
        val now = System.currentTimeMillis() / 1000
        return demoJoinRequests[chatId].orEmpty().mapIndexed { index, request ->
            request.copy(date = now - (index + 1) * 3_600L)
        }
    }

    override suspend fun processJoinRequest(chatId: Long, userId: Long, approve: Boolean) {
        delay(200)
        val requests = demoJoinRequests[chatId] ?: return
        val request = requests.firstOrNull { it.user.id == userId } ?: return
        requests.remove(request)
        if (approve) joinedMembers.getOrPut(chatId) { mutableListOf() } += request.user
    }

    override suspend fun renameForumTopic(chatId: Long, topicId: Int, name: String) {
        delay(150)
        val at = demoTopics.indexOfFirst { it.id == topicId }
        if (at >= 0) demoTopics[at] = demoTopics[at].copy(name = name.trim())
    }

    override suspend fun setForumTopicClosed(chatId: Long, topicId: Int, closed: Boolean) {
        delay(150)
        val at = demoTopics.indexOfFirst { it.id == topicId }
        if (at >= 0) demoTopics[at] = demoTopics[at].copy(isClosed = closed)
    }

    override suspend fun deleteForumTopic(chatId: Long, topicId: Int) {
        delay(150)
        demoTopics.removeAll { it.id == topicId && !it.isGeneral }
        chatMessages[chatId]?.removeAll { it.topicId == topicId }
    }

    override suspend fun saveTopicDraft(chatId: Long, topicId: Int, text: String) {
        if (text.isBlank()) topicDrafts.remove(topicId) else topicDrafts[topicId] = text
    }

    override suspend fun setGroupPermissions(chatId: Long, permissions: GroupPermissions) {
        delay(150)
        demoPermissions[chatId] = permissions
    }

    /** Every group's links, the primary one first; made on first asking. */
    private val demoLinks = mutableMapOf<Long, MutableList<InviteLink>>()

    private fun linksOf(chatId: Long): MutableList<InviteLink> = demoLinks.getOrPut(chatId) {
        mutableListOf(
            InviteLink("https://t.me/+TelegramYouDemo$chatId", isPrimary = true, memberCount = 12),
            InviteLink(
                "https://t.me/+DesignReview$chatId",
                name = "Design review",
                memberCount = 3,
                memberLimit = 10,
                expiresAt = System.currentTimeMillis() / 1000 + 2 * 86_400 + 600
            ),
            // The link the two waiting in joinRequests asked through.
            InviteLink(
                "https://t.me/+Applications$chatId",
                name = "Applications",
                createsJoinRequest = true,
                pendingRequests = demoJoinRequests[chatId]?.size ?: 0
            )
        )
    }

    override suspend fun inviteLinks(chatId: Long): List<InviteLink> {
        delay(150)
        val chat = _chats.value.firstOrNull { it.id == chatId } ?: return emptyList()
        if (!chat.isGroup && !chat.isChannel) return emptyList()
        return linksOf(chatId).sortedBy { it.isRevoked }
    }

    override suspend fun createInviteLink(
        chatId: Long,
        name: String,
        expiresAt: Long,
        memberLimit: Int,
        createsJoinRequest: Boolean
    ): InviteLink {
        delay(200)
        val link = InviteLink(
            link = "https://t.me/+Demo${chatId}n${messageId.incrementAndGet()}",
            name = name.trim(),
            expiresAt = expiresAt,
            memberLimit = if (createsJoinRequest) 0 else memberLimit,
            createsJoinRequest = createsJoinRequest
        )
        linksOf(chatId).add(link)
        return link
    }

    override suspend fun revokeInviteLink(chatId: Long, link: String) {
        delay(200)
        val links = linksOf(chatId)
        val at = links.indexOfFirst { it.link == link }
        if (at < 0) return
        val revoked = links[at]
        links[at] = revoked.copy(isRevoked = true, isPrimary = false)
        // The group always has a primary link: revoking it makes the next.
        if (revoked.isPrimary) {
            links.add(0, InviteLink("https://t.me/+TelegramYouDemo${chatId}r${messageId.incrementAndGet()}", isPrimary = true))
        }
    }

    /** The demo forum's topics, in the order they were started. */
    private val demoTopics = mutableListOf(
        ForumTopic(1, "General", TOPIC_COLORS[0], isGeneral = true),
        ForumTopic(2, DEMO_FORUM_TOPIC, TOPIC_COLORS[1], isPinned = true, unreadCount = 3),
        ForumTopic(3, "Bugs", TOPIC_COLORS[5]),
        ForumTopic(4, "Ideas", TOPIC_COLORS[3])
    )

    override suspend fun forumTopics(chatId: Long): List<ForumTopic> {
        delay(150)
        if (chatId != FORUM_CHAT_ID) return emptyList()
        val messages = chatMessages[chatId].orEmpty()
        // Each with its newest message, as the list shows a chat's.
        return demoTopics.map { topic ->
            val last = messages.lastOrNull { it.topicId == topic.id }
            topic.copy(
                lastMessage = last?.text.orEmpty(),
                timestampLabel = last?.timeLabel.orEmpty(),
                draft = topicDrafts[topic.id].orEmpty()
            )
        }
    }

    override suspend fun createForumTopic(chatId: Long, name: String): ForumTopic {
        delay(200)
        val topic = ForumTopic(
            id = (demoTopics.maxOfOrNull { it.id } ?: 0) + 1,
            name = name.trim(),
            iconColor = TOPIC_COLORS[demoTopics.size % TOPIC_COLORS.size]
        )
        demoTopics += topic
        return topic
    }

    /**
     * The demo music channel: a dozen tracks over as many days, oldest
     * first, every one the demo's chime under its own name — enough of a
     * queue to play through, shuffle and scroll.
     */
    private fun musicChannel(now: Long, day: Long): MutableList<ChatMessage> {
        val names = listOf(
            "Tonal Spot", "Container Transform", "Spring Back", "Shape Morph", "Wavy Line",
            "Surface Tint", "Emphasized Easing", "Cookie Nine", "Motion Scheme", "Pure Black",
            "Night Palette", "Morning Light"
        )
        return names.mapIndexed { index, name ->
            demoMessage(
                95_000L + index, MUSIC_CHANNEL_ID, "", false,
                now - (names.size - index) * day, DEMO_MUSIC_CHANNEL
            ).copy(
                contentType = MessageContentType.Audio,
                audio = AudioContent(
                    title = name,
                    performer = "Material Sound",
                    durationSeconds = DEMO_AUDIO_SECONDS,
                    fileName = "${name.lowercase().replace(' ', '-')}.wav",
                    fileId = DEMO_AUDIO_FILE_ID
                ),
                voiceFileId = DEMO_AUDIO_FILE_ID
            )
        }.toMutableList()
    }

    /** What has been said in each of the forum's topics. */
    private fun forumMessages(today: Long, yesterday: Long): MutableList<ChatMessage> {
        fun said(id: Long, topic: Int, text: String, at: Long, who: String) =
            demoMessage(id, FORUM_CHAT_ID, text, false, at, who).copy(topicId = topic)
        return mutableListOf(
            said(90001, 1, "Welcome! Pick a topic, or just say hi here.", yesterday, "Nadia Orlova"),
            said(90002, 3, "The composer jumps when the keyboard opens on Android 12", yesterday + 300, "Pavel Gromov"),
            said(90003, 4, "Could chat folders get their own colours?", yesterday + 600, "Mira Solano"),
            said(90004, 2, "1.5.1 is out: custom emoji and sending a location", yesterday + 900, "Nadia Orlova"),
            said(90005, 2, "Releases: 1.6 is out", today, "Nadia Orlova"),
            said(90006, 2, "Stories, video speed and picture-in-picture", today + 60, "Nadia Orlova")
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
    // ── privacy ──────────────────────────────────────────────────────────

    /**
     * An account set up the way people's usually are: the number for
     * contacts, most things for everybody, and one setting carrying
     * exceptions made somewhere else — which the screen has to show and keep.
     */
    private val demoPrivacy = mutableMapOf(
        PrivacySetting.PhoneNumber to PrivacyRules(PrivacyAudience.Contacts),
        PrivacySetting.FindByNumber to PrivacyRules(PrivacyAudience.Everybody),
        PrivacySetting.LastSeen to PrivacyRules(
            PrivacyAudience.Everybody,
            listOf(PrivacyException(allow = false, count = 2, raw = ""))
        ),
        PrivacySetting.ProfilePhoto to PrivacyRules(PrivacyAudience.Everybody),
        PrivacySetting.Bio to PrivacyRules(PrivacyAudience.Everybody),
        PrivacySetting.Forwards to PrivacyRules(PrivacyAudience.Everybody),
        PrivacySetting.Calls to PrivacyRules(PrivacyAudience.Contacts),
        PrivacySetting.Invites to PrivacyRules(PrivacyAudience.Everybody)
    )

    override suspend fun privacyRules(setting: PrivacySetting): PrivacyRules {
        delay(120)
        return demoPrivacy[setting] ?: PrivacyRules(PrivacyAudience.Everybody)
    }

    override suspend fun setPrivacyRules(setting: PrivacySetting, rules: PrivacyRules) {
        delay(250)
        demoPrivacy[setting] = rules
    }

    // ── sessions and storage ─────────────────────────────────────────────

    /**
     * Four places signed in: this phone, a desktop, a browser, and one
     * client that is not Telegram's — the case the devices screen exists to
     * catch, so the demo has to show what it looks like.
     */
    private val demoSessions: MutableList<ActiveSession> by lazy {
        val now = System.currentTimeMillis() / 1000
        mutableListOf(
            ActiveSession(1, true, DeviceKind.Android, "TelegramYou", "1.0", false, "Pixel 9", "Android", "16", now, "", "Home"),
            ActiveSession(2, false, DeviceKind.Windows, "Telegram Desktop", "5.2.3", true, "Desktop", "Windows", "11", now - 2 * 60 * 60, "", "Berlin, Germany"),
            ActiveSession(3, false, DeviceKind.Browser, "Telegram Web", "2.1", true, "Chrome", "macOS", "15", now - 3 * 24 * 60 * 60, "", "Lisbon, Portugal"),
            ActiveSession(4, false, DeviceKind.Android, "Nekogram", "11.1", false, "Galaxy S21", "Android", "14", now - 12 * 24 * 60 * 60, "", "Unknown")
        )
    }

    override suspend fun activeSessions(): List<ActiveSession> {
        delay(300)
        return demoSessions.toList()
    }

    override suspend fun terminateSession(id: Long) {
        delay(300)
        demoSessions.removeAll { it.id == id && !it.isCurrent }
    }

    override suspend fun terminateOtherSessions() {
        delay(400)
        demoSessions.removeAll { !it.isCurrent }
    }

    /** About two gigabytes, videos first — the shape a real cache takes after a month. */
    private val demoStorage = mutableListOf(
        StorageSlice(StorageKind.Videos, 1_284_000_000, 96),
        StorageSlice(StorageKind.Photos, 412_000_000, 1_830),
        StorageSlice(StorageKind.Files, 96_400_000, 14),
        StorageSlice(StorageKind.Stickers, 54_100_000, 620),
        StorageSlice(StorageKind.Voice, 38_200_000, 210),
        StorageSlice(StorageKind.ProfilePhotos, 21_300_000, 340)
    )

    override suspend fun storageUsage(): StorageUsage {
        delay(600)
        return StorageUsage(demoStorage.toList(), databaseBytes = 64_000_000)
    }

    override suspend fun clearCache(kinds: Set<StorageKind>): StorageUsage {
        delay(800)
        demoStorage.removeAll { it.kind in kinds }
        return StorageUsage(demoStorage.toList(), databaseBytes = 64_000_000)
    }

    override suspend fun loadOlderMessages(
        chatId: Long,
        beforeMessageId: Long,
        limit: Int
    ): List<ChatMessage> {
        delay(220)
        val all = messagesIn(chatId)
        val at = all.indexOfFirst { it.id == beforeMessageId }
        if (at <= 0) return emptyList()
        return all.subList((at - limit).coerceAtLeast(0), at).toList()
    }

    override suspend fun loadMessagesAround(
        chatId: Long,
        messageId: Long,
        limit: Int
    ): List<ChatMessage> {
        delay(220)
        val all = messagesIn(chatId)
        val at = all.indexOfFirst { it.id == messageId }
        if (at < 0) return emptyList()
        val from = (at - limit / 2).coerceAtLeast(0)
        return all.subList(from, (from + limit).coerceAtMost(all.size)).toList()
    }

    override suspend fun loadNewerMessages(
        chatId: Long,
        afterMessageId: Long,
        limit: Int
    ): List<ChatMessage> {
        delay(220)
        val all = messagesIn(chatId)
        val at = all.indexOfFirst { it.id == afterMessageId }
        if (at < 0) return emptyList()
        return all.subList(at + 1, (at + 1 + limit).coerceAtMost(all.size)).toList()
    }

    override suspend fun allMusic(query: String, cursor: String, limit: Int): Pair<List<ChatMessage>, String?> {
        delay(150)
        val all = chatMessages.values.flatten()
            .filter { it.contentType == MessageContentType.Audio && it.audio != null }
            .filter { track ->
                query.isBlank() ||
                    track.audio!!.displayTitle.contains(query, ignoreCase = true) ||
                    track.audio!!.performer.contains(query, ignoreCase = true)
            }
            .sortedByDescending { it.date }
        val from = cursor.toIntOrNull() ?: 0
        val page = all.drop(from).take(limit)
        return page to (from + page.size).takeIf { it < all.size }?.toString()
    }

    override suspend fun sharedMedia(
        chatId: Long,
        kind: SharedMediaKind,
        beforeMessageId: Long,
        limit: Int
    ): List<ChatMessage> {
        delay(150)
        // By date rather than id: the demo's ids are not in time order.
        val sorted = chatMessages[chatId].orEmpty()
            .filter { kind.matches(it) }
            .sortedByDescending { it.date }
        val start = if (beforeMessageId == 0L) 0 else sorted.indexOfFirst { it.id == beforeMessageId } + 1
        if (start <= 0 && beforeMessageId != 0L) return emptyList()
        return sorted.drop(start).take(limit)
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

    /** Messages waiting to go, by chat; see scheduledMessages. */
    private val scheduled = mutableMapOf<Long, MutableList<ChatMessage>>()

    override suspend fun saveDraft(chatId: Long, text: String) {
        val draft = if (text.isBlank()) "" else text
        _chats.update { chats -> chats.map { if (it.id == chatId) it.copy(draft = draft) else it } }
    }

    override suspend fun sendText(chatId: Long, text: String, replyToId: Long?, sendAt: Long?) {
        delay(120)
        if (sendAt != null) {
            // Kept apart until its time, which in the demo never comes on
            // its own: "Send now" is how one leaves the list.
            scheduled.getOrPut(chatId) { mutableListOf() } += ChatMessage(
                id = messageId.incrementAndGet(),
                chatId = chatId,
                text = text,
                isOutgoing = true,
                timeLabel = demoTimeFormat.format(Date(sendAt * 1000)),
                date = sendAt,
                canBeEdited = true,
                canBeDeletedForSelf = true,
                canBeDeletedForEveryone = true,
                scheduledAt = sendAt
            )
            setHasScheduled(chatId)
            return
        }
        val (plain, entities) = parseMarkdown(text)
        appendOutgoing(
            chatId = chatId,
            text = plain,
            type = MessageContentType.Text,
            replyToId = replyToId,
            entities = entities
        )
        if (chatId == BOT_CHAT_ID) scope.launch {
            delay(500)
            val answer = when (text.trim()) {
                "Status" -> DEMO_BOT_STATUS
                "Latest build" -> "Build 1.0.366, from main."
                "Help" -> "Press a key below, or a button under a message."
                else -> "I only know the keys below."
            }
            val reply = demoMessage(
                messageId.incrementAndGet(), BOT_CHAT_ID, answer, false,
                System.currentTimeMillis() / 1000, "Build Bot"
            )
            chatMessages.getOrPut(BOT_CHAT_ID) { mutableListOf() }.add(reply)
            _messageUpdates.tryEmit(MessageUpdate.Added(reply))
        }
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
                        // The caption on the first only, as the live client
                        // sends it.
                        text = caption.takeIf { index == 0 }.orEmpty().ifBlank { name },
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
                        text = caption.takeIf { index == 0 }.orEmpty().ifBlank { "Photo" },
                        type = MessageContentType.Photo,
                        mediaEmoji = "🖼️",
                        replyToId = replyToId.takeIf { index == 0 },
                        // The Uri the picker returned. Coil opens a content://
                        // as readily as a file, so a photo picked in demo mode
                        // is actually drawn rather than described.
                        photoPath = uri,
                        // An id to hang a progress bar on. The bytes are on
                        // this device already, so nothing is really sent —
                        // but a send with no bar would be a send nobody can
                        // look at, and this is the build people look at.
                        photoFileId = uploadFileId.incrementAndGet()
                            .also { fakeUpload(it, DEMO_UPLOAD_BYTES) }
                    )
                }
            }
        }
    }

    override suspend fun forwardMessages(
        fromChatId: Long,
        messageIds: List<Long>,
        toChatId: Long,
        withoutQuote: Boolean
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
                // Sent now, as Telegram dates a forward: Saved Messages'
                // music, newest first, starts with the track just saved.
                date = System.currentTimeMillis() / 1000,
                reactions = emptyList(),
                isRead = false,
                canBeEdited = false,
                canBeDeletedForEveryone = true
            ).also { _messageUpdates.tryEmit(MessageUpdate.Added(it)) }
        }
    }

    override suspend fun deleteMessage(
        chatId: Long,
        messageId: Long,
        forEveryone: Boolean
    ) {
        delay(80)
        chatMessages[chatId]?.removeAll { it.id == messageId }
        // A scheduled one is deleted the same way, from its own list.
        if (scheduled[chatId]?.removeAll { it.id == messageId } == true) setHasScheduled(chatId)
        _messageUpdates.tryEmit(MessageUpdate.Deleted(chatId, setOf(messageId)))
    }

    override suspend fun editMessage(chatId: Long, messageId: Long, text: String) {
        delay(80)
        val bucket = chatMessages[chatId] ?: return
        val index = bucket.indexOfFirst { it.id == messageId }
        if (index == -1) return
        val (plain, entities) = parseMarkdown(text)
        bucket[index] = bucket[index].copy(text = plain, entities = entities, isEdited = true)
        _messageUpdates.tryEmit(MessageUpdate.Edited(chatId, messageId, plain, entities))
    }

    override suspend fun setMessagePinned(chatId: Long, messageId: Long, pinned: Boolean) {
        delay(100)
        val bucket = chatMessages[chatId] ?: return
        val index = bucket.indexOfFirst { it.id == messageId }
        if (index == -1) return
        bucket[index] = bucket[index].copy(isPinned = pinned)
        _messageUpdates.tryEmit(MessageUpdate.PinChanged(chatId, messageId, pinned))
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

    override suspend fun sendPoll(chatId: Long, draft: PollDraft) {
        delay(160)
        val options = draft.filledOptions
        appendOutgoing(
            chatId = chatId,
            text = draft.question.trim(),
            type = MessageContentType.Poll,
            poll = PollContent(
                id = messageId.get() + 1,
                question = draft.question.trim(),
                options = options.map { PollOption(it) },
                isAnonymous = draft.isAnonymous,
                allowsMultiple = draft.allowsMultiple,
                allowsRevoting = !draft.isQuiz,
                isQuiz = draft.isQuiz,
                correctOptions = setOfNotNull(draft.correctIndex),
                explanation = draft.explanation.trim()
            )
        )
    }

    override suspend fun scheduledMessages(chatId: Long): List<ChatMessage> {
        delay(80)
        return scheduled[chatId].orEmpty().sortedBy { it.scheduledAt }
    }

    override suspend fun sendScheduledNow(chatId: Long, messageId: Long) {
        delay(80)
        val waiting = scheduled[chatId]?.firstOrNull { it.id == messageId } ?: return
        scheduled[chatId]?.remove(waiting)
        setHasScheduled(chatId)
        appendOutgoing(chatId = chatId, text = waiting.text, type = MessageContentType.Text)
    }

    private fun setHasScheduled(chatId: Long) {
        val has = scheduled[chatId].orEmpty().isNotEmpty()
        _chats.update { list -> list.map { if (it.id == chatId) it.copy(hasScheduledMessages = has) else it } }
    }

    /**
     * Telegram's own default set, in its order. Demo mode has no chat
     * restrictions to honour, so every chat offers all of them.
     */
    override suspend fun availableReactions(chatId: Long): List<String> = DEMO_REACTIONS

    override suspend fun votePoll(chatId: Long, messageId: Long, optionIds: List<Int>) {
        delay(150)
        val bucket = chatMessages[chatId] ?: return
        val index = bucket.indexOfFirst { it.id == messageId }
        val poll = bucket.getOrNull(index)?.poll ?: return
        val voted = poll.withVote(optionIds.toSet())
        bucket[index] = bucket[index].copy(poll = voted)
        _messageUpdates.tryEmit(MessageUpdate.PollChanged(chatId, messageId, voted))
    }

    override suspend fun pressButton(chatId: Long, messageId: Long, data: String): CallbackAnswer? {
        delay(300)
        return CallbackAnswer(text = DEMO_CHANGELOG_ANSWER)
    }

    /** The bot's keyboard is up from the start; nothing else has one. */
    private val _replyKeyboards = MutableStateFlow(
        mapOf(
            BOT_CHAT_ID to ReplyKeyboard(
                rows = listOf(
                    listOf(ReplyKey("Status"), ReplyKey("Latest build")),
                    listOf(ReplyKey("Help"))
                ),
                placeholder = "Ask the bot"
            )
        )
    )
    override val replyKeyboards: StateFlow<Map<Long, ReplyKeyboard>> = _replyKeyboards.asStateFlow()

    /**
     * Demo mode holds no remote files, so there is never anything to fetch.
     *
     * A recording made here is already a path on this device and arrives on
     * the message itself — which is why playing back your own voice message
     * works offline, and why nothing else does.
     */
    // ── the download manager ─────────────────────────────────────────────

    /**
     * The demo's download list, seeded with what a phone that has used the
     * app a while would have: a file finished, and one paused half-way.
     */
    private val demoDownloads = MutableStateFlow(
        run {
            val now = System.currentTimeMillis() / 1000
            mapOf(
                DEMO_NOTES_FILE_ID to DownloadEntry(
                    fileId = DEMO_NOTES_FILE_ID, chatId = 3, messageId = 1903, name = "release-notes.txt",
                    sizeBytes = 1_024, downloadedBytes = 1_024, mimeType = "text/plain", chatTitle = "Design Circle",
                    addedAt = now - 3_600, completedAt = now - 3_590
                ),
                DEMO_GUIDE_FILE_ID to DownloadEntry(
                    fileId = DEMO_GUIDE_FILE_ID, chatId = 3, messageId = 1901, name = "Expressive-guidelines.pdf",
                    sizeBytes = DEMO_GUIDE_BYTES, downloadedBytes = DEMO_GUIDE_BYTES * 2 / 5,
                    mimeType = "application/pdf", chatTitle = "Design Circle", addedAt = now - 600, isPaused = true
                )
            )
        }
    )

    /** Downloads running now, by file id, so a resumed one and its opener share one run. */
    private val demoRuns = java.util.concurrent.ConcurrentHashMap<Int, Deferred<DownloadOutcome>>()

    private fun demoRun(fileId: Int): Deferred<DownloadOutcome> = demoRuns.getOrPut(fileId) {
        scope.async {
            try {
                val start = demoDownloads.value[fileId] ?: return@async DownloadOutcome.Failed
                val total = start.sizeBytes
                var done = start.downloadedBytes
                while (done < total) {
                    delay(DEMO_DOWNLOAD_STEP_MS)
                    val now = demoDownloads.value[fileId]
                    if (now == null || now.isPaused) {
                        _fileTransfers.update { it - fileId }
                        return@async DownloadOutcome.Stopped
                    }
                    done = (done + total / DEMO_DOWNLOAD_STEPS).coerceAtMost(total)
                    demoDownloads.update { it + (fileId to now.copy(downloadedBytes = done)) }
                    _fileTransfers.update { it + (fileId to FileTransfer(fileId, done, total)) }
                }
                _fileTransfers.update { it - fileId }
                val path = withContext(Dispatchers.IO) { demoDownloadedFile(start.name).absolutePath }
                demoDownloads.update { list ->
                    list[fileId]?.let { list + (fileId to it.copy(completedAt = System.currentTimeMillis() / 1000)) } ?: list
                }
                DownloadOutcome.Done(path)
            } finally {
                demoRuns.remove(fileId)
            }
        }
    }

    override suspend fun downloadToList(chatId: Long, messageId: Long, fileId: Int): DownloadOutcome {
        val message = chatMessages[chatId].orEmpty().firstOrNull { it.id == messageId }
        val now = System.currentTimeMillis() / 1000
        val known = demoDownloads.value[fileId]
        // Files the demo makes on the spot are there at once, and listed.
        if (fileId == DEMO_NOTES_FILE_ID || fileId == DEMO_AUDIO_FILE_ID) {
            val path = downloadFile(fileId) ?: return DownloadOutcome.Failed
            val size = java.io.File(path).length()
            demoDownloads.update {
                it + (fileId to (known ?: DownloadEntry(
                    fileId = fileId, chatId = chatId, messageId = messageId,
                    name = message?.fileName ?: message?.audio?.displayTitle ?: java.io.File(path).name,
                    sizeBytes = size, downloadedBytes = size, mimeType = message?.mimeType,
                    chatTitle = chatTitle(chatId), addedAt = now
                )).copy(completedAt = now))
            }
            return DownloadOutcome.Done(path)
        }
        val bytes = when (fileId) {
            DEMO_GUIDE_FILE_ID -> DEMO_GUIDE_BYTES
            DEMO_KIT_FILE_ID -> DEMO_KIT_BYTES
            else -> return downloadFile(fileId)?.let { DownloadOutcome.Done(it) } ?: DownloadOutcome.Failed
        }
        if (known?.completedAt != null) {
            return DownloadOutcome.Done(withContext(Dispatchers.IO) { demoDownloadedFile(known.name).absolutePath })
        }
        demoDownloads.update {
            it + (fileId to (known?.copy(isPaused = false) ?: DownloadEntry(
                fileId = fileId, chatId = chatId, messageId = messageId, name = message?.fileName ?: "File",
                sizeBytes = bytes, mimeType = message?.mimeType, chatTitle = chatTitle(chatId), addedAt = now
            )))
        }
        return demoRun(fileId).await()
    }

    private fun chatTitle(chatId: Long): String = _chats.value.firstOrNull { it.id == chatId }?.title.orEmpty()

    override suspend fun fileDownloads(): List<DownloadEntry> = withContext(Dispatchers.IO) {
        demoDownloads.value.values.map { entry ->
            if (entry.completedAt == null) {
                entry
            } else {
                entry.copy(
                    path = when (entry.fileId) {
                        DEMO_NOTES_FILE_ID -> demoNotesFile().absolutePath
                        DEMO_AUDIO_FILE_ID -> demoAudioFile().absolutePath
                        else -> demoDownloadedFile(entry.name).absolutePath
                    }
                )
            }
        }
    }

    override suspend fun setDownloadPaused(fileId: Int, paused: Boolean) {
        demoDownloads.update { list -> list[fileId]?.let { list + (fileId to it.copy(isPaused = paused)) } ?: list }
        if (!paused) demoRun(fileId)
    }

    override suspend fun setAllDownloadsPaused(paused: Boolean) {
        val active = demoDownloads.value.values.filter { it.completedAt == null }.map { it.fileId }
        active.forEach { setDownloadPaused(it, paused) }
    }

    override suspend fun removeDownload(fileId: Int, deleteFile: Boolean) {
        demoDownloads.update { it - fileId }
        _fileTransfers.update { it - fileId }
    }

    override suspend fun clearFinishedDownloads(deleteFiles: Boolean) {
        demoDownloads.update { list -> list.filterValues { it.completedAt == null } }
    }

    /**
     * Pretends to fetch a file, slowly enough to be watched.
     *
     * Only the seeded video has an id here, and it answers with the clip the
     * app ships. The point is not the bytes — they are already on the device
     * — but the bar: a progress indicator nobody can make appear is a
     * progress indicator nobody can check, and the demo build is the only
     * one CI can make.
     */
    override suspend fun downloadFile(fileId: Int): String? {
        if (fileId == DEMO_AUDIO_FILE_ID) return withContext(Dispatchers.IO) { demoAudioFile().absolutePath }
        if (fileId == DEMO_NOTES_FILE_ID) return withContext(Dispatchers.IO) { demoNotesFile().absolutePath }
        if (fileId != DEMO_VIDEO_FILE_ID) return null
        val total = DEMO_VIDEO_BYTES
        var done = 0L
        while (done < total) {
            done = (done + total / DEMO_TRANSFER_STEPS).coerceAtMost(total)
            _fileTransfers.update {
                it + (fileId to FileTransfer(fileId, done, total))
            }
            delay(DEMO_TRANSFER_STEP_MS)
        }
        _fileTransfers.update { it - fileId }
        return DEMO_VIDEO
    }

    /**
     * The other direction, for a photo just picked.
     *
     * Runs alongside the message rather than before it, which is what a real
     * send does: the bubble appears immediately with a bar over it, and the
     * bar goes when the bytes are through.
     */
    private fun fakeUpload(fileId: Int, total: Long) {
        scope.launch {
            var done = 0L
            while (done < total) {
                done = (done + total / DEMO_TRANSFER_STEPS).coerceAtMost(total)
                _fileTransfers.update {
                    it + (fileId to FileTransfer(fileId, done, total, isUpload = true))
                }
                delay(DEMO_TRANSFER_STEP_MS)
            }
            _fileTransfers.update { it - fileId }
        }
    }

    /**
     * One story per circle, made of its emoji and caption: the demo has no
     * pictures to show, and the viewer draws a story without media from
     * those.
     */
    override suspend fun storyFrames(storyId: Long): List<StoryFrame> {
        if (storyId == MY_STORIES_ID) {
            return postedStories.mapIndexed { index, posted ->
                StoryFrame(
                    id = index + 1,
                    caption = posted.caption,
                    date = posted.date,
                    localPath = posted.uri,
                    isVideo = posted.isVideo,
                    durationSeconds = if (posted.isVideo) 5.0 else 0.0,
                    isSeen = true
                )
            }
        }
        val story = _stories.value.firstOrNull { it.id == storyId } ?: return emptyList()
        return listOf(StoryFrame(id = 1, caption = story.caption, isSeen = !story.hasUnseen))
    }

    /** A story posted in the demo, as the viewer will show it. */
    private data class PostedStory(val uri: String, val isVideo: Boolean, val caption: String, val date: Long)

    /** Stories posted in the demo, oldest first: "My story" in the rail. */
    private val postedStories = mutableListOf<PostedStory>()

    override suspend fun postStory(uri: String, isVideo: Boolean, caption: String, audience: StoryAudience) {
        delay(600)
        postedStories += PostedStory(uri, isVideo, caption, System.currentTimeMillis() / 1000)
        // Beside the add entry, as the official client puts one's own.
        _stories.update { list ->
            if (list.any { it.isMine }) {
                list
            } else {
                val mine = StoryItem(
                    MY_STORIES_ID, "My story", hasUnseen = false, avatarColor = 1,
                    previewEmoji = "✨", caption = caption, isMine = true
                )
                list.take(1) + mine + list.drop(1)
            }
        }
    }

    override suspend fun markStorySeen(storyId: Long, frameId: Int) {
        _stories.update { list ->
            list.map { if (it.id == storyId) it.copy(hasUnseen = false) else it }
        }
    }

    // ── proxies ──────────────────────────────────────────────────────────
    //
    // Kept in memory, which is all the demo keeps. Every proxy "answers" in a
    // time made from its address, so the screen shows a ping without the
    // demo reaching anything.

    private val demoProxies = MutableStateFlow<List<ProxyServer>>(emptyList())
    private val proxyIds = AtomicLong(0)

    override suspend fun proxies(): List<ProxyServer> = demoProxies.value

    override suspend fun addProxy(proxy: ProxyServer, enable: Boolean): Int {
        delay(150)
        val id = proxyIds.incrementAndGet().toInt()
        demoProxies.update { list ->
            list.map { if (enable) it.copy(isEnabled = false) else it } +
                proxy.copy(id = id, isEnabled = enable)
        }
        return id
    }

    override suspend fun enableProxy(id: Int) {
        demoProxies.update { list -> list.map { it.copy(isEnabled = it.id == id) } }
    }

    override suspend fun disableProxy() {
        demoProxies.update { list -> list.map { it.copy(isEnabled = false) } }
    }

    override suspend fun removeProxy(id: Int) {
        demoProxies.update { list -> list.filterNot { it.id == id } }
    }

    override suspend fun pingProxy(proxy: ProxyServer): Long? {
        delay(300)
        return 40L + (proxy.server.hashCode() and 0xff)
    }

    // ── stickers ─────────────────────────────────────────────────────────
    //
    // Emoji rather than pictures: the demo has no sticker files to ship, and
    // Telegram's are not this repository's to copy. A sticker with no file is
    // drawn as its emoji, large, which exercises the picker and the bubble
    // the same way.

    private val demoStickerSets = listOf(
        StickerSetPreview(1, "Faces", StickerContent(emoji = "😀")),
        StickerSetPreview(2, "Animals", StickerContent(emoji = "🐱")),
        StickerSetPreview(3, "Things", StickerContent(emoji = "🚀"))
    )
    private val demoStickers = mapOf(
        1L to "😀 😂 🥹 😍 😎 🤔 😴 🤯 🥳 😭 😡 🤗",
        2L to "🐱 🐶 🦊 🐼 🐨 🐸 🐧 🦉 🐙 🦋 🐢 🦄",
        3L to "🚀 ☕ 🎧 📷 🎨 💡 🔥 🌈 🎉 ⚡ 🌙 ⭐"
    )
    private val sentStickers = MutableStateFlow<List<StickerContent>>(emptyList())

    override suspend fun stickerSets(): List<StickerSetPreview> = demoStickerSets

    override suspend fun stickerSet(setId: Long): List<StickerContent> =
        if (setId == DEMO_EMOJI_SET) {
            // The demo's one custom-emoji set: the unicorn its messages
            // already use, and a few more on ids of their own.
            DEMO_EMOJI_SET_EMOJI.split(' ').mapIndexed { index, emoji ->
                val id = if (index == 0) DEMO_CUSTOM_EMOJI else DEMO_CUSTOM_EMOJI + index
                StickerContent(id = id, emoji = emoji, customEmojiId = id)
            }
        } else {
            demoStickers[setId].orEmpty().split(' ').mapIndexed { index, emoji ->
                StickerContent(id = setId * 100 + index, emoji = emoji)
            }
        }

    override suspend fun customEmojiSets(): List<StickerSetPreview> = listOf(
        StickerSetPreview(
            id = DEMO_EMOJI_SET,
            title = "TelegramYou",
            cover = StickerContent(id = DEMO_CUSTOM_EMOJI, emoji = "🦄", customEmojiId = DEMO_CUSTOM_EMOJI)
        )
    )

    override suspend fun sendTextWithEmoji(
        chatId: Long,
        text: String,
        picked: List<PickedEmoji>,
        replyToId: Long?,
        sendAt: Long?
    ) {
        if (sendAt != null) return sendText(chatId, text, replyToId, sendAt)
        delay(120)
        val (plain, entities) = parseMarkdown(text)
        val custom = placePickedEmoji(plain, picked).map { placed ->
            TextEntity(placed.offset, placed.length, EntityType.CustomEmoji(placed.customEmojiId))
        }
        appendOutgoing(
            chatId = chatId,
            text = plain,
            type = MessageContentType.Text,
            replyToId = replyToId,
            entities = (entities + custom).sortedBy { it.offset }
        )
    }

    override suspend fun recentStickers(): List<StickerContent> = sentStickers.value

    /**
     * The demo's one clip, offered as a few GIFs — enough to fill a row of
     * the picker and to send one. Searching finds the same clip, whatever
     * was typed: there is no bot offline to ask.
     */
    private fun demoGifs(prefix: String): List<GifItem> = List(4) { index ->
        GifItem(
            id = "$prefix-$index",
            video = VideoContent(
                durationSeconds = 8,
                aspect = 360f / 202f,
                thumbPath = DEMO_VIDEO_POSTER,
                path = DEMO_VIDEO
            ),
            width = 360,
            height = 202
        )
    }

    /**
     * One custom emoji, the demo's stand-in for a Premium reaction: a
     * unicorn with no file behind it, so it draws as its emoji wherever a
     * custom emoji's sticker would go.
     */
    override suspend fun customEmoji(ids: List<Long>): Map<Long, StickerContent> {
        // The set's emoji by their ids, the unicorn first; see stickerSet.
        val set = DEMO_EMOJI_SET_EMOJI.split(' ')
        return ids.mapNotNull { id ->
            set.getOrNull((id - DEMO_CUSTOM_EMOJI).toInt().takeIf { id >= DEMO_CUSTOM_EMOJI } ?: -1)
                ?.let { emoji -> id to StickerContent(id = id, emoji = emoji, customEmojiId = id) }
        }.toMap()
    }

    override suspend fun messageReactions(chatId: Long, messageId: Long): List<ReactionOption> =
        availableReactions(chatId).map { ReactionOption(it) } +
            ReactionOption(
                customReactionKey(DEMO_CUSTOM_EMOJI),
                StickerContent(id = DEMO_CUSTOM_EMOJI, emoji = "🦄"),
                needsPremium = true
            )

    /** GIFs kept from messages, newest first, ahead of the demo's own. */
    private val keptGifs = mutableListOf<GifItem>()

    override suspend fun sendContact(chatId: Long, contact: ContactContent, replyToId: Long?) {
        delay(150)
        appendOutgoing(
            chatId = chatId,
            text = "👤 ${contact.displayName}",
            type = MessageContentType.Contact,
            replyToId = replyToId,
            contact = contact
        )
    }

    override suspend fun sendLocation(
        chatId: Long,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Double,
        replyToId: Long?
    ) {
        delay(150)
        appendOutgoing(
            chatId = chatId,
            text = "📍 Location",
            type = MessageContentType.Location,
            replyToId = replyToId,
            location = LocationContent(latitude, longitude)
        )
    }

    /** The demo's groups hand out links; a new one each time it is renewed. */
    private var demoLinkSerial = 0

    override suspend fun renewInviteLink(chatId: Long): String? {
        delay(150)
        demoLinkSerial++
        return "https://t.me/+demo${chatId}x$demoLinkSerial"
    }

    override suspend fun savedGifs(): List<GifItem> {
        delay(120)
        return keptGifs + demoGifs("saved")
    }

    override suspend fun saveGif(video: VideoContent) {
        delay(100)
        keptGifs.add(0, GifItem(id = "kept-${keptGifs.size}", video = video, width = 360, height = 202))
    }

    override suspend fun searchGifs(query: String): List<GifItem> {
        delay(250)
        return if (query.isBlank()) demoGifs("saved") else demoGifs("found")
    }

    override suspend fun sendGif(chatId: Long, gif: GifItem, replyToId: Long?) {
        delay(150)
        appendOutgoing(
            chatId = chatId,
            text = "GIF",
            type = MessageContentType.Animation,
            replyToId = replyToId,
            video = gif.video
        )
    }

    override suspend fun sendSticker(chatId: Long, sticker: StickerContent, replyToId: Long?) {
        delay(150)
        sentStickers.update { list -> (listOf(sticker) + list.filterNot { it.id == sticker.id }).take(20) }
        appendOutgoing(
            chatId = chatId,
            text = "${sticker.emoji} Sticker",
            type = MessageContentType.Sticker,
            replyToId = replyToId,
            sticker = sticker
        )
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

    override suspend fun setProfilePhoto(uri: String) {
        delay(300)
        // The picked image itself: Coil reads a content Uri as readily as a
        // file, and the demo has no server to upload it to.
        updateMe { it.copy(photoPath = uri) }
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
        photoPath: String? = null,
        photoFileId: Int? = null,
        sticker: StickerContent? = null,
        poll: PollContent? = null,
        entities: List<TextEntity> = emptyList(),
        video: VideoContent? = null,
        contact: ContactContent? = null,
        location: LocationContent? = null
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
            photoPath = photoPath,
            photoFileId = photoFileId,
            sticker = sticker,
            poll = poll,
            entities = entities,
            video = video,
            contact = contact,
            location = location,
            topicId = openTopics[chatId] ?: 0
        )
        val bucket = chatMessages.getOrPut(chatId) { mutableListOf() }
        bucket.add(msg)
        // The conversation on screen draws our own message from this, the
        // same way it draws one arriving — it no longer fetches itself again
        // after a send.
        _messageUpdates.tryEmit(MessageUpdate.Added(msg))
        _chats.update { list ->
            list.map { chat ->
                if (chat.id == chatId) {
                    chat.copy(
                        lastMessage = when (type) {
                            MessageContentType.Document -> "📎 ${fileName ?: "File"}"
                            MessageContentType.Photo -> "🖼 Photo"
                            MessageContentType.Poll -> "📊 $text"
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
        ChatPreview(
            1, "Material Design", "Expressive motion is live ✨", "12:41",
            unreadCount = 3, isPinned = true, isOnline = true,
            folderIds = setOf(FOLDER_WORK)
        ),
        ChatPreview(
            2, "Lina Park", "typing… wait, almost", "11:02", unreadCount = 1,
            // Always typing, so the morph is on screen from the first look.
            isOnline = true, isTyping = true, avatarColor = 22,
            folderIds = setOf(FOLDER_PEOPLE)
        ),
        ChatPreview(
            3, "Design Circle", "New Figma dump in #files", "Yesterday",
            isGroup = true, unreadCount = 18, avatarColor = 33,
            folderIds = setOf(FOLDER_WORK)
        ),
        ChatPreview(
            4, "TelegramYou News", "M3 Expressive build notes", "Mon",
            isChannel = true, isMuted = true, avatarColor = 44,
            folderIds = setOf(FOLDER_NEWS)
        ),
        ChatPreview(
            5, "Artem", "Send me the apk?", "Sun", avatarColor = 55,
            folderIds = setOf(FOLDER_PEOPLE)
        ),
        ChatPreview(6, "Saved Messages", "Color tokens & springs", "Sat", isPinned = true, avatarColor = 66, isSavedMessages = true),
        ChatPreview(
            7, "Kotlin Night", "Compose BOM tips", "Fri", isGroup = true,
            avatarColor = 77, folderIds = setOf(FOLDER_WORK, FOLDER_NEWS)
        ),
        ChatPreview(
            8, "Mom", "Call me when free 💚", "Thu", unreadCount = 2,
            avatarColor = 88, folderIds = setOf(FOLDER_PEOPLE)
        ),
        // A bot, so its buttons — under a message and under the composer —
        // can be pressed without an account.
        ChatPreview(
            BOT_CHAT_ID, "Build Bot", "Build 1.0.366 is ready", "Thu",
            isBot = true, avatarColor = 121
        ),
        // A music channel, so the player has a queue offline: the case the
        // owner's brother described, a channel of tracks to listen through.
        ChatPreview(
            MUSIC_CHANNEL_ID, DEMO_MUSIC_CHANNEL, "🎵 Morning Light", "Wed",
            isChannel = true, isMuted = true, avatarColor = 141
        ),
        // A forum, so topics open offline: a group split into threads.
        ChatPreview(
            FORUM_CHAT_ID, DEMO_FORUM, "Releases: 1.6 is out", "Thu",
            isGroup = true, isForum = true, unreadCount = 3, avatarColor = 131
        ),
        // Two in the archive from the start, one of them unread, so the entry
        // row has both halves of its summary to show offline — and so the
        // main list can be seen not to include them.
        ChatPreview(
            9, "Delivery updates", "Your parcel is on its way", "Wed",
            isArchived = true, avatarColor = 99
        ),
        ChatPreview(
            10, "Old project", "Archived last spring", "Mar",
            isArchived = true, unreadCount = 4, avatarColor = 111
        )
    )

    /**
     * A link for the groups, nothing for a conversation with one person.
     *
     * The shape Telegram's own private links have, so the row it fills looks
     * like what it will look like against a real account.
     */
    override suspend fun chatInviteLink(chatId: Long): String? {
        val chat = _chats.value.firstOrNull { it.id == chatId } ?: return null
        if (!chat.isGroup && !chat.isChannel) return null
        return "https://t.me/+TelegramYouDemo$chatId"
    }

    override suspend fun createGroup(title: String, memberIds: List<Long>): Long {
        delay(300)
        return addDemoChat(title.trim(), isGroup = true, firstLine = "You created the group")
    }

    override suspend fun createChannel(title: String, description: String): Long {
        delay(300)
        return addDemoChat(
            title.trim(),
            isChannel = true,
            firstLine = description.trim().ifBlank { "You created the channel" }
        )
    }

    /**
     * Two kinds of link answer here: a group's own link, which leads back to a
     * chat this account is already in, and [DEMO_JOIN_LINK], which leads to one
     * it is not — so both buttons on the join screen, Open and Join, can be
     * seen offline. Anything else is a link to nowhere, which is the third
     * thing that screen has to handle.
     */
    override suspend fun checkInviteLink(link: String): InviteLinkPreview? {
        delay(250)
        if (link == DEMO_JOIN_LINK) {
            val joined = _chats.value.firstOrNull { it.title == DEMO_JOIN_TITLE }
            return InviteLinkPreview(
                link = link,
                title = DEMO_JOIN_TITLE,
                memberCount = 1284,
                joinedChatId = joined?.id
            )
        }
        val own = link.removePrefix("https://t.me/+TelegramYouDemo").toLongOrNull()
        val chat = own?.let { id -> _chats.value.firstOrNull { it.id == id } } ?: return null
        return InviteLinkPreview(
            link = link,
            title = chat.title,
            memberCount = 42,
            isChannel = chat.isChannel,
            joinedChatId = chat.id,
            avatarColor = chat.avatarColor
        )
    }

    override suspend fun joinByInviteLink(link: String): Long {
        delay(300)
        _chats.value.firstOrNull { it.title == DEMO_JOIN_TITLE }?.let { return it.id }
        return addDemoChat(DEMO_JOIN_TITLE, isGroup = true, firstLine = "You joined the group")
    }

    /** A chat that did not exist a moment ago, at the top of the list. */
    private fun addDemoChat(
        title: String,
        isGroup: Boolean = false,
        isChannel: Boolean = false,
        firstLine: String
    ): Long {
        val id = (_chats.value.maxOfOrNull { it.id } ?: 0L) + 1
        val now = System.currentTimeMillis() / 1000
        chatMessages[id] = mutableListOf(
            demoMessage(messageId.incrementAndGet(), id, firstLine, true, now, isRead = true)
        )
        _chats.update { list ->
            listOf(
                ChatPreview(
                    id = id,
                    title = title,
                    lastMessage = firstLine,
                    timestampLabel = "now",
                    isGroup = isGroup,
                    isChannel = isChannel,
                    avatarColor = title.hashCode().toLong()
                )
            ) + list
        }
        return id
    }

    override suspend fun deleteAllMyMessages(chatId: Long): Int {
        delay(300)
        val bucket = chatMessages[chatId] ?: return 0
        val mine = bucket.filter { it.isOutgoing }.map { it.id }.toSet()
        bucket.removeAll { it.id in mine }
        if (mine.isNotEmpty()) _messageUpdates.tryEmit(MessageUpdate.Deleted(chatId, mine))
        return mine.size
    }

    override suspend fun leaveChat(chatId: Long) {
        _chats.update { list -> list.filterNot { it.id == chatId } }
    }

    /**
     * Each folder's rules, made on first asking from which chats the seed
     * put in it — one by one, as a folder of hand-picked chats. Saved rules
     * replace them, and membership is then worked out by the same rule the
     * server applies (FolderRules.contains).
     */
    private val demoFolderRules = mutableMapOf<Int, FolderRules>()

    private fun rulesOf(folderId: Int): FolderRules = demoFolderRules.getOrPut(folderId) {
        val folder = _folders.value.first { it.id == folderId }
        FolderRules(
            name = folder.title,
            iconName = folder.iconName,
            includedChatIds = _chats.value.filter { folderId in it.folderIds }.map { it.id }
        )
    }

    override suspend fun folderRules(folderId: Int): FolderRules {
        delay(120)
        return rulesOf(folderId)
    }

    override suspend fun saveFolder(folderId: Int?, rules: FolderRules): Int {
        delay(200)
        val id = folderId ?: ((_folders.value.maxOfOrNull { it.id } ?: 0) + 1)
        val saved = rules.copy(name = rules.name.trim())
        // Every other folder's rules pinned down first, while the chats still
        // say what is in them; working membership out again below would
        // otherwise empty the ones never opened.
        _folders.value.forEach { rulesOf(it.id) }
        demoFolderRules[id] = saved
        _folders.update { list ->
            val folder = ChatFolder(id, saved.name, iconName = saved.iconName)
            if (list.any { it.id == id }) list.map { if (it.id == id) folder else it } else list + folder
        }
        _chats.update { chats ->
            chats.map { chat ->
                val inIt = saved.contains(chat, isContact = !chat.isGroup && !chat.isChannel && !chat.isBot)
                chat.copy(folderIds = if (inIt) chat.folderIds + id else chat.folderIds - id)
            }
        }
        return id
    }

    override suspend fun deleteFolder(folderId: Int) {
        delay(150)
        demoFolderRules.remove(folderId)
        _folders.update { list -> list.filterNot { it.id == folderId } }
        _chats.update { chats -> chats.map { it.copy(folderIds = it.folderIds - folderId) } }
    }

    override suspend fun reorderFolders(folderIds: List<Int>) {
        delay(100)
        _folders.update { list -> folderIds.mapNotNull { id -> list.firstOrNull { it.id == id } } }
    }

    private fun seedFolders(): List<ChatFolder> = listOf(
        // Deliberately overlapping: Kotlin Night is in two of them, which is
        // what folders are — filters over one list, not boxes a chat is put
        // into.
        ChatFolder(FOLDER_WORK, "Work", iconName = "Work"),
        ChatFolder(FOLDER_PEOPLE, "People", iconName = "Private"),
        ChatFolder(FOLDER_NEWS, "News", iconName = "Channel")
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

        chatMessages[FORUM_CHAT_ID] = forumMessages(today, yesterday)
        chatMessages[MUSIC_CHANNEL_ID] = musicChannel(now, day)
        chatMessages[1] = mutableListOf(
            demoMessage(1, 1, "Welcome to TelegramYou", false, today, "Material Design"),
            demoMessage(2, 1, "Material 3 Expressive: MaterialExpressiveTheme, the stock motion scheme, a real LoadingIndicator. On the alpha, since no stable release exposes any of it.", false, today + 60, "Material Design", reactions = listOf(MessageReaction("🔥", count = 12), MessageReaction("👍", count = 4, isChosen = true), MessageReaction(customReactionKey(DEMO_CUSTOM_EMOJI), count = 2))),
            demoMessage(3, 1, "Attach files from the composer. Stories sit on top of the chat list.", false, today + 120, "Material Design"),
            // One message with a card, so the link preview is visible offline.
            // Its text still holds the URL: the card is an addition to the
            // message, not a replacement for it, and that is what a person
            // copying the link expects to find.
            demoMessage(
                5, 1,
                "The whole argument for this update: m3.material.io/blog/building-with-m3-expressive",
                false, today + 180, "Material Design",
                linkPreview = LinkPreview(
                    url = "https://m3.material.io/blog/building-with-m3-expressive",
                    siteName = "Material Design",
                    title = "Building with M3 Expressive",
                    description = "Motion physics, emphasized type, a shape " +
                        "library and more vivid colour — what the update " +
                        "actually contains."
                )
            ),
            // With a custom emoji at its end — the demo's unicorn, drawn in
            // the line as its sticker where one exists (see customEmoji).
            demoMessage(4, 1, "Looks sharp. Let’s keep the teal identity. 🦄", true, today + 660, isRead = true, reactions = listOf(MessageReaction("❤️", count = 1)))
                .let { it.copy(entities = listOf(TextEntity(it.text.length - 2, 2, EntityType.CustomEmoji(DEMO_CUSTOM_EMOJI)))) },
        )
        // Photos with no file behind them, which is not a shortcut: this is
        // exactly what a real chat looks like between a message arriving and
        // its bytes doing so, and it is the state the grid has to draw well.
        // Without these the shared-media screen would be empty offline and
        // therefore unverifiable on the emulator.
        chatMessages[1]?.addAll(
            listOf(
                demoMessage(
                    6, 1, "Palette exploration", false, today + 200, "Material Design",
                    contentType = MessageContentType.Photo
                ),
                demoMessage(
                    7, 1, "Shape library sheet", false, today + 240, "Material Design",
                    contentType = MessageContentType.Photo
                ),
                demoMessage(
                    8, 1, "Motion spec", true, today + 280,
                    isRead = true, contentType = MessageContentType.Photo
                ),
                demoMessage(
                    9, 1, "Composer study", false, today + 320, "Material Design",
                    contentType = MessageContentType.Photo
                ),
                // A video with neither file behind it, for the same reason
                // the photos above have none: that is what a chat looks like
                // between a message arriving and its bytes doing so, and it
                // is the state the bubble has to draw well. The duration and
                // the shape come with the message on a real account too, so
                // the poster's space is reserved either way.
                demoMessage(
                    15, 1, "Expressive motion, slowed down", false, today + 360,
                    "Material Design",
                    contentType = MessageContentType.Video,
                    video = VideoContent(
                        durationSeconds = 4,
                        aspect = 360f / 202f,
                        thumbPath = DEMO_VIDEO_POSTER,
                        path = DEMO_VIDEO
                    )
                ),
                // The same clip with no files behind it, which is the other
                // half of what this has to draw: a video message between
                // arriving and its bytes doing so. Tapping it downloads
                // nothing in demo mode, so the player opens on its spinner —
                // which is exactly the state a slow connection produces.
                demoMessage(
                    16, 1, "Composer, one take", true, today + 400,
                    isRead = true,
                    contentType = MessageContentType.Video,
                    video = VideoContent(
                        durationSeconds = 8,
                        aspect = 9f / 16f,
                        fileId = DEMO_VIDEO_FILE_ID
                    )
                )
            )
        )

        chatMessages[2] = mutableListOf(
            // One emoji and nothing else, drawn large and without a bubble;
            // a live account plays Telegram's animation of it instead.
            demoMessage(900, 2, "🎉", false, yesterday - 900, "Lina Park"),
            // A contact card and a place, before anything else in the chat:
            // how both draw, offline — the place opens in whatever maps app
            // the phone has.
            demoMessage(901, 2, "👤 Sasha Kim", false, yesterday - 600, "Lina Park",
                contentType = MessageContentType.Contact)
                .copy(contact = ContactContent("Sasha", "Kim", "+15550100", userId = 0)),
            demoMessage(902, 2, "📍 Coffee after the meetup?", false, yesterday - 300, "Lina Park",
                contentType = MessageContentType.Location)
                .copy(location = LocationContent(37.7793, -122.4193, title = "Blue Bottle Coffee", address = "66 Mint St, San Francisco")),
            demoMessage(10, 2, "Did you try the expressive loading indicator?", false, yesterday, "Lina Park"),
            demoMessage(11, 2, "Yes — and the split send button feels great.", true, yesterday + 180, isRead = true),
            // Two voice messages in a row, as people send them: tapping the
            // first plays both, the second after the first.
            demoMessage(903, 2, "0:12", false, yesterday + 200, "Lina Park", contentType = MessageContentType.Voice)
                .copy(voiceFileId = DEMO_AUDIO_FILE_ID, waveform = DEMO_WAVEFORM),
            demoMessage(904, 2, "0:12", false, yesterday + 215, "Lina Park", contentType = MessageContentType.Voice)
                .copy(voiceFileId = DEMO_AUDIO_FILE_ID, waveform = DEMO_WAVEFORM.reversed()),
            // A GIF and a round video message, the two kinds of video people
            // send most. The demo's one clip stands in for both; the circle
            // crops it square, as it would a real one.
            demoMessage(
                13, 2, "GIF", false, yesterday + 240, "Lina Park",
                contentType = MessageContentType.Animation,
                video = VideoContent(
                    durationSeconds = 8,
                    aspect = 360f / 202f,
                    thumbPath = DEMO_VIDEO_POSTER,
                    path = DEMO_VIDEO
                )
            ),
            demoMessage(
                14, 2, "Video message", false, today + 200, "Lina Park",
                contentType = MessageContentType.VideoNote,
                video = VideoContent(
                    durationSeconds = 8,
                    aspect = 1f,
                    thumbPath = DEMO_VIDEO_POSTER,
                    path = DEMO_VIDEO
                )
            ),
            demoMessage(12, 2, "Sending a voice note next 🎧", false, today + 300, "Lina Park")
        )
        // Artem's chat, with a video sticker — VP9 with its transparency
        // riding beside it, the way Telegram's are — so the player for them
        // has something to play offline and in the smoke test.
        chatMessages[5] = mutableListOf(
            demoMessage(
                289, 5, "Send me the apk?", false, today + 60, "Artem"
            ),
            demoMessage(
                290, 5, "😊", false, today + 100, "Artem",
                contentType = MessageContentType.Sticker
            ).copy(
                sticker = StickerContent(
                    id = 9_001,
                    emoji = "😊",
                    format = StickerFormat.Webm,
                    path = DEMO_VIDEO_STICKER
                )
            )
        )
        // A group that behaves like one: several people, because the header
        // draws a cluster of whoever is talking, and a "group" where one
        // person says everything shows a single avatar — which is to say it
        // shows nothing of what a group looks like. Demo mode exists to make
        // the interface visible without an account, and that has to include
        // the parts which only appear with more than one person in the room.
        chatMessages[3] = (designCircleArchive(now, day) + listOf(
            // Older than the rest, so the chat reads as it always did, and
            // enough of each kind for the shared media tabs to have rows.
            demoMessage(1901, 3, "Expressive-guidelines.pdf", false, now - 3 * day, "Noor", contentType = MessageContentType.Document, fileName = "Expressive-guidelines.pdf", fileSizeLabel = "2.1 MB").copy(documentFileId = DEMO_GUIDE_FILE_ID, mimeType = "application/pdf"),
            demoMessage(1902, 3, "The motion spec, if anyone wants it: m3.material.io/styles/motion", false, now - 3 * day + 120, "Noor"),
            demoMessage(1903, 3, "release-notes.txt", true, now - 3 * day + 240, isRead = true, contentType = MessageContentType.Document, fileName = "release-notes.txt", fileSizeLabel = "1 KB").copy(documentFileId = DEMO_NOTES_FILE_ID, mimeType = "text/plain"),
            demoMessage(20, 3, "Drop assets in the thread", false, now - 2 * day, "Maya"),
            demoMessage(21, 3, "brand-kit.zip", false, now - 2 * day + 30, "Maya", contentType = MessageContentType.Document, fileName = "brand-kit.zip", fileSizeLabel = "4.8 MB").copy(documentFileId = DEMO_KIT_FILE_ID, mimeType = "application/zip"),
            demoMessage(22, 3, "Got them. The tonal palette is the part I want to steal.", false, now - day - 4 * 60 * 60, "Ivan"),
            demoMessage(23, 3, "Shapes too — every avatar up there is a different one.", false, now - day - 3 * 60 * 60, "Noor", reactions = listOf(MessageReaction("🔥", count = 3))),
            demoMessage(24, 3, "That is the shape library doing its job.", true, now - day - 2 * 60 * 60, isRead = true),
            demoMessage(25, 3, "Figma dump is in #files now", false, today - 90 * 60, "Sasha"),
            demoMessage(26, 3, "Reviewing tonight 👀", false, today - 40 * 60, "Ivan")
        )).toMutableList()
        // A poll nobody here has answered yet, so voting is the first thing
        // the chat offers, and a quiz already answered beside it, so the
        // results and the right answer are visible without a tap.
        // Formatting, a forward and an album, so all three draw offline.
        val bom = "Compose BOM tips: pin one version, see developer.android.com, ask @lina"
        chatMessages[7] = mutableListOf(
            // With a picture behind them — the clip's poster, the one image
            // the demo carries — so they open, and the gallery has three to
            // swipe through.
            demoMessage(33, 7, "Photos from the meetup", false, yesterday - 3600, "Pavel",
                contentType = MessageContentType.Photo).copy(albumId = DEMO_ALBUM_ID, photoPath = DEMO_VIDEO_POSTER),
            demoMessage(34, 7, "", false, yesterday - 3600, "Pavel",
                contentType = MessageContentType.Photo).copy(albumId = DEMO_ALBUM_ID, photoPath = DEMO_VIDEO_POSTER),
            demoMessage(35, 7, "", false, yesterday - 3600, "Pavel",
                contentType = MessageContentType.Photo).copy(albumId = DEMO_ALBUM_ID, photoPath = DEMO_VIDEO_POSTER),
            demoMessage(36, 7, DEMO_FORWARDED_TEXT, false, yesterday - 1800, "Nadia")
                .copy(forwardedFrom = "Android Developers"),
            demoMessage(40, 7, bom, false, yesterday, "Pavel").copy(
                entities = listOf(
                    TextEntity(0, 12, EntityType.Bold),
                    TextEntity(bom.indexOf("pin one version"), "pin one version".length, EntityType.Italic),
                    TextEntity(bom.indexOf("developer.android.com"), "developer.android.com".length, EntityType.Url),
                    TextEntity(bom.indexOf("@lina"), 5, EntityType.Mention)
                )
            ),
            demoMessage(41, 7, DEMO_POLL_QUESTION, false, yesterday + 600, "Pavel").copy(
                contentType = MessageContentType.Poll,
                poll = PollContent(
                    id = 41,
                    question = DEMO_POLL_QUESTION,
                    options = listOf(
                        PollOption("LazyColumn", voterCount = 14, percentage = 47),
                        PollOption("ListItem", voterCount = 9, percentage = 30),
                        PollOption("SharedTransitionLayout", voterCount = 7, percentage = 23)
                    ),
                    totalVoters = 30,
                    isAnonymous = true
                )
            ),
            demoMessage(42, 7, "Which spring does MotionScheme.standard() use?", false, today - 30 * 60, "Nadia").copy(
                contentType = MessageContentType.Poll,
                poll = PollContent(
                    id = 42,
                    question = "Which spring does MotionScheme.standard() use?",
                    options = listOf(
                        PollOption("Bouncy", voterCount = 3, percentage = 25),
                        PollOption("No bounce, critically damped", voterCount = 8, percentage = 67, isChosen = true),
                        PollOption("A tween", voterCount = 1, percentage = 8)
                    ),
                    totalVoters = 12,
                    isAnonymous = false,
                    isQuiz = true,
                    correctOptions = setOf(1),
                    explanation = "Standard never overshoots; expressive does."
                )
            )
        )
        // Posts in the public channels, for post search and for reading one
        // found that way before joining it.
        chatMessages[PUBLIC_CHANNEL_ID] = mutableListOf(
            demoMessage(60, PUBLIC_CHANNEL_ID, "Why Expressive motion uses springs, not curves", false, yesterday, DEMO_PUBLIC_CHANNEL),
            demoMessage(61, PUBLIC_CHANNEL_ID, "Shape morphing in Material 3 Expressive, explained", false, today - 2 * 60 * 60, DEMO_PUBLIC_CHANNEL)
        )
        chatMessages[PUBLIC_CHANNEL_ID + 1] = mutableListOf(
            demoMessage(70, PUBLIC_CHANNEL_ID + 1, "Compose 1.12 is out: what changed for Material 3", false, today - 60 * 60, "Android Developers")
        )
        chatMessages[PUBLIC_CHANNEL_ID + 2] = mutableListOf(
            demoMessage(80, PUBLIC_CHANNEL_ID + 2, "Ask anything about Compose", false, yesterday, "Compose Community")
        )
        chatMessages[PUBLIC_CHANNEL_ID + 3] = mutableListOf(
            demoMessage(90, PUBLIC_CHANNEL_ID + 3, "Send a colour, get a palette", false, yesterday, "Material Colour Bot")
        )
        // Saved Messages keeps a song, so the music bubble has something to
        // show and play offline: a few seconds of chime the demo writes
        // itself on first play (demoAudioFile), rather than a file shipped.
        chatMessages[6] = mutableListOf(
            demoMessage(95, 6, "Color tokens & springs", true, yesterday, isRead = true),
            demoMessage(96, 6, "", true, today - 50 * 60, isRead = true).copy(
                contentType = MessageContentType.Audio,
                audio = AudioContent(
                    title = DEMO_AUDIO_TITLE,
                    performer = "Material Sound",
                    durationSeconds = DEMO_AUDIO_SECONDS,
                    fileName = "expressive-motion.wav",
                    fileId = DEMO_AUDIO_FILE_ID
                ),
                voiceFileId = DEMO_AUDIO_FILE_ID
            )
        )
        chatMessages[BOT_CHAT_ID] = mutableListOf(
            demoMessage(50, BOT_CHAT_ID, "Hi! I post every build of TelegramYou.", false, yesterday, "Build Bot"),
            demoMessage(51, BOT_CHAT_ID, "Build 1.0.366 is ready", false, today - 20 * 60, "Build Bot").copy(
                inlineKeyboard = listOf(
                    listOf(
                        InlineButton("Download", ButtonAction.OpenUrl("https://github.com/TemaSil/TelegramYou")),
                        InlineButton(DEMO_CHANGELOG_BUTTON, ButtonAction.Callback("Y2hhbmdlbG9n"))
                    ),
                    listOf(InlineButton("Copy version", ButtonAction.CopyText("1.0.366")))
                )
            )
        )
    }

    /**
     * Months of the group before the conversation above — far more than
     * opening a chat loads — so there is history to page back through, and a
     * search hit older than anything on screen. That hit is the first line,
     * which the UI test searches for and jumps to.
     */
    private fun designCircleArchive(now: Long, day: Long): List<ChatMessage> {
        val people = listOf("Maya", "Ivan", "Noor", "Sasha")
        val start = now - 90 * day
        val lines = listOf(
            "Pushed a new round of the chat list.",
            "The tonal steps read better on the darker panel.",
            "Can we try the rail on the tablet build?",
            "Motion feels calmer with the standard springs.",
            "Filed the spacing issues in the tracker.",
            "Avatars look sharp with the shape set."
        )
        return listOf(
            demoMessage(3000, 3, DEMO_ARCHIVE_FIRST_LINE, false, start, "Maya")
        ) + (1 until DEMO_ARCHIVE_SIZE).map { n ->
            demoMessage(
                3000L + n, 3, lines[n % lines.size], false,
                start + n * (80 * day / DEMO_ARCHIVE_SIZE), people[n % people.size]
            )
        }
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
        reactions: List<MessageReaction> = emptyList(),
        linkPreview: LinkPreview? = null,
        video: VideoContent? = null
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
        reactions = reactions,
        linkPreview = linkPreview,
        video = video
    )

    /** Telegram's default reaction set, in its order. */
private val DEMO_REACTIONS = listOf("👍", "👎", "❤️", "🔥", "🎉", "😁", "🤔", "😢")

/**
 * A real four-second clip, shipped with the app, and its poster frame.
 *
 * Generated rather than borrowed, and tiny — sixty-four kilobytes of test
 * pattern — because the demo build is the only one CI can make, and a video
 * feature nobody can play is a feature nobody can check. `android.resource://`
 * is a Uri both Coil and Media3 read, which is why the model's path is a
 * String rather than a File: it already carries `content://` Uris from the
 * pickers.
 */
/**
 * The id the seeded video's file answers to, and how big it pretends to be.
 *
 * A number, because that is what TDLib identifies a file by and what the
 * bubble hands back when somebody taps it.
 */
/**
 * An invite to a group this account is not in, for the join screen's main case.
 * The hash is shaped like a real one so it passes the same parsing.
 */
private val DEMO_JOIN_LINK = "https://t.me/+ExpressiveDesignClub"

/** The seeded chat the demo's timer speaks in — Material Design. */
private val CHATTY_CHAT_ID = 1L

/** How long the chatty chat types before each line. */
private val DEMO_TYPING_MS = 3_000L
private val DEMO_JOIN_TITLE = "Expressive Design Club"

private val DEMO_VIDEO_FILE_ID = 1601
private val DEMO_AUDIO_FILE_ID = 1602

/** The rail entry for the demo account's own posted stories. */
private val MY_STORIES_ID = 99L
private val DEMO_AUDIO_SECONDS = 12

/** The shape drawn for the demo's voice messages, in Telegram's 5-bit levels. */
private val DEMO_WAVEFORM = listOf(3, 8, 14, 20, 26, 18, 10, 6, 12, 22, 29, 24, 15, 9, 5, 11, 19, 27, 21, 13, 7, 4, 9, 16, 23, 17, 8, 3)

/** A small text file in Design Circle, so a file can be opened offline. */
private val DEMO_NOTES_FILE_ID = 1603

/**
 * Two files in Design Circle for the download manager: the guidelines,
 * listed and paused part-way, and the brand kit, slow enough to pause.
 */
private val DEMO_GUIDE_FILE_ID = 1604
private val DEMO_KIT_FILE_ID = 1605
private val DEMO_GUIDE_BYTES = 2_200_000L
private val DEMO_KIT_BYTES = 5_000_000L
private val DEMO_DOWNLOAD_STEPS = 25
private val DEMO_DOWNLOAD_STEP_MS = 400L

/** A made-up file of [bytes] in the temporary directory, standing for a download. */
private fun demoDownloadedFile(name: String): java.io.File {
    val file = java.io.File(System.getProperty("java.io.tmpdir") ?: "/tmp", "downloads/$name")
    if (!file.exists()) {
        file.parentFile?.mkdirs()
        file.writeText("A file from the TelegramYou demo: $name\n")
    }
    return file
}

/** The demo's text file, written on first open like the song. */
private fun demoNotesFile(): java.io.File {
    val file = java.io.File(System.getProperty("java.io.tmpdir") ?: "/tmp", "release-notes.txt")
    if (!file.exists()) {
        file.writeText("TelegramYou release notes\n\nGroups, music and files — made in the demo.\n")
    }
    return file
}

/**
 * The demo song: a soft arpeggio of sine tones, written as a WAV into the
 * app's temporary directory (Android points java.io.tmpdir at the cache)
 * the first time it is asked for. Twelve seconds at 8 kHz, 16-bit mono —
 * under 200 KB, and made rather than shipped.
 */
private fun demoAudioFile(): java.io.File {
    val file = java.io.File(System.getProperty("java.io.tmpdir") ?: "/tmp", "demo-expressive-motion.wav")
    if (file.exists() && file.length() > 0) return file
    val rate = 8_000
    val samples = rate * DEMO_AUDIO_SECONDS
    // C major, up and back: C E G C' G E, half a second a note.
    val notes = doubleArrayOf(261.63, 329.63, 392.0, 523.25, 392.0, 329.63)
    val noteLength = rate / 2
    val pcm = java.nio.ByteBuffer.allocate(samples * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
    for (i in 0 until samples) {
        val within = i % noteLength
        val frequency = notes[(i / noteLength) % notes.size]
        // A quick rise and a long fall, so each note chimes rather than beeps.
        val envelope = minOf(1.0, within / 80.0) * Math.exp(-3.0 * within / noteLength)
        val value = Math.sin(2 * Math.PI * frequency * i / rate) * envelope * 0.35
        pcm.putShort((value * Short.MAX_VALUE).toInt().toShort())
    }
    val header = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray()); putInt(36 + samples * 2); put("WAVE".toByteArray())
        put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
        putInt(rate); putInt(rate * 2); putShort(2); putShort(16)
        put("data".toByteArray()); putInt(samples * 2)
    }
    file.outputStream().use { out ->
        out.write(header.array())
        out.write(pcm.array())
    }
    return file
}

/** Ids for the files a demo send pretends to upload, and their pretend size. */
private val uploadFileId = java.util.concurrent.atomic.AtomicInteger(9_000)
private val DEMO_UPLOAD_BYTES = 2_600_000L
private val DEMO_VIDEO_BYTES = 8_400_000L

/** Two seconds of bar, in ten steps: long enough to watch, short enough to wait. */
private val DEMO_TRANSFER_STEPS = 10
private val DEMO_TRANSFER_STEP_MS = 200L

private val DEMO_VIDEO =
    "android.resource://${BuildConfig.APPLICATION_ID}/${R.raw.demo_video}"
private val DEMO_VIDEO_STICKER =
    "android.resource://${BuildConfig.APPLICATION_ID}/${R.raw.demo_video_sticker}"
private val DEMO_VIDEO_POSTER =
    "android.resource://${BuildConfig.APPLICATION_ID}/${R.raw.demo_video_poster}"

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

/** How long the demo's QR code waits before it counts as scanned. */
private const val DEMO_QR_SCAN_MS = 6_000L

/** A demo number ending in these asks for a login email; see submitPhoneNumber. */
private const val DEMO_EMAIL_PHONE_SUFFIX = "99999"

/** The week Telegram makes an email reset wait without Premium. */
private const val DEMO_EMAIL_RESET_WAIT = 7 * 24 * 60 * 60

/** Telegram's way of saying where a code went without saying all of it: m***@example.org. */
private fun maskedEmail(email: String): String {
    val at = email.indexOf('@')
    if (at <= 0) return email
    return email.take(1) + "***" + email.substring(at)
}

/** How many of a chat's latest messages opening it loads, as TDLib's first page. */
private const val DEMO_WINDOW = 40

/** How long Design Circle's history is before its visible conversation. */
private const val DEMO_ARCHIVE_SIZE = 120

/** The oldest line in Design Circle — well outside what opening it loads. */
const val DEMO_ARCHIVE_FIRST_LINE = "Kickoff: first sketches of the floating composer"

/** User ids for demo people known only from a private chat: this plus the chat id. */
private const val DEMO_PERSON_BASE = 2000L

/** User ids for contacts added in the demo. */
private const val DEMO_ADDED_BASE = 3000L

/** The demo bot's chat; see seedChats. */
private const val BOT_CHAT_ID = 11L

/** The demo's music channel; see musicChannel. */
private const val MUSIC_CHANNEL_ID = 13L
internal const val DEMO_MUSIC_CHANNEL = "Material Sound"

/** The demo's forum, a group whose conversation is split into topics. */
private const val FORUM_CHAT_ID = 12L
internal const val DEMO_FORUM = "Compose Forum"
internal const val DEMO_FORUM_TOPIC = "Releases"

/** The first of the public chats search finds; see publicChats. */
private const val PUBLIC_CHANNEL_ID = 500L
internal const val DEMO_PUBLIC_CHANNEL = "Expressive Design Weekly"
internal const val DEMO_POLL_QUESTION = "What do you reach for first?"
internal const val DEMO_CHANGELOG_BUTTON = "Changelog"
internal const val DEMO_CHANGELOG_ANSWER = "Polls and bot buttons landed"
internal const val DEMO_BOT_STATUS = "All green ✅"
internal const val DEMO_AUDIO_TITLE = "Expressive Motion"
internal const val DEMO_FORWARDED_TEXT = "Material 3 Expressive is now in the Compose alpha"
private const val DEMO_ALBUM_ID = 7001L
