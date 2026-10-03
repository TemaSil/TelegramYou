package com.telegramyou.app.ui.chat

import com.telegramyou.app.settings.MessageExtra
import com.telegramyou.app.telegram.model.downloadedFileId
import com.telegramyou.app.telegram.model.TelegramUser
import com.telegramyou.app.telegram.model.ContactContent
import com.telegramyou.app.notifications.ChatNotificationSettings
import com.telegramyou.app.telegram.model.StickerSetPreview
import com.telegramyou.app.telegram.model.StickerContent
import com.telegramyou.app.telegram.model.GifItem
import com.telegramyou.app.telegram.model.ReactionOption
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.telegramyou.app.navigation.Route
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.AttachmentDraft
import com.telegramyou.app.telegram.model.PickedEmoji
import com.telegramyou.app.telegram.model.ChatDetail
import com.telegramyou.app.telegram.model.ChatMessage
import com.telegramyou.app.telegram.model.MessageContentType
import com.telegramyou.app.ui.media.FileTransfer
import com.telegramyou.app.telegram.model.ChatPreview
import com.telegramyou.app.telegram.model.PersonProfile
import com.telegramyou.app.telegram.model.withPermissions
import com.telegramyou.app.telegram.model.MessagePermissions
import com.telegramyou.app.telegram.model.MessageUpdate
import com.telegramyou.app.telegram.model.ButtonAction
import com.telegramyou.app.telegram.model.CallbackAnswer
import com.telegramyou.app.telegram.model.InlineButton
import com.telegramyou.app.telegram.model.ReplyKeyboard
import com.telegramyou.app.telegram.model.PollDraft
import com.telegramyou.app.telegram.model.isValidSchedule
import com.telegramyou.app.telegram.model.scheduleLabel
import java.time.ZoneId
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.telegramyou.app.telegram.model.toggleReaction
import com.telegramyou.app.ui.failureText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.flow.collectLatest

/**
 * Searching inside one conversation.
 *
 * [results] are whole messages rather than ids: a hit may be older than
 * anything loaded, and the result row has to render without the conversation
 * having reached it.
 */
data class ChatSearchState(
    val isOpen: Boolean = false,
    val query: String = "",
    val results: List<ChatMessage> = emptyList(),
    val isSearching: Boolean = false
) {
    /** A query that found nothing, as opposed to one not yet typed. */
    val isEmpty: Boolean get() = query.isNotBlank() && !isSearching && results.isEmpty()
}

/**
 * State for one conversation.
 *
 * [replyTo] and [editing] are alternatives, never both at once — the composer
 * shows one banner, and choosing either cancels the other. [pendingDelete]
 * being non-null is what puts the confirmation dialog on screen.
 */
/**
 * One message's text in another language, as the translation dialog shows
 * it: [text] is null while Telegram is still working on it.
 */
data class Translation(
    val messageId: Long,
    /** The language it is put into, as an ISO 639 code. */
    val language: String,
    val text: String? = null
)

data class ChatUiState(
    val detail: ChatDetail? = null,
    /** The forum topic this screen is on, by name; null in any other chat. */
    val topicName: String? = null,
    /** A file in a bubble being fetched before it opens, by message id. */
    val openingFileId: Long? = null,
    /** A file fetched and waiting for the screen to open it. */
    val fileToOpen: FileToOpen? = null,
    val draft: String = "",
    val pendingAttachment: AttachmentDraft? = null,
    val replyTo: ChatMessage? = null,
    val editing: ChatMessage? = null,
    val pendingDelete: ChatMessage? = null,
    /** Older messages fetched by scrolling, oldest first, ahead of [detail]. */
    val olderMessages: List<ChatMessage> = emptyList(),
    val isLoadingOlder: Boolean = false,
    /**
     * False once the client has answered a request with nothing, which is the
     * only way it says a conversation has no more history.
     */
    val hasMoreOlder: Boolean = true,
    /**
     * A stretch of history away from the latest messages — where a jump to a
     * search hit or a pinned message older than anything loaded lands. While
     * it is set, it is what is on screen instead of [ChatDetail.messages],
     * with [olderMessages] paging above it as usual and newer pages below it
     * until it meets the latest ones again and is folded back in.
     */
    val detachedWindow: List<ChatMessage>? = null,
    /**
     * People this account has blocked whose messages a group leaves out —
     * Settings → For geeks → Hide blocked people in groups; empty otherwise.
     */
    val hiddenSenders: Set<Long> = emptySet(),
    val isLoadingNewer: Boolean = false,
    /**
     * A message the list should bring on screen, once it is there — set by a
     * jump and cleared by the screen when it has scrolled.
     */
    val scrollTarget: Long? = null,
    /** The message a jump landed on, lit for a moment so the eye finds it. */
    val highlightedId: Long? = null,
    /**
     * Files moving right now, by file id — see `TelegramMessages`.
     *
     * Handed to the bubbles whole rather than matched to messages here: a
     * bubble knows its own file id, and threading the lookup through the
     * state would mean rebuilding the message list on every tick of every
     * bar.
     */
    val transfers: Map<Int, FileTransfer> = emptyMap(),
    /** Non-null while the reaction picker is open, naming what it reacts to. */
    val reactingTo: ChatMessage? = null,
    /** What this chat permits, fetched once — see TelegramMessages. */
    val availableReactions: List<String> = emptyList(),
    /**
     * What the message whose menu is open may be reacted with, with
     * Telegram's animations — asked for as the menu opens. Empty until
     * then, and the chat's list stands in.
     */
    val reactionOptions: List<ReactionOption> = emptyList(),
    /** Empty until someone chooses Select; non-empty puts the toolbar up. */
    val selection: MessageSelection = MessageSelection(),
    /**
     * Every photo in this chat, for the media grid — newest first.
     *
     * Fetched when that screen opens rather than with the conversation: it is
     * a separate request to the server and most conversations are never
     * browsed that way.
     */
    val media: List<ChatMessage> = emptyList(),
    val isLoadingMedia: Boolean = false,
    /** True while the confirmation for deleting the selection is on screen. */
    val confirmingSelectionDelete: Boolean = false,
    /** Search inside this conversation; see ChatSearchState. */
    val search: ChatSearchState = ChatSearchState(),
    /** The voice message currently playing, if any. */
    val playingVoiceId: Long? = null,
    /** A voice message whose file is still arriving. */
    val loadingVoiceId: Long? = null,
    /** How far through the playing message is, 0..1. */
    val voiceProgress: Float = 0f,
    /** The photo open full-screen, if one is. */
    val viewingPhoto: ChatMessage? = null,
    /**
     * The video open full-screen, if one is.
     *
     * The message rather than the file, because the player wants the caption
     * and the shape as well — and because the file may still be arriving
     * while the dialog is already up.
     */
    val viewingVideo: ChatMessage? = null,
    /** True while the "what would you like to attach" sheet is up. */
    val attachmentSheetOpen: Boolean = false,
    /** True while the chat picker for forwarding a selection is up. */
    val forwardSheetOpen: Boolean = false,
    /** Somewhere to forward to; every chat but this one. */
    val forwardTargets: List<ChatPreview> = emptyList(),
    /** The account's contacts, for the forward sheet's search (1.7). */
    val forwardContacts: List<TelegramUser> = emptyList(),
    /**
     * The chat's invite link, for the info screen. Null for a private chat
     * and for a group this account may not invite to — see `chatInviteLink`.
     */
    val inviteLink: String? = null,
    /**
     * The person behind a private chat, for the info screen: their bio,
     * number and username, and whether they are blocked. Null for groups,
     * channels and Saved Messages, and until loadPerson answers.
     */
    val person: PersonProfile? = null,
    /** True while the "leave this group" confirmation is up. */
    val confirmingLeave: Boolean = false,
    /**
     * Set once leaving has gone through, so the info screen can navigate out
     * of a conversation that is no longer this account's.
     *
     * A flag the screen clears rather than a navigation from here: the view
     * model has no navigator, and a screen that left on its own would have
     * to guess when.
     */
    val hasLeft: Boolean = false,
    /**
     * What the last request that failed has to say, for a snackbar; null once
     * it has been shown.
     *
     * The server refuses things this client cannot predict — an edit past its
     * time limit, a message in a channel this account may not post to, a
     * flood wait — and every one of those used to escape the view model's
     * scope and take the app down with it.
     */
    val errorMessage: String? = null,
    /**
     * Which tab of the emoji, GIF and sticker panel is up in the keyboard's
     * place; null while it is down.
     */
    val expressions: ExpressionTab? = null,
    /** The sticker tab's sets and stickers, once it has been opened. */
    val stickerPicker: StickerPickerState? = null,
    /** The GIF tab's search and results, once it has been opened. */
    val gifPicker: GifPickerState? = null,
    /** The emoji tab's custom-emoji sets, once it has been opened; see CustomEmojiState. */
    val customEmoji: CustomEmojiState? = null,
    /** The account's contacts, while the sheet to send one is up. */
    val contactPicker: List<TelegramUser>? = null,
    /** The bot's keyboard under the composer, in a chat that has one. */
    val replyKeyboard: ReplyKeyboard? = null,
    /** What a bot said back to a pressed button, until it has been shown. */
    val botAnswer: CallbackAnswer? = null,
    /** The poll being written, while its form is open. */
    val pollDraft: PollDraft? = null,
    /**
     * This chat's scheduled messages, while their sheet is open; null when it
     * is closed. Fetched each time it opens, since they leave on their own.
     */
    val scheduled: List<ChatMessage>? = null,
    /** Something done that is worth a line in a snackbar, until shown. */
    val notice: String? = null,
    /** A message being translated, or shown translated; see onTranslate. */
    val translation: Translation? = null
) {
    /** Polls go to groups and channels, as in every Telegram client. */
    val canSendPolls: Boolean get() = detail?.chat?.let { it.isGroup || it.isChannel } == true

    val messages: List<ChatMessage>
        get() = (olderMessages + (detachedWindow ?: detail?.messages.orEmpty())).let { all ->
            if (hiddenSenders.isEmpty()) all else all.filter { it.senderId !in hiddenSenders }
        }

    /** Whether what is on screen is away from the latest messages. */
    val isDetached: Boolean get() = detachedWindow != null

    val selectedMessages: List<ChatMessage> get() = messages.filter { it.id in selection }

    /**
     * What the toolbar may offer, computed rather than stored: it is a
     * function of the selection and the messages, and storing it means two
     * things that can disagree. Named apart from the `selectionActions`
     * function it calls so neither reads as the other.
     */
    val availableActions: SelectionActions get() = selectionActions(selectedMessages)
}

