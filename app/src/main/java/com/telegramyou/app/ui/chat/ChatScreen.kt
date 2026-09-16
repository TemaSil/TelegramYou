package com.telegramyou.app.ui.chat

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoCamera
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import com.telegramyou.app.telegram.model.AttachmentDraft
import com.telegramyou.app.telegram.model.ChatDetail
import com.telegramyou.app.telegram.model.ChatMessage
import com.telegramyou.app.telegram.model.MessageContentType
import com.telegramyou.app.telegram.model.MessageReaction
import com.telegramyou.app.ui.components.AvatarBubble
import com.telegramyou.app.ui.theme.BubbleIncomingShape
import com.telegramyou.app.ui.theme.BubbleOutgoingShape
import com.telegramyou.app.ui.theme.ComposerShape
import com.telegramyou.app.ui.theme.DeepInk
import androidx.core.content.FileProvider
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    onAttachmentSheetOpenChange: (Boolean) -> Unit
) {
    val listState = rememberLazyListState()

    val context = LocalContext.current
    // Declared before the pickers, which launch work on it from their callbacks.
    val scope = rememberCoroutineScope()
    // Held across the launch because TakePicture answers with a boolean, not
    // with the Uri: the destination is chosen here and has to survive until
    // the camera app comes back.
    var cameraTarget by remember { mutableStateOf<File?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { taken ->
        val file = cameraTarget
        cameraTarget = null
        // false means the camera app was cancelled, and the empty file it was
        // given is left for the cache to clear rather than sent as a photo.
        if (taken && file != null) {
            onAttachmentPicked(AttachmentDraft.Photos(listOf(file.absolutePath)))
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val copied = withContext(Dispatchers.IO) { uris.mapNotNull { copyIn(context, it) } }
            if (copied.isEmpty()) return@launch
            onAttachmentPicked(
                AttachmentDraft.Files(
                    uris = copied.map { it.absolutePath },
                    names = copied.map { it.name }
                )
            )
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val copied = withContext(Dispatchers.IO) { uris.mapNotNull { copyIn(context, it) } }
            if (copied.isNotEmpty()) {
                onAttachmentPicked(AttachmentDraft.Photos(copied.map { it.absolutePath }))
            }
        }
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (chat != null) {
                                AvatarBubble(
                                    title = chat.title,
                                    seed = chat.avatarColor,
                                    size = 40.dp
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(chat.title, fontWeight = FontWeight.Bold, maxLines = 1)
                                    Text(
                                        text = when {
                                            detail?.isTyping == true -> "typing…"
                                            else -> detail?.memberCountLabel ?: ""
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (detail?.isTyping == true) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                actions = {
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceContainerLow,
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        )
                    )
                )
                .imePadding()
        ) {
            if (state.search.isOpen && state.search.query.isNotBlank()) {
                // Results take the conversation's place rather than covering
                // it. A panel over the messages would put two scrollable lists
                // one on top of the other, and the one underneath is not what
                // the finger is on.
                ChatSearchResults(
                    search = state.search,
                    modifier = Modifier.weight(1f),
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
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
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
                    itemsIndexed(messages, key = { _, m -> m.id }) { index, message ->
                        val previous = messages.getOrNull(index - 1)
                        val next = messages.getOrNull(index + 1)

                        if (startsNewDay(previous, message)) {
                            DaySeparator(message.date)
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
                            onSelect = { onSelectionToggled(message) }
                        )
                    }
                }
            }

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

            state.pendingDelete?.let { target ->
                DeleteMessageDialog(
                    message = target,
                    onDismiss = onDeleteDismissed,
                    onDelete = { forEveryone -> onDeleteConfirmed(target, forEveryone) }
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

            if (state.selection.isActive) {
                // The toolbar takes the composer's place rather than floating
                // over it. Nothing can be typed while a selection is up, so
                // leaving the field there would be a control that does nothing.
                SelectionToolbar(
                    count = state.selection.count,
                    actions = state.availableActions,
                    onCopy = {
                        clipboard.setText(AnnotatedString(copyText(state.selectedMessages)))
                        onSelectionCleared()
                    },
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
                            val file = newCameraFile(context)
                            cameraTarget = file
                            cameraLauncher.launch(cameraUri(context, file))
                        }
                    )
                }

                ComposerBar(
                    value = state.draft,
                    onValueChange = onDraftChange,
                    onAttach = { onAttachmentSheetOpenChange(true) },
                    onSend = onSend
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
    onSelect: () -> Unit
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
                        onTint = if (outgoing) DeepInk else MaterialTheme.colorScheme.onSurface
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
                        Text("${message.mediaEmoji ?: "🖼"} ${message.text}")
                    }
                    else -> Text(
                        message.text,
                        color = if (outgoing) DeepInk else MaterialTheme.colorScheme.onSurface
                    )
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
                val footnote = (if (outgoing) DeepInk else MaterialTheme.colorScheme.onSurface)
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
 * A real file rather than a gallery entry: nothing is left behind if the shot
 * is cancelled, and TDLib is handed a path it can open — see [copyIn] for why
 * that matters.
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
 * Copies what a picker returned into the app's cache, and answers with the
 * file.
 *
 * TDLib's `inputFileLocal` takes a filesystem path and opens it directly. A
 * picker hands back a `content://` Uri, which is not a path and which TDLib
 * cannot open — so passing one through, as this screen did, meant every
 * attachment failed to send in live mode while looking fine in demo mode,
 * where the backend ignores the value entirely.
 *
 * Null when the Uri cannot be read at all: a permission already revoked, or a
 * provider that has gone away. The caller drops it rather than attaching a
 * path to nothing.
 */
private fun copyIn(context: Context, uri: Uri): File? = runCatching {
    val directory = File(context.cacheDir, "outgoing").apply { mkdirs() }
    // Prefixed with the clock so two files of the same name do not collide,
    // and stripped of separators so a hostile name cannot climb out of the
    // directory.
    val name = uri.lastPathSegment?.substringAfterLast('/').orEmpty().ifBlank { "attachment" }
    val target = File(directory, "${System.currentTimeMillis()}-$name")
    context.contentResolver.openInputStream(uri)?.use { input ->
        target.outputStream().use { output -> input.copyTo(output) }
    } ?: return null
    target
}.getOrNull()

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
    onTakePhoto: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
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
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
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

@Composable
private fun ComposerBar(
    value: String,
    onValueChange: (String) -> Unit,
    onAttach: () -> Unit,
    onSend: () -> Unit
) {
    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // One button, not two. Which system picker to open is a question
            // for the sheet, and a composer that grows an icon per attachment
            // type runs out of room before it runs out of types.
            IconButton(onClick = onAttach) {
                Icon(Icons.Rounded.AttachFile, contentDescription = "Attach")
            }
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message") },
                shape = ComposerShape,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                maxLines = 5
            )
            Spacer(Modifier.width(8.dp))
            if (value.isBlank()) {
                FilledIconButton(
                    onClick = {},
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Icon(Icons.Rounded.Mic, contentDescription = "Voice")
                }
            } else {
                FilledIconButton(
                    onClick = onSend,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = DeepInk
                    )
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send")
                }
            }
        }
    }
}
