package com.telegramyou.app.ui.newchat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.telegramyou.app.ui.components.AvatarBubble

/**
 * A new group or channel: its name, and for a group, who is in it.
 *
 * One screen rather than Telegram's two steps (pick people, then name it):
 * with the name at the top and the people under it, the whole decision is on
 * one page, and a group with nobody else in it yet — which TDLib allows — is
 * not a detour through an empty picker.
 *
 * The create button appears once there is something to create, rather than
 * sitting greyed out. A disabled button on an untouched form says "you did
 * something wrong" to somebody who has not done anything yet.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NewChatScreen(
    kind: NewChatKind,
    state: NewChatUiState,
    onBack: () -> Unit,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onMemberToggled: (Long) -> Unit,
    onCreate: () -> Unit,
    onErrorShown: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    state.errorMessage?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            onErrorShown()
        }
    }
    // Straight into the name: it is the only thing the screen cannot do
    // without, and making somebody tap the field to start is a step that
    // exists for no reason — the same argument the sign-in screen settled.
    val nameFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { nameFocus.requestFocus() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (kind == NewChatKind.Group) "New group" else "New channel") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            if (state.canCreate || state.isWorking) {
                ExtendedFloatingActionButton(
                    onClick = onCreate,
                    // Above the keyboard, which is up from the moment this
                    // screen opens because the name field takes focus. The
                    // Scaffold places its button against the bottom of the
                    // window, which with the keyboard showing is behind the
                    // keyboard — the emulator could not find the button, and
                    // neither would a thumb.
                    modifier = Modifier.imePadding(),
                    icon = {
                        if (state.isWorking) {
                            LoadingIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Icon(Icons.Rounded.Check, contentDescription = null)
                        }
                    },
                    text = { Text("Create") }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            item {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = onTitleChange,
                    label = {
                        Text(if (kind == NewChatKind.Group) "Group name" else "Channel name")
                    },
                    singleLine = true,
                    isError = state.titleProblem != null,
                    supportingText = state.titleProblem?.let { problem -> { Text(problem) } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .focusRequester(nameFocus)
                )
            }
            if (kind == NewChatKind.Channel) {
                item {
                    OutlinedTextField(
                        value = state.description,
                        onValueChange = onDescriptionChange,
                        label = { Text("Description (optional)") },
                        minLines = 3,
                        supportingText = {
                            Text("Shown to people before they subscribe")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            } else {
                item {
                    Text(
                        if (state.selected.isEmpty()) {
                            "Members"
                        } else {
                            "Members · ${state.selected.size} chosen"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
                    )
                }
                if (state.isLoadingContacts) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) { LoadingIndicator() }
                    }
                }
                items(state.contacts, key = { it.id }) { person ->
                    val checked = person.id in state.selected
                    ListItem(
                        headlineContent = { Text(person.displayName) },
                        supportingContent = person.username
                            ?.takeIf { it.isNotBlank() }
                            ?.let { handle -> { Text("@$handle") } },
                        leadingContent = {
                            AvatarBubble(
                                title = person.displayName,
                                seed = person.avatarColor,
                                size = 40.dp
                            )
                        },
                        // The checkbox draws the state; the row takes the tap,
                        // so the target is the whole row a thumb aims at and a
                        // screen reader announces one checkbox, not two things.
                        trailingContent = { Checkbox(checked = checked, onCheckedChange = null) },
                        modifier = Modifier.toggleable(
                            value = checked,
                            role = Role.Checkbox,
                            onValueChange = { onMemberToggled(person.id) }
                        )
                    )
                }
            }
        }
    }
}

/**
 * Getting into a chat through somebody's invite link.
 *
 * The link is checked before anything is joined, and what it leads to is
 * shown — its name and how many people are in it — because a link is a
 * stranger's word about where it goes. Telegram does the same; joining on
 * paste would be joining something sight unseen.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun JoinLinkScreen(
    state: NewChatUiState,
    onBack: () -> Unit,
    onLinkChange: (String) -> Unit,
    onJoin: () -> Unit,
    onErrorShown: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    state.errorMessage?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            onErrorShown()
        }
    }
    val linkFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { linkFocus.requestFocus() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Join with a link") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = state.link,
                onValueChange = onLinkChange,
                label = { Text("Invite link") },
                placeholder = { Text("t.me/+…") },
                singleLine = true,
                isError = state.linkProblem != null,
                supportingText = state.linkProblem?.let { problem -> { Text(problem) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .focusRequester(linkFocus)
            )

            if (state.isCheckingLink) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            }

            state.preview?.let { preview ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        AvatarBubble(title = preview.title, seed = preview.avatarColor, size = 72.dp)
                        Text(preview.title, style = MaterialTheme.typography.titleLarge)
                        Text(
                            "%,d %s".format(
                                preview.memberCount,
                                if (preview.isChannel) "subscribers" else "members"
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row {
                            Button(onClick = onJoin, enabled = !state.isWorking) {
                                Text(
                                    when {
                                        preview.joinedChatId != null -> "Open"
                                        preview.isChannel -> "Join channel"
                                        else -> "Join group"
                                    }
                                )
                            }
                        }
                        if (preview.joinedChatId != null) {
                            // Said rather than left to the button's wording:
                            // "Open" alone could read as a broken join.
                            Text(
                                "You are already in it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