class ChatViewModel(
    private val repository: TelegramRepository,
    savedStateHandle: SavedStateHandle,
    /** The app's voice messages, which outlive this screen; its own where none is given, as in tests. */
    private val voice: com.telegramyou.app.music.VoicePlayback = com.telegramyou.app.music.VoicePlayback(repository, null)
) : ViewModel() {

    /**
     * Read from the saved state rather than passed in, so the chat being
     * shown survives process death along with everything else here.
     */
    private val chatId: Long = requireNotNull(savedStateHandle[Route.Chat.ARG_CHAT_ID]) {
        "ChatViewModel needs a ${Route.Chat.ARG_CHAT_ID} argument"
    }

    /**
     * The forum topic this screen is on, or 0 for a whole chat. Said to the
     * repository before anything loads, so that the history, the sends and
     * the paging are all the topic's; see TelegramGroups.setOpenTopic.
     */
    private val topicId: Int = savedStateHandle[Route.Chat.ARG_TOPIC_ID] ?: 0

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        // Held open for as long as this state holder lives; released in
        // onCleared. See TelegramChats.retainChat for why it is counted.
        repository.retainChat(chatId)
        if (topicId != 0) {
            repository.setOpenTopic(chatId, topicId)
            viewModelScope.launch {
                val topic = runCatching { repository.forumTopics(chatId) }.getOrNull()
                    ?.firstOrNull { it.id == topicId }
                // The topic's own draft back in the field, once and only if
                // nothing has been typed yet; from then on it is kept as the
                // chat's is, but as the topic's.
                val draft = topic?.draft.orEmpty()
                keptDraft = draft
                draftRestored = true
                _uiState.update {
                    it.copy(
                        topicName = topic?.name ?: "Topic",
                        draft = if (it.draft.isEmpty()) draft else it.draft
                    )
                }
            }
        } else {
            // Messages from the first frame, where the tap fetched them ahead
            // of the screen; the reload below still replaces them with a
            // fresh window. The whole chat's, so never for a topic.
            repository.takeWarmChat(chatId)?.let { warm ->
                _uiState.update { it.copy(detail = warm) }
            }
        }
        reload()
        // The draft, kept as it is typed: a pause of a second and it is on
        // the server, where the chat list and the account's other devices
        // see it. Not while editing a message — the field holds the edit
        // then, which is not a draft of anything.
        viewModelScope.launch {
            _uiState
                .map { state -> state.draft.takeIf { state.editing == null } }
                .distinctUntilChanged()
                .collectLatest { text ->
                    if (text == null || !draftRestored) return@collectLatest
                    delay(DRAFT_SAVE_MS)
                    keepDraft(text)
                }
        }
        viewModelScope.launch {
            // Forwarding needs somewhere to forward to, and the chat list is
            // already a flow the repository keeps current — asking for it per
            // tap would put a request between the button and the sheet.
            repository.observeChats().collect { chats ->
                // The same list carries this chat's own changing state — who
                // is typing, its picture once downloaded — which the header
                // used to take once from openChat and never again. So in the
                // live client the header never said anyone was typing.
                val here = chats.firstOrNull { it.id == chatId }
                _uiState.update { state ->
                    state.copy(
                        // This chat is excluded: forwarding a message into the
                        // conversation it came from is a copy of itself.
                        forwardTargets = chats.filter { it.id != chatId },
                        detail = state.detail?.let { detail ->
                            if (here == null) detail
                            else detail.copy(chat = here, isTyping = here.isTyping)
                        }
                    )
                }
            }
        }
        viewModelScope.launch {
            // Everything that happens to this chat's messages while it is on
            // screen — arrivals, our own sends as the server confirms them,
            // edits, deletions, reactions, the other side reading. Without
            // this the conversation is whatever openChat returned, and the
            // only way to see a change was to fetch the whole window again.
            repository.messageUpdates
                .filter { it.chatId == chatId && inThisTopic(it) }
                .collect { update -> applyUpdate(update) }
        }
        viewModelScope.launch {
            // The bot keyboard is chat state and changes on the bot's say-so,
            // so it is followed rather than read once.
            repository.replyKeyboards
                .map { it[chatId] }
                .distinctUntilChanged()
                .collect { keyboard -> _uiState.update { it.copy(replyKeyboard = keyboard) } }
        }
        viewModelScope.launch {
            // Every file in flight, for every bubble that has one. The map is
            // usually empty; when it is not, it ticks several times a second
            // and each tick is a new state — which is why nothing else is
            // recomputed from it.
            repository.observeTransfers().collect { transfers ->
                _uiState.update { it.copy(transfers = transfers) }
            }
        }
    }

    /**
     * Applies one change to the window on screen, wherever the message is.
     *
     * Patched rather than reloaded, because a reload discards the history
     * paged in above and jumps the list. A new message goes on the end of the
     * newest page only. What points at a message — the reply and edit
     * banners, the selection — follows it when its id changes and lets go
     * when it is deleted, or the next action would aim at nothing.
     */
    private fun applyUpdate(update: MessageUpdate) = _uiState.update { state ->
        val detail = state.detail ?: return@update state
        val patched = state.copy(
            detail = detail.copy(messages = detail.messages.applying(update)),
            olderMessages = state.olderMessages.applying(update, appendNew = false),
            // New messages belong to the latest window, not to a stretch of
            // the past; edits and deletions reach wherever the message is.
            detachedWindow = state.detachedWindow?.applying(update, appendNew = false)
        )
        when (update) {
            is MessageUpdate.Deleted -> patched.copy(
                replyTo = patched.replyTo?.takeIf { it.id !in update.messageIds },
                editing = patched.editing?.takeIf { it.id !in update.messageIds },
                draft = if (patched.editing?.id in update.messageIds) "" else patched.draft,
                selection = patched.selection.retaining(patched.messages)
            )
            // Said as well as drawn: the bubble's mark is easy to miss, and
            // what the server said is the only clue to what went wrong.
            is MessageUpdate.SendFailed -> patched.copy(
                errorMessage = failureText("Could not send", update.error),
                selection = patched.selection.retaining(patched.messages)
            )
            // The bar follows: a message pinned now is the one it shows,
            // and the one it showed going unpinned takes the bar with it.
            is MessageUpdate.PinChanged -> patched.copy(
                detail = patched.detail?.let { chat ->
                    when {
                        update.isPinned -> patched.messages.firstOrNull { it.id == update.messageId }
                            ?.let { chat.copy(pinnedMessage = it) } ?: chat
                        chat.pinnedMessage?.id == update.messageId -> chat.copy(pinnedMessage = null)
                        else -> chat
                    }
                }
            )
            is MessageUpdate.Replaced -> patched.copy(
                replyTo = patched.replyTo?.let {
                    if (it.id == update.oldId) update.message else it
                },
                editing = patched.editing?.let {
                    if (it.id == update.oldId) update.message else it
                },
                selection = patched.selection.retaining(patched.messages)
            )
            else -> patched
        }
    }

    /**
     * Runs one request, and turns a refusal into a message on screen rather
     * than an exception out of the scope — which, uncaught, is a crash.
     *
     * Cancellation is let through: it is the screen going away, not the
     * server saying no.
     */
    private suspend fun attempt(failure: String, request: suspend () -> Unit): Boolean =
        try {
            request()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = failureText(failure, e.message)) }
            false
        }

    fun onErrorShown() = _uiState.update { it.copy(errorMessage = null) }

    /** The newest message already marked read from here; see [onSeen]. */
    private var markedUpTo = 0L

    /**
     * The conversation is on screen, showing everything up to its newest
     * message — so that much is read.
     *
     * Called by the screen while it is in front, again whenever a newer
     * message arrives, and never from the background: a message landing in
     * a chat left open on a phone in a pocket has not been read by anybody.
     * Only what came from someone else counts, and only once per message.
     * Opening a chat used to leave its badge where it was until "Mark as
     * read" was chosen from the list.
     */
    fun onSeen() {
        val newest = _uiState.value.messages.lastOrNull { !it.isOutgoing } ?: return
        if (newest.id <= markedUpTo) return
        markedUpTo = newest.id
        viewModelScope.launch {
            // Quietly: a read receipt that did not go is not worth a snackbar,
            // and the next message on screen will try again.
            try {
                repository.markChatRead(chatId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                markedUpTo = 0L
            }
        }
    }

    // ── composing ────────────────────────────────────────────────────────

    fun onDraftChange(text: String) {
        // An emptied field takes the picked custom emoji with it.
        if (text.isEmpty()) picked.clear()
        _uiState.update { it.copy(draft = text) }
    }

    /**
     * The custom emoji picked into the draft, in order; placed back onto the
     * text as it is sent (placePickedEmoji). Not in the UI state: nothing
     * draws it, and the field shows their plain emoji.
     */
    private val picked = mutableListOf<PickedEmoji>()

    /**
     * A custom emoji from the emoji tab, into the draft as its plain emoji
     * and remembered as the custom one. Sending one needs Premium; without
     * it the tab says so rather than sending a plain emoji in its place.
     */
    fun onCustomEmojiPicked(sticker: StickerContent) {
        if (sticker.customEmojiId == 0L) return
        if (repository.authState.value.me?.isPremium != true) {
            _uiState.update { it.copy(notice = "Custom emoji need Telegram Premium") }
            return
        }
        val emoji = sticker.emoji.ifBlank { "🙂" }
        picked += PickedEmoji(emoji, sticker.customEmojiId)
        _uiState.update { it.copy(draft = it.draft + emoji) }
    }

    /** A custom-emoji set chosen in the emoji tab, or null for the standard emoji. */
    fun onCustomEmojiSetSelected(setId: Long?) {
        _uiState.update { state ->
            state.copy(
                customEmoji = state.customEmoji?.copy(
                    selected = setId,
                    emoji = emptyList(),
                    isLoading = setId != null
                )
            )
        }
        if (setId == null) return
        viewModelScope.launch {
            var emoji = emptyList<StickerContent>()
            attempt("Could not load the emoji") { emoji = repository.stickerSet(setId) }
            _uiState.update { state ->
                val current = state.customEmoji
                if (current?.selected != setId) state
                else state.copy(customEmoji = current.copy(emoji = emoji, isLoading = false))
            }
        }
    }

    private fun loadCustomEmojiSets() {
        _uiState.update { it.copy(customEmoji = CustomEmojiState()) }
        viewModelScope.launch {
            var sets = emptyList<StickerSetPreview>()
            // Quietly: an account with none, or a backend without them, has
            // the standard emoji and nothing to be told about.
            try {
                sets = repository.customEmojiSets()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
            _uiState.update { state -> state.copy(customEmoji = state.customEmoji?.copy(sets = sets)) }
        }
    }

    // ── notifications ────────────────────────────────────────────────────

    /**
     * The chat's notification settings, drawn changed at once and then sent.
     * A refusal says so; the server's own copy comes back through the chat
     * list either way and settles the switches.
     */
    fun onNotificationsChange(settings: ChatNotificationSettings) {
        val now = System.currentTimeMillis() / 1000
        _uiState.update { state ->
            state.copy(
                detail = state.detail?.let { detail ->
                    detail.copy(chat = detail.chat.copy(notifications = settings, isMuted = settings.isMuted(now)))
                }
            )
        }
        viewModelScope.launch {
            attempt("Could not change the notifications") {
                repository.setChatNotifications(chatId, settings)
            }
        }
    }

    // ── emoji, GIFs and stickers ─────────────────────────────────────────

    /** The tab the panel opened on last, so it comes back where it was left. */
    private var lastExpressionTab = ExpressionTab.Emoji

    /** The panel, in the keyboard's place, on the tab it was left on. */
    fun onExpressionsOpen() = onExpressionTab(lastExpressionTab)

    fun onExpressionsClose() = _uiState.update { it.copy(expressions = null) }

    /** A tab chosen; stickers and GIFs are fetched the first time theirs opens. */
    fun onExpressionTab(tab: ExpressionTab) {
        lastExpressionTab = tab
        _uiState.update { it.copy(expressions = tab) }
        val state = _uiState.value
        when (tab) {
            ExpressionTab.Stickers -> if (state.stickerPicker == null) loadStickers()
            ExpressionTab.Gifs -> if (state.gifPicker == null) {
                _uiState.update { it.copy(gifPicker = GifPickerState()) }
                searchGifs("")
            }
            ExpressionTab.Emoji -> if (state.customEmoji == null) loadCustomEmojiSets()
        }
    }

    /**
     * The sticker tab, opening on what was sent lately — or on the first
     * set, for an account that has sent none yet, rather than on an empty
     * tab explaining why it is empty.
     */
    private fun loadStickers() {
        _uiState.update { it.copy(stickerPicker = StickerPickerState()) }
        viewModelScope.launch {
            var sets = emptyList<StickerSetPreview>()
            var recent = emptyList<StickerContent>()
            attempt("Could not load stickers") {
                sets = repository.stickerSets()
                recent = repository.recentStickers()
            }
            val first = if (recent.isEmpty()) sets.firstOrNull()?.id else null
            _uiState.update { state ->
                state.copy(
                    stickerPicker = state.stickerPicker?.copy(
                        sets = sets,
                        selected = first ?: RECENT_STICKERS,
                        stickers = recent,
                        isLoading = first != null
                    )
                )
            }
            if (first != null) onStickerSetSelected(first)
        }
    }

    fun onStickerSetSelected(setId: Long) {
        _uiState.update { state ->
            state.copy(stickerPicker = state.stickerPicker?.copy(selected = setId, isLoading = true))
        }
        viewModelScope.launch {
            var stickers = emptyList<StickerContent>()
            attempt("Could not load stickers") {
                stickers = if (setId == RECENT_STICKERS) {
                    repository.recentStickers()
                } else {
                    repository.stickerSet(setId)
                }
            }
            _uiState.update { state ->
                // Only if that is still the tab: a quick run across the tabs
                // must not end on the stickers of the one passed through.
                if (state.stickerPicker?.selected != setId) state
                else state.copy(stickerPicker = state.stickerPicker.copy(stickers = stickers, isLoading = false))
            }
        }
    }

    /**
     * Sent at once, answering the message being replied to, if any. The
     * panel stays up, as it does in every Telegram client: stickers are
     * often sent two at a time.
     */
    fun onStickerPicked(sticker: StickerContent) {
        val answering = _uiState.value.replyTo
        _uiState.update { it.copy(replyTo = null) }
        viewModelScope.launch {
            attempt("Could not send the sticker") {
                repository.sendSticker(chatId, sticker, answering?.id)
            }
        }
    }

    /**
     * A still sticker, made into a picture by the screen, sent as a photo —
     * answering the message being replied to, as a sticker would.
     */
    fun onStickerImage(path: String?) {
        if (path == null) {
            _uiState.update { it.copy(errorMessage = "This sticker cannot be sent as an image") }
            return
        }
        val answering = _uiState.value.replyTo
        _uiState.update { it.copy(replyTo = null) }
        viewModelScope.launch {
            attempt("Could not send the image") {
                repository.sendMessage(chatId, "", AttachmentDraft.Photos(listOf("file://$path")), answering?.id)
            }
        }
    }

    private var gifSearch: Job? = null

    /**
     * What is typed into the GIF search, asked of Telegram once typing
     * pauses — each letter a request to a bot would be most of them wasted.
     * Empty goes back to the saved GIFs.
     */
    fun onGifQueryChange(query: String) {
        _uiState.update { it.copy(gifPicker = (it.gifPicker ?: GifPickerState()).copy(query = query)) }
        searchGifs(query, pause = GIF_SEARCH_PAUSE_MILLIS)
    }

    private fun searchGifs(query: String, pause: Long = 0) {
        gifSearch?.cancel()
        gifSearch = viewModelScope.launch {
            delay(pause)
            _uiState.update { it.copy(gifPicker = it.gifPicker?.copy(isLoading = true)) }
            var gifs = emptyList<GifItem>()
            attempt("Could not load GIFs") {
                gifs = if (query.isBlank()) repository.savedGifs() else repository.searchGifs(query)
            }
            _uiState.update { state ->
                if (state.gifPicker?.query != query) state
                else state.copy(gifPicker = state.gifPicker.copy(gifs = gifs, isLoading = false))
            }
        }
    }

    /** A GIF on screen: its poster and its clip fetched, and put in its place. */
    fun onGifVisible(gif: GifItem) {
        val video = gif.video
        if (video.path != null) return
        viewModelScope.launch {
            val thumb = video.thumbPath ?: video.thumbFileId?.let { repository.downloadFile(it) }
            val clip = video.fileId?.let { repository.downloadFile(it) }
            _uiState.update { state ->
                val picker = state.gifPicker ?: return@update state
                state.copy(
                    gifPicker = picker.copy(
                        gifs = picker.gifs.map {
                            if (it.id != gif.id) it
                            else it.copy(video = it.video.copy(thumbPath = thumb ?: it.video.thumbPath, path = clip))
                        }
                    )
                )
            }
        }
    }

    /** Sent at once, like a sticker, and the panel stays up. */
    fun onGifPicked(gif: GifItem) {
        val answering = _uiState.value.replyTo
        _uiState.update { it.copy(replyTo = null) }
        viewModelScope.launch {
            attempt("Could not send the GIF") {
                repository.sendGif(chatId, gif, answering?.id)
            }
        }
    }

    /** A file by TDLib's id, for what draws one without a state holder; see LocalFileLoader. */
    suspend fun loadFile(fileId: Int): String? = repository.downloadFile(fileId)

    /** A custom emoji's sticker, for a Premium reaction; see LocalCustomEmojiLoader. */
    suspend fun loadCustomEmoji(id: Long): StickerContent? =
        try {
            repository.customEmoji(listOf(id))[id]
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    fun onAttachmentSheetOpenChange(open: Boolean) =
        _uiState.update { it.copy(attachmentSheetOpen = open) }

    fun onAttachmentPicked(draft: AttachmentDraft) =
        // Closing here rather than where the picker is launched: the sheet has
        // to stay up while the system picker is in front of it, or coming back
        // from a cancelled pick lands on the composer with nothing explained.
        _uiState.update { it.copy(pendingAttachment = draft, attachmentSheetOpen = false) }

    fun onAttachmentCleared() = _uiState.update { it.copy(pendingAttachment = null) }

    /** Answering a message cancels an edit in progress, and vice versa. */
    fun onReplyTo(message: ChatMessage) =
        _uiState.update { it.copy(replyTo = message, editing = null) }

    fun onEdit(message: ChatMessage) =
        _uiState.update { it.copy(editing = message, replyTo = null, draft = message.text) }

    fun onComposerBannerCancelled() = _uiState.update {
        // Cancelling an edit throws the draft away too: it was the old text,
        // put there to be amended, not something the person typed.
        if (it.editing != null) it.copy(editing = null, replyTo = null, draft = "")
        else it.copy(replyTo = null)
    }

    /**
     * Sends the draft, or saves the edit.
     *
     * The composer is cleared before the request rather than after it, so a
     * second tap while the first is on its way has nothing to send twice.
     * Nothing is fetched afterwards: the message arrives through
     * [TelegramRepository.messageUpdates] like any other, and an edit is
     * drawn at once and confirmed the same way. A refusal puts back what was
     * typed, unless something new has been typed since.
     */
    fun onSend() {
        val state = _uiState.value
        val text = state.draft
        val attachment = state.pendingAttachment
        if (text.isBlank() && attachment == null) return
        val amending = state.editing
        val answering = state.replyTo
        val withEmoji = picked.toList()
        picked.clear()

        _uiState.update {
            it.copy(draft = "", pendingAttachment = null, replyTo = null, editing = null)
        }
        // A new message goes at the end of the conversation, so that is where
        // the list has to be to show it — not in a stretch of the past.
        if (amending == null) onJumpToLatest()
        if (amending != null) applyUpdate(MessageUpdate.Edited(chatId, amending.id, text))

        viewModelScope.launch {
            val sent = if (amending != null) {
                attempt("Could not save the edit") {
                    repository.editMessage(chatId, amending.id, text)
                }
            } else {
                attempt("Could not send") {
                    repository.sendMessage(chatId, text, attachment, answering?.id, picked = withEmoji)
                }
            }
            if (sent) return@launch
            _uiState.update {
                if (it.draft.isNotEmpty() || it.pendingAttachment != null) it
                else it.copy(
                    draft = text,
                    pendingAttachment = attachment,
                    replyTo = answering,
                    editing = amending
                )
            }
            // The edit was drawn before it was refused; the server's copy is
            // the one to show.
            if (amending != null) reload()
        }
    }

    // ── reacting ─────────────────────────────────────────────────────────

    fun onReactionsRequested(message: ChatMessage) =
        _uiState.update { it.copy(reactingTo = message) }

    fun onReactionPickerDismissed() = _uiState.update { it.copy(reactingTo = null) }

    /**
     * Redraws the row immediately and tells the server afterwards.
     *
     * No reload follows. The optimistic result is what the backends apply to
     * their own copy as well, so re-opening the chat agrees with it; reloading
     * here would replace a correct row with an identical one and flicker for
     * nothing.
     */
    fun onReactionToggled(message: ChatMessage, emoji: String) {
        _uiState.update { state ->
            state.copy(reactingTo = null).mapMessage(message.id) {
                it.copy(reactions = toggleReaction(it.reactions, emoji))
            }
        }
        viewModelScope.launch {
            val done = attempt("Could not react") {
                repository.toggleReaction(chatId, message.id, emoji)
            }
            // Toggling is its own inverse, so a refusal is undone by drawing
            // the same tap again.
            if (!done) _uiState.update { state ->
                state.mapMessage(message.id) {
                    it.copy(reactions = toggleReaction(it.reactions, emoji))
                }
            }
        }
    }

    /**
     * Rewrites one message wherever it happens to live.
     *
     * A message is in [ChatUiState.detail] or in [ChatUiState.olderMessages]
     * depending on whether it arrived with the chat or was paged in, and a
     * caller that has one in hand has no reason to know which.
     */
    private fun ChatUiState.mapMessage(
        messageId: Long,
        transform: (ChatMessage) -> ChatMessage
    ): ChatUiState = copy(
        // let rather than ?.copy(detail.messages): a safe call smart-casts its
        // receiver, not the same property named again in the arguments.
        detail = detail?.let { chat ->
            chat.copy(
                messages = chat.messages.map {
                    if (it.id == messageId) transform(it) else it
                }
            )
        },
        olderMessages = olderMessages.map { if (it.id == messageId) transform(it) else it },
        detachedWindow = detachedWindow?.map { if (it.id == messageId) transform(it) else it },
        // The grid's copy too, so a file fetched from one screen is not
        // fetched again from the other.
        media = media.map { if (it.id == messageId) transform(it) else it }
    )

    // ── polls and bots ───────────────────────────────────────────────────

    /**
     * Votes, drawing the result at once the way a reaction is drawn. A
     * refusal puts the poll back as it was; an acceptance needs nothing,
     * since the server's counts arrive as a [MessageUpdate.PollChanged].
     */
    fun onVote(message: ChatMessage, chosen: Set<Int>) {
        val before = message.poll ?: return
        _uiState.update { state ->
            state.mapMessage(message.id) { it.copy(poll = before.withVote(chosen)) }
        }
        viewModelScope.launch {
            val done = attempt(if (chosen.isEmpty()) "Could not retract the vote" else "Could not vote") {
                repository.votePoll(chatId, message.id, chosen.sorted())
            }
            if (!done) _uiState.update { state ->
                state.mapMessage(message.id) { it.copy(poll = before) }
            }
        }
    }

    /**
     * A bot button that needs the bot. Links and copying never reach here —
     * the screen does those itself.
     */
    fun onBotButton(message: ChatMessage, button: InlineButton) {
        when (val action = button.action) {
            is ButtonAction.Callback -> viewModelScope.launch {
                attempt("The bot did not answer") {
                    val answer = repository.pressButton(chatId, message.id, action.data)
                    if (answer != null) _uiState.update { it.copy(botAnswer = answer) }
                }
            }
            else -> Unit
        }
    }

    fun onBotAnswerShown() = _uiState.update { it.copy(botAnswer = null) }

    /** A key of the bot's keyboard: its text goes out as a message. */
    fun onReplyKey(text: String) {
        if (text.isBlank()) return
        onJumpToLatest()
        viewModelScope.launch {
            attempt("Could not send") { repository.sendMessage(chatId, text) }
        }
    }

    // ── pinning ──────────────────────────────────────────────────────────

    /** Pins or unpins, drawn at once and put back if the server refuses. */
    fun onPinToggled(message: ChatMessage) {
        val pin = !message.isPinned
        applyUpdate(MessageUpdate.PinChanged(chatId, message.id, pin))
        viewModelScope.launch {
            val done = attempt(if (pin) "Could not pin" else "Could not unpin") {
                repository.setMessagePinned(chatId, message.id, pin)
            }
            if (!done) applyUpdate(MessageUpdate.PinChanged(chatId, message.id, !pin))
        }
    }

    // ── writing a poll ───────────────────────────────────────────────────

    fun onPollOpen() = _uiState.update { it.copy(pollDraft = PollDraft(), attachmentSheetOpen = false) }

    fun onPollChange(draft: PollDraft) = _uiState.update { it.copy(pollDraft = draft) }

    fun onPollDismiss() = _uiState.update { it.copy(pollDraft = null) }

    /** Sends the poll being written; a refusal keeps the form as it was. */
    fun onPollSend() {
        val draft = _uiState.value.pollDraft ?: return
        if (!draft.canSend) return
        _uiState.update { it.copy(pollDraft = null) }
        onJumpToLatest()
        viewModelScope.launch {
            val sent = attempt("Could not send the poll") { repository.sendPoll(chatId, draft) }
            if (!sent) _uiState.update { it.copy(pollDraft = draft) }
        }
    }

    // ── scheduled messages ───────────────────────────────────────────────

    /**
     * Schedules what is typed for [sendAt], epoch seconds. The composer is
     * cleared as for a send; the message goes to the scheduled list, not
     * the conversation, and a snackbar says when it will go.
     */
    fun onSchedule(sendAt: Long, nowSeconds: Long = System.currentTimeMillis() / 1000) {
        val state = _uiState.value
        val text = state.draft
        if (text.isBlank() || state.pendingAttachment != null || state.editing != null) return
        if (!isValidSchedule(sendAt, nowSeconds)) {
            _uiState.update { it.copy(errorMessage = "Pick a time in the future") }
            return
        }
        val answering = state.replyTo
        _uiState.update { it.copy(draft = "", replyTo = null) }
        viewModelScope.launch {
            val done = attempt("Could not schedule") {
                repository.sendMessage(chatId, text, replyToId = answering?.id, sendAt = sendAt)
            }
            _uiState.update {
                if (done) it.copy(notice = "Scheduled for ${scheduleLabel(sendAt, nowSeconds, ZoneId.systemDefault())}")
                else if (it.draft.isEmpty()) it.copy(draft = text, replyTo = answering)
                else it
            }
        }
    }

    fun onScheduledOpen() {
        _uiState.update { it.copy(scheduled = it.scheduled ?: emptyList()) }
        refreshScheduled()
    }

    fun onScheduledDismiss() = _uiState.update { it.copy(scheduled = null) }

    private fun refreshScheduled() {
        viewModelScope.launch {
            val waiting = runCatching { repository.scheduledMessages(chatId) }.getOrDefault(emptyList())
            _uiState.update { if (it.scheduled == null) it else it.copy(scheduled = waiting) }
        }
    }

    fun onScheduledSendNow(message: ChatMessage) {
        _uiState.update { state -> state.copy(scheduled = state.scheduled?.filterNot { it.id == message.id }) }
        viewModelScope.launch {
            attempt("Could not send") { repository.sendScheduledNow(chatId, message.id) }
            refreshScheduled()
        }
    }

    fun onScheduledDelete(message: ChatMessage) {
        _uiState.update { state -> state.copy(scheduled = state.scheduled?.filterNot { it.id == message.id }) }
        viewModelScope.launch {
            attempt("Could not delete") { repository.deleteMessage(chatId, message.id, forEveryone = true) }
            refreshScheduled()
        }
    }

    fun onNoticeShown() = _uiState.update { it.copy(notice = null) }

    // ── contacts and places ──────────────────────────────────────────────

    /** A contact card's Add: into this account's contacts, by its number. */
    fun onContactAdd(contact: ContactContent) {
        viewModelScope.launch {
            var added: Long? = null
            val done = attempt("Could not add the contact") {
                added = repository.addContact(contact.phoneNumber, contact.firstName, contact.lastName)
            }
            if (done) {
                val name = contact.displayName.ifBlank { contact.phoneNumber }
                _uiState.update {
                    it.copy(notice = if (added != null) "$name added to contacts" else "$name is not on Telegram")
                }
            }
        }
    }

    /** Where this phone is, sent as a place, answering the reply if there is one. */
    fun onSendLocation(latitude: Double, longitude: Double, accuracyMeters: Double) {
        val answering = _uiState.value.replyTo
        _uiState.update { it.copy(replyTo = null) }
        viewModelScope.launch {
            attempt("Could not send the location") {
                repository.sendLocation(chatId, latitude, longitude, accuracyMeters, answering?.id)
            }
        }
    }

    /** The sheet of contacts to send one of, from the attachment sheet. */
    fun onContactPickerOpen() {
        _uiState.update { it.copy(attachmentSheetOpen = false, contactPicker = emptyList()) }
        viewModelScope.launch {
            var contacts = emptyList<TelegramUser>()
            attempt("Could not load contacts") { contacts = repository.contacts() }
            _uiState.update { state -> if (state.contactPicker == null) state else state.copy(contactPicker = contacts) }
        }
    }

    fun onContactPickerDismiss() = _uiState.update { it.copy(contactPicker = null) }

    /** Sends [user]'s card, answering the message being replied to, if any. */
    fun onContactPicked(user: TelegramUser) {
        val answering = _uiState.value.replyTo
        _uiState.update { it.copy(contactPicker = null, replyTo = null) }
        viewModelScope.launch {
            attempt("Could not send the contact") {
                repository.sendContact(
                    chatId,
                    ContactContent(
                        firstName = user.firstName,
                        lastName = user.lastName,
                        phoneNumber = user.phoneNumber.orEmpty(),
                        userId = user.id
                    ),
                    answering?.id
                )
            }
        }
    }

    /**
     * The chat's invite link revoked and a new one made — or the first made,
     * where there was none. Only an admin who may invite can; anyone else
     * hears the server's no.
     */
    fun onRenewInviteLink() {
        viewModelScope.launch {
            var link: String? = null
            val done = attempt("Could not make an invite link") { link = repository.renewInviteLink(chatId) }
            if (done && link != null) _uiState.update { it.copy(inviteLink = link) }
        }
    }

    /**
     * A message's text in the phone's own language (1.8), by Telegram's
     * translator — the one the official client uses, so the result is the
     * same. The dialog opens at once and fills when the answer comes.
     */
    fun onTranslate(message: ChatMessage, language: String) {
        if (message.text.isBlank()) return
        _uiState.update { it.copy(translation = Translation(message.id, language)) }
        viewModelScope.launch {
            var translated: String? = null
            val done = attempt("Could not translate it") {
                translated = repository.translateMessage(message.chatId, message.id, language)
            }
            _uiState.update { state ->
                val open = state.translation?.takeIf { it.messageId == message.id } ?: return@update state
                val text = translated
                when {
                    done && text != null -> state.copy(translation = open.copy(text = text))
                    // A failure has said so through the notice already.
                    done -> state.copy(translation = null, notice = "Nothing to translate")
                    else -> state.copy(translation = null)
                }
            }
        }
    }

    fun onTranslationDismissed() {
        _uiState.update { it.copy(translation = null) }
    }

    /**
     * For geeks → Hide blocked people in groups (1.8, after Nekogram): their
     * messages left out of a group's history, as if they were not there.
     * Asked again on every change of the setting, so someone blocked since
     * the chat opened goes when it is next switched on.
     */
    fun onHideBlocked(on: Boolean) {
        val isGroup = _uiState.value.detail?.chat?.isGroup == true
        if (!on || !isGroup) {
            _uiState.update { it.copy(hiddenSenders = emptySet()) }
            return
        }
        viewModelScope.launch {
            val blocked = runCatching { repository.blockedPeople() }.getOrNull() ?: return@launch
            _uiState.update { it.copy(hiddenSenders = blocked.mapTo(HashSet()) { person -> person.id }) }
        }
    }

    /**
     * For geeks → More in a message's menu (1.8, after Nekogram): the
     * message again in this chat, as a copy; into Saved Messages, as a
     * forward that says where it came from; or its file off the phone.
     */
    fun onMessageExtra(message: ChatMessage, extra: MessageExtra) {
        viewModelScope.launch {
            when (extra) {
                MessageExtra.Repeat -> attempt("Could not repeat it") {
                    repository.forwardMessages(chatId, listOf(message.id), chatId, true)
                }
                MessageExtra.SaveToSavedMessages -> {
                    val me = repository.observeAuth().value.me ?: return@launch
                    val done = attempt("Could not save it") {
                        val saved = repository.openPrivateChat(me.id)
                        repository.forwardMessages(chatId, listOf(message.id), saved, false)
                    }
                    if (done) _uiState.update { it.copy(notice = "Saved to Saved Messages") }
                }
                MessageExtra.DeleteFile -> {
                    val fileId = message.downloadedFileId() ?: return@launch
                    val done = attempt("Could not delete the file") { repository.deleteDownloadedFile(fileId) }
                    if (done) _uiState.update { it.copy(notice = "Deleted from this phone") }
                }
            }
        }
    }

    /**
     * A GIF from a message kept among the saved ones, as the official
     * client's "Add to GIFs" does. The GIF tab is loaded again when it next
     * opens, so it is there.
     */
    fun onSaveGif(message: ChatMessage) {
        val video = message.video ?: return
        viewModelScope.launch {
            val done = attempt("Could not add the GIF") { repository.saveGif(video) }
            if (done) _uiState.update { it.copy(notice = "Added to GIFs", gifPicker = null) }
        }
    }

    // ── voice ────────────────────────────────────────────────────────────

    // Voice messages play in the app's VoicePlayback, so they go on when
    // this chat is closed; the bubbles here show what it is doing.
    init {
        viewModelScope.launch {
            voice.state.collect { now ->
                val here = now.messageId != null && now.chatId == chatId
                _uiState.update {
                    it.copy(
                        playingVoiceId = if (here && now.isPlaying) now.messageId else null,
                        voiceProgress = if (here) now.progress else 0f
                    )
                }
            }
        }
    }

    /**
     * Plays a voice message, stops it, or fetches it first.
     *
     * Tapping the one that is playing stops it, which is the same gesture
     * meaning the opposite thing — the button shows which of the two it is.
     * A message whose file has not arrived is downloaded on the tap rather
     * than on arrival in the window: a conversation of voice notes would
     * otherwise fetch every one of them to play none.
     */
    fun onVoiceToggled(message: ChatMessage) {
        // The one under way, playing or paused from the bar: pause or go on.
        val now = voice.state.value
        if (now.messageId == message.id && now.chatId == chatId) {
            voice.toggle()
            return
        }

        val local = message.voicePath
        if (local != null) {
            start(message.id, local)
            return
        }

        val fileId = message.voiceFileId ?: return
        _uiState.update { it.copy(loadingVoiceId = message.id) }
        viewModelScope.launch {
            val path = repository.downloadFile(fileId)
            _uiState.update { it.copy(loadingVoiceId = null) }
            if (path != null) {
                // Kept on the message too, so a second play does not fetch it
                // again for as long as the window lives.
                _uiState.update { state ->
                    state.mapMessage(message.id) { it.copy(voicePath = path) }
                }
                start(message.id, path)
            }
        }
    }

    /**
     * Moves playback within the message that is playing.
     *
     * A tap on a bar of any other message means play that one instead — the
     * bar is where the finger landed, not a request to seek something that is
     * silent.
     */
    fun onVoiceSeek(message: ChatMessage, fraction: Float) {
        if (voice.state.value.messageId != message.id) {
            onVoiceToggled(message)
            return
        }
        voice.seekTo(fraction)
    }

    /**
     * [messageId] plays, and the chat's voice messages below it after it —
     * a run of five is heard as five, not one and a tap four times more.
     */
    private fun start(messageId: Long, path: String) {
        val messages = _uiState.value.messages
        val message = messages.firstOrNull { it.id == messageId }?.copy(voicePath = path) ?: return
        val after = messages
            .filter { it.id > messageId && it.contentType == MessageContentType.Voice }
            .sortedBy { it.id }
        voice.play(message, after)
        if (voice.state.value.messageId == messageId) onContentOpened(messageId)
    }

    /** Messages whose content has been reported opened, so each is told once. */
    private val openedContent = mutableSetOf<Long>()

    /**
     * A voice or video message was played: tell Telegram, so its sender sees
     * it listened to. Nothing said this before, and friends saw their voice
     * messages unplayed however many times they had been heard.
     */
    fun onContentOpened(messageId: Long) {
        if (!openedContent.add(messageId)) return
        viewModelScope.launch {
            try {
                repository.openMessageContent(chatId, messageId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    /** The draft the server holds, as far as this chat knows; null until read. */
    private var keptDraft: String? = null
    private var draftRestored = false

    /** The chat's draft, or in a topic the topic's own. */
    private suspend fun saveDraftHere(text: String) {
        if (topicId == 0) repository.saveDraft(chatId, text) else repository.saveTopicDraft(chatId, topicId, text)
    }

    private suspend fun keepDraft(text: String) {
        if (text == keptDraft) return
        keptDraft = text
        try {
            saveDraftHere(text)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // A draft that did not save is still in the field; the next
            // keystroke or leaving the chat tries again.
            keptDraft = null
        }
    }

    /**
     * Whether an update belongs on this screen: in a topic, a new message
     * only if it was written in the topic. Anything addressed by id — an
     * edit, a deletion — is about a message already here, or about nothing.
     */
    private fun inThisTopic(update: MessageUpdate): Boolean {
        if (topicId == 0) return true
        return when (update) {
            is MessageUpdate.Added -> update.message.topicId == topicId
            is MessageUpdate.Replaced -> update.message.topicId == topicId
            else -> true
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCleared() {
        super.onCleared()
        // Leaving within the second after the last keystroke would lose it,
        // and this scope is already gone — so the last save outlives it.
        val state = _uiState.value
        if (draftRestored && state.editing == null && state.draft != keptDraft) {
            val text = state.draft
            GlobalScope.launch { runCatching { saveDraftHere(text) } }
        }
        repository.releaseChat(chatId)
        if (topicId != 0) repository.closeOpenTopic(chatId, topicId)
        // Voice messages are not stopped: they are the app's, and go on
        // after the chat is closed, with the bar to stop them.
    }

    /**
     * Fetches a photo the first time its bubble is on screen.
     *
     * Unlike a voice note, a photo is meant to be seen without being asked
     * for, so this is driven by the bubble appearing rather than by a tap. The
     * set of ids already asked for is what stops a message being fetched again
     * every time it scrolls back into view.
     */
    fun onPhotoVisible(message: ChatMessage) {
        // A video's poster, when this is one: the same fetch with a different
        // file behind it, and the same guard against asking twice. A video
        // itself is not fetched here — see onVideoOpened — but a GIF and a
        // round video message are: they are seconds long and meant to be
        // seen without asking, a GIF playing by itself.
        message.video?.let { video ->
            if (message.contentType == MessageContentType.Animation ||
                message.contentType == MessageContentType.VideoNote
            ) {
                fetchVideo(message)
            }
            val thumbId = video.thumbFileId ?: return
            if (video.thumbPath != null || !requestedPhotos.add(thumbId)) return
            viewModelScope.launch {
                val path = repository.downloadFile(thumbId)
                if (path == null) {
                    // Forgotten, so the next time it scrolls into view asks
                    // again — a dropped connection is not a missing poster.
                    requestedPhotos.remove(thumbId)
                    return@launch
                }
                _uiState.update { state ->
                    state.mapMessage(message.id) {
                        it.copy(video = it.video?.copy(thumbPath = path))
                    }
                }
            }
            return
        }
        val fileId = message.photoFileId ?: return
        if (message.photoPath != null || !requestedPhotos.add(fileId)) return
        viewModelScope.launch {
            val path = repository.downloadFile(fileId)
            if (path == null) {
                // See the poster above: failing once must not mean never.
                requestedPhotos.remove(fileId)
                return@launch
            }
            _uiState.update { state ->
                state.mapMessage(message.id) { it.copy(photoPath = path) }
            }
        }
    }

    /** File ids already asked for, so scrolling does not re-request them. */
    private val requestedPhotos = mutableSetOf<Int>()

    /**
     * Opens a photo full-screen.
     *
     * Only one that has arrived: tapping a bubble still loading would open a
     * black screen and a spinner, which is a worse answer than the bubble the
     * finger is already on.
     */
    fun onPhotoOpened(message: ChatMessage) {
        if (message.photoPath == null) return
        _uiState.update { it.copy(viewingPhoto = message) }
    }

    fun onPhotoClosed() = _uiState.update { it.copy(viewingPhoto = null) }

    /**
     * A page of the gallery came into view: a video's file is fetched, as
     * opening it would, and a photo still at its thumbnail gets its full size.
     */
    fun onGalleryPage(message: ChatMessage) {
        when {
            message.video != null && message.contentType == MessageContentType.Video -> fetchVideo(message)
            message.photoPath == null -> onPhotoVisible(message)
        }
    }

    /**
     * Opens a video full-screen, fetching it if it is not here yet.
     *
     * Unlike a photo, this opens before the file has arrived. A video is tens
     * of megabytes and is deliberately not downloaded on sight, so the tap is
     * the first moment anyone asked for it — and a button that sat dead for
     * the length of a download would read as broken. The dialog shows a
     * spinner and starts playing when the bytes land.
     */
    /**
     * A file in a bubble tapped: opened at once when it is on the phone,
     * fetched first when it is not, the bubble showing the wait.
     */
    fun onDocumentOpened(message: ChatMessage) {
        message.documentPath?.let { path ->
            _uiState.update { it.copy(fileToOpen = FileToOpen(path, message.mimeType, message.fileName)) }
            return
        }
        val fileId = message.documentFileId ?: return
        if (_uiState.value.openingFileId != null) return
        _uiState.update { it.copy(openingFileId = message.id) }
        viewModelScope.launch {
            // Through the download list, so it is in Downloads — and keeps
            // coming if this chat is closed before it has.
            val outcome = runCatching { repository.downloadToList(message.chatId, message.id, fileId) }
                .getOrDefault(com.telegramyou.app.telegram.model.DownloadOutcome.Failed)
            val path = (outcome as? com.telegramyou.app.telegram.model.DownloadOutcome.Done)?.path
            _uiState.update { state ->
                val kept = if (path == null) state else state.mapMessage(message.id) { it.copy(documentPath = path) }
                kept.copy(
                    openingFileId = null,
                    fileToOpen = path?.let { FileToOpen(it, message.mimeType, message.fileName) },
                    // Paused or cancelled in Downloads is not a failure.
                    errorMessage = if (outcome == com.telegramyou.app.telegram.model.DownloadOutcome.Failed) {
                        "Could not download ${message.fileName ?: "the file"}"
                    } else {
                        kept.errorMessage
                    }
                )
            }
        }
    }

    fun onFileOpened() = _uiState.update { it.copy(fileToOpen = null) }

    fun onFileRefused(name: String?) = _uiState.update {
        it.copy(fileToOpen = null, errorMessage = "No app on this phone opens ${name ?: "this file"}")
    }

    fun onVideoOpened(message: ChatMessage) {
        if (message.video == null) return
        _uiState.update { it.copy(viewingVideo = message) }
        fetchVideo(message)
    }

    /** The video file itself, once, into the message and into the open player. */
    private fun fetchVideo(message: ChatMessage) {
        // Played as it downloads where the backend can (VideoPage): fetching
        // it whole here as well would compete with the stream for the
        // connection, and make the first frame wait for the last byte.
        if (repository.canStream) return
        val video = message.video ?: return
        val fileId = video.fileId ?: return
        if (video.path != null || !requestedVideos.add(fileId)) return
        viewModelScope.launch {
            val path = repository.downloadFile(fileId)
            if (path == null) {
                // Forgotten, so opening it again tries again rather than
                // spinning on a download nobody is running.
                requestedVideos.remove(fileId)
                return@launch
            }
            _uiState.update { state ->
                val withFile: (ChatMessage) -> ChatMessage = {
                    it.copy(video = it.video?.copy(path = path))
                }
                state.mapMessage(message.id, withFile).copy(
                    // The open dialog holds its own copy of the message from
                    // before the file arrived, and it may have been opened
                    // from the media grid — whose messages need not be in
                    // the conversation's window at all. Patched directly, or
                    // it would keep its spinner until closed and reopened.
                    viewingVideo = state.viewingVideo
                        ?.takeIf { it.id == message.id }
                        ?.let(withFile)
                        ?: state.viewingVideo
                )
            }
        }
    }

    fun onVideoClosed() = _uiState.update { it.copy(viewingVideo = null) }

    /** Video file ids already asked for, so reopening does not re-request. */
    private val requestedVideos = mutableSetOf<Int>()

    // ── searching ────────────────────────────────────────────────────────

    /** Cancelled on each keystroke, which is what makes the delay a debounce. */
    private var searchJob: Job? = null

    fun onSearchOpenChange(open: Boolean) {
        searchJob?.cancel()
        // Closing clears it: a query left behind would still be filtering the
        // next time the field is opened, with nothing on screen saying so.
        _uiState.update {
            it.copy(search = if (open) ChatSearchState(isOpen = true) else ChatSearchState())
        }
    }

    fun onSearchQueryChange(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update {
                it.copy(
                    search = it.search.copy(
                        query = query,
                        results = emptyList(),
                        isSearching = false
                    )
                )
            }
            return
        }
        _uiState.update { it.copy(search = it.search.copy(query = query, isSearching = true)) }
        searchJob = viewModelScope.launch {
            // Long enough that typing a word is one request rather than five,
            // short enough that it does not feel like waiting. Same figure as
            // the chat list's search, for the same reason.
            delay(SEARCH_DEBOUNCE_MS)
            val hits = repository.searchChatMessages(chatId, query)
            _uiState.update {
                it.copy(search = it.search.copy(results = hits, isSearching = false))
            }
        }
    }

    // ── selecting ────────────────────────────────────────────────────────

    /**
     * Adds or removes one message.
     *
     * The same call starts a selection and ends it: the first message chosen
     * raises the toolbar and deselecting the last one puts it away, so there
     * is no separate mode to enter or leave.
     */
    fun onSelectionToggled(message: ChatMessage) {
        _uiState.update { it.copy(selection = it.selection.toggle(message.id)) }
        // The selection bar offers Delete from what the selected messages
        // allow, so what they allow has to be known.
        onMessageActionsNeeded(message)
    }

    /**
     * A message's menu is opening, or it is being selected: ask what may be
     * done to it. TDLib keeps that apart from the message (see
     * MessagePermissions); the demo's messages carry it and answer null.
     */
    fun onMessageActionsNeeded(message: ChatMessage) {
        viewModelScope.launch {
            val options = try {
                repository.messageReactions(chatId, message.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            _uiState.update { it.copy(reactionOptions = options) }
        }
        viewModelScope.launch {
            val permissions: MessagePermissions? = try {
                repository.messagePermissions(chatId, message.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (permissions == null) return@launch
            _uiState.update { state -> state.mapMessage(message.id) { it.withPermissions(permissions) } }
        }
    }

    /** Forward from a message's own menu: it alone, into the forward sheet. */
    fun onForwardOne(message: ChatMessage) =
        _uiState.update {
            it.copy(selection = it.selection.cleared().toggle(message.id), forwardSheetOpen = true)
        }

    fun onSelectionCleared() =
        _uiState.update { it.copy(selection = it.selection.cleared()) }

    fun onForwardRequested() {
        _uiState.update { it.copy(forwardSheetOpen = true) }
        // The contacts too, so the sheet's search reaches people there is no
        // chat with yet — Telegram's forward search does.
        viewModelScope.launch {
            val contacts = runCatching { repository.contacts() }.getOrDefault(emptyList())
            _uiState.update { it.copy(forwardContacts = contacts) }
        }
    }

    fun onForwardDismissed() =
        _uiState.update { it.copy(forwardSheetOpen = false) }

    /**
     * Sends the selection on, then puts both the sheet and the selection away.
     *
     * The conversation is not reloaded: the messages went somewhere else, and
     * nothing about this one changed.
     */
    fun onForwardTo(target: ChatPreview, withoutQuote: Boolean = false, onForwarded: (Long) -> Unit = {}) =
        forwardTo(withoutQuote, onForwarded) { target.id }

    /**
     * To a contact found by the sheet's search: their private chat, made if
     * there is none yet, and the messages into it.
     */
    fun onForwardToContact(user: TelegramUser, withoutQuote: Boolean = false, onForwarded: (Long) -> Unit = {}) =
        forwardTo(withoutQuote, onForwarded) { repository.openPrivateChat(user.id) }

    /**
     * Then [onForwarded] with where they went, which opens that chat — as
     * the official client does, so what was forwarded is seen arriving
     * (1.7). Only on success: a failed forward stays here with its error.
     */
    private fun forwardTo(withoutQuote: Boolean, onForwarded: (Long) -> Unit, target: suspend () -> Long) {
        val ids = _uiState.value.selection.ids.toList()
        if (ids.isEmpty()) return
        _uiState.update {
            it.copy(forwardSheetOpen = false, selection = it.selection.cleared())
        }
        viewModelScope.launch {
            var sentTo: Long? = null
            attempt("Could not forward") {
                val to = target()
                repository.forwardMessages(chatId, ids, to, withoutQuote)
                sentTo = to
            }
            sentTo?.let(onForwarded)
        }
    }

    fun onSelectionDeleteRequested() =
        _uiState.update { it.copy(confirmingSelectionDelete = true) }

    fun onSelectionDeleteDismissed() =
        _uiState.update { it.copy(confirmingSelectionDelete = false) }

    /**
     * Deletes everything selected, then clears it.
     *
     * One request per message: TDLib takes a list, but the client interface
     * takes one id, and widening it for this would push the batching into both
     * backends for the sake of one caller.
     */
    fun onSelectionDeleted(forEveryone: Boolean) {
        val targets = _uiState.value.selectedMessages
        if (targets.isEmpty()) return
        _uiState.update {
            it.copy(selection = it.selection.cleared(), confirmingSelectionDelete = false)
        }
        delete(targets.map { it.id }, forEveryone)
    }

    /**
     * Takes the messages off the screen at once, then asks the server.
     *
     * A refusal fetches the window again, since it is the only honest way to
     * put back whichever of them the server kept.
     */
    private fun delete(ids: List<Long>, forEveryone: Boolean) {
        applyUpdate(MessageUpdate.Deleted(chatId, ids.toSet()))
        viewModelScope.launch {
            val done = attempt("Could not delete") {
                ids.forEach { repository.deleteMessage(chatId, it, forEveryone) }
            }
            if (!done) reload()
        }
    }

    // ── deleting ─────────────────────────────────────────────────────────

    fun onDeleteRequested(message: ChatMessage) =
        _uiState.update { it.copy(pendingDelete = message) }

    fun onDeleteDismissed() = _uiState.update { it.copy(pendingDelete = null) }

    fun onDeleteConfirmed(message: ChatMessage, forEveryone: Boolean) {
        _uiState.update { it.copy(pendingDelete = null) }
        // The banners pointing at it go with it — see applyUpdate.
        delete(listOf(message.id), forEveryone)
    }

    /**
     * Fetches the invite link for the info screen.
     *
     * On opening that screen rather than with the conversation: it is a
     * round trip to the server for something no one sees until they go
     * looking for it.
     */
    fun loadInviteLink() {
        viewModelScope.launch {
            val link = repository.chatInviteLink(chatId)
            _uiState.update { it.copy(inviteLink = link) }
        }
    }

    /**
     * Fetches who is behind a private chat, for the info screen — on
     * opening it, like the invite link, and again on each visit: a bio or a
     * block can change while it is closed.
     */
    fun loadPerson() {
        viewModelScope.launch {
            var person: PersonProfile? = null
            attempt("Could not load the profile") { person = repository.personInChat(chatId) }
            _uiState.update { it.copy(person = person) }
        }
    }

    /** Blocks or unblocks the person behind this chat; confirmed by the screen. */
    fun onBlockedChange(blocked: Boolean) {
        val person = _uiState.value.person ?: return
        viewModelScope.launch {
            val done = attempt(if (blocked) "Could not block" else "Could not unblock") {
                repository.setBlocked(person.user.id, blocked)
            }
            if (done) _uiState.update { it.copy(person = it.person?.copy(isBlocked = blocked)) }
        }
    }

    fun onLeaveRequested() {
        _uiState.update { it.copy(confirmingLeave = true) }
    }

    fun onLeaveDismissed() {
        _uiState.update { it.copy(confirmingLeave = false) }
    }

    fun onLeaveConfirmed() {
        viewModelScope.launch {
            val left = attempt("Could not leave") { repository.leaveChat(chatId) }
            _uiState.update { it.copy(confirmingLeave = false, hasLeft = left) }
        }
    }

    /**
     * Everything this account has said in the group, deleted for everyone;
     * the answer says how many went, or that there was nothing.
     */
    fun onDeleteAllMine() {
        viewModelScope.launch {
            var count = 0
            val done = attempt("Could not delete your messages") {
                count = repository.deleteAllMyMessages(chatId)
            }
            if (done) {
                val said = when (count) {
                    0 -> "You have no messages here"
                    1 -> "1 message deleted"
                    else -> "$count messages deleted"
                }
                _uiState.update { it.copy(notice = said) }
            }
        }
    }

    /** Cleared by the screen once it has navigated, so a rotation cannot repeat it. */
    fun onLeaveNavigated() {
        _uiState.update { it.copy(hasLeft = false) }
    }

    /**
     * Fetches every photo in this chat, for the media grid.
     *
     * Called when that screen opens rather than with the conversation: it is
     * a separate request and most chats are never browsed this way. Called
     * again on each visit, because photos are added while it is closed and a
     * grid that showed yesterday's set would be quietly wrong.
     */
    fun loadMedia() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMedia = true) }
            val found = repository.chatMedia(chatId)
            _uiState.update { it.copy(media = found, isLoadingMedia = false) }
        }
    }

    /**
     * Asks for the page before the oldest message on screen.
     *
     * Guarded on both flags: a list scrolled to the top fires this on every
     * frame, and without the guard that is a request per frame — and then a
     * request per frame forever once the history runs out.
     */
    fun onLoadOlder() {
        val state = _uiState.value
        if (state.isLoadingOlder || !state.hasMoreOlder) return
        val oldest = state.messages.firstOrNull() ?: return

        _uiState.update { it.copy(isLoadingOlder = true) }
        viewModelScope.launch {
            var older = emptyList<ChatMessage>()
            val done = attempt("Could not load older messages") {
                older = repository.loadOlderMessages(chatId, oldest.id)
            }
            if (!done) {
                // Not "no more history" — the next scroll to the top may try
                // again.
                _uiState.update { it.copy(isLoadingOlder = false) }
                return@launch
            }
            _uiState.update { current ->
                current.copy(
                    olderMessages = older + current.olderMessages,
                    isLoadingOlder = false,
                    hasMoreOlder = older.isNotEmpty()
                )
            }
        }
    }

    // ── jumping ──────────────────────────────────────────────────────────

    private var jumpJob: Job? = null

    /**
     * Brings [messageId] on screen — a search hit, the pinned message. Where
     * it is loaded already that is a scroll. Where it is older than anything
     * loaded, the page around it is fetched and shown in place of the latest
     * messages, which is the only way to reach it without paging back through
     * everything in between.
     */
    fun onJumpToMessage(messageId: Long) {
        if (_uiState.value.messages.any { it.id == messageId }) {
            _uiState.update { it.copy(scrollTarget = messageId) }
            highlight(messageId)
            return
        }
        jumpJob?.cancel()
        jumpJob = viewModelScope.launch {
            var around = emptyList<ChatMessage>()
            val done = attempt("Could not open that message") {
                around = repository.loadMessagesAround(chatId, messageId)
            }
            if (!done || around.none { it.id == messageId }) return@launch
            _uiState.update {
                it.copy(
                    olderMessages = emptyList(),
                    hasMoreOlder = true,
                    detachedWindow = around,
                    isLoadingNewer = false,
                    scrollTarget = messageId
                ).joinedIfCaughtUp()
            }
            highlight(messageId)
        }
    }

    /** The list has scrolled to [ChatUiState.scrollTarget]. */
    fun onScrollTargetReached() {
        _uiState.update { it.copy(scrollTarget = null) }
    }

    /**
     * Back to the latest messages. From a stretch of the past that means
     * dropping it rather than paging forward through everything after it —
     * a cut like the jump that led there, landing on the newest message.
     */
    fun onJumpToLatest() {
        jumpJob?.cancel()
        _uiState.update {
            if (!it.isDetached) it
            else it.copy(
                detachedWindow = null,
                olderMessages = emptyList(),
                hasMoreOlder = true,
                isLoadingNewer = false,
                scrollTarget = it.detail?.messages?.lastOrNull()?.id
            )
        }
    }

    /** The page after the newest message of a detached stretch. */
    fun onLoadNewer() {
        val state = _uiState.value
        val window = state.detachedWindow ?: return
        if (state.isLoadingNewer) return
        val newest = window.lastOrNull() ?: return
        _uiState.update { it.copy(isLoadingNewer = true) }
        viewModelScope.launch {
            var newer = emptyList<ChatMessage>()
            val done = attempt("Could not load newer messages") {
                newer = repository.loadNewerMessages(chatId, newest.id)
            }
            _uiState.update { current ->
                val detached = current.detachedWindow
                when {
                    !done || detached == null -> current.copy(isLoadingNewer = false)
                    // Nothing newer: this stretch runs to the end of the
                    // conversation, and so into the latest window.
                    newer.isEmpty() -> current.copy(isLoadingNewer = false).joined()
                    else -> current.copy(
                        detachedWindow = detached + newer.filter { m -> detached.none { it.id == m.id } },
                        isLoadingNewer = false
                    ).joinedIfCaughtUp()
                }
            }
        }
    }

    /** Folds a detached stretch back in once it reaches the latest messages. */
    private fun ChatUiState.joinedIfCaughtUp(): ChatUiState {
        val window = detachedWindow ?: return this
        val latest = detail?.messages.orEmpty().mapTo(HashSet()) { it.id }
        return if (window.any { it.id in latest }) joined() else this
    }

    /**
     * The stretch becomes history above the latest window: everything in it
     * that the latest window does not already hold goes on the end of
     * [ChatUiState.olderMessages], and the list is one conversation again.
     */
    private fun ChatUiState.joined(): ChatUiState {
        val window = detachedWindow ?: return this
        val latest = detail?.messages.orEmpty().mapTo(HashSet()) { it.id }
        return copy(
            olderMessages = olderMessages + window.takeWhile { it.id !in latest },
            detachedWindow = null
        )
    }

    private fun highlight(messageId: Long) {
        _uiState.update { it.copy(highlightedId = messageId) }
        viewModelScope.launch {
            delay(HIGHLIGHT_MS)
            _uiState.update { if (it.highlightedId == messageId) it.copy(highlightedId = null) else it }
        }
    }

    private companion object {
        /** Long enough to find, short enough not to linger. */
        const val HIGHLIGHT_MS = 1_600L

        const val SEARCH_DEBOUNCE_MS = 250L

        /** Ten position reads a second, which is smooth at a hundred pixels. */
        const val PROGRESS_TICK_MS = 100L

        /** How long typing pauses before the draft is saved. */
        const val DRAFT_SAVE_MS = 1_000L

        /** How long typing in the GIF search pauses before the bot is asked. */
        const val GIF_SEARCH_PAUSE_MILLIS = 400L
    }

    private fun reload() {
        viewModelScope.launch {
            lateinit var detail: ChatDetail
            var reactions = emptyList<String>()
            val opened = attempt("Could not open the chat") {
                detail = repository.openChat(chatId)
                // Asked for once per load rather than per tap: a chat's
                // permitted reactions do not change while it is open, and
                // the picker has to open without waiting for a request.
                reactions = repository.availableReactions(chatId)
            }
            if (!opened) return@launch
            // The window from openChat is fresh, so anything paged in before
            // it is discarded rather than left to duplicate or contradict it.
            // The saved draft goes into the field once, on the first load,
            // and only if nothing has been typed yet. Decided outside the
            // update, whose block may run more than once.
            // Never in a topic: the draft is the chat's, and it would
            // follow the person into every topic they opened.
            val restoring = !draftRestored && topicId == 0
            if (restoring) keptDraft = detail.chat.draft
            if (topicId == 0) draftRestored = true
            _uiState.update {
                val reloaded = it.copy(
                    draft = if (restoring && it.draft.isEmpty()) detail.chat.draft else it.draft,
                    detail = detail,
                    olderMessages = emptyList(),
                    detachedWindow = null,
                    hasMoreOlder = true,
                    availableReactions = reactions
                )
                // A selected message can be gone by the time the chat reloads —
                // deleted here or by someone else. Keeping its id would leave
                // the toolbar counting something that cannot be acted on.
                reloaded.copy(selection = it.selection.retaining(reloaded.messages))
            }
        }
    }
}
