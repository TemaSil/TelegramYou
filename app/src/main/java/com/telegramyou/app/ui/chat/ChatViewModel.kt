package com.telegramyou.app.ui.chat

import com.telegramyou.app.notifications.ChatNotificationSettings
import com.telegramyou.app.telegram.model.StickerSetPreview
import com.telegramyou.app.telegram.model.StickerContent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.telegramyou.app.navigation.Route
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.AttachmentDraft
import com.telegramyou.app.telegram.model.ChatDetail
import com.telegramyou.app.telegram.model.ChatMessage
import com.telegramyou.app.ui.media.FileTransfer
import com.telegramyou.app.telegram.model.ChatPreview
import com.telegramyou.app.telegram.model.MessageUpdate
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
data class ChatUiState(
    val detail: ChatDetail? = null,
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
    /**
     * The chat's invite link, for the info screen. Null for a private chat
     * and for a group this account may not invite to — see `chatInviteLink`.
     */
    val inviteLink: String? = null,
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
    /** The sticker sheet, while it is up. */
    val stickerPicker: StickerPickerState? = null
) {
    val messages: List<ChatMessage>
        get() = olderMessages + (detachedWindow ?: detail?.messages.orEmpty())

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
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    /**
     * Read from the saved state rather than passed in, so the chat being
     * shown survives process death along with everything else here.
     */
    private val chatId: Long = requireNotNull(savedStateHandle[Route.Chat.ARG_CHAT_ID]) {
        "ChatViewModel needs a ${Route.Chat.ARG_CHAT_ID} argument"
    }

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        // Held open for as long as this state holder lives; released in
        // onCleared. See TelegramChats.retainChat for why it is counted.
        repository.retainChat(chatId)
        reload()
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
                .filter { it.chatId == chatId }
                .collect { update -> applyUpdate(update) }
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

    fun onDraftChange(text: String) = _uiState.update { it.copy(draft = text) }

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

    // ── stickers ─────────────────────────────────────────────────────────

    /**
     * The sticker sheet, opening on what was sent lately — or on the first
     * set, for an account that has sent none yet, rather than on an empty
     * tab explaining why it is empty.
     */
    fun onStickerPickerOpen() {
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

    fun onStickerPickerDismiss() = _uiState.update { it.copy(stickerPicker = null) }

    /** Sent at once, answering the message being replied to, if any. */
    fun onStickerPicked(sticker: StickerContent) {
        val answering = _uiState.value.replyTo
        _uiState.update { it.copy(stickerPicker = null, replyTo = null) }
        viewModelScope.launch {
            attempt("Could not send the sticker") {
                repository.sendSticker(chatId, sticker, answering?.id)
            }
        }
    }

    /** A file by TDLib's id, for what draws one without a state holder; see LocalFileLoader. */
    suspend fun loadFile(fileId: Int): String? = repository.downloadFile(fileId)

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
                    repository.sendMessage(chatId, text, attachment, answering?.id)
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

    // ── voice ────────────────────────────────────────────────────────────

    private val voicePlayer = VoicePlayer()
    private var progressJob: Job? = null

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
        if (_uiState.value.playingVoiceId == message.id) {
            voicePlayer.stop()
            progressJob?.cancel()
            _uiState.update { it.copy(playingVoiceId = null, voiceProgress = 0f) }
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
        if (_uiState.value.playingVoiceId != message.id) {
            onVoiceToggled(message)
            return
        }
        voicePlayer.seekTo(fraction)
        _uiState.update { it.copy(voiceProgress = fraction) }
    }

    private fun start(messageId: Long, path: String) {
        val started = voicePlayer.play(messageId, path) {
            // The audio ran out on its own; nothing else would tell the bubble
            // to stop showing a pause button.
            _uiState.update { it.copy(playingVoiceId = null, voiceProgress = 0f) }
        }
        _uiState.update {
            it.copy(
                playingVoiceId = if (started) messageId else null,
                voiceProgress = 0f
            )
        }
        if (started) followProgress()
    }

    /**
     * Reports where playback has got to, while it is playing.
     *
     * A poll rather than a callback, because MediaPlayer offers no position
     * updates — it answers when asked. Ten a second is smooth enough for a
     * bar that is a hundred pixels wide and far cheaper than a frame clock.
     *
     * The previous job is cancelled first: two tickers writing the same field
     * would fight over which message's position it holds.
     *
     * The loop ends on the player, not on this class's own record of what is
     * playing. A player that finished, was released, or never really started
     * leaves that record set — and a loop reading it would tick forever with
     * nothing to report. It did, in a unit test, where MediaPlayer is a stub
     * that starts successfully and plays nothing.
     */
    private fun followProgress() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (voicePlayer.isPlaying()) {
                _uiState.update { it.copy(voiceProgress = voicePlayer.progress()) }
                delay(PROGRESS_TICK_MS)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        repository.releaseChat(chatId)
        // The screen can go away mid-sentence, and a MediaPlayer left holding
        // a file handle outlives it.
        voicePlayer.stop()
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
        // file behind it, and the same guard against asking twice. The video
        // itself is not fetched here — see onVideoOpened.
        message.video?.let { video ->
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
     * Opens a video full-screen, fetching it if it is not here yet.
     *
     * Unlike a photo, this opens before the file has arrived. A video is tens
     * of megabytes and is deliberately not downloaded on sight, so the tap is
     * the first moment anyone asked for it — and a button that sat dead for
     * the length of a download would read as broken. The dialog shows a
     * spinner and starts playing when the bytes land.
     */
    fun onVideoOpened(message: ChatMessage) {
        val video = message.video ?: return
        _uiState.update { it.copy(viewingVideo = message) }
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
    fun onSelectionToggled(message: ChatMessage) =
        _uiState.update { it.copy(selection = it.selection.toggle(message.id)) }

    fun onSelectionCleared() =
        _uiState.update { it.copy(selection = it.selection.cleared()) }

    fun onForwardRequested() =
        _uiState.update { it.copy(forwardSheetOpen = true) }

    fun onForwardDismissed() =
        _uiState.update { it.copy(forwardSheetOpen = false) }

    /**
     * Sends the selection on, then puts both the sheet and the selection away.
     *
     * The conversation is not reloaded: the messages went somewhere else, and
     * nothing about this one changed.
     */
    fun onForwardTo(target: ChatPreview) {
        val ids = _uiState.value.selection.ids.toList()
        if (ids.isEmpty()) return
        _uiState.update {
            it.copy(forwardSheetOpen = false, selection = it.selection.cleared())
        }
        viewModelScope.launch {
            attempt("Could not forward") {
                repository.forwardMessages(chatId, ids, target.id)
            }
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
     * dropping it rather than paging forward through everything after it.
     */
    fun onJumpToLatest() {
        jumpJob?.cancel()
        _uiState.update {
            if (!it.isDetached) it
            else it.copy(
                detachedWindow = null,
                olderMessages = emptyList(),
                hasMoreOlder = true,
                isLoadingNewer = false
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
            _uiState.update {
                val reloaded = it.copy(
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
