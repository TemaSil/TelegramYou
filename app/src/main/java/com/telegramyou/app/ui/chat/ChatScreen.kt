package com.telegramyou.app.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.AttachmentDraft
import com.telegramyou.app.telegram.model.ChatDetail
import com.telegramyou.app.telegram.model.ChatMessage
import com.telegramyou.app.telegram.model.MessageContentType
import com.telegramyou.app.ui.components.AvatarBubble
import com.telegramyou.app.ui.theme.BubbleIncomingShape
import com.telegramyou.app.ui.theme.BubbleOutgoingShape
import com.telegramyou.app.ui.theme.ComposerShape
import com.telegramyou.app.ui.theme.DeepInk
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: Long,
    repository: TelegramRepository,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<ChatDetail?>(null) }
    var draft by remember { mutableStateOf("") }
    var pendingAttachment by remember { mutableStateOf<AttachmentDraft?>(null) }
    val listState = rememberLazyListState()

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        pendingAttachment = AttachmentDraft.Files(
            uris = uris.map { it.toString() },
            names = uris.map { it.lastPathSegment?.substringAfterLast('/') ?: "file" }
        )
    }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        pendingAttachment = AttachmentDraft.Photos(uris.map { it.toString() })
    }

    LaunchedEffect(chatId) {
        detail = repository.openChat(chatId)
    }

    LaunchedEffect(detail?.messages?.size) {
        val size = detail?.messages?.size ?: 0
        if (size > 0) listState.animateScrollToItem(size - 1)
    }

    val chat = detail?.chat

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (chat != null) {
                            AvatarBubble(title = chat.title, seed = chat.avatarColor, size = 40.dp)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(chat.title, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text(
                                    text = when {
                                        detail?.isTyping == true -> "typing…"
                                        else -> detail?.memberCountLabel ?: ""
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (detail?.isTyping == true) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
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
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val messages = detail?.messages.orEmpty()
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
                        isFirstInRun = endsRun(previous, message)
                    )
                }
            }

            AnimatedVisibility(
                visible = pendingAttachment != null,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut()
            ) {
                AttachmentChip(
                    draft = pendingAttachment,
                    onClear = { pendingAttachment = null }
                )
            }

            ComposerBar(
                value = draft,
                onValueChange = { draft = it },
                onAttachFile = { filePicker.launch(arrayOf("*/*")) },
                onAttachPhoto = { photoPicker.launch("image/*") },
                onSend = {
                    val text = draft
                    val attachment = pendingAttachment
                    if (text.isBlank() && attachment == null) return@ComposerBar
                    scope.launch {
                        repository.sendMessage(chatId, text, attachment)
                        draft = ""
                        pendingAttachment = null
                        detail = repository.openChat(chatId)
                    }
                }
            )
        }
    }
}

/** True when [message] belongs to a different day than [previous]. */
private fun startsNewDay(previous: ChatMessage?, message: ChatMessage): Boolean {
    if (message.date <= 0L) return false
    if (previous == null) return true
    return !sameDay(previous.date, message.date)
}

private fun sameDay(a: Long, b: Long): Boolean {
    val first = Calendar.getInstance().apply { timeInMillis = a * 1000L }
    val second = Calendar.getInstance().apply { timeInMillis = b * 1000L }
    return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
        first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
}

/**
 * True when [message] is the last of its run — the next one comes from the
 * other side, sits more than five minutes later, or does not exist.
 */
private fun endsRun(message: ChatMessage?, next: ChatMessage?): Boolean {
    if (message == null || next == null) return true
    if (message.isOutgoing != next.isOutgoing) return true
    if (message.date <= 0L || next.date <= 0L) return true
    return next.date - message.date > 5 * 60
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

private fun dayLabel(date: Long): String {
    if (date <= 0L) return ""
    val now = System.currentTimeMillis() / 1000
    return when {
        sameDay(date, now) -> "Today"
        sameDay(date, now - 24 * 60 * 60) -> "Yesterday"
        else -> SimpleDateFormat("d MMMM", Locale.getDefault())
            .format(Date(date * 1000L))
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    isLastInRun: Boolean,
    isFirstInRun: Boolean
) {
    val outgoing = message.isOutgoing
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
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = shape,
            color = if (outgoing) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceContainerHighest,
            shadowElevation = 1.dp,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (!outgoing && isFirstInRun && !message.senderName.isNullOrBlank()) {
                    Text(
                        message.senderName,
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
                Spacer(Modifier.height(4.dp))
                Text(
                    message.timeLabel + if (outgoing && message.isRead) " ✓✓" else if (outgoing) " ✓" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = (if (outgoing) DeepInk else MaterialTheme.colorScheme.onSurface).copy(alpha = 0.55f),
                    modifier = Modifier.align(Alignment.End)
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (draft is AttachmentDraft.Photos) Icons.Rounded.Image else Icons.Rounded.AttachFile,
            contentDescription = null
        )
        Spacer(Modifier.width(8.dp))
        Text(label, modifier = Modifier.weight(1f), maxLines = 1)
        androidx.compose.material3.TextButton(onClick = onClear) {
            Text("Clear")
        }
    }
}

@Composable
private fun ComposerBar(
    value: String,
    onValueChange: (String) -> Unit,
    onAttachFile: () -> Unit,
    onAttachPhoto: () -> Unit,
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
            IconButton(onClick = onAttachFile) {
                Icon(Icons.Rounded.AttachFile, contentDescription = "Attach file")
            }
            IconButton(onClick = onAttachPhoto) {
                Icon(Icons.Rounded.Image, contentDescription = "Attach photo")
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
