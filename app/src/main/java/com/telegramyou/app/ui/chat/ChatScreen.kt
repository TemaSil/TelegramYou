package com.telegramyou.app.ui.chat

import com.telegramyou.app.telegram.model.TelegramUser
import com.telegramyou.app.telegram.model.ContactContent
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import com.telegramyou.app.telegram.model.ReactionOption
import com.telegramyou.app.telegram.model.GifItem
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.runtime.mutableLongStateOf
import android.os.SystemClock
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.WindowInsets
import androidx.activity.compose.BackHandler
import com.telegramyou.app.ui.icons.Symbols
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.graphics.TransformOrigin
import com.telegramyou.app.telegram.model.ButtonAction
import com.telegramyou.app.telegram.model.InlineButton
import com.telegramyou.app.telegram.model.PollDraft
import com.telegramyou.app.telegram.model.StickerContent
import com.telegramyou.app.ui.components.personShape
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.graphicsLayer
import com.telegramyou.app.ui.common.rememberTextCopier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.telegramyou.app.telegram.model.AttachmentDraft
import com.telegramyou.app.telegram.model.ChatMessage
import com.telegramyou.app.music.NowPlaying
import com.telegramyou.app.telegram.model.ChatPreview
import com.telegramyou.app.telegram.model.MessageContentType
import com.telegramyou.app.ui.components.AvatarBubble
import com.telegramyou.app.ui.components.ClusterMember
import com.telegramyou.app.ui.components.AvatarCluster
import com.telegramyou.app.ui.components.TypingIndicator
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


/**
 * How often the recorder is asked for an amplitude, and the clock redrawn.
 *
 * Four a second: fast enough that a two-second message still has eight bars
 * to draw, slow enough that it is not a reading per frame.
 */
internal const val RECORDING_TICK_MS = 250L

