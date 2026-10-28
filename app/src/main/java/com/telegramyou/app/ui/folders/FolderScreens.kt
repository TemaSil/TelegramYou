package com.telegramyou.app.ui.folders

import com.telegramyou.app.i18n.tr

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import com.telegramyou.app.telegram.model.folderLinkSlug
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.telegramyou.app.telegram.model.ChatFolder
import com.telegramyou.app.telegram.model.ChatPreview
import com.telegramyou.app.telegram.model.FolderRules
import com.telegramyou.app.ui.components.AvatarBubble
import com.telegramyou.app.ui.components.personShape
import com.telegramyou.app.ui.icons.Symbols
import com.telegramyou.app.ui.settings.SettingsGroup
import com.telegramyou.app.ui.settings.settingsBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Symbols.ArrowBack, contentDescription = tr("Back", "Назад")) }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = settingsBackground())
    )
}

@Composable
private fun Snackbars(message: String?, host: SnackbarHostState, onShown: () -> Unit) {
    message?.let { text ->
        LaunchedEffect(text) {
            host.showSnackbar(text)
            onShown()
        }
    }
}

private fun chatsLabel(count: Int): String = when (count) {
    0 -> tr("No chats", "Нет чатов")
    else -> com.telegramyou.app.i18n.counted(count, "chat", "chats", "чат", "чата", "чатов")
}

/**
 * Settings → Chat folders: the folders in the order their tabs are in, each
 * opening its editor, each with a menu to move it or delete it — and a
 * button for a new one. Material has no drag-to-reorder list, and the
 * official client's drag handles are hand-drawn; moving by a place at a
 * time from a menu is what the stock parts offer.
 */
@Composable
fun FoldersScreen(
    state: FoldersUiState,
    onBack: () -> Unit,
    onOpenFolder: (Int) -> Unit,
    onNewFolder: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onDeleteRequested: (ChatFolder) -> Unit,
    onDeleteDismissed: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    onMessageShown: () -> Unit,
    onShare: (Int) -> Unit = {},
    onShared: () -> Unit = {},
    /** A shared folder's link typed in, by the part after addlist/ (2.7). */
    onOpenLink: (String) -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    Snackbars(state.message, snackbarHostState, onMessageShown)
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(state.shareLink) {
        val link = state.shareLink ?: return@LaunchedEffect
        val send = android.content.Intent(android.content.Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(android.content.Intent.EXTRA_TEXT, link)
        context.startActivity(android.content.Intent.createChooser(send, null))
        onShared()
    }
    var addingByLink by remember { mutableStateOf(false) }
    if (addingByLink) {
        FolderLinkDialog(
            onOpen = { slug ->
                addingByLink = false
                onOpenLink(slug)
            },
            onDismiss = { addingByLink = false }
        )
    }
    state.deleting?.let { folder -> DeleteFolderDialog(folder.title, onDeleteDismissed, onDeleteConfirmed) }
    Scaffold(
        containerColor = settingsBackground(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { FolderBar(tr("Chat folders", "Папки с чатами"), onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewFolder,
                // Named explicitly: the alpha's extended button publishes an
                // empty node otherwise — see ContactsScreen.
                modifier = Modifier.semantics { contentDescription = tr("New folder", "Новая папка") },
                icon = { Icon(Symbols.Add, contentDescription = null) },
                text = { Text(tr("New folder", "Новая папка")) }
            )
        }
    ) { padding ->
        val rows = state.folders
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 96.dp)
        ) {
            Text(
                tr(
                    "Folders sort your chats into tabs above the chat list. " +
                        "A folder takes chats by type, or one by one.",
                    "Папки раскладывают чаты по вкладкам над списком. " +
                        "В папку попадают чаты по типу или по одному."
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
            )
            if (rows.isNotEmpty()) {
                SettingsGroup(tr("Your folders", "Ваши папки")) {
                    rows.forEachIndexed { index, row ->
                        item(
                            title = row.folder.title,
                            summary = chatsLabel(row.chatCount),
                            onClick = { onOpenFolder(row.folder.id) },
                            trailing = {
                                FolderMenu(
                                    title = row.folder.title,
                                    canMoveUp = index > 0,
                                    canMoveDown = index < rows.lastIndex,
                                    onMoveUp = { onMove(row.folder.id, -1) },
                                    onMoveDown = { onMove(row.folder.id, 1) },
                                    onShare = { onShare(row.folder.id) },
                                    onDelete = { onDeleteRequested(row.folder) }
                                )
                            }
                        )
                    }
                }
            }
            // Someone else's folder, from its t.me/addlist link (2.7).
            SettingsGroup {
                item(
                    title = tr("Add a folder by link", "Добавить папку по ссылке"),
                    summary = tr("A shared folder's t.me/addlist link", "Ссылка t.me/addlist на общую папку"),
                    leading = { Icon(Symbols.Link, contentDescription = null) },
                    onClick = { addingByLink = true }
                )
            }
        }
    }
}

/** A shared folder's link, pasted; Open goes on only once it is one. */
@Composable
private fun FolderLinkDialog(onOpen: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val slug = folderLinkSlug(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Symbols.Link, contentDescription = null) },
        title = { Text(tr("Add a folder by link", "Добавить папку по ссылке")) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text("t.me/addlist/…") },
                isError = text.isNotBlank() && slug == null,
                supportingText = if (text.isNotBlank() && slug == null) {
                    { Text(tr("Not a folder link — they look like t.me/addlist/…", "Это не ссылка на папку — они выглядят как t.me/addlist/…")) }
                } else {
                    null
                }
            )
        },
        confirmButton = {
            TextButton(onClick = { slug?.let(onOpen) }, enabled = slug != null) { Text(tr("Open", "Открыть")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "Отмена")) } }
    )
}

