package com.telegramyou.app.telegram

import com.telegramyou.app.notifications.ChatNotificationSettings
import com.telegramyou.app.telegram.model.ActiveSession
import com.telegramyou.app.telegram.model.AttachmentDraft
import com.telegramyou.app.telegram.model.AuthUiState
import com.telegramyou.app.telegram.model.CallbackAnswer
import com.telegramyou.app.telegram.model.ChatDetail
import com.telegramyou.app.telegram.model.ReplyKeyboard
import com.telegramyou.app.telegram.model.ChatMessage
import com.telegramyou.app.telegram.model.MessageUpdate
import com.telegramyou.app.telegram.model.ChatFolder
import com.telegramyou.app.telegram.model.ChatPreview
import com.telegramyou.app.telegram.model.InviteLinkPreview
import com.telegramyou.app.telegram.model.MessageHit
import com.telegramyou.app.telegram.model.PostSearch
import com.telegramyou.app.telegram.model.PollDraft
import com.telegramyou.app.telegram.model.PrivacyAudience
import com.telegramyou.app.telegram.model.PrivacyRules
import com.telegramyou.app.telegram.model.PrivacySetting
import com.telegramyou.app.telegram.model.ProxyServer
import com.telegramyou.app.telegram.model.StickerContent
import com.telegramyou.app.telegram.model.StickerSetPreview
import com.telegramyou.app.telegram.model.StorageKind
import com.telegramyou.app.telegram.model.StorageUsage
import com.telegramyou.app.telegram.model.StoryFrame
import com.telegramyou.app.telegram.model.StoryItem
import com.telegramyou.app.telegram.model.TelegramUser
import com.telegramyou.app.ui.media.FileTransfer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A client that answers from lists handed to it.
 *
 * Exists so state holders can be tested without TDLib, a device or the demo
 * backend's own opinions. Every method records that it was called, because
 * "did not ask again" is exactly what the paging guard has to prove.
 */
