package com.telegramyou.app.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.telegramyou.app.telegram.model.ChatDetail
import com.telegramyou.app.ui.components.AvatarBubble
import kotlinx.coroutines.launch

/**
 * Who is in this conversation, how to invite somebody, and the way out.
 *
 * Its own screen rather than a sheet over the chat: it is a place to look
 * around in, it has a destructive action at the bottom of it, and leaving
 * from a sheet would drop the person back into a conversation they are no
 * longer in.
 *
 * Every row is a stock `ListItem`. There is nothing here Material does not
 * already ship, down to the tonal colour that makes leaving read as the one
 * action on the screen to be careful with.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInfoScreen(
    detail: ChatDetail?,
    inviteLink: String?,
    confirmingLeave: Boolean,
    onBack: () -> Unit,
    onLeaveRequested: () -> Unit,
    onLeaveDismissed: () -> Unit,
    onLeaveConfirmed: () -> Unit
) {
    val chat = detail?.chat
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    if (confirmingLeave) {
        AlertDialog(
            onDismissRequest = onLeaveDismissed,
            title = { Text("Leave ${chat?.title ?: "this chat"}?") },
            text = {
                Text("You will stop receiving messages from it. You can be added back.")
            },
            confirmButton = {
                TextButton(onClick = onLeaveConfirmed) { Text("Leave") }
            },
            dismissButton = {
                TextButton(onClick = onLeaveDismissed) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Info") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AvatarBubble(
                        title = chat?.title ?: "Chat",
                        seed = chat?.avatarColor ?: 1,
                        size = 96.dp
                    )
                    Text(
                        chat?.title ?: "Chat",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    // The same line the conversation's header carries — how
                    // many members, or when someone was last seen — so this
                    // screen agrees with the one it was opened from.
                    detail?.memberCountLabel?.let { label ->
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            inviteLink?.let { link ->
                item(key = "invite") {
                    ListItem(
                        headlineContent = { Text(link) },
                        overlineContent = { Text("Invite link") },
                        leadingContent = {
                            Icon(Icons.Rounded.Link, contentDescription = null)
                        },
                        trailingContent = {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                        },
                        // Tapping the row rather than only the icon: the whole
                        // row is what a finger aims at, and the only thing
                        // there is to do with a link on a phone is copy it.
                        modifier = Modifier.clickable {
                            clipboard.setText(AnnotatedString(link))
                            scope.launch {
                                snackbarHostState.showSnackbar("Invite link copied")
                            }
                        }
                    )
                }
            }

            // Above the members, not under them. A group with forty people
            // in it puts its own list between the header and anything else,
            // and the one action on this screen was three screens down —
            // which the emulator found by not finding it. A private chat is
            // not left but deleted, a different action with different
            // consequences, so the row is absent there rather than renamed.
            if (chat?.isGroup == true || chat?.isChannel == true) {
                item(key = "leave") {
                    ListItem(
                        headlineContent = { Text("Leave ${if (chat.isChannel) "channel" else "group"}") },
                        leadingContent = {
                            Icon(
                                Icons.AutoMirrored.Rounded.Logout,
                                contentDescription = null
                            )
                        },
                        // Material's error tones, because this is the one row
                        // here that takes something away.
                        colors = ListItemDefaults.colors(
                            headlineColor = MaterialTheme.colorScheme.error,
                            leadingIconColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.clickable(onClick = onLeaveRequested)
                    )
                }
            }

            val members = detail?.members.orEmpty()
            if (members.isNotEmpty()) {
                item(key = "members-heading") {
                    Text(
                        "Members",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
                    )
                }
                items(members, key = { it.id }) { member ->
                    ListItem(
                        headlineContent = { Text(member.displayName) },
                        supportingContent = member.username
                            ?.takeIf { it.isNotBlank() }
                            ?.let { handle -> { Text("@$handle") } },
                        leadingContent = {
                            AvatarBubble(
                                title = member.displayName,
                                seed = member.avatarColor,
                                size = 40.dp
                            )
                        }
                    )
                }
            }

        }
    }
}
