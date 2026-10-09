package com.telegramyou.app.telegram.tdlib

import com.telegramyou.app.notifications.ScopeNotifications
import com.telegramyou.app.notifications.NotificationScope
import com.telegramyou.app.telegram.model.CommentThread
import com.telegramyou.app.telegram.model.FileStream
import com.telegramyou.app.telegram.model.splitLongText
import com.telegramyou.app.telegram.model.TextSpan
import android.media.MediaMetadataRetriever
import com.telegramyou.app.telegram.model.STORY_VIDEO_MAX_SECONDS
import com.telegramyou.app.telegram.model.StoryAudience
import com.telegramyou.app.telegram.model.placePickedEmoji
import com.telegramyou.app.telegram.model.PickedEmoji
import com.telegramyou.app.telegram.model.ContactContent
import com.telegramyou.app.telegram.model.VideoContent
import com.telegramyou.app.telegram.model.customEmojiIdOf
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async
import com.telegramyou.app.telegram.model.ReactionOption
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import com.telegramyou.app.BuildConfig
import com.telegramyou.app.telegram.TelegramClient
import com.telegramyou.app.telegram.model.AttachmentDraft
import com.telegramyou.app.telegram.model.AuthState
import com.telegramyou.app.telegram.model.audienceRules
import com.telegramyou.app.telegram.model.privacyRulesOf
import com.telegramyou.app.telegram.model.PrivacySetting
import com.telegramyou.app.telegram.model.PrivacyRules
import com.telegramyou.app.telegram.model.storageSlices
import com.telegramyou.app.telegram.model.deviceKindOf
import com.telegramyou.app.telegram.model.StorageUsage
import com.telegramyou.app.telegram.model.StorageKind
import com.telegramyou.app.telegram.model.ActiveSession
import com.telegramyou.app.telegram.model.AuthUiState
import com.telegramyou.app.telegram.model.EmailReset
import com.telegramyou.app.telegram.model.ChatDetail
import com.telegramyou.app.telegram.model.ChatFolder
import com.telegramyou.app.telegram.model.GifItem
import com.telegramyou.app.telegram.model.FolderRules
import com.telegramyou.app.telegram.model.ChatPositions
import com.telegramyou.app.telegram.model.MessageUpdate
import com.telegramyou.app.telegram.model.CallbackAnswer
import com.telegramyou.app.telegram.model.ReplyKeyboard
import com.telegramyou.app.ui.auth.codeDeliveryText
import com.telegramyou.app.ui.chat.applying
import com.telegramyou.app.ui.format.chatListTimeLabel
import com.telegramyou.app.ui.format.isOnline
import com.telegramyou.app.ui.format.memberCountLabel
import com.telegramyou.app.ui.format.presenceLabel
import com.telegramyou.app.telegram.model.ChatMessage
import com.telegramyou.app.telegram.model.InlineBot
import com.telegramyou.app.telegram.model.InlineResults
import com.telegramyou.app.telegram.model.ReportOption
import com.telegramyou.app.telegram.model.ReportStep
import com.telegramyou.app.telegram.model.ReadInfo
import com.telegramyou.app.telegram.model.Viewer
import com.telegramyou.app.telegram.model.WebAppSession
import com.telegramyou.app.telegram.model.WebAppTheme
import com.telegramyou.app.telegram.model.MessageHit
import com.telegramyou.app.telegram.model.PostSearch
import com.telegramyou.app.telegram.model.AudioContent
import com.telegramyou.app.telegram.model.PollDraft
import com.telegramyou.app.telegram.model.isAudioFileName
import com.telegramyou.app.telegram.model.packWaveform
import com.telegramyou.app.telegram.model.unpackWaveform
import com.telegramyou.app.telegram.model.ChatPreview
import com.telegramyou.app.telegram.model.LastMessageStatus
import com.telegramyou.app.telegram.model.MessageContentType
import com.telegramyou.app.notifications.ChatNotificationSettings
import com.telegramyou.app.telegram.model.ProxyKind
import com.telegramyou.app.telegram.model.ProxyServer
import com.telegramyou.app.telegram.model.SendState
import com.telegramyou.app.telegram.model.StickerContent
import com.telegramyou.app.telegram.model.StickerSetPreview
import com.telegramyou.app.telegram.model.StoryFrame
import com.telegramyou.app.telegram.model.StoryItem
import com.telegramyou.app.telegram.model.TelegramUser
import com.telegramyou.app.telegram.model.PersonProfile
import com.telegramyou.app.telegram.model.MessagePermissions
import com.telegramyou.app.telegram.model.InviteLinkPreview
import com.telegramyou.app.telegram.model.SharedMediaKind
import com.telegramyou.app.telegram.model.DownloadEntry
import com.telegramyou.app.telegram.model.DownloadOutcome
import com.telegramyou.app.telegram.model.ForumTopic
import com.telegramyou.app.telegram.model.GroupManagement
import com.telegramyou.app.telegram.model.GroupMember
import com.telegramyou.app.telegram.model.GroupPermissions
import com.telegramyou.app.telegram.model.InviteLink
import com.telegramyou.app.telegram.model.AdminRights
import com.telegramyou.app.telegram.model.JoinRequest
import com.telegramyou.app.telegram.model.adminTitle
import com.telegramyou.app.telegram.model.MemberAction
import com.telegramyou.app.telegram.model.MemberRole
import com.telegramyou.app.telegram.model.TOPIC_COLORS
import com.telegramyou.app.ui.media.FileTransfer
import com.telegramyou.app.ui.media.formatBytes
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
import kotlinx.coroutines.delay
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
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
     * The rail's circles by the id this client gave them: one per posting
     * chat, not per story.
     *
     * Keyed by story, the id changed as soon as the first unseen story was
     * seen — under the viewer showing it, which then found nothing under its
     * id and closed. A chat's circle keeps its id for as long as it has
     * stories, which is what the viewer holds on to.
     */
    private val storyKeys = ConcurrentHashMap<Long, Long>()
    private val storyKeySeq = AtomicLong(0)

    /**
     * Until when each chat has someone typing, in epoch milliseconds.
     *
     * TDLib repeats an action every few seconds while it lasts and says
     * `chatActionCancel` when it stops — but a person whose connection drops
     * mid-sentence never sends the cancel, so each action also expires on
     * its own after [TYPING_MILLIS].
     */
    private val typingUntil = ConcurrentHashMap<Long, ConcurrentHashMap<Long, Long>>()

    /**
     * Who is typing in [chatId] now: by sender — a user's id, a chat's as
     * its negative — until when (2.1). Expired ones
     * are left out here and dropped on the next action.
     */
    private fun typingNow(chatId: Long): List<Long> {
        val now = System.currentTimeMillis()
        return typingUntil[chatId]?.entries?.filter { it.value > now }?.map { it.key }.orEmpty()
    }

    /** A typing sender's first name, or a chat's title when a chat writes. */
    private fun typingName(sender: Long): String? =
        if (sender > 0) {
            usersById[sender]?.optString("first_name")?.takeIf { it.isNotBlank() }
        } else {
            chatsById[-sender]?.optString("title")?.takeIf { it.isNotBlank() }
        }

    /**
     * How private chats, groups and channels notify by default, by the scope's
     * TDLib type. A chat whose own setting says "use the default" takes it
     * from here — which is most of them, so reading only the chat's own
     * values would have called nearly every chat unmuted.
     */
    private val scopeNotifications = ConcurrentHashMap<String, JSONObject>()

    /** Each chat's `chatActiveStories`, as `updateChatActiveStories` last said. */
    private val activeStories = ConcurrentHashMap<Long, JSONObject>()

    /**
     * Profile and chat pictures, by TDLib file id: the ones asked for, and
     * where each landed once it did.
     *
     * TDLib hands out a chat's picture as a file that is not downloaded yet,
     * and nothing downloads it unless asked. Nothing asked, which is why no
     * avatar in the live client ever showed anything but initials.
     */
    private val requestedPhotos = ConcurrentHashMap.newKeySet<Int>()
    private val downloadedPhotos = ConcurrentHashMap<Int, String>()
    /**
     * How often each picture has been asked for. A download that stops
     * short — the network went, TDLib gave up — is asked for again the next
     * time the picture is wanted, up to [PHOTO_ATTEMPTS] times: asked once
     * per process, as before 1.6.10, a dropped connection left an avatar on
     * initials for as long as the connection service kept the process alive,
     * which is days.
     */
    private val photoAttempts = ConcurrentHashMap<Int, Int>()
    private val photoRepublishPending = AtomicBoolean(false)
    private val chatsRepublishPending = AtomicBoolean(false)

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

    private val _replyKeyboards = MutableStateFlow<Map<Long, ReplyKeyboard>>(emptyMap())
    override val replyKeyboards: StateFlow<Map<Long, ReplyKeyboard>> = _replyKeyboards.asStateFlow()

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

    /** The last thing [setOnline] was told, sent again once signed in. */
    @Volatile
    private var isOnline = false

    override fun setOnline(online: Boolean) {
        isOnline = online
        sendOnline()
    }

    private fun sendOnline() {
        engine?.sendFireAndForget(
            JSONObject()
                .put("@type", "setOption")
                .put("name", "online")
                .put("value", JSONObject().put("@type", "optionValueBoolean").put("value", isOnline))
        )
    }

    private var preferIpv6 = false

    override fun setPreferIpv6(prefer: Boolean) {
        preferIpv6 = prefer
        sendPreferIpv6()
    }

    private fun sendPreferIpv6() {
        engine?.sendFireAndForget(
            JSONObject()
                .put("@type", "setOption")
                .put("name", "prefer_ipv6")
                .put("value", JSONObject().put("@type", "optionValueBoolean").put("value", preferIpv6))
        )
    }

    /** Set by [shutdown], so the close it causes is not taken for a log-out. */
    @Volatile
    private var shuttingDown = false

    override fun shutdown() {
        shuttingDown = true
        engine?.stop()
        engine = null
    }

    /**
     * TDLib's QR sign-in: it answers with
     * `authorizationStateWaitOtherDeviceConfirmation`, whose link is what the
     * code encodes, and renews the link on its own while the screen shows it.
     */
    override suspend fun requestQrLogin() {
        _authState.update { it.copy(isLoading = true, errorMessage = null) }
        try {
            requireEngine().send(
                JSONObject()
                    .put("@type", "requestQrCodeAuthentication")
                    .put("other_user_ids", JSONArray())
            )
        } catch (e: TdLibException) {
            _authState.update { it.copy(isLoading = false, errorMessage = e.message) }
        }
    }

    override suspend fun submitEmailAddress(email: String) {
        _authState.update { it.copy(isLoading = true, errorMessage = null) }
        try {
            requireEngine().send(
                JSONObject()
                    .put("@type", "setAuthenticationEmailAddress")
                    .put("email_address", email.trim())
            )
        } catch (e: TdLibException) {
            _authState.update { it.copy(isLoading = false, errorMessage = e.message) }
        }
    }

    override suspend fun submitEmailCode(code: String) {
        _authState.update { it.copy(isLoading = true, errorMessage = null) }
        try {
            requireEngine().send(
                JSONObject()
                    .put("@type", "checkAuthenticationEmailCode")
                    .put(
                        "code",
                        JSONObject()
                            .put("@type", "emailAddressAuthenticationCode")
                            .put("code", code.trim())
                    )
            )
        } catch (e: TdLibException) {
            _authState.update { it.copy(isLoading = false, errorMessage = e.message) }
        }
    }

    /**
     * Where the reset lands immediately, TDLib moves on to a code by SMS by
     * itself. Otherwise the state stays on the email code with the reset now
     * pending, and asking again before it lands is refused with
     * TASK_ALREADY_EXISTS — which is said as what it means.
     */
    override suspend fun resetEmail() {
        _authState.update { it.copy(isLoading = true, errorMessage = null) }
        try {
            requireEngine().send(JSONObject().put("@type", "resetAuthenticationEmailAddress"))
            _authState.update { it.copy(isLoading = false) }
        } catch (e: TdLibException) {
            val message = if (e.message.orEmpty().contains("TASK_ALREADY_EXISTS")) {
                "The reset is already on its way"
            } else {
                e.message
            }
            _authState.update { it.copy(isLoading = false, errorMessage = message) }
        }
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

    override suspend fun searchPublicChats(query: String): List<ChatPreview> {
        if (query.isBlank()) return emptyList()
        awaitReady()
        return chatsFrom("searchPublicChats") { it.put("query", query) }
    }

    override suspend fun chatByUsername(username: String): Long? {
        awaitReady()
        return try {
            requireEngine().send(JSONObject().put("@type", "searchPublicChat").put("username", username))
                .optLong("id").takeIf { it != 0L }
        } catch (e: TdLibException) {
            null
        }
    }

    override suspend fun topPeople(limit: Int): List<ChatPreview> {
        awaitReady()
        return chatsFrom("getTopChats") {
            it.put("category", JSONObject().put("@type", "topChatCategoryUsers"))
                .put("limit", limit)
        }
    }

    override suspend fun recentlyFoundChats(): List<ChatPreview> {
        awaitReady()
        // An empty query is how TDLib lists them all, up to fifty.
        return chatsFrom("searchRecentlyFoundChats") { it.put("query", "").put("limit", 50) }
    }

    override suspend fun addRecentlyFoundChat(chatId: Long) {
        awaitReady()
        quietly("addRecentlyFoundChat") { it.put("chat_id", chatId) }
    }

    override suspend fun removeRecentlyFoundChat(chatId: Long) {
        awaitReady()
        quietly("removeRecentlyFoundChat") { it.put("chat_id", chatId) }
    }

    override suspend fun clearRecentlyFoundChats() {
        awaitReady()
        quietly("clearRecentlyFoundChats") { it }
    }

    override suspend fun recommendedChannels(): List<ChatPreview> {
        awaitReady()
        return chatsFrom("getRecommendedChats") { it }
    }

    /**
     * One of TDLib's many requests that answer with `chats` — a list of ids
     * whose chats it has already announced — read into rows. A refusal is an
     * empty list: every caller is a part of search that can simply be absent.
     */
    private suspend fun chatsFrom(type: String, fill: (JSONObject) -> JSONObject): List<ChatPreview> {
        val found = try {
            requireEngine().send(fill(JSONObject().put("@type", type)))
        } catch (e: TdLibException) {
            Log.w(TAG, "$type: ${e.message}")
            return emptyList()
        }
        val ids = found.optJSONArray("chat_ids") ?: return emptyList()
        return (0 until ids.length()).mapNotNull { index -> chatsById[ids.optLong(index)]?.let(::toPreview) }
    }

    /** A request whose failure costs nothing worth telling anyone about. */
    private suspend fun quietly(type: String, fill: (JSONObject) -> JSONObject) {
        try {
            requireEngine().send(fill(JSONObject().put("@type", type)))
        } catch (e: TdLibException) {
            Log.w(TAG, "$type: ${e.message}")
        }
    }

    override suspend fun searchPublicPosts(query: String, limit: Int): PostSearch {
        if (query.isBlank()) return PostSearch()
        awaitReady()
        val found = try {
            requireEngine().send(
                JSONObject()
                    .put("@type", "searchPublicPosts")
                    .put("query", query)
                    .put("offset", "")
                    .put("limit", limit)
                    // Never pays: a search that costs Stars is refused here
                    // rather than charged, and the tab says why.
                    .put("star_count", 0)
            )
        } catch (e: TdLibException) {
            Log.w(TAG, "searchPublicPosts: ${e.message}")
            return PostSearch(limitReached = e.message.orEmpty().contains("LIMIT", ignoreCase = true))
        }
        val limits = found.optJSONObject("search_limits")
        val array = found.optJSONArray("messages") ?: JSONArray()
        val hits = (0 until array.length()).mapNotNull { index ->
            val raw = array.optJSONObject(index) ?: return@mapNotNull null
            val chatId = raw.optLong("chat_id")
            val chat = chatsById[chatId] ?: return@mapNotNull null
            MessageHit(chat = toPreview(chat), message = mapMessage(chatId, raw))
        }
        return PostSearch(
            hits = hits,
            limitReached = found.optBoolean("are_limits_exceeded"),
            freeLeft = limits?.optInt("remaining_free_query_count"),
            nextFreeInSeconds = limits?.optInt("next_free_query_in") ?: 0
        )
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

    override suspend fun sharedMedia(
        chatId: Long,
        kind: SharedMediaKind,
        beforeMessageId: Long,
        limit: Int
    ): List<ChatMessage> {
        awaitReady()
        val filter = when (kind) {
            SharedMediaKind.Media -> "searchMessagesFilterPhotoAndVideo"
            SharedMediaKind.Files -> "searchMessagesFilterDocument"
            SharedMediaKind.Music -> "searchMessagesFilterAudio"
            SharedMediaKind.Voice -> "searchMessagesFilterVoiceAndVideoNote"
            SharedMediaKind.Links -> "searchMessagesFilterUrl"
            SharedMediaKind.Gifs -> "searchMessagesFilterAnimation"
        }
        val found = try {
            requireEngine().send(
                JSONObject()
                    .put("@type", "searchChatMessages")
                    .put("chat_id", chatId)
                    .put("query", "")
                    .put("filter", JSONObject().put("@type", filter))
                    .put("limit", limit)
                    .put("from_message_id", beforeMessageId)
                    .put("offset", 0)
            )
        } catch (e: TdLibException) {
            Log.w(TAG, "sharedMedia: ${e.message}")
            return emptyList()
        }
        // Newest first, whatever order the page came in, and without the
        // message it was asked from, which a page may repeat.
        return parseMessages(chatId, found.optJSONArray("messages"))
            .filter { beforeMessageId == 0L || it.id < beforeMessageId }
            .sortedByDescending { it.id }
    }

    // ── the download manager: TDLib's file-download list ──────────────────

    /** Who is waiting on a listed download, by file id; see updateFile. */
    private val downloadWaiters = ConcurrentHashMap<Int, CompletableDeferred<DownloadOutcome>>()

    /**
     * Downloads the person paused or cancelled: when one of these stops
     * short, it was asked to, and the chat that started it says nothing.
     */
    private val stoppedByPerson: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    /**
     * Listed downloads seen running. A file is announced idle for all sorts
     * of reasons before its download starts; only one that was running and
     * then stopped has stopped.
     */
    private val seenRunning: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    override suspend fun downloadToList(chatId: Long, messageId: Long, fileId: Int): DownloadOutcome {
        awaitReady()
        // A second tap on the same file waits on the first rather than
        // starting a race with it.
        downloadWaiters[fileId]?.let { return it.await() }
        val waiter = CompletableDeferred<DownloadOutcome>()
        downloadWaiters[fileId] = waiter
        stoppedByPerson.remove(fileId)
        seenRunning.remove(fileId)
        return try {
            val file = try {
                requireEngine().send(
                    JSONObject()
                        .put("@type", "addFileToDownloads")
                        .put("file_id", fileId)
                        .put("chat_id", chatId)
                        .put("message_id", messageId)
                        .put("priority", DOWNLOAD_PRIORITY)
                )
            } catch (e: TdLibException) {
                // Already listed — a file downloaded once and since cleared
                // from the phone — or a message TDLib will not list: fetched
                // all the same, as it always was.
                Log.w(TAG, "addFileToDownloads($fileId): ${e.message}")
                requireEngine().send(
                    JSONObject()
                        .put("@type", "downloadFile")
                        .put("file_id", fileId)
                        .put("priority", DOWNLOAD_PRIORITY)
                        .put("offset", 0)
                        .put("limit", 0)
                        .put("synchronous", false)
                )
            }
            file.localPathIfDownloaded()?.let { DownloadOutcome.Done(it) } ?: waiter.await()
        } catch (e: TdLibException) {
            Log.w(TAG, "downloadToList($fileId): ${e.message}")
            DownloadOutcome.Failed
        } finally {
            downloadWaiters.remove(fileId, waiter)
            seenRunning.remove(fileId)
        }
    }

    override suspend fun fileDownloads(): List<DownloadEntry> {
        awaitReady()
        val found = try {
            requireEngine().send(
                JSONObject()
                    .put("@type", "searchFileDownloads")
                    .put("query", "")
                    .put("only_active", false)
                    .put("only_completed", false)
                    .put("offset", "")
                    .put("limit", DOWNLOADS_LIMIT)
            )
        } catch (e: TdLibException) {
            Log.w(TAG, "searchFileDownloads: ${e.message}")
            return emptyList()
        }
        val files = found.optJSONArray("files") ?: return emptyList()
        return (0 until files.length()).mapNotNull { i ->
            val item = files.optJSONObject(i) ?: return@mapNotNull null
            val fileId = item.optInt("file_id")
            val raw = item.optJSONObject("message") ?: return@mapNotNull null
            val chatId = raw.optLong("chat_id")
            val message = mapMessage(chatId, raw)
            // The file itself for its size and whether it is still here: the
            // list keeps a finished download after the cache has let it go.
            val file = runCatching {
                requireEngine().send(JSONObject().put("@type", "getFile").put("file_id", fileId))
            }.getOrNull()
            val local = file?.optJSONObject("local")
            DownloadEntry(
                fileId = fileId,
                chatId = chatId,
                messageId = message.id,
                name = message.fileName ?: message.audio?.displayTitle ?: message.text.ifBlank { "File" },
                sizeBytes = file?.let { f -> f.optLong("size").takeIf { it > 0 } ?: f.optLong("expected_size") } ?: 0L,
                downloadedBytes = local?.optLong("downloaded_size") ?: 0L,
                mimeType = message.mimeType,
                chatTitle = chatsById[chatId]?.optString("title").orEmpty(),
                addedAt = item.optLong("add_date"),
                completedAt = item.optLong("complete_date").takeIf { it > 0 },
                isPaused = item.optBoolean("is_paused"),
                path = file?.localPathIfDownloaded()
            )
        }
    }

    override suspend fun setDownloadPaused(fileId: Int, paused: Boolean) {
        awaitReady()
        if (paused) stoppedByPerson += fileId else stoppedByPerson -= fileId
        // Whoever opened it stops waiting now, not when the bytes stop.
        if (paused) downloadWaiters[fileId]?.complete(DownloadOutcome.Stopped)
        runCatching {
            requireEngine().send(
                JSONObject().put("@type", "toggleDownloadIsPaused").put("file_id", fileId).put("is_paused", paused)
            )
        }.onFailure { Log.w(TAG, "toggleDownloadIsPaused: ${it.message}") }
    }

    override suspend fun setAllDownloadsPaused(paused: Boolean) {
        awaitReady()
        if (paused) {
            stoppedByPerson += downloadWaiters.keys
            downloadWaiters.values.forEach { it.complete(DownloadOutcome.Stopped) }
        } else {
            stoppedByPerson.clear()
        }
        runCatching {
            requireEngine().send(JSONObject().put("@type", "toggleAllDownloadsArePaused").put("are_paused", paused))
        }.onFailure { Log.w(TAG, "toggleAllDownloadsArePaused: ${it.message}") }
    }

    override suspend fun removeDownload(fileId: Int, deleteFile: Boolean) {
        awaitReady()
        stoppedByPerson += fileId
        runCatching {
            requireEngine().send(
                JSONObject()
                    .put("@type", "removeFileFromDownloads")
                    .put("file_id", fileId)
                    .put("delete_from_cache", deleteFile)
            )
        }.onFailure { Log.w(TAG, "removeFileFromDownloads: ${it.message}") }
        // Cancelling stops the bytes too: a download taken out of the list
        // while it ran would otherwise go on arriving with nowhere to show.
        if (deleteFile) {
            runCatching {
                requireEngine().send(
                    JSONObject().put("@type", "cancelDownloadFile").put("file_id", fileId).put("only_if_pending", false)
                )
            }
        }
        downloadWaiters[fileId]?.complete(DownloadOutcome.Stopped)
    }

    override suspend fun translateMessage(chatId: Long, messageId: Long, toLanguage: String): String? {
        awaitReady()
        val translated = requireEngine().send(
            JSONObject()
                .put("@type", "translateMessageText")
                .put("chat_id", chatId)
                .put("message_id", messageId)
                .put("to_language_code", toLanguage)
        )
        return translated.optString("text").takeIf { it.isNotBlank() }
    }

    override suspend fun deleteDownloadedFile(fileId: Int) {
        awaitReady()
        requireEngine().send(JSONObject().put("@type", "deleteFile").put("file_id", fileId))
    }

    override suspend fun clearFinishedDownloads(deleteFiles: Boolean) {
        awaitReady()
        runCatching {
            requireEngine().send(
                JSONObject()
                    .put("@type", "removeAllFilesFromDownloads")
                    .put("only_active", false)
                    .put("only_completed", true)
                    .put("delete_from_cache", deleteFiles)
            )
        }.onFailure { Log.w(TAG, "removeAllFilesFromDownloads: ${it.message}") }
    }

    override suspend fun allMusic(query: String, cursor: String, limit: Int): Pair<List<ChatMessage>, String?> {
        awaitReady()
        val found = try {
            requireEngine().send(
                JSONObject()
                    .put("@type", "searchMessages")
                    .put("query", query.trim())
                    .put("filter", JSONObject().put("@type", "searchMessagesFilterAudio"))
                    .put("offset", cursor)
                    .put("limit", limit)
                    .put("min_date", 0)
                    .put("max_date", 0)
            )
        } catch (e: TdLibException) {
            Log.w(TAG, "allMusic: ${e.message}")
            return emptyList<ChatMessage>() to null
        }
        val messages = found.optJSONArray("messages")
        val out = mutableListOf<ChatMessage>()
        for (i in 0 until (messages?.length() ?: 0)) {
            val message = messages?.optJSONObject(i) ?: continue
            out += mapMessage(message.optLong("chat_id"), message)
        }
        return out to found.optString("next_offset").takeIf { it.isNotBlank() }
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
        val current = chatsById[chatId]?.let { notificationsOf(it) } ?: ChatNotificationSettings()
        setChatNotifications(
            chatId,
            current.copy(mutedUntil = if (muted) ChatNotificationSettings.MUTED_FOREVER else 0L)
        )
    }

    /**
     * The chat's whole settings object, copied and changed where the person
     * changed something — each `use_default_…` set false for what is written,
     * or TDLib ignores the value beside it. Sound on goes back to the
     * default sound rather than naming one: this client has no sound picker,
     * and "the default" is what a person turning sound back on expects.
     */
    override suspend fun setChatNotifications(chatId: Long, settings: ChatNotificationSettings) {
        awaitReady()
        val now = nowSeconds()
        val object_ = chatsById[chatId]?.optJSONObject("notification_settings")
            ?.let { JSONObject(it.toString()) }
            ?: defaultNotificationSettings()
        object_
            .put("use_default_mute_for", false)
            // Telegram measures a mute in seconds from now; its "forever" is a
            // very large number rather than a flag.
            .put("mute_for", settings.muteForSeconds(now).coerceAtMost(MUTE_FOREVER_SECONDS.toLong()))
            .put("use_default_show_preview", false)
            .put("show_preview", settings.showPreview)
            .put("use_default_sound", settings.sound)
            .put("sound_id", 0L)
        val settingsObject = object_
        requireEngine().send(
            JSONObject()
                .put("@type", "setChatNotificationSettings")
                .put("chat_id", chatId)
                .put("notification_settings", settingsObject)
        )
    }

    private fun scopeKey(scope: NotificationScope): String = when (scope) {
        NotificationScope.PrivateChats -> "notificationSettingsScopePrivateChats"
        NotificationScope.Groups -> "notificationSettingsScopeGroupChats"
        NotificationScope.Channels -> "notificationSettingsScopeChannelChats"
    }

    override suspend fun scopeNotifications(): Map<NotificationScope, ScopeNotifications> {
        awaitReady()
        return NotificationScope.entries.associateWith { scope ->
            val key = scopeKey(scope)
            val settings = scopeNotifications[key] ?: runCatching {
                requireEngine().send(
                    JSONObject().put("@type", "getScopeNotificationSettings").put("scope", JSONObject().put("@type", key))
                )
            }.getOrNull()?.also { scopeNotifications[key] = it } ?: JSONObject()
            ScopeNotifications(
                enabled = settings.optLong("mute_for") == 0L,
                showPreview = settings.optBoolean("show_preview", true),
                sound = !settings.has("sound_id") || settings.optLong("sound_id") != 0L
            )
        }
    }

    /**
     * The scope's whole settings object, changed where the person changed
     * something — the story and pin settings beside them kept as they are.
     * Sound on is the default sound (-1): there is no picker here.
     */
    override suspend fun setScopeNotifications(scope: NotificationScope, settings: ScopeNotifications) {
        awaitReady()
        val key = scopeKey(scope)
        val current = scopeNotifications[key]?.let { JSONObject(it.toString()) } ?: JSONObject()
            .put("use_default_mute_stories", true)
            .put("mute_stories", false)
            .put("story_sound_id", -1L)
            .put("show_story_poster", true)
            .put("disable_pinned_message_notifications", false)
            .put("disable_mention_notifications", false)
        current
            .put("@type", "scopeNotificationSettings")
            .put("mute_for", if (settings.enabled) 0 else MUTE_FOREVER_SECONDS)
            .put("show_preview", settings.showPreview)
            .put("sound_id", if (settings.sound) -1L else 0L)
        requireEngine().send(
            JSONObject()
                .put("@type", "setScopeNotificationSettings")
                .put("scope", JSONObject().put("@type", key))
                .put("notification_settings", current)
        )
        // Kept at once, so the chat list's mute icons and the next read
        // agree before TDLib's own update says the same.
        scopeNotifications[key] = current
        publishChats()
    }

    /** Every setting following the account's defaults, for a chat not yet seen. */
    /**
     * What a chat's settings come to once its defaults are filled in from its
     * scope: its own values where it has them, the scope's where it says to
     * use the default.
     */
    private fun notificationsOf(chat: JSONObject): ChatNotificationSettings {
        val own = chat.optJSONObject("notification_settings") ?: JSONObject()
        val type = chat.optJSONObject("type")?.optString("@type")
        val scope = scopeNotifications[
            when {
                type == "chatTypePrivate" || type == "chatTypeSecret" -> "notificationSettingsScopePrivateChats"
                chat.optJSONObject("type")?.optBoolean("is_channel") == true -> "notificationSettingsScopeChannelChats"
                else -> "notificationSettingsScopeGroupChats"
            }
        ] ?: JSONObject()
        fun <T> pick(flag: String, ownValue: () -> T, scopeValue: () -> T): T =
            if (own.optBoolean(flag, true)) scopeValue() else ownValue()
        val muteFor = pick("use_default_mute_for", { own.optLong("mute_for") }, { scope.optLong("mute_for") })
        return ChatNotificationSettings(
            mutedUntil = ChatNotificationSettings.mutedUntil(muteFor, nowSeconds()),
            showPreview = pick("use_default_show_preview", { own.optBoolean("show_preview", true) }, {
                scope.optBoolean("show_preview", true)
            }),
            sound = pick("use_default_sound", { own.optLong("sound_id") != 0L }, {
                !scope.has("sound_id") || scope.optLong("sound_id") != 0L
            })
        )
    }

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
        // The keyboard a bot left up, if TDLib has not announced it yet.
        chat?.optLong("reply_markup_message_id")?.takeIf { it != 0L }?.let { markupId ->
            if (chatId !in _replyKeyboards.value) scope.launch { loadReplyKeyboard(chatId, markupId) }
        }
        return ChatDetail(
            chat = preview,
            messages = mapped,
            memberCountLabel = statusLabel(chat),
            isTyping = preview.isTyping,
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
                    .inOpenTopic(chatId)
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
    override suspend fun renewInviteLink(chatId: Long): String? {
        awaitReady()
        val link = requireEngine().send(
            JSONObject().put("@type", "replacePrimaryChatInviteLink").put("chat_id", chatId)
        )
        return link.optString("invite_link").ifBlank { null }
    }

    override suspend fun sendLocation(
        chatId: Long,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Double,
        replyToId: Long?
    ) {
        awaitReady()
        requireEngine().send(
            JSONObject()
                .put("@type", "sendMessage")
                .put("chat_id", chatId)
                .inOpenTopic(chatId)
                .withReplyTo(replyToId)
                .put(
                    "input_message_content",
                    JSONObject()
                        .put("@type", "inputMessageLocation")
                        .put(
                            "location",
                            JSONObject()
                                .put("@type", "location")
                                .put("latitude", latitude)
                                .put("longitude", longitude)
                                .put("horizontal_accuracy", accuracyMeters)
                        )
                        // Where it is now, once; not a live location.
                        .put("live_period", 0)
                )
        )
    }

    override suspend fun sendContact(chatId: Long, contact: ContactContent, replyToId: Long?) {
        awaitReady()
        requireEngine().send(
            JSONObject()
                .put("@type", "sendMessage")
                .put("chat_id", chatId)
                .inOpenTopic(chatId)
                .withReplyTo(replyToId)
                .put(
                    "input_message_content",
                    JSONObject()
                        .put("@type", "inputMessageContact")
                        .put(
                            "contact",
                            JSONObject()
                                .put("@type", "contact")
                                .put("phone_number", contact.phoneNumber)
                                .put("first_name", contact.firstName)
                                .put("last_name", contact.lastName)
                                .put("vcard", "")
                                .put("user_id", contact.userId)
                        )
                )
        )
    }

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

    override suspend fun clearHistory(chatId: Long, forEveryone: Boolean) {
        awaitReady()
        requireEngine().send(
            JSONObject()
                .put("@type", "deleteChatHistory")
                .put("chat_id", chatId)
                .put("remove_from_chat_list", false)
                .put("revoke", forEveryone)
        )
    }

    override suspend fun deleteChat(chatId: Long, forEveryone: Boolean) {
        awaitReady()
        val type = chatsById[chatId]?.optJSONObject("type")?.optString("@type")
        if (type == "chatTypeBasicGroup" || type == "chatTypeSupergroup") {
            // A group or channel is left, and then its history taken off the
            // list. Leaving a supergroup already removes it, and TDLib then
            // refuses the second call — which is the outcome wanted, so
            // neither refusal is an error here. A basic group stays in the
            // list after leaving until its history goes.
            try {
                requireEngine().send(JSONObject().put("@type", "leaveChat").put("chat_id", chatId))
            } catch (e: Throwable) {
                Log.w(TAG, "deleteChat leave: ${e.message}")
            }
            try {
                requireEngine().send(
                    JSONObject()
                        .put("@type", "deleteChatHistory")
                        .put("chat_id", chatId)
                        .put("remove_from_chat_list", true)
                        .put("revoke", false)
                )
            } catch (e: Throwable) {
                Log.w(TAG, "deleteChat history: ${e.message}")
            }
            return
        }
        requireEngine().send(
            JSONObject()
                .put("@type", "deleteChatHistory")
                .put("chat_id", chatId)
                .put("remove_from_chat_list", true)
                .put("revoke", forEveryone)
        )
    }

    /** A user this client has heard of, or asks TDLib for; null when it cannot say. */
    private suspend fun userObject(userId: Long): JSONObject? =
        usersById[userId] ?: try {
            requireEngine()
                .send(JSONObject().put("@type", "getUser").put("user_id", userId))
                .also { usersById[userId] = it }
        } catch (_: Throwable) {
            null
        }

    override suspend fun person(userId: Long): PersonProfile? {
        awaitReady()
        val raw = userObject(userId) ?: return null
        // The bio and the block list live on userFullInfo, not on user. A
        // failure there still shows the person, with neither.
        val full = try {
            requireEngine().send(JSONObject().put("@type", "getUserFullInfo").put("user_id", userId))
        } catch (e: Throwable) {
            Log.w(TAG, "person full info: ${e.message}")
            null
        }
        return PersonProfile(
            user = mapUser(raw).copy(bio = full?.optJSONObject("bio")?.optString("text").orEmpty()),
            isContact = raw.optBoolean("is_contact"),
            // A BlockList object when blocked, absent when not.
            isBlocked = full?.optJSONObject("block_list") != null,
            isBot = raw.optJSONObject("type")?.optString("@type") == "userTypeBot"
        )
    }

    override suspend fun personInChat(chatId: Long): PersonProfile? {
        awaitReady()
        val chat = chatsById[chatId] ?: try {
            requireEngine().send(JSONObject().put("@type", "getChat").put("chat_id", chatId))
                .also { chatsById[chatId] = it }
        } catch (_: Throwable) {
            return null
        }
        if (isSavedMessages(chat)) return null
        val type = chat.optJSONObject("type") ?: return null
        // A secret chat has a person behind it too, under the same field.
        if (type.optString("@type") != "chatTypePrivate" && type.optString("@type") != "chatTypeSecret") {
            return null
        }
        return person(type.optLong("user_id"))
    }

    override suspend fun setBlocked(userId: Long, blocked: Boolean) {
        awaitReady()
        val request = JSONObject()
            .put("@type", "setMessageSenderBlockList")
            .put("sender_id", JSONObject().put("@type", "messageSenderUser").put("user_id", userId))
        // Absent, not an empty object, to unblock: TDLib reads a missing
        // block_list as null, which is what unblocking is.
        if (blocked) request.put("block_list", JSONObject().put("@type", "blockListMain"))
        requireEngine().send(request)
    }

    override suspend fun blockedPeople(): List<TelegramUser> {
        awaitReady()
        return try {
            val senders = requireEngine().send(
                JSONObject()
                    .put("@type", "getBlockedMessageSenders")
                    .put("block_list", JSONObject().put("@type", "blockListMain"))
                    .put("offset", 0)
                    .put("limit", BLOCKED_LIMIT)
            ).optJSONArray("senders") ?: return emptyList()
            // Users only: a blocked supergroup is a sender too, and this list
            // is of people.
            (0 until senders.length())
                .mapNotNull { senders.optJSONObject(it) }
                .filter { it.optString("@type") == "messageSenderUser" }
                .mapNotNull { userObject(it.optLong("user_id")) }
                .map(::mapUser)
        } catch (e: Throwable) {
            Log.w(TAG, "blockedPeople: ${e.message}")
            emptyList()
        }
    }

    override suspend fun addContact(phone: String, firstName: String, lastName: String): Long? {
        awaitReady()
        val contact = JSONObject()
            .put("@type", "importedContact")
            .put("phone_number", phone.filter(Char::isDigit))
            .put("first_name", firstName.trim())
            .put("last_name", lastName.trim())
            .put(
                "note",
                JSONObject().put("@type", "formattedText").put("text", "").put("entities", JSONArray())
            )
        val ids = requireEngine().send(
            JSONObject().put("@type", "importContacts").put("contacts", JSONArray().put(contact))
        ).optJSONArray("user_ids")
        // 0 is Telegram's answer for a number nobody has signed up with.
        return ids?.optLong(0)?.takeIf { it != 0L }
    }

    override suspend fun deleteAllMyMessages(chatId: Long): Int {
        awaitReady()
        val me = _authState.value.me?.id ?: fetchMe()?.id ?: return 0
        var from = 0L
        var deleted = 0
        repeat(MY_MESSAGES_PAGES) {
            val found = requireEngine().send(
                JSONObject()
                    .put("@type", "searchChatMessages")
                    .put("chat_id", chatId)
                    .put("query", "")
                    .put("sender_id", JSONObject().put("@type", "messageSenderUser").put("user_id", me))
                    .put("from_message_id", from)
                    .put("offset", 0)
                    .put("limit", MY_MESSAGES_PAGE)
            )
            val messages = found.optJSONArray("messages") ?: return deleted
            if (messages.length() == 0) return deleted
            val ids = (0 until messages.length()).mapNotNull { i ->
                messages.optJSONObject(i)?.optLong("id")?.takeIf { it != 0L }
            }
            requireEngine().send(
                JSONObject()
                    .put("@type", "deleteMessages")
                    .put("chat_id", chatId)
                    .put("message_ids", JSONArray(ids))
                    .put("revoke", true)
            )
            val gone = ids.toSet()
            messagesByChat[chatId]?.removeAll { it.id in gone }
            deleted += ids.size
            from = found.optLong("next_from_message_id").takeIf { it != 0L } ?: return deleted
        }
        return deleted
    }

    override suspend fun leaveChat(chatId: Long) {
        awaitReady()
        requireEngine().send(JSONObject().put("@type", "leaveChat").put("chat_id", chatId))
        // Nothing is removed here. Leaving takes the chat out of the main
        // list, and TDLib says so with a position update — the same one that
        // would arrive if this happened on another device.
    }

    // ── running a group ─────────────────────────────────────────────────

    /** The forum topic each chat's conversation screen is on; see setOpenTopic. */
    private val openTopics = ConcurrentHashMap<Long, Int>()

    override fun setOpenTopic(chatId: Long, topicId: Int?) {
        if (topicId == null) openTopics.remove(chatId) else openTopics[chatId] = topicId
    }

    override fun closeOpenTopic(chatId: Long, topicId: Int) {
        openTopics.remove(chatId, topicId)
    }

    /** The comment thread each discussion group's screen is on; see setOpenThread. */
    private val openThreads = ConcurrentHashMap<Long, Long>()

    override fun setOpenThread(chatId: Long, threadId: Long?) {
        if (threadId == null) openThreads.remove(chatId) else openThreads[chatId] = threadId
    }

    override fun closeOpenThread(chatId: Long, threadId: Long) {
        openThreads.remove(chatId, threadId)
    }

    override suspend fun commentThread(chatId: Long, messageId: Long): CommentThread? {
        awaitReady()
        return try {
            val info = requireEngine().send(
                JSONObject().put("@type", "getMessageThread").put("chat_id", chatId).put("message_id", messageId)
            )
            CommentThread(chatId = info.optLong("chat_id"), threadId = info.optLong("message_thread_id"))
                .takeIf { it.chatId != 0L && it.threadId != 0L }
        } catch (e: TdLibException) {
            Log.w(TAG, "commentThread($chatId, $messageId): ${e.message}")
            null
        }
    }

    override suspend fun commentPost(chatId: Long, threadId: Long): List<ChatMessage> {
        awaitReady()
        return try {
            // Asked of the discussion group's own copy of the post, the
            // thread's root: the answer's messages are that copy, newest
            // first, an album's every part.
            val info = requireEngine().send(
                JSONObject().put("@type", "getMessageThread").put("chat_id", chatId).put("message_id", threadId)
            )
            parseMessages(chatId, info.optJSONArray("messages"))
        } catch (e: TdLibException) {
            Log.w(TAG, "commentPost($chatId, $threadId): ${e.message}")
            emptyList()
        }
    }

    /**
     * A history request or a send, pointed at the topic [chatId]'s screen is
     * on, if it is on one: history becomes that topic's history, and a send
     * or a draft carries the topic. Anything else passes through unchanged.
     */
    private fun JSONObject.inOpenTopic(chatId: Long): JSONObject = apply {
        // A comment thread, open on a channel post's discussion group, is
        // the same idea as a forum topic: history becomes the thread's, and
        // what is sent goes into it (2.0).
        openThreads[chatId]?.let { thread ->
            if (optString("@type") == "getChatHistory") {
                put("@type", "getMessageThreadHistory")
                put("message_id", thread)
            } else {
                put("topic_id", commentTopic(thread))
            }
            return@apply
        }
        val topic = openTopics[chatId] ?: return@apply
        if (optString("@type") == "getChatHistory") {
            put("@type", "getForumTopicHistory")
            put("forum_topic_id", topic)
        } else {
            put("topic_id", forumTopic(topic))
        }
    }

    /**
     * The group behind [chatId]: whether it is a basic group, its id, and
     * the `basicGroup` or `supergroup` object — from the cache the updates
     * keep, or asked for when an update has not brought it yet.
     */
    private suspend fun groupOf(chatId: Long): Triple<Boolean, Long, JSONObject>? {
        val type = (chatsById[chatId] ?: runCatching {
            requireEngine().send(JSONObject().put("@type", "getChat").put("chat_id", chatId))
        }.getOrNull())?.optJSONObject("type") ?: return null
        return when (type.optString("@type")) {
            "chatTypeBasicGroup" -> {
                val id = type.optLong("basic_group_id")
                val group = basicGroups[id] ?: requireEngine().send(
                    JSONObject().put("@type", "getBasicGroup").put("basic_group_id", id)
                ).also { basicGroups[id] = it }
                Triple(true, id, group)
            }
            "chatTypeSupergroup" -> {
                if (type.optBoolean("is_channel")) return null
                val id = type.optLong("supergroup_id")
                val group = supergroups[id] ?: requireEngine().send(
                    JSONObject().put("@type", "getSupergroup").put("supergroup_id", id)
                ).also { supergroups[id] = it }
                Triple(false, id, group)
            }
            else -> null
        }
    }

    /** A user, from the cache or the server; null when neither knows them. */
    private suspend fun userOf(userId: Long): TelegramUser? {
        val raw = usersById[userId] ?: runCatching {
            requireEngine().send(JSONObject().put("@type", "getUser").put("user_id", userId))
                .also { usersById[userId] = it }
        }.getOrNull() ?: return null
        return mapUser(raw)
    }

    override suspend fun groupManagement(chatId: Long): GroupManagement? {
        awaitReady()
        return try {
            val (isBasic, groupId, group) = groupOf(chatId) ?: return null
            val chat = chatsById[chatId]
            val entries = if (isBasic) {
                requireEngine().send(
                    JSONObject().put("@type", "getBasicGroupFullInfo").put("basic_group_id", groupId)
                ).optJSONArray("members")
            } else {
                requireEngine().send(
                    JSONObject()
                        .put("@type", "getSupergroupMembers")
                        .put("supergroup_id", groupId)
                        .put("filter", JSONObject().put("@type", "supergroupMembersFilterRecent"))
                        .put("offset", 0)
                        .put("limit", MANAGED_MEMBER_LIMIT)
                ).optJSONArray("members")
            }
            val members = groupMembersFrom(entries, isBasic)
            GroupManagement(
                rights = rightsOf(group.optJSONObject("status"), isBasic),
                members = members,
                permissions = permissionsOf(chat?.optJSONObject("permissions")),
                isBasicGroup = isBasic,
                isForum = !isBasic && group.optBoolean("is_forum")
            )
        } catch (e: Throwable) {
            Log.w(TAG, "groupManagement: ${e.message}")
            null
        }
    }

    /** `chatMember` entries as the group's members, with their standing. */
    private suspend fun groupMembersFrom(entries: JSONArray?, isBasic: Boolean): List<GroupMember> {
        val members = mutableListOf<GroupMember>()
        for (index in 0 until (entries?.length() ?: 0)) {
            val entry = entries?.optJSONObject(index) ?: continue
            val sender = entry.optJSONObject("member_id") ?: continue
            if (sender.optString("@type") != "messageSenderUser") continue
            val status = entry.optJSONObject("status")
            val role = roleOf(status) ?: continue
            val user = userOf(sender.optLong("user_id")) ?: continue
            members += GroupMember(
                user = user,
                role = role,
                // The admin's title is the member's tag since TDLib moved it.
                title = entry.optString("tag"),
                canBeEdited = status?.optBoolean("can_be_edited") == true,
                adminRights = if (role == MemberRole.Admin) adminRightsOf(status, isBasic) else null
            )
        }
        return members
    }

    override suspend fun searchGroupMembers(chatId: Long, query: String): List<GroupMember> {
        awaitReady()
        return try {
            val (isBasic, _, _) = groupOf(chatId) ?: return emptyList()
            val found = requireEngine().send(
                JSONObject()
                    .put("@type", "searchChatMembers")
                    .put("chat_id", chatId)
                    .put("query", query.trim().removePrefix("@"))
                    .put("limit", MEMBER_SEARCH_LIMIT)
                    // No filter: everybody, whatever their standing.
                    .put("filter", JSONObject.NULL)
            )
            groupMembersFrom(found.optJSONArray("members"), isBasic)
        } catch (e: Throwable) {
            Log.w(TAG, "searchGroupMembers: ${e.message}")
            emptyList()
        }
    }

    override suspend fun promoteMember(chatId: Long, userId: Long, rights: AdminRights, title: String) {
        awaitReady()
        requireEngine().send(
            JSONObject()
                .put("@type", "setChatMemberStatus")
                .put("chat_id", chatId)
                .put("member_id", JSONObject().put("@type", "messageSenderUser").put("user_id", userId))
                .put(
                    "status",
                    JSONObject()
                        .put("@type", "chatMemberStatusAdministrator")
                        .put("can_be_edited", true)
                        .put("rights", rights.toJson())
                )
        )
        // The title is the member's tag, set on its own since TDLib split it
        // out; an empty one clears it.
        requireEngine().send(
            JSONObject()
                .put("@type", "setChatMemberTag")
                .put("chat_id", chatId)
                .put("user_id", userId)
                .put("tag", adminTitle(title))
        )
    }

    override suspend fun joinRequests(chatId: Long): List<JoinRequest> {
        awaitReady()
        return try {
            val answer = requireEngine().send(
                JSONObject()
                    .put("@type", "getChatJoinRequests")
                    .put("chat_id", chatId)
                    .put("invite_link", "")
                    .put("query", "")
                    .put("offset_request", JSONObject.NULL)
                    .put("limit", JOIN_REQUEST_LIMIT)
            )
            val requests = answer.optJSONArray("requests") ?: return emptyList()
            (0 until requests.length()).mapNotNull { index ->
                val request = requests.optJSONObject(index) ?: return@mapNotNull null
                val user = userOf(request.optLong("user_id")) ?: return@mapNotNull null
                JoinRequest(user = user, date = request.optLong("date"), bio = request.optString("bio"))
            }
        } catch (e: Throwable) {
            Log.w(TAG, "joinRequests: ${e.message}")
            emptyList()
        }
    }

    override suspend fun processJoinRequest(chatId: Long, userId: Long, approve: Boolean) {
        awaitReady()
        requireEngine().send(
            JSONObject()
                .put("@type", "processChatJoinRequest")
                .put("chat_id", chatId)
                .put("user_id", userId)
                .put("approve", approve)
        )
    }

    override suspend fun renameForumTopic(chatId: Long, topicId: Int, name: String) {
        awaitReady()
        requireEngine().send(
            JSONObject()
                .put("@type", "editForumTopic")
                .put("chat_id", chatId)
                .put("forum_topic_id", topicId)
                .put("name", name.trim())
                .put("edit_icon_custom_emoji", false)
                .put("icon_custom_emoji_id", 0)
        )
    }

    override suspend fun setForumTopicClosed(chatId: Long, topicId: Int, closed: Boolean) {
        awaitReady()
        requireEngine().send(
            JSONObject()
                .put("@type", "toggleForumTopicIsClosed")
                .put("chat_id", chatId)
                .put("forum_topic_id", topicId)
                .put("is_closed", closed)
        )
    }

    override suspend fun deleteForumTopic(chatId: Long, topicId: Int) {
        awaitReady()
        requireEngine().send(
            JSONObject()
                .put("@type", "deleteForumTopic")
                .put("chat_id", chatId)
                .put("forum_topic_id", topicId)
        )
    }

    override suspend fun saveTopicDraft(chatId: Long, topicId: Int, text: String) {
        awaitReady()
        requireEngine().send(
            JSONObject()
                .put("@type", "setChatDraftMessage")
                .put("chat_id", chatId)
                .put("topic_id", forumTopic(topicId))
                .put("draft_message", draftOf(text))
        )
    }

    override suspend fun applyMemberAction(chatId: Long, userId: Long, action: MemberAction) {
        awaitReady()
        val member = JSONObject().put("@type", "messageSenderUser").put("user_id", userId)
        val status = when (action) {
            MemberAction.MakeAdmin, MemberAction.EditAdmin -> JSONObject()
                .put("@type", "chatMemberStatusAdministrator")
                .put("can_be_edited", true)
                .put("rights", AdminRights().toJson())
            MemberAction.RemoveAdmin, MemberAction.Unrestrict -> JSONObject()
                .put("@type", "chatMemberStatusMember")
                .put("member_until_date", 0)
            MemberAction.Restrict -> JSONObject()
                .put("@type", "chatMemberStatusRestricted")
                .put("is_member", true)
                .put("restricted_until_date", 0)
                .put("permissions", silencedPermissions())
            MemberAction.Remove -> {
                // Removed, not banned for good: banned for a moment, which
                // takes them out and lets them be invited back.
                requireEngine().send(
                    JSONObject()
                        .put("@type", "banChatMember")
                        .put("chat_id", chatId)
                        .put("member_id", member)
                        .put("banned_until_date", nowSeconds() + REMOVE_BAN_SECONDS)
                        .put("revoke_messages", false)
                )
                return
            }
        }
        requireEngine().send(
            JSONObject()
                .put("@type", "setChatMemberStatus")
                .put("chat_id", chatId)
                .put("member_id", member)
                .put("status", status)
        )
    }

    override suspend fun setGroupPermissions(chatId: Long, permissions: GroupPermissions) {
        awaitReady()
        requireEngine().send(
            JSONObject()
                .put("@type", "setChatPermissions")
                .put("chat_id", chatId)
                .put("permissions", permissions.toJson())
        )
    }

    override suspend fun inviteLinks(chatId: Long): List<InviteLink> {
        awaitReady()
        val me = _authState.value.me?.id ?: return emptyList()
        val out = mutableListOf<InviteLink>()
        for (revoked in listOf(false, true)) {
            val answer = requireEngine().send(
                JSONObject()
                    .put("@type", "getChatInviteLinks")
                    .put("chat_id", chatId)
                    .put("creator_user_id", me)
                    .put("is_revoked", revoked)
                    .put("offset_date", 0)
                    .put("offset_invite_link", "")
                    .put("limit", INVITE_LINK_LIMIT)
            )
            val links = answer.optJSONArray("invite_links") ?: continue
            for (index in 0 until links.length()) {
                links.optJSONObject(index)?.let { out += inviteLinkOf(it) }
            }
        }
        return out
    }

    override suspend fun createInviteLink(
        chatId: Long,
        name: String,
        expiresAt: Long,
        memberLimit: Int,
        createsJoinRequest: Boolean
    ): InviteLink? {
        awaitReady()
        val link = requireEngine().send(
            JSONObject()
                .put("@type", "createChatInviteLink")
                .put("chat_id", chatId)
                .put("name", name.trim())
                .put("expiration_date", expiresAt)
                // Telegram refuses a limit on a link that asks first.
                .put("member_limit", if (createsJoinRequest) 0 else memberLimit)
                .put("creates_join_request", createsJoinRequest)
        )
        return inviteLinkOf(link)
    }

    override suspend fun revokeInviteLink(chatId: Long, link: String) {
        awaitReady()
        requireEngine().send(
            JSONObject()
                .put("@type", "revokeChatInviteLink")
                .put("chat_id", chatId)
                .put("invite_link", link)
        )
    }

    override suspend fun forumTopics(chatId: Long): List<ForumTopic> {
        awaitReady()
        val answer = requireEngine().send(
            JSONObject()
                .put("@type", "getForumTopics")
                .put("chat_id", chatId)
                .put("query", "")
                .put("offset_date", 0)
                .put("offset_message_id", 0)
                .put("offset_forum_topic_id", 0)
                .put("limit", TOPIC_LIMIT)
        )
        val topics = answer.optJSONArray("topics") ?: return emptyList()
        return (0 until topics.length()).mapNotNull { index ->
            val topic = topics.optJSONObject(index) ?: return@mapNotNull null
            val info = topic.optJSONObject("info") ?: return@mapNotNull null
            if (info.optBoolean("is_hidden")) return@mapNotNull null
            val last = topic.optJSONObject("last_message")
            ForumTopic(
                id = info.optInt("forum_topic_id"),
                name = info.optString("name"),
                iconColor = info.optJSONObject("icon")?.optInt("color") ?: TOPIC_COLORS.first(),
                lastMessage = previewText(last),
                timestampLabel = chatListTimeLabel(
                    epochSeconds = last?.optLong("date") ?: 0L,
                    nowSeconds = nowSeconds(),
                    zone = ZoneId.systemDefault()
                ),
                unreadCount = topic.optInt("unread_count"),
                isPinned = topic.optBoolean("is_pinned"),
                isClosed = info.optBoolean("is_closed"),
                isGeneral = info.optBoolean("is_general"),
                draft = topic.optJSONObject("draft_message")
                    ?.optJSONObject("input_message_text")
                    ?.optJSONObject("text")
                    ?.optString("text")
                    .orEmpty()
            )
        }
    }

    override suspend fun createForumTopic(chatId: Long, name: String): ForumTopic? {
        awaitReady()
        val color = TOPIC_COLORS[(name.hashCode() and Int.MAX_VALUE) % TOPIC_COLORS.size]
        val info = requireEngine().send(
            JSONObject()
                .put("@type", "createForumTopic")
                .put("chat_id", chatId)
                .put("name", name.trim())
                .put("is_name_implicit", false)
                .put(
                    "icon",
                    JSONObject().put("@type", "forumTopicIcon").put("color", color).put("custom_emoji_id", 0)
                )
        )
        return ForumTopic(id = info.optInt("forum_topic_id"), name = info.optString("name"), iconColor = color)
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
                .inOpenTopic(chatId)
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
     * A negative offset is how getChatHistory reaches forward: -k returns
     * k - 1 messages newer than from_message_id, the message itself, and
     * older ones to fill the limit. Half each way puts the message in the
     * middle of the page.
     */
    override suspend fun loadMessagesAround(
        chatId: Long,
        messageId: Long,
        limit: Int
    ): List<ChatMessage> {
        awaitReady()
        val history = requireEngine().send(
            JSONObject()
                .put("@type", "getChatHistory")
                .put("chat_id", chatId)
                .inOpenTopic(chatId)
                .put("from_message_id", messageId)
                .put("offset", -(limit / 2))
                .put("limit", limit)
                .put("only_local", false)
        )
        return parseMessages(chatId, history.optJSONArray("messages")).also(::rememberMessages)
    }

    /**
     * The whole page forward: an offset of -limit returns limit - 1 newer
     * messages and the one asked from, which is on screen already and is
     * dropped. Fewer than that newer, and the rest are older ones filling the
     * limit — dropped too, by the same test.
     */
    override suspend fun loadNewerMessages(
        chatId: Long,
        afterMessageId: Long,
        limit: Int
    ): List<ChatMessage> {
        awaitReady()
        val history = requireEngine().send(
            JSONObject()
                .put("@type", "getChatHistory")
                .put("chat_id", chatId)
                .inOpenTopic(chatId)
                .put("from_message_id", afterMessageId)
                .put("offset", -limit)
                .put("limit", limit)
                .put("only_local", false)
        )
        return parseMessages(chatId, history.optJSONArray("messages"))
            .filter { it.id > afterMessageId }
            .also(::rememberMessages)
    }

    /** Where the reply lookup reads, so quotes of paged-in messages resolve. */
    private fun rememberMessages(messages: List<ChatMessage>) {
        val chatId = messages.firstOrNull()?.chatId ?: return
        val known = messagesByChat.getOrPut(chatId) { mutableListOf() }
        val ids = known.mapTo(HashSet()) { it.id }
        known.addAll(messages.filter { it.id !in ids })
    }

    override suspend fun saveDraft(chatId: Long, text: String) {
        awaitReady()
        requireEngine().send(
            JSONObject()
                .put("@type", "setChatDraftMessage")
                .put("chat_id", chatId)
                .put("draft_message", draftOf(text))
        )
    }

    override suspend fun sendText(chatId: Long, text: String, replyToId: Long?, sendAt: Long?) {
        awaitReady()
        sendFormatted(chatId, formatted(text), replyToId, sendAt)
    }

    override suspend fun sendTextWithEmoji(
        chatId: Long,
        text: String,
        picked: List<PickedEmoji>,
        replyToId: Long?,
        sendAt: Long?
    ) {
        awaitReady()
        // Placed on the text as TDLib gives it back from the markdown, not
        // as typed: taking out a ** moves every offset after it.
        val parsed = formatted(text)
        val entities = parsed.optJSONArray("entities") ?: JSONArray().also { parsed.put("entities", it) }
        placePickedEmoji(parsed.optString("text"), picked).forEach { placed ->
            entities.put(
                JSONObject()
                    .put("@type", "textEntity")
                    .put("offset", placed.offset)
                    .put("length", placed.length)
                    .put(
                        "type",
                        JSONObject()
                            .put("@type", "textEntityTypeCustomEmoji")
                            .put("custom_emoji_id", placed.customEmojiId.toString())
                    )
            )
        }
        sendFormatted(chatId, parsed, replyToId, sendAt)
    }

    /**
     * A text already made into TDLib's formattedText, sent now or at
     * [sendAt] — as several messages when it is longer than Telegram takes
     * in one, cut where a reader would and with its formatting carried
     * across (splitLongText). Only the first answers [replyToId]. Telegram
     * used to refuse such a text outright with "Message is too long"; the
     * official client cuts it, and so does this (1.8.1).
     */
    override suspend fun sendQuotedReply(chatId: Long, text: String, replyToId: Long, quote: String, quotePosition: Int) {
        awaitReady()
        sendFormatted(
            chatId,
            formatted(text),
            replyToId,
            null,
            quote = JSONObject()
                .put("@type", "inputTextQuote")
                .put("text", JSONObject().put("@type", "formattedText").put("text", quote).put("entities", JSONArray()))
                .put("position", quotePosition)
        )
    }

    /**
     * TDLib answers "the last message no later than" a date, which is the
     * day before's last; the first of the day is the one after it, asked
     * for with a negative offset. Where the chat begins on that day there
     * is nothing before, and the day's own last message is the answer.
     */
    override suspend fun firstMessageFrom(chatId: Long, from: Long): Long? {
        awaitReady()
        val before = try {
            requireEngine().send(
                JSONObject().put("@type", "getChatMessageByDate").put("chat_id", chatId).put("date", from - 1)
            ).optLong("id").takeIf { it != 0L }
        } catch (e: TdLibException) {
            null
        }
        if (before != null) {
            val newer = try {
                requireEngine().send(
                    JSONObject()
                        .put("@type", "getChatHistory")
                        .put("chat_id", chatId)
                        .put("from_message_id", before)
                        .put("offset", -FIRST_FROM_PAGE)
                        .put("limit", FIRST_FROM_PAGE + 1)
                        .put("only_local", false)
                ).optJSONArray("messages")
            } catch (e: TdLibException) {
                null
            }
            val first = newer?.let { list ->
                List(list.length()) { list.optJSONObject(it) }
                    .filterNotNull()
                    .filter { it.optLong("date") >= from }
                    .minByOrNull { it.optLong("date") }
                    ?.optLong("id")
            }
            if (first != null) return first
        }
        return try {
            requireEngine().send(
                JSONObject().put("@type", "getChatMessageByDate").put("chat_id", chatId).put("date", from + DAY_SECONDS - 1)
            ).optLong("id").takeIf { it != 0L }
        } catch (e: TdLibException) {
            null
        }
    }

    override suspend fun messageLink(chatId: Long, messageId: Long): String? {
        awaitReady()
        return try {
            requireEngine().send(
                JSONObject()
                    .put("@type", "getMessageLink")
                    .put("chat_id", chatId)
                    .put("message_id", messageId)
                    .put("media_timestamp", 0)
                    .put("for_album", false)
                    .put("in_message_thread", false)
            ).optString("link").takeIf { it.isNotBlank() }
        } catch (e: TdLibException) {
            null
        }
    }

    override suspend fun readInfo(chatId: Long, messageId: Long, isGroup: Boolean): ReadInfo? {
        awaitReady()
        return try {
            if (isGroup) {
                val list = requireEngine().send(
                    JSONObject().put("@type", "getMessageViewers").put("chat_id", chatId).put("message_id", messageId)
                ).optJSONArray("viewers") ?: JSONArray()
                ReadInfo.SeenBy(
                    List(list.length()) { list.optJSONObject(it) }.filterNotNull().mapNotNull { viewer ->
                        val userId = viewer.optLong("user_id").takeIf { it != 0L } ?: return@mapNotNull null
                        val user = userObject(userId)
                        Viewer(
                            userId = userId,
                            name = user?.let { mapUser(it).displayName } ?: "Someone",
                            date = viewer.optLong("view_date"),
                            photoPath = user?.let { photoPath(it.optJSONObject("profile_photo")?.optJSONObject("small")) }
                        )
                    }.sortedByDescending { it.date }
                )
            } else {
                val read = requireEngine().send(
                    JSONObject().put("@type", "getMessageReadDate").put("chat_id", chatId).put("message_id", messageId)
                )
                when (read.optString("@type")) {
                    "messageReadDateRead" -> ReadInfo.ReadAt(read.optLong("read_date"))
                    "messageReadDateUnread" -> ReadInfo.Unread
                    else -> ReadInfo.Hidden
                }
            }
        } catch (e: TdLibException) {
            // A group too big for it, or a message too old: Telegram says no.
            ReadInfo.Hidden
        }
    }

    override suspend fun report(chatId: Long, messageIds: List<Long>, optionId: String, text: String): ReportStep {
        awaitReady()
        val ids = JSONArray().apply { messageIds.forEach { put(it) } }
        val answer = requireEngine().send(
            JSONObject()
                .put("@type", "reportChat")
                .put("chat_id", chatId)
                .put("option_id", optionId)
                .put("message_ids", ids)
                .put("text", text)
        )
        return when (answer.optString("@type")) {
            "reportChatResultOptionRequired" -> {
                val options = answer.optJSONArray("options") ?: JSONArray()
                ReportStep.Choose(
                    title = answer.optString("title"),
                    options = List(options.length()) { options.optJSONObject(it) }
                        .filterNotNull()
                        .map { ReportOption(id = it.optString("id"), text = it.optString("text")) }
                )
            }
            "reportChatResultTextRequired" -> ReportStep.Explain(
                optionId = answer.optString("option_id"),
                optional = answer.optBoolean("is_optional")
            )
            else -> ReportStep.Done
        }
    }

    override suspend fun repliedMessage(chatId: Long, messageId: Long): ChatMessage? {
        awaitReady()
        return try {
            val replied = requireEngine().send(
                JSONObject()
                    .put("@type", "getRepliedMessage")
                    .put("chat_id", chatId)
                    .put("message_id", messageId)
            )
            mapMessage(replied.optLong("chat_id", chatId), replied)
        } catch (e: TdLibException) {
            null
        }
    }

    private suspend fun sendFormatted(
        chatId: Long,
        text: JSONObject,
        replyToId: Long?,
        sendAt: Long?,
        /** An inputTextQuote for the first part's reply, when part of the original is quoted. */
        quote: JSONObject? = null
    ) {
        val raw = text.optJSONArray("entities") ?: JSONArray()
        val spans = (0 until raw.length()).map { i ->
            val entity = raw.getJSONObject(i)
            TextSpan(entity.optInt("offset"), entity.optInt("length"), entity.optJSONObject("type") ?: JSONObject())
        }
        splitLongText(text.optString("text"), spans, messageTextLimit()).forEachIndexed { index, part ->
            val entities = JSONArray()
            part.entities.forEach { span ->
                entities.put(
                    JSONObject()
                        .put("@type", "textEntity")
                        .put("offset", span.offset)
                        .put("length", span.length)
                        .put("type", span.type)
                )
            }
            sendFormattedPart(
                chatId,
                JSONObject().put("@type", "formattedText").put("text", part.text).put("entities", entities),
                if (index == 0) replyToId else null,
                sendAt,
                if (index == 0) quote else null
            )
        }
    }

    /** Telegram's limit on a message's text, asked once; 4096 until it answers. */
    private var textLimit: Int? = null

    private suspend fun messageTextLimit(): Int = textLimit ?: try {
        val option = requireEngine().send(
            JSONObject().put("@type", "getOption").put("name", "message_text_length_max")
        )
        (option.optString("value").toIntOrNull() ?: option.optInt("value"))
            .takeIf { it > 0 }
            ?.also { textLimit = it }
            ?: DEFAULT_TEXT_LIMIT
    } catch (e: TdLibException) {
        DEFAULT_TEXT_LIMIT
    }

    private suspend fun sendFormattedPart(
        chatId: Long,
        text: JSONObject,
        replyToId: Long?,
        sendAt: Long?,
        quote: JSONObject? = null
    ) {
        requireEngine().send(
            JSONObject()
                .put("@type", "sendMessage")
                .put("chat_id", chatId)
                .inOpenTopic(chatId)
                .withReplyTo(replyToId)
                .apply { if (quote != null) optJSONObject("reply_to")?.put("quote", quote) }
                .apply {
                    if (sendAt != null) {
                        put(
                            "options",
                            JSONObject()
                                .put("@type", "messageSendOptions")
                                .put(
                                    "scheduling_state",
                                    JSONObject()
                                        .put("@type", "messageSchedulingStateSendAtDate")
                                        .put("send_date", sendAt)
                                )
                        )
                    }
                }
                .put(
                    "input_message_content",
                    JSONObject()
                        .put("@type", "inputMessageText")
                        .put("text", text)
                )
        )
    }

    override suspend fun sendPoll(chatId: Long, draft: PollDraft) {
        awaitReady()
        fun text(value: String) = JSONObject().put("@type", "formattedText").put("text", value)
        val type = if (draft.isQuiz) {
            JSONObject()
                .put("@type", "inputPollTypeQuiz")
                .put("correct_option_ids", JSONArray(listOfNotNull(draft.correctIndex)))
                // The older schema's single id, for a TDLib that still wants it.
                .put("correct_option_id", draft.correctIndex ?: 0)
                .put("explanation", text(draft.explanation.trim()))
        } else {
            JSONObject().put("@type", "inputPollTypeRegular")
        }
        requireEngine().send(
            JSONObject()
                .put("@type", "sendMessage")
                .put("chat_id", chatId)
                .inOpenTopic(chatId)
                .put(
                    "input_message_content",
                    JSONObject()
                        .put("@type", "inputMessagePoll")
                        .put("question", text(draft.question.trim()))
                        .put(
                            "options",
                            JSONArray(draft.filledOptions.map { option ->
                                JSONObject().put("@type", "inputPollOption").put("text", text(option))
                            })
                        )
                        .put("is_anonymous", draft.isAnonymous)
                        .put("allows_multiple_answers", draft.allowsMultiple && !draft.isQuiz)
                        .put("allows_revoting", !draft.isQuiz)
                        .put("type", type)
                        .put("is_closed", false)
                )
        )
    }

    override suspend fun scheduledMessages(chatId: Long): List<ChatMessage> {
        awaitReady()
        val found = try {
            requireEngine().send(
                JSONObject().put("@type", "getChatScheduledMessages").put("chat_id", chatId)
            )
        } catch (e: TdLibException) {
            Log.w(TAG, "getChatScheduledMessages: ${e.message}")
            return emptyList()
        }
        val array = found.optJSONArray("messages") ?: return emptyList()
        return (0 until array.length())
            .mapNotNull { array.optJSONObject(it)?.let { raw -> mapMessage(chatId, raw) } }
            .sortedBy { it.scheduledAt ?: Long.MAX_VALUE }
    }

    override suspend fun sendScheduledNow(chatId: Long, messageId: Long) {
        awaitReady()
        // No scheduling state is TDLib's way of saying "now".
        requireEngine().send(
            JSONObject()
                .put("@type", "editMessageSchedulingState")
                .put("chat_id", chatId)
                .put("message_id", messageId)
                .put("scheduling_state", JSONObject.NULL)
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
                    val fileCaption = caption.takeIf { index == 0 }.orEmpty()
                    val quoted = replyToId.takeIf { index == 0 }
                    // Music goes as music, so it plays in the bubble with its
                    // title rather than arriving as a file to save and open.
                    if (isAudioFileName(name)) {
                        sendAudio(chatId, path, fileCaption, quoted)
                    } else {
                        sendLocalFile(chatId, path, fileCaption, photo = false, replyToId = quoted)
                    }
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
            // A video message takes no caption: Telegram has nowhere to show
            // one under a circle.
            is AttachmentDraft.VideoNote -> requireEngine().send(
                JSONObject()
                    .put("@type", "sendMessage")
                    .put("chat_id", chatId)
                    .inOpenTopic(chatId)
                    .withReplyTo(replyToId)
                    .put(
                        "input_message_content",
                        JSONObject()
                            .put("@type", "inputMessageVideoNote")
                            .put(
                                "video_note",
                                JSONObject()
                                    .put("@type", "inputVideoNote")
                                    .put("video_note", localFile(draft.path))
                                    .put("duration", draft.durationSeconds)
                                    .put("length", draft.length)
                            )
                    )
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
                        .put("photo", inputPhoto(path))
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
                    .inOpenTopic(chatId)
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
            .put(
                "voice_note",
                JSONObject()
                    .put("@type", "inputVoiceNote")
                    .put("voice_note", localFile(path))
                    .put("duration", durationSeconds)
                    // The bar chart Telegram draws behind a voice message:
                    // 5-bit samples packed into bytes and base64'd. Measured
                    // while recording, so every client that opens this message
                    // sees the shape of what was actually said.
                    .put("waveform", Base64.getEncoder().encodeToString(packWaveform(waveform)))
            )
            .put(
                "caption",
                JSONObject().put("@type", "formattedText").put("text", caption)
            )
        requireEngine().send(
            JSONObject()
                .put("@type", "sendMessage")
                .put("chat_id", chatId)
                .inOpenTopic(chatId)
                .put("input_message_content", content)
                .withReplyTo(replyToId)
        )
    }

    override suspend fun forwardMessages(
        fromChatId: Long,
        messageIds: List<Long>,
        toChatId: Long,
        withoutQuote: Boolean
    ) {
        if (messageIds.isEmpty()) return
        awaitReady()
        // Strictly increasing, or TDLib refuses the lot: the ids come in the
        // order they were selected, and selecting from the bottom up is the
        // natural way to pick a run.
        val ids = JSONArray().apply { messageIds.distinct().sorted().forEach { put(it) } }
        requireEngine().send(
            JSONObject()
                .put("@type", "forwardMessages")
                .put("chat_id", toChatId)
                .inOpenTopic(toChatId)
                .put("from_chat_id", fromChatId)
                .put("message_ids", ids)
                // The plain forward carries the author's name. A copy, asked
                // for in Settings → For geeks, arrives as the forwarder's own.
                .put("send_copy", withoutQuote)
                .put("remove_caption", false)
        )
    }

    override suspend fun messagePermissions(chatId: Long, messageId: Long): MessagePermissions? {
        awaitReady()
        return try {
            val properties = requireEngine().send(
                JSONObject()
                    .put("@type", "getMessageProperties")
                    .put("chat_id", chatId)
                    .put("message_id", messageId)
            )
            MessagePermissions(
                canEdit = properties.optBoolean("can_be_edited"),
                canDeleteForSelf = properties.optBoolean("can_be_deleted_only_for_self"),
                canDeleteForEveryone = properties.optBoolean("can_be_deleted_for_all_users"),
                canForward = properties.optBoolean("can_be_forwarded")
            )
        } catch (e: Throwable) {
            Log.w(TAG, "messagePermissions: ${e.message}")
            null
        }
    }

    override suspend fun openMessageContent(chatId: Long, messageId: Long) {
        awaitReady()
        try {
            requireEngine().send(
                JSONObject()
                    .put("@type", "openMessageContent")
                    .put("chat_id", chatId)
                    .put("message_id", messageId)
            )
        } catch (e: Throwable) {
            // The playback goes on either way; only the sender's "listened"
            // mark is missed.
            Log.w(TAG, "openMessageContent: ${e.message}")
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
                        .put("text", formatted(text))
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
        val reactionType = reactionTypeOf(emoji)

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

    override suspend fun votePoll(chatId: Long, messageId: Long, optionIds: List<Int>) {
        awaitReady()
        requireEngine().send(
            JSONObject()
                .put("@type", "setPollAnswer")
                .put("chat_id", chatId)
                .put("message_id", messageId)
                .put("option_ids", JSONArray(optionIds))
        )
        // The same guess the screen drew, so a reload before
        // updateMessageContent arrives does not undo the vote.
        messagesByChat[chatId]?.let { bucket ->
            val index = bucket.indexOfFirst { it.id == messageId }
            val poll = bucket.getOrNull(index)?.poll ?: return@let
            bucket[index] = bucket[index].copy(poll = poll.withVote(optionIds.toSet()))
        }
    }

    override suspend fun pressButton(chatId: Long, messageId: Long, data: String): CallbackAnswer? {
        awaitReady()
        val answer = requireEngine().send(
            JSONObject()
                .put("@type", "getCallbackQueryAnswer")
                .put("chat_id", chatId)
                .put("message_id", messageId)
                .put(
                    "payload",
                    // bytes travel as base64 in TDLib's JSON, which is the
                    // form the button's data arrived in; it goes back as is.
                    JSONObject().put("@type", "callbackQueryPayloadData").put("data", data)
                )
        )
        return CallbackAnswer(
            text = answer.optString("text"),
            showAlert = answer.optBoolean("show_alert"),
            url = answer.optString("url")
        ).takeIf { it.text.isNotBlank() || it.url.isNotBlank() }
    }

    /** Fetches the message a chat's bot keyboard lives on and reads it. */
    private suspend fun loadReplyKeyboard(chatId: Long, messageId: Long) {
        val message = try {
            requireEngine().send(
                JSONObject()
                    .put("@type", "getMessage")
                    .put("chat_id", chatId)
                    .put("message_id", messageId)
            )
        } catch (e: TdLibException) {
            return
        }
        setReplyKeyboard(chatId, replyKeyboardOf(message.optJSONObject("reply_markup")))
    }

    private fun setReplyKeyboard(chatId: Long, keyboard: ReplyKeyboard?) {
        _replyKeyboards.update {
            if (keyboard == null || keyboard.isEmpty) it - chatId else it + (chatId to keyboard)
        }
    }

    /**
     * Read from the cached chat rather than asked for.
     *
     * A chat carries its own `available_reactions`: every emoji, a restricted
     * list, or nothing at all. Offering the full set in a group that permits
     * three would be a tap the server refuses for a reason this already knows.
     */
    /** Telegram's animation for each emoji reaction, fetched once for the run. */
    private val reactionAnimations = java.util.concurrent.ConcurrentHashMap<String, StickerContent>()

    override suspend fun messageReactions(chatId: Long, messageId: Long): List<ReactionOption> {
        awaitReady()
        // Key to whether it needs Premium, in Telegram's order.
        val offered: List<Pair<String, Boolean>> = try {
            val answer = requireEngine().send(
                JSONObject()
                    .put("@type", "getMessageAvailableReactions")
                    .put("chat_id", chatId)
                    .put("message_id", messageId)
                    .put("row_size", QUICK_REACTION_COUNT)
            )
            // Top first — what this account and chat use most — then the
            // rest, custom emoji (Premium's) among them.
            listOf("top_reactions", "recent_reactions", "popular_reactions")
                .flatMap { key ->
                    val list = answer.optJSONArray(key) ?: return@flatMap emptyList<Pair<String, Boolean>>()
                    List(list.length()) { list.optJSONObject(it) }.mapNotNull { available ->
                        reactionKeyOf(available?.optJSONObject("type"))
                            ?.let { it to available!!.optBoolean("needs_premium") }
                    }
                }
                .distinctBy { it.first }
        } catch (e: TdLibException) {
            Log.w(TAG, "getMessageAvailableReactions: ${e.message}")
            availableReactions(chatId).map { it to false }
        }
        val custom = customEmoji(offered.mapNotNull { customEmojiIdOf(it.first) })
        return coroutineScope {
            offered.map { (key, premium) ->
                async {
                    val customId = customEmojiIdOf(key)
                    ReactionOption(
                        emoji = key,
                        animation = if (customId != null) custom[customId] else reactionAnimation(key),
                        needsPremium = premium
                    )
                }
            }.awaitAll()
        }
    }

    /** Custom emoji fetched this run, by id; they do not change. */
    private val customEmojiCache = java.util.concurrent.ConcurrentHashMap<Long, StickerContent>()

    override suspend fun customEmoji(ids: List<Long>): Map<Long, StickerContent> {
        val missing = ids.distinct().filterNot { customEmojiCache.containsKey(it) }
        if (missing.isNotEmpty()) {
            awaitReady()
            try {
                val answer = requireEngine().send(
                    JSONObject()
                        .put("@type", "getCustomEmojiStickers")
                        .put("custom_emoji_ids", JSONArray(missing))
                )
                val stickers = answer.optJSONArray("stickers")
                for (i in 0 until (stickers?.length() ?: 0)) {
                    val sticker = stickers!!.optJSONObject(i) ?: continue
                    val id = sticker.optJSONObject("full_type")?.optInt64("custom_emoji_id") ?: continue
                    if (id != 0L) customEmojiCache[id] = stickerOf(sticker)
                }
            } catch (e: TdLibException) {
                Log.w(TAG, "getCustomEmojiStickers: ${e.message}")
            }
        }
        return ids.mapNotNull { id -> customEmojiCache[id]?.let { id to it } }.toMap()
    }

    /** The emoji's centre animation, which plays on its own in a small square. */
    private suspend fun reactionAnimation(emoji: String): StickerContent? {
        reactionAnimations[emoji]?.let { return it }
        return try {
            val reaction = requireEngine().send(JSONObject().put("@type", "getEmojiReaction").put("emoji", emoji))
            val sticker = (reaction.optJSONObject("center_animation") ?: reaction.optJSONObject("static_icon"))
                ?.let { stickerOf(it).copy(emoji = emoji) }
            sticker?.also { reactionAnimations[emoji] = it }
        } catch (e: TdLibException) {
            null
        }
    }

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
    override val canStream: Boolean get() = true

    /**
     * TDLib downloads from any offset and writes each part where it belongs
     * in the file, so what is there from an offset can be read straight off
     * the phone while the rest comes in — the official client streams a
     * video or a track the same way (1.9). Priority 32, above everything
     * else: this is being watched or listened to now.
     */
    override suspend fun streamFile(fileId: Int, offset: Long): FileStream? {
        awaitReady()
        return try {
            val file = requireEngine().send(
                JSONObject()
                    .put("@type", "downloadFile")
                    .put("file_id", fileId)
                    .put("priority", 32)
                    .put("offset", offset)
                    .put("limit", 0)
                    .put("synchronous", false)
            )
            fileStream(file, fileId, offset)
        } catch (e: TdLibException) {
            Log.w(TAG, "streamFile($fileId, $offset): ${e.message}")
            null
        }
    }

    override suspend fun streamedFrom(fileId: Int, offset: Long): FileStream? {
        awaitReady()
        return try {
            val file = requireEngine().send(JSONObject().put("@type", "getFile").put("file_id", fileId))
            val stream = fileStream(file, fileId, offset)
            val local = file.optJSONObject("local")
            // Stopped short — the connection dropped, or something else
            // asked for the file from elsewhere: asked for again from here.
            if (!stream.isComplete && stream.readyFromOffset == 0L && local?.optBoolean("is_downloading_active") != true) {
                streamFile(fileId, offset) ?: stream
            } else {
                stream
            }
        } catch (e: TdLibException) {
            Log.w(TAG, "streamedFrom($fileId, $offset): ${e.message}")
            null
        }
    }

    override suspend fun stopStreaming(fileId: Int) {
        try {
            requireEngine().send(
                JSONObject().put("@type", "cancelDownloadFile").put("file_id", fileId).put("only_if_pending", false)
            )
        } catch (e: TdLibException) {
            Log.w(TAG, "stopStreaming($fileId): ${e.message}")
        }
    }

    /** [file] as a FileStream from [offset]: TDLib counts what is ready from there itself. */
    private suspend fun fileStream(file: JSONObject, fileId: Int, offset: Long): FileStream {
        val local = file.optJSONObject("local")
        val size = file.optLong("size").takeIf { it > 0 } ?: file.optLong("expected_size")
        val complete = local?.optBoolean("is_downloading_completed") == true
        val ready = if (complete) {
            (size - offset).coerceAtLeast(0)
        } else {
            requireEngine().send(
                JSONObject().put("@type", "getFileDownloadedPrefixSize").put("file_id", fileId).put("offset", offset)
            ).optLong("size")
        }
        return FileStream(
            path = local?.optString("path")?.takeIf { it.isNotBlank() },
            size = size,
            readyFromOffset = ready,
            isComplete = complete
        )
    }

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

    /**
     * Every active story of the chat behind [storyId], oldest first, with
     * what each one shows.
     *
     * `getStory` for each: the active-stories update only lists ids. A story
     * that cannot be fetched — deleted in the meantime, or expired — is left
     * out rather than failing the rest.
     */
    override suspend fun storyFrames(storyId: Long): List<StoryFrame> {
        awaitReady()
        val chatId = storyKeys[storyId] ?: return emptyList()
        val active = activeStories[chatId] ?: return emptyList()
        val maxRead = active.optInt("max_read_story_id")
        return activeStoryIds(active).mapNotNull { id ->
            try {
                val story = requireEngine().send(
                    JSONObject()
                        .put("@type", "getStory")
                        .put("story_poster_chat_id", chatId)
                        .put("story_sender_chat_id", chatId)
                        .put("story_id", id)
                        .put("only_local", false)
                )
                storyFrame(story, isSeen = id <= maxRead)
            } catch (e: TdLibException) {
                Log.d(TAG, "getStory($chatId, $id): ${e.message}")
                null
            }
        }
    }

    /**
     * Opening a story is what tells Telegram it was seen. Opened and closed
     * again straight away: a story left open keeps TDLib polling for it.
     */
    override suspend fun postStory(uri: String, isVideo: Boolean, caption: String, audience: StoryAudience) {
        awaitReady()
        // A story from the account itself is posted through its Saved
        // Messages chat, whose id is the account's own; made sure of first,
        // since canPostStory only knows chats TDLib has loaded.
        val me = _authState.value.me?.id ?: fetchMe()?.id ?: error("Not signed in")
        requireEngine().send(JSONObject().put("@type", "createPrivateChat").put("user_id", me).put("force", false))
        val can = requireEngine().send(JSONObject().put("@type", "canPostStory").put("chat_id", me))
        storyRefusal(can)?.let { error(it) }
        val path = copyUriToCache(uri, if (isVideo) "story.mp4" else "story.jpg")
        val file = JSONObject().put("@type", "inputFileLocal").put("path", path)
        val content = if (isVideo) {
            val seconds = videoSeconds(path)
            if (seconds > STORY_VIDEO_MAX_SECONDS) error("A story video can be at most a minute long")
            JSONObject()
                .put("@type", "inputStoryContentVideo")
                .put("video", file)
                .put("added_sticker_file_ids", JSONArray())
                .put("duration", seconds)
                .put("cover_frame_timestamp", 0.0)
                .put("is_animation", false)
        } else {
            JSONObject()
                .put("@type", "inputStoryContentPhoto")
                .put("photo", file)
                .put("added_sticker_file_ids", JSONArray())
        }
        val privacy = when (audience) {
            StoryAudience.Everyone -> JSONObject()
                .put("@type", "storyPrivacySettingsEveryone").put("except_user_ids", JSONArray())
            StoryAudience.Contacts -> JSONObject()
                .put("@type", "storyPrivacySettingsContacts").put("except_user_ids", JSONArray())
            StoryAudience.CloseFriends -> JSONObject().put("@type", "storyPrivacySettingsCloseFriends")
        }
        requireEngine().send(
            JSONObject()
                .put("@type", "postStory")
                .put("chat_id", me)
                .put("content", content)
                .put("caption", formatted(caption))
                .put("privacy_settings", privacy)
                .put("album_ids", JSONArray())
                .put("active_period", STORY_ACTIVE_SECONDS)
                .put("is_posted_to_chat_page", true)
                .put("protect_content", false)
        )
        // The story uploads from here on; TDLib says how it went with
        // updateStoryPostSucceeded or updateStoryPostFailed.
    }

    /** What canPostStory's answer means for a person, or null for go ahead. */
    private fun storyRefusal(answer: JSONObject): String? = when (answer.optString("@type")) {
        "canPostStoryResultOk" -> null
        "canPostStoryResultPremiumNeeded" -> "Posting more stories needs Telegram Premium"
        "canPostStoryResultActiveStoryLimitExceeded" -> "You have as many stories up as Telegram allows"
        "canPostStoryResultWeeklyLimitExceeded" -> "That is this week's limit of stories"
        "canPostStoryResultMonthlyLimitExceeded" -> "That is this month's limit of stories"
        "canPostStoryResultBoostNeeded" -> "This chat needs boosts to post stories"
        else -> null
    }

    /** A video file's length in seconds, as Telegram wants it for a story. */
    private fun videoSeconds(path: String): Double {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) / 1000.0
        } catch (_: RuntimeException) {
            0.0
        } finally {
            retriever.release()
        }
    }

    override suspend fun markStorySeen(storyId: Long, frameId: Int) {
        val chatId = storyKeys[storyId] ?: return
        // Seen to the end: the circle's ring goes grey now rather than when
        // the server's echo arrives.
        val last = activeStories[chatId]?.let { activeStoryIds(it) }?.maxOrNull()
        if (last != null && frameId >= last) {
            _stories.update { list ->
                list.map { if (it.id == storyId) it.copy(hasUnseen = false) else it }
            }
        }
        try {
            awaitReady()
            for (type in listOf("openStory", "closeStory")) {
                requireEngine().send(
                    JSONObject()
                        .put("@type", type)
                        .put("story_poster_chat_id", chatId)
                        .put("story_sender_chat_id", chatId)
                        .put("story_id", frameId)
                )
            }
        } catch (e: TdLibException) {
            Log.d(TAG, "markStorySeen: ${e.message}")
        }
    }

    // ── proxies ──────────────────────────────────────────────────────────
    //
    // No awaitReady in any of these: TDLib takes proxies before sign-in,
    // which is exactly when someone who needs one needs it.

    override suspend fun proxies(): List<ProxyServer> {
        val list = requireEngine().send(JSONObject().put("@type", "getProxies"))
            .optJSONArray("proxies") ?: return emptyList()
        return (0 until list.length()).mapNotNull { index ->
            val added = list.optJSONObject(index) ?: return@mapNotNull null
            val proxy = added.optJSONObject("proxy") ?: return@mapNotNull null
            val type = proxy.optJSONObject("type")
            ProxyServer(
                id = added.optInt("id"),
                server = proxy.optString("server"),
                port = proxy.optInt("port"),
                kind = when (type?.optString("@type")) {
                    "proxyTypeSocks5" -> ProxyKind.Socks5
                    "proxyTypeHttp" -> ProxyKind.Http
                    else -> ProxyKind.MtProto
                },
                username = type?.optString("username").orEmpty(),
                password = type?.optString("password").orEmpty(),
                secret = type?.optString("secret").orEmpty(),
                isEnabled = added.optBoolean("is_enabled")
            )
        }
    }

    override suspend fun addProxy(proxy: ProxyServer, enable: Boolean): Int =
        requireEngine().send(
            JSONObject()
                .put("@type", "addProxy")
                .put("proxy", proxyObject(proxy))
                .put("enable", enable)
                .put("comment", "")
        ).optInt("id")

    override suspend fun enableProxy(id: Int) {
        requireEngine().send(JSONObject().put("@type", "enableProxy").put("proxy_id", id))
    }

    override suspend fun disableProxy() {
        requireEngine().send(JSONObject().put("@type", "disableProxy"))
    }

    override suspend fun removeProxy(id: Int) {
        requireEngine().send(JSONObject().put("@type", "removeProxy").put("proxy_id", id))
    }

    override suspend fun pingProxy(proxy: ProxyServer): Long? = try {
        val seconds = requireEngine().send(
            JSONObject().put("@type", "pingProxy").put("proxy", proxyObject(proxy))
        ).optDouble("seconds")
        (seconds * 1000).toLong().takeIf { !seconds.isNaN() }
    } catch (e: TdLibException) {
        Log.d(TAG, "pingProxy: ${e.message}")
        null
    }

    // ── sessions ─────────────────────────────────────────────────────────

    override suspend fun activeSessions(): List<ActiveSession> {
        awaitReady()
        val list = requireEngine().send(JSONObject().put("@type", "getActiveSessions"))
            .optJSONArray("sessions") ?: return emptyList()
        return (0 until list.length()).mapNotNull { index ->
            val raw = list.optJSONObject(index) ?: return@mapNotNull null
            ActiveSession(
                id = raw.optInt64("id"),
                isCurrent = raw.optBoolean("is_current"),
                kind = deviceKindOf(raw.optJSONObject("device_type")?.optString("@type").orEmpty()),
                applicationName = raw.optString("application_name"),
                applicationVersion = raw.optString("application_version"),
                isOfficialApplication = raw.optBoolean("is_official_application"),
                deviceModel = raw.optString("device_model"),
                platform = raw.optString("platform"),
                systemVersion = raw.optString("system_version"),
                lastActiveDate = raw.optLong("last_active_date"),
                ipAddress = raw.optString("ip_address"),
                location = raw.optString("location"),
                isPasswordPending = raw.optBoolean("is_password_pending")
            )
        }
    }

    override suspend fun terminateSession(id: Long) {
        awaitReady()
        requireEngine().send(JSONObject().put("@type", "terminateSession").put("session_id", id.toString()))
    }

    override suspend fun terminateOtherSessions() {
        awaitReady()
        requireEngine().send(JSONObject().put("@type", "terminateAllOtherSessions"))
    }

    // ── privacy ──────────────────────────────────────────────────────────

    override suspend fun privacyRules(setting: PrivacySetting): PrivacyRules {
        awaitReady()
        val answer = requireEngine().send(
            JSONObject()
                .put("@type", "getUserPrivacySettingRules")
                .put("setting", JSONObject().put("@type", setting.tdType))
        )
        val rules = answer.optJSONArray("rules") ?: JSONArray()
        return privacyRulesOf(
            (0 until rules.length()).mapNotNull { index ->
                val rule = rules.optJSONObject(index) ?: return@mapNotNull null
                val named = rule.optJSONArray("user_ids") ?: rule.optJSONArray("chat_ids")
                Triple(rule.optString("@type"), named?.length() ?: 0, rule.toString())
            }
        )
    }

    override suspend fun setPrivacyRules(setting: PrivacySetting, rules: PrivacyRules) {
        awaitReady()
        val list = JSONArray()
        rules.exceptions.forEach { list.put(JSONObject(it.raw)) }
        audienceRules(rules.audience).forEach { list.put(JSONObject().put("@type", it)) }
        requireEngine().send(
            JSONObject()
                .put("@type", "setUserPrivacySettingRules")
                .put("setting", JSONObject().put("@type", setting.tdType))
                .put("rules", JSONObject().put("@type", "userPrivacySettingRules").put("rules", list))
        )
    }

    // ── storage ──────────────────────────────────────────────────────────

    /**
     * chat_limit 0 folds every chat into one entry, which is all this needs:
     * the screen counts by kind of file, not by chat. The database's size
     * comes from the fast statistics, which read it without walking files.
     */
    override suspend fun storageUsage(): StorageUsage {
        awaitReady()
        val stats = requireEngine().send(
            JSONObject().put("@type", "getStorageStatistics").put("chat_limit", 0)
        )
        val database = try {
            requireEngine().send(JSONObject().put("@type", "getStorageStatisticsFast"))
                .optLong("database_size")
        } catch (e: TdLibException) {
            Log.d(TAG, "getStorageStatisticsFast: ${e.message}")
            0L
        }
        return StorageUsage(storageSlices(byFileType(stats)), database)
    }

    /**
     * optimizeStorage with every limit at zero deletes everything it is
     * pointed at; file_types is what points it. Passed explicitly, the types
     * reach profile photos and stickers too, which TDLib's default spares.
     */
    override suspend fun clearCache(kinds: Set<StorageKind>): StorageUsage {
        awaitReady()
        if (kinds.isEmpty()) return storageUsage()
        val types = JSONArray()
        kinds.flatMap { it.tdTypes }.forEach { types.put(JSONObject().put("@type", it)) }
        requireEngine().send(
            JSONObject()
                .put("@type", "optimizeStorage")
                .put("size", 0)
                .put("ttl", 0)
                .put("count", 0)
                .put("immunity_delay", 0)
                .put("file_types", types)
                .put("chat_ids", JSONArray())
                .put("exclude_chat_ids", JSONArray())
                .put("return_deleted_file_statistics", false)
                .put("chat_limit", 0)
        )
        // Whatever was deleted is wanted back where it shows: the avatars
        // on the chat list are asked for again now, rather than left on
        // initials (1.6.10). A fresh start for their attempts too.
        if (StorageKind.ProfilePhotos in kinds) {
            requestedPhotos.clear()
            downloadedPhotos.clear()
            photoAttempts.clear()
            republishPhotos()
        }
        return storageUsage()
    }

    /** Every (file type, size, count) across a `storageStatistics`'s chats. */
    private fun byFileType(stats: JSONObject): List<Triple<String, Long, Int>> {
        val chats = stats.optJSONArray("by_chat") ?: return emptyList()
        val out = ArrayList<Triple<String, Long, Int>>()
        for (c in 0 until chats.length()) {
            val types = chats.optJSONObject(c)?.optJSONArray("by_file_type") ?: continue
            for (t in 0 until types.length()) {
                val entry = types.optJSONObject(t) ?: continue
                out += Triple(
                    entry.optJSONObject("file_type")?.optString("@type").orEmpty(),
                    entry.optLong("size"),
                    entry.optInt("count")
                )
            }
        }
        return out
    }

    // ── stickers ─────────────────────────────────────────────────────────

    override suspend fun stickerSets(): List<StickerSetPreview> = installedSets("stickerTypeRegular")

    override suspend fun customEmojiSets(): List<StickerSetPreview> = installedSets("stickerTypeCustomEmoji")

    /** The account's added sets of one kind — stickers, or custom emoji. */
    private suspend fun installedSets(type: String): List<StickerSetPreview> {
        awaitReady()
        val sets = requireEngine().send(
            JSONObject()
                .put("@type", "getInstalledStickerSets")
                .put("sticker_type", JSONObject().put("@type", type))
        ).optJSONArray("sets") ?: return emptyList()
        return (0 until sets.length()).mapNotNull { index ->
            val set = sets.optJSONObject(index) ?: return@mapNotNull null
            // The set's own picture where it has one, else its first sticker —
            // `covers` holds a few of them for exactly this.
            val cover = set.optJSONObject("thumbnail")?.let { thumbnail ->
                stickerFromThumbnail(thumbnail, set.optString("title"))
            } ?: set.optJSONArray("covers")?.optJSONObject(0)?.let(::stickerOf)
            StickerSetPreview(
                id = set.optInt64("id"),
                title = set.optString("title"),
                cover = cover
            )
        }
    }

    override suspend fun stickerSet(setId: Long): List<StickerContent> {
        awaitReady()
        val stickers = requireEngine().send(
            JSONObject().put("@type", "getStickerSet").put("set_id", setId.toString())
        ).optJSONArray("stickers") ?: return emptyList()
        return (0 until stickers.length()).mapNotNull { stickers.optJSONObject(it)?.let(::stickerOf) }
    }

    override suspend fun recentStickers(): List<StickerContent> {
        awaitReady()
        val stickers = requireEngine().send(
            JSONObject().put("@type", "getRecentStickers").put("is_attached", false)
        ).optJSONArray("stickers") ?: return emptyList()
        return (0 until stickers.length()).mapNotNull { stickers.optJSONObject(it)?.let(::stickerOf) }
    }

    /**
     * By the file TDLib already has: a sticker from a set is on Telegram's
     * servers, and naming its file id sends it without uploading anything.
     * The file goes inside an `inputSticker` — the same wrapping photos
     * needed, which unwrapped is "Input file is not specified".
     */
    override suspend fun sendSticker(chatId: Long, sticker: StickerContent, replyToId: Long?) {
        awaitReady()
        val fileId = sticker.fileId ?: error("This sticker has no file to send")
        requireEngine().send(
            JSONObject()
                .put("@type", "sendMessage")
                .put("chat_id", chatId)
                .inOpenTopic(chatId)
                .withReplyTo(replyToId)
                .put(
                    "input_message_content",
                    JSONObject()
                        .put("@type", "inputMessageSticker")
                        .put(
                            "sticker",
                            JSONObject()
                                .put("@type", "inputSticker")
                                .put("sticker", JSONObject().put("@type", "inputFileId").put("id", fileId))
                                .put("width", sticker.width)
                                .put("height", sticker.height)
                        )
                        .put("emoji", sticker.emoji)
                )
        )
    }

    // ── GIFs ─────────────────────────────────────────────────────────────

    override suspend fun savedGifs(): List<GifItem> {
        awaitReady()
        val answer = requireEngine().send(JSONObject().put("@type", "getSavedAnimations"))
        val animations = answer.optJSONArray("animations") ?: return emptyList()
        return List(animations.length()) { animations.optJSONObject(it) }.mapNotNull { gifItem(it) }
    }

    override suspend fun saveGif(video: VideoContent) {
        awaitReady()
        val fileId = video.fileId ?: error("This GIF has no file to keep")
        requireEngine().send(
            JSONObject()
                .put("@type", "addSavedAnimation")
                .put("animation", JSONObject().put("@type", "inputFileId").put("id", fileId))
        )
    }

    // ── inline bots and Mini Apps (2.0) ──────────────────────────────────

    /** Bots found by name, and names found to be no inline bot: each asked once. */
    private val inlineBots = ConcurrentHashMap<String, java.util.Optional<InlineBot>>()

    override suspend fun inlineBot(username: String): InlineBot? {
        awaitReady()
        val key = username.lowercase()
        inlineBots[key]?.let { return it.orElse(null) }
        val found = try {
            val chat = requireEngine().send(JSONObject().put("@type", "searchPublicChat").put("username", username))
            val userId = chat.optJSONObject("type")
                ?.takeIf { it.optString("@type") == "chatTypePrivate" }
                ?.optLong("user_id")
                ?.takeIf { it != 0L }
            val type = userId?.let { userObject(it) }?.optJSONObject("type")
            if (userId != null && type?.optString("@type") == "userTypeBot" && type.optBoolean("is_inline")) {
                InlineBot(
                    userId = userId,
                    username = username,
                    placeholder = type.optString("inline_query_placeholder")
                )
            } else {
                null
            }
        } catch (e: TdLibException) {
            null
        }
        inlineBots[key] = java.util.Optional.ofNullable(found)
        return found
    }

    override suspend fun inlineResults(botId: Long, chatId: Long, query: String, offset: String): InlineResults? {
        awaitReady()
        val answer = requireEngine().send(
            JSONObject()
                .put("@type", "getInlineQueryResults")
                .put("bot_user_id", botId)
                .put("chat_id", chatId)
                .put("query", query)
                .put("offset", offset)
        )
        val results = answer.optJSONArray("results") ?: JSONArray()
        return InlineResults(
            queryId = answer.optInt64("inline_query_id"),
            results = List(results.length()) { results.optJSONObject(it) }.mapNotNull { inlineResultOf(it) },
            nextOffset = answer.optString("next_offset")
        )
    }

    override suspend fun sendInlineResult(chatId: Long, queryId: Long, resultId: String, replyToId: Long?) {
        awaitReady()
        requireEngine().send(
            JSONObject()
                .put("@type", "sendInlineQueryResultMessage")
                .put("chat_id", chatId)
                .inOpenTopic(chatId)
                .withReplyTo(replyToId)
                .put("query_id", queryId.toString())
                .put("result_id", resultId)
                .put("hide_via_bot", false)
        )
    }

    override suspend fun openWebApp(chatId: Long, botId: Long, url: String, theme: WebAppTheme): WebAppSession? {
        awaitReady()
        return try {
            val info = requireEngine().send(
                JSONObject()
                    .put("@type", "openWebApp")
                    .put("chat_id", chatId)
                    .put("bot_user_id", botId)
                    .put("url", url)
                    .inOpenTopic(chatId)
                    .put(
                        "parameters",
                        JSONObject()
                            .put("@type", "webAppOpenParameters")
                            .put("theme", themeParametersOf(theme))
                            .put("application_name", "TelegramYou")
                            .put("mode", JSONObject().put("@type", "webAppOpenModeFullSize"))
                    )
            )
            WebAppSession(launchId = info.optInt64("launch_id"), url = info.optString("url"))
                .takeIf { it.url.isNotBlank() }
        } catch (e: TdLibException) {
            Log.w(TAG, "openWebApp: ${e.message}")
            null
        }
    }

    override suspend fun closeWebApp(launchId: Long) {
        if (launchId == 0L) return
        runCatching {
            requireEngine().send(
                JSONObject().put("@type", "closeWebApp").put("web_app_launch_id", launchId.toString())
            )
        }
    }

    private fun themeParametersOf(theme: WebAppTheme): JSONObject = JSONObject()
        .put("@type", "themeParameters")
        .put("background_color", theme.background)
        .put("secondary_background_color", theme.secondaryBackground)
        .put("header_background_color", theme.headerBackground)
        .put("bottom_bar_background_color", theme.bottomBarBackground)
        .put("section_background_color", theme.sectionBackground)
        .put("section_separator_color", theme.sectionSeparator)
        .put("text_color", theme.text)
        .put("accent_text_color", theme.accentText)
        .put("section_header_text_color", theme.sectionHeaderText)
        .put("subtitle_text_color", theme.subtitleText)
        .put("destructive_text_color", theme.destructiveText)
        .put("hint_color", theme.hint)
        .put("link_color", theme.link)
        .put("button_color", theme.button)
        .put("button_text_color", theme.buttonText)

    /** The @gif bot's user id, found once; the bot's handle does not change. */
    @Volatile
    private var gifBotId: Long? = null

    override suspend fun searchGifs(query: String): List<GifItem> {
        awaitReady()
        val botId = gifBotId ?: requireEngine()
            .send(JSONObject().put("@type", "searchPublicChat").put("username", "gif"))
            .optJSONObject("type")
            ?.optLong("user_id")
            ?.takeIf { it != 0L }
            ?.also { gifBotId = it }
            ?: return emptyList()
        val answer = requireEngine().send(
            JSONObject()
                .put("@type", "getInlineQueryResults")
                .put("bot_user_id", botId)
                .put("chat_id", 0)
                .put("query", query.trim())
                .put("offset", "")
        )
        val results = answer.optJSONArray("results") ?: return emptyList()
        return List(results.length()) { results.optJSONObject(it) }
            .filter { it?.optString("@type") == "inlineQueryResultAnimation" }
            .mapNotNull { gifItem(it.optJSONObject("animation")) }
    }

    override suspend fun sendGif(chatId: Long, gif: GifItem, replyToId: Long?) {
        awaitReady()
        val fileId = gif.video.fileId ?: error("This GIF has no file to send")
        requireEngine().send(
            JSONObject()
                .put("@type", "sendMessage")
                .put("chat_id", chatId)
                .inOpenTopic(chatId)
                .withReplyTo(replyToId)
                .put(
                    "input_message_content",
                    JSONObject()
                        .put("@type", "inputMessageAnimation")
                        .put(
                            "animation",
                            JSONObject()
                                .put("@type", "inputAnimation")
                                .put("animation", JSONObject().put("@type", "inputFileId").put("id", fileId))
                                .put("added_sticker_file_ids", JSONArray())
                                .put("duration", gif.video.durationSeconds)
                                .put("width", gif.width)
                                .put("height", gif.height)
                        )
                        .put("show_caption_above_media", false)
                        .put("has_spoiler", false)
                )
        )
    }

    /** A TDLib `animation` as the picker's item, read the way a GIF message is. */
    private fun gifItem(animation: JSONObject?): GifItem? {
        if (animation == null) return null
        val video = videoContent(JSONObject().put("@type", "messageAnimation").put("animation", animation))
            ?: return null
        val file = animation.optJSONObject("animation")
        val id = file?.optJSONObject("remote")?.optString("unique_id")?.takeIf { it.isNotBlank() }
            ?: video.fileId?.toString()
            ?: return null
        return GifItem(id = id, video = video, width = animation.optInt("width"), height = animation.optInt("height"))
    }

    override suspend fun logout() {
        try {
            requireEngine().send(JSONObject().put("@type", "logOut"))
        } catch (_: Throwable) {
            // ignore
        }
        // Not the phone screen yet: TDLib is closing this instance, and
        // the new one it gets replaced with says when it wants a number —
        // a number typed before then went to the closed one and was lost.
        _authState.value = AuthUiState(state = AuthState.Bootstrapping, isLoading = true)
        // Everything the last account left behind: the next one to sign in
        // must not see its folders, its archive or its cached messages.
        chatsById.clear()
        usersById.clear()
        basicGroups.clear()
        supergroups.clear()
        messagesByChat.clear()
        positions.clear()
        storyKeys.clear()
        requestedPhotos.clear()
        downloadedPhotos.clear()
        photoAttempts.clear()
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

    override suspend fun setProfilePhoto(uri: String) {
        awaitReady()
        val path = copyUriToCache(uri, "profile_${System.currentTimeMillis()}.jpg")
        requireEngine().send(
            JSONObject()
                .put("@type", "setProfilePhoto")
                .put(
                    "photo",
                    JSONObject().put("@type", "inputChatPhotoStatic").put("photo", localFile(path))
                )
                .put("is_public", false)
        )
        refreshMe()
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

    /**
     * A music file. The title, the performer and the length are left for
     * Telegram to read from the file's own tags.
     */
    private suspend fun sendAudio(chatId: Long, path: String, caption: String, replyToId: Long?) {
        requireEngine().send(
            JSONObject()
                .put("@type", "sendMessage")
                .put("chat_id", chatId)
                .inOpenTopic(chatId)
                .withReplyTo(replyToId)
                .put(
                    "input_message_content",
                    JSONObject()
                        .put("@type", "inputMessageAudio")
                        .put("audio", JSONObject().put("@type", "inputAudio").put("audio", localFile(path)))
                        .put("caption", JSONObject().put("@type", "formattedText").put("text", caption))
                )
        )
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
                .put("photo", inputPhoto(path))
                .put(
                    "caption",
                    JSONObject().put("@type", "formattedText").put("text", caption)
                )
        } else {
            JSONObject()
                .put("@type", "inputMessageDocument")
                .put(
                    "document",
                    JSONObject()
                        .put("@type", "inputDocument")
                        .put("document", localFile(path))
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
                .inOpenTopic(chatId)
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
                val before = supergroups.put(group.optLong("id"), group)
                // A group turned into a forum, or back, opens differently:
                // the list has to learn it, and it often arrives after the chat.
                if (before?.optBoolean("is_forum") != group.optBoolean("is_forum")) requestPublishChats()
            }
            "updateChatAction" -> {
                val chatId = update.optLong("chat_id")
                val action = update.optJSONObject("action")?.optString("@type").orEmpty()
                // Who: a person, or a chat writing as itself, by its id negated.
                val senderObject = update.optJSONObject("sender_id")
                val sender = when (senderObject?.optString("@type")) {
                    "messageSenderUser" -> senderObject.optLong("user_id")
                    "messageSenderChat" -> -senderObject.optLong("chat_id")
                    else -> 0L
                }
                if (action == "chatActionCancel" || action.isEmpty()) {
                    typingUntil[chatId]?.remove(sender)
                } else {
                    // Typing, recording a voice note, choosing a sticker:
                    // all of them are someone in the middle of writing.
                    val senders = typingUntil.getOrPut(chatId) { ConcurrentHashMap() }
                    val now = System.currentTimeMillis()
                    senders.entries.removeIf { it.value <= now }
                    senders[sender] = now + TYPING_MILLIS
                    // Redrawn once it has lapsed, in case no cancel comes.
                    scope.launch {
                        delay(TYPING_MILLIS + 100)
                        requestPublishChats()
                    }
                }
                requestPublishChats()
            }
            "updateUserStatus" -> {
                // Someone came online or went away. Only the status changes,
                // and the chat list redraws for the dot beside their avatar.
                val user = usersById[update.optLong("user_id")] ?: return
                user.put("status", update.optJSONObject("status"))
                requestPublishChats()
            }
            "updateFile" -> {
                // The only place progress comes from. TDLib does not answer
                // a download with a stream of percentages; it announces the
                // file, repeatedly, as more of it arrives — the same update
                // for a file being sent, with the bytes on the other side of
                // the object.
                val file = update.optJSONObject("file") ?: return
                val id = file.optInt("id")
                if (id in requestedPhotos) {
                    val path = file.localPathIfDownloaded()
                    val local = file.optJSONObject("local")
                    when {
                        path != null -> {
                            downloadedPhotos[id] = path
                            republishPhotos()
                        }
                        // Stopped short, or deleted under us: free to be
                        // asked for again, the next time it is wanted.
                        local?.optBoolean("is_downloading_active") != true -> {
                            requestedPhotos.remove(id)
                            downloadedPhotos.remove(id)
                        }
                    }
                }
                val local = file.optJSONObject("local")
                val remote = file.optJSONObject("remote")
                val downloading = local?.optBoolean("is_downloading_active") == true
                val uploading = remote?.optBoolean("is_uploading_active") == true
                // A listed download someone is waiting on: done, or stopped
                // short — asked to by the person, or not.
                downloadWaiters[id]?.let { waiter ->
                    val done = file.localPathIfDownloaded()
                    if (downloading) seenRunning += id
                    when {
                        done != null -> waiter.complete(DownloadOutcome.Done(done))
                        !downloading && id in seenRunning -> waiter.complete(
                            if (id in stoppedByPerson) DownloadOutcome.Stopped else DownloadOutcome.Failed
                        )
                    }
                }
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
            // A draft saved here or on another device. It comes with the
            // chat's positions, since a draft can lift a chat in the list.
            "updateChatDraftMessage" -> {
                val chatId = update.optLong("chat_id")
                val chat = chatsById[chatId] ?: return
                chat.put("draft_message", update.optJSONObject("draft_message") ?: JSONObject.NULL)
                update.optJSONArray("positions")?.let { applyPositions(chatId, it) }
                publishChats()
            }
            "updateNewChat" -> {
                val chat = update.optJSONObject("chat") ?: return
                val chatId = chat.optLong("id")
                chatsById[chatId] = chat
                // A new chat arrives with its positions inside it; nothing
                // else will announce them.
                chat.optJSONArray("positions")?.let { applyPositions(chatId, it) }
                requestPublishChats()
            }
            "updateChatTitle", "updateChatPhoto", "updateChatLastMessage",
            "updateChatReadInbox", "updateChatReadOutbox", "updateChatNotificationSettings",
            "updateChatUnreadMentionCount", "updateChatHasScheduledMessages", "updateChatPermissions" -> {
                val chatId = update.optLong("chat_id")
                val chat = chatsById[chatId] ?: return
                when (update.optString("@type")) {
                    "updateChatTitle" -> chat.put("title", update.optString("title"))
                    // Kept for the permissions screen, which reads it from here.
                    "updateChatPermissions" -> chat.put("permissions", update.optJSONObject("permissions"))
                    "updateChatPhoto" -> chat.put("photo", update.optJSONObject("photo"))
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
                    "updateChatHasScheduledMessages" -> {
                        chat.put("has_scheduled_messages", update.optBoolean("has_scheduled_messages"))
                    }
                }
                publishChats()
            }
            "updateScopeNotificationSettings" -> {
                val scope = update.optJSONObject("scope")?.optString("@type") ?: return
                scopeNotifications[scope] = update.optJSONObject("notification_settings") ?: return
                publishChats()
            }
            "updateChatFolders" -> {
                // The account's folders, whole, on every change: TDLib sends
                // the list rather than a diff, so this replaces rather than
                // merges. Their chats arrive separately, as positions, and
                // only for the lists that have been loaded — which is why
                // refreshChats loads each folder as well as the main list.
                _folders.value = parseFolders(update.optJSONArray("chat_folders"))
                mainListPosition = update.optInt("main_chat_list_position")
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
                // A scheduled message is not in the conversation until it
                // goes; it lives in the chat's scheduled list instead.
                if (message.optJSONObject("scheduling_state") != null) return
                val chatId = message.optLong("chat_id")
                val mapped = mapMessage(chatId, message)
                chatsById[chatId]?.put("last_message", message)
                // Announced before publishChats, because a subscriber that
                // reacts to the message should not have to race the chat list
                // rebuild to see it — and only from a chat the account is
                // in: a discussion group whose comments were opened once
                // keeps sending, and the shade filled with it (2.1.3).
                if (isAccountsChat(chatId)) _incomingMessages.tryEmit(mapped)
                emitUpdate(MessageUpdate.Added(mapped))
                publishChats()
            }
            "updateMessageSendSucceeded" -> {
                // Our message, now under the id the server gave it. Until
                // this lands it has a temporary one, and anything aimed at
                // that — an edit, a reply, a delete — would be refused.
                val message = update.optJSONObject("message") ?: return
                if (message.optJSONObject("scheduling_state") != null) return
                val chatId = message.optLong("chat_id")
                emitUpdate(
                    MessageUpdate.Replaced(
                        oldId = update.optLong("old_message_id"),
                        message = mapMessage(chatId, message)
                    )
                )
            }
            "updateMessageSendFailed" -> {
                // Refused by the server, or failed on the way: an upload that
                // broke, a file too big, a chat that no longer takes posts.
                // Unhandled, a photo that failed looked exactly like one
                // still sending, for ever.
                val message = update.optJSONObject("message") ?: return
                val chatId = message.optLong("chat_id")
                val error = update.optJSONObject("error")
                Log.w(TAG, "send failed in $chatId: ${error?.optInt("code")} ${error?.optString("message")}")
                emitUpdate(
                    MessageUpdate.SendFailed(
                        oldId = update.optLong("old_message_id"),
                        message = mapMessage(chatId, message),
                        error = error?.optString("message").orEmpty()
                            .ifBlank { "the server refused it" }
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
                // Only what changes the words is passed on as an edit. The
                // same update fires for a poll's votes or a location moving,
                // and marking those "edited" would be wrong — a poll gets a
                // case of its own instead.
                val chatId = update.optLong("chat_id")
                val messageId = update.optLong("message_id")
                val newContent = update.optJSONObject("new_content")
                if (newContent?.optString("@type") == "messagePoll") {
                    val poll = pollOf(newContent.optJSONObject("poll")) ?: return
                    emitUpdate(MessageUpdate.PollChanged(chatId, messageId, poll))
                    return
                }
                val text = contentText(update.optJSONObject("new_content"))
                    ?.takeIf { it.isNotBlank() }
                    ?: return
                val entities = entitiesOf(formattedOf(newContent), text)
                val known = messagesByChat[chatId]?.firstOrNull { it.id == messageId }
                if (known != null && known.text == text && known.entities == entities) return
                emitUpdate(MessageUpdate.Edited(chatId, messageId, text, entities))
            }
            "updateMessageEdited" -> {
                // Carries the buttons, not the text: a bot paging through a
                // list rewrites its keyboard in place. The words, if they
                // changed too, come separately as updateMessageContent.
                emitUpdate(
                    MessageUpdate.ButtonsChanged(
                        chatId = update.optLong("chat_id"),
                        messageId = update.optLong("message_id"),
                        buttons = inlineKeyboardOf(update.optJSONObject("reply_markup"))
                    )
                )
            }
            "updateChatReplyMarkup" -> {
                val chatId = update.optLong("chat_id")
                val message = update.optJSONObject("reply_markup_message")
                // An older TDLib names the message by id only.
                val messageId = update.optLong("reply_markup_message_id")
                when {
                    message != null ->
                        setReplyKeyboard(chatId, replyKeyboardOf(message.optJSONObject("reply_markup")))
                    messageId != 0L -> scope.launch { loadReplyKeyboard(chatId, messageId) }
                    else -> setReplyKeyboard(chatId, null)
                }
            }
            "updateMessageIsPinned" -> {
                emitUpdate(
                    MessageUpdate.PinChanged(
                        chatId = update.optLong("chat_id"),
                        messageId = update.optLong("message_id"),
                        isPinned = update.optBoolean("is_pinned")
                    )
                )
            }
            "updateMessageInteractionInfo" -> {
                emitUpdate(
                    MessageUpdate.ReactionsChanged(
                        chatId = update.optLong("chat_id"),
                        messageId = update.optLong("message_id"),
                        reactions = parseReactions(update.optJSONObject("interaction_info")),
                        commentCount = update.optJSONObject("interaction_info")
                            ?.optJSONObject("reply_info")?.optInt("reply_count")
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
                // Where the code went, how long it is — so the screen can
                // send it on the last digit — and when another may be asked
                // for. next_type is the other route the server offers; with
                // none, there is nothing to resend.
                val codeInfo = state.optJSONObject("code_info")
                val type = codeInfo?.optJSONObject("type")
                val phone = codeInfo?.optString("phone_number").orEmpty()
                _authState.update {
                    it.copy(
                        state = AuthState.WaitCode,
                        isLoading = false,
                        errorMessage = null,
                        codeHint = codeDeliveryText(type?.optString("@type").orEmpty(), phone),
                        codeLength = type?.optInt("length") ?: 0,
                        canResend = codeInfo?.optJSONObject("next_type") != null,
                        resendAfterSeconds = codeInfo?.optInt("timeout") ?: 0,
                        codeSentAtMillis = System.currentTimeMillis()
                    )
                }
            }
            "authorizationStateWaitPassword" -> {
                val hint = state.optString("password_hint")
                _authState.update {
                    it.copy(
                        state = AuthState.WaitPassword,
                        isLoading = false,
                        errorMessage = null,
                        codeHint = if (hint.isBlank()) "" else "Hint: $hint"
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
            "authorizationStateWaitOtherDeviceConfirmation" -> {
                _authState.update {
                    it.copy(
                        state = AuthState.WaitQrScan,
                        isLoading = false,
                        errorMessage = null,
                        qrLink = state.optString("link").takeIf { link -> link.isNotBlank() }
                    )
                }
            }
            // Telegram's login email: asked for on some sign-ins before a
            // code is sent at all, and where an account has one, the place
            // the code goes. Apple and Google sign-in are offered here too
            // (allow_apple_id, allow_google_id) and not taken: both need the
            // vendor's SDK, and the code by email reaches the same place.
            "authorizationStateWaitEmailAddress" -> {
                _authState.update {
                    it.copy(
                        state = AuthState.WaitEmailAddress,
                        isLoading = false,
                        errorMessage = null,
                        emailReset = null
                    )
                }
            }
            "authorizationStateWaitEmailCode" -> {
                val codeInfo = state.optJSONObject("code_info")
                val pattern = codeInfo?.optString("email_address_pattern").orEmpty()
                _authState.update {
                    it.copy(
                        state = AuthState.WaitEmailCode,
                        isLoading = false,
                        errorMessage = null,
                        codeHint = if (pattern.isBlank()) "We emailed you a code" else "We sent a code to $pattern",
                        codeLength = codeInfo?.optInt("length") ?: 0,
                        emailReset = emailResetOf(state.optJSONObject("email_address_reset_state")),
                        // An email code can always be asked for again; there
                        // is no timeout on it the way there is on an SMS.
                        canResend = true,
                        resendAfterSeconds = 0,
                        codeSentAtMillis = System.currentTimeMillis()
                    )
                }
            }
            "authorizationStateReady" -> {
                val me = fetchMe()
                _authState.update {
                    it.copy(state = AuthState.Ready, isLoading = false, me = me, errorMessage = null)
                }
                if (!readySignal.isCompleted) readySignal.complete(Unit)
                // The activity said it was in front before there was an
                // account to be online as.
                sendOnline()
                // And set again here, where the engine is certainly running:
                // the setting may have been read before it was.
                sendPreferIpv6()
                refreshChats()
            }
            "authorizationStateLoggingOut",
            "authorizationStateClosing" -> {
                _authState.update { it.copy(state = AuthState.Bootstrapping, isLoading = true) }
            }
            // A closed TDLib instance is finished for good: every request
            // sent to it after this answers "Request aborted", which is
            // what signing in again after a log-out used to show. TDLib's
            // own rule is to make a new instance, and it reopens the same
            // database, so the next account starts at the phone number.
            // Only a shutdown this client asked for stays closed.
            "authorizationStateClosed" -> {
                if (shuttingDown) {
                    _authState.update { it.copy(state = AuthState.Closed, isLoading = false) }
                } else {
                    engine?.let { eng ->
                        eng.reopen()
                        // Kicked as at start: the first request is what
                        // makes the new instance ask for its parameters.
                        eng.sendFireAndForget(JSONObject().put("@type", "getOption").put("name", "version"))
                    }
                }
            }
        }
    }

    private fun buildTdlibParameters(): JSONObject =
        JSONObject()
            .put("@type", "setTdlibParameters")
            .put("use_test_dc", BuildConfig.USE_TEST_DC)
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
        // This account's own, whatever list TDLib files them in: its private
        // chat with itself carries the account's id.
        val myId = _authState.value.me?.id
        val mine = activeStories.values
            .firstOrNull { it.optLong("chat_id") == myId && activeStoryIds(it).isNotEmpty() }
            ?.let { active ->
                val chatId = active.optLong("chat_id")
                StoryItem(
                    id = storyKey(chatId),
                    authorName = "My story",
                    hasUnseen = false,
                    avatarColor = chatId,
                    previewEmoji = "✨",
                    caption = "My story",
                    photoPath = photoPath(chatsById[chatId]?.optJSONObject("photo")?.optJSONObject("small")),
                    isMine = true
                )
            }
        val others = activeStories.values
            .filter { it.optLong("chat_id") != myId }
            .filter { it.optJSONObject("list")?.optString("@type") == "storyListMain" }
            .sortedByDescending { it.optLong("order") }
            .mapNotNull { active ->
                val chatId = active.optLong("chat_id")
                val ids = activeStoryIds(active)
                if (ids.isEmpty()) return@mapNotNull null
                val maxRead = active.optInt("max_read_story_id")
                val chat = chatsById[chatId]
                val title = chat?.optString("title").orEmpty()
                StoryItem(
                    id = storyKey(chatId),
                    authorName = title.ifBlank { "Story" },
                    hasUnseen = ids.any { it > maxRead },
                    avatarColor = chatId,
                    previewEmoji = "✨",
                    caption = title,
                    photoPath = photoPath(chat?.optJSONObject("photo")?.optJSONObject("small"))
                )
            }
            .take(STORY_RAIL_LIMIT)
        _stories.value = listOfNotNull(own, mine) + others
    }

    /** The ids of a `chatActiveStories`, in the order TDLib lists them. */
    private fun activeStoryIds(active: JSONObject): List<Int> {
        val stories = active.optJSONArray("stories") ?: return emptyList()
        return (0 until stories.length()).mapNotNull { index ->
            stories.optJSONObject(index)
                ?.let { it.optInt("story_id", it.optInt("id")) }
                ?.takeIf { it != 0 }
        }
    }

    /** The same key for the same chat every time — see [storyKeys]. */
    private fun storyKey(chatId: Long): Long {
        storyKeys.entries.firstOrNull { it.value == chatId }?.let { return it.key }
        val key = storyKeySeq.incrementAndGet()
        storyKeys[key] = chatId
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
    /**
     * [publishChats], soon, once for however many asked in the meantime.
     *
     * For the updates that come in floods and change little each: a contact
     * going online or away, someone typing, and the hundreds of
     * `updateNewChat` a sign-in starts with. Each used to rebuild every row
     * of the list — every chat's preview, time and notification state read
     * back out of its JSON, then a sort — so an account with a few hundred
     * chats did that a few hundred times over while it loaded, and once
     * more for every status change after. The window is shorter than a
     * frame is noticeable; a message still publishes at once.
     */
    private fun requestPublishChats() {
        if (!chatsRepublishPending.compareAndSet(false, true)) return
        scope.launch {
            delay(CHATS_REPUBLISH_MILLIS)
            chatsRepublishPending.set(false)
            withContext(updateDispatcher) { publishChats() }
        }
    }

    /** See com.telegramyou.app.notifications.isAccountsChat. */
    private fun isAccountsChat(chatId: Long): Boolean {
        val type = chatsById[chatId]?.optJSONObject("type")
        val status = type?.takeIf { it.optString("@type") == "chatTypeSupergroup" }
            ?.let { supergroups[it.optLong("supergroup_id")] }
            ?.optJSONObject("status")
        return com.telegramyou.app.notifications.isAccountsChat(
            chatType = type?.optString("@type"),
            memberStatus = status?.optString("@type"),
            isMember = status?.optBoolean("is_member") == true,
            listed = positions.isListed(chatId)
        )
    }

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
    /**
     * Where the main list sits among the folder tabs, as updateChatFolders
     * last said. reorderChatFolders asks for it back, and sending 0 would
     * move somebody's "All chats" tab to the front behind their back.
     */
    @Volatile
    private var mainListPosition = 0

    override suspend fun folderRules(folderId: Int): FolderRules {
        awaitReady()
        return parseFolderRules(
            requireEngine().send(JSONObject().put("@type", "getChatFolder").put("chat_folder_id", folderId))
        )
    }

    override suspend fun saveFolder(folderId: Int?, rules: FolderRules): Int {
        awaitReady()
        // Editing starts from the folder as it is, so what this client does
        // not show — its colour tag, whether it is shared — stays as set.
        val base = if (folderId != null) {
            requireEngine().send(JSONObject().put("@type", "getChatFolder").put("chat_folder_id", folderId))
        } else {
            JSONObject().put("@type", "chatFolder").put("color_id", -1).put("is_shareable", false)
        }
        val folder = folderJson(base, rules)
        val info = if (folderId == null) {
            requireEngine().send(JSONObject().put("@type", "createChatFolder").put("folder", folder))
        } else {
            requireEngine().send(
                JSONObject().put("@type", "editChatFolder").put("chat_folder_id", folderId).put("folder", folder)
            )
        }
        return info.optInt("id")
    }

    override suspend fun deleteFolder(folderId: Int) {
        awaitReady()
        requireEngine().send(
            JSONObject()
                .put("@type", "deleteChatFolder")
                .put("chat_folder_id", folderId)
                // Leaving chats along with a shared folder is a question for
                // shared folders, which this client does not make.
                .put("leave_chat_ids", JSONArray())
        )
    }

    override suspend fun reorderFolders(folderIds: List<Int>) {
        awaitReady()
        requireEngine().send(
            JSONObject()
                .put("@type", "reorderChatFolders")
                .put("chat_folder_ids", JSONArray(folderIds))
                .put("main_chat_list_position", mainListPosition)
        )
    }

    private fun parseFolderRules(folder: JSONObject): FolderRules {
        fun ids(key: String): List<Long> {
            val array = folder.optJSONArray(key) ?: return emptyList()
            return List(array.length()) { array.optLong(it) }
        }
        return FolderRules(
            name = folder.optJSONObject("name")?.optJSONObject("text")?.optString("text").orEmpty(),
            iconName = folder.optJSONObject("icon")?.optString("name").orEmpty(),
            includedChatIds = ids("included_chat_ids"),
            excludedChatIds = ids("excluded_chat_ids"),
            pinnedChatIds = ids("pinned_chat_ids"),
            includeContacts = folder.optBoolean("include_contacts"),
            includeNonContacts = folder.optBoolean("include_non_contacts"),
            includeGroups = folder.optBoolean("include_groups"),
            includeChannels = folder.optBoolean("include_channels"),
            includeBots = folder.optBoolean("include_bots"),
            excludeMuted = folder.optBoolean("exclude_muted"),
            excludeRead = folder.optBoolean("exclude_read"),
            excludeArchived = folder.optBoolean("exclude_archived")
        )
    }

    /** [rules] written over [base], a chatFolder, keeping the fields they do not cover. */
    private fun folderJson(base: JSONObject, rules: FolderRules): JSONObject {
        val folder = JSONObject(base.toString())
        folder.remove("@extra")
        folder.put("@type", "chatFolder")
        folder.put(
            "name",
            JSONObject()
                .put("@type", "chatFolderName")
                .put("text", JSONObject().put("@type", "formattedText").put("text", rules.name.trim()).put("entities", JSONArray()))
                .put("animate_custom_emoji", false)
        )
        // A null icon asks TDLib for the default one for the folder's rules.
        if (rules.iconName.isBlank()) {
            folder.remove("icon")
        } else {
            folder.put("icon", JSONObject().put("@type", "chatFolderIcon").put("name", rules.iconName))
        }
        // A pinned chat that is no longer included would be refused.
        val pinned = rules.pinnedChatIds.filter { it in rules.includedChatIds }
        folder.put("pinned_chat_ids", JSONArray(pinned))
        folder.put("included_chat_ids", JSONArray(rules.includedChatIds.filterNot { it in pinned }))
        folder.put("excluded_chat_ids", JSONArray(rules.excludedChatIds))
        folder.put("include_contacts", rules.includeContacts)
        folder.put("include_non_contacts", rules.includeNonContacts)
        folder.put("include_groups", rules.includeGroups)
        folder.put("include_channels", rules.includeChannels)
        folder.put("include_bots", rules.includeBots)
        folder.put("exclude_muted", rules.excludeMuted)
        folder.put("exclude_read", rules.excludeRead)
        folder.put("exclude_archived", rules.excludeArchived)
        return folder
    }

    private fun parseFolders(array: JSONArray?): List<ChatFolder> {
        if (array == null) return emptyList()
        val folders = ArrayList<ChatFolder>(array.length())
        for (i in 0 until array.length()) {
            val info = array.optJSONObject(i) ?: continue
            // `name` is a chatFolderName, whose text is a formattedText —
            // two objects down. This read a `title` that TDLib no longer
            // sends, and every folder came out called "Folder".
            val title = info.optJSONObject("name")
                ?.optJSONObject("text")
                ?.optString("text")
                .orEmpty()
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
            order = position.optInt64("order"),
            isPinned = position.optBoolean("is_pinned")
        )
    }

    private fun toPreview(chat: JSONObject): ChatPreview {
        val id = chat.optLong("id")
        val last = chat.optJSONObject("last_message")
        val type = chat.optJSONObject("type")?.optString("@type").orEmpty()
        val notif = chat.optJSONObject("notification_settings")
        val saved = isSavedMessages(chat)
        return ChatPreview(
            id = id,
            title = if (saved) "Saved Messages" else chat.optString("title").ifBlank { "Chat" },
            lastMessage = previewText(last),
            timestampLabel = chatListTimeLabel(
                epochSeconds = last?.optLong("date") ?: 0L,
                nowSeconds = nowSeconds(),
                zone = ZoneId.systemDefault()
            ),
            unreadCount = chat.optInt("unread_count"),
            folderIds = positions.folderIds(id),
            isPinned = positions.isPinned(id),
            isMuted = notificationsOf(chat).isMuted(nowSeconds()),
            notifications = notificationsOf(chat),
            // Nobody there to be online: Saved Messages is the account
            // itself, which is always online while it is using the app, and
            // a bot is a program. The official client shows neither a dot.
            isOnline = !saved && !isBotChat(chat) &&
                privateChatUser(chat)?.let { presenceOf(it).isOnline(nowSeconds()) } == true,
            isTyping = typingNow(id).isNotEmpty(),
            // Who, in a group (2.1); a private chat's one person is the chat.
            typingNames = if (privateChatUser(chat) != null) emptyList() else typingNow(id).mapNotNull(::typingName),
            photoPath = photoPath(chat.optJSONObject("photo")?.optJSONObject("small")),
            // A channel is a supergroup with is_channel set inside its type.
            // This read the flag off the chat itself, where it never is, so
            // every channel was drawn and filtered as a group.
            isChannel = chat.optJSONObject("type")?.optBoolean("is_channel") == true,
            isGroup = type == "chatTypeBasicGroup" ||
                (type == "chatTypeSupergroup" && chat.optJSONObject("type")?.optBoolean("is_channel") != true),
            isBot = isBotChat(chat),
            isSavedMessages = saved,
            draft = chat.optJSONObject("draft_message")
                ?.optJSONObject("input_message_text")
                ?.optJSONObject("text")
                ?.optString("text")
                .orEmpty(),
            hasScheduledMessages = chat.optBoolean("has_scheduled_messages"),
            avatarColor = id,
            hasUnreadMention = chat.optInt("unread_mention_count") > 0,
            isArchived = positions.isArchived(id),
            canDeleteForEveryone = chat.optBoolean("can_be_deleted_for_all_users"),
            isForum = type == "chatTypeSupergroup" &&
                supergroups[chat.optJSONObject("type")?.optLong("supergroup_id")]?.optBoolean("is_forum") == true,
            // Our own last message: on its way, refused, or read once the
            // chat's last_read_outbox_message_id has reached it.
            lastMessageStatus = last?.takeIf { it.optBoolean("is_outgoing") && !saved }?.let { message ->
                when (message.optJSONObject("sending_state")?.optString("@type")) {
                    "messageSendingStatePending" -> LastMessageStatus.Sending
                    "messageSendingStateFailed" -> LastMessageStatus.Failed
                    else -> if (message.optLong("id") <= chat.optLong("last_read_outbox_message_id")) {
                        LastMessageStatus.Read
                    } else {
                        LastMessageStatus.Sent
                    }
                }
            }
        )
    }

    /** A private chat whose other person is the account itself. */
    private fun isSavedMessages(chat: JSONObject): Boolean {
        val type = chat.optJSONObject("type") ?: return false
        val me = _authState.value.me?.id ?: return false
        return type.optString("@type") == "chatTypePrivate" && type.optLong("user_id") == me
    }

    /** The other person in a private chat, if this client has heard of them. */
    private fun isBotChat(chat: JSONObject): Boolean =
        privateChatUser(chat)?.optJSONObject("type")?.optString("@type") == "userTypeBot"

    private fun privateChatUser(chat: JSONObject): JSONObject? {
        val type = chat.optJSONObject("type") ?: return null
        if (type.optString("@type") != "chatTypePrivate") return null
        return usersById[type.optLong("user_id")]
    }

    private fun statusLabel(chat: JSONObject?): String? {
        chat ?: return null
        return when (chat.optJSONObject("type")?.optString("@type")) {
            // Where the person is, the way the header of every messenger
            // says it — this used to be the words "private chat".
            // Nobody to be online: it is the account talking to itself.
            // A bot is a bot, as the official client says, not "last seen".
            "chatTypePrivate" -> when {
                isSavedMessages(chat) -> null
                isBotChat(chat) -> "bot"
                else -> privateChatUser(chat)
                    ?.let { presenceLabel(presenceOf(it), nowSeconds(), ZoneId.systemDefault()) }
            }
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

    private fun mapMessage(chatId: Long, message: JSONObject): ChatMessage {
        val content = message.optJSONObject("content")
        val type = content?.optString("@type").orEmpty()
        // A caption where there is one — a video's used to be dropped for
        // "🎬 Video" — and the list's short description where there is not.
        val text = contentText(content)?.takeIf { it.isNotBlank() } ?: previewText(message)
        val contentType = when (type) {
            "messagePhoto" -> MessageContentType.Photo
            "messageVideo" -> MessageContentType.Video
            "messageAnimation" -> MessageContentType.Animation
            "messageVideoNote" -> MessageContentType.VideoNote
            "messageDocument" -> MessageContentType.Document
            "messageVoiceNote" -> MessageContentType.Voice
            "messageSticker" -> MessageContentType.Sticker
            // A lone emoji, which Telegram plays as an animation: a sticker
            // here when the animation came with it, the emoji drawn large
            // (jumboEmojiCount) when it did not.
            "messageAnimatedEmoji" -> if (animatedEmojiSticker(content) != null) {
                MessageContentType.Sticker
            } else {
                MessageContentType.Text
            }
            "messagePoll" -> MessageContentType.Poll
            "messageContact" -> MessageContentType.Contact
            "messageLocation", "messageVenue" -> MessageContentType.Location
            "messageAudio" -> MessageContentType.Audio
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
            senderPhotoPath = sender?.let {
                photoPath(it.optJSONObject("profile_photo")?.optJSONObject("small"))
            },
            // Not on the message in this TDLib — messageProperties holds
            // them, asked for when a menu opens (messagePermissions). Read
            // here still for a TDLib that sends them; otherwise false.
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
                // A textQuote's text is a formattedText, not a string.
                ?.optJSONObject("text")
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
            sendState = when (message.optJSONObject("sending_state")?.optString("@type")) {
                "messageSendingStatePending" -> SendState.Pending
                "messageSendingStateFailed" -> SendState.Failed
                else -> SendState.Sent
            },
            contentType = contentType,
            sticker = (
                content?.takeIf { type == "messageSticker" }?.optJSONObject("sticker")
                    ?: animatedEmojiSticker(content)
                )?.let(::stickerOf),
            fileName = content?.optJSONObject("document")?.optString("file_name"),
            fileSizeLabel = content?.optJSONObject("document")?.optJSONObject("document")
                ?.let { file -> file.optLong("size").takeIf { it > 0 } ?: file.optLong("expected_size") }
                ?.takeIf { it > 0 }
                ?.let(::formatBytes),
            documentFileId = content?.optJSONObject("document")?.optJSONObject("document")
                ?.optInt("id")?.takeIf { it != 0 },
            documentPath = content?.optJSONObject("document")?.optJSONObject("document")
                ?.optJSONObject("local")
                ?.takeIf { it.optBoolean("is_downloading_completed") }
                ?.optString("path")
                ?.takeIf { it.isNotBlank() },
            mimeType = content?.optJSONObject("document")?.optString("mime_type")?.takeIf { it.isNotBlank() },
            mediaEmoji = when (contentType) {
                MessageContentType.Photo -> "🖼️"
                MessageContentType.Document -> "📎"
                else -> null
            },
            reactions = parseReactions(message.optJSONObject("interaction_info")),
            forwardCount = message.optJSONObject("interaction_info")?.optInt("forward_count") ?: 0,
            linkPreview = linkPreview(content),
            // Only a voice note carries these, and only once TDLib has the
            // bytes: the id arrives with the message, the path with the file.
            // A music file's bytes travel in the same two fields: the one
            // player plays both, and it asks for them by these names.
            voiceFileId = (content?.optJSONObject("voice_note")?.optJSONObject("voice")
                ?: content?.optJSONObject("audio")?.optJSONObject("audio"))
                ?.optInt("id")
                ?.takeIf { it != 0 },
            voicePath = (content?.optJSONObject("voice_note")?.optJSONObject("voice")
                ?: content?.optJSONObject("audio")?.optJSONObject("audio"))
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
            photoMini = content?.takeIf { type == "messagePhoto" }?.optJSONObject("photo")?.miniThumbnail(),
            video = videoContent(content),
            poll = content?.takeIf { type == "messagePoll" }?.optJSONObject("poll")?.let(::pollOf),
            contact = content?.takeIf { type == "messageContact" }?.optJSONObject("contact")?.let(::contactOf),
            location = content?.let(::locationOf),
            inlineKeyboard = inlineKeyboardOf(message.optJSONObject("reply_markup")),
            audio = content?.takeIf { type == "messageAudio" }?.optJSONObject("audio")?.let { audio ->
                val file = audio.optJSONObject("audio")
                AudioContent(
                    title = audio.optString("title"),
                    performer = audio.optString("performer"),
                    durationSeconds = audio.optInt("duration"),
                    fileName = audio.optString("file_name"),
                    fileId = file?.optInt("id")?.takeIf { it != 0 },
                    path = file?.localPathIfDownloaded(),
                    // Telegram's thumbnail of the album cover, where the file has one.
                    coverFileId = audio.optJSONObject("album_cover_thumbnail")?.optJSONObject("file")
                        ?.optInt("id")?.takeIf { it != 0 },
                    coverPath = audio.optJSONObject("album_cover_thumbnail")?.optJSONObject("file")
                        ?.localPathIfDownloaded()
                )
            },
            scheduledAt = message.optJSONObject("scheduling_state")
                ?.optLong("send_date")
                ?.takeIf { it > 0 },
            entities = entitiesOf(formattedOf(content), text),
            forwardedFrom = forwardOrigin(message.optJSONObject("forward_info")),
            albumId = message.optInt64("media_album_id").takeIf { it != 0L },
            isPinned = message.optBoolean("is_pinned"),
            topicId = topicIdOf(message),
            commentCount = commentCountOf(message),
            threadId = threadIdOf(message)
        )
    }

    /** "Forwarded from" whom, by the name the origin carries or points at. */
    private fun forwardOrigin(info: JSONObject?): String? {
        val origin = info?.optJSONObject("origin") ?: return null
        return when (origin.optString("@type")) {
            "messageOriginUser" -> usersById[origin.optLong("sender_user_id")]?.let { mapUser(it).displayName }
            "messageOriginHiddenUser" -> origin.optString("sender_name")
            "messageOriginChat" -> chatsById[origin.optLong("sender_chat_id")]?.optString("title")
            "messageOriginChannel" -> chatsById[origin.optLong("chat_id")]?.optString("title")
            else -> null
        }?.takeIf { it.isNotBlank() } ?: "someone"
    }

    override suspend fun setMessagePinned(chatId: Long, messageId: Long, pinned: Boolean) {
        awaitReady()
        requireEngine().send(
            if (pinned) {
                JSONObject()
                    .put("@type", "pinChatMessage")
                    .put("chat_id", chatId)
                    .put("message_id", messageId)
                    .put("disable_notification", false)
                    .put("only_for_self", false)
            } else {
                JSONObject().put("@type", "unpinChatMessage").put("chat_id", chatId).put("message_id", messageId)
            }
        )
    }

    /**
     * What was typed, with Telegram's own markdown read into formatting:
     * **bold**, __italic__, ~~strikethrough~~, ||spoiler||, `code` and
     * [links](url). TDLib does the reading; a refusal sends the text as
     * typed rather than not at all.
     */
    private suspend fun formatted(text: String): JSONObject {
        val plain = JSONObject().put("@type", "formattedText").put("text", text)
        return try {
            requireEngine().send(JSONObject().put("@type", "parseMarkdown").put("text", plain))
                .also { it.put("@type", "formattedText") }
        } catch (e: TdLibException) {
            plain
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

    private fun mapUser(user: JSONObject): TelegramUser =
        TelegramUser(
            id = user.optLong("id"),
            firstName = user.optString("first_name"),
            lastName = user.optString("last_name"),
            // `usernames` is an object holding a list of plain strings, the
            // first being the one to show.
            username = user.optJSONObject("usernames")
                ?.optJSONArray("active_usernames")
                ?.optString(0)
                ?.ifBlank { null },
            phoneNumber = user.optString("phone_number").ifBlank { null },
            avatarColor = user.optLong("id"),
            isPremium = user.optBoolean("is_premium"),
            photoPath = photoPath(user.optJSONObject("profile_photo")?.optJSONObject("small"))
        )

    /**
     * Where a picture is on this device, or null while it is not — in which
     * case it is asked for, once, and [republishPhotos] redraws whatever
     * showed initials when it arrives.
     *
     * Priority 1, the lowest: an avatar is worth having but never worth
     * delaying a photo someone tapped.
     */
    private fun photoPath(file: JSONObject?): String? {
        file ?: return null
        file.localPathIfDownloaded()?.let { return it }
        val id = file.optInt("id").takeIf { it != 0 } ?: return null
        downloadedPhotos[id]?.let { path ->
            if (File(path).exists()) return path
            // Deleted since it arrived (Settings → Storage): ask again.
            downloadedPhotos.remove(id)
            requestedPhotos.remove(id)
        }
        if ((photoAttempts[id] ?: 0) >= PHOTO_ATTEMPTS) return null
        if (requestedPhotos.add(id)) {
            photoAttempts.merge(id, 1, Int::plus)
            engine?.sendFireAndForget(
                JSONObject()
                    .put("@type", "downloadFile")
                    .put("file_id", id)
                    .put("priority", 1)
                    .put("offset", 0)
                    .put("limit", 0)
                    .put("synchronous", false)
            )
        }
        return null
    }

    /**
     * Redraws the chat list, the stories rail and the account once pictures
     * have arrived — once for a burst of them, which is how they arrive when
     * the list first loads.
     */
    private fun republishPhotos() {
        if (!photoRepublishPending.compareAndSet(false, true)) return
        scope.launch {
            delay(PHOTO_REPUBLISH_MILLIS)
            photoRepublishPending.set(false)
            withContext(updateDispatcher) {
                publishChats()
                publishStories()
                val me = _authState.value.me ?: return@withContext
                val user = usersById[me.id] ?: return@withContext
                val path = photoPath(user.optJSONObject("profile_photo")?.optJSONObject("small"))
                if (path != me.photoPath) _authState.update { it.copy(me = it.me?.copy(photoPath = path)) }
            }
        }
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
        /** Telegram's limit on a message's text, until the server says otherwise. */
        private const val DEFAULT_TEXT_LIMIT = 4096
        /** Above the app's own fetches, below nothing: somebody asked for this one. */
        private const val DOWNLOAD_PRIORITY = 30
        /** One page is the whole list for anyone but a hoarder; see fileDownloads. */
        private const val DOWNLOADS_LIMIT = 200
        /** How long a chat action counts without being repeated. */
        private const val TYPING_MILLIS = 6_000L

        /** How long arriving avatars are gathered before one redraw. */
        private const val PHOTO_REPUBLISH_MILLIS = 300L
        /** Tries at a picture before it is left on initials for this run. */
        private const val PHOTO_ATTEMPTS = 3
        private const val CHATS_REPUBLISH_MILLIS = 50L

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

        /** Members listed for running a group — Telegram's page size for them. */
        private const val MANAGED_MEMBER_LIMIT = 200

        /** Members found by a search over a group's members. */
        private const val MEMBER_SEARCH_LIMIT = 50

        /** Join requests listed at once. */
        private const val JOIN_REQUEST_LIMIT = 100

        /** Links listed of each kind, working and revoked. */
        private const val INVITE_LINK_LIMIT = 50

        /** Topics listed in a forum; more than anybody scrolls. */
        private const val TOPIC_LIMIT = 100

        /**
         * How long "Remove from group" bans for: past Telegram's 30-second
         * floor, so it removes rather than bans for ever, and short enough
         * that they can be invited back the same day.
         */
        private const val REMOVE_BAN_SECONDS = 60L

        /**
         * How many contacts the picker lists.
         *
         * Each one not already cached costs a getUser, and a sheet nobody
         * can scroll to the end of is a search field's job rather than a
         * list's. Search across chats already exists for the rest.
         */
        private const val CONTACT_LIMIT = 100

        /** Blocked people fetched at once; TDLib answers at most a hundred. */
        private const val BLOCKED_LIMIT = 100

        /**
         * What a chat offers when it does not restrict reactions.
         *
         * TDLib says "all" without enumerating them, and the full set runs to
         * thousands once custom emoji are counted. These are Telegram's own
         * defaults, in its order, and they are what a picker can reasonably
         * show without a grid and a search field.
         */
        /** How many reactions the quick row over a message's menu offers. */
        private const val QUICK_REACTION_COUNT = 7

        private val DEFAULT_REACTIONS =
            listOf("👍", "👎", "❤️", "🔥", "🎉", "😁", "🤔", "😢")
    }
}

/** TDLib's `EmailAddressResetState`, or null where there is none on offer. */
private fun emailResetOf(state: JSONObject?): EmailReset? {
    if (state == null) return null
    return when (state.optString("@type")) {
        "emailAddressResetStateAvailable" -> EmailReset.Available(state.optInt("wait_period"))
        "emailAddressResetStatePending" -> EmailReset.Pending(state.optInt("reset_in"))
        else -> null
    }
}

/**
 * A TDLib `int64`, which its JSON sends as a string — "5139012345678901234".
 *
 * `optLong` looks as if it reads one, and does not: Android's org.json turns
 * a string into a number through a Double, which keeps about sixteen
 * significant digits, and an int64 id has nineteen. The last three come back
 * wrong, and the id names nothing — which is how every sticker set said "This
 * set is empty", and how ending a session would have ended none. Read as a
 * string and parsed exactly; a plain number, where TDLib sends one, still
 * reads. Sent back to TDLib, an int64 goes as a string for the same reason.
 * (int53 fields — chat, user and message ids — are numbers and exact already.)
 */
internal fun JSONObject.optInt64(key: String): Long =
    optString(key).toLongOrNull() ?: optLong(key)

/** How many messages after the day before's last are looked through for a date's first. */
private const val FIRST_FROM_PAGE = 20

/** A day, in the seconds TDLib's dates are in. */
private const val DAY_SECONDS = 24 * 60 * 60L

/** A page of this account's own messages, found and deleted together. */
private const val MY_MESSAGES_PAGE = 100

/** How many pages "delete all my messages" goes through before stopping. */
private const val MY_MESSAGES_PAGES = 50

/** How long a posted story stays up: a day, as every client posts them. */
private const val STORY_ACTIVE_SECONDS = 86_400
