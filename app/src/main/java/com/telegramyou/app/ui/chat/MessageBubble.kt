package com.telegramyou.app.ui.chat

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import android.widget.Toast
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Download
import com.telegramyou.app.settings.QUICK_REACTION
import com.telegramyou.app.settings.DoubleTapAction
import com.telegramyou.app.settings.LocalGeekSettings
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import com.telegramyou.app.telegram.model.InlineButton
import com.telegramyou.app.telegram.model.forwardedLabel
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Poll
import com.telegramyou.app.ui.components.personShape
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.AddReaction
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import com.telegramyou.app.ui.common.rememberTextCopier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import com.telegramyou.app.telegram.model.ChatMessage
import com.telegramyou.app.telegram.model.SendState
import com.telegramyou.app.telegram.model.MessageContentType
import com.telegramyou.app.telegram.model.LinkPreview
import com.telegramyou.app.telegram.model.MessageReaction
import com.telegramyou.app.ui.components.AvatarBubble
import com.telegramyou.app.ui.media.FileTransfer
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

// One message as the conversation draws it: the bubble, what it quotes, its reactions and link preview, and the details dialog behind it.

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MessageBubble(
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
    onPhotoOpened: () -> Unit,
    onVideoOpened: () -> Unit,
    /** Files in flight, by id — usually empty. See ChatUiState.transfers. */
    transfers: Map<Int, FileTransfer>,
    /** Every photo of this message's album when it leads one; see AlbumGrid. */
    album: List<ChatMessage>? = null,
    onAlbumPhotoVisible: (ChatMessage) -> Unit = {},
    onAlbumPhotoOpened: (ChatMessage) -> Unit = {},
    onMention: (String) -> Unit = {},
    onHashtag: (String) -> Unit = {},
    onPinToggled: () -> Unit = {},
    /** Our answer to a poll, by the options' positions; empty takes it back. */
    onVote: (Set<Int>) -> Unit = {},
    /** One of a bot's buttons under this message. */
    onButton: (InlineButton) -> Unit = {}
) {
    val outgoing = message.isOutgoing
    var menuOpen by remember { mutableStateOf(false) }
    // Settings → For geeks, which adds to this bubble's gestures and menu.
    val geeks = LocalGeekSettings.current
    var detailsOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val mediaScope = rememberCoroutineScope()
    if (detailsOpen) {
        MessageDetailsDialog(message = message, onDismiss = { detailsOpen = false })
    }

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
            // 36dp, up from 28: at 28 a person's shape — a clover, a
            // hexagon — was too small to read as anything but a stray
            // mark, which is how it was described. At the bubble's bottom,
            // against the last message of a run, as Telegram places it.
            Box(modifier = Modifier.width(44.dp), contentAlignment = Alignment.BottomStart) {
                if (isLastInRun) {
                    AvatarBubble(
                        title = message.senderName.orEmpty().ifBlank { "?" },
                        seed = message.senderId ?: message.chatId,
                        size = 36.dp,
                        shape = personShape(message.senderId ?: message.chatId),
                        photoPath = message.senderPhotoPath
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
        }
        val isSticker = message.contentType == MessageContentType.Sticker && message.sticker != null
        val isVideoNote = message.contentType == MessageContentType.VideoNote && message.video != null
        // What stands on the conversation with no bubble round it.
        val standsAlone = isSticker || isVideoNote
        Box {
            WithInlineKeyboard(rows = message.inlineKeyboard, outgoing = outgoing, onPress = onButton) {
            Surface(
            shape = shape,
            color = when {
                // Selected wins over the sender's own colour: which messages
                // are about to be acted on has to be readable at a glance,
                // and on an outgoing bubble the ordinary primary fill is
                // already the loudest thing on screen.
                isSelected -> MaterialTheme.colorScheme.tertiaryContainer
                // A sticker stands on the conversation itself, as it does in
                // every Telegram client: it is its own shape, and a bubble
                // around it would be a frame round a picture of a frame.
                // A round video message likewise: the circle is the message.
                standsAlone -> Color.Transparent
                outgoing -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.surfaceContainerHighest
            },
            // No shadow. Material 3 gives depth by tone — the bubble's fill
            // against the conversation — and the composer below dropped its
            // shadow for the same reason; a bubble casting one read as a
            // card lifted off the page, which a message is not.
            shadowElevation = 0.dp,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .combinedClickable(
                    // Once a selection is up, a tap adds to it. Opening the
                    // menu on a plain tap the rest of the time would fire on
                    // every scroll that ends on a bubble.
                    onClick = { if (isSelecting) onSelect() },
                    onLongClick = { if (isSelecting) onSelect() else menuOpen = true },
                    // Only when one is chosen: a double-tap handler makes every
                    // single tap wait to see whether a second is coming.
                    onDoubleClick = when (geeks.doubleTap) {
                        DoubleTapAction.Nothing -> null
                        DoubleTapAction.React -> ({ if (!isSelecting) onReactionToggled(QUICK_REACTION) })
                        DoubleTapAction.Reply -> ({ if (!isSelecting) onReply() })
                        DoubleTapAction.Copy -> ({ if (!isSelecting) onCopy() })
                    }
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
                // Where a forward came from, above it — without this a
                // forwarded message read as the sender's own words.
                message.forwardedFrom?.let { origin ->
                    Text(
                        forwardedLabel(origin),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (outgoing) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                }
                when (message.contentType) {
                    MessageContentType.Sticker -> {
                        val sticker = message.sticker
                        if (sticker != null) {
                            StickerView(sticker, size = STICKER_SIZE)
                        } else {
                            Text(message.text)
                        }
                    }
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
                    MessageContentType.Photo -> if (album != null) {
                        AlbumGrid(
                            photos = album,
                            onVisible = onAlbumPhotoVisible,
                            onOpen = onAlbumPhotoOpened,
                            captionColor = if (outgoing) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        PhotoMessage(
                            // Flush with the bubble's top as well as its sides,
                            // unless a name or a quote sits above it.
                            bleedTop = message.replyToId == null &&
                                !(!outgoing && isFirstInRun && sender != null),
                            transfer = message.photoFileId?.let { transfers[it] },
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
                                // As a photo: flush with the top unless a name
                                // or a quote sits above it.
                                bleedTop = message.replyToId == null &&
                                    !(!outgoing && isFirstInRun && sender != null),
                                // Either file can be the one moving: the
                                // poster on sight, the video when asked for.
                                transfer = video.fileId?.let { transfers[it] }
                                    ?: video.thumbFileId?.let { transfers[it] },
                                onPosterVisible = onPhotoVisible,
                                onOpen = onVideoOpened
                            )
                        }
                    }
                    MessageContentType.Animation -> {
                        val gif = message.video
                        if (gif == null) {
                            Text(
                                message.text.ifBlank { "GIF" },
                                color = if (outgoing) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        } else {
                            AnimationMessage(
                                gif = gif,
                                caption = message.text,
                                outgoing = outgoing,
                                bleedTop = message.replyToId == null &&
                                    !(!outgoing && isFirstInRun && sender != null),
                                transfer = gif.fileId?.let { transfers[it] },
                                onVisible = onPhotoVisible,
                                onOpen = onVideoOpened
                            )
                        }
                    }
                    MessageContentType.VideoNote -> {
                        val note = message.video
                        if (note == null) {
                            Text(
                                "Video message",
                                color = if (outgoing) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        } else {
                            VideoNoteMessage(
                                note = note,
                                transfer = note.fileId?.let { transfers[it] },
                                onVisible = onPhotoVisible
                            )
                        }
                    }
                    MessageContentType.Poll -> {
                        val poll = message.poll
                        if (poll != null) {
                            PollMessage(poll = poll, outgoing = outgoing, onVote = onVote)
                        } else {
                            Text(message.text)
                        }
                    }
                    MessageContentType.Audio -> {
                        val audio = message.audio
                        if (audio != null) {
                            AudioMessage(
                                audio = audio,
                                outgoing = outgoing,
                                state = voiceState,
                                progress = voiceProgress,
                                onToggle = onVoiceToggled
                            )
                        } else {
                            Text(message.text.ifBlank { "Audio" })
                        }
                        if (message.text.isNotBlank() && audio != null) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                message.text,
                                color = if (outgoing) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface
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
                    else -> FormattedText(
                        text = message.text,
                        entities = message.entities,
                        color = if (outgoing) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
                        linkColor = if (outgoing) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.primary,
                        onMention = onMention,
                        onHashtag = onHashtag
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
                    if (outgoing && !standsAlone) MaterialTheme.colorScheme.onPrimary
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
                        if (geeks.showSeconds) timeWithSeconds(message.date) ?: message.timeLabel
                        else message.timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = footnote
                    )
                    if (outgoing) {
                        Spacer(Modifier.width(4.dp))
                        // Material ships both ticks, so there is nothing to draw
                        // by hand: one for sent, two for read.
                        // A clock while it is on its way — a photo uploading
                        // spends seconds there — and the error mark if the
                        // server refused it, which used to wear a tick.
                        Icon(
                            imageVector = when {
                                message.sendState == SendState.Failed -> Icons.Rounded.ErrorOutline
                                message.sendState == SendState.Pending -> Icons.Rounded.Schedule
                                message.isRead -> Icons.Rounded.DoneAll
                                else -> Icons.Rounded.Done
                            },
                            contentDescription = when {
                                message.sendState == SendState.Failed -> "Not sent"
                                message.sendState == SendState.Pending -> "Sending"
                                message.isRead -> "Read"
                                else -> "Sent"
                            },
                            tint = if (message.sendState == SendState.Failed) {
                                MaterialTheme.colorScheme.error
                            } else {
                                footnote
                            },
                            modifier = Modifier.size(14.dp)
                        )
                    }
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
                    text = { Text(if (message.isPinned) "Unpin" else "Pin") },
                    leadingIcon = { Icon(Icons.Rounded.PushPin, contentDescription = null) },
                    onClick = {
                        onPinToggled()
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
                // Settings → For geeks → Save and copy media, for a file that
                // is on this phone already: nothing is fetched to save it.
                val mediaPath = message.photoPath ?: message.video?.path
                if (geeks.saveMedia && mediaPath != null && MediaActions.canSaveToDownloads) {
                    DropdownMenuItem(
                        text = { Text("Save to Downloads") },
                        leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            val mime = if (message.photoPath != null) "image/jpeg" else "video/mp4"
                            mediaScope.launch {
                                val saved = withContext(Dispatchers.IO) {
                                    MediaActions.saveToDownloads(context, mediaPath, mime)
                                }
                                Toast.makeText(
                                    context,
                                    if (saved) "Saved to Downloads" else "Could not save",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                }
                val photoPath = message.photoPath
                if (geeks.saveMedia && photoPath != null) {
                    DropdownMenuItem(
                        text = { Text("Copy photo") },
                        leadingIcon = { Icon(Icons.Rounded.Image, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            mediaScope.launch {
                                val copied = withContext(Dispatchers.IO) {
                                    MediaActions.copyPhoto(context, photoPath)
                                }
                                if (!copied) {
                                    Toast.makeText(context, "Could not copy the photo", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
                if (geeks.messageDetails) {
                    DropdownMenuItem(
                        text = { Text("Details") },
                        leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            detailsOpen = true
                        }
                    )
                }
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
 * Settings → For geeks → Message details: when it was sent, to the second,
 * and the ids Telegram knows it by — what a bug report or a bot needs, and
 * what no bubble shows. Every line can be copied at once.
 */
@Composable
internal fun MessageDetailsDialog(message: ChatMessage, onDismiss: () -> Unit) {
    val copy = rememberTextCopier()
    val lines = buildList {
        add("Sent" to fullDate(message.date))
        add("Message ID" to message.id.toString())
        add("Chat ID" to message.chatId.toString())
        message.senderName?.takeIf { it.isNotBlank() }?.let { add("From" to it) }
        message.senderId?.let { add("Sender ID" to it.toString()) }
        if (message.isEdited) add("Edited" to "Yes")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Message details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                lines.forEach { (label, value) ->
                    Column {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                copy(lines.joinToString("\n") { "${it.first}: ${it.second}" })
                onDismiss()
            }) { Text("Copy") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

/** 14:03:27, from a message's epoch seconds; null when it has none. */
internal fun timeWithSeconds(epochSeconds: Long): String? =
    if (epochSeconds <= 0) null
    else java.time.Instant.ofEpochSecond(epochSeconds).atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))

/** Fri 25 Sep 2026, 14:03:27 — in the phone's own language for the day and month. */
internal fun fullDate(epochSeconds: Long): String =
    java.time.Instant.ofEpochSecond(epochSeconds).atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm:ss"))

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
internal fun ReactionRow(
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
 * The quoted block inside a bubble.
 *
 * [text] is null when the original falls outside the loaded window, which is
 * ordinary for a reply to something old — the block still shows, because the
 * fact that this is a reply matters even when the quote cannot be recovered.
 */
@Composable
internal fun QuotedMessage(sender: String?, text: String?, onTint: Color) {
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
internal fun LinkPreviewCard(preview: LinkPreview, outgoing: Boolean) {
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

/** How big a sticker is drawn in the conversation. */
internal val STICKER_SIZE = 160.dp

/** The room the words in a bubble keep from its edges. */
internal val BUBBLE_PADDING_H = 14.dp
internal val BUBBLE_PADDING_V = 10.dp

/**
 * Lays this out [horizontal] wider on both sides and [top] higher than the
 * space it was given, so it reaches past the padding of what holds it — a
 * photo out to the edges of its bubble, which clips it to its own shape.
 */
internal fun Modifier.bleed(horizontal: Dp, top: Dp): Modifier = layout { measurable, constraints ->
    val side = horizontal.roundToPx()
    val up = top.roundToPx()
    val widened = constraints.copy(
        minWidth = constraints.minWidth + 2 * side,
        maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + 2 * side else constraints.maxWidth
    )
    val placeable = measurable.measure(widened)
    layout(placeable.width - 2 * side, (placeable.height - up).coerceAtLeast(0)) {
        placeable.place(-side, -up)
    }
}
