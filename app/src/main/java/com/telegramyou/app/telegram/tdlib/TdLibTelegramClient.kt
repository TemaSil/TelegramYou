package com.telegramyou.app.telegram.tdlib

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import com.telegramyou.app.BuildConfig
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Live Telegram client via official TDLib JSON API.
 * Authorization & updates follow https://core.telegram.org/tdlib/getting-started
 */
class TdLibTelegramClient(
    private val context: Context,
    private val apiId: Int,
    private val apiHash: String
) : TelegramClient {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val chatMutex = Mutex()

    private val databaseDir: File =
        File(context.filesDir, "tdlib").also { it.mkdirs() }
    private val filesDir: File =
        File(context.filesDir, "tdlib-files").also { it.mkdirs() }
    private val uploadCache: File =
        File(context.cacheDir, "tdlib-upload").also { it.mkdirs() }

    private val chatsById = ConcurrentHashMap<Long, JSONObject>()
    private val usersById = ConcurrentHashMap<Long, JSONObject>()
    private val chatOrder = ConcurrentHashMap<Long, Long>()
    private val messagesByChat = ConcurrentHashMap<Long, MutableList<ChatMessage>>()

    private val _authState = MutableStateFlow(
        AuthUiState(state = AuthState.Bootstrapping, isLoading = true)
    )
    override val authState: StateFlow<AuthUiState> = _authState.asStateFlow()

    private val _chats = MutableStateFlow<List<ChatPreview>>(emptyList())
    override val chats: StateFlow<List<ChatPreview>> = _chats.asStateFlow()

    private val _stories = MutableStateFlow<List<StoryItem>>(emptyList())
    override val stories: StateFlow<List<StoryItem>> = _stories.asStateFlow()

    private var engine: TdJsonEngine? = null
    private var readySignal = CompletableDeferred<Unit>()

    override fun start() {
        if (apiId == 0 || apiHash.isBlank()) {
            _authState.value = AuthUiState(
                state = AuthState.Error,
                errorMessage = "Set TELEGRAM_API_ID and TELEGRAM_API_HASH in local.properties (my.telegram.org)",
                isLoading = false
            )
            return
        }
        try {
            val eng = TdJsonEngine { update -> handleUpdate(update) }
            engine = eng
            eng.start()
            // Kick the client — first request triggers authorization updates
            eng.sendFireAndForget(JSONObject().put("@type", "getOption").put("name", "version"))
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start TDLib", t)
            _authState.value = AuthUiState(
                state = AuthState.Error,
                errorMessage = "TDLib native load failed: ${t.message}",
                isLoading = false
            )
        }
    }

    override fun shutdown() {
        engine?.stop()
        engine = null
    }

    override suspend fun submitPhoneNumber(phone: String) {
        _authState.update { it.copy(isLoading = true, errorMessage = null, phoneNumber = phone) }
        try {
            requireEngine().send(
                JSONObject()
                    .put("@type", "setAuthenticationPhoneNumber")
                    .put("phone_number", phone)
                    .put("settings", JSONObject().put("@type", "phoneNumberAuthenticationSettings")
                        .put("allow_flash_call", false)
                        .put("allow_missed_call", false)
                        .put("is_current_phone_number", false)
                        .put("has_unknown_phone_number", false)
                        .put("allow_sms_retriever_api", true))
            )
        } catch (e: TdLibException) {
            _authState.update { it.copy(isLoading = false, errorMessage = e.message) }
        }
    }

    override suspend fun submitCode(code: String) {
        _authState.update { it.copy(isLoading = true, errorMessage = null) }
        try {
            requireEngine().send(
                JSONObject()
                    .put("@type", "checkAuthenticationCode")
                    .put("code", code.trim())
            )
        } catch (e: TdLibException) {
            _authState.update { it.copy(isLoading = false, errorMessage = e.message) }
        }
    }

    override suspend fun submitPassword(password: String) {
        _authState.update { it.copy(isLoading = true, errorMessage = null) }
        try {
            requireEngine().send(
                JSONObject()
                    .put("@type", "checkAuthenticationPassword")
                    .put("password", password)
            )
        } catch (e: TdLibException) {
            _authState.update { it.copy(isLoading = false, errorMessage = e.message) }
        }
    }

    override suspend fun resendCode() {
        try {
            requireEngine().send(JSONObject().put("@type", "resendAuthenticationCode"))
            _authState.update { it.copy(codeHint = "Code resent", errorMessage = null) }
        } catch (e: TdLibException) {
            _authState.update { it.copy(errorMessage = e.message) }
        }
    }

    override suspend fun refreshChats() {
        awaitReady()
        try {
            requireEngine().send(
                JSONObject()
                    .put("@type", "loadChats")
                    .put("chat_list", JSONObject().put("@type", "chatListMain"))
                    .put("limit", 50)
            )
            publishChats()
            refreshStories()
        } catch (e: TdLibException) {
            Log.w(TAG, "loadChats: ${e.message}")
        }
    }

    override suspend fun openChat(chatId: Long): ChatDetail {
        awaitReady()
        val eng = requireEngine()
        eng.send(JSONObject().put("@type", "openChat").put("chat_id", chatId))
        val history = eng.send(
            JSONObject()
                .put("@type", "getChatHistory")
                .put("chat_id", chatId)
                .put("from_message_id", 0)
                .put("offset", 0)
                .put("limit", 50)
                .put("only_local", false)
        )
        val mapped = parseMessages(chatId, history.optJSONArray("messages"))
        messagesByChat[chatId] = mapped.toMutableList()
        val chat = chatsById[chatId]
        val preview = chat?.let { toPreview(it) }
            ?: ChatPreview(chatId, "Chat $chatId", "", "")
        return ChatDetail(
            chat = preview,
            messages = mapped,
            memberCountLabel = statusLabel(chat),
            isTyping = false
        )
    }

    /**
     * TDLib expects a reply as an inputMessageReplyToMessage on the send, not
     * a bare id. Absent when nothing is being answered — passing a null
     * message_id would be rejected.
     */
    private fun JSONObject.withReplyTo(replyToId: Long?): JSONObject = apply {
        if (replyToId != null) {
            put(
                "reply_to",
                JSONObject()
                    .put("@type", "inputMessageReplyToMessage")
                    .put("message_id", replyToId)
            )
        }
    }

    override suspend fun sendText(chatId: Long, text: String, replyToId: Long?) {
        awaitReady()
        requireEngine().send(
            JSONObject()
                .put("@type", "sendMessage")
                .put("chat_id", chatId)
                .withReplyTo(replyToId)
                .put(
                    "input_message_content",
                    JSONObject()
                        .put("@type", "inputMessageText")
                        .put(
                            "text",
                            JSONObject()
                                .put("@type", "formattedText")
                                .put("text", text)
                        )
                )
        )
    }

    override suspend fun sendAttachment(
        chatId: Long,
        draft: AttachmentDraft,
        caption: String,
        replyToId: Long?
    ) {
        awaitReady()
        when (draft) {
            // Only the first of a batch answers the quoted message; the rest
            // would each repeat the quote, which is not what Telegram does.
            is AttachmentDraft.Photos -> draft.uris.forEachIndexed { index, uri ->
                val path = copyUriToCache(uri, "photo_${System.currentTimeMillis()}.jpg")
                sendLocalFile(chatId, path, caption, photo = true,
                    replyToId = replyToId.takeIf { index == 0 })
            }
            is AttachmentDraft.Files ->
                draft.uris.zip(draft.names).forEachIndexed { index, (uri, name) ->
                    val path = copyUriToCache(uri, name)
                    sendLocalFile(chatId, path, caption, photo = false,
                        replyToId = replyToId.takeIf { index == 0 })
                }
        }
    }

    override suspend fun deleteMessage(
        chatId: Long,
        messageId: Long,
        forEveryone: Boolean
    ) {
        awaitReady()
        requireEngine().send(
            JSONObject()
                .put("@type", "deleteMessages")
                .put("chat_id", chatId)
                .put("message_ids", JSONArray().put(messageId))
                .put("revoke", forEveryone)
        )
        // TDLib confirms with updateDeleteMessages, but the local copy is
        // dropped now so the bubble goes as the tap lands.
        messagesByChat[chatId]?.removeAll { it.id == messageId }
    }

    override suspend fun editMessage(chatId: Long, messageId: Long, text: String) {
        awaitReady()
        requireEngine().send(
            JSONObject()
                .put("@type", "editMessageText")
                .put("chat_id", chatId)
                .put("message_id", messageId)
                .put(
                    "input_message_content",
                    JSONObject()
                        .put("@type", "inputMessageText")
                        .put(
                            "text",
                            JSONObject()
                                .put("@type", "formattedText")
                                .put("text", text)
                        )
                )
        )
    }

    override suspend fun markStorySeen(storyId: Long) {
        _stories.update { list ->
            list.map { if (it.id == storyId) it.copy(hasUnseen = false) else it }
        }
        try {
            // storyId encodes chatId in high bits for our mapping; openStory when possible
            val chatId = storyId shr 16
            val realStoryId = (storyId and 0xFFFF).toInt()
            requireEngine().send(
                JSONObject()
                    .put("@type", "openStory")
                    .put("story_sender_chat_id", chatId)
                    .put("story_id", realStoryId)
            )
        } catch (_: Throwable) {
            // optional on older TDLib builds
        }
    }

    override suspend fun logout() {
        try {
            requireEngine().send(JSONObject().put("@type", "logOut"))
        } catch (_: Throwable) {
            // ignore
        }
        _authState.value = AuthUiState(state = AuthState.WaitPhoneNumber)
        _chats.value = emptyList()
        _stories.value = emptyList()
    }

    private suspend fun sendLocalFile(
        chatId: Long,
        path: String,
        caption: String,
        photo: Boolean,
        replyToId: Long? = null
    ) {
        val content = if (photo) {
            JSONObject()
                .put("@type", "inputMessagePhoto")
                .put(
                    "photo",
                    JSONObject().put("@type", "inputFileLocal").put("path", path)
                )
                .put(
                    "caption",
                    JSONObject().put("@type", "formattedText").put("text", caption)
                )
        } else {
            JSONObject()
                .put("@type", "inputMessageDocument")
                .put(
                    "document",
                    JSONObject().put("@type", "inputFileLocal").put("path", path)
                )
                .put(
                    "caption",
                    JSONObject().put("@type", "formattedText").put("text", caption)
                )
        }
        requireEngine().send(
            JSONObject()
                .put("@type", "sendMessage")
                .put("chat_id", chatId)
                .withReplyTo(replyToId)
                .put("input_message_content", content)
        )
    }

    private fun handleUpdate(update: JSONObject) {
        when (update.optString("@type")) {
            "updateAuthorizationState" -> {
                val state = update.optJSONObject("authorization_state") ?: return
                scope.launch { onAuthorizationState(state) }
            }
            "updateUser" -> {
                val user = update.optJSONObject("user") ?: return
                usersById[user.optLong("id")] = user
            }
            "updateNewChat" -> {
                val chat = update.optJSONObject("chat") ?: return
                chatsById[chat.optLong("id")] = chat
                scope.launch { publishChats() }
            }
            "updateChatTitle", "updateChatPhoto", "updateChatLastMessage",
            "updateChatReadInbox", "updateChatNotificationSettings",
            "updateChatUnreadMentionCount" -> {
                val chatId = update.optLong("chat_id")
                val chat = chatsById[chatId] ?: return
                when (update.optString("@type")) {
                    "updateChatTitle" -> chat.put("title", update.optString("title"))
                    "updateChatLastMessage" -> {
                        chat.put("last_message", update.optJSONObject("last_message"))
                        val positions = update.optJSONArray("positions")
                        if (positions != null) applyPositions(chatId, positions)
                    }
                    "updateChatReadInbox" -> {
                        chat.put("unread_count", update.optInt("unread_count"))
                    }
                    "updateChatNotificationSettings" -> {
                        chat.put("notification_settings", update.optJSONObject("notification_settings"))
                    }
                }
                scope.launch { publishChats() }
            }
            "updateChatPosition" -> {
                val chatId = update.optLong("chat_id")
                val position = update.optJSONObject("position") ?: return
                applyPosition(chatId, position)
                scope.launch { publishChats() }
            }
            "updateNewMessage" -> {
                val message = update.optJSONObject("message") ?: return
                val chatId = message.optLong("chat_id")
                val mapped = mapMessage(chatId, message)
                messagesByChat.getOrPut(chatId) { mutableListOf() }.add(mapped)
                chatsById[chatId]?.put("last_message", message)
                scope.launch { publishChats() }
            }
            "updateMessageSendSucceeded" -> {
                // refresh last message mapping if needed
            }
            "updateActiveStories", "updateChatActiveStories" -> {
                scope.launch { refreshStories() }
            }
        }
    }

    private suspend fun onAuthorizationState(state: JSONObject) {
        when (state.optString("@type")) {
            "authorizationStateWaitTdlibParameters" -> {
                _authState.update { it.copy(state = AuthState.Bootstrapping, isLoading = true) }
                try {
                    requireEngine().send(buildTdlibParameters())
                } catch (e: TdLibException) {
                    _authState.update {
                        it.copy(state = AuthState.Error, errorMessage = e.message, isLoading = false)
                    }
                }
            }
            "authorizationStateWaitPhoneNumber" -> {
                _authState.update {
                    it.copy(state = AuthState.WaitPhoneNumber, isLoading = false, errorMessage = null)
                }
            }
            "authorizationStateWaitCode" -> {
                val codeInfo = state.optJSONObject("code_info")
                val hint = codeInfo?.optJSONObject("type")?.optString("@type") ?: "code"
                _authState.update {
                    it.copy(
                        state = AuthState.WaitCode,
                        isLoading = false,
                        codeHint = "Enter the code from Telegram ($hint)"
                    )
                }
            }
            "authorizationStateWaitPassword" -> {
                val hint = state.optString("password_hint").ifBlank { "2FA password" }
                _authState.update {
                    it.copy(
                        state = AuthState.WaitPassword,
                        isLoading = false,
                        codeHint = "Cloud password: $hint"
                    )
                }
            }
            "authorizationStateWaitRegistration" -> {
                // Auto-register with app name as first name for first-time accounts
                try {
                    requireEngine().send(
                        JSONObject()
                            .put("@type", "registerUser")
                            .put("first_name", "TelegramYou")
                            .put("last_name", "")
                    )
                } catch (e: TdLibException) {
                    _authState.update {
                        it.copy(state = AuthState.Error, errorMessage = e.message, isLoading = false)
                    }
                }
            }
            "authorizationStateWaitEmailAddress",
            "authorizationStateWaitEmailCode",
            "authorizationStateWaitOtherDeviceConfirmation" -> {
                _authState.update {
                    it.copy(
                        state = AuthState.Error,
                        isLoading = false,
                        errorMessage = "This login step (${state.optString("@type")}) is not yet supported in TelegramYou UI."
                    )
                }
            }
            "authorizationStateReady" -> {
                val me = fetchMe()
                _authState.update {
                    it.copy(state = AuthState.Ready, isLoading = false, me = me, errorMessage = null)
                }
                if (!readySignal.isCompleted) readySignal.complete(Unit)
                refreshChats()
            }
            "authorizationStateLoggingOut",
            "authorizationStateClosing" -> {
                _authState.update { it.copy(state = AuthState.Bootstrapping, isLoading = true) }
            }
            "authorizationStateClosed" -> {
                _authState.update { it.copy(state = AuthState.Closed, isLoading = false) }
            }
        }
    }

    private fun buildTdlibParameters(): JSONObject =
        JSONObject()
            .put("@type", "setTdlibParameters")
            .put("use_test_dc", false)
            .put("database_directory", databaseDir.absolutePath)
            .put("files_directory", filesDir.absolutePath)
            .put("use_file_database", true)
            .put("use_chat_info_database", true)
            .put("use_message_database", true)
            .put("use_secret_chats", true)
            .put("api_id", apiId)
            .put("api_hash", apiHash)
            .put("system_language_code", TdJsonEngine.systemLanguage())
            .put("device_model", TdJsonEngine.deviceModel())
            .put("system_version", "Android ${Build.VERSION.RELEASE}")
            .put("application_version", BuildConfig.VERSION_NAME)

    private suspend fun fetchMe(): TelegramUser? {
        return try {
            val user = requireEngine().send(JSONObject().put("@type", "getMe"))
            usersById[user.optLong("id")] = user
            mapUser(user)
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun refreshStories() {
        val own = StoryItem(
            id = 0,
            authorName = "My story",
            isOwn = true,
            hasUnseen = false,
            previewEmoji = "＋",
            caption = "Add"
        )
        val items = mutableListOf(own)
        try {
            // Prefer active stories from main list chats
            chatsById.values.take(30).forEach { chat ->
                val chatId = chat.optLong("id")
                try {
                    val active = requireEngine().send(
                        JSONObject()
                            .put("@type", "getChatActiveStories")
                            .put("chat_id", chatId)
                    )
                    val stories = active.optJSONObject("stories")
                        ?.optJSONArray("stories")
                        ?: return@forEach
                    if (stories.length() == 0) return@forEach
                    val first = stories.optJSONObject(0) ?: return@forEach
                    val storyId = first.optInt("id")
                    items += StoryItem(
                        id = (chatId shl 16) + storyId,
                        authorName = chat.optString("title").ifBlank { "Story" },
                        hasUnseen = !(active.optJSONObject("stories")?.optBoolean("max_read_story_id") ?: false),
                        avatarColor = chatId,
                        previewEmoji = "✨",
                        caption = chat.optString("title")
                    )
                } catch (_: Throwable) {
                    // chat may not support stories
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "stories: ${t.message}")
        }
        _stories.value = items.distinctBy { it.id }.take(20)
    }

    private suspend fun publishChats() = chatMutex.withLock {
        val list = chatsById.values
            .map { toPreview(it) }
            .sortedWith(
                compareByDescending<ChatPreview> { chatOrder[it.id] ?: 0L }
                    .thenByDescending { it.id }
            )
        _chats.value = list
    }

    private fun applyPositions(chatId: Long, positions: JSONArray) {
        for (i in 0 until positions.length()) {
            applyPosition(chatId, positions.optJSONObject(i) ?: continue)
        }
    }

    private fun applyPosition(chatId: Long, position: JSONObject) {
        val listType = position.optJSONObject("list")?.optString("@type")
        if (listType != null && listType != "chatListMain") return
        val order = position.optLong("order")
        if (order == 0L) {
            chatOrder.remove(chatId)
        } else {
            chatOrder[chatId] = order
        }
    }

    private fun toPreview(chat: JSONObject): ChatPreview {
        val id = chat.optLong("id")
        val last = chat.optJSONObject("last_message")
        val type = chat.optJSONObject("type")?.optString("@type").orEmpty()
        val notif = chat.optJSONObject("notification_settings")
        return ChatPreview(
            id = id,
            title = chat.optString("title").ifBlank { "Chat" },
            lastMessage = previewText(last),
            timestampLabel = formatTime(last?.optInt("date") ?: 0),
            unreadCount = chat.optInt("unread_count"),
            isPinned = (chatOrder[id] ?: 0L) >= PINNED_ORDER_THRESHOLD,
            isMuted = notif?.optInt("mute_for", 0)?.let { it > 0 } ?: false,
            isOnline = false,
            isChannel = chat.optBoolean("is_channel") || type.contains("channel", ignoreCase = true),
            isGroup = type == "chatTypeBasicGroup" || type == "chatTypeSupergroup",
            avatarColor = id,
            hasUnreadMention = chat.optInt("unread_mention_count") > 0
        )
    }

    private fun statusLabel(chat: JSONObject?): String? {
        chat ?: return null
        return when (chat.optJSONObject("type")?.optString("@type")) {
            "chatTypePrivate" -> "private chat"
            "chatTypeBasicGroup", "chatTypeSupergroup" -> "group"
            else -> null
        }
    }

    private fun previewText(message: JSONObject?): String {
        message ?: return ""
        val content = message.optJSONObject("content") ?: return ""
        return when (content.optString("@type")) {
            "messageText" -> content.optJSONObject("text")?.optString("text").orEmpty()
            "messagePhoto" -> "🖼 Photo"
            "messageVideo" -> "🎬 Video"
            "messageDocument" -> "📎 ${content.optJSONObject("document")?.optString("file_name") ?: "File"}"
            "messageVoiceNote" -> "🎤 Voice"
            "messageSticker" -> "Sticker"
            else -> content.optString("@type").removePrefix("message")
        }
    }

    private fun parseMessages(chatId: Long, array: JSONArray?): List<ChatMessage> {
        if (array == null) return emptyList()
        val out = ArrayList<ChatMessage>(array.length())
        for (i in 0 until array.length()) {
            val msg = array.optJSONObject(i) ?: continue
            out += mapMessage(chatId, msg)
        }
        // TDLib returns newest first; UI expects chronological
        return resolveReplies(out.asReversed())
    }

    /**
     * Fills in the text and author of quoted messages from the same window.
     *
     * TDLib puts only an id in reply_to, so the quote has to be looked up.
     * Anything referring outside the loaded window stays unresolved and the
     * bubble shows a neutral placeholder — fetching each one separately would
     * mean a request per reply on every chat open.
     */
    private fun resolveReplies(messages: List<ChatMessage>): List<ChatMessage> {
        if (messages.none { it.replyToId != null }) return messages
        val byId = messages.associateBy { it.id }
        return messages.map { message ->
            val target = message.replyToId?.let { byId[it] } ?: return@map message
            message.copy(
                // A quote the sender chose wins over the original's text.
                replyToText = message.replyToText ?: target.text,
                replyToSender = target.senderName
            )
        }
    }

    private fun mapMessage(chatId: Long, message: JSONObject): ChatMessage {
        val content = message.optJSONObject("content")
        val type = content?.optString("@type").orEmpty()
        val text = when (type) {
            "messageText" -> content?.optJSONObject("text")?.optString("text").orEmpty()
            "messagePhoto" -> content?.optJSONObject("caption")?.optString("text").orEmpty().ifBlank { "Photo" }
            "messageDocument" -> content?.optJSONObject("caption")?.optString("text").orEmpty()
                .ifBlank { content?.optJSONObject("document")?.optString("file_name").orEmpty() }
            else -> previewText(message)
        }
        val contentType = when (type) {
            "messagePhoto" -> MessageContentType.Photo
            "messageVideo" -> MessageContentType.Video
            "messageDocument" -> MessageContentType.Document
            "messageVoiceNote" -> MessageContentType.Voice
            "messageSticker" -> MessageContentType.Sticker
            else -> MessageContentType.Text
        }
        val senderId = message.optJSONObject("sender_id")?.optLong("user_id")
        val sender = senderId?.let { usersById[it] }
        return ChatMessage(
            id = message.optLong("id"),
            chatId = chatId,
            text = text,
            isOutgoing = message.optBoolean("is_outgoing"),
            timeLabel = formatTime(message.optInt("date")),
            date = message.optInt("date").toLong(),
            senderName = sender?.let { mapUser(it).displayName },
            senderId = senderId,
            canBeEdited = message.optBoolean("can_be_edited"),
            canBeDeletedForSelf = message.optBoolean("can_be_deleted_only_for_self"),
            canBeDeletedForEveryone =
                message.optBoolean("can_be_deleted_for_all_users"),
            isEdited = message.optInt("edit_date") > 0,
            replyToId = message.optJSONObject("reply_to")
                ?.takeIf { it.optString("@type") == "messageReplyToMessage" }
                ?.optLong("message_id")
                ?.takeIf { it != 0L },
            // Newer TDLib carries the fragment the sender highlighted; when it
            // is there it is more accurate than the whole original message.
            replyToText = message.optJSONObject("reply_to")
                ?.optJSONObject("quote")
                ?.optString("text")
                ?.takeIf { it.isNotBlank() },
            isRead = !message.optBoolean("is_outgoing") || message.optInt("sending_state") == 0,
            contentType = contentType,
            fileName = content?.optJSONObject("document")?.optString("file_name"),
            fileSizeLabel = null,
            mediaEmoji = when (contentType) {
                MessageContentType.Photo -> "🖼️"
                MessageContentType.Document -> "📎"
                else -> null
            }
        )
    }

    private fun mapUser(user: JSONObject): TelegramUser =
        TelegramUser(
            id = user.optLong("id"),
            firstName = user.optString("first_name"),
            lastName = user.optString("last_name"),
            username = user.optJSONArray("usernames")
                ?.optJSONObject(0)
                ?.optString("username")
                ?: user.optString("username").ifBlank { null },
            phoneNumber = user.optString("phone_number").ifBlank { null },
            avatarColor = user.optLong("id"),
            isPremium = user.optBoolean("is_premium")
        )

    private fun formatTime(epochSec: Int): String {
        if (epochSec <= 0) return ""
        val date = Date(epochSec * 1000L)
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        return fmt.format(date)
    }

    private suspend fun copyUriToCache(uriString: String, preferredName: String): String =
        withContext(Dispatchers.IO) {
            val uri = Uri.parse(uriString)
            val name = queryDisplayName(uri) ?: preferredName
            val safe = name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val out = File(uploadCache, "${System.currentTimeMillis()}_$safe")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(out).use { output -> input.copyTo(output) }
            } ?: error("Cannot open $uriString")
            out.absolutePath
        }

    private fun queryDisplayName(uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) return c.getString(idx)
        }
        return null
    }

    private fun requireEngine(): TdJsonEngine =
        engine ?: error("TDLib not started")

    private suspend fun awaitReady() {
        if (_authState.value.state == AuthState.Ready) return
        readySignal.await()
    }

    companion object {
        private const val TAG = "TdLibTelegramClient"
        // TDLib uses very large order values for pinned chats
        private const val PINNED_ORDER_THRESHOLD = 1L shl 50
    }
}
