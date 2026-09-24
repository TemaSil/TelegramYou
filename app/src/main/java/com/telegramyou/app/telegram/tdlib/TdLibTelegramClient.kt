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
import com.telegramyou.app.telegram.model.ChatFolder
import com.telegramyou.app.telegram.model.ChatPositions
import com.telegramyou.app.telegram.model.MessageUpdate
import com.telegramyou.app.ui.chat.applying
import com.telegramyou.app.ui.format.Presence
import com.telegramyou.app.ui.format.chatListTimeLabel
import com.telegramyou.app.ui.format.isOnline
import com.telegramyou.app.ui.format.memberCountLabel
import com.telegramyou.app.ui.format.presenceLabel
import com.telegramyou.app.telegram.model.ChatMessage
import com.telegramyou.app.telegram.model.MessageHit
import com.telegramyou.app.telegram.model.MessageReaction
import com.telegramyou.app.telegram.model.packWaveform
import com.telegramyou.app.telegram.model.unpackWaveform
import com.telegramyou.app.telegram.model.ChatPreview
import com.telegramyou.app.telegram.model.LinkPreview
import com.telegramyou.app.telegram.model.MessageContentType
import com.telegramyou.app.telegram.model.StoryItem
import com.telegramyou.app.telegram.model.TelegramUser
import com.telegramyou.app.telegram.model.InviteLinkPreview
import com.telegramyou.app.telegram.model.VideoContent
import com.telegramyou.app.ui.media.FileTransfer
// Aliased: this class has a toggleReaction of its own, with a different job.
import com.telegramyou.app.telegram.model.toggleReaction as applyReaction
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
import java.time.ZoneId
import java.io.FileOutputStream
import java.util.Base64
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

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

    /**
     * Where every update is handled, one at a time and in the order TDLib
     * sent them.
     *
     * The chat objects below are `JSONObject`s, which are not safe to change
     * on one thread while another reads them — and updates arrive on TDLib's
     * receiver thread while the chat list is built on another. Handling each
     * update, and building the list, on this one thread is what keeps the two
     * from meeting halfway through a write.
     */
    private val updateDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "tdlib-updates").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    private val chatsById = ConcurrentHashMap<Long, JSONObject>()

    /**
     * Which list each chat is in, whether it is pinned there, and which
     * folders hold it — see ChatPositions in :core, where the rules are
     * tested.
     */
    private val positions = ChatPositions()
    private val usersById = ConcurrentHashMap<Long, JSONObject>()

    /**
     * Basic groups and supergroups by their own ids, as TDLib announces them.
     * A chat only names its group; the member count and whether a supergroup
     * is really a channel live on these.
     */
    private val basicGroups = ConcurrentHashMap<Long, JSONObject>()
    private val supergroups = ConcurrentHashMap<Long, JSONObject>()
    private val messagesByChat = ConcurrentHashMap<Long, MutableList<ChatMessage>>()

    /**
     * Stories by the id this client gave them.
     *
     * A story is named by its poster's chat and its own number, and the
     * screens want one Long. The pair used to be packed into one as
     * `chatId shl 16 + storyId`, which broke on the 65,536th story; a table
     * cannot run out.
     */
    private val storyKeys = ConcurrentHashMap<Long, Pair<Long, Int>>()
    private val storyKeySeq = AtomicLong(0)

    /** Each chat's `chatActiveStories`, as `updateChatActiveStories` last said. */
    private val activeStories = ConcurrentHashMap<Long, JSONObject>()

    private val _authState = MutableStateFlow(
        AuthUiState(state = AuthState.Bootstrapping, isLoading = true)
    )
    override val authState: StateFlow<AuthUiState> = _authState.asStateFlow()

    private val _chats = MutableStateFlow<List<ChatPreview>>(emptyList())
    override val chats: StateFlow<List<ChatPreview>> = _chats.asStateFlow()

    private val _fileTransfers = MutableStateFlow<Map<Int, FileTransfer>>(emptyMap())
    override val fileTransfers: StateFlow<Map<Int, FileTransfer>> =
        _fileTransfers.asStateFlow()

    private val _folders = MutableStateFlow<List<ChatFolder>>(emptyList())
    override val folders: StateFlow<List<ChatFolder>> = _folders.asStateFlow()

    // extraBufferCapacity so emitting never suspends: this is written from
    // the TDLib update callback, which must return promptly — blocking it
    // stalls every other update behind this one.
    private val _incomingMessages = MutableSharedFlow<ChatMessage>(extraBufferCapacity = 64)
    override val incomingMessages: SharedFlow<ChatMessage> = _incomingMessages.asSharedFlow()

    // Same buffering, for the same reason: emitted from the update handler.
    private val _messageUpdates = MutableSharedFlow<MessageUpdate>(extraBufferCapacity = 256)
    override val messageUpdates: SharedFlow<MessageUpdate> = _messageUpdates.asSharedFlow()

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
            val eng = TdJsonEngine { update ->
                // Off TDLib's receiver thread straight away: it must get back
                // to receiving, and everything that changes the chat objects
                // runs on updateDispatcher — see there.
                scope.launch(updateDispatcher) { handleUpdate(update) }
            }
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

    /**
     * Loads the main list, the archive and every folder, then publishes.
     *
     * Each load may answer 404, and for all three that is TDLib saying "you
     * already have everything" — which is what every refresh after the first
     * one hears. So a failure is per list and never stops the rest: it used
     * to be one try around the lot, and the second pull-to-refresh skipped
     * the folders, the list and the stories alike.
     */
    override suspend fun refreshChats() {
        awaitReady()
        loadChatList(JSONObject().put("@type", "chatListMain"))
        loadChatList(JSONObject().put("@type", "chatListArchive"))
        loadFolderChats()
        withContext(updateDispatcher) { publishChats() }
        refreshStories()
    }

    /**
     * One `loadChats`, answering whether the list may have more. 404 is TDLib
     * saying it has nothing left to load — the ordinary end of a list, not a
     * failure.
     */
    private suspend fun loadChatList(list: JSONObject): Boolean {
        val engine = this.engine ?: return false
        return try {
            engine.send(
                JSONObject()
                    .put("@type", "loadChats")
                    .put("chat_list", list)
                    .put("limit", CHAT_PAGE)
            )
            true
        } catch (e: TdLibException) {
            Log.d(TAG, "loadChats(${list.optString("@type")}): ${e.message}")
            false
        }
    }

    override suspend fun loadMoreChats(folderId: Int?): Boolean {
        awaitReady()
        val list = if (folderId == null) {
            JSONObject().put("@type", "chatListMain")
        } else {
            JSONObject().put("@type", "chatListFolder").put("chat_folder_id", folderId)
        }
        // The chats themselves arrive as updates and are published there.
        return loadChatList(list)
    }

    override suspend fun searchChats(query: String, limit: Int): List<ChatPreview> {
        if (query.isBlank()) return emptyList()
        awaitReady()
        // searchChatsOnServer, not searchChats: the local one only looks at
        // what has been loaded, so a conversation you have not scrolled to
        // would appear not to exist.
        val found = try {
            requireEngine().send(
                JSONObject()
                    .put("@type", "searchChatsOnServer")
                    .put("query", query)
                    .put("limit", limit)
            )
        } catch (e: TdLibException) {
            Log.w(TAG, "searchChatsOnServer: ${e.message}")
            return emptyList()
        }

        val ids = found.optJSONArray("chat_ids") ?: return emptyList()
        val out = ArrayList<ChatPreview>(ids.length())
        for (i in 0 until ids.length()) {
            val id = ids.optLong(i)
            // TDLib guarantees the chat itself is known once it has returned
            // the id, so this is a lookup rather than a request per result.
            val chat = chatsById[id] ?: continue
            out += toPreview(chat)
        }
        return out
    }

    override suspend fun searchChatMessages(
        chatId: Long,
        query: String,
        limit: Int
    ): List<ChatMessage> {
        if (query.isBlank()) return emptyList()
        awaitReady()
        val found = try {
            requireEngine().send(
                JSONObject()
                    .put("@type", "searchChatMessages")
                    .put("chat_id", chatId)
                    .put("query", query)
                    .put("limit", limit)
                    // Paging fields TDLib requires even for a first page.
                    // from_message_id 0 means "from the newest".
                    .put("from_message_id", 0)
                    .put("offset", 0)
            )
        } catch (e: TdLibException) {
            Log.w(TAG, "searchChatMessages: ${e.message}")
            return emptyList()
        }
        // parseMessages reverses into chronological order, which is what the
        // rest of this client returns and what the result list renders.
        return parseMessages(chatId, found.optJSONArray("messages"))
    }

    override suspend fun chatMedia(chatId: Long, limit: Int): List<ChatMessage> {
        awaitReady()
        val found = try {
            requireEngine().send(
                JSONObject()
                    .put("@type", "searchChatMessages")
                    .put("chat_id", chatId)
                    // Empty, which searchChatMessages allows and which is how
                    // "everything of this kind" is asked for. The filter does
                    // the selecting.
                    .put("query", "")
                    .put(
                        "filter",
                        JSONObject().put("@type", "searchMessagesFilterPhotoAndVideo")
                    )
                    .put("limit", limit)
                    .put("from_message_id", 0)
                    .put("offset", 0)
            )
        } catch (e: TdLibException) {
            Log.w(TAG, "chatMedia: ${e.message}")
            return emptyList()
        }
        // Newest first, which is the order a grid of media is read in — and
        // the opposite of the conversation, where the newest is at the bottom.
        return parseMessages(chatId, found.optJSONArray("messages")).asReversed()
    }

    override suspend fun searchMessages(query: String, limit: Int): List<MessageHit> {
        if (query.isBlank()) return emptyList()
        awaitReady()
        val found = try {
            requireEngine().send(
                JSONObject()
                    .put("@type", "searchMessages")
                    .put("query", query)
                    .put("limit", limit)
                    // Paging fields TDLib requires even for a first page.
                    .put("offset", "")
            )
        } catch (e: TdLibException) {
            Log.w(TAG, "searchMessages: ${e.message}")
            return emptyList()
        }

        val array = found.optJSONArray("messages") ?: return emptyList()
        val out = ArrayList<MessageHit>(array.length())
        for (i in 0 until array.length()) {
            val raw = array.optJSONObject(i) ?: continue
            val chatId = raw.optLong("chat_id")
            // A hit in a chat we know nothing about cannot be shown usefully:
            // the row needs a title and an avatar colour, and inventing them
            // would be worse than leaving the hit out.
            val chat = chatsById[chatId] ?: continue
            out += MessageHit(
                chat = toPreview(chat),
                message = mapMessage(chatId, raw)
            )
        }
        return out
    }

    override suspend fun contacts(): List<TelegramUser> {
        awaitReady()
        return try {
            val ids = requireEngine()
                .send(JSONObject().put("@type", "getContacts"))
                .optJSONArray("user_ids")
                ?: return emptyList()
            val users = mutableListOf<TelegramUser>()
            for (index in 0 until minOf(ids.length(), CONTACT_LIMIT)) {
                val userId = ids.optLong(index)
                val raw = usersById[userId] ?: try {
                    requireEngine()
                        .send(JSONObject().put("@type", "getUser").put("user_id", userId))
                        .also { usersById[userId] = it }
                } catch (_: Throwable) {
                    continue
                }
                users += mapUser(raw)
            }
            // By name, because a picker is read rather than scanned for
            // recency — the list of who you have spoken to lately is the chat
            // list, and it is the thing this button is an alternative to.
            users.sortedBy { it.displayName.lowercase() }
        } catch (e: Throwable) {
            Log.w(TAG, "contacts: ${e.message}")
            emptyList()
        }
    }

    override suspend fun openPrivateChat(userId: Long): Long {
        awaitReady()
        // force = false lets Telegram return the existing chat rather than
        // making a second one; creating it is what happens when there is none.
        val chat = requireEngine().send(
            JSONObject()
                .put("@type", "createPrivateChat")
                .put("user_id", userId)
                .put("force", false)
        )
        val chatId = chat.optLong("id")
        chatsById[chatId] = chat
        refreshChats()
        return chatId
    }

    override suspend fun setChatArchived(chatId: Long, archived: Boolean) {
        awaitReady()
        // addChatToList, not a flag: the archive is a chat list like the main
        // one, and moving between them is the only operation there is.
        requireEngine().send(
            JSONObject()
                .put("@type", "addChatToList")
                .put("chat_id", chatId)
                .put(
                    "chat_list",
                    JSONObject().put(
                        "@type",
                        if (archived) "chatListArchive" else "chatListMain"
                    )
                )
        )
        refreshChats()
    }

    override suspend fun setChatPinned(chatId: Long, pinned: Boolean) {
        awaitReady()
        requireEngine().send(
            JSONObject()
                .put("@type", "toggleChatIsPinned")
                // Pinned within the list it is in: an archived chat pinned in
                // the main list would be refused, since it is not there.
                .put(
                    "chat_list",
                    JSONObject().put(
                        "@type",
                        if (positions.isArchived(chatId)) "chatListArchive" else "chatListMain"
                    )
                )
                .put("chat_id", chatId)
                .put("is_pinned", pinned)
        )
        refreshChats()
    }

    override suspend fun markChatRead(chatId: Long) {
        awaitReady()
        // TDLib has no "mark this chat read". The unread count comes down when
        // its messages are viewed, and force_read is what makes that count
        // while the chat is not open on screen — which is the whole point of
        // clearing a badge from the list.
        //
        // Viewing the last message is enough: Telegram reads it as everything
        // up to there. Without one there is nothing to view and nothing to do,
        // which is the state of a chat that has never had a message.
        val lastMessageId = chatsById[chatId]
            ?.optJSONObject("last_message")
            ?.optLong("id")
            ?.takeIf { it != 0L }
            ?: return
        requireEngine().send(
            JSONObject()
                .put("@type", "viewMessages")
                .put("chat_id", chatId)
                .put("message_ids", JSONArray().put(lastMessageId))
                .put("force_read", true)
        )
        refreshChats()
    }

    /**
     * Mutes or unmutes, and changes nothing else.
     *
     * `setChatNotificationSettings` replaces the chat's settings whole, and
     * a field left out of the object is read as false or zero — so sending
     * only the two mute fields also switched off the chat's sound and its
     * message previews. The chat's own current settings are the starting
     * point instead, which also keeps this right for whatever fields the
     * TDLib build behind the `.so` has that this code has never heard of.
     */
    override suspend fun setChatMuted(chatId: Long, muted: Boolean) {
        awaitReady()
        val settings = chatsById[chatId]?.optJSONObject("notification_settings")
            ?.let { JSONObject(it.toString()) }
            ?: defaultNotificationSettings()
        settings
            // use_default_mute_for false is what makes mute_for this chat's
            // own setting rather than the scope's; without it the value
            // below is ignored.
            .put("use_default_mute_for", false)
            // Telegram measures a mute in seconds. Its "forever" is a very
            // large number rather than a flag, and this is the value its own
            // clients use.
            .put("mute_for", if (muted) MUTE_FOREVER_SECONDS else 0)
        requireEngine().send(
            JSONObject()
                .put("@type", "setChatNotificationSettings")
                .put("chat_id", chatId)
                .put("notification_settings", settings)
        )
    }

    /** Every setting following the account's defaults, for a chat not yet seen. */
    private fun defaultNotificationSettings(): JSONObject = JSONObject()
        .put("@type", "chatNotificationSettings")
        .put("use_default_sound", true)
        .put("use_default_show_preview", true)
        .put("use_default_mute_stories", true)
        .put("use_default_story_sound", true)
        .put("use_default_show_story_poster", true)
        .put("use_default_disable_pinned_message_notifications", true)
        .put("use_default_disable_mention_notifications", true)

    /**
     * How many state holders have this chat open; see [retainChat]. TDLib's
     * own openChat and closeChat are a switch rather than a count, so the
     * count is kept here and only its first and last steps reach TDLib.
     */
    private val openCounts = ConcurrentHashMap<Long, Int>()

    override fun retainChat(chatId: Long) {
        if (openCounts.merge(chatId, 1, Int::plus) != 1) return
        switchChat(chatId, "openChat")
    }

    override fun releaseChat(chatId: Long) {
        val left = openCounts.compute(chatId) { _, count -> ((count ?: 1) - 1).takeIf { it > 0 } }
        if (left != null) return
        switchChat(chatId, "closeChat")
    }

    private fun switchChat(chatId: Long, type: String) {
        scope.launch {
            try {
                awaitReady()
                requireEngine().send(JSONObject().put("@type", type).put("chat_id", chatId))
            } catch (e: Exception) {
                Log.d(TAG, "$type: ${e.message}")
            }
        }
    }

    override suspend fun openChat(chatId: Long): ChatDetail {
        awaitReady()
        val mapped = parseMessages(chatId, firstPage(chatId))
        messagesByChat[chatId] = mapped.toMutableList()
        val chat = chatsById[chatId]
        val preview = chat?.let { toPreview(it) }
            ?: ChatPreview(chatId, "Chat $chatId", "", "")
        return ChatDetail(
            chat = preview,
            messages = mapped,
            memberCountLabel = statusLabel(chat),
            isTyping = false,
            pinnedMessage = pinnedMessage(chatId),
            members = groupMembers(chat)
        )
    }

    /**
     * The newest [HISTORY_PAGE] messages, newest first, as TDLib lists them.
     *
     * Asked for more than once, because TDLib answers from what it has to
     * hand: the first `getChatHistory` of a chat opened cold often returns a
     * single message, and a conversation opening with one line in it reads
     * as empty. Each further request starts below the oldest message so far,
     * and it stops at a full page, at the end of the history, or after
     * [HISTORY_ATTEMPTS] tries — scrolling up pages in the rest either way.
     */
    private suspend fun firstPage(chatId: Long): JSONArray {
        val engine = requireEngine()
        val page = JSONArray()
        var from = 0L
        repeat(HISTORY_ATTEMPTS) {
            val answer = engine.send(
                JSONObject()
                    .put("@type", "getChatHistory")
                    .put("chat_id", chatId)
                    .put("from_message_id", from)
                    .put("offset", 0)
                    .put("limit", HISTORY_PAGE - page.length())
                    .put("only_local", false)
            ).optJSONArray("messages")
            if (answer == null || answer.length() == 0) return page
            for (index in 0 until answer.length()) page.put(answer.get(index))
            if (page.length() >= HISTORY_PAGE) return page
            from = answer.optJSONObject(answer.length() - 1)?.optLong("id") ?: return page
        }
        return page
    }

    /**
     * The chat's primary invite link.
     *
     * Read from the same full-info objects the member list comes from, and
     * null for everything else — a private chat has no link, and neither
     * does a group that has not told this account about one. TDLib only
     * fills `invite_link` in for members who may actually invite, so null is
     * the ordinary answer rather than a failure.
     *
     * Not `createChatInviteLink`: that makes a new link, and a screen that
     * silently mints one because it wanted something to show would be
     * handing out an invitation nobody asked to create.
     */
    override suspend fun chatInviteLink(chatId: Long): String? {
        awaitReady()
        val type = chatsById[chatId]?.optJSONObject("type") ?: return null
        return try {
            val full = when (type.optString("@type")) {
                "chatTypeBasicGroup" -> requireEngine().send(
                    JSONObject()
                        .put("@type", "getBasicGroupFullInfo")
                        .put("basic_group_id", type.optLong("basic_group_id"))
                )
                "chatTypeSupergroup" -> requireEngine().send(
                    JSONObject()
                        .put("@type", "getSupergroupFullInfo")
                        .put("supergroup_id", type.optLong("supergroup_id"))
                )
                else -> return null
            }
            full.optJSONObject("invite_link")?.optString("invite_link")?.ifBlank { null }
        } catch (e: Throwable) {
            Log.w(TAG, "chatInviteLink: ${e.message}")
            null
        }
    }

    /**
     * `createNewBasicGroupChat`, whose answer changed shape between TDLib
     * versions: newer builds return `createdBasicGroupChat` with a `chat_id`
     * (and the members it could not add), older ones return the `chat`
     * itself. Which one arrives depends on the `.so`, not on anything here.
     */
    override suspend fun createGroup(title: String, memberIds: List<Long>): Long {
        awaitReady()
        val answer = requireEngine().send(
            JSONObject()
                .put("@type", "createNewBasicGroupChat")
                .put("user_ids", JSONArray(memberIds))
                .put("title", title.trim())
                .put("message_auto_delete_time", 0)
        )
        return answer.optLong("chat_id").takeIf { it != 0L } ?: answer.optLong("id")
    }

    /**
     * A channel is a supergroup with `is_channel` set; TDLib has no separate
     * call. Fields this does not send — forum, location, auto-delete — keep
     * the server's defaults, which are what a new channel should start with.
     */
    override suspend fun createChannel(title: String, description: String): Long {
        awaitReady()
        val chat = requireEngine().send(
            JSONObject()
                .put("@type", "createNewSupergroupChat")
                .put("title", title.trim())
                .put("is_channel", true)
                .put("description", description.trim())
        )
        return chat.optLong("id")
    }

    /**
     * `checkChatInviteLink`, read two ways for the same reason as folder
     * titles: newer TDLib says what the chat is through a `type` object,
     * older through an `is_channel` flag.
     */
    override suspend fun checkInviteLink(link: String): InviteLinkPreview? {
        awaitReady()
        return try {
            val info = requireEngine().send(
                JSONObject()
                    .put("@type", "checkChatInviteLink")
                    .put("invite_link", link)
            )
            val type = info.optJSONObject("type")?.optString("@type").orEmpty()
            InviteLinkPreview(
                link = link,
                title = info.optString("title").ifBlank { "Chat" },
                memberCount = info.optInt("member_count"),
                isChannel = type == "inviteLinkChatTypeChannel" ||
                    info.optBoolean("is_channel"),
                // Zero when this account is not in it, which is the usual case.
                joinedChatId = info.optLong("chat_id").takeIf { it != 0L }
            )
        } catch (e: TdLibException) {
            Log.d(TAG, "checkChatInviteLink: ${e.message}")
            null
        }
    }

    override suspend fun joinByInviteLink(link: String): Long {
        awaitReady()
        val chat = requireEngine().send(
            JSONObject()
                .put("@type", "joinChatByInviteLink")
                .put("invite_link", link)
        )
        return chat.optLong("id")
    }

    override suspend fun leaveChat(chatId: Long) {
        awaitReady()
        requireEngine().send(JSONObject().put("@type", "leaveChat").put("chat_id", chatId))
        // Nothing is removed here. Leaving takes the chat out of the main
        // list, and TDLib says so with a position update — the same one that
        // would arrive if this happened on another device.
    }

    /**
     * Who is in a group, from the server rather than from who has spoken.
     *
     * TDLib splits this by chat type and there is no call that spans them. A
     * basic group carries its members inside `basicGroupFullInfo`; a
     * supergroup has too many to inline, so they are paged with
     * `getSupergroupMembers`. A channel has subscribers rather than members
     * and does not answer at all, which is correct — a header listing faces
     * for a broadcast would be inventing a room that is not there.
     *
     * Capped at [MEMBER_LIMIT]. The cluster draws a handful and says how many
     * more; fetching two hundred to draw five is a round trip spent on
     * nothing.
     *
     * Every failure here is swallowed to an empty list on purpose. This is a
     * decoration on a header: a group that will not say who is in it should
     * still open.
     */
    private suspend fun groupMembers(chat: JSONObject?): List<TelegramUser> {
        val type = chat?.optJSONObject("type") ?: return emptyList()
        return try {
            when (type.optString("@type")) {
                "chatTypeBasicGroup" -> {
                    val full = requireEngine().send(
                        JSONObject()
                            .put("@type", "getBasicGroupFullInfo")
                            .put("basic_group_id", type.optLong("basic_group_id"))
                    )
                    membersFrom(full.optJSONArray("members"))
                }
                "chatTypeSupergroup" -> {
                    // is_channel is the difference between a group and a
                    // broadcast, and the same chat type carries both.
                    if (type.optBoolean("is_channel")) return emptyList()
                    val page = requireEngine().send(
                        JSONObject()
                            .put("@type", "getSupergroupMembers")
                            .put("supergroup_id", type.optLong("supergroup_id"))
                            .put("offset", 0)
                            .put("limit", MEMBER_LIMIT)
                    )
                    membersFrom(page.optJSONArray("members"))
                }
                else -> emptyList()
            }
        } catch (e: Throwable) {
            Log.w(TAG, "groupMembers: ${e.message}")
            emptyList()
        }
    }

    /**
     * Turns `chatMember` entries into users.
     *
     * A member is a `MessageSender`, which is a user or a chat — a group can
     * have another chat in it. Only the users are drawn, because a face is
     * what the cluster is made of.
     */
    private suspend fun membersFrom(members: JSONArray?): List<TelegramUser> {
        if (members == null) return emptyList()
        val users = mutableListOf<TelegramUser>()
        for (index in 0 until minOf(members.length(), MEMBER_LIMIT)) {
            val sender = members.optJSONObject(index)?.optJSONObject("member_id") ?: continue
            if (sender.optString("@type") != "messageSenderUser") continue
            val userId = sender.optLong("user_id")
            val cached = usersById[userId]
            val raw = cached ?: try {
                requireEngine()
                    .send(JSONObject().put("@type", "getUser").put("user_id", userId))
                    .also { usersById[userId] = it }
            } catch (_: Throwable) {
                continue
            }
            users += mapUser(raw)
        }
        return users
    }

    /**
     * The chat's pinned message — the newest one, which is what the bar shows.
     *
     * `getChatPinnedMessage`, because a `chat` carries no pinned id: this
     * used to read a `pinned_message_id` field TDLib stopped sending when
     * chats gained several pins, and so the bar never appeared. What is
     * pinned is usually old, so it is rarely in the window just loaded
     * anyway.
     *
     * A chat with nothing pinned answers 404, the ordinary case; any failure
     * is a missing bar, never a chat that will not open.
     */
    private suspend fun pinnedMessage(chatId: Long): ChatMessage? = try {
        val raw = requireEngine().send(
            JSONObject()
                .put("@type", "getChatPinnedMessage")
                .put("chat_id", chatId)
        )
        mapMessage(chatId, raw)
    } catch (e: TdLibException) {
        Log.d(TAG, "pinnedMessage: ${e.message}")
        null
    }

    override suspend fun loadOlderMessages(
        chatId: Long,
        beforeMessageId: Long,
        limit: Int
    ): List<ChatMessage> {
        awaitReady()
        // from_message_id is exclusive and offset 0 means "older than this",
        // so this returns the page immediately before what is on screen.
        val history = requireEngine().send(
            JSONObject()
                .put("@type", "getChatHistory")
                .put("chat_id", chatId)
                .put("from_message_id", beforeMessageId)
                .put("offset", 0)
                .put("limit", limit)
                // only_local = false: an empty answer has to mean the end of
                // the history, not merely the end of what happens to be
                // cached, or the list would stop short and look complete.
                .put("only_local", false)
        )
        val older = parseMessages(chatId, history.optJSONArray("messages"))
        if (older.isNotEmpty()) {
            // Kept in the same window the reply lookup reads, so quotes of
            // newly loaded messages resolve instead of showing a placeholder.
            val known = messagesByChat.getOrPut(chatId) { mutableListOf() }
            known.addAll(0, older)
        }
        return older
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
            // Several photos go as an album, which is what Telegram draws as
            // one grid rather than a column of separate bubbles. The caption
            // and the quote belong to the first; each photo used to carry
            // both, so a three-photo send said the same words three times.
            is AttachmentDraft.Photos -> {
                val paths = draft.uris.map { uri ->
                    copyUriToCache(uri, "photo_${System.currentTimeMillis()}.jpg")
                }
                if (paths.size == 1) {
                    sendLocalFile(chatId, paths.single(), caption, photo = true, replyToId = replyToId)
                } else {
                    sendPhotoAlbums(chatId, paths, caption, replyToId)
                }
            }
            // Files one by one, with the caption and the quote on the first
            // only — the same rule, without the album.
            is AttachmentDraft.Files ->
                draft.uris.zip(draft.names).forEachIndexed { index, (uri, name) ->
                    val path = copyUriToCache(uri, name)
                    sendLocalFile(chatId, path, caption.takeIf { index == 0 }.orEmpty(),
                        photo = false, replyToId = replyToId.takeIf { index == 0 })
                }
            // No copy here: a recording is already a file this app wrote, in
            // this app's own cache. Everything else arrives as a Uri from
            // somewhere else and has to be resolved first.
            is AttachmentDraft.Voice -> sendVoiceNote(
                chatId = chatId,
                path = draft.path,
                durationSeconds = draft.durationSeconds,
                waveform = draft.waveform,
                caption = caption,
                replyToId = replyToId
            )
        }
    }

    /**
     * `sendMessageAlbum`, in groups of ten — the most Telegram puts in one.
     * Only the very first photo carries the caption and answers the quote.
     */
    private suspend fun sendPhotoAlbums(
        chatId: Long,
        paths: List<String>,
        caption: String,
        replyToId: Long?
    ) {
        paths.chunked(ALBUM_LIMIT).forEachIndexed { chunkIndex, chunk ->
            val contents = JSONArray()
            chunk.forEachIndexed { index, path ->
                val first = chunkIndex == 0 && index == 0
                contents.put(
                    JSONObject()
                        .put("@type", "inputMessagePhoto")
                        .put("photo", JSONObject().put("@type", "inputFileLocal").put("path", path))
                        .put(
                            "caption",
                            JSONObject()
                                .put("@type", "formattedText")
                                .put("text", if (first) caption else "")
                        )
                )
            }
            requireEngine().send(
                JSONObject()
                    .put("@type", "sendMessageAlbum")
                    .put("chat_id", chatId)
                    .withReplyTo(replyToId.takeIf { chunkIndex == 0 })
                    .put("input_message_contents", contents)
            )
        }
    }

    private suspend fun sendVoiceNote(
        chatId: Long,
        path: String,
        durationSeconds: Int,
        waveform: List<Int>,
        caption: String,
        replyToId: Long?
    ) {
        val content = JSONObject()
            .put("@type", "inputMessageVoiceNote")
            .put("voice_note", JSONObject().put("@type", "inputFileLocal").put("path", path))
            .put("duration", durationSeconds)
            // The bar chart Telegram draws behind a voice message: 5-bit
            // samples packed into bytes and base64'd. Measured while
            // recording, so every client that opens this message sees the
            // shape of what was actually said.
            .put("waveform", Base64.getEncoder().encodeToString(packWaveform(waveform)))
            .put(
                "caption",
                JSONObject().put("@type", "formattedText").put("text", caption)
            )
        requireEngine().send(
            JSONObject()
                .put("@type", "sendMessage")
                .put("chat_id", chatId)
                .put("input_message_content", content)
                .withReplyTo(replyToId)
        )
    }

    override suspend fun forwardMessages(
        fromChatId: Long,
        messageIds: List<Long>,
        toChatId: Long
    ) {
        if (messageIds.isEmpty()) return
        awaitReady()
        val ids = JSONArray().apply { messageIds.forEach { put(it) } }
        requireEngine().send(
            JSONObject()
                .put("@type", "forwardMessages")
                .put("chat_id", toChatId)
                .put("from_chat_id", fromChatId)
                .put("message_ids", ids)
                // The plain forward: the author's name travels with it, and
                // the copy is not presented as something we wrote.
                .put("send_copy", false)
                .put("remove_caption", false)
        )
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

    /**
     * Two TDLib calls behind one, because TDLib splits what the tap does not:
     * a reaction is added or removed, and which of the two this is depends on
     * what we already chose.
     *
     * The local copy is updated with the same arithmetic the screen used, so a
     * reload before updateMessageInteractionInfo arrives does not undo the tap
     * on screen.
     */
    override suspend fun toggleReaction(chatId: Long, messageId: Long, emoji: String) {
        awaitReady()
        val known = messagesByChat[chatId]?.firstOrNull { it.id == messageId }
        val chosen = known?.reactions?.any { it.emoji == emoji && it.isChosen } == true
        val reactionType = JSONObject()
            .put("@type", "reactionTypeEmoji")
            .put("emoji", emoji)

        requireEngine().send(
            JSONObject()
                .put("@type", if (chosen) "removeMessageReaction" else "addMessageReaction")
                .put("chat_id", chatId)
                .put("message_id", messageId)
                .put("reaction_type", reactionType)
                .apply {
                    if (!chosen) {
                        // Telegram shows a reaction to everyone by default;
                        // is_big is the animated burst, which belongs to a
                        // long press we do not have yet.
                        put("is_big", false)
                        put("update_recent_reactions", true)
                    }
                }
        )

        messagesByChat[chatId]?.let { bucket ->
            val index = bucket.indexOfFirst { it.id == messageId }
            if (index != -1) {
                bucket[index] = bucket[index]
                    .copy(reactions = applyReaction(bucket[index].reactions, emoji))
            }
        }
    }

    /**
     * Read from the cached chat rather than asked for.
     *
     * A chat carries its own `available_reactions`: every emoji, a restricted
     * list, or nothing at all. Offering the full set in a group that permits
     * three would be a tap the server refuses for a reason this already knows.
     */
    override suspend fun availableReactions(chatId: Long): List<String> {
        awaitReady()
        val available = chatsById[chatId]?.optJSONObject("available_reactions")
            ?: return DEFAULT_REACTIONS
        return when (available.optString("@type")) {
            "chatAvailableReactionsAll" -> DEFAULT_REACTIONS
            "chatAvailableReactionsSome" -> {
                val types = available.optJSONArray("reactions") ?: return emptyList()
                (0 until types.length()).mapNotNull { index ->
                    types.optJSONObject(index)
                        ?.takeIf { it.optString("@type") == "reactionTypeEmoji" }
                        ?.optString("emoji")
                        ?.takeIf { it.isNotBlank() }
                }
            }
            // An unknown shape is not a licence to guess: a chat that permits
            // nothing and a chat this client cannot read are both "no chips".
            else -> emptyList()
        }
    }

    /**
     * Downloads a file and waits for it, then answers with its path.
     *
     * priority 32 is TDLib's "the user is looking at this"; synchronous true
     * makes the call return when the bytes are there rather than immediately
     * with a file that is still arriving, which is what a play button needs.
     */
    override suspend fun downloadFile(fileId: Int): String? {
        awaitReady()
        return try {
            val file = requireEngine().send(
                JSONObject()
                    .put("@type", "downloadFile")
                    .put("file_id", fileId)
                    .put("priority", 32)
                    .put("offset", 0)
                    .put("limit", 0)
                    .put("synchronous", true)
            )
            file.optJSONObject("local")
                ?.takeIf { it.optBoolean("is_downloading_completed") }
                ?.optString("path")
                ?.takeIf { it.isNotBlank() }
        } catch (e: TdLibException) {
            Log.w(TAG, "downloadFile($fileId): ${e.message}")
            null
        }
    }

    override suspend fun markStorySeen(storyId: Long) {
        _stories.update { list ->
            list.map { if (it.id == storyId) it.copy(hasUnseen = false) else it }
        }
        val (chatId, realStoryId) = storyKeys[storyId] ?: return
        try {
            // Opened and closed again straight away: openStory is what marks
            // it viewed, and a story left open keeps TDLib polling for it.
            val engine = requireEngine()
            engine.send(
                JSONObject()
                    .put("@type", "openStory")
                    .put("story_poster_chat_id", chatId)
                    .put("story_sender_chat_id", chatId)
                    .put("story_id", realStoryId)
            )
            engine.send(
                JSONObject()
                    .put("@type", "closeStory")
                    .put("story_poster_chat_id", chatId)
                    .put("story_sender_chat_id", chatId)
                    .put("story_id", realStoryId)
            )
        } catch (e: Throwable) {
            Log.d(TAG, "markStorySeen: ${e.message}")
        }
    }

    override suspend fun logout() {
        try {
            requireEngine().send(JSONObject().put("@type", "logOut"))
        } catch (_: Throwable) {
            // ignore
        }
        _authState.value = AuthUiState(state = AuthState.WaitPhoneNumber)
        // Everything the last account left behind: the next one to sign in
        // must not see its folders, its archive or its cached messages.
        chatsById.clear()
        usersById.clear()
        basicGroups.clear()
        supergroups.clear()
        messagesByChat.clear()
        positions.clear()
        storyKeys.clear()
        _chats.value = emptyList()
        _stories.value = emptyList()
        _folders.value = emptyList()
        // Nothing may run as though still signed in until the next account
        // is ready.
        readySignal = CompletableDeferred()
    }

    // The profile calls. Three, because TDLib has three, and each is allowed
    // to throw: `send` raises TdLibException carrying the server's own
    // message, and the screen shows that rather than a sentence this client
    // made up. A username refused for being taken is the common case and the
    // only one nothing here can predict.

    override suspend fun setName(firstName: String, lastName: String) {
        awaitReady()
        requireEngine().send(
            JSONObject()
                .put("@type", "setName")
                .put("first_name", firstName)
                .put("last_name", lastName)
        )
    }

    override suspend fun setBio(bio: String) {
        awaitReady()
        requireEngine().send(JSONObject().put("@type", "setBio").put("bio", bio))
    }

    override suspend fun setUsername(username: String) {
        awaitReady()
        // An empty string is how TDLib is told to give the username up; there
        // is no separate method for clearing it.
        requireEngine().send(
            JSONObject().put("@type", "setUsername").put("username", username)
        )
    }

    override suspend fun refreshMe() {
        awaitReady()
        val me = fetchMe() ?: return
        _authState.update { it.copy(me = me) }
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

    /** Runs on updateDispatcher, one update at a time — see there. */
    private suspend fun handleUpdate(update: JSONObject) {
        when (update.optString("@type")) {
            "updateAuthorizationState" -> {
                val state = update.optJSONObject("authorization_state") ?: return
                scope.launch { onAuthorizationState(state) }
            }
            "updateUser" -> {
                val user = update.optJSONObject("user") ?: return
                usersById[user.optLong("id")] = user
            }
            "updateBasicGroup" -> {
                val group = update.optJSONObject("basic_group") ?: return
                basicGroups[group.optLong("id")] = group
            }
            "updateSupergroup" -> {
                val group = update.optJSONObject("supergroup") ?: return
                supergroups[group.optLong("id")] = group
            }
            "updateUserStatus" -> {
                // Someone came online or went away. Only the status changes,
                // and the chat list redraws for the dot beside their avatar.
                val user = usersById[update.optLong("user_id")] ?: return
                user.put("status", update.optJSONObject("status"))
                publishChats()
            }
            "updateFile" -> {
                // The only place progress comes from. TDLib does not answer
                // a download with a stream of percentages; it announces the
                // file, repeatedly, as more of it arrives — the same update
                // for a file being sent, with the bytes on the other side of
                // the object.
                val file = update.optJSONObject("file") ?: return
                val id = file.optInt("id")
                val local = file.optJSONObject("local")
                val remote = file.optJSONObject("remote")
                val downloading = local?.optBoolean("is_downloading_active") == true
                val uploading = remote?.optBoolean("is_uploading_active") == true
                if (!downloading && !uploading) {
                    // Finished, failed or never started: either way there is
                    // no bar to draw, and leaving the entry behind would
                    // leave one on screen forever.
                    if (_fileTransfers.value.containsKey(id)) {
                        _fileTransfers.update { it - id }
                    }
                    return
                }
                val transfer = FileTransfer(
                    fileId = id,
                    doneBytes = if (uploading) {
                        remote.optLong("uploaded_size")
                    } else {
                        local?.optLong("downloaded_size") ?: 0L
                    },
                    // expected_size, not size: the second is zero until the
                    // whole file is known, which is exactly while a bar is
                    // wanted.
                    totalBytes = file.optLong("expected_size")
                        .takeIf { it > 0 } ?: file.optLong("size"),
                    isUpload = uploading
                )
                _fileTransfers.update { it + (id to transfer) }
            }
            "updateNewChat" -> {
                val chat = update.optJSONObject("chat") ?: return
                val chatId = chat.optLong("id")
                chatsById[chatId] = chat
                // A new chat arrives with its positions inside it; nothing
                // else will announce them.
                chat.optJSONArray("positions")?.let { applyPositions(chatId, it) }
                publishChats()
            }
            "updateChatTitle", "updateChatPhoto", "updateChatLastMessage",
            "updateChatReadInbox", "updateChatReadOutbox", "updateChatNotificationSettings",
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
                    "updateChatReadOutbox" -> {
                        // The other side has read up to here. Kept on the
                        // chat, which is where mapMessage reads it for the
                        // ticks, and told to the conversation on screen.
                        val lastRead = update.optLong("last_read_outbox_message_id")
                        chat.put("last_read_outbox_message_id", lastRead)
                        emitUpdate(MessageUpdate.ReadUpTo(chatId, lastRead))
                    }
                    "updateChatUnreadMentionCount" -> {
                        chat.put("unread_mention_count", update.optInt("unread_mention_count"))
                    }
                    "updateChatNotificationSettings" -> {
                        chat.put("notification_settings", update.optJSONObject("notification_settings"))
                    }
                }
                publishChats()
            }
            "updateChatFolders" -> {
                // The account's folders, whole, on every change: TDLib sends
                // the list rather than a diff, so this replaces rather than
                // merges. Their chats arrive separately, as positions, and
                // only for the lists that have been loaded — which is why
                // refreshChats loads each folder as well as the main list.
                _folders.value = parseFolders(update.optJSONArray("chat_folders"))
                // Loading waits on the server, and this thread must not: the
                // positions it brings back arrive as updates of their own.
                scope.launch { loadFolderChats() }
            }
            "updateChatPosition" -> {
                val chatId = update.optLong("chat_id")
                val position = update.optJSONObject("position") ?: return
                applyPosition(chatId, position)
                publishChats()
            }
            "updateNewMessage" -> {
                val message = update.optJSONObject("message") ?: return
                val chatId = message.optLong("chat_id")
                val mapped = mapMessage(chatId, message)
                chatsById[chatId]?.put("last_message", message)
                // Announced before publishChats, because a subscriber that
                // reacts to the message should not have to race the chat list
                // rebuild to see it.
                _incomingMessages.tryEmit(mapped)
                emitUpdate(MessageUpdate.Added(mapped))
                publishChats()
            }
            "updateMessageSendSucceeded" -> {
                // Our message, now under the id the server gave it. Until
                // this lands it has a temporary one, and anything aimed at
                // that — an edit, a reply, a delete — would be refused.
                val message = update.optJSONObject("message") ?: return
                val chatId = message.optLong("chat_id")
                emitUpdate(
                    MessageUpdate.Replaced(
                        oldId = update.optLong("old_message_id"),
                        message = mapMessage(chatId, message)
                    )
                )
            }
            "updateDeleteMessages" -> {
                // from_cache means TDLib only dropped its local copy to save
                // memory; the messages still exist.
                if (!update.optBoolean("is_permanent")) return
                val ids = update.optJSONArray("message_ids") ?: return
                emitUpdate(
                    MessageUpdate.Deleted(
                        chatId = update.optLong("chat_id"),
                        messageIds = (0 until ids.length()).map { ids.optLong(it) }.toSet()
                    )
                )
            }
            "updateMessageContent" -> {
                // Only what changes the words is passed on. The same update
                // fires for a poll's votes or a location moving, and marking
                // those "edited" would be wrong.
                val chatId = update.optLong("chat_id")
                val messageId = update.optLong("message_id")
                val text = contentText(update.optJSONObject("new_content"))
                    ?.takeIf { it.isNotBlank() }
                    ?: return
                val known = messagesByChat[chatId]?.firstOrNull { it.id == messageId }
                if (known != null && known.text == text) return
                emitUpdate(MessageUpdate.Edited(chatId, messageId, text))
            }
            "updateMessageInteractionInfo" -> {
                emitUpdate(
                    MessageUpdate.ReactionsChanged(
                        chatId = update.optLong("chat_id"),
                        messageId = update.optLong("message_id"),
                        reactions = parseReactions(update.optJSONObject("interaction_info"))
                    )
                )
            }
            "updateChatActiveStories" -> {
                // The whole of one chat's active stories, every time they
                // change. Kept rather than fetched: this is how TDLib hands
                // them out, once loadActiveStories has asked.
                val active = update.optJSONObject("active_stories") ?: return
                activeStories[active.optLong("chat_id")] = active
                publishStories()
            }
        }
    }

    /**
     * Tells the open conversation, and keeps this client's own copy in step:
     * reply quotes and reaction toggles read [messagesByChat], and a copy
     * that fell behind would quote a deleted message or undo a reaction.
     */
    private fun emitUpdate(update: MessageUpdate) {
        messagesByChat[update.chatId]?.let { known ->
            val applied = known.applying(update)
            messagesByChat[update.chatId] = applied.toMutableList()
        }
        _messageUpdates.tryEmit(update)
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
            mapUser(user).copy(bio = fetchBio(user.optLong("id")))
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * The "about" text, which `getMe` does not carry.
     *
     * TDLib splits a user across two objects: `user` has the name, the
     * username and the phone number, and `userFullInfo` has the bio, which is
     * a formatted text rather than a string. A failure here is not a failure
     * to fetch the account — the profile screen should still open, with an
     * empty bio, rather than showing nothing because one extra call did not
     * answer.
     */
    private suspend fun fetchBio(userId: Long): String = try {
        requireEngine()
            .send(JSONObject().put("@type", "getUserFullInfo").put("user_id", userId))
            .optJSONObject("bio")
            ?.optString("text")
            .orEmpty()
    } catch (_: Throwable) {
        ""
    }

    /**
     * Asks TDLib to announce the main story list.
     *
     * The stories themselves come back as `updateChatActiveStories`, one per
     * chat, and [publishStories] builds the rail from those. This used to ask
     * thirty chats one by one for their stories, read the answer from a field
     * that is not there, and so never showed a live story at all. 404 means
     * everything is already loaded, which after the first time it always is.
     */
    private suspend fun refreshStories() {
        val engine = this.engine ?: return
        try {
            engine.send(
                JSONObject()
                    .put("@type", "loadActiveStories")
                    .put("story_list", JSONObject().put("@type", "storyListMain"))
            )
        } catch (e: TdLibException) {
            Log.d(TAG, "loadActiveStories: ${e.message}")
        }
        withContext(updateDispatcher) { publishStories() }
    }

    /**
     * The rail: our own entry first, then every chat with an active story in
     * the main list, in TDLib's order.
     *
     * Unseen is read the way Telegram keeps it — one watermark per chat,
     * `max_read_story_id`, and any story above it is new. The first unseen
     * one is what opening the circle marks as seen; with none unseen, the
     * first.
     */
    private fun publishStories() {
        val own = StoryItem(
            id = 0,
            authorName = "My story",
            isOwn = true,
            hasUnseen = false,
            previewEmoji = "＋",
            caption = "Add"
        )
        val others = activeStories.values
            .filter { it.optJSONObject("list")?.optString("@type") == "storyListMain" }
            .sortedByDescending { it.optLong("order") }
            .mapNotNull { active ->
                val chatId = active.optLong("chat_id")
                val stories = active.optJSONArray("stories") ?: return@mapNotNull null
                val ids = (0 until stories.length()).mapNotNull { index ->
                    stories.optJSONObject(index)
                        ?.let { it.optInt("story_id", it.optInt("id")) }
                        ?.takeIf { it != 0 }
                }
                if (ids.isEmpty()) return@mapNotNull null
                val maxRead = active.optInt("max_read_story_id")
                val firstUnseen = ids.firstOrNull { it > maxRead }
                val title = chatsById[chatId]?.optString("title").orEmpty()
                StoryItem(
                    id = storyKey(chatId, firstUnseen ?: ids.first()),
                    authorName = title.ifBlank { "Story" },
                    hasUnseen = firstUnseen != null,
                    avatarColor = chatId,
                    previewEmoji = "✨",
                    caption = title
                )
            }
            .take(STORY_RAIL_LIMIT)
        _stories.value = listOf(own) + others
    }

    /** The same key for the same story every time — see [storyKeys]. */
    private fun storyKey(chatId: Long, storyId: Int): Long {
        storyKeys.entries.firstOrNull { it.value == chatId to storyId }?.let { return it.key }
        val key = storyKeySeq.incrementAndGet()
        storyKeys[key] = chatId to storyId
        return key
    }

    /**
     * Rebuilds the chat list from the chats and their positions.
     *
     * Only chats with a place in the main list or the archive. TDLib also
     * knows about chats the account is not in — search results, a group just
     * left, a channel opened from a link — and every one of those used to
     * turn up at the bottom of the list, which is also why leaving a group
     * seemed not to work.
     *
     * Called on updateDispatcher, so the chats cannot change while this
     * reads them.
     */
    private suspend fun publishChats() = chatMutex.withLock {
        _chats.value = positions.listed(chatsById.keys)
            .mapNotNull { id -> chatsById[id]?.let { toPreview(it) } }
    }

    /**
     * Reads `chatFolderInfo` objects into the model.
     *
     * The title is read two ways on purpose. TDLib changed it from a plain
     * string to a `formattedText` — the one that can carry custom emoji — and
     * which of those arrives depends on the version of the library the `.so`
     * was built from, not on anything this code can see. Reading both is
     * three lines; guessing wrong is a client whose folder tabs are all
     * blank.
     */
    private fun parseFolders(array: JSONArray?): List<ChatFolder> {
        if (array == null) return emptyList()
        val folders = ArrayList<ChatFolder>(array.length())
        for (i in 0 until array.length()) {
            val info = array.optJSONObject(i) ?: continue
            val title = info.optJSONObject("title")?.optString("text")
                ?: info.optString("title")
            folders += ChatFolder(
                id = info.optInt("id"),
                title = title.ifBlank { "Folder" },
                iconName = info.optJSONObject("icon")?.optString("name").orEmpty()
            )
        }
        return folders
    }

    /**
     * Asks TDLib for each folder's chats.
     *
     * Without this a folder has no members at all: TDLib only sends
     * positions for chat lists a client has actually loaded, so a folder
     * nobody asked about is a tab over an empty list. A failure is per
     * folder and not fatal — an empty folder answers 404, which is a normal
     * thing for a folder to be.
     */
    private suspend fun loadFolderChats() {
        for (folder in _folders.value) {
            loadChatList(
                JSONObject()
                    .put("@type", "chatListFolder")
                    .put("chat_folder_id", folder.id)
            )
        }
    }

    private fun applyPositions(chatId: Long, positions: JSONArray) {
        for (i in 0 until positions.length()) {
            applyPosition(chatId, positions.optJSONObject(i) ?: continue)
        }
    }

    /**
     * One `chatPosition` into [positions]. A position names its list, its
     * order — zero meaning "not in this list any more" — and whether the chat
     * is pinned there.
     */
    private fun applyPosition(chatId: Long, position: JSONObject) {
        val list = position.optJSONObject("list") ?: return
        val kind = when (list.optString("@type")) {
            "chatListMain" -> ChatPositions.ChatList.Main
            "chatListArchive" -> ChatPositions.ChatList.Archive
            "chatListFolder" -> ChatPositions.ChatList.Folder(list.optInt("chat_folder_id"))
            else -> return
        }
        positions.apply(
            chatId = chatId,
            list = kind,
            order = position.optLong("order"),
            isPinned = position.optBoolean("is_pinned")
        )
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
            timestampLabel = chatListTimeLabel(
                epochSeconds = last?.optLong("date") ?: 0L,
                nowSeconds = nowSeconds(),
                zone = ZoneId.systemDefault()
            ),
            unreadCount = chat.optInt("unread_count"),
            folderIds = positions.folderIds(id),
            isPinned = positions.isPinned(id),
            isMuted = notif?.optInt("mute_for", 0)?.let { it > 0 } ?: false,
            isOnline = privateChatUser(chat)?.let { presenceOf(it).isOnline(nowSeconds()) } == true,
            isChannel = chat.optBoolean("is_channel") || type.contains("channel", ignoreCase = true),
            isGroup = type == "chatTypeBasicGroup" || type == "chatTypeSupergroup",
            avatarColor = id,
            hasUnreadMention = chat.optInt("unread_mention_count") > 0,
            isArchived = positions.isArchived(id)
        )
    }

    /** The other person in a private chat, if this client has heard of them. */
    private fun privateChatUser(chat: JSONObject): JSONObject? {
        val type = chat.optJSONObject("type") ?: return null
        if (type.optString("@type") != "chatTypePrivate") return null
        return usersById[type.optLong("user_id")]
    }

    /** A TDLib `userStatus`, read into the tested model in :core. */
    private fun presenceOf(user: JSONObject): Presence {
        val status = user.optJSONObject("status") ?: return Presence.Unknown
        return when (status.optString("@type")) {
            "userStatusOnline" -> Presence.Online(status.optLong("expires"))
            "userStatusOffline" -> Presence.Offline(status.optLong("was_online"))
            "userStatusRecently" -> Presence.Recently
            "userStatusLastWeek" -> Presence.WithinWeek
            "userStatusLastMonth" -> Presence.WithinMonth
            else -> Presence.Unknown
        }
    }

    private fun nowSeconds(): Long = System.currentTimeMillis() / 1000

    private fun statusLabel(chat: JSONObject?): String? {
        chat ?: return null
        return when (chat.optJSONObject("type")?.optString("@type")) {
            // Where the person is, the way the header of every messenger
            // says it — this used to be the words "private chat".
            "chatTypePrivate" -> privateChatUser(chat)
                ?.let { presenceLabel(presenceOf(it), nowSeconds(), ZoneId.systemDefault()) }
            // How many are in it, from the group objects TDLib keeps current;
            // this used to say "group" for groups and channels alike.
            "chatTypeBasicGroup" -> {
                val id = chat.optJSONObject("type")?.optLong("basic_group_id")
                memberCountLabel(basicGroups[id]?.optInt("member_count") ?: 0, isChannel = false)
            }
            "chatTypeSupergroup" -> {
                val type = chat.optJSONObject("type")
                val group = supergroups[type?.optLong("supergroup_id")]
                memberCountLabel(
                    count = group?.optInt("member_count") ?: 0,
                    isChannel = type?.optBoolean("is_channel") == true
                )
            }
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

    /**
     * Telegram's own card for a link in a message.
     *
     * Only on a text message: a photo with a link in its caption gets no
     * `web_page` from the server, and inventing one here would mean fetching
     * the page from the phone.
     *
     * The image is deliberately left alone. `photo` arrives as a set of sizes
     * whose files are not downloaded yet, and a card that waits for bytes
     * before drawing is worse than one that shows the words immediately —
     * the text is the part that says whether the link is worth opening.
     */
    private fun linkPreview(content: JSONObject?): LinkPreview? {
        val page = content?.optJSONObject("web_page") ?: return null
        val preview = LinkPreview(
            url = page.optString("url"),
            siteName = page.optString("site_name"),
            title = page.optString("title"),
            description = page.optJSONObject("description")?.optString("text").orEmpty()
        )
        // A card holding nothing but the URL says less than the link already
        // in the message text.
        return preview.takeIf { it.hasContent }
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

    /**
     * The words a message's content shows: its text, or a media caption with
     * a fallback where the caption is empty. Null for content with no words
     * of its own — which is how an edit that did not touch them is told
     * apart from one that did.
     */
    private fun contentText(content: JSONObject?): String? = when (content?.optString("@type")) {
        "messageText" -> content.optJSONObject("text")?.optString("text").orEmpty()
        "messagePhoto" -> content.optJSONObject("caption")?.optString("text").orEmpty()
            .ifBlank { "Photo" }
        "messageDocument" -> content.optJSONObject("caption")?.optString("text").orEmpty()
            .ifBlank { content.optJSONObject("document")?.optString("file_name").orEmpty() }
        "messageVideo", "messageVoiceNote", "messageAudio", "messageAnimation" ->
            content.optJSONObject("caption")?.optString("text")
        else -> null
    }

    private fun mapMessage(chatId: Long, message: JSONObject): ChatMessage {
        val content = message.optJSONObject("content")
        val type = content?.optString("@type").orEmpty()
        // A caption where there is one — a video's used to be dropped for
        // "🎬 Video" — and the list's short description where there is not.
        val text = contentText(content)?.takeIf { it.isNotBlank() } ?: previewText(message)
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
            // An incoming message is ours to read, not theirs; an outgoing
            // one is read when the chat's watermark has reached it. This used
            // to test sending_state as a number — it is an object — which
            // made every message we sent look read the moment it left.
            isRead = !message.optBoolean("is_outgoing") ||
                (message.optJSONObject("sending_state") == null &&
                    message.optLong("id") <=
                    (chatsById[chatId]?.optLong("last_read_outbox_message_id") ?: 0L)),
            contentType = contentType,
            fileName = content?.optJSONObject("document")?.optString("file_name"),
            fileSizeLabel = null,
            mediaEmoji = when (contentType) {
                MessageContentType.Photo -> "🖼️"
                MessageContentType.Document -> "📎"
                else -> null
            },
            reactions = parseReactions(message.optJSONObject("interaction_info")),
            linkPreview = linkPreview(content),
            // Only a voice note carries these, and only once TDLib has the
            // bytes: the id arrives with the message, the path with the file.
            voiceFileId = content?.optJSONObject("voice_note")
                ?.optJSONObject("voice")
                ?.optInt("id")
                ?.takeIf { it != 0 },
            voicePath = content?.optJSONObject("voice_note")
                ?.optJSONObject("voice")
                ?.optJSONObject("local")
                ?.takeIf { it.optBoolean("is_downloading_completed") }
                ?.optString("path")
                ?.takeIf { it.isNotBlank() },
            waveform = parseWaveform(content),
            photoFileId = largestPhotoSize(content)?.optJSONObject("photo")?.optInt("id")
                ?.takeIf { it != 0 },
            photoPath = largestPhotoSize(content)
                ?.optJSONObject("photo")
                ?.optJSONObject("local")
                ?.takeIf { it.optBoolean("is_downloading_completed") }
                ?.optString("path")
                ?.takeIf { it.isNotBlank() },
            photoAspect = largestPhotoSize(content)?.let { size ->
                val width = size.optInt("width")
                val height = size.optInt("height")
                if (width > 0 && height > 0) width.toFloat() / height else 1f
            } ?: 1f,
            video = videoContent(content)
        )
    }

    /**
     * Reads `messageVideo` into the model, or answers null for anything else.
     *
     * Two files, and they arrive on different schedules. The poster is a
     * thumbnail a few kilobytes wide and is fetched as soon as the bubble is
     * on screen; the video behind it is not fetched until somebody asks for
     * it, because a chat scrolled past should not pull down a hundred
     * megabytes of things nobody watched.
     *
     * The duration and the dimensions come with the message itself, so the
     * bubble knows its shape and its length before either file exists —
     * which is what lets it reserve the space rather than jump when the
     * poster lands.
     */
    private fun videoContent(content: JSONObject?): VideoContent? {
        val video = content
            ?.takeIf { it.optString("@type") == "messageVideo" }
            ?.optJSONObject("video")
            ?: return null
        val width = video.optInt("width")
        val height = video.optInt("height")
        val thumbnail = video.optJSONObject("thumbnail")?.optJSONObject("file")
        val file = video.optJSONObject("video")
        return VideoContent(
            durationSeconds = video.optInt("duration"),
            aspect = if (width > 0 && height > 0) width.toFloat() / height else 16f / 9f,
            thumbFileId = thumbnail?.optInt("id")?.takeIf { it != 0 },
            thumbPath = thumbnail?.localPathIfDownloaded(),
            fileId = file?.optInt("id")?.takeIf { it != 0 },
            path = file?.localPathIfDownloaded()
        )
    }

    /**
     * The path of a TDLib `file`, but only once all of it is here.
     *
     * A partially downloaded file has a path too, and it points at bytes that
     * are still arriving — handing that to a player is how you get a video
     * that plays for two seconds and stops.
     */
    private fun JSONObject.localPathIfDownloaded(): String? = optJSONObject("local")
        ?.takeIf { it.optBoolean("is_downloading_completed") }
        ?.optString("path")
        ?.takeIf { it.isNotBlank() }

    /**
     * Reactions hang off interaction_info, alongside view and forward counts,
     * and are absent on the overwhelming majority of messages.
     *
     * Only emoji reactions are read. A custom reaction is a sticker id that
     * means nothing without fetching the sticker, and a chip showing a
     * numeric id would be worse than showing nothing.
     */
    private fun parseReactions(interactionInfo: JSONObject?): List<MessageReaction> {
        val array = interactionInfo
            ?.optJSONObject("reactions")
            ?.optJSONArray("reactions")
            ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val reaction = array.optJSONObject(index) ?: return@mapNotNull null
            val emoji = reaction.optJSONObject("type")
                ?.takeIf { it.optString("@type") == "reactionTypeEmoji" }
                ?.optString("emoji")
                ?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            MessageReaction(
                emoji = emoji,
                count = reaction.optInt("total_count"),
                isChosen = reaction.optBoolean("is_chosen")
            )
        }
    }

    /**
     * The voice note's waveform, unpacked.
     *
     * Base64 in the JSON, 5-bit samples inside that. A message that is not a
     * voice note has none, and one whose base64 will not decode is treated
     * the same as one that sent nothing: an empty waveform draws a flat row,
     * which is better than refusing to draw the message.
     */
    private fun parseWaveform(content: JSONObject?): List<Int> {
        val encoded = content?.optJSONObject("voice_note")
            ?.optString("waveform")
            ?.takeIf { it.isNotBlank() }
            ?: return emptyList()
        return try {
            unpackWaveform(Base64.getDecoder().decode(encoded))
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "parseWaveform: ${e.message}")
            emptyList()
        }
    }

    /**
     * The biggest size Telegram offers for a photo.
     *
     * A messagePhoto carries several, smallest first — thumbnails through to
     * the original. The last is the one worth showing: anything smaller is
     * visibly soft at the width a bubble draws it, and TDLib downsamples on
     * request anyway.
     */
    private fun largestPhotoSize(content: JSONObject?): JSONObject? {
        val sizes = content?.optJSONObject("photo")?.optJSONArray("sizes") ?: return null
        return sizes.optJSONObject(sizes.length() - 1)
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
        /** How many messages a conversation opens with. */
        private const val HISTORY_PAGE = 50

        /** How many requests [firstPage] may spend filling that page. */
        private const val HISTORY_ATTEMPTS = 4

        /** How many chats one `loadChats` asks for. */
        private const val CHAT_PAGE = 50

        /** The most photos Telegram puts in one album. */
        private const val ALBUM_LIMIT = 10

        /** How many circles the stories rail draws besides our own. */
        private const val STORY_RAIL_LIMIT = 20

        /** Telegram's own "muted forever": about 100 years, in seconds. */
        private const val MUTE_FOREVER_SECONDS = 2_147_483_647

        /**
         * How many of a group's members to fetch for the header.
         *
         * The cluster draws a handful and counts the rest, so the number only
         * has to be more than it draws. A supergroup can have two hundred
         * thousand people in it, and each one not already cached costs a call.
         */
        private const val MEMBER_LIMIT = 12

        /**
         * How many contacts the picker lists.
         *
         * Each one not already cached costs a getUser, and a sheet nobody
         * can scroll to the end of is a search field's job rather than a
         * list's. Search across chats already exists for the rest.
         */
        private const val CONTACT_LIMIT = 100

        /**
         * What a chat offers when it does not restrict reactions.
         *
         * TDLib says "all" without enumerating them, and the full set runs to
         * thousands once custom emoji are counted. These are Telegram's own
         * defaults, in its order, and they are what a picker can reasonably
         * show without a grid and a search field.
         */
        private val DEFAULT_REACTIONS =
            listOf("👍", "👎", "❤️", "🔥", "🎉", "😁", "🤔", "😢")
    }
}