/**
 * One conversation.
 *
 * Renders a [ChatUiState] and reports what happened. The picker launchers and
 * the list's scroll position stay here because they are properties of this
 * composition, not of the conversation; everything else — the draft, the
 * reply and edit banners, the pending deletion, the messages themselves —
 * belongs to [ChatViewModel] and survives rotation there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: ChatUiState,
    onBack: () -> Unit,
    /** Opens every photo in this chat as a grid. */
    onOpenMedia: () -> Unit,
    onOpenInfo: () -> Unit,
    onDraftChange: (String) -> Unit,
    onAttachmentPicked: (AttachmentDraft) -> Unit,
    onAttachmentCleared: () -> Unit,
    onReplyTo: (ChatMessage) -> Unit,
    onEdit: (ChatMessage) -> Unit,
    onComposerBannerCancelled: () -> Unit,
    onSend: () -> Unit,
    onLoadOlder: () -> Unit,
    /** A search hit or the pinned message; see ChatViewModel.onJumpToMessage. */
    onJumpToMessage: (Long) -> Unit = {},
    onJumpToLatest: () -> Unit = {},
    onLoadNewer: () -> Unit = {},
    onScrollTargetReached: () -> Unit = {},
    onDeleteRequested: (ChatMessage) -> Unit,
    onDeleteDismissed: () -> Unit,
    onDeleteConfirmed: (ChatMessage, Boolean) -> Unit,
    onReactionsRequested: (ChatMessage) -> Unit,
    onReactionPickerDismissed: () -> Unit,
    onReactionToggled: (ChatMessage, String) -> Unit,
    onSelectionToggled: (ChatMessage) -> Unit,
    /** A message's menu is opening; see ChatViewModel.onMessageActionsNeeded. */
    onMessageMenuOpened: (ChatMessage) -> Unit = {},
    /** Forward one message from its menu. */
    onForwardOne: (ChatMessage) -> Unit = {},
    /** A voice or video message was played; its sender is told. */
    onContentOpened: (ChatMessage) -> Unit = {},
    /** A message put into the phone's language; see TranslationDialog. */
    onTranslate: (ChatMessage, String) -> Unit = { _, _ -> },
    onTranslationDismissed: () -> Unit = {},
    /** Settings → For geeks → More in a message's menu. */
    onMessageExtra: (ChatMessage, com.telegramyou.app.settings.MessageExtra) -> Unit = { _, _ -> },
    onSelectionCleared: () -> Unit,
    onSelectionDeleteRequested: () -> Unit,
    onSelectionDeleteDismissed: () -> Unit,
    onSelectionDeleted: (Boolean) -> Unit,
    onSearchOpenChange: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onAttachmentSheetOpenChange: (Boolean) -> Unit,
    onForwardRequested: () -> Unit,
    onForwardDismissed: () -> Unit,
    onForwardTo: (ChatPreview) -> Unit,
    /** A contact found by the forward sheet's search, with no chat yet. */
    onForwardToContact: (TelegramUser) -> Unit = {},
    onVoiceToggled: (ChatMessage) -> Unit,
    onVoiceSeek: (ChatMessage, Float) -> Unit,
    /** A file in a bubble tapped; see ChatViewModel.onDocumentOpened. */
    onDocumentOpened: (ChatMessage) -> Unit = {},
    /** The app's music player, for the tracks in this chat's bubbles. */
    music: NowPlaying = NowPlaying(),
    onMusicSeek: (Float) -> Unit = {},
    /** The mini player, under the header while something plays. */
    musicBar: @Composable () -> Unit = {},
    /** The mini player, over the composer. */
    playerBar: @Composable () -> Unit = {},
    onFileOpened: () -> Unit = {},
    onFileRefused: (String?) -> Unit = {},
    onPhotoVisible: (ChatMessage) -> Unit,
    onPhotoOpened: (ChatMessage) -> Unit,
    onPhotoClosed: () -> Unit,
    onVideoOpened: (ChatMessage) -> Unit,
    onVideoClosed: () -> Unit,
    /** A photo or video swiped to in the gallery; what it needs is fetched. */
    onGalleryPage: (ChatMessage) -> Unit = {},
    onSaveGif: (ChatMessage) -> Unit = {},
    /** A track into the player's Up next: first (true) or last. */
    onLineUp: (ChatMessage, Boolean) -> Unit = { _, _ -> },
    onContactOpen: (ContactContent) -> Unit = {},
    onContactAdd: (ContactContent) -> Unit = {},
    onContactPickerOpen: () -> Unit = {},
    onContactPickerDismiss: () -> Unit = {},
    onContactPicked: (TelegramUser) -> Unit = {},
    /** Called once a failure in [ChatUiState.errorMessage] has been shown. */
    onErrorShown: () -> Unit,
    /** The emoji, GIF and sticker panel, in the keyboard's place. */
    onExpressionsOpen: () -> Unit = {},
    onExpressionsClose: () -> Unit = {},
    onExpressionTab: (ExpressionTab) -> Unit = {},
    onStickerSetSelected: (Long) -> Unit = {},
    onStickerPicked: (StickerContent) -> Unit = {},
    onCustomEmojiSetSelected: (Long?) -> Unit = {},
    onCustomEmojiPicked: (StickerContent) -> Unit = {},
    /** Where this phone is, found and confirmed in SendLocationDialog. */
    onSendLocation: (latitude: Double, longitude: Double, accuracyMeters: Double) -> Unit = { _, _, _ -> },
    /** A still sticker made into a picture on disk, to send as a photo; null when it could not be. */
    onStickerImage: (String?) -> Unit = {},
    onGifQueryChange: (String) -> Unit = {},
    onGifVisible: (GifItem) -> Unit = {},
    onGifPicked: (GifItem) -> Unit = {},
    onVote: (ChatMessage, Set<Int>) -> Unit = { _, _ -> },
    /** A bot button that talks to the bot or fills the composer. */
    onBotButton: (ChatMessage, InlineButton) -> Unit = { _, _ -> },
    /** A key of the bot's keyboard under the composer. */
    onReplyKey: (String) -> Unit = {},
    onBotAnswerShown: () -> Unit = {},
    onPollOpen: () -> Unit = {},
    onPollChange: (PollDraft) -> Unit = {},
    onPollSend: () -> Unit = {},
    onPollDismiss: () -> Unit = {},
    /** What is typed, scheduled for this moment in epoch seconds. */
    onSchedule: (Long) -> Unit = {},
    onScheduledOpen: () -> Unit = {},
    onScheduledSendNow: (ChatMessage) -> Unit = {},
    onScheduledDelete: (ChatMessage) -> Unit = {},
    onScheduledDismiss: () -> Unit = {},
    onNoticeShown: () -> Unit = {},
    /** An @username tapped in a message; the graph opens that chat. */
    onMention: (String) -> Unit = {},
    onPinToggled: (ChatMessage) -> Unit = {}
) {
    // Settings → For geeks, which changes how a recording leaves.
    val geeks = com.telegramyou.app.settings.LocalGeekSettings.current
    val listState = rememberLazyListState()
    val uriHandler = LocalUriHandler.current

    // The list is laid out from the bottom (reverseLayout, below), so it
    // holds on to its newest message rather than its oldest. That one choice
    // is what opens a chat at its latest line with nothing scrolling, keeps
    // the bubbles rising with the keyboard as imePadding shrinks the list's
    // box, and lets older pages arrive above without moving what is on
    // screen. Item 0 is the newest message; see listIndexOf.

    // When this conversation was opened, in the same seconds as a message's
    // date: what arrives after it pops into place; what was already here
    // does not. And each message pops once — a sent one is announced under a
    // temporary id and again under the server's, which is a new list item
    // with the same words, and would otherwise bounce twice.
    val openedAt = remember { System.currentTimeMillis() / 1000 }
    val popped = remember { mutableSetOf<String>() }

    // Items spring into their new places only once the chat has settled.
    // While it opens, the history arrives in pages and every page moved
    // every bubble above it — on a spring each, the whole conversation
    // wobbling while the screen was still growing out of its row.
    var itemsAnimate by remember { mutableStateOf(false) }
    // Nor while a jump is landing. A jump swaps the whole window, and with
    // item animations on, the messages leaving faded and slid out over the
    // ones arriving — two conversations on top of each other for a third of
    // a second. A jump is a cut, not a move, so it happens at once.
    //
    // And not straight after one, either: the pages either side of the hit
    // arrive once it has landed, and with placement springs back on every
    // bubble flew to its new place at once — date chips and messages drawn
    // across each other, caught that way in the search screenshot. After a
    // jump the list waits to settle exactly as it does after opening.
    val animateItems = itemsAnimate && state.scrollTarget == null
    LaunchedEffect(state.scrollTarget) {
        if (state.scrollTarget != null) {
            itemsAnimate = false
        } else {
            delay(SETTLE_MILLIS)
            itemsAnimate = true
        }
    }

    // A refusal from the server, said once in a snackbar — a failed send
    // puts the text back in the composer as well, so the message is there
    // to try again.
    val snackbarHostState = remember { SnackbarHostState() }
    state.errorMessage?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            onErrorShown()
        }
    }
    // Something done rather than refused — "Scheduled for today at 18:00" —
    // said the same way, once.
    state.notice?.let { notice ->
        LaunchedEffect(notice) {
            snackbarHostState.showSnackbar(notice)
            onNoticeShown()
        }
    }
    // The two pickers for scheduling, one after the other; see SchedulePicker.
    var schedulePicking by remember { mutableStateOf(false) }
    if (schedulePicking) {
        SchedulePicker(
            onPicked = { at ->
                schedulePicking = false
                onSchedule(at)
            },
            onDismiss = { schedulePicking = false }
        )
    }
    state.pollDraft?.let { draft ->
        PollComposer(draft = draft, onChange = onPollChange, onSend = onPollSend, onDismiss = onPollDismiss)
    }
    state.scheduled?.let { waiting ->
        ScheduledSheet(
            messages = waiting,
            onSendNow = onScheduledSendNow,
            onDelete = onScheduledDelete,
            onDismiss = onScheduledDismiss
        )
    }
    // What a bot said back to a pressed button: a snackbar, unless the bot
    // asked for it to be dismissed by hand — then a dialog, which is what
    // Telegram's show_alert means. A link in the answer opens as it arrives.
    state.botAnswer?.let { answer ->
        if (answer.showAlert) {
            AlertDialog(
                onDismissRequest = onBotAnswerShown,
                confirmButton = { TextButton(onClick = onBotAnswerShown) { Text("OK") } },
                text = { Text(answer.text) }
            )
        } else {
            LaunchedEffect(answer) {
                if (answer.url.isNotBlank()) runCatching { uriHandler.openUri(answer.url) }
                if (answer.text.isNotBlank()) snackbarHostState.showSnackbar(answer.text)
                onBotAnswerShown()
            }
        }
    }

    val context = LocalContext.current
    // Declared before the pickers, which launch work on it from their callbacks.
    val scope = rememberCoroutineScope()

    // Owned here rather than inside the composer, because what puts the caret
    // in the field is a reply or an edit starting — and those are the screen's
    // state, not the composer's.
    val composerFocus = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val fileLoader = LocalFileLoader.current
    // Sending where you are: asked for at that moment, and either answer is
    // enough — coarse only makes the circle round the pin wider.
    var locationOpen by rememberSaveable { mutableStateOf(false) }
    var locationRefused by remember { mutableStateOf(false) }
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.any { it }) locationOpen = true else locationRefused = true
    }
    if (locationRefused) {
        LaunchedEffect(Unit) {
            snackbarHostState.showSnackbar("Sending where you are needs location access")
            locationRefused = false
        }
    }
    if (locationOpen) {
        SendLocationDialog(
            onDismiss = { locationOpen = false },
            onSend = { latitude, longitude, accuracy ->
                locationOpen = false
                onSendLocation(latitude, longitude, accuracy)
            }
        )
    }
    // Scrolling the conversation by hand puts the keyboard away, as the
    // forks and most messengers do: reading back needs the room, and the
    // field keeps its text and its caret, so a tap on it brings the keyboard
    // straight back. Only a drag counts — the list moving by itself, for a
    // new message or a jump to a reply, leaves the keyboard where it is.
    //
    // And back, as on iOS: put away by a drag, the keyboard comes back up if
    // the conversation is flicked back down towards the newest message
    // within a couple of seconds — changed one's mind, carried on typing.
    // Only when the drag was what put it away; someone who was only reading
    // is not handed a keyboard for scrolling.
    var hiddenByDragAt by remember { mutableLongStateOf(0L) }
    val keyboardInsets = WindowInsets.ime
    val insetDensity = LocalDensity.current
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) {
                if (keyboardInsets.getBottom(insetDensity) > 0) hiddenByDragAt = SystemClock.uptimeMillis()
                keyboardController?.hide()
            }
        }
    }
    val keyboardBack = remember {
        object : NestedScrollConnection {
            override suspend fun onPreFling(available: Velocity): Velocity {
                // The list is laid out from the bottom: towards the newest
                // message is the finger going up, a negative velocity.
                val recently = SystemClock.uptimeMillis() - hiddenByDragAt < KEYBOARD_BACK_WITHIN_MS
                if (recently && available.y < -KEYBOARD_BACK_FLING) {
                    hiddenByDragAt = 0L
                    keyboardController?.show()
                }
                return Velocity.Zero
            }
        }
    }
    // Held across the launch because TakePicture answers with a boolean, not
    // with the Uri: the destination is chosen here and has to survive until
    // the camera app comes back.
    var cameraTarget by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { taken ->
        val uri = cameraTarget
        cameraTarget = null
        // false means the camera app was cancelled, and the empty file it was
        // given is left for the cache to clear rather than sent as a photo.
        //
        // The FileProvider Uri, not the path behind it: the backend takes what
        // a picker returns and resolves it itself, and handing it two shapes
        // would mean two paths through the same code.
        if (taken && uri != null) {
            onAttachmentPicked(AttachmentDraft.Photos(listOf(uri.toString())))
        }
    }

    // One recorder for the life of the screen: it holds the microphone, and a
    // new one per press would race the previous one's release.
    val recorder = remember(context) { VoiceRecorder(context) }
    var recordingSince by remember { mutableStateOf<Long?>(null) }

    // A photo through the camera app. Since 1.7 the app declares the camera
    // for round video messages, and Android then refuses ACTION_IMAGE_CAPTURE
    // to an app that declares it without holding it — so a photo asks first.
    val takePhoto: () -> Unit = {
        val uri = cameraUri(context, newCameraFile(context))
        cameraTarget = uri
        cameraLauncher.launch(uri)
    }
    val cameraForPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) takePhoto() }
    val onTakePhoto: () -> Unit = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            takePhoto()
        } else {
            cameraForPhoto.launch(Manifest.permission.CAMERA)
        }
    }

    // The camera button held: a round video message (1.7). One recorder for
    // the screen, as for voice. Opening the camera takes a moment, so a
    // finger lifted before it has opened waits for it and then ends the
    // recording, rather than leaving one running nobody is holding.
    val videoNotes = remember(context) { VideoNoteRecorder(context) }
    var videoNoteSince by remember { mutableStateOf<Long?>(null) }
    var videoNoteLocked by remember { mutableStateOf(false) }
    val videoNoteStart = remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val cameraAndMicrophone = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Nothing starts on the grant, for the reason the microphone's gives:
        // the hold that asked is over by the time the dialog is answered.
    }
    DisposableEffect(videoNotes) { onDispose { videoNotes.cancel() } }
    // CameraX started while the chat opens, where the camera is allowed, so
    // the first hold records at once.
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            warmUpCamera(context)
        }
    }
    val cancelVideoNote: () -> Unit = {
        if (videoNoteSince != null) {
            videoNoteSince = null
            videoNoteLocked = false
            scope.launch {
                videoNoteStart.value?.join()
                videoNotes.cancel()
            }
        }
    }
    // Lifted, or the minute is up: what was recorded goes, as a voice
    // message does when the microphone is let go.
    val stopVideoNote: () -> Unit = {
        if (videoNoteSince != null) {
            videoNoteSince = null
            videoNoteLocked = false
            scope.launch {
                videoNoteStart.value?.join()
                videoNotes.stop()?.let { note ->
                    onAttachmentPicked(AttachmentDraft.VideoNote(note.path, note.durationSeconds, note.length))
                    if (!geeks.confirmRecordings) onSend()
                }
            }
        }
    }

    // The bot's keyboard, when this chat has one: whether it is up, and how
    // tall it drew. Held here rather than beside the composer, because the
    // list needs its height — it sits over the conversation like the
    // composer does, and without room made for it the newest message and the
    // buttons under it were hidden behind the keys.
    val botKeyboard = state.replyKeyboard
    var botKeyboardShown by remember(botKeyboard) { mutableStateOf(true) }
    var botPanelHeight by remember { mutableIntStateOf(0) }
    var playerBarHeight by remember { mutableIntStateOf(0) }
    val playerBarPadding = with(LocalDensity.current) { playerBarHeight.toDp() }
    val botPanelVisible = botKeyboard != null && botKeyboardShown && recordingSince == null
    val botPanelPadding = if (botPanelVisible) with(LocalDensity.current) { botPanelHeight.toDp() } else 0.dp

    // The emoji, GIF and sticker panel stands where the keyboard stood, at
    // the keyboard's height — the tallest it has been seen, less the
    // navigation bar it sits over — so switching between the two moves
    // nothing above them. Read through snapshotFlow rather than in
    // composition: the inset changes every frame the keyboard slides, and
    // this whole screen recomposing with it is what that would cost.
    val expressionsOpen = state.expressions != null
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val navInsets = WindowInsets.navigationBars
    var keyboardHeight by rememberSaveable { mutableIntStateOf(0) }
    val latestExpressionsOpen by rememberUpdatedState(expressionsOpen)
    LaunchedEffect(imeInsets, navInsets) {
        snapshotFlow { imeInsets.getBottom(density) - navInsets.getBottom(density) }
            .collect { height -> if (height > keyboardHeight) keyboardHeight = height }
    }
    // The keyboard coming up — the field tapped, a reply started — puts the
    // panel away: one keyboard at a time, as with the bot's.
    LaunchedEffect(imeInsets) {
        snapshotFlow { imeInsets.getBottom(density) > 0 }
            .collect { up -> if (up && latestExpressionsOpen) onExpressionsClose() }
    }
    val panelHeight = if (keyboardHeight > 0) {
        with(density) { keyboardHeight.toDp() }.coerceAtLeast(PANEL_MIN_HEIGHT)
    } else {
        PANEL_DEFAULT_HEIGHT
    }
    // The navigation bar's height with the keyboard down, and only that. On
    // gesture navigation the bar grows while the keyboard is up — it holds
    // the keyboard's own hide and switch buttons — and drops back the moment
    // the keyboard is gone. The composer used to stand on the bar as it was
    // at each frame, so it rode the keyboard down and then fell the
    // difference in one step at the end: the jump the owner filmed.
    // Measured from the resting bar, the composer's height over the bottom
    // is the keyboard's own all the way down, and never steps.
    var restingNav by remember { mutableIntStateOf(navInsets.getBottom(density)) }
    LaunchedEffect(imeInsets, navInsets) {
        snapshotFlow { (imeInsets.getBottom(density) == 0) to navInsets.getBottom(density) }
            .collect { (keyboardDown, nav) -> if (keyboardDown) restingNav = nav }
    }
    val navBarHeight = with(density) { restingNav.toDp() }
    // How far the composer stands off the bottom of the list's Box, which
    // runs under the navigation bar: the bar's height, except with the panel
    // up, which stands under the composer and carries the inset itself. With
    // the keyboard up the Box ends that far inside the keyboard (the screen's
    // Column pads by the keyboard less the bar), so the same lift puts the
    // capsule exactly on the keyboard.
    val composerLift = if (expressionsOpen) 0.dp else navBarHeight
    BackHandler(enabled = expressionsOpen) { onExpressionsClose() }

    // What the open menu offers to react with: the message's own list, with
    // Telegram's animations, once asked; the chat's until then.
    val quickReactions = remember(state.reactionOptions, state.availableReactions) {
        state.reactionOptions.ifEmpty { state.availableReactions.map { ReactionOption(it) } }
    }
    val microphone = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Nothing starts on the grant itself. The press that asked for it is
        // long over by the time the dialog is answered, and starting then
        // would record from a finger that is no longer down.
        if (!granted) Unit
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        onAttachmentPicked(
            AttachmentDraft.Files(
                uris = uris.map { it.toString() },
                names = uris.map { it.lastPathSegment?.substringAfterLast('/') ?: "file" }
            )
        )
    }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        onAttachmentPicked(AttachmentDraft.Photos(uris.map { it.toString() }))
    }

    // Keyed on the newest message, not on the count. Paging older history in
    // also changes the count, and scrolling to the bottom because somebody
    // scrolled up is the opposite of what they asked for.
    //
    // And only when following along: after our own message, or when the list
    // was already at its end. Someone reading back through the history is not
    // pulled away from it by a new line — the jump-to-latest button is there
    // for that. There is no first scroll in: a list laid out from the bottom
    // opens there. It used to be laid out from the top and scrolled down on
    // a spring once the first page arrived, and the older page that loaded
    // meanwhile moved the index it was aiming at — so opening a chat showed
    // its history sliding past and stopping somewhere above the latest line.
    LaunchedEffect(state.messages.lastOrNull()?.id) {
        val newest = state.messages.lastOrNull() ?: return@LaunchedEffect
        // One from the start: the list may or may not have laid out the new
        // item by the time this runs.
        // Not while in a stretch of the past: its newest message changes as
        // newer pages load under the finger, and that is not following along.
        val following = !state.isDetached && listState.firstVisibleItemIndex <= 1
        if (newest.isOutgoing || following) listState.animateScrollToItem(0)
    }

    // A jump: the list goes to the message once it is in the list. Straight
    // there rather than animated — from a stretch of the past there is no
    // path between here and there to animate along.
    LaunchedEffect(state.scrollTarget, state.messages) {
        val target = state.scrollTarget ?: return@LaunchedEffect
        val index = state.messages.indexOfFirst { it.id == target }
        if (index < 0) return@LaunchedEffect
        listState.scrollToItem(listIndexOf(index, state.messages))
        onScrollTargetReached()
    }

    // The near end of a stretch of the past, for the page after it.
    val nearNewest by remember {
        derivedStateOf { listState.firstVisibleItemIndex <= 3 }
    }
    LaunchedEffect(nearNewest, state.isDetached) {
        if (nearNewest && state.isDetached) onLoadNewer()
    }

    // derivedStateOf so this recomputes on scroll without recomposing the
    // screen on every pixel of it.
    val nearTop by remember {
        derivedStateOf {
            // The oldest end is the far end of a list laid out from the bottom.
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            last != null && last.index >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearTop, state.hasMoreOlder) {
        if (nearTop && state.hasMoreOlder) onLoadOlder()
    }

    // Same reasoning as nearTop: this changes on scroll, and without
    // derivedStateOf the whole screen recomposes on every pixel of it.
    val atLatest by remember {
        derivedStateOf {
            // An empty list counts as "at the latest": there is nothing to
            // jump to, and offering the button would be a control that does
            // nothing.
            val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()
            first == null || first.index == 0
        }
    }
    // Away from the latest messages altogether, however the list is
    // scrolled: the bottom of a stretch of the past is not the latest.
    val showJump = !atLatest || state.isDetached

    val detail = state.detail
    val chat = detail?.chat
    val copyToClipboard = rememberTextCopier()
    // A file fetched and ready: handed to whichever app on the phone reads it.
    val fileContext = LocalContext.current
    state.fileToOpen?.let { file ->
        LaunchedEffect(file) {
            if (MediaActions.openFile(fileContext, file.path, file.mime, file.name)) onFileOpened() else onFileRefused(file.name)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Symbols.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    if (state.search.isOpen) {
                        // The field takes the title's place rather than
                        // sliding in beneath it: the chat's name and avatar
                        // are not what is being searched, and keeping them
                        // there would leave two things competing for the row.
                        ChatSearchField(
                            query = state.search.query,
                            onQueryChange = onSearchQueryChange
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            // The header is the way in to the info screen —
                            // the avatar and the name are what a thumb goes
                            // for when it wants to know who is in here, and
                            // every other client answers that tap.
                            modifier = Modifier.clickable(
                                enabled = chat != null,
                                onClick = onOpenInfo
                            )
                        ) {
                            if (chat != null) {
                                // A group shows who is in it, each member in
                                // their own shape; anything with one person
                                // behind it keeps a single avatar — in the
                                // shape it has in the chat list — because a
                                // cluster of one is just an avatar drawn oddly.
                                val members = if (chat.isGroup || chat.isChannel) {
                                    // The server's list when there is one,
                                    // and whoever has spoken when there is
                                    // not: a channel has no members, and a
                                    // group that will not answer should still
                                    // show a header.
                                    detail?.members
                                        ?.takeIf { it.isNotEmpty() }
                                        ?.map { ClusterMember(name = it.displayName, seed = it.id, photoPath = it.photoPath) }
                                        ?: clusterMembers(state.messages)
                                } else {
                                    emptyList()
                                }
                                if (members.size > 1) {
                                    AvatarCluster(members = members, size = 34.dp)
                                } else {
                                    AvatarBubble(
                                        title = chat.title,
                                        seed = chat.avatarColor,
                                        size = 40.dp,
                                        // The shape the chat has in the list,
                                        // morphing while they type here too.
                                        shape = personShape(chat.avatarColor),
                                        typing = detail?.isTyping == true,
                                        photoPath = chat.photoPath,
                                        savedMessages = chat.isSavedMessages
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    // In a forum's topic, the topic is the
                                    // name and the group the line under it.
                                    Text(state.topicName ?: chat.title, fontWeight = FontWeight.Bold, maxLines = 1)
                                    if (state.topicName != null) {
                                        Text(
                                            text = chat.title,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else if (detail?.isTyping == true) {
                                        // Drawn rather than written: "typing…"
                                        // is a word that has to be read, and
                                        // this is a thing that is happening.
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            TypingIndicator()
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                "typing",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    } else {
                                        // No line at all when there is nothing
                                        // to say — Saved Messages — so the name
                                        // sits centred rather than over a gap.
                                        detail?.memberCountLabel?.let { label ->
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                actions = {
                    // Only while something is waiting: a clock in the bar of
                    // a chat with nothing scheduled would be a button that
                    // opens an empty list.
                    if (detail?.chat?.hasScheduledMessages == true) {
                        IconButton(onClick = onScheduledOpen) {
                            Icon(Symbols.Schedule, contentDescription = "Scheduled messages")
                        }
                    }
                    // Beside search rather than behind an overflow: both are
                    // ways of finding something in a long conversation, and a
                    // bar with two actions has room for two.
                    IconButton(onClick = onOpenMedia) {
                        Icon(
                            Symbols.PhotoLibrary,
                            contentDescription = "Photos in this chat"
                        )
                    }
                    IconButton(onClick = { onSearchOpenChange(!state.search.isOpen) }) {
                        Icon(
                            if (state.search.isOpen) Symbols.Close else Symbols.Search,
                            contentDescription = if (state.search.isOpen) {
                                "Close search"
                            } else {
                                "Search in chat"
                            }
                        )
                    }
                },
                // One solid fill, the colour Material gives a top app bar
                // with content scrolled beneath it — which a conversation
                // always has. It was transparent, for a gradient that ran the
                // full height of the screen; once the wallpapers became
                // patterns, the pattern showed through the header and the
                // status bar, and the owner asked for a fill (29 September).
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Background before padding, and the order is the point. A
                // modifier chain paints where it stands: with the inset
                // applied first, the gradient stopped where the navigation
                // bar began and left a band of bare window colour under it —
                // which is exactly what a transparent system bar shows
                // through. Painting first and insetting after puts the
                // conversation under the bar and the content clear of it.
                // The wallpaper chosen in Appearance — the gradient that was
                // always here unless another was picked; see chatWallpaper.
                .chatWallpaper(LocalChatStyle.current.wallpaper)
                // No bottom inset: the conversation runs under the
                // navigation bar to the bottom of the screen, as it runs
                // under the capsule, rather than stopping on a strip of bare
                // wallpaper there (the owner's word, 29 September). What must
                // stay clear of the bar takes the inset itself: the
                // composer's column, the list's last message, search
                // results, and the panel, which stands where the keyboard
                // does.
                .padding(
                    start = padding.calculateStartPadding(LocalLayoutDirection.current),
                    top = padding.calculateTopPadding(),
                    end = padding.calculateEndPadding(LocalLayoutDirection.current)
                )
                // The keyboard less the resting navigation bar, which
                // composerLift adds back — so the capsule's height over the
                // bottom is exactly the keyboard's, frame by frame, however
                // the bar changes under it (see restingNav). The bar is still
                // consumed for what is inside.
                .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets(bottom = restingNav)))
                .consumeWindowInsets(WindowInsets.navigationBars)
        ) {
            musicBar()
            detail?.pinnedMessage?.let { pinned ->
                PinnedMessageBar(
                    message = pinned,
                    // Loaded or not: an old pinned message is fetched with the
                    // history around it, the same way a search hit is.
                    onClick = { onJumpToMessage(pinned.id) }
                )
            }

            // The list and the composer share one Box, and that is what makes
            // the composer float. It used to be the next row of a Column, so
            // the messages stopped above it and the strip it sat on was bare
            // chat background — a band, not something hovering over anything.
            // The list fills the Box now and the composer sits on top, so what
            // shows behind and around the capsule is the conversation.
            Box(modifier = Modifier.weight(1f)) {
            if (state.search.isOpen && state.search.query.isNotBlank()) {
                // Results take the conversation's place rather than covering
                // it. A panel over the messages would put two scrollable lists
                // one on top of the other, and the one underneath is not what
                // the finger is on.
                ChatSearchResults(
                    search = state.search,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = composerLift),
                    // Any hit, however old. One older than what is loaded used
                    // to do nothing at all when tapped.
                    onOpen = { hit ->
                        onSearchOpenChange(false)
                        onJumpToMessage(hit.id)
                    }
                )
            } else {
                // The jump-to-latest button sits over the list rather than
                // in a row of its own.
                //
                // The Box holds the two together — the list and the button
                // over it. It blurred the conversation while a reply was
                // being composed until 19 September, under the narrow
                // exception CLAUDE.md granted for exactly that; the owner
                // looked at it on a phone and withdrew it.
                Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    // To the bottom of the screen: the conversation shows
                    // under the capsule's margin too, rather than a band of
                    // bare background beneath it, on the owner's word.
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(keyboardBack),
                    // Sixteen on three sides, and room for the composer on
                    // the fourth. The list runs underneath it now, so without
                    // this the newest message would sit behind the capsule
                    // permanently — the price of floating it, paid here.
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp,
                        bottom = 92.dp + botPanelPadding + playerBarPadding + composerLift
                    ),
                    // Bottom, as a list laid out from the bottom has by
                    // default: a short conversation sits on the composer.
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.Bottom),
                    reverseLayout = true
                ) {
                    val messages = state.messages
                    val unreadFrom = unreadDividerIndex(
                        messages,
                        detail?.chat?.unreadCount ?: 0
                    )
                    // Newest first, because item 0 is the bottom of the
                    // list; `index` stays the message's place in time.
                    // Photos sent together draw once, as a grid, where the
                    // album's first photo is; the others draw nothing.
                    // Not remembered: this is the list's builder, not a
                    // composition, and the pass over the window is cheap.
                    val albums = messages
                        .filter { it.albumId != null && it.contentType == MessageContentType.Photo }
                        .groupBy { it.albumId }
                        .filterValues { it.size > 1 }
                    itemsIndexed(messages.asReversed(), key = { _, m -> m.id }) { fromNewest, message ->
                        val album = message.albumId?.let { albums[it] }
                        if (album != null && album.first().id != message.id) return@itemsIndexed
                        val index = messages.lastIndex - fromNewest
                        val previous = messages.getOrNull(index - 1)
                        val next = messages.getOrNull(index + 1)

                        // A message arriving fades in, and the ones already on
                        // screen spring out of its way rather than jumping.
                        //
                        // `animateItem` and not an AnimatedVisibility around
                        // the bubble: a list item that animates itself into
                        // place is what the modifier is for, and the earlier
                        // attempt at this — AnimatedVisibility with
                        // visible = true — could never transition at all,
                        // because nothing ever changed. Wrapping every row in
                        // a composition layer that did nothing is a cost this
                        // project has paid once already, on the chat list.
                        //
                        // The specs come from the theme rather than from
                        // animateItem's defaults, which is the whole point of
                        // setting MotionScheme.expressive() on it: spatial
                        // springs for things that move, effects springs for
                        // things that only change alpha. Material's own
                        // components read the same two.
                        //
                        // The Column is the item's single root, which the
                        // modifier needs; the separators above the bubble
                        // belong to the same item and have to move with it.
                        // A message that arrives while the chat is open grows
                        // out of the corner it belongs to — ours from the
                        // composer's side, theirs from the other — on the
                        // spatial spring, bounce and all. It used to only
                        // fade, which read as the message having always been
                        // there and the screen being slow to show it.
                        val signature = "${message.date}:${message.isOutgoing}:${message.text}"
                        val appear = remember(message.id) {
                            val fresh = message.date >= openedAt && popped.add(signature)
                            Animatable(if (fresh) 0f else 1f)
                        }
                        val pop = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
                        LaunchedEffect(appear) { if (appear.value < 1f) appear.animateTo(1f, pop) }
                        // The message a jump landed on, lit and let fade —
                        // on a list of look-alike bubbles, where the eye
                        // would otherwise have to hunt for it.
                        val flash = remember(message.id) { Animatable(0f) }
                        val lit = state.highlightedId == message.id
                        val flashIn = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
                        val flashOut = MaterialTheme.motionScheme.slowEffectsSpec<Float>()
                        LaunchedEffect(lit) {
                            if (lit) flash.animateTo(1f, flashIn) else flash.animateTo(0f, flashOut)
                        }
                        val flashColor = MaterialTheme.colorScheme.primary
                        val outgoing = message.isOutgoing
                        Column(
                            modifier = Modifier
                                .animateItem(
                                    fadeInSpec = if (animateItems) MaterialTheme.motionScheme.defaultEffectsSpec() else null,
                                    placementSpec = if (animateItems) MaterialTheme.motionScheme.defaultSpatialSpec() else null,
                                    fadeOutSpec = if (animateItems) MaterialTheme.motionScheme.fastEffectsSpec() else null
                                )
                                .graphicsLayer {
                                    val progress = appear.value
                                    alpha = progress.coerceIn(0f, 1f)
                                    scaleX = POP_FROM + (1f - POP_FROM) * progress
                                    scaleY = scaleX
                                    translationY = (1f - progress) * POP_RISE.toPx()
                                    transformOrigin = TransformOrigin(if (outgoing) 1f else 0f, 1f)
                                }
                                .drawBehind {
                                    val strength = flash.value
                                    if (strength > 0f) {
                                        drawRoundRect(
                                            color = flashColor.copy(alpha = HIGHLIGHT_ALPHA * strength),
                                            cornerRadius = CornerRadius(HIGHLIGHT_CORNER.toPx())
                                        )
                                    }
                                }
                        ) {
                        if (startsNewDay(previous, message)) {
                            DaySeparator(message.date)
                        }
                        if (index == unreadFrom) {
                            UnreadSeparator()
                        }

                        // In the message size and, for the account's own,
                        // the tone chosen in Appearance; see StyledMessage.
                        StyledMessage(outgoing = message.isOutgoing) {
                            MessageBubble(
                                message = message,
                                // Only the last message of a run carries the tail, so a
                                // burst from one person reads as one block.
                                isLastInRun = endsRun(message, next),
                                isFirstInRun = endsRun(previous, message),
                                // An avatar per message would be a column of repeats;
                                // one against the last of a run is what reads right.
                                showAvatar = detail?.chat?.isGroup == true,
                                onCopy = {
                                    copyToClipboard(message.text)
                                },
                                onReply = { onReplyTo(message) },
                                onEdit = { onEdit(message) },
                                onDelete = { onDeleteRequested(message) },
                                onReact = { onReactionsRequested(message) },
                                onReactionToggled = { emoji -> onReactionToggled(message, emoji) },
                                quickReactions = quickReactions,
                                onSaveGif = { onSaveGif(message) },
                                onPlayNext = {
                                    onLineUp(message, true)
                                    scope.launch { snackbarHostState.showSnackbar("Plays next") }
                                },
                                onAddToQueue = {
                                    onLineUp(message, false)
                                    scope.launch { snackbarHostState.showSnackbar("Added to the queue") }
                                },
                                onContactOpen = onContactOpen,
                                onContactAdd = onContactAdd,
                                isSelected = message.id in state.selection,
                                isSelecting = state.selection.isActive,
                                onSelect = { onSelectionToggled(message) },
                                onMenuOpened = { onMessageMenuOpened(message) },
                                onForward = { onForwardOne(message) },
                                onContentOpened = { onContentOpened(message) },
                                onExtra = { extra -> onMessageExtra(message, extra) },
                                onTranslate = { onTranslate(message, translationLanguage()) },
                                // A track is the music player's, which outlives
                                // this screen; a voice note is the chat's own.
                                voiceState = when {
                                    message.audio != null && music.track?.messageId == message.id -> when {
                                        music.isLoading -> VoiceState.Loading
                                        music.isPlaying -> VoiceState.Playing
                                        else -> VoiceState.Idle
                                    }
                                    message.id == state.playingVoiceId -> VoiceState.Playing
                                    message.id == state.loadingVoiceId -> VoiceState.Loading
                                    else -> VoiceState.Idle
                                },
                                onVoiceToggled = { onVoiceToggled(message) },
                                voiceProgress = when {
                                    message.audio != null && music.track?.messageId == message.id -> music.progress
                                    message.id == state.playingVoiceId -> state.voiceProgress
                                    else -> 0f
                                },
                                onVoiceSeek = { at ->
                                    if (message.audio != null) onMusicSeek(at) else onVoiceSeek(message, at)
                                },
                                onDocumentOpen = { onDocumentOpened(message) },
                                documentOpening = state.openingFileId == message.id,
                                onPhotoVisible = { onPhotoVisible(message) },
                                onPhotoOpened = { onPhotoOpened(message) },
                                onVideoOpened = { onVideoOpened(message) },
                                album = album,
                                onAlbumPhotoVisible = onPhotoVisible,
                                onAlbumPhotoOpened = onPhotoOpened,
                                onMention = onMention,
                                onHashtag = { tag ->
                                    onSearchOpenChange(true)
                                    onSearchQueryChange(tag)
                                },
                                onPinToggled = { onPinToggled(message) },
                                transfers = state.transfers,
                                onVote = { chosen -> onVote(message, chosen) },
                                onButton = { button ->
                                    when (val action = button.action) {
                                        // Done here, not in the view model: both
                                        // are the platform's, and neither talks
                                        // to Telegram.
                                        is ButtonAction.OpenUrl -> runCatching { uriHandler.openUri(action.url) }
                                        is ButtonAction.CopyText -> {
                                            copyToClipboard(action.text)
                                            scope.launch { snackbarHostState.showSnackbar("Copied") }
                                        }
                                        else -> onBotButton(message, button)
                                    }
                                }
                            )
                        }
                        }
                    }

                    // A spinner where the older messages will appear — the
                    // top, the far end of this list — so the wait has a place
                    // on screen instead of nothing happening.
                    if (state.isLoadingOlder) {
                        item(key = "loading-older") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }

                }
            }

            // Replying is a request to type, so the field takes the caret and
            // the keyboard comes up with the banner. Without this the banner
            // appeared over a composer nobody had touched, and answering meant
            // a tap the app had already been told about — the swipe that
            // started the reply.
            //
            // Keyed on both, because editing is the same intent by another
            // route, and on their ids rather than on the objects so that a
            // list refresh delivering an equal-but-new message does not steal
            // the focus back mid-sentence.
            LaunchedEffect(state.replyTo?.id, state.editing?.id) {
                if (state.replyTo != null || state.editing != null) {
                    composerFocus.requestFocus()
                    keyboardController?.show()
                }
            }


            // The composer and its banners, over the list rather than
            // under it. Bottom-centred in the shared Box; the Column keeps
            // the reply banner and the attachment chip stacked above the
            // capsule and moving with it.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = composerLift)
            ) {
                // Back to the newest message, once the conversation has been
                // scrolled away from it — with how many are unread on it,
                // as Telegram does. Here, at the top of the composer's own
                // column, so it always sits just above whatever the composer
                // has grown to. It used to be anchored to the bottom corner
                // of the list, which the floating composer now covers, and
                // it was there and could not be seen.
                AnimatedVisibility(
                    visible = showJump,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(end = 16.dp, bottom = 4.dp),
                    enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                        scaleIn(MaterialTheme.motionScheme.fastSpatialSpec()),
                    exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                        scaleOut(MaterialTheme.motionScheme.fastEffectsSpec())
                ) {
                    val unread = detail?.chat?.unreadCount ?: 0
                    BadgedBox(
                        badge = {
                            // Primary, as the chat list counts: unread is not
                            // an error, which is the badge's default colour.
                            if (unread > 0) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ) { Text(if (unread > 99) "99+" else unread.toString()) }
                            }
                        }
                    ) {
                        SmallFloatingActionButton(
                            onClick = {
                                if (state.isDetached) {
                                    // The latest messages replace the stretch
                                    // on screen, and the list starts at their
                                    // end; there is nothing between to glide
                                    // through.
                                    onJumpToLatest()
                                } else {
                                    scope.launch { listState.animateScrollToItem(0) }
                                }
                            },
                            // Flat, at the owner's request: it floats over the
                            // conversation, not over a page, and a shadow on
                            // the wallpaper read as a smudge under it.
                            elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
                        ) {
                            Icon(Symbols.KeyboardArrowDown, contentDescription = "Jump to latest")
                        }
                    }
                }

                // The mini player, over the composer: where the thumb is,
                // as it sits over the navigation bar on Home (1.6.7). Measured,
                // so the newest message is not left behind it.
                Box(Modifier.onSizeChanged { playerBarHeight = it.height }) { playerBar() }

                // What the banner and the chip last showed, kept while they
                // fold away: the state is cleared the moment a reply is sent
                // or dismissed, and an exit animating an empty banner had no
                // height to shrink from — it went at once, and the mini
                // player above dropped in one jump (fixed in 1.6.10).
                val bannerNow = state.editing ?: state.replyTo
                var bannerShown by remember { mutableStateOf(bannerNow) }
                var bannerEditing by remember { mutableStateOf(state.editing != null) }
                if (bannerNow != null) {
                    bannerShown = bannerNow
                    bannerEditing = state.editing != null
                }
                var attachmentShown by remember { mutableStateOf(state.pendingAttachment) }
                state.pendingAttachment?.let { attachmentShown = it }

                AnimatedVisibility(
                    visible = state.replyTo != null || state.editing != null,
                    // Folding as well as fading, both ways: with only a fade
                    // the banner's height went at once when a reply was sent
                    // or dismissed, and the mini player and the capsule
                    // under it dropped in one jump.
                    enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                        expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec(), expandFrom = Alignment.Bottom),
                    exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                        shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec(), shrinkTowards = Alignment.Bottom)
                ) {
                    // One banner for both: they are alternatives, never both at
                    // once, and each cancels the other when chosen.
                    bannerShown?.let { message ->
                        ComposerBanner(
                            message = message,
                            isEditing = bannerEditing,
                            onCancel = onComposerBannerCancelled
                        )
                    }
                }

                AnimatedVisibility(
                    visible = state.pendingAttachment != null,
                    // As the reply banner above: folding, so nothing under it jumps.
                    enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                        expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec(), expandFrom = Alignment.Bottom),
                    exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                        shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec(), shrinkTowards = Alignment.Bottom)
                ) {
                    AttachmentChip(
                        draft = attachmentShown,
                        onClear = onAttachmentCleared
                    )
                }

                if (state.selection.isActive) {
                    // The toolbar takes the composer's place rather than floating
                    // over it. Nothing can be typed while a selection is up, so
                    // leaving the field there would be a control that does nothing.
                    if (state.forwardSheetOpen) {
                        ForwardSheet(
                            targets = state.forwardTargets,
                            count = state.selection.count,
                            onDismiss = onForwardDismissed,
                            onPick = onForwardTo,
                            contacts = state.forwardContacts,
                            onPickContact = onForwardToContact
                        )
                    }

                    SelectionToolbar(
                        count = state.selection.count,
                        actions = state.availableActions,
                        onCopy = {
                            copyToClipboard(copyText(state.selectedMessages))
                            onSelectionCleared()
                        },
                        onForward = onForwardRequested,
                        onDelete = onSelectionDeleteRequested,
                        onClear = onSelectionCleared
                    )
                } else {
                    if (state.attachmentSheetOpen) {
                        AttachmentSheet(
                            onDismiss = { onAttachmentSheetOpenChange(false) },
                            onPickPhoto = { photoPicker.launch("image/*") },
                            onPickFile = { filePicker.launch(arrayOf("*/*")) },
                            onTakePhoto = onTakePhoto,
                            onPickRecent = { uri ->
                                onAttachmentPicked(AttachmentDraft.Photos(listOf(uri)))
                                onAttachmentSheetOpenChange(false)
                            },
                            onPoll = if (state.canSendPolls) onPollOpen else null,
                            onContact = onContactPickerOpen,
                            onLocation = {
                                onAttachmentSheetOpenChange(false)
                                if (hasLocationPermission(context)) {
                                    locationOpen = true
                                } else {
                                    locationPermission.launch(LOCATION_PERMISSIONS)
                                }
                            }
                        )
                    }
                    state.contactPicker?.let { contacts ->
                        ContactPickerSheet(
                            contacts = contacts,
                            onPick = onContactPicked,
                            onDismiss = onContactPickerDismiss
                        )
                    }

                    if (botKeyboard != null && botPanelVisible) {
                        ReplyKeyboardPanel(
                            modifier = Modifier.onSizeChanged { botPanelHeight = it.height },
                            keyboard = botKeyboard,
                            onKey = { key ->
                                onReplyKey(key)
                                if (botKeyboard.oneTime) botKeyboardShown = false
                            }
                        )
                    }
                    ComposerBar(
                        placeholder = botKeyboard?.placeholder?.takeIf { it.isNotBlank() } ?: "Message",
                        botKeyboardShown = botKeyboard?.let { botKeyboardShown },
                        onBotKeyboardToggle = {
                            botKeyboardShown = !botKeyboardShown
                            // One keyboard at a time: the bot's comes up as
                            // the system one goes down, and the other way.
                            if (botKeyboardShown) keyboardController?.hide()
                        },
                        value = state.draft,
                        onValueChange = onDraftChange,
                        onAttach = { onAttachmentSheetOpenChange(true) },
                        expressionsOpen = expressionsOpen,
                        onExpressions = {
                            keyboardController?.hide()
                            botKeyboardShown = false
                            onExpressionsOpen()
                        },
                        onKeyboard = {
                            onExpressionsClose()
                            composerFocus.requestFocus()
                            keyboardController?.show()
                        },
                        // The same launch the sheet's camera entry makes,
                        // shared now that it has a permission to ask first.
                        onCamera = onTakePhoto,
                        onVideoNoteStart = {
                            val missing = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                                .filter {
                                    ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                                }
                            if (missing.isNotEmpty()) {
                                cameraAndMicrophone.launch(missing.toTypedArray())
                            } else if (videoNoteSince == null) {
                                videoNoteSince = System.currentTimeMillis()
                                videoNoteStart.value = scope.launch {
                                    if (!videoNotes.start(lifecycleOwner)) {
                                        videoNoteSince = null
                                        snackbarHostState.showSnackbar("The camera could not be opened")
                                    }
                                }
                            }
                        },
                        onVideoNoteStop = stopVideoNote,
                        onVideoNoteCancel = cancelVideoNote,
                        onVideoNoteLock = { if (videoNoteSince != null) videoNoteLocked = true },
                        onSend = onSend,
                        // Held, send offers to schedule — only for text: an
                        // attachment or an edit goes now or not at all.
                        onSchedule = if (state.pendingAttachment == null && state.editing == null) {
                            { schedulePicking = true }
                        } else null,
                        recordingSince = recordingSince,
                        onRecordStart = {
                            if (ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                microphone.launch(Manifest.permission.RECORD_AUDIO)
                            } else if (recorder.start()) {
                                recordingSince = System.currentTimeMillis()
                            }
                        },
                        onRecordStop = {
                            recordingSince = null
                            recorder.stop()?.let { recording ->
                                onAttachmentPicked(
                                    AttachmentDraft.Voice(
                                        path = recording.path,
                                        durationSeconds = recording.durationSeconds,
                                        waveform = recording.waveform
                                    )
                                )
                                // For geeks → Ask before sending a recording:
                                // left in the composer, to be sent or taken
                                // back, rather than gone with the finger.
                                if (!geeks.confirmRecordings) onSend()
                            }
                        },
                        onRecordCancel = {
                            recordingSince = null
                            recorder.cancel()
                        },
                        onSampleAmplitude = recorder::sample,
                        hasAttachment = state.pendingAttachment != null,
                        focusRequester = composerFocus,
                        attachedAbove = state.replyTo != null || state.editing != null
                    )
                }
            }
            }

            // Under the composer, in the keyboard's place; see keyboardHeight.
            state.expressions?.let { tab ->
                ExpressionPanel(
                    tab = tab,
                    height = panelHeight + navBarHeight,
                    // Up to three quarters of the screen, pulled by its
                    // handle; the header and the composer stay in view.
                    expandedHeight = LocalConfiguration.current.screenHeightDp.dp * PANEL_EXPANDED_SHARE + navBarHeight,
                    stickers = state.stickerPicker,
                    gifs = state.gifPicker,
                    onTab = onExpressionTab,
                    onEmoji = { emoji -> onDraftChange(state.draft + emoji) },
                    onBackspace = { onDraftChange(dropLastGrapheme(state.draft)) },
                    onStickerSetSelected = onStickerSetSelected,
                    onStickerPicked = onStickerPicked,
                    onStickerAsImage = { sticker ->
                        scope.launch { onStickerImage(stickerAsPicture(context, sticker, fileLoader)) }
                    },
                    onGifQueryChange = onGifQueryChange,
                    onGifVisible = onGifVisible,
                    onGifPicked = onGifPicked,
                    customEmoji = state.customEmoji,
                    onCustomEmojiSetSelected = onCustomEmojiSetSelected,
                    onCustomEmojiPicked = onCustomEmojiPicked,
                    bottomInset = navBarHeight
                )
            }

            state.translation?.let { translation ->
                TranslationDialog(
                    translation = translation,
                    onCopy = { text ->
                        copyToClipboard(text)
                        onTranslationDismissed()
                    },
                    onDismiss = onTranslationDismissed
                )
            }

            state.pendingDelete?.let { target ->
                DeleteMessageDialog(
                    message = target,
                    onDismiss = onDeleteDismissed,
                    onDelete = { forEveryone -> onDeleteConfirmed(target, forEveryone) }
                )
            }

            // One gallery for both: a photo or a video opens among the
            // chat's others, a swipe apart. See MediaGallery.
            (state.viewingPhoto ?: state.viewingVideo)?.let { opened ->
                val gallery = remember(state.messages, opened) { galleryOf(state.messages, opened) }
                MediaGallery(
                    items = gallery,
                    startId = opened.id,
                    transfers = state.transfers,
                    onPage = onGalleryPage,
                    onDismiss = {
                        onPhotoClosed()
                        onVideoClosed()
                    }
                )
            }

            state.reactingTo?.let { target ->
                ReactionPicker(
                    available = quickReactions,
                    chosen = target.reactions.firstOrNull { it.isChosen }?.emoji,
                    onDismiss = onReactionPickerDismissed,
                    onPick = { emoji -> onReactionToggled(target, emoji) }
                )
            }

            if (state.confirmingSelectionDelete) {
                DeleteSelectionDialog(
                    count = state.selection.count,
                    actions = state.availableActions,
                    onDismiss = onSelectionDeleteDismissed,
                    onDelete = onSelectionDeleted
                )
            }

        }
    }

    // Over everything, the bars included, while a video message records:
    // laid after the Scaffold, so it is drawn on top of it.
    VideoNoteCapture(
        recorder = videoNotes,
        since = videoNoteSince,
        onLimit = stopVideoNote,
        locked = videoNoteLocked,
        onSend = stopVideoNote,
        onDelete = cancelVideoNote
    )
    // Back, while a locked recording is up, throws it away rather than
    // leaving the chat with the camera still running.
    BackHandler(enabled = videoNoteLocked) { cancelVideoNote() }
}

