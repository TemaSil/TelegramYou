package com.telegramyou.app.ui.chat

import com.telegramyou.app.i18n.tr

import androidx.compose.ui.unit.Dp
import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedContent
import com.telegramyou.app.ui.auth.PhoneEntry
import com.telegramyou.app.telegram.model.TelegramUser
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import com.telegramyou.app.telegram.model.ReactionOption
import com.telegramyou.app.ui.icons.Symbols
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
import androidx.compose.foundation.lazy.items
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
    onPick: (ChatPreview) -> Unit,
    /** The account's contacts, for people the search finds with no chat yet. */
    contacts: List<TelegramUser> = emptyList(),
    onPickContact: (TelegramUser) -> Unit = {},
    /** In place of "Forward to…", for the same list put to another use — a share (2.1). */
    title: String? = null
) {
    // Expanded straight away: the search field takes the keyboard, and a
    // half-open sheet under it would leave the results off the screen.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        var query by rememberSaveable { mutableStateOf("") }
        val wanted = query.trim()
        val chats = remember(targets, wanted) {
            if (wanted.isEmpty()) targets else targets.filter { it.title.contains(wanted, ignoreCase = true) }
        }
        // Contacts only while searching, and only those without a chat in
        // the list already: the list is where people are, the search reaches
        // the rest — as Telegram's own forward search does (1.7).
        val people = remember(contacts, targets, wanted) {
            if (wanted.isEmpty()) {
                emptyList()
            } else {
                val listed = targets.map { it.title }.toSet()
                contacts.filter { contact ->
                    contact.displayName !in listed && (
                        contact.displayName.contains(wanted, ignoreCase = true) ||
                            contact.username?.contains(wanted, ignoreCase = true) == true
                        )
                }
            }
        }
        Text(
            title ?: if (count == 1) tr("Forward to…", "Переслать в…") else tr("Forward $count messages to…", "Переслать сообщения ($count) в…"),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        // The same pill as search's own field elsewhere in the app.
        SearchBarDefaults.InputField(
            query = query,
            onQueryChange = { query = it },
            onSearch = {},
            expanded = false,
            onExpandedChange = {},
            placeholder = { Text(tr("Search chats and contacts", "Поиск чатов и контактов")) },
            leadingIcon = { Icon(Symbols.Search, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(SearchBarDefaults.inputFieldShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )
        if (chats.isEmpty() && people.isEmpty()) {
            Text(
                if (wanted.isEmpty()) tr("No other chats to forward to.", "Других чатов для пересылки нет.") else tr("Nothing called that", "Ничего с таким названием"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(chats, key = { "c${it.id}" }) { target ->
                    ListItem(
                        headlineContent = { Text(target.title, maxLines = 1) },
                        leadingContent = {
                            AvatarBubble(
                                title = target.title,
                                seed = target.avatarColor,
                                size = 40.dp,
                                shape = personShape(target.avatarColor),
                                photoPath = target.photoPath,
                                savedMessages = target.isSavedMessages
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
                if (people.isNotEmpty()) {
                    item(key = "contacts-header") {
                        Text(
                            tr("Contacts", "Контакты"),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
                        )
                    }
                    items(people, key = { "u${it.id}" }) { person ->
                        ListItem(
                            headlineContent = { Text(person.displayName, maxLines = 1) },
                            supportingContent = person.username?.let { { Text("@$it", maxLines = 1) } },
                            leadingContent = {
                                AvatarBubble(
                                    title = person.displayName,
                                    seed = person.avatarColor,
                                    size = 40.dp,
                                    shape = personShape(person.avatarColor),
                                    photoPath = person.photoPath
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { onPickContact(person) }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * What the plus opens (2.2, after Google Messages): a panel in the
 * keyboard's place, as the emoji panel is, rather than a sheet over the
 * conversation — a grid of what can be sent, each a round tonal button
 * with its name.
 *
 * Expressive's own icon buttons, the medium size in their wide form, which
 * is what Messages draws there; the composer stays in view above, and the
 * plus turns into a cross to put the panel away.
 *
 * Camera hands the photo straight to the composer as a draft, the same shape
 * the gallery picker produces, so everything downstream treats the two alike.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AttachPanel(
    height: Dp,
    /** The navigation bar's height, which the panel runs under and keeps clear. */
    bottomInset: Dp,
    onPickPhoto: () -> Unit,
    onPickFile: () -> Unit,
    onTakePhoto: () -> Unit,
    onGifs: () -> Unit = {},
    onStickers: () -> Unit = {},
    /** A poll, where the chat takes them — groups and channels. */
    onPoll: (() -> Unit)? = null,
    /** Somebody's card, from this account's contacts. */
    onContact: () -> Unit = {},
    /** Where this phone is, sent once. */
    onLocation: () -> Unit = {},
    /** What is typed, sent later; null where there is nothing typed to send. */
    onSchedule: (() -> Unit)? = null
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
    ) {
        // Only the buttons (2.2): the strip of recent photos that stood
        // above them was dropped on the owner's word — Gallery is one tap
        // away and opens the whole library. Scrolls on a short screen.
        Column(
            Modifier
                .padding(bottom = bottomInset)
                .verticalScroll(rememberScrollState())
                .padding(top = 12.dp)
        ) {
            val actions = buildList {
                add(AttachAction(tr("Gallery", "Галерея"), Symbols.PhotoLibrary, onPickPhoto))
                add(AttachAction(tr("Camera", "Камера"), Symbols.PhotoCamera, onTakePhoto))
                add(AttachAction(tr("GIFs", "GIF"), Symbols.GifBox, onGifs))
                add(AttachAction(tr("Stickers", "Стикеры"), Symbols.Sticker, onStickers))
                add(AttachAction(tr("Files", "Файлы"), Symbols.AttachFile, onPickFile))
                add(AttachAction(tr("Location", "Геопозиция"), Symbols.LocationOn, onLocation))
                add(AttachAction(tr("Contacts", "Контакты"), Symbols.Person, onContact))
                if (onPoll != null) add(AttachAction(tr("Poll", "Опрос"), Symbols.Poll, onPoll))
                if (onSchedule != null) add(AttachAction(tr("Schedule send", "Отложить"), Symbols.Schedule, onSchedule))
            }
            actions.chunked(ATTACH_COLUMNS).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                    row.forEach { action -> AttachButton(action, Modifier.weight(1f)) }
                    repeat(ATTACH_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

private data class AttachAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/** One of the panel's buttons: Expressive's medium wide icon button, its name under it. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AttachButton(action: AttachAction, modifier: Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        FilledTonalIconButton(
            onClick = action.onClick,
            shape = IconButtonDefaults.mediumRoundShape,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier
                .size(IconButtonDefaults.mediumContainerSize(IconButtonDefaults.IconButtonWidthOption.Wide))
                .semantics { contentDescription = action.label }
        ) {
            Icon(action.icon, contentDescription = null, modifier = Modifier.size(IconButtonDefaults.mediumIconSize))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            action.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            // Its name is the button's; read once, from the button.
            modifier = Modifier.clearAndSetSemantics {}
        )
    }
}

/** Four to a row, as Messages has them. */
private const val ATTACH_COLUMNS = 4

/** This account's contacts, one of whom to send as a card. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContactPickerSheet(
    contacts: List<TelegramUser>,
    onPick: (TelegramUser) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            tr("Send a contact", "Отправить контакт"),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        if (contacts.isEmpty()) {
            Text(
                tr("No contacts yet.", "Контактов пока нет."),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp)
            )
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(contacts, key = { it.id }) { user ->
                ListItem(
                    headlineContent = { Text(user.displayName) },
                    supportingContent = user.phoneNumber?.takeIf { it.isNotBlank() }?.let { phone ->
                        { Text(PhoneEntry.format(phone)) }
                    },
                    leadingContent = {
                        AvatarBubble(
                            title = user.displayName,
                            seed = user.avatarColor,
                            size = 40.dp,
                            shape = personShape(user.avatarColor),
                            photoPath = user.photoPath
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onPick(user) }
                )
            }
        }
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
        placeholder = { Text(tr("Search in chat", "Поиск в чате")) },
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
                tr("Nothing in this chat matches “${search.query}”.", "В этом чате ничего не нашлось по «${search.query}»."),
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
                Icon(Symbols.Close, contentDescription = tr("Clear selection", "Снять выделение"))
            }
            // Centred on the icons, and as wide at 9 as at 10, so the buttons
            // beside it do not shift as it counts; the number itself rolls
            // up or down with the theme's spring rather than blinking over.
            AnimatedContent(
                targetState = count,
                transitionSpec = {
                    val up = targetState > initialState
                    (slideInVertically { if (up) it else -it } + fadeIn()) togetherWith
                        (slideOutVertically { if (up) -it else it } + fadeOut())
                },
                contentAlignment = Alignment.Center,
                label = "selectionCount",
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .widthIn(min = COUNT_MIN_WIDTH)
                    .padding(horizontal = 4.dp)
            ) { shown ->
                Text(
                    shown.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
            }
            if (actions.canCopy) {
                IconButton(onClick = onCopy) {
                    Icon(Symbols.ContentCopy, contentDescription = tr("Copy", "Копировать"))
                }
            }
            // No flag guards this one: anything that can be selected can be
            // sent on, and Telegram refuses at the far end if the target chat
            // does not accept it.
            IconButton(onClick = onForward) {
                Icon(Symbols.Forward, contentDescription = tr("Forward", "Переслать"))
            }
            if (actions.canDeleteForSelf || actions.canDeleteForEveryone) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Symbols.Delete,
                        contentDescription = tr("Delete", "Удалить"),
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
        icon = { Icon(Symbols.Delete, contentDescription = null) },
        title = { Text(if (count == 1) tr("Delete message?", "Удалить сообщение?") else tr("Delete $count messages?", "Удалить сообщения ($count)?")) },
        text = {
            Text(
                if (actions.canDeleteForEveryone) {
                    tr("This cannot be undone.", "Это не отменить.")
                } else {
                    tr("They will be removed for you. The other side keeps their copies.", "Они удалятся у вас. У собеседника останутся.")
                }
            )
        },
        confirmButton = {
            TextButton(onClick = { onDelete(false) }) {
                Text(if (actions.canDeleteForEveryone) tr("Delete for me", "Удалить у меня") else tr("Delete", "Удалить"))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text(tr("Cancel", "Отмена")) }
                if (actions.canDeleteForEveryone) {
                    TextButton(
                        onClick = { onDelete(true) },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(tr("Delete for everyone", "Удалить у всех"))
                    }
                }
            }
        }
    )
}

/**
 * Every reaction the message may take, from the arrow at the end of the
 * quick row over its menu: a grid of Telegram's own animations of them,
 * playing, as every Telegram client shows them — each the emoji itself where
 * its animation has not arrived.
 *
 * A bottom sheet rather than a popup over the bubble: the popup is what
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
    available: List<ReactionOption>,
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
                tr("This chat does not allow reactions.", "В этом чате реакции отключены."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 60.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.heightIn(max = 420.dp)
            ) {
                items(available, key = { it.emoji }) { option ->
                    Box(Modifier.padding(2.dp), contentAlignment = Alignment.Center) {
                        ReactionCell(
                            option = option,
                            chosen = option.emoji == chosen,
                            size = 42.dp,
                            onClick = { onPick(option.emoji) }
                        )
                    }
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
        icon = { Icon(Symbols.Delete, contentDescription = null) },
        title = { Text(tr("Delete message?", "Удалить сообщение?")) },
        text = {
            Text(
                if (message.canBeDeletedForEveryone) {
                    tr("This cannot be undone.", "Это не отменить.")
                } else {
                    tr("It will be removed for you. The other side keeps their copy.", "Оно удалится у вас. У собеседника останется.")
                }
            )
        },
        confirmButton = {
            TextButton(onClick = { onDelete(false) }) {
                Text(if (message.canBeDeletedForEveryone) tr("Delete for me", "Удалить у меня") else tr("Delete", "Удалить"))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text(tr("Cancel", "Отмена")) }
                if (message.canBeDeletedForEveryone) {
                    TextButton(
                        onClick = { onDelete(true) },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text(tr("Delete for everyone", "Удалить у всех")) }
                }
            }
        }
    )
}

/** Room for two digits, so the toolbar does not grow as the count does. */
private val COUNT_MIN_WIDTH = 28.dp
