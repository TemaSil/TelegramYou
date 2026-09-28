package com.telegramyou.app.ui.people

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.telegramyou.app.telegram.model.TelegramUser
import com.telegramyou.app.ui.auth.PhoneEntry
import com.telegramyou.app.ui.common.rememberTextCopier
import com.telegramyou.app.ui.components.AvatarBubble
import com.telegramyou.app.ui.components.personShape
import com.telegramyou.app.ui.icons.Symbols
import com.telegramyou.app.ui.settings.settingsBackground
import kotlinx.coroutines.launch

/**
 * One-shot navigation to a chat, and a message said once — the two things
 * each of these screens hands back to whoever hosts it.
 */
@Composable
private fun OneShots(
    openChatId: Long?,
    onOpenChat: (Long) -> Unit,
    onChatOpened: () -> Unit,
    message: String?,
    snackbarHostState: SnackbarHostState,
    onMessageShown: () -> Unit
) {
    openChatId?.let { chatId ->
        LaunchedEffect(chatId) {
            onChatOpened()
            onOpenChat(chatId)
        }
    }
    message?.let { text ->
        LaunchedEffect(text) {
            snackbarHostState.showSnackbar(text)
            onMessageShown()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackBar(title: String, onBack: () -> Unit, containerColor: androidx.compose.ui.graphics.Color? = null) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Symbols.ArrowBack, contentDescription = "Back") }
        },
        colors = if (containerColor != null) {
            TopAppBarDefaults.topAppBarColors(containerColor = containerColor)
        } else {
            TopAppBarDefaults.topAppBarColors()
        }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Loading(padding: PaddingValues) {
    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        LoadingIndicator()
    }
}

/**
 * Somebody's profile, reached from a group's member list or the block
 * list: the same rows a private chat's info screen shows, and a way to
 * write to them.
 */
