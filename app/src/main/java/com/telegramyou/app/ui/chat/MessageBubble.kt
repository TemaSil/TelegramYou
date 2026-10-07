package com.telegramyou.app.ui.chat

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.LocalContentColor
import com.telegramyou.app.telegram.model.ContactContent
import com.telegramyou.app.telegram.model.ReadInfo
import com.telegramyou.app.telegram.model.readLine
import androidx.compose.ui.semantics.semantics
import com.telegramyou.app.telegram.model.customEmojiIdOf
import androidx.compose.material3.IconButton
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import com.telegramyou.app.telegram.model.ReactionOption
import com.telegramyou.app.ui.icons.Symbols
import com.telegramyou.app.settings.MessageExtra
import com.telegramyou.app.telegram.model.downloadedFileId
import androidx.compose.foundation.border
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import android.widget.Toast
import com.telegramyou.app.settings.QUICK_REACTION
import com.telegramyou.app.settings.DoubleTapAction
import com.telegramyou.app.settings.LocalGeekSettings
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import com.telegramyou.app.telegram.model.InlineButton
import com.telegramyou.app.telegram.model.forwardedLabel
import com.telegramyou.app.ui.components.personShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import com.telegramyou.app.telegram.model.jumboEmojiCount
import androidx.compose.ui.semantics.selected
import androidx.compose.material3.Checkbox
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.material3.CircularProgressIndicator
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
    /**
     * The reactions offered over this message's menu, with Telegram's
     * animations where they have arrived; the full list is behind the arrow.
     */
    quickReactions: List<ReactionOption> = emptyList(),
    /** "Add to GIFs", offered on a GIF. */
    onSaveGif: () -> Unit = {},
    /** A track lined up in the player: after this one, or at the end of Up next. */
    onPlayNext: () -> Unit = {},
    onAddToQueue: () -> Unit = {},
    /** A contact card's View (their profile) and Add (to contacts). */
    onContactOpen: (ContactContent) -> Unit = {},
    onContactAdd: (ContactContent) -> Unit = {},
    isSelected: Boolean,
    isSelecting: Boolean,
    onSelect: () -> Unit,
    voiceState: VoiceState,
    onVoiceToggled: () -> Unit,
    voiceProgress: Float,
    onVoiceSeek: (Float) -> Unit,
    /** A file tapped: opened in whichever app reads it. */
    onDocumentOpen: () -> Unit = {},
    /** The file is being fetched before it can be opened. */
    documentOpening: Boolean = false,
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
    onButton: (InlineButton) -> Unit = {},
    /** The menu is opening: what it offers is asked for now; see MessagePermissions. */
    onMenuOpened: () -> Unit = {},
    /** Forward this message, from its menu. */
    onForward: () -> Unit = {},
    /** A round video message was started: its sender sees it watched. */
    onContentOpened: () -> Unit = {},
    /** Settings → For geeks → More in a message's menu; see MessageExtra. */
    onExtra: (MessageExtra) -> Unit = {},
    /** Its text in the phone's language; see TranslationDialog. */
    onTranslate: () -> Unit = {},
    /** A channel post's comments opened (2.0); see commentCount. */
    onOpenComments: () -> Unit = {},
    /** Answered with part of its text quoted, from [start] to [end] (2.0). */
    onQuote: (start: Int, end: Int) -> Unit = { _, _ -> },
    /**
     * Its link copied, where the chat has links — a group or a channel
     * (2.0); null in a private chat, where Telegram gives none.
     */
    onCopyLink: (() -> Unit)? = null,
    /** Reported to Telegram (2.0); null for one's own messages. */
    onReport: (() -> Unit)? = null,
    /** Who has read it, once asked; see ReadInfo (2.0). */
    readInfo: ReadInfo? = null
) {
    val outgoing = message.isOutgoing
    var menuOpen by remember { mutableStateOf(false) }
    var quoteOpen by remember { mutableStateOf(false) }
    var viewersOpen by remember { mutableStateOf(false) }
    val seen = (readInfo as? ReadInfo.SeenBy)?.viewers.orEmpty()
    if (viewersOpen && seen.isNotEmpty()) {
        SeenByDialog(seen, onDismiss = { viewersOpen = false })
    }
    if (quoteOpen) {
        QuoteDialog(
            text = message.text,
            onQuote = { start, end ->
                quoteOpen = false
                onQuote(start, end)
            },
            onDismiss = { quoteOpen = false }
        )
    }
    // The bubble's long-press, handed to a photo, video or GIF inside it as
    // well: those are clickable themselves, so they take the press before
    // the bubble sees it, and a long one used to open the viewer.
    val onBubbleLongClick = {
        if (isSelecting) {
            onSelect()
        } else {
            menuOpen = true
            onMenuOpened()
        }
    }
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

    // Round as Appearance says, the tail as tight as ever unless the
    // corners themselves go tighter.
    val corner = LocalChatStyle.current.bubbleCorners.dp
    val tail = minOf(6.dp, corner)
    // Tight corners where a run continues, the tail only on its last message.
    val shape = RoundedCornerShape(
        topStart = if (outgoing || isFirstInRun) corner else tail,
        topEnd = if (!outgoing || isFirstInRun) corner else tail,
        bottomStart = if (outgoing || isLastInRun) corner else tail,
        bottomEnd = if (!outgoing || isLastInRun) corner else tail
    )
    // Selecting, the whole row is the target — Material's list selection:
    // a tap anywhere across the message's height, not only on the bubble,
    // toggles it, and a chosen row is washed in the primary colour behind a
    // leading checkbox. The wash sits under the swipe offset, so it stays
    // put; there is no swiping to reply while selecting.
    val selectionWash = MaterialTheme.colorScheme.primary.copy(alpha = SELECTED_WASH)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelecting) {
                    Modifier
                        .background(if (isSelected) selectionWash else Color.Transparent)
                        .clickable(onClick = onSelect)
                        .semantics { selected = isSelected }
                } else {
                    Modifier
                }
            )
            .offset { IntOffset(offset.roundToInt(), 0) }
            .pointerInput(message.id, isSelecting) {
                if (isSelecting) return@pointerInput
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
        // At the start for everyone's messages, as Telegram and Material's
        // lists both put it, sliding in as the selection begins. Display
        // only: the row takes the tap.
        AnimatedVisibility(
            visible = isSelecting,
            enter = expandHorizontally() + fadeIn(),
            exit = shrinkHorizontally() + fadeOut(),
            modifier = Modifier.align(Alignment.CenterVertically)
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = null,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
        // Pushes an outgoing message to the far edge now that the checkbox
        // can sit at the near one.
        if (outgoing) Spacer(Modifier.weight(1f))
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
        // One to three emoji and nothing else: drawn large, as Telegram does,
        // where no animation came for them.
        val jumbo = remember(message.text, message.contentType, message.entities) {
            if (message.contentType == MessageContentType.Text && message.entities.isEmpty()) {
                jumboEmojiCount(message.text)
            } else {
                0
            }
        }
        // What stands on the conversation with no bubble round it.
        val standsAlone = isSticker || isVideoNote || jumbo > 0
        // A photo, video, GIF or album with nothing written about it is the
        // message by itself: no bubble colour round it, its own corners, the
        // time on it. Words — a caption, a quote, "Forwarded from" — bring the
        // bubble back, and the picture fills its top as before.
        val frameless = message.replyToId == null && message.forwardedFrom == null && when (message.contentType) {
            // "Photo", "Video" and "GIF" are what a message with no caption
            // is called, not words anyone wrote.
            MessageContentType.Photo -> if (album != null) {
                album.none { it.text.isNotBlank() && it.text != "Photo" }
            } else {
                message.text.isBlank() || message.text == "Photo"
            }
            MessageContentType.Video -> message.video != null && (message.text.isBlank() || message.text == "Video")
            MessageContentType.Animation -> message.video != null && (message.text.isBlank() || message.text == "GIF")
            else -> false
        }
        // The time, "edited" and the ticks: under the words in a bubble, or
        // on the picture in a scrim chip when the picture is the message —
        // one style for everything drawn over media, as the gallery has it.
        val footer: @Composable (onMedia: Boolean, rowModifier: Modifier) -> Unit = { onMedia, rowModifier ->
            val footnote = when {
                onMedia -> Color.White
                outgoing && !standsAlone -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.55f)
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = rowModifier
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
                            message.sendState == SendState.Failed -> Symbols.ErrorOutlineFilled
                            message.sendState == SendState.Pending -> Symbols.Schedule
                            message.isRead -> Symbols.DoneAll
                            else -> Symbols.Done
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
        Box {
            WithInlineKeyboard(rows = message.inlineKeyboard, outgoing = outgoing, onPress = onButton) {
            Surface(
            shape = shape,
            // Its own colour even when selected: the checkbox and the row's
            // wash say that now. Recolouring the bubble made a chosen
            // message look like somebody else's.
            color = when {
                // A sticker stands on the conversation itself, as it does in
                // every Telegram client: it is its own shape, and a bubble
                // around it would be a frame round a picture of a frame.
                // A round video message likewise: the circle is the message.
                standsAlone || frameless -> Color.Transparent
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
                    onLongClick = onBubbleLongClick,
                    // Only when one is chosen: a double-tap handler makes every
                    // single tap wait to see whether a second is coming.
                    onDoubleClick = when (geeks.doubleTap) {
                        DoubleTapAction.Nothing -> null
                        DoubleTapAction.React -> ({ if (!isSelecting) onReactionToggled(QUICK_REACTION) })
                        DoubleTapAction.Reply -> ({ if (!isSelecting) onReply() })
                        // Nothing to copy from a photo or a sticker with no
                        // caption, and copying "" emptied the clipboard.
                        DoubleTapAction.Copy -> ({ if (!isSelecting && message.text.isNotBlank()) onCopy() })
                    }
                )
        ) {
            Column(
                modifier = if (frameless) Modifier else Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
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
                // Only where several people write — the same test as the
                // avatar beside the run. In a private chat the header already
                // says who, and TDLib names the sender of every message, so
                // the name sat over each of the other person's runs.
                if (showAvatar && !outgoing && isFirstInRun && sender != null) {
                    Text(
                        sender,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold,
                        // Over the conversation itself when there is no bubble.
                        modifier = if (frameless) Modifier.padding(start = 4.dp, bottom = 2.dp) else Modifier
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
                Box {
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .clickable(onClick = onDocumentOpen)
                                .semantics { contentDescription = "Open ${message.fileName ?: "file"}" }
                        ) {
                            if (documentOpening) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Symbols.Description, contentDescription = null)
                            }
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
                            onOpen = { if (isSelecting) onSelect() else onAlbumPhotoOpened(it) },
                            onLongClick = onBubbleLongClick,
                            captionColor = if (outgoing) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        PhotoMessage(
                            framed = !frameless,
                            // Flush with the bubble's top as well as its sides,
                            // unless a name or a quote sits above it.
                            bleedTop = message.replyToId == null &&
                                !(!outgoing && isFirstInRun && sender != null),
                            transfer = message.photoFileId?.let { transfers[it] },
                            path = message.photoPath,
                            mini = message.photoMini,
                            aspect = message.photoAspect,
                            caption = message.text,
                            outgoing = outgoing,
                            onVisible = onPhotoVisible,
                            onOpen = { if (isSelecting) onSelect() else onPhotoOpened() },
                            onLongClick = onBubbleLongClick,
                            originId = message.id
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
                                framed = !frameless,
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
                                onOpen = { if (isSelecting) onSelect() else onVideoOpened() },
                                onLongClick = onBubbleLongClick,
                                originId = message.id
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
                                framed = !frameless,
                                gif = gif,
                                caption = message.text,
                                outgoing = outgoing,
                                bleedTop = message.replyToId == null &&
                                    !(!outgoing && isFirstInRun && sender != null),
                                transfer = gif.fileId?.let { transfers[it] },
                                onVisible = onPhotoVisible,
                                onOpen = { if (isSelecting) onSelect() else onVideoOpened() },
                                onLongClick = onBubbleLongClick,
                                originId = message.id
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
                                onVisible = onPhotoVisible,
                                onPlayed = onContentOpened
                            )
                        }
                    }
                    MessageContentType.Contact -> {
                        val contact = message.contact
                        if (contact != null) {
                            ContactCard(
                                contact = contact,
                                outgoing = outgoing,
                                onOpen = { onContactOpen(contact) },
                                onAdd = { onContactAdd(contact) }
                            )
                        } else {
                            Text(message.text)
                        }
                    }
                    MessageContentType.Location -> {
                        val location = message.location
                        if (location != null) LocationCard(location, outgoing) else Text(message.text)
                    }
                    MessageContentType.Poll -> {
                        val poll = message.poll
                        if (poll != null) {
                            PollMessage(poll = poll, outgoing = outgoing, onVote = onVote)
                        } else {
                            Text(message.text)
                        }
                    }
                    // A column: this branch sits in the bubble's Box, and
                    // without one a track's caption ("🎵 Разум") was drawn
                    // over the track rather than under it.
                    MessageContentType.Audio -> Column {
                        val audio = message.audio
                        if (audio != null) {
                            AudioMessage(
                                audio = audio,
                                outgoing = outgoing,
                                state = voiceState,
                                progress = voiceProgress,
                                onToggle = onVoiceToggled,
                                onSeek = onVoiceSeek
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
                    else -> if (jumbo > 0) {
                        Text(
                            message.text.trim(),
                            fontSize = when (jumbo) {
                                1 -> 56.sp
                                2 -> 44.sp
                                else -> 36.sp
                            },
                            lineHeight = 64.sp
                        )
                    } else FormattedText(
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
                if (frameless) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = Color.Black.copy(alpha = 0.45f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                    ) {
                        footer(true, Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
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
                        outgoing = outgoing,
                        onToggle = onReactionToggled
                    )
                }
                if (!frameless) {
                    Spacer(Modifier.height(4.dp))
                    footer(false, Modifier.align(Alignment.End))
                }
                // A channel post's comments, along the bottom of the post as
                // Telegram puts them: a stock text button, the whole width,
                // over a hairline that says it is the post's and not a reply.
                message.commentCount?.let { count ->
                    HorizontalDivider(
                        modifier = Modifier.padding(top = 6.dp),
                        color = LocalContentColor.current.copy(alpha = 0.12f)
                    )
                    TextButton(
                        onClick = onOpenComments,
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Symbols.Forum, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(commentsLabel(count), modifier = Modifier.weight(1f))
                    }
                }
            }
            }
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
            ) {
                // Who has read it, over everything else, as the official
                // client has it (2.0) — one's own messages only; a group's
                // list opens from it.
                val readText = readLine(readInfo) { date -> readTimeLabel(context, date) }
                if (outgoing && readText != null) {
                    val seenBy = (readInfo as? ReadInfo.SeenBy)?.viewers.orEmpty()
                    DropdownMenuItem(
                        text = { Text(readText, style = MaterialTheme.typography.labelLarge) },
                        leadingIcon = { Icon(Symbols.DoneAll, contentDescription = null) },
                        enabled = seenBy.size > 1,
                        onClick = {
                            menuOpen = false
                            viewersOpen = true
                        }
                    )
                    HorizontalDivider()
                }
                // The reactions first, as Telegram and Google Messages put
                // them: the most used in a row, and the arrow to all of them.
                if (quickReactions.isNotEmpty()) {
                    QuickReactions(
                        options = quickReactions.take(QUICK_REACTIONS),
                        chosen = message.reactions.firstOrNull { it.isChosen }?.emoji,
                        onPick = { emoji ->
                            menuOpen = false
                            onReactionToggled(emoji)
                        },
                        onMore = {
                            menuOpen = false
                            onReact()
                        }
                    )
                    HorizontalDivider()
                }
                DropdownMenuItem(
                    text = { Text("Reply") },
                    leadingIcon = { Icon(Symbols.Reply, contentDescription = null) },
                    onClick = {
                        onReply()
                        menuOpen = false
                    }
                )
                // Part of it, rather than all of it (2.0): the words chosen
                // in a dialog, and the reply shows those over itself.
                if (message.text.length > QUOTE_WORTH_IT) {
                    DropdownMenuItem(
                        text = { Text("Quote") },
                        leadingIcon = { Icon(Symbols.FormatQuote, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            quoteOpen = true
                        }
                    )
                }
                // Here as well as on the selection bar: forwarding one message
                // was a long press, then Select, then the bar — three steps
                // nobody found, and the report was that forwarding did not
                // work at all.
                if (message.contentType == MessageContentType.Animation && message.video != null) {
                    DropdownMenuItem(
                        text = { Text("Add to GIFs") },
                        leadingIcon = { Icon(Symbols.Gif, contentDescription = null) },
                        onClick = {
                            onSaveGif()
                            menuOpen = false
                        }
                    )
                }
                // A track lined up from where it is, whatever is playing —
                // the queue is otherwise one chat's music.
                if (message.audio != null) {
                    DropdownMenuItem(
                        text = { Text("Play next") },
                        leadingIcon = { Icon(Symbols.SkipNext, contentDescription = null) },
                        onClick = {
                            onPlayNext()
                            menuOpen = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to queue") },
                        leadingIcon = { Icon(Symbols.QueueMusic, contentDescription = null) },
                        onClick = {
                            onAddToQueue()
                            menuOpen = false
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Forward") },
                    leadingIcon = { Icon(Symbols.Forward, contentDescription = null) },
                    onClick = {
                        onForward()
                        menuOpen = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(if (message.isPinned) "Unpin" else "Pin") },
                    leadingIcon = { Icon(Symbols.PushPinFilled, contentDescription = null) },
                    onClick = {
                        onPinToggled()
                        menuOpen = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Select") },
                    leadingIcon = {
                        Icon(Symbols.CheckCircleFilled, contentDescription = null)
                    },
                    onClick = {
                        onSelect()
                        menuOpen = false
                    }
                )
                if (quickReactions.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("React") },
                        leadingIcon = {
                            Icon(Symbols.AddReaction, contentDescription = null)
                        },
                        onClick = {
                            onReact()
                            menuOpen = false
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Copy") },
                    leadingIcon = { Icon(Symbols.ContentCopy, contentDescription = null) },
                    onClick = {
                        onCopy()
                        menuOpen = false
                    }
                )
                if (message.text.isNotBlank()) {
                    DropdownMenuItem(
                        text = { Text("Translate") },
                        leadingIcon = { Icon(Symbols.Translate, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onTranslate()
                        }
                    )
                }
                if (onCopyLink != null) {
                    DropdownMenuItem(
                        text = { Text("Copy link") },
                        leadingIcon = { Icon(Symbols.Link, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onCopyLink()
                        }
                    )
                }
                if (onReport != null) {
                    DropdownMenuItem(
                        text = { Text("Report") },
                        leadingIcon = { Icon(Symbols.ErrorOutline, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onReport()
                        }
                    )
                }
                // Settings → For geeks → Save and copy media, for a file that
                // is on this phone already: nothing is fetched to save it.
                val mediaPath = message.photoPath ?: message.video?.path
                if (geeks.saveMedia && mediaPath != null && MediaActions.canSaveToDownloads) {
                    DropdownMenuItem(
                        text = { Text("Save to Downloads") },
                        leadingIcon = { Icon(Symbols.Download, contentDescription = null) },
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
                        leadingIcon = { Icon(Symbols.Image, contentDescription = null) },
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
                // Settings → For geeks → More in a message's menu: what
                // Nekogram's menu carries and a stock one does not.
                if (geeks.messageExtras) {
                    DropdownMenuItem(
                        text = { Text("Repeat") },
                        leadingIcon = { Icon(Symbols.Repeat, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onExtra(MessageExtra.Repeat)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Save to Saved Messages") },
                        leadingIcon = { Icon(Symbols.Bookmark, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onExtra(MessageExtra.SaveToSavedMessages)
                        }
                    )
                    if (message.downloadedFileId() != null) {
                        DropdownMenuItem(
                            text = { Text("Delete from this phone") },
                            leadingIcon = { Icon(Symbols.DeleteSweep, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onExtra(MessageExtra.DeleteFile)
                            }
                        )
                    }
                }
                if (geeks.messageDetails) {
                    DropdownMenuItem(
                        text = { Text("Details") },
                        leadingIcon = { Icon(Symbols.Info, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            detailsOpen = true
                        }
                    )
                }
                if (message.canBeEdited) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(Symbols.Edit, contentDescription = null) },
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
                                Symbols.Delete,
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
    outgoing: Boolean,
    onToggle: (String) -> Unit
) {
    // In the accent, as the owner asked: this account's own reaction filled
    // with it, the others in its container. Inside one of this account's
    // messages the bubble is the accent already, so there the two turn
    // round — onPrimary filled, and a wash of it.
    val colors = MaterialTheme.colorScheme
    val chosenFill = if (outgoing) colors.onPrimary else colors.primary
    val chosenText = if (outgoing) colors.primary else colors.onPrimary
    val restFill = if (outgoing) colors.onPrimary.copy(alpha = 0.18f) else colors.primaryContainer
    val restText = if (outgoing) colors.onPrimary else colors.onPrimaryContainer
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState())
    ) {
        reactions.forEach { reaction ->
            FilterChip(
                selected = reaction.isChosen,
                onClick = { onToggle(reaction.emoji) },
                label = {
                    // A custom emoji (Premium's) is its sticker; see ReactionGlyph.
                    // Larger than the label text, and with less of the chip's
                    // own side padding around it — the owner found the
                    // chips wide for what they hold. The chip stays
                    // Material's; only the room around its label is trimmed.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.trimSides(REACTION_TRIM)
                    ) {
                        ReactionGlyph(reaction.emoji, size = REACTION_GLYPH)
                        if (reaction.count > 1) {
                            Text(
                                " ${reaction.count}",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                },
                // Tighter than the default pill, so a row of chips inside a
                // bubble reads as an annotation on the message rather than a
                // second control bar.
                shape = MaterialTheme.shapes.small,
                border = null,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = restFill,
                    labelColor = restText,
                    selectedContainerColor = chosenFill,
                    selectedLabelColor = chosenText
                )
            )
        }
    }
}

/**
 * The row of reactions over a message's menu: Telegram's animation of
 * each, playing, or the emoji where none has arrived — and an arrow to the
 * full picker.
 */
@Composable
private fun QuickReactions(
    options: List<ReactionOption>,
    chosen: String?,
    onPick: (String) -> Unit,
    onMore: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        options.forEach { option ->
            ReactionCell(
                option = option,
                chosen = option.emoji == chosen,
                // Up from 30dp with the rest of the reactions: the owner
                // found every emoji in them small.
                size = 36.dp,
                onClick = { onPick(option.emoji) }
            )
        }
        IconButton(onClick = onMore, modifier = Modifier.size(40.dp)) {
            Icon(Symbols.KeyboardArrowDown, contentDescription = "More reactions")
        }
    }
}

/**
 * One reaction to choose: its animation or its emoji, ringed in the accent
 * when it is ours. One only Premium may use is dimmed, with a lock in its
 * corner, as the official client marks it.
 */
@Composable
internal fun ReactionCell(option: ReactionOption, chosen: Boolean, size: Dp, onClick: () -> Unit) {
    val custom = customEmojiIdOf(option.emoji) != null
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size + 12.dp)
            .clip(CircleShape)
            .background(if (chosen) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = when {
                    !custom -> option.emoji
                    option.needsPremium -> "Custom emoji, Premium"
                    else -> "Custom emoji"
                }
            }
    ) {
        Box(Modifier.alpha(if (option.needsPremium) 0.45f else 1f), contentAlignment = Alignment.Center) {
            val animation = option.animation
            if (animation != null) {
                // Telegram's centre animation fills only the middle of its
                // canvas — the rest is room for the effect around it — so
                // drawn one to one it came out half the size of an emoji.
                // Scaled up here as the official client does; the cell's
                // circle clips whatever reaches past it.
                StickerView(
                    animation,
                    size = size,
                    modifier = Modifier.graphicsLayer {
                        scaleX = CENTER_ANIMATION_SCALE
                        scaleY = CENTER_ANIMATION_SCALE
                    }
                )
            } else {
                ReactionGlyph(option.emoji, size = size)
            }
        }
        if (option.needsPremium) {
            Icon(
                Symbols.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(14.dp)
            )
        }
    }
}

/** How much a reaction's centre animation is enlarged to fill its cell. */
private const val CENTER_ANIMATION_SCALE = 1.7f

/** How many reactions the row over a message's menu shows before the arrow. */
private const val QUICK_REACTIONS = 6

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

/** How strongly a selected row is washed in the primary colour. */
private const val SELECTED_WASH = 0.14f

/** A reaction's emoji in its chip: up from 18dp, which read as small. */
private val REACTION_GLYPH = 22.dp

/** How much of a reaction chip's side padding goes, on each side. */
private val REACTION_TRIM = 6.dp

/**
 * Takes [each] off both sides of what this reports as its width, drawing
 * into that space: the way to narrow a chip whose padding is not a
 * parameter without drawing the chip by hand.
 */
private fun Modifier.trimSides(each: Dp): Modifier = layout { measurable, constraints ->
    val trim = each.roundToPx()
    val placeable = measurable.measure(constraints)
    layout((placeable.width - trim * 2).coerceAtLeast(0), placeable.height) {
        placeable.place(-trim, 0)
    }
}