@Composable
internal fun DaySeparator(date: Long) {
    val label = remember(date) { dayLabel(date) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

/**
 * What the chat has pinned, under the app bar.
 *
 * A `Surface` rather than a second `TopAppBar`: it is part of the
 * conversation, not a second place to navigate from, and the accent bar down
 * its left edge is the same device the quoted block inside a bubble uses for
 * the same idea — this text belongs to another message.
 *
 * One line, ellipsised. A pinned message can be as long as any other, and a
 * bar that grows to fit it would push the conversation off the screen to show
 * something the tap already leads to.
 */
@Composable
internal fun PinnedMessageBar(
    message: ChatMessage,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    "Pinned message",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    message.text.ifBlank { "Attachment" },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * The line between what has been read and what has not.
 *
 * The same shape as the day separator, in the primary colour rather than the
 * neutral one: both divide the conversation, but only this one is about the
 * reader. Where it goes is decided in :core — see `unreadDividerIndex`.
 */
@Composable
internal fun UnreadSeparator() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                text = "Unread messages",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

/**
 * The people to draw in a group's header.
 *
 * The fallback, for when there is no member list to have: a channel has
 * subscribers rather than members and does not answer, and a group that
 * refuses the call should still show a header.
 *
 * It was the only source until `ChatDetail.members` existed, and the
 * difference is worth stating: this shows who is *talking*, not who is
 * *present*, so anybody quiet is missing from it. The real list is preferred
 * wherever the server gives one.
 *
 * The shape each person gets is keyed on their id either way, so nobody's
 * shape changes when the list does.
 *
 * Own messages are left out. The cluster answers "who else is here", and a
 * person already knows they are.
 */
internal fun clusterMembers(messages: List<ChatMessage>): List<ClusterMember> =
    messages
        .asReversed()
        .asSequence()
        .filterNot { it.isOutgoing }
        .mapNotNull { message ->
            val name = message.senderName?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            ClusterMember(
                name = name,
                seed = message.senderId ?: name.hashCode().toLong(),
                photoPath = message.senderPhotoPath
            )
        }
        .distinctBy { it.seed }
        .toList()

/** How small a new message starts before it springs to size. */
internal const val POP_FROM = 0.72f

/** How far below its place a new message starts. */
internal val POP_RISE = 28.dp

/** How long after opening before the conversation's items animate their moves. */
internal const val SETTLE_MILLIS = 900L

/**
 * Where the message at [index] in time sits in the conversation's list,
 * which is laid out from the bottom with the newest message as item 0.
 */
internal fun listIndexOf(index: Int, messages: List<ChatMessage>): Int = messages.lastIndex - index

/** How strongly a jumped-to message is lit: a state layer's weight, not a fill. */
internal const val HIGHLIGHT_ALPHA = 0.16f

internal val HIGHLIGHT_CORNER = 20.dp

/** The panel's height before the keyboard has been seen: a keyboard's, near enough. */
/** How much of the screen the expression panel may be pulled up to. */
private const val PANEL_EXPANDED_SHARE = 0.72f
private val PANEL_DEFAULT_HEIGHT = 300.dp

/** No shorter than this, whatever a floating or split keyboard measured. */
private val PANEL_MIN_HEIGHT = 240.dp

/** How soon after a drag put the keyboard away a flick back brings it up. */
private const val KEYBOARD_BACK_WITHIN_MS = 2_500L
/** How fast that flick back has to be, in px a second: a deliberate one. */
private const val KEYBOARD_BACK_FLING = 2_500f
