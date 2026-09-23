package com.telegramyou.app.ui.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Forward
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddReaction
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.telegramyou.app.telegram.model.AttachmentDraft
import com.telegramyou.app.telegram.model.ChatMessage
import com.telegramyou.app.telegram.model.ChatPreview
import com.telegramyou.app.telegram.model.MessageContentType
import com.telegramyou.app.telegram.model.LinkPreview
import com.telegramyou.app.telegram.model.MessageReaction
import com.telegramyou.app.telegram.model.VideoContent
import com.telegramyou.app.telegram.model.waveformBars
import com.telegramyou.app.ui.components.AvatarBubble
import com.telegramyou.app.ui.components.ClusterMember
import com.telegramyou.app.ui.media.Zoom
import com.telegramyou.app.ui.media.dismissProgress
import com.telegramyou.app.ui.media.shouldDismiss
import com.telegramyou.app.ui.media.zoomAfterGesture
import com.telegramyou.app.ui.media.zoomToggled
import com.telegramyou.app.ui.components.AvatarCluster
import com.telegramyou.app.ui.components.TypingIndicator
import com.telegramyou.app.ui.theme.ComposerShape
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * How often the recorder is asked for an amplitude, and the clock redrawn.
 *
 * Four a second: fast enough that a two-second message still has eight bars
 * to draw, slow enough that it is not a reading per frame.
 */
