package com.telegramyou.app.telegram

import com.telegramyou.app.telegram.model.AttachmentDraft
import com.telegramyou.app.telegram.model.AuthUiState
import com.telegramyou.app.telegram.model.ChatDetail
import com.telegramyou.app.telegram.model.ChatMessage
import com.telegramyou.app.telegram.model.ChatPreview
import com.telegramyou.app.telegram.model.MessageHit
import com.telegramyou.app.telegram.model.StoryItem
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
    /** Settable, so a test can give the forward picker somewhere to point. */
    private val _chats = MutableStateFlow<List<ChatPreview>>(emptyList())
    override val chats: StateFlow<List<ChatPreview>> = _chats

    fun setChats(value: List<ChatPreview>) {
        _chats.value = value
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
    suspend fun deliver(message: ChatMessage) = _incomingMessages.emit(message)

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

    // ── not under test ───────────────────────────────────────────────────

    override suspend fun submitPhoneNumber(phone: String) = Unit
    override suspend fun submitCode(code: String) = Unit
    override suspend fun submitPassword(password: String) = Unit
    override suspend fun resendCode() = Unit
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
        toChatId: Long
    ) {
        forwarded = Triple(fromChatId, messageIds, toChatId)
    }

    /** The last mute asked for, as (chat, muted). */
    var muted: Pair<Long, Boolean>? = null
        private set

    override suspend fun setChatMuted(chatId: Long, muted: Boolean) {
        this.muted = chatId to muted
    }

    /** What downloadFile answers with; null unless a test sets it. */
    var downloadedPath: String? = null

    override suspend fun downloadFile(fileId: Int): String? = downloadedPath

    override suspend fun sendText(chatId: Long, text: String, replyToId: Long?) = Unit
    override suspend fun sendAttachment(
        chatId: Long,
        draft: AttachmentDraft,
        caption: String,
        replyToId: Long?
    ) = Unit
    /** In call order, so a batch delete can be checked message by message. */
    val deletedIds = mutableListOf<Long>()

    override suspend fun deleteMessage(chatId: Long, messageId: Long, forEveryone: Boolean) {
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

    override suspend fun markStorySeen(storyId: Long) = Unit
}
