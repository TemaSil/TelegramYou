package com.telegramyou.app.ui.chat

import androidx.compose.material.icons.rounded.Poll
import com.telegramyou.app.ui.components.personShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Forward
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.telegramyou.app.telegram.model.ChatMessage
import com.telegramyou.app.telegram.model.ChatPreview
import com.telegramyou.app.ui.components.AvatarBubble
import java.io.File

// The sheets, dialogs and bars a conversation opens over itself: forward, attach, search in chat, selection, reactions and deleting.

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
internal fun ForwardSheet(
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
                                size = 40.dp,
                                shape = personShape(target.avatarColor),
                                photoPath = target.photoPath
                            )
                        },
                        // The sheet's tone, not the row's default — see the
                        // attachment sheet for what the mismatch looks like.
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent
                        ),
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
internal fun AttachmentSheet(
    onDismiss: () -> Unit,
    onPickPhoto: () -> Unit,
    onPickFile: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickRecent: (String) -> Unit,
    /** A poll, where the chat takes them — groups and channels. */
    onPoll: (() -> Unit)? = null
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // All the way up at once: half open, the rows past File sit under the fold.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        // The pictures somebody is most likely to send are the ones they just
        // took, and reaching them through a row called "Photo or video" is a
        // screen and a scroll away from the sheet that was supposed to be the
        // shortcut. Draws nothing without the permission for it.
        RecentPhotoCarousel(
            onPick = onPickRecent,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        // Transparent containers, all three. A ListItem paints itself
        // `surface` by default, and a ModalBottomSheet is
        // `surfaceContainerLow` — so the rows sat as three pale slabs on a
        // slightly darker sheet, with seams between them. Inside a container
        // that has already chosen a tone, the rows take it.
        val sheetRow = ListItemDefaults.colors(containerColor = Color.Transparent)
        ListItem(
            headlineContent = { Text("Photo or video") },
            supportingContent = { Text("From the gallery") },
            leadingContent = { Icon(Icons.Rounded.Image, contentDescription = null) },
            colors = sheetRow,
            modifier = Modifier.clickable(onClick = onPickPhoto)
        )
        ListItem(
            headlineContent = { Text("Camera") },
            supportingContent = { Text("Take a photo now") },
            leadingContent = { Icon(Icons.Rounded.PhotoCamera, contentDescription = null) },
            colors = sheetRow,
            modifier = Modifier.clickable(onClick = onTakePhoto)
        )
        ListItem(
            headlineContent = { Text("File") },
            supportingContent = { Text("Anything else") },
            leadingContent = { Icon(Icons.Rounded.AttachFile, contentDescription = null) },
            colors = sheetRow,
            modifier = Modifier.clickable(onClick = onPickFile)
        )
        if (onPoll != null) {
            ListItem(
                headlineContent = { Text("Poll") },
                supportingContent = { Text("A question with answers to vote on") },
                leadingContent = { Icon(Icons.Rounded.Poll, contentDescription = null) },
                colors = sheetRow,
                modifier = Modifier.clickable(onClick = onPoll)
            )
        }
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
internal fun ChatSearchField(
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
internal fun ChatSearchResults(
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
internal fun SelectionToolbar(
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
internal fun DeleteSelectionDialog(
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
internal fun ReactionPicker(
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
 * Confirms a deletion, and asks the one question Telegram asks: for me, or
 * for everyone.
 *
 * Deleting for everyone withdraws the message from the other side and cannot
 * be undone, so it is never the default action — it is offered only where the
 * server said it is permitted, and sits apart from the safe one.
 */
@Composable
internal fun DeleteMessageDialog(
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