private const val RECORDING_TICK_MS = 250L

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
    onDeleteRequested: (ChatMessage) -> Unit,
    onDeleteDismissed: () -> Unit,
    onDeleteConfirmed: (ChatMessage, Boolean) -> Unit,
    onReactionsRequested: (ChatMessage) -> Unit,
    onReactionPickerDismissed: () -> Unit,
    onReactionToggled: (ChatMessage, String) -> Unit,
    onSelectionToggled: (ChatMessage) -> Unit,
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
    onVoiceToggled: (ChatMessage) -> Unit,
    onVoiceSeek: (ChatMessage, Float) -> Unit,
    onPhotoVisible: (ChatMessage) -> Unit,
    onPhotoOpened: (ChatMessage) -> Unit,
    onPhotoClosed: () -> Unit
) {
    val listState = rememberLazyListState()

    val context = LocalContext.current
    // Declared before the pickers, which launch work on it from their callbacks.
    val scope = rememberCoroutineScope()

    // Owned here rather than inside the composer, because what puts the caret
    // in the field is a reply or an edit starting — and those are the screen's
    // state, not the composer's.
    val composerFocus = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
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
    LaunchedEffect(state.messages.lastOrNull()?.id) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size - 1)
    }

    // derivedStateOf so this recomputes on scroll without recomposing the
    // screen on every pixel of it.
    val nearTop by remember {
        derivedStateOf {
            val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()
            first != null && first.index <= 3
        }
    }
    LaunchedEffect(nearTop, state.hasMoreOlder) {
        if (nearTop && state.hasMoreOlder) onLoadOlder()
    }

    // Same reasoning as nearTop: this changes on scroll, and without
    // derivedStateOf the whole screen recomposes on every pixel of it.
    val atLatest by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            // An empty list counts as "at the latest": there is nothing to
            // jump to, and offering the button would be a control that does
            // nothing.
            last == null || last.index >= info.totalItemsCount - 1
        }
    }

    val detail = state.detail
    val chat = detail?.chat
    val clipboard = LocalClipboardManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
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
                                // behind it keeps the single circle, because a
                                // cluster of one is just an avatar drawn oddly.
                                val members = if (chat.isGroup || chat.isChannel) {
                                    // The server's list when there is one,
                                    // and whoever has spoken when there is
                                    // not: a channel has no members, and a
                                    // group that will not answer should still
                                    // show a header.
                                    detail?.members
                                        ?.takeIf { it.isNotEmpty() }
                                        ?.map { ClusterMember(name = it.displayName, seed = it.id) }
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
                                        size = 40.dp
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(chat.title, fontWeight = FontWeight.Bold, maxLines = 1)
                                    if (detail?.isTyping == true) {
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
                                        Text(
                                            text = detail?.memberCountLabel ?: "",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                actions = {
                    // Beside search rather than behind an overflow: both are
                    // ways of finding something in a long conversation, and a
                    // bar with two actions has room for two.
                    IconButton(onClick = onOpenMedia) {
                        Icon(
                            Icons.Rounded.PhotoLibrary,
                            contentDescription = "Photos in this chat"
                        )
                    }
                    IconButton(onClick = { onSearchOpenChange(!state.search.isOpen) }) {
                        Icon(
                            if (state.search.isOpen) Icons.Rounded.Close else Icons.Rounded.Search,
                            contentDescription = if (state.search.isOpen) {
                                "Close search"
                            } else {
                                "Search in chat"
                            }
                        )
                    }
                },
                // Transparent, so the conversation's own gradient runs the
                // full height of the screen instead of starting below a grey
                // band. The bar's contents still read: they sit over the top
                // of that gradient, which is the lightest part of it.
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
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
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceContainerLow,
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        )
                    )
                )
                .padding(padding)
                .imePadding()
        ) {
            detail?.pinnedMessage?.let { pinned ->
                PinnedMessageBar(
                    message = pinned,
                    onClick = {
                        val index = state.messages.indexOfFirst { it.id == pinned.id }
                        if (index >= 0) {
                            scope.launch {
                                listState.animateScrollToItem(
                                    index + if (state.isLoadingOlder) 1 else 0
                                )
                            }
                        }
                    }
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
                    modifier = Modifier.fillMaxSize(),
                    onOpen = { hit ->
                        val index = state.messages.indexOfFirst { it.id == hit.id }
                        onSearchOpenChange(false)
                        if (index >= 0) {
                            scope.launch {
                                // The spinner, when it is up, is item zero.
                                listState.animateScrollToItem(
                                    index + if (state.isLoadingOlder) 1 else 0
                                )
                            }
                        }
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
                    modifier = Modifier.fillMaxSize(),
                    // Sixteen on three sides, and room for the composer on
                    // the fourth. The list runs underneath it now, so without
                    // this the newest message would sit behind the capsule
                    // permanently — the price of floating it, paid here.
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp,
                        bottom = 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // A spinner where the older messages will appear, so the wait
                    // has a place on screen instead of nothing happening.
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

                    val messages = state.messages
                    val unreadFrom = unreadDividerIndex(
                        messages,
                        detail?.chat?.unreadCount ?: 0
                    )
                    itemsIndexed(messages, key = { _, m -> m.id }) { index, message ->
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
                        Column(
                            modifier = Modifier.animateItem(
                                fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                                placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                                fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec()
                            )
                        ) {
                        if (startsNewDay(previous, message)) {
                            DaySeparator(message.date)
                        }
                        if (index == unreadFrom) {
                            UnreadSeparator()
                        }

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
                                clipboard.setText(AnnotatedString(message.text))
                            },
                            onReply = { onReplyTo(message) },
                            onEdit = { onEdit(message) },
                            onDelete = { onDeleteRequested(message) },
                            onReact = { onReactionsRequested(message) },
                            onReactionToggled = { emoji -> onReactionToggled(message, emoji) },
                            isSelected = message.id in state.selection,
                            isSelecting = state.selection.isActive,
                            onSelect = { onSelectionToggled(message) },
                            voiceState = when (message.id) {
                                state.playingVoiceId -> VoiceState.Playing
                                state.loadingVoiceId -> VoiceState.Loading
                                else -> VoiceState.Idle
                            },
                            onVoiceToggled = { onVoiceToggled(message) },
                            voiceProgress = if (message.id == state.playingVoiceId) {
                                state.voiceProgress
                            } else {
                                0f
                            },
                            onVoiceSeek = { at -> onVoiceSeek(message, at) },
                            onPhotoVisible = { onPhotoVisible(message) },
                            onPhotoOpened = { onPhotoOpened(message) }
                        )
                        }
                    }
                }

                    // Shown only once the conversation has been scrolled away
                    // from: a button that is always there is one more thing
                    // over the messages for no reason. The Column is not
                    // decoration either: AnimatedVisibility's overloads are
                    // scope extensions, and inside a Box there is no receiver
                    // for any of them to resolve against.
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                    ) {
                        AnimatedVisibility(
                            visible = !atLatest,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            SmallFloatingActionButton(
                                onClick = {
                                    scope.launch {
                                        listState.animateScrollToItem(
                                            (listState.layoutInfo.totalItemsCount - 1)
                                                .coerceAtLeast(0)
                                        )
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = "Jump to latest"
                                )
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
            ) {
                AnimatedVisibility(
                    visible = state.replyTo != null || state.editing != null,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut()
                ) {
                    // One banner for both: they are alternatives, never both at
                    // once, and each cancels the other when chosen.
                    (state.editing ?: state.replyTo)?.let { message ->
                        ComposerBanner(
                            message = message,
                            isEditing = state.editing != null,
                            onCancel = onComposerBannerCancelled
                        )
                    }
                }

                AnimatedVisibility(
                    visible = state.pendingAttachment != null,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut()
                ) {
                    AttachmentChip(
                        draft = state.pendingAttachment,
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
                            onPick = onForwardTo
                        )
                    }

                    SelectionToolbar(
                        count = state.selection.count,
                        actions = state.availableActions,
                        onCopy = {
                            clipboard.setText(AnnotatedString(copyText(state.selectedMessages)))
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
                            onTakePhoto = {
                                val uri = cameraUri(context, newCameraFile(context))
                                cameraTarget = uri
                                cameraLauncher.launch(uri)
                            },
                            onPickRecent = { uri ->
                                onAttachmentPicked(AttachmentDraft.Photos(listOf(uri)))
                                onAttachmentSheetOpenChange(false)
                            }
                        )
                    }

                    ComposerBar(
                        value = state.draft,
                        onValueChange = onDraftChange,
                        onAttach = { onAttachmentSheetOpenChange(true) },
                        onCamera = {
                            // The same launch the sheet's camera entry makes. Kept
                            // as one expression rather than shared with it: this
                            // is three lines, and a helper that exists to avoid
                            // repeating three lines is the harder thing to read.
                            val uri = cameraUri(context, newCameraFile(context))
                            cameraTarget = uri
                            cameraLauncher.launch(uri)
                        },
                        onSend = onSend,
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
                                onSend()
                            }
                        },
                        onRecordCancel = {
                            recordingSince = null
                            recorder.cancel()
                        },
                        onSampleAmplitude = recorder::sample,
                        hasAttachment = state.pendingAttachment != null,
                        focusRequester = composerFocus
                    )
                }
            }
            }

            state.pendingDelete?.let { target ->
                DeleteMessageDialog(
                    message = target,
                    onDismiss = onDeleteDismissed,
                    onDelete = { forEveryone -> onDeleteConfirmed(target, forEveryone) }
                )
            }

            state.viewingPhoto?.let { photo ->
                PhotoViewer(
                    path = photo.photoPath.orEmpty(),
                    caption = photo.text,
                    onDismiss = onPhotoClosed
                )
            }

            state.reactingTo?.let { target ->
                ReactionPicker(
                    available = state.availableReactions,
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
}

@Composable
private fun DaySeparator(date: Long) {
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MessageBubble(
    message: ChatMessage,
    isLastInRun: Boolean,
    isFirstInRun: Boolean,
    showAvatar: Boolean,
    onCopy: () -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onReact: () -> Unit,
    onReactionToggled: (String) -> Unit,
    isSelected: Boolean,
    isSelecting: Boolean,
    onSelect: () -> Unit,
    voiceState: VoiceState,
    onVoiceToggled: () -> Unit,
    voiceProgress: Float,
    onVoiceSeek: (Float) -> Unit,
    onPhotoVisible: () -> Unit,
    onPhotoOpened: () -> Unit
) {
    val outgoing = message.isOutgoing
    var menuOpen by remember { mutableStateOf(false) }

    // Swipe right to reply. The drag is kept in pixels because that is what
    // the pointer reports; the thresholds are dp, so they convert once here
    // rather than on every frame.
    val density = LocalDensity.current
    val maxOffsetPx = with(density) { SwipeToReply.MAX_OFFSET_DP.dp.toPx() }
    val triggerOffsetPx = with(density) { SwipeToReply.TRIGGER_OFFSET_DP.dp.toPx() }
    var rawDrag by remember { mutableFloatStateOf(0f) }
    val offset by animateFloatAsState(
        targetValue = swipeOffset(rawDrag, maxOffsetPx),
        // Released, the bubble springs back; this is the theme's own spring,
        // so it moves like everything else rather than to a number chosen here.
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "swipeToReply"
    )
    val haptics = LocalHapticFeedback.current
    val armed = shouldTriggerReply(offset, triggerOffsetPx)
    LaunchedEffect(armed) {
        // Felt at the moment the threshold is crossed, not when the finger
        // lifts: by then the decision has already been made.
        if (armed) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    val corner = 20.dp
    val tail = 6.dp
    // Tight corners where a run continues, the tail only on its last message.
    val shape = RoundedCornerShape(
        topStart = if (outgoing || isFirstInRun) corner else tail,
        topEnd = if (!outgoing || isFirstInRun) corner else tail,
        bottomStart = if (outgoing || isLastInRun) corner else tail,
        bottomEnd = if (!outgoing || isLastInRun) corner else tail
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(offset.roundToInt(), 0) }
            .pointerInput(message.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (shouldTriggerReply(swipeOffset(rawDrag, maxOffsetPx), triggerOffsetPx)) {
                            onReply()
                        }
                        rawDrag = 0f
                    },
                    onDragCancel = { rawDrag = 0f }
                ) { change, dragAmount ->
                    // Consumed so the conversation does not scroll sideways
                    // underneath the gesture.
                    change.consume()
                    rawDrag += dragAmount
                }
            },
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (showAvatar && !outgoing) {
            // The gutter is held even where no avatar is drawn, so bubbles in a
            // run stay on one left edge instead of stepping in and out.
            Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                if (isLastInRun) {
                    AvatarBubble(
                        title = message.senderName.orEmpty().ifBlank { "?" },
                        seed = message.senderId ?: message.chatId,
                        size = 28.dp
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
        }
        Box {
            Surface(
            shape = shape,
            color = when {
                // Selected wins over the sender's own colour: which messages
                // are about to be acted on has to be readable at a glance,
                // and on an outgoing bubble the ordinary primary fill is
                // already the loudest thing on screen.
                isSelected -> MaterialTheme.colorScheme.tertiaryContainer
                outgoing -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.surfaceContainerHighest
            },
            shadowElevation = 1.dp,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .combinedClickable(
                    // Once a selection is up, a tap adds to it. Opening the
                    // menu on a plain tap the rest of the time would fire on
                    // every scroll that ends on a bubble.
                    onClick = { if (isSelecting) onSelect() },
                    onLongClick = { if (isSelecting) onSelect() else menuOpen = true }
                )
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (message.replyToId != null) {
                    QuotedMessage(
                        sender = message.replyToSender,
                        text = message.replyToText,
                        onTint = if (outgoing) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                }
                // takeIf/let rather than a null check and a bare read: since
                // ChatMessage moved to :core, Kotlin will not smart-cast its
                // nullable properties — a public property of another module can
                // change under a compiled caller.
                val sender = message.senderName?.takeIf { it.isNotBlank() }
                if (!outgoing && isFirstInRun && sender != null) {
                    Text(
                        sender,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                }
                when (message.contentType) {
                    MessageContentType.Document -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Description, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(message.fileName ?: "File", fontWeight = FontWeight.SemiBold)
                                Text(
                                    message.fileSizeLabel ?: "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                        if (message.text.isNotBlank() && message.text != message.fileName) {
                            Spacer(Modifier.height(6.dp))
                            Text(message.text)
                        }
                    }
                    MessageContentType.Photo -> {
                        PhotoMessage(
                            path = message.photoPath,
                            aspect = message.photoAspect,
                            caption = message.text,
                            outgoing = outgoing,
                            onVisible = onPhotoVisible,
                            onOpen = onPhotoOpened
                        )
                    }
                    MessageContentType.Video -> {
                        val video = message.video
                        if (video == null) {
                            // A video message the backend could not read.
                            // Rather than an empty bubble, the caption — which
                            // is what the rest of the app would show anyway.
                            Text(
                                message.text.ifBlank { "Video" },
                                color = if (outgoing) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        } else {
                            VideoMessage(
                                video = video,
                                caption = message.text,
                                outgoing = outgoing,
                                onPosterVisible = onPhotoVisible,
                                onOpen = onPhotoOpened
                            )
                        }
                    }
                    MessageContentType.Voice -> {
                        VoiceMessage(
                            label = message.text.ifBlank { "Voice message" },
                            waveform = message.waveform,
                            state = voiceState,
                            outgoing = outgoing,
                            onToggle = onVoiceToggled,
                            progress = voiceProgress,
                            onSeek = onVoiceSeek
                        )
                    }
                    else -> Text(
                        message.text,
                        color = if (outgoing) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
                message.linkPreview?.let { preview ->
                    Spacer(Modifier.height(8.dp))
                    LinkPreviewCard(preview = preview, outgoing = outgoing)
                }
                if (message.reactions.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    // Inside the bubble rather than under it, which is where
                    // Telegram puts them and what keeps a reaction attached to
                    // its message when the list is dense.
                    ReactionRow(
                        reactions = message.reactions,
                        onToggle = onReactionToggled
                    )
                }
                Spacer(Modifier.height(4.dp))
                val footnote = (
                    if (outgoing) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface
                )
                    .copy(alpha = 0.55f)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    if (message.isEdited) {
                        // Telegram marks an edited message; hiding it would
                        // let a bubble quietly differ from what was sent.
                        Text(
                            "edited ",
                            style = MaterialTheme.typography.labelSmall,
                            color = footnote
                        )
                    }
                    Text(
                        message.timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = footnote
                    )
                    if (outgoing) {
                        Spacer(Modifier.width(4.dp))
                        // Material ships both ticks, so there is nothing to draw
                        // by hand: one for sent, two for read.
                        Icon(
                            imageVector = if (message.isRead) {
                                Icons.Rounded.DoneAll
                            } else {
                                Icons.Rounded.Done
                            },
                            contentDescription = if (message.isRead) "Read" else "Sent",
                            tint = footnote,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Reply") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Reply, contentDescription = null) },
                    onClick = {
                        onReply()
                        menuOpen = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Select") },
                    leadingIcon = {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                    },
                    onClick = {
                        onSelect()
                        menuOpen = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("React") },
                    leadingIcon = {
                        Icon(Icons.Rounded.AddReaction, contentDescription = null)
                    },
                    onClick = {
                        onReact()
                        menuOpen = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Copy") },
                    leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                    onClick = {
                        onCopy()
                        menuOpen = false
                    }
                )
                if (message.canBeEdited) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        onClick = {
                            onEdit()
                            menuOpen = false
                        }
                    )
                }
                if (message.canBeDeletedForSelf || message.canBeDeletedForEveryone) {
                    DropdownMenuItem(
                        text = {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            onDelete()
                            menuOpen = false
                        }
                    )
                }
            }
        }
    }
}

/**
 * Where the camera app writes, in the app's own cache.
 *
 * A real file rather than a gallery entry: nothing is left behind in the
 * user's gallery if the shot is cancelled, and the app owns what it sends.
 */
private fun newCameraFile(context: Context): File {
    val directory = File(context.cacheDir, "camera").apply { mkdirs() }
    return File(directory, "capture-${System.currentTimeMillis()}.jpg")
}

/**
 * The same file as a Uri the camera app is allowed to write to.
 *
 * The authority matches the provider in the manifest; getting it wrong throws
 * at the launch rather than returning null, which is the right failure — a
 * silent one would look like a camera that does nothing.
 */
private fun cameraUri(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

/**
 * One photo, full screen.
 *
 * A `Dialog` rather than a route: it is not somewhere the conversation has
 * navigated to, and back should return to the message rather than to whatever
 * the graph thinks came before. usePlatformDefaultWidth false is what lets it
 * reach the edges — without it a dialog is inset like an alert, which is not
 * what a photo wants.
 *
 * `ContentScale.Fit`, not Crop: the bubble crops to keep the list tidy, and
 * the whole point of opening it is to see the parts the bubble cut off.
 *
 * Pinch to zoom is not here yet. A photo that fills the screen is most of the
 * way to the thing, and a half-working gesture would be worse than none.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoViewer(
    path: String,
    caption: String,
    onDismiss: () -> Unit
) {
    // Where the photo is and how big, and how far a drag has taken it towards
    // being let go. Both are remembered per photo rather than hoisted: a
    // viewer that reopened at yesterday's zoom would be answering a question
    // nobody asked.
    var zoom by remember { mutableStateOf(Zoom()) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }

    val progress = dismissProgress(dragY, viewport.height.toFloat())
    // The scrim thins and the photo shrinks together, so letting go halfway is
    // visibly halfway rather than a state the gesture cannot show.
    val scrim = 0.92f * (1f - progress)
    val dismissScale = 1f - progress * 0.2f

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewport = it }
                // Its own scrim, because the photo is the content rather than
                // something sitting on a surface.
                .background(Color.Black.copy(alpha = scrim)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = path,
                contentDescription = caption.ifBlank { "Photo" },
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoom.scale * dismissScale
                        scaleY = zoom.scale * dismissScale
                        translationX = zoom.offsetX
                        translationY = zoom.offsetY + dragY
                    }
                    // Pinch and drag, in one gesture detector because they are
                    // one gesture: two fingers scale, one pans, and which is
                    // happening changes mid-stroke.
                    .pointerInput(path) {
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            zoom = zoomAfterGesture(
                                current = zoom,
                                scaleChange = gestureZoom,
                                panX = pan.x,
                                panY = pan.y,
                                viewportWidth = size.width.toFloat(),
                                viewportHeight = size.height.toFloat()
                            )
                        }
                    }
                    // Drag to dismiss, and only while zoomed out: once the
                    // photo is larger than the frame a vertical drag means
                    // "look further down", which is what the detector above
                    // is for.
                    .pointerInput(path, zoom.isZoomed) {
                        if (zoom.isZoomed) return@pointerInput
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (shouldDismiss(dragY, size.height.toFloat())) {
                                    onDismiss()
                                } else {
                                    dragY = 0f
                                }
                            },
                            onDragCancel = { dragY = 0f }
                        ) { _, delta -> dragY += delta }
                    }
                    .pointerInput(path) {
                        detectTapGestures(
                            // A tap on the photo closes it, as it always has.
                            // A tap while zoomed does not: the photo is being
                            // looked at, and a stray finger should not end
                            // that.
                            onTap = { if (!zoom.isZoomed) onDismiss() },
                            onDoubleTap = { zoom = zoomToggled(zoom) }
                        )
                    }
            )
            if (caption.isNotBlank() && caption != "Photo") {
                Text(
                    caption,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(24.dp)
                        // Out of the way of the photo itself once it is being
                        // examined, and back when it is not.
                        .alpha(if (zoom.isZoomed) 0f else 1f - progress)
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(8.dp)
                    .alpha(1f - progress)
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}

/**
 * A photo in a bubble.
 *
 * The space is reserved from the photo's own aspect ratio before any bytes
 * arrive, so the bubble does not change size when they do — in a list, one
 * bubble resizing moves everything below it, which is the difference between
 * a conversation loading and a conversation jumping.
 *
 * The download is asked for when this appears rather than on a tap, because a
 * photo is meant to be seen without being asked for. Coil takes it from there:
 * a cache, cancellation when the row scrolls away, and downsampling to the
 * size actually drawn.
 */
@Composable
private fun PhotoMessage(
    path: String?,
    aspect: Float,
    caption: String,
    outgoing: Boolean,
    onVisible: () -> Unit,
    onOpen: () -> Unit
) {
    LaunchedEffect(path) {
        if (path == null) onVisible()
    }
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // Clamped: a panorama would otherwise be a sliver and a very
                // tall photo would fill the screen on its own.
                .aspectRatio(aspect.coerceIn(0.6f, 1.9f))
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable(onClick = onOpen),
            contentAlignment = Alignment.Center
        ) {
            if (path == null) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                AsyncImage(
                    model = path,
                    contentDescription = caption.ifBlank { "Photo" },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        // "Photo" is what a message with no caption is called, not something
        // the sender wrote, so it is not repeated under the picture.
        if (caption.isNotBlank() && caption != "Photo") {
            Spacer(Modifier.height(6.dp))
            Text(
                caption,
                color = if (outgoing) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * A video message: its poster, its length, and the one control it needs.
 *
 * Built like [PhotoMessage] on purpose — same clamped aspect, same rounded
 * container, same reserved space before anything arrives — because a video in
 * a chat is a picture you can start, and making it a different shape would
 * say it was a different kind of thing.
 *
 * Two things sit over the poster: a play button in the middle, and the
 * duration in the corner. Both are drawn on scrims rather than straight onto
 * the frame, because a poster can be any colour and white on a white sky is
 * not a control.
 *
 * The poster is fetched on sight and the video is not. Scrolling past a
 * conversation should not pull down everything anyone ever sent.
 */
@Composable
private fun VideoMessage(
    video: VideoContent,
    caption: String,
    outgoing: Boolean,
    onPosterVisible: () -> Unit,
    onOpen: () -> Unit
) {
    LaunchedEffect(video.thumbPath) {
        if (video.thumbPath == null) onPosterVisible()
    }
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(video.aspect.coerceIn(0.6f, 1.9f))
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable(onClick = onOpen),
            contentAlignment = Alignment.Center
        ) {
            video.thumbPath?.let { poster ->
                AsyncImage(
                    model = poster,
                    contentDescription = caption.ifBlank { "Video" },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            // The button is there whether or not the poster is. A video with
            // no frame yet is still a video, and a bubble showing only a
            // spinner would look like a photo that failed.
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.45f),
                contentColor = Color.White,
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = "Play",
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            if (video.durationSeconds > 0) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = Color.Black.copy(alpha = 0.45f),
                    contentColor = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                ) {
                    Text(
                        formatDuration(video.durationSeconds.toLong()),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
        // As with a photo: "Video" is what an untitled one is called, not
        // something the sender wrote.
        if (caption.isNotBlank() && caption != "Video") {
            Spacer(Modifier.height(6.dp))
            Text(
                caption,
                color = if (outgoing) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** What the play button on a voice bubble is currently doing. */
enum class VoiceState { Idle, Loading, Playing }

/**
 * A voice message, with the one control it needs.
 *
 * Play and pause are the same button showing which of the two it is, because
 * the gesture is the same tap and a second button would have to be greyed out
 * half the time. A file still arriving gets a spinner in the button's place
 * rather than a play triangle that does nothing for two seconds.
 *
 * There is no waveform yet. Nothing captures amplitudes when recording and
 * TDLib's own waveform is not read here, so drawing one would be drawing a
 * shape that has nothing to do with the sound.
 */
@Composable
private fun VoiceMessage(
    label: String,
    waveform: List<Int>,
    state: VoiceState,
    outgoing: Boolean,
    onToggle: () -> Unit,
    progress: Float,
    onSeek: (Float) -> Unit
) {
    val tint = if (outgoing) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.primary
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (state) {
            VoiceState.Loading -> CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = tint,
                modifier = Modifier
                    .size(24.dp)
                    .padding(2.dp)
            )
            else -> IconButton(onClick = onToggle, modifier = Modifier.size(24.dp)) {
                Icon(
                    if (state == VoiceState.Playing) Icons.Rounded.Pause
                    else Icons.Rounded.PlayArrow,
                    contentDescription = if (state == VoiceState.Playing) "Pause" else "Play",
                    tint = tint
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Waveform(
            bars = remember(waveform) { waveformBars(waveform, WAVEFORM_BARS) },
            color = tint,
            progress = progress,
            onSeek = onSeek,
            modifier = Modifier
                .width(120.dp)
                .height(28.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (outgoing) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

/** How many bars a voice bubble draws, whatever the recording's length. */
private const val WAVEFORM_BARS = 28

/**
 * The shape of what was said.
 *
 * Drawn rather than composed, and this is the case CLAUDE.md keeps a place
 * for: Material has no component for a bar chart of amplitudes, and
 * twenty-eight Boxes with animated heights would be twenty-eight layout nodes
 * per bubble in a list that scrolls.
 *
 * A silent bar is still drawn, at a minimum height, because a row with gaps in
 * it reads as a broken picture rather than as a pause.
 */
@Composable
private fun Waveform(
    bars: List<Float>,
    color: Color,
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.pointerInput(bars.size) {
            // Tapping the bars moves playback there, which is the only reason
            // to know where in the row the finger landed. A drag is left to
            // the list, so scrolling past a voice message still scrolls.
            detectTapGestures { offset ->
                onSeek((offset.x / size.width).coerceIn(0f, 1f))
            }
        }
    ) {
        if (bars.isEmpty()) return@Canvas
        val slot = size.width / bars.size
        // Roughly a third of each slot is the gap; the rest is the bar.
        val barWidth = slot * 0.6f
        val radius = barWidth / 2f
        // Measured in bars rather than pixels: a bar half-filled would be a
        // second way of showing the same thing, at a resolution nobody reads.
        val played = (bars.size * progress).toInt()
        bars.forEachIndexed { index, value ->
            val height = (size.height * value).coerceAtLeast(barWidth)
            val left = index * slot + (slot - barWidth) / 2f
            drawRoundRect(
                color = color,
                topLeft = Offset(left, (size.height - height) / 2f),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(radius, radius),
                // The part already heard is solid and the rest is faded,
                // rather than two colours: one of them would have to be picked
                // out of the scheme for a bubble that is already tinted, and
                // opacity says "behind you" in either palette.
                alpha = if (index < played) 1f else 0.4f
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
private fun PinnedMessageBar(
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
private fun UnreadSeparator() {
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
 * Where to send the selection.
 *
 * Every chat but this one, as `ListItem` rows with the avatar the list already
 * draws — a picker that looked nothing like the chat list would be a second
 * vocabulary for the same thing.
 *
 * One tap sends. There is no confirmation because there is nothing to
 * confirm: the sheet was opened deliberately, it names the count, and a
 * forward is undone by deleting it like any other message.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForwardSheet(
    targets: List<ChatPreview>,
    count: Int,
    onDismiss: () -> Unit,
    onPick: (ChatPreview) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Text(
            if (count == 1) "Forward to…" else "Forward $count messages to…",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        if (targets.isEmpty()) {
            Text(
                "No other chats to forward to.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(targets, key = { it.id }) { target ->
                    ListItem(
                        headlineContent = { Text(target.title, maxLines = 1) },
                        leadingContent = {
                            AvatarBubble(
                                title = target.title,
                                seed = target.avatarColor,
                                size = 40.dp
                            )
                        },
                        modifier = Modifier.clickable { onPick(target) }
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * What the paperclip opens.
 *
 * `ModalBottomSheet` with `ListItem` rows, which is what Material ships for
 * "choose one of these" — Telegram draws a grid of its own here, and this is
 * the platform's answer to the same question.
 *
 * Camera hands the photo straight to the composer as a draft, the same shape
 * the gallery picker produces, so everything downstream treats the two alike.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachmentSheet(
    onDismiss: () -> Unit,
    onPickPhoto: () -> Unit,
    onPickFile: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickRecent: (String) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        // The pictures somebody is most likely to send are the ones they just
        // took, and reaching them through a row called "Photo or video" is a
        // screen and a scroll away from the sheet that was supposed to be the
        // shortcut. Draws nothing without the permission for it.
        RecentPhotoCarousel(
            onPick = onPickRecent,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        ListItem(
            headlineContent = { Text("Photo or video") },
            supportingContent = { Text("From the gallery") },
            leadingContent = { Icon(Icons.Rounded.Image, contentDescription = null) },
            modifier = Modifier.clickable(onClick = onPickPhoto)
        )
        ListItem(
            headlineContent = { Text("Camera") },
            supportingContent = { Text("Take a photo now") },
            leadingContent = { Icon(Icons.Rounded.PhotoCamera, contentDescription = null) },
            modifier = Modifier.clickable(onClick = onTakePhoto)
        )
        ListItem(
            headlineContent = { Text("File") },
            supportingContent = { Text("Anything else") },
            leadingContent = { Icon(Icons.Rounded.AttachFile, contentDescription = null) },
            modifier = Modifier.clickable(onClick = onPickFile)
        )
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * The search field that takes the chat's name out of the app bar.
 *
 * Focused as it appears: the field arrives because someone tapped search, and
 * making them tap again to type in it is a step that exists for no reason.
 */
@Composable
private fun ChatSearchField(
    query: String,
    onQueryChange: (String) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search in chat") },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        colors = TextFieldDefaults.colors(
            // The app bar is already a surface; a second one inside it would
            // read as a box drawn on a box.
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        )
    )
}

/**
 * What a search inside the conversation found.
 *
 * `ListItem`, because that is what Material ships for a row with a headline, a
 * supporting line and a trailing value — building the same thing by hand loses
 * its metrics, its state layer and its accessibility, which has happened in
 * this project once already.
 *
 * Each row shows one line of context around the hit rather than the whole
 * message, with the term itself in bold. A result that reads as an unbroken
 * wall of text says only that the word is in there somewhere.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatSearchResults(
    search: ChatSearchState,
    modifier: Modifier = Modifier,
    onOpen: (ChatMessage) -> Unit
) {
    Box(modifier = modifier.fillMaxWidth()) {
        when {
            search.isSearching -> CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 32.dp)
            )
            search.isEmpty -> Text(
                "Nothing in this chat matches “${search.query}”.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 24.dp, vertical = 32.dp)
            )
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(search.results, key = { it.id }) { hit ->
                    val line = remember(hit.id, search.query) {
                        snippet(hit.text, search.query)
                    }
                    ListItem(
                        headlineContent = {
                            Text(
                                buildAnnotatedString {
                                    append(line.text)
                                    if (line.matchLength > 0) {
                                        addStyle(
                                            SpanStyle(fontWeight = FontWeight.Bold),
                                            line.matchStart,
                                            line.matchStart + line.matchLength
                                        )
                                    }
                                },
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        overlineContent = hit.senderName?.let { sender ->
                            { Text(sender) }
                        },
                        trailingContent = { Text(hit.timeLabel) },
                        modifier = Modifier.clickable { onOpen(hit) }
                    )
                }
            }
        }
    }
}

/**
 * What replaces the composer while messages are selected.
 *
 * `HorizontalFloatingToolbar` is the Expressive component for exactly this —
 * a small set of actions on a floating surface — so there is nothing to build.
 * Which buttons appear comes from [SelectionActions]: a Delete that only works
 * on some of the selection is a failure the UI could have predicted.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SelectionToolbar(
    count: Int,
    actions: SelectionActions,
    onCopy: () -> Unit,
    onForward: () -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit
) {
    // Centred and hugging its content: a floating toolbar is a pill, and
    // stretching it across the screen would make it the bar it is not.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        HorizontalFloatingToolbar(expanded = true) {
            IconButton(onClick = onClear) {
                Icon(Icons.Rounded.Close, contentDescription = "Clear selection")
            }
            Text(
                count.toString(),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            if (actions.canCopy) {
                IconButton(onClick = onCopy) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy")
                }
            }
            // No flag guards this one: anything that can be selected can be
            // sent on, and Telegram refuses at the far end if the target chat
            // does not accept it.
            IconButton(onClick = onForward) {
                Icon(Icons.AutoMirrored.Rounded.Forward, contentDescription = "Forward")
            }
            if (actions.canDeleteForSelf || actions.canDeleteForEveryone) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/**
 * The confirmation for deleting a whole selection.
 *
 * Separate from [DeleteMessageDialog] because the choice is not the same: the
 * for-everyone option is offered only when every selected message allows it,
 * and the count is the only thing telling the user what is about to go.
 */
@Composable
private fun DeleteSelectionDialog(
    count: Int,
    actions: SelectionActions,
    onDismiss: () -> Unit,
    onDelete: (forEveryone: Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
        title = { Text(if (count == 1) "Delete message?" else "Delete $count messages?") },
        text = {
            Text(
                if (actions.canDeleteForEveryone) {
                    "This cannot be undone."
                } else {
                    "They will be removed for you. The other side keeps their copies."
                }
            )
        },
        confirmButton = {
            TextButton(onClick = { onDelete(false) }) {
                Text(if (actions.canDeleteForEveryone) "Delete for me" else "Delete")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                if (actions.canDeleteForEveryone) {
                    TextButton(
                        onClick = { onDelete(true) },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete for everyone")
                    }
                }
            }
        }
    )
}

/**
 * The chips under a message's text.
 *
 * FilterChip rather than anything hand-drawn: the state it already models —
 * selected or not, with the tonal fill and the state layer that go with it —
 * is exactly the distinction a reaction needs, between one we are part of and
 * one we are not. A count of one is shown as the bare emoji, because "🔥 1"
 * says nothing "🔥" does not.
 *
 * Scrolls sideways rather than wrapping. A message with a dozen distinct
 * reactions is rare enough that giving it several lines of height would cost
 * every ordinary message the layout pass.
 *
 * A Row with horizontalScroll, not a LazyRow: a LazyRow measures to the width
 * it is offered, which inside a bubble is the 320dp cap — so a single reaction
 * would stretch every message carrying one to full width. This wraps its
 * content and only scrolls once there is more of it than fits.
 */
@Composable
private fun ReactionRow(
    reactions: List<MessageReaction>,
    onToggle: (String) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState())
    ) {
        reactions.forEach { reaction ->
            FilterChip(
                selected = reaction.isChosen,
                onClick = { onToggle(reaction.emoji) },
                label = {
                    Text(
                        if (reaction.count > 1) "${reaction.emoji} ${reaction.count}"
                        else reaction.emoji,
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                // Tighter than the default pill, so a row of chips inside a
                // bubble reads as an annotation on the message rather than a
                // second control bar.
                shape = MaterialTheme.shapes.small,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)
                )
            )
        }
    }
}

/**
 * The sheet that opens from "React".
 *
 * A bottom sheet rather than a popup row above the bubble: the popup is what
 * Telegram draws, and drawing it means positioning a floating surface against
 * a bubble that may be at either edge and near either end of the list. The
 * sheet is the platform's own answer to "choose one of these", and it is one
 * stock component instead of a positioning problem.
 *
 * [chosen] marks what is already on the message, so the sheet opens showing
 * the current state rather than a blank set — and tapping it withdraws the
 * reaction, which is the same gesture as tapping its chip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReactionPicker(
    available: List<String>,
    chosen: String?,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        if (available.isEmpty()) {
            // A chat can forbid reactions outright, and an empty sheet with no
            // explanation reads as a bug rather than a rule.
            Text(
                "This chat does not allow reactions.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 24.dp)
            ) {
                items(available, key = { it }) { emoji ->
                    FilterChip(
                        selected = emoji == chosen,
                        onClick = { onPick(emoji) },
                        label = {
                            Text(emoji, style = MaterialTheme.typography.headlineSmall)
                        }
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * The quoted block inside a bubble.
 *
 * [text] is null when the original falls outside the loaded window, which is
 * ordinary for a reply to something old — the block still shows, because the
 * fact that this is a reply matters even when the quote cannot be recovered.
 */
@Composable
private fun QuotedMessage(sender: String?, text: String?, onTint: Color) {
    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(onTint.copy(alpha = 0.5f))
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                sender ?: "Reply",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = onTint.copy(alpha = 0.9f)
            )
            Text(
                text ?: "Message",
                style = MaterialTheme.typography.bodySmall,
                color = onTint.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Confirms a deletion, and asks the one question Telegram asks: for me, or
 * for everyone.
 *
 * Deleting for everyone withdraws the message from the other side and cannot
 * be undone, so it is never the default action — it is offered only where the
 * server said it is permitted, and sits apart from the safe one.
 */
@Composable
private fun DeleteMessageDialog(
    message: ChatMessage,
    onDismiss: () -> Unit,
    onDelete: (forEveryone: Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
        title = { Text("Delete message?") },
        text = {
            Text(
                if (message.canBeDeletedForEveryone) {
                    "This cannot be undone."
                } else {
                    "It will be removed for you. The other side keeps their copy."
                }
            )
        },
        confirmButton = {
            TextButton(onClick = { onDelete(false) }) {
                Text(if (message.canBeDeletedForEveryone) "Delete for me" else "Delete")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                if (message.canBeDeletedForEveryone) {
                    TextButton(
                        onClick = { onDelete(true) },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text("Delete for everyone") }
                }
            }
        }
    )
}

/** The banner over the composer: what is being answered, or amended. */
@Composable
private fun ComposerBanner(
    message: ChatMessage,
    isEditing: Boolean,
    onCancel: () -> Unit
) {
    // The conversation's own colour, opaque — not transparent, and not a
    // container tone.
    //
    // Two versions of this were wrong in opposite directions. A filled
    // surfaceContainerHigh strip read as a bar welded to the top of the
    // composer, a second band where there should be one floating control.
    // Making it transparent fixed the band and broke something worse: the
    // messages behind it showed through the text.
    //
    // So it paints what is behind it. Two layers rather than one, because the
    // conversation's background is a gradient and this is the bottom of it:
    // `surface` with primary at eight percent over it is exactly what that
    // gradient ends on, so the banner disappears into it while still hiding
    // whatever it covers.
    //
    // Not blur. Material 3 Expressive ships no blurred material — the effect
    // exists in Compose as Modifier.blur, and it is the one thing CLAUDE.md
    // rules out by name: glass belongs to another platform's design language
    // and to the sibling project, not here.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // The conversation's own background, opaque. Not a tone of its
            // own: the banner is not a strip attached to the composer, it is
            // the place the reply is being written. Translucency was tried
            // with the conversation blurred behind it and the owner asked for
            // both to go — see CLAUDE.md.
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp)
        ) {
            Icon(
                if (isEditing) Icons.Rounded.Edit else Icons.AutoMirrored.Rounded.Reply,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isEditing) "Edit message" else (message.senderName ?: "You"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    message.text.ifBlank { "Attachment" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onCancel) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = if (isEditing) "Cancel edit" else "Cancel reply"
                )
            }
        }
    }
}

@Composable
private fun AttachmentChip(draft: AttachmentDraft?, onClear: () -> Unit) {
    if (draft == null) return
    val label = when (draft) {
        is AttachmentDraft.Files -> "${draft.names.size} file(s): ${draft.names.firstOrNull().orEmpty()}"
        is AttachmentDraft.Photos -> "${draft.uris.size} photo(s)"
        // A recording is sent the moment the finger lifts, so this chip is
        // only ever seen for the instant between the two — named anyway,
        // because a `when` over a sealed type is where a new case should
        // announce itself rather than fall into an else.
        is AttachmentDraft.Voice -> "Voice message ${formatDuration(draft.durationSeconds.toLong())}"
    }
    // An InputChip, which is the Material component for "one item you have
    // added and can take back". It was a Row painted to look like a chip,
    // with a "Clear" TextButton where the dismiss icon belongs.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        InputChip(
            selected = false,
            onClick = onClear,
            label = { Text(label, maxLines = 1) },
            leadingIcon = {
                Icon(
                    if (draft is AttachmentDraft.Photos) Icons.Rounded.Image
                    else Icons.Rounded.AttachFile,
                    contentDescription = null
                )
            },
            trailingIcon = {
                Icon(Icons.Rounded.Close, contentDescription = "Remove attachment")
            }
        )
    }
}

/**
 * How far the composer's icon buttons sit above the bottom of their row.
 *
 * Not a nudge by eye. A filled `TextField` is 56dp tall by the spec and an
 * `IconButton` is 48dp, and the row aligns them at the bottom so that a field
 * grown to several lines keeps its buttons beside the last one. Aligning the
 * boxes at the bottom leaves their centres eight apart, so the icons draw four
 * low — measured off an emulator screenshot as ten pixels at 2.75x, which is
 * exactly this.
 *
 * Lifting the buttons rather than centring the row fixes every line count at
 * once: the offset between the field's last line and the row's bottom does not
 * change as the field grows.
 */
private val ComposerButtonLift = 4.dp

@Composable
private fun ComposerBar(
    value: String,
    onValueChange: (String) -> Unit,
    onAttach: () -> Unit,
    /** The camera, straight from the composer rather than through the sheet. */
    onCamera: () -> Unit,
    onSend: () -> Unit,
    recordingSince: Long?,
    onRecordStart: () -> Unit,
    onRecordStop: () -> Unit,
    onRecordCancel: () -> Unit,
    onSampleAmplitude: () -> Unit,
    /**
     * Whether something is already attached and waiting to go.
     *
     * The right-hand button used to be chosen from the text alone, so a
     * photo picked with nothing typed left a microphone where send should
     * have been — the attachment was on screen as a chip and there was no
     * way to send it without also writing something. What decides that
     * button is whether there is anything to send, and a photo is.
     */
    hasAttachment: Boolean,
    /** Held by the screen, so replying can put the caret in the field. */
    focusRequester: FocusRequester
) {
    // Floating, not a bar. It used to be a full-width surface welded to the
    // bottom of the screen with the buttons outside the field; this is one
    // capsule held clear of the edges, with everything inside it — the shape
    // Android's own messaging apps have settled on.
    //
    // The version before this grouped the two buttons into a ButtonGroup and
    // left them beside the field, which read as a split button sitting next
    // to a text box: three things in a row rather than one control. The
    // capsule is what makes it read as one, so the buttons are plain icon
    // buttons inside it and the group is gone. ButtonGroup is still the right
    // component for a segmented choice — see ROADMAP.md — just not for this.
    // No navigationBarsPadding here, and its absence is the fix. The Scaffold
    // this sits inside already applies the bottom inset through the padding
    // it hands its content, so adding it again spaced the capsule off the
    // navigation bar twice. The call was inherited from the full-width bar
    // this replaced, where it went unnoticed: that bar was painted to the
    // bottom of the screen, so a doubled inset only made it look tall. Give
    // it a shape and lift it off the edges and the gap becomes a hole.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Sixteen, the side gutter Material uses everywhere else in this
            // app. It was twelve, and the gap was reported as missing
            // entirely — correctly, but not for the reason it looked like.
            // The inset was there; the capsule's edge was not visible, so
            // there was nothing for the inset to hold clear of. See the
            // colour below. Sixteen once the edge shows is simply the right
            // number.
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // No shadow, and that is the correction rather than an omission. The
        // first version of this carried shadowElevation = 6.dp, inherited
        // from the full-width bar it replaced and then nudged by eye. Material
        // 3 expresses depth as tone — the surfaceContainer ladder — and keeps
        // shadows for the few things that genuinely hover, like a FAB. The
        // apps this shape was taken from have no shadow under their composer
        // either: theirs reads as lifted because it is plainly darker than the
        // conversation, not because something is cast beneath it.
        //
        // tonalElevation is gone with the shadow: Compose only applies it when
        // the colour is `surface`, so on an explicit container colour it was
        // doing nothing at all.
        //
        // The capsule sits one step below the field inside it, which is why
        // it is not at the top of the ladder. See the field's colours below.
        // The darkest container, and the field inside it the lightest —
        // which is the reverse of what this used to be, on measurement
        // rather than on taste. Read off a screenshot from the emulator:
        //
        //   conversation background         (239, 240, 246)
        //   capsule, surfaceContainer       (239, 237, 241)   <- 5 apart
        //   field, surfaceContainerHighest  (227, 226, 230)
        //
        // Five units is nothing. The capsule had a border, a 28.dp corner
        // and a 12.dp inset, and none of the three could be seen, so the
        // composer read as a full-width band with a pill floating in it.
        // The cause is the conversation's own gradient: its bottom stop is
        // primary at 8% alpha, which lands almost exactly on
        // surfaceContainer — a decorative tint placed under the one control
        // that has to stand away from the background.
        //
        // Moving the capsule to the top of the container ladder puts twelve
        // units between it and the conversation, and the field then has to
        // go the other way to stay visible inside it. In dark mode the two
        // tones swap ends by construction, so the arrangement holds without
        // a second branch.
        Surface(
            shape = ComposerShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                // Eight rather than four, so the field inside has room to
                // breathe instead of meeting the capsule's edge. The capsule
                // grows with it, which is the intent: it is a container, and
                // a container whose contents touch its sides looks like a
                // mistake rather than like a frame.
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                // Bottom, so a field grown to several lines keeps the buttons
                // beside its last line rather than floating them in the middle.
                // Centring instead would fix the single-line case and break
                // every other one, which is why the buttons are lifted rather
                // than the row re-aligned — see ComposerButtonLift.
                verticalAlignment = Alignment.Bottom
            ) {
                // Three, not one, and that reverses an earlier decision in
                // this project: the argument was that a composer growing an
                // icon per attachment type runs out of room before it runs
                // out of types. True in general, and beside the point here —
                // these three are not "types of attachment" but the three
                // things people actually reach for, which is why the messaging
                // app this was modelled on puts exactly these three here. The
                // sheet still exists behind the plus for everything else.
                IconButton(
                    onClick = onAttach,
                    enabled = recordingSince == null,
                    modifier = Modifier.padding(bottom = ComposerButtonLift)
                ) {
                    Icon(Icons.Rounded.AttachFile, contentDescription = "Attach")
                }
                IconButton(
                    onClick = onCamera,
                    enabled = recordingSince == null,
                    modifier = Modifier.padding(bottom = ComposerButtonLift)
                ) {
                    Icon(Icons.Rounded.PhotoCamera, contentDescription = "Camera")
                }

                if (recordingSince != null) {
                    // The field is replaced rather than covered: nothing can be
                    // typed one-handed while the other thumb is holding the
                    // microphone down, and a running clock is the one thing worth
                    // knowing at that moment.
                    var elapsed by remember(recordingSince) { mutableLongStateOf(0L) }
                    LaunchedEffect(recordingSince) {
                        while (true) {
                            elapsed = (System.currentTimeMillis() - recordingSince) / 1000
                            // The same beat takes an amplitude reading, because
                            // getMaxAmplitude answers for the time since the last
                            // call — an irregular tick makes bars that stand for
                            // different lengths of recording.
                            onSampleAmplitude()
                            delay(RECORDING_TICK_MS)
                        }
                    }
                    Text(
                        "Recording  ${formatDuration(elapsed)}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp, vertical = 14.dp)
                    )
                } else {
                    TextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 2.dp)
                            .focusRequester(focusRequester),
                        placeholder = { Text("Message") },
                        shape = ComposerShape,
                        // The field carries its own fill, at the opposite
                        // end of the container ladder from the capsule
                        // around it. An earlier version made every
                        // container colour transparent on the argument that a
                        // filled field inside a filled surface draws a second
                        // shape nobody asked for — which is true about shapes
                        // and wrong about people. With nothing to fill it, the
                        // field was invisible: the words "Message" floated in
                        // a bar whose tappable part could not be told from its
                        // buttons, and the first person to look at it said so.
                        //
                        // A text field has to look like somewhere to type.
                        // That is what the second shape is for.
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor =
                                MaterialTheme.colorScheme.surfaceContainerLowest,
                            unfocusedContainerColor =
                                MaterialTheme.colorScheme.surfaceContainerLowest,
                            disabledContainerColor =
                                MaterialTheme.colorScheme.surfaceContainerLowest,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        ),
                        maxLines = 5
                    )
                }

                if (value.isBlank() && !hasAttachment) {
                    FilledIconButton(
                        // onClick stays empty because this is a hold, not a tap:
                        // the gesture below owns press, release and cancel, and a
                        // tap that fired as well would send an empty recording.
                        onClick = {},
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (recordingSince != null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            }
                        ),
                        modifier = Modifier
                            .padding(bottom = ComposerButtonLift)
                            .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    onRecordStart()
                                    // Waits here until the finger lifts or the
                                    // gesture is taken over by a scroll; either
                                    // way the recording ends where it started.
                                    val released = tryAwaitRelease()
                                    if (released) onRecordStop() else onRecordCancel()
                                }
                            )
                        }
                    ) {
                        Icon(Icons.Rounded.Mic, contentDescription = "Hold to record")
                    }
                } else {
                    FilledIconButton(
                        onClick = onSend,
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.padding(bottom = ComposerButtonLift)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send")
                    }
                }
            }
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
private fun clusterMembers(messages: List<ChatMessage>): List<ClusterMember> =
    messages
        .asReversed()
        .asSequence()
        .filterNot { it.isOutgoing }
        .mapNotNull { message ->
            val name = message.senderName?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            ClusterMember(name = name, seed = message.senderId ?: name.hashCode().toLong())
        }
        .distinctBy { it.seed }
        .toList()

/**
 * Telegram's card for a link, under the message that carried it.
 *
 * The accent bar down the leading edge is the same device the pinned-message
 * bar uses, and it is doing the same job: saying "this is about something
 * else" without a heading. It is a `Surface` rather than a `Card` because a
 * Card inside a bubble is a container inside a container with its own
 * elevation, and the bubble has already said where this belongs.
 *
 * Tapping it opens the link. Nothing here fetches the page: what is drawn is
 * what the server sent with the message, so every person in the conversation
 * sees the same card and no site learns who is reading it.
 */
@Composable
private fun LinkPreviewCard(preview: LinkPreview, outgoing: Boolean) {
    val uriHandler = LocalUriHandler.current
    // Against the bubble it sits in, not against the screen: an outgoing
    // bubble is primary, so the same tone would vanish into one of them.
    val container = if (outgoing) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val accent = if (outgoing) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.primary
    }
    val body = if (outgoing) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        color = container,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { uriHandler.openUri(preview.url) }
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(accent)
            )
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (preview.siteName.isNotBlank()) {
                    Text(
                        preview.siteName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = accent
                    )
                }
                if (preview.title.isNotBlank()) {
                    Text(
                        preview.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = body,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (preview.description.isNotBlank()) {
                    Text(
                        preview.description,
                        style = MaterialTheme.typography.bodySmall,
                        // Three lines: enough to say what the page is, few
                        // enough that a card cannot grow taller than the
                        // message it belongs to.
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = body.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}