@Composable
private fun FolderMenu(
    title: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Symbols.MoreVert, contentDescription = tr("$title options", "$title: действия"))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (canMoveUp) {
                DropdownMenuItem(
                    text = { Text(tr("Move up", "Выше")) },
                    leadingIcon = { Icon(Symbols.ArrowUpward, contentDescription = null) },
                    onClick = { open = false; onMoveUp() }
                )
            }
            if (canMoveDown) {
                DropdownMenuItem(
                    text = { Text(tr("Move down", "Ниже")) },
                    leadingIcon = { Icon(Symbols.ArrowDownward, contentDescription = null) },
                    onClick = { open = false; onMoveDown() }
                )
            }
            DropdownMenuItem(
                text = { Text(tr("Share", "Поделиться")) },
                leadingIcon = { Icon(Symbols.Share, contentDescription = null) },
                onClick = { open = false; onShare() }
            )
            DropdownMenuItem(
                text = { Text(tr("Delete", "Удалить")) },
                leadingIcon = { Icon(Symbols.Delete, contentDescription = null) },
                onClick = { open = false; onDelete() }
            )
        }
    }
}

@Composable
private fun DeleteFolderDialog(title: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Symbols.Delete, contentDescription = null) },
        title = { Text(tr("Delete $title?", "Удалить $title?")) },
        text = { Text(tr("The folder goes; its chats stay in your chat list.", "Папка удалится, а её чаты останутся в списке.")) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(tr("Delete", "Удалить")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "Отмена")) } }
    )
}

