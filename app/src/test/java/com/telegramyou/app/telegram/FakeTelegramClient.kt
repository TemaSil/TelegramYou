package com.telegramyou.app.telegram

import com.telegramyou.app.telegram.model.AttachmentDraft
import com.telegramyou.app.telegram.model.AuthUiState
import com.telegramyou.app.telegram.model.ChatDetail
import com.telegramyou.app.telegram.model.ChatMessage
import com.telegramyou.app.telegram.model.ChatPreview
import com.telegramyou.app.telegram.model.MessageHit
import com.telegramyou.app.telegram.model.StoryItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A client that answers from lists handed to it.
 *
 * Exists so state holders can be tested without TDLib, a device or the demo
 * backend's own opinions. Every method records that it was called, because
 * "did not ask again" is exactly what the paging guard has to prove.
 */
class FakeTelegramClient(
    private val window: List<ChatMessage> = emptyList(),
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

    override val authState: StateFlow<AuthUiState> = MutableStateFlow(AuthUiState())
    override val chats: StateFlow<List<ChatPreview>> = MutableStateFlow(emptyList())
    override val stories: StateFlow<List<StoryItem>> = MutableStateFlow(emptyList())

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

    override suspend fun sendText(chatId: Long, text: String, replyToId: Long?) = Unit
    override suspend fun sendAttachment(
        chatId: Long,
        draft: AttachmentDraft,
        caption: String,
        replyToId: Long?
    ) = Unit
    override suspend fun deleteMessage(chatId: Long, messageId: Long, forEveryone: Boolean) = Unit
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
