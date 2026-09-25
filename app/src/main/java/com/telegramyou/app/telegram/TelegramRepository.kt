package com.telegramyou.app.telegram

import com.telegramyou.app.telegram.model.AttachmentDraft
import com.telegramyou.app.telegram.model.AuthUiState
import com.telegramyou.app.telegram.model.ChatDetail
import com.telegramyou.app.telegram.model.ChatFolder
import com.telegramyou.app.telegram.model.ChatPreview
import com.telegramyou.app.telegram.model.StoryItem
import com.telegramyou.app.ui.media.FileTransfer
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

class TelegramRepository(
    private val client: TelegramClient
) : TelegramClient by client {

    fun observeAuth(): StateFlow<AuthUiState> = authState
    fun observeChats(): StateFlow<List<ChatPreview>> = chats
    fun observeStories(): StateFlow<List<StoryItem>> = stories
    fun observeFolders(): StateFlow<List<ChatFolder>> = folders
    fun observeTransfers(): StateFlow<Map<Int, FileTransfer>> = fileTransfers

    /** Opening windows fetched ahead of their screen; see [warmChat]. */
    private val warmed = ConcurrentHashMap<Long, ChatDetail>()

    /**
     * Fetches a chat's opening window before its screen exists, so the
     * container transform opens onto a conversation that is already there.
     *
     * Without it the screen grew out of its row empty and the messages
     * arrived partway through, and the list laying itself out under a moving
     * container was the rest of the shake. TDLib answers this from its local
     * database in milliseconds; [waitMillis] caps the wait for when it has to
     * ask the server, and past it the chat opens as before and loads itself.
     * Only a read — the chat is held open by its screen, not by this.
     */
    suspend fun warmChat(chatId: Long, waitMillis: Long = WARM_WAIT_MS) {
        val detail = withTimeoutOrNull(waitMillis) {
            runCatching { client.openChat(chatId) }.getOrNull()
        } ?: return
        warmed[chatId] = detail
    }

    /** The window [warmChat] fetched, once; null when there is none. */
    fun takeWarmChat(chatId: Long): ChatDetail? = warmed.remove(chatId)

    // deleteMessage and editMessage arrive through the `by client` delegation
    // above; redeclaring them here only shadowed the interface.

    suspend fun sendMessage(
        chatId: Long,
        text: String,
        attachment: AttachmentDraft? = null,
        replyToId: Long? = null
    ) {
        if (attachment != null) {
            client.sendAttachment(chatId, attachment, text, replyToId)
        } else if (text.isNotBlank()) {
            client.sendText(chatId, text, replyToId)
        }
    }
}

/** Longest a tap waits for a chat's messages before opening it without them. */
private const val WARM_WAIT_MS = 200L