@Composable
fun PersonScreen(
    state: PersonUiState,
    onBack: () -> Unit,
    onBlockedChange: (Boolean) -> Unit,
    onSendMessage: () -> Unit,
    onOpenChat: (Long) -> Unit,
    onChatOpened: () -> Unit,
    onMessageShown: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val copyToClipboard = rememberTextCopier()
    val scope = rememberCoroutineScope()
    var confirmingBlock by rememberSaveable { mutableStateOf(false) }
    OneShots(state.openChatId, onOpenChat, onChatOpened, state.message, snackbarHostState, onMessageShown)

    val profile = state.profile
    if (confirmingBlock && profile != null) {
        BlockDialog(
            profile = profile,
            onDismiss = { confirmingBlock = false },
            onConfirm = {
                confirmingBlock = false
                onBlockedChange(true)
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { BackBar("Info", onBack) }
    ) { padding ->
        if (state.isLoading) {
            Loading(padding)
            return@Scaffold
        }
        if (profile == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("This person could not be found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        val user = profile.user
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item(key = "header") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AvatarBubble(
                        title = user.displayName,
                        seed = user.avatarColor,
                        size = 96.dp,
                        shape = personShape(user.avatarColor),
                        photoPath = user.photoPath
                    )
                    Text(user.displayName, style = MaterialTheme.typography.headlineSmall)
                    if (profile.isBot) {
                        Text(
                            "bot",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            personRows(profile) { what, text ->
                copyToClipboard(text)
                scope.launch { snackbarHostState.showSnackbar("$what copied") }
            }
            item(key = "message") {
                ListItem(
                    headlineContent = { Text("Send message") },
                    leadingContent = { Icon(Symbols.Chat, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onSendMessage)
                )
            }
            blockRow(profile) {
                if (profile.isBlocked) {
                    onBlockedChange(false)
                } else {
                    confirmingBlock = true
                }
            }
        }
    }
}

/**
 * Everyone this account has blocked, each with its own way back — a
 * button on the row rather than a trip into the profile, because undoing a
 * block is what somebody comes here to do.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BlockedScreen(
    state: BlockedUiState,
    onBack: () -> Unit,
    onUnblock: (TelegramUser) -> Unit,
    onOpenPerson: (Long) -> Unit,
    onMessageShown: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    OneShots(null, {}, {}, state.message, snackbarHostState, onMessageShown)
    Scaffold(
        containerColor = settingsBackground(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { BackBar("Blocked users", onBack, settingsBackground()) }
    ) { padding ->
        if (state.isLoading) {
            Loading(padding)
            return@Scaffold
        }
        if (state.people.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Nobody is blocked", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Block someone from their profile. They will not be able to message or call you.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            itemsIndexed(state.people, key = { _, user -> user.id }) { index, user ->
                PersonListItem(
                    user = user,
                    index = index,
                    count = state.people.size,
                    onClick = { onOpenPerson(user.id) },
                    trailing = { TextButton(onClick = { onUnblock(user) }) { Text("Unblock") } }
                )
            }
        }
    }
}

/**
 * The account's contacts, by name, and adding one by phone number. A
 * contact tapped opens the chat with them, as the official client does;
 * their profile is one tap further, from the chat's header.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ContactsScreen(
    state: ContactsUiState,
    onBack: () -> Unit,
    onContactClick: (Long) -> Unit,
    onAddRequested: () -> Unit,
    onDraftChange: (ContactDraft) -> Unit,
    onAddDismissed: () -> Unit,
    onAddConfirmed: () -> Unit,
    onOpenChat: (Long) -> Unit,
    onChatOpened: () -> Unit,
    onMessageShown: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    OneShots(state.openChatId, onOpenChat, onChatOpened, state.message, snackbarHostState, onMessageShown)
    state.draft?.let { draft ->
        AddContactDialog(
            draft = draft,
            isSaving = state.isSaving,
            onChange = onDraftChange,
            onDismiss = onAddDismissed,
            onConfirm = onAddConfirmed
        )
    }
    Scaffold(
        containerColor = settingsBackground(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { BackBar("Contacts", onBack, settingsBackground()) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddRequested,
                // Named explicitly: the alpha's extended button publishes an
                // empty node, which TalkBack reads as "button" and UiAutomator
                // cannot find — see the same fix on NewChatScreen.
                modifier = Modifier.semantics { contentDescription = "Add contact" },
                icon = { Icon(Symbols.PersonAdd, contentDescription = null) },
                text = { Text("Add contact") }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Loading(padding)
            return@Scaffold
        }
        if (state.contacts.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No contacts yet. Add someone by their phone number.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            // Clear of the floating button at the end.
            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            itemsIndexed(state.contacts, key = { _, user -> user.id }) { index, user ->
                PersonListItem(
                    user = user,
                    index = index,
                    count = state.contacts.size,
                    onClick = { onContactClick(user.id) }
                )
            }
        }
    }
}

/** One person in a list, in Material's segmented rows, like search's. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PersonListItem(
    user: TelegramUser,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        leadingContent = {
            AvatarBubble(
                title = user.displayName,
                seed = user.avatarColor,
                size = 40.dp,
                shape = personShape(user.avatarColor),
                photoPath = user.photoPath
            )
        },
        supportingContent = (user.username?.takeIf { it.isNotBlank() }?.let { "@$it" }
            ?: user.phoneNumber?.takeIf { it.isNotBlank() }?.let { PhoneEntry.format(it) })
            ?.let { line -> { Text(line, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        trailingContent = trailing,
        content = { Text(user.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    )
}

/**
 * The number and a name, which is all Telegram takes.
 */
@Composable
private fun AddContactDialog(
    draft: ContactDraft,
    isSaving: Boolean,
    onChange: (ContactDraft) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add contact") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // A plus and digits, as typed — not reformatted under the
                // caret, which a plain string field cannot do without moving
                // it. The sign-in screen's field does that with an offset map.
                OutlinedTextField(
                    value = draft.phone,
                    onValueChange = { onChange(draft.copy(phone = PhoneEntry.normalize(it))) },
                    label = { Text("Phone number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = draft.firstName,
                    onValueChange = { onChange(draft.copy(firstName = it)) },
                    label = { Text("First name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = draft.lastName,
                    onValueChange = { onChange(draft.copy(lastName = it)) },
                    label = { Text("Last name (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = draft.isComplete && !isSaving) {
                Text(if (isSaving) "Adding…" else "Add")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