/**
 * One folder, new or existing: its name, the chats added by hand, the kinds
 * of chat it takes, and what it leaves out — the official client's folder
 * editor, in Settings rows. Saved from the bar, as Android's editors are.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FolderEditScreen(
    state: FolderEditUiState,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onRulesChange: ((FolderRules) -> FolderRules) -> Unit,
    onPickerOpen: () -> Unit,
    onPickerDismiss: () -> Unit,
    onChatToggled: (Long) -> Unit,
    onSave: () -> Unit,
    onDeleteRequested: () -> Unit,
    onDeleteDismissed: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    onMessageShown: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    Snackbars(state.message, snackbarHostState, onMessageShown)
    if (state.done) LaunchedEffect(Unit) { onBack() }
    if (state.deleteAsked) {
        DeleteFolderDialog(state.rules.name.ifBlank { tr("this folder", "эту папку") }, onDeleteDismissed, onDeleteConfirmed)
    }
    if (state.pickerOpen) {
        ChatPicker(
            chats = state.chats,
            chosen = state.rules.includedChatIds.toSet(),
            onToggle = onChatToggled,
            onDismiss = onPickerDismiss
        )
    }
    Scaffold(
        containerColor = settingsBackground(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            FolderBar(if (state.isNew) tr("New folder", "Новая папка") else tr("Edit folder", "Изменить папку"), onBack) {
                TextButton(onClick = onSave, enabled = state.rules.canSave && !state.isSaving) {
                    Text(tr("Save", "Сохранить"))
                }
            }
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
            return@Scaffold
        }
        val rules = state.rules
        val removeColor = MaterialTheme.colorScheme.onSurfaceVariant
        val errorColor = MaterialTheme.colorScheme.error
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            OutlinedTextField(
                value = rules.name,
                onValueChange = onNameChange,
                label = { Text(tr("Folder name", "Название папки")) },
                singleLine = true,
                supportingText = {
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            if (rules.name.isNotBlank() && !rules.includesSomething) {
                                tr("Add chats or choose a type of chat below", "Добавьте чаты или выберите типы чатов ниже")
                            } else {
                                ""
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Text("${rules.name.length}/${FolderRules.MAX_NAME}")
                    }
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .semantics { contentDescription = tr("Folder name", "Название папки") }
            )

            SettingsGroup(tr("Chats", "Чаты")) {
                link(title = tr("Add chats", "Добавить чаты"), icon = Symbols.Add, onClick = onPickerOpen)
                state.includedChats.forEach { chat ->
                    item(
                        title = chat.title,
                        onClick = {},
                        leading = {
                            AvatarBubble(
                                title = chat.title,
                                seed = chat.avatarColor,
                                size = 40.dp,
                                shape = personShape(chat.avatarColor),
                                photoPath = chat.photoPath,
                                savedMessages = chat.isSavedMessages
                            )
                        },
                        trailing = {
                            IconButton(onClick = { onChatToggled(chat.id) }) {
                                Icon(Symbols.Close, contentDescription = tr("Remove ${chat.title}", "Убрать ${chat.title}"), tint = removeColor)
                            }
                        }
                    )
                }
            }

            SettingsGroup(tr("Chat types", "Типы чатов")) {
                switch(tr("Contacts", "Контакты"), rules.includeContacts) { on -> onRulesChange { it.copy(includeContacts = on) } }
                switch(tr("Non-contacts", "Не контакты"), rules.includeNonContacts) { on -> onRulesChange { it.copy(includeNonContacts = on) } }
                switch(tr("Groups", "Группы"), rules.includeGroups) { on -> onRulesChange { it.copy(includeGroups = on) } }
                switch(tr("Channels", "Каналы"), rules.includeChannels) { on -> onRulesChange { it.copy(includeChannels = on) } }
                switch(tr("Bots", "Боты"), rules.includeBots) { on -> onRulesChange { it.copy(includeBots = on) } }
            }

            // Only what the types let in: a chat added by hand stays.
            SettingsGroup(tr("Leave out", "Исключить")) {
                switch(tr("Muted", "Без звука"), rules.excludeMuted) { on -> onRulesChange { it.copy(excludeMuted = on) } }
                switch(tr("Read", "Прочитанные"), rules.excludeRead) { on -> onRulesChange { it.copy(excludeRead = on) } }
                switch(tr("Archived", "Архивные"), rules.excludeArchived) { on -> onRulesChange { it.copy(excludeArchived = on) } }
            }

            if (!state.isNew) {
                SettingsGroup {
                    item(title = tr("Delete folder", "Удалить папку"), titleColor = errorColor, onClick = onDeleteRequested)
                }
            }
        }
    }
}

/** The chat list with a tick on each chat in the folder; a tap adds or removes it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatPicker(
    chats: List<ChatPreview>,
    chosen: Set<Long>,
    onToggle: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                tr("Choose chats", "Выберите чаты"),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) { Text(tr("Done", "Готово")) }
        }
        if (chats.isEmpty()) {
            Text(
                tr("No chats yet.", "Чатов пока нет."),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(32.dp)
            )
            return@ModalBottomSheet
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp), modifier = Modifier.navigationBarsPadding()) {
            items(chats, key = { it.id }) { chat ->
                val checked = chat.id in chosen
                ListItem(
                    headlineContent = { Text(chat.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = {
                        AvatarBubble(
                            title = chat.title,
                            seed = chat.avatarColor,
                            size = 40.dp,
                            shape = personShape(chat.avatarColor),
                            photoPath = chat.photoPath,
                            savedMessages = chat.isSavedMessages
                        )
                    },
                    trailingContent = { Checkbox(checked = checked, onCheckedChange = null) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.clickable { onToggle(chat.id) }
                )
            }
        }
    }
}