class FakeTelegramClient(
    /**
     * The window openChat answers with. A var, so a test can make a message
     * disappear between reloads the way another client deleting it would.
     */
    var window: List<ChatMessage> = emptyList(),
    /** Pages returned by successive calls; an exhausted list answers empty. */
    private val olderPages: MutableList<List<ChatMessage>> = mutableListOf(),
    /** Everything searchChats may match against. */
    private val searchable: List<ChatPreview> = emptyList(),
    /** Everything searchMessages may match against. */
    private val searchableMessages: List<MessageHit> = emptyList()
) : TelegramClient {

    var openChatCount = 0
        private set
    var loadOlderCount = 0
        private set
    var lastLoadOlderBefore: Long? = null
        private set
    var searchCount = 0
        private set
    var lastSearchQuery: String? = null
        private set
    var messageSearchCount = 0
        private set

    // Kept as a MutableStateFlow rather than published straight as a
    // StateFlow, so a test can hand the fake an account and so the profile
    // calls below can change it the way a real backend would.
    val mutableAuthState = MutableStateFlow(AuthUiState())
    override val authState: StateFlow<AuthUiState> = mutableAuthState
    /** Nothing moves in a fake unless a test says so. */
    val mutableTransfers = MutableStateFlow<Map<Int, FileTransfer>>(emptyMap())
    override val fileTransfers: StateFlow<Map<Int, FileTransfer>> = mutableTransfers

    /** Settable, so a test can give the forward picker somewhere to point. */
    private val _chats = MutableStateFlow<List<ChatPreview>>(emptyList())
    override val chats: StateFlow<List<ChatPreview>> = _chats

    fun setChats(value: List<ChatPreview>) {
        _chats.value = value
    }

    /** Settable for the same reason: a test decides which tabs exist. */
    private val _folders = MutableStateFlow<List<ChatFolder>>(emptyList())
    override val folders: StateFlow<List<ChatFolder>> = _folders

    fun setFolders(value: List<ChatFolder>) {
        _folders.value = value
    }

    /** What [chatInviteLink] answers, and which chats were left. */
    var inviteLink: String? = null
    val leftChats = mutableListOf<Long>()

    override suspend fun chatInviteLink(chatId: Long): String? = inviteLink

    /** What was asked for, so a test can see it arrived intact. */
    val createdGroups = mutableListOf<Pair<String, List<Long>>>()
    val createdChannels = mutableListOf<Pair<String, String>>()
    var invitePreview: InviteLinkPreview? = null
    val joinedLinks = mutableListOf<String>()

    override suspend fun createGroup(title: String, memberIds: List<Long>): Long {
        createdGroups += title to memberIds
        return 900L + createdGroups.size
    }

    override suspend fun createChannel(title: String, description: String): Long {
        createdChannels += title to description
        return 950L + createdChannels.size
    }

    override suspend fun checkInviteLink(link: String): InviteLinkPreview? = invitePreview

    override suspend fun joinByInviteLink(link: String): Long {
        joinedLinks += link
        return 990L
    }

    override suspend fun leaveChat(chatId: Long) {
        leftChats += chatId
        _chats.value = _chats.value.filterNot { it.id == chatId }
    }
    override val stories: StateFlow<List<StoryItem>> = MutableStateFlow(emptyList())

    private val _incomingMessages = MutableSharedFlow<ChatMessage>(extraBufferCapacity = 16)
    override val incomingMessages: SharedFlow<ChatMessage> = _incomingMessages

    /**
     * Delivers a message as though it had just arrived from the server.
     *
     * `suspend` and `emit` rather than `tryEmit`: a test needs the collector
     * to have run by the time it asserts, and tryEmit would return before
     * anyone had seen it.
     */
    suspend fun deliver(message: ChatMessage) {
        _incomingMessages.emit(message)
        _messageUpdates.emit(MessageUpdate.Added(message))
    }

    private val _messageUpdates = MutableSharedFlow<MessageUpdate>(extraBufferCapacity = 16)
    override val messageUpdates: SharedFlow<MessageUpdate> = _messageUpdates

    /** Any other change, as though the server had just announced it. */
    suspend fun announce(update: MessageUpdate) = _messageUpdates.emit(update)

    /** Set to make the next request of each kind fail the way TDLib does. */
    var failWith: Exception? = null

    private fun maybeFail() {
        failWith?.let { throw it }
    }

    override fun start() = Unit
    override fun shutdown() = Unit

    override suspend fun openChat(chatId: Long): ChatDetail {
        openChatCount++
        return ChatDetail(
            chat = ChatPreview(chatId, "Fake", "", ""),
            messages = window
        )
    }

    override suspend fun loadOlderMessages(
        chatId: Long,
        beforeMessageId: Long,
        limit: Int
    ): List<ChatMessage> {
        loadOlderCount++
        lastLoadOlderBefore = beforeMessageId
        return if (olderPages.isEmpty()) emptyList() else olderPages.removeAt(0)
    }

    /** What a jump to an old message is answered with; see ChatViewModelTest. */
    var aroundPage: List<ChatMessage> = emptyList()
    var lastAroundId: Long? = null

    /** Handed out one per call to loadNewerMessages, then empty. */
    val newerPages = mutableListOf<List<ChatMessage>>()
    var lastNewerAfter: Long? = null

    override suspend fun loadMessagesAround(
        chatId: Long,
        messageId: Long,
        limit: Int
    ): List<ChatMessage> {
        lastAroundId = messageId
        return aroundPage
    }

    override suspend fun loadNewerMessages(
        chatId: Long,
        afterMessageId: Long,
        limit: Int
    ): List<ChatMessage> {
        lastNewerAfter = afterMessageId
        return if (newerPages.isEmpty()) emptyList() else newerPages.removeAt(0)
    }

    // ── not under test ───────────────────────────────────────────────────

    override suspend fun submitPhoneNumber(phone: String) = Unit
    override suspend fun submitCode(code: String) = Unit
    override suspend fun submitPassword(password: String) = Unit
    override suspend fun requestQrLogin() = Unit

    override suspend fun activeSessions(): List<ActiveSession> = emptyList()

    override suspend fun terminateSession(id: Long) = Unit

    override suspend fun privacyRules(setting: PrivacySetting) = PrivacyRules(PrivacyAudience.Everybody)

    override suspend fun setPrivacyRules(setting: PrivacySetting, rules: PrivacyRules) = Unit

    override suspend fun terminateOtherSessions() = Unit

    override suspend fun storageUsage() = StorageUsage(emptyList())

    override suspend fun clearCache(kinds: Set<StorageKind>) =
        StorageUsage(emptyList())

    override suspend fun submitEmailAddress(email: String) = Unit

    override suspend fun submitEmailCode(code: String) = Unit

    override suspend fun resetEmail() = Unit

    override suspend fun resendCode() = Unit
    /** Chat actions, in order, kept apart from the profile's own calls. */
    val chatCalls = mutableListOf<String>()

    /**
     * Handed to the picker; empty unless a test says otherwise.
     *
     * Named contactList rather than contacts because the interface's own
     * member is a function of that name — both compile, and reading them
     * side by side would not.
     */
    var contactList: List<TelegramUser> = emptyList()

    override suspend fun contacts(): List<TelegramUser> = contactList

    override suspend fun openPrivateChat(userId: Long): Long {
        chatCalls += "openPrivateChat:$userId"
        return userId
    }

    /** What chatMedia answers with; empty unless a test says otherwise. */
    var media: List<ChatMessage> = emptyList()

    override suspend fun chatMedia(chatId: Long, limit: Int): List<ChatMessage> {
        chatCalls += "chatMedia:$chatId"
        return media
    }

    override suspend fun setChatArchived(chatId: Long, archived: Boolean) {
        chatCalls += "setChatArchived:$chatId:$archived"
    }

    override suspend fun setChatPinned(chatId: Long, pinned: Boolean) {
        chatCalls += "setChatPinned:$chatId:$pinned"
    }

    /** What loadMoreChats answers; each call is recorded in chatCalls. */
    var hasMoreChats = false

    override suspend fun loadMoreChats(folderId: Int?): Boolean {
        chatCalls += "loadMoreChats:$folderId"
        return hasMoreChats
    }

    override fun retainChat(chatId: Long) {
        chatCalls += "retainChat:$chatId"
    }

    override fun releaseChat(chatId: Long) {
        chatCalls += "releaseChat:$chatId"
    }

    override suspend fun markChatRead(chatId: Long) {
        chatCalls += "markChatRead:$chatId"
    }

    override suspend fun logout() = Unit

    // ── profile ──────────────────────────────────────────────────────────

    /** What was sent, in order, so a test can prove only the changes went. */
    val profileCalls = mutableListOf<String>()

    /** Set to make the next save fail the way a taken username does. */
    var profileError: String? = null

    override suspend fun setName(firstName: String, lastName: String) {
        profileError?.let { throw IllegalStateException(it) }
        profileCalls += "setName:$firstName|$lastName"
        me = me?.copy(firstName = firstName, lastName = lastName)
    }

    override suspend fun setProfilePhoto(uri: String) {
        profileError?.let { throw IllegalStateException(it) }
        profileCalls += "setProfilePhoto:$uri"
        me = me?.copy(photoPath = uri)
    }

    override suspend fun setBio(bio: String) {
        profileError?.let { throw IllegalStateException(it) }
        profileCalls += "setBio:$bio"
        me = me?.copy(bio = bio)
    }

    override suspend fun setUsername(username: String) {
        profileError?.let { throw IllegalStateException(it) }
        profileCalls += "setUsername:$username"
        me = me?.copy(username = username.ifBlank { null })
    }

    override suspend fun refreshMe() {
        profileCalls += "refreshMe"
    }

    /** The signed-in account, as the auth state carries it. */
    private var me
        get() = mutableAuthState.value.me
        set(value) {
            mutableAuthState.value = mutableAuthState.value.copy(me = value)
        }
    override suspend fun refreshChats() = Unit

    /** Public chats the next global search finds. */
    var publicChats: List<ChatPreview> = emptyList()
    override suspend fun searchPublicChats(query: String): List<ChatPreview> =
        publicChats.filter { it.title.contains(query, ignoreCase = true) }

    var people: List<ChatPreview> = emptyList()
    override suspend fun topPeople(limit: Int): List<ChatPreview> = people.take(limit)

    val recentlyFound = mutableListOf<ChatPreview>()
    override suspend fun recentlyFoundChats(): List<ChatPreview> = recentlyFound.toList()
    override suspend fun addRecentlyFoundChat(chatId: Long) {
        val chat = (chats.value + searchable + publicChats).firstOrNull { it.id == chatId } ?: return
        recentlyFound.removeAll { it.id == chatId }
        recentlyFound.add(0, chat)
    }
    override suspend fun removeRecentlyFoundChat(chatId: Long) {
        recentlyFound.removeAll { it.id == chatId }
    }
    override suspend fun clearRecentlyFoundChats() = recentlyFound.clear()
    override suspend fun recommendedChannels(): List<ChatPreview> = emptyList()

    var postSearch = PostSearch()
    var postSearchCount = 0
    override suspend fun searchPublicPosts(query: String, limit: Int): PostSearch {
        postSearchCount++
        return postSearch
    }

    override suspend fun searchChats(query: String, limit: Int): List<ChatPreview> {
        searchCount++
        lastSearchQuery = query
        return searchable.filter { it.title.contains(query, ignoreCase = true) }
    }

    override suspend fun searchMessages(query: String, limit: Int): List<MessageHit> {
        messageSearchCount++
        return searchableMessages.filter { it.message.text.contains(query, ignoreCase = true) }
    }

    var chatSearchCount = 0
        private set

    /**
     * Matches against the window, which is enough: the state holder's job is
     * to debounce, not to search.
     */
    override suspend fun searchChatMessages(
        chatId: Long,
        query: String,
        limit: Int
    ): List<ChatMessage> {
        chatSearchCount++
        if (query.isBlank()) return emptyList()
        return window.filter { it.text.contains(query, ignoreCase = true) }
    }

    /** The one forward that was asked for, as (from, ids, to). */
    var forwarded: Triple<Long, List<Long>, Long>? = null
        private set

    override suspend fun forwardMessages(
        fromChatId: Long,
        messageIds: List<Long>,
        toChatId: Long,
        withoutQuote: Boolean
    ) {
        forwarded = Triple(fromChatId, messageIds, toChatId)
    }

    /** The last mute asked for, as (chat, muted). */
    var muted: Pair<Long, Boolean>? = null
        private set

    override suspend fun setChatMuted(chatId: Long, muted: Boolean) {
        maybeFail()
        this.muted = chatId to muted
    }

    override suspend fun setChatNotifications(chatId: Long, settings: ChatNotificationSettings) = Unit

    /** What downloadFile answers with; null unless a test sets it. */
    var downloadedPath: String? = null

    override suspend fun downloadFile(fileId: Int): String? = downloadedPath

    /** Every text sent, in order. */
    val sentTexts = mutableListOf<String>()

    /** Texts scheduled rather than sent, with when for. */
    val scheduledTexts = mutableListOf<Pair<String, Long>>()

    override suspend fun sendText(chatId: Long, text: String, replyToId: Long?, sendAt: Long?) {
        maybeFail()
        if (sendAt != null) scheduledTexts += text to sendAt else sentTexts += text
    }

    val sentPolls = mutableListOf<PollDraft>()
    override suspend fun sendPoll(chatId: Long, draft: PollDraft) {
        maybeFail()
        sentPolls += draft
    }

    var scheduled: List<ChatMessage> = emptyList()
    val sentNow = mutableListOf<Long>()
    override suspend fun scheduledMessages(chatId: Long): List<ChatMessage> = scheduled
    override suspend fun sendScheduledNow(chatId: Long, messageId: Long) {
        sentNow += messageId
        scheduled = scheduled.filterNot { it.id == messageId }
    }
    override suspend fun sendAttachment(
        chatId: Long,
        draft: AttachmentDraft,
        caption: String,
        replyToId: Long?
    ) = Unit
    /** In call order, so a batch delete can be checked message by message. */
    val deletedIds = mutableListOf<Long>()

    override suspend fun deleteMessage(chatId: Long, messageId: Long, forEveryone: Boolean) {
        maybeFail()
        deletedIds += messageId
    }
    override suspend fun editMessage(chatId: Long, messageId: Long, text: String) = Unit

    /**
     * Recorded rather than applied: the tests check what was asked for.
     *
     * A val, not a var: `+=` on a var of MutableList type is ambiguous between
     * plusAssign and plus-then-reassign, and Kotlin refuses to pick.
     */
    val reactionCalls = mutableListOf<Triple<Long, Long, String>>()

    /** Named apart from the override so neither shadows the other. */
    var permittedReactions: List<String> = listOf("👍", "🔥")

    override suspend fun toggleReaction(chatId: Long, messageId: Long, emoji: String) {
        reactionCalls += Triple(chatId, messageId, emoji)
    }

    override suspend fun availableReactions(chatId: Long): List<String> = permittedReactions

    /** Votes, in the order they were cast: chat, message, options. */
    val votes = mutableListOf<Triple<Long, Long, List<Int>>>()
    override suspend fun votePoll(chatId: Long, messageId: Long, optionIds: List<Int>) {
        votes += Triple(chatId, messageId, optionIds)
    }

    var callbackAnswer: CallbackAnswer? = null
    override suspend fun pressButton(chatId: Long, messageId: Long, data: String): CallbackAnswer? =
        callbackAnswer

    val mutableReplyKeyboards = MutableStateFlow<Map<Long, ReplyKeyboard>>(emptyMap())
    override val replyKeyboards: StateFlow<Map<Long, ReplyKeyboard>> = mutableReplyKeyboards

    override suspend fun storyFrames(storyId: Long): List<StoryFrame> = emptyList()

    override suspend fun stickerSets(): List<StickerSetPreview> = emptyList()
    override suspend fun stickerSet(setId: Long): List<StickerContent> = emptyList()
    override suspend fun recentStickers(): List<StickerContent> = emptyList()
    override suspend fun sendSticker(chatId: Long, sticker: StickerContent, replyToId: Long?) = Unit

    override suspend fun proxies(): List<ProxyServer> = emptyList()
    override suspend fun addProxy(proxy: ProxyServer, enable: Boolean): Int = 1
    override suspend fun enableProxy(id: Int) = Unit
    override suspend fun disableProxy() = Unit
    override suspend fun removeProxy(id: Int) = Unit
    override suspend fun pingProxy(proxy: ProxyServer): Long? = null

    override suspend fun markStorySeen(storyId: Long, frameId: Int) = Unit
}
