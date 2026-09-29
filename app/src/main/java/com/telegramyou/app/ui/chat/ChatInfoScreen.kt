package com.telegramyou.app.ui.chat

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import com.telegramyou.app.ui.icons.Symbols
import com.telegramyou.app.notifications.ChatNotificationSettings
import com.telegramyou.app.notifications.MuteDuration
import com.telegramyou.app.notifications.notificationStatusLabel
import java.time.ZoneId
import androidx.compose.material3.Switch
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import com.telegramyou.app.telegram.model.PersonProfile
import com.telegramyou.app.ui.people.BlockDialog
import com.telegramyou.app.ui.people.blockRow
import com.telegramyou.app.ui.people.personRows
import com.telegramyou.app.ui.components.personShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.telegramyou.app.ui.common.rememberTextCopier
import androidx.compose.ui.unit.dp
import com.telegramyou.app.telegram.model.ChatDetail
import com.telegramyou.app.ui.components.AvatarBubble
import kotlinx.coroutines.launch

/**
 * Who is in this conversation, how to invite somebody, and the way out —
 * and, for a private chat, who the other person is and blocking them.
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
    onLeaveConfirmed: () -> Unit,
    onNotificationsChange: (ChatNotificationSettings) -> Unit = {},
    /** The person behind a private chat; null for everything else. */
    person: PersonProfile? = null,
    onBlockedChange: (Boolean) -> Unit = {},
    /** Every photo and video in the chat, on its own screen. */
    onOpenMedia: () -> Unit = {},
    /** A member of a group, tapped: their profile. */
    onMemberClick: (Long) -> Unit = {},
    /** Revoke the invite link and make a new one, or make the first. */
    onRenewInviteLink: () -> Unit = {},
    /** A group only: delete everything this account has sent in it. */
    onDeleteAllMine: () -> Unit = {},
    errorMessage: String? = null,
    onErrorShown: () -> Unit = {},
    /** Something done — "12 messages deleted" — said once, in a snackbar. */
    notice: String? = null,
    onNoticeShown: () -> Unit = {}
) {
    val chat = detail?.chat
    val copyToClipboard = rememberTextCopier()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmingBlock by rememberSaveable { mutableStateOf(false) }
    var confirmingRenew by rememberSaveable { mutableStateOf(false) }
    var confirmingDeleteMine by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    if (confirmingRenew) {
        AlertDialog(
            onDismissRequest = { confirmingRenew = false },
            icon = { Icon(Symbols.Link, contentDescription = null) },
            title = { Text("Revoke the link?") },
            text = { Text("The link stops working for anyone who has it, and a new one takes its place.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmingRenew = false
                    onRenewInviteLink()
                }) { Text("Revoke link") }
            },
            dismissButton = { TextButton(onClick = { confirmingRenew = false }) { Text("Cancel") } }
        )
    }

    errorMessage?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            onErrorShown()
        }
    }
    notice?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            onNoticeShown()
        }
    }
    if (confirmingDeleteMine) {
        AlertDialog(
            onDismissRequest = { confirmingDeleteMine = false },
            icon = { Icon(Symbols.DeleteSweep, contentDescription = null) },
            title = { Text("Delete all your messages?") },
            text = {
                Text("Everything you have sent in ${chat?.title ?: "this group"} goes, for everyone. It cannot be undone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDeleteMine = false
                    onDeleteAllMine()
                }) { Text("Delete all") }
            },
            dismissButton = { TextButton(onClick = { confirmingDeleteMine = false }) { Text("Cancel") } }
        )
    }
    if (confirmingBlock && person != null) {
        BlockDialog(
            profile = person,
            onDismiss = { confirmingBlock = false },
            onConfirm = {
                confirmingBlock = false
                onBlockedChange(true)
            }
        )
    }

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
                            Symbols.ArrowBack,
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
                        size = 96.dp,
                        shape = personShape(chat?.avatarColor ?: 1),
                        photoPath = chat?.photoPath
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

            // Who they are, right under their name: the number, the
            // username, the bio — before anything this screen can change.
            person?.let { profile ->
                personRows(profile) { what, text ->
                    copyToClipboard(text)
                    scope.launch { snackbarHostState.showSnackbar("$what copied") }
                }
            }

            inviteLink?.let { link ->
                item(key = "invite") {
                    ListItem(
                        headlineContent = { Text(link) },
                        overlineContent = { Text("Invite link") },
                        leadingContent = {
                            Icon(Symbols.Link, contentDescription = null)
                        },
                        trailingContent = {
                            Icon(Symbols.ContentCopy, contentDescription = null)
                        },
                        // Tapping the row rather than only the icon: the whole
                        // row is what a finger aims at, and the only thing
                        // there is to do with a link on a phone is copy it.
                        modifier = Modifier.clickable {
                            copyToClipboard(link)
                            scope.launch {
                                snackbarHostState.showSnackbar("Invite link copied")
                            }
                        }
                    )
                }
                // What else a link is for: handing it to someone through
                // Android's own share sheet, and taking it back.
                item(key = "invite-actions") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(start = 72.dp, end = 16.dp, bottom = 8.dp)
                    ) {
                        FilledTonalButton(onClick = {
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link)
                            context.startActivity(Intent.createChooser(send, "Share invite link"))
                        }) { Text("Share") }
                        OutlinedButton(onClick = { confirmingRenew = true }) { Text("Revoke") }
                    }
                }
            } ?: run {
                // No link to show: this account may make one where it is an
                // admin; the server says so if it is not.
                if (chat != null && (chat.isGroup || chat.isChannel)) {
                    item(key = "invite-create") {
                        ListItem(
                            headlineContent = { Text("Create invite link") },
                            leadingContent = { Icon(Symbols.Link, contentDescription = null) },
                            modifier = Modifier.clickable(onClick = onRenewInviteLink)
                        )
                    }
                }
            }

            chat?.let { current ->
                item(key = "notifications") {
                    NotificationSettingsSection(
                        settings = current.notifications,
                        onChange = onNotificationsChange
                    )
                }
            }

            // Everything sent here that is a picture. The chat's bar has a
            // button for it too; this is where a profile is expected to have it.
            if (chat != null) {
                item(key = "media") {
                    ListItem(
                        headlineContent = { Text("Photos and videos") },
                        leadingContent = { Icon(Symbols.PhotoLibrary, contentDescription = null) },
                        modifier = Modifier.clickable(onClick = onOpenMedia)
                    )
                }
            }

            person?.let { profile ->
                blockRow(profile) {
                    if (profile.isBlocked) {
                        onBlockedChange(false)
                    } else {
                        confirmingBlock = true
                    }
                }
            }

            // Above the members, not under them. A group with forty people
            // in it puts its own list between the header and anything else,
            // and the one action on this screen was three screens down —
            // which the emulator found by not finding it. A private chat is
            // not left but deleted, a different action with different
            // consequences, so the row is absent there rather than renamed.
            // A group only: in a channel this account posts as the channel,
            // not as itself. Beside leaving, in the same error tone, since it
            // too takes something away for good — the forks' "delete all my
            // messages", which the official client does not offer.
            if (chat?.isGroup == true) {
                item(key = "delete-mine") {
                    ListItem(
                        headlineContent = { Text("Delete all my messages") },
                        leadingContent = { Icon(Symbols.DeleteSweep, contentDescription = null) },
                        colors = ListItemDefaults.colors(
                            headlineColor = MaterialTheme.colorScheme.error,
                            leadingIconColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.clickable { confirmingDeleteMine = true }
                    )
                }
            }
            if (chat?.isGroup == true || chat?.isChannel == true) {
                item(key = "leave") {
                    ListItem(
                        headlineContent = { Text("Leave ${if (chat.isChannel) "channel" else "group"}") },
                        leadingContent = {
                            Icon(
                                Symbols.Logout,
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
                        modifier = Modifier.clickable { onMemberClick(member.id) },
                        headlineContent = { Text(member.displayName) },
                        supportingContent = member.username
                            ?.takeIf { it.isNotBlank() }
                            ?.let { handle -> { Text("@$handle") } },
                        leadingContent = {
                            AvatarBubble(
                                title = member.displayName,
                                seed = member.avatarColor,
                                size = 40.dp,
                                shape = personShape(member.avatarColor),
                                photoPath = member.photoPath
                            )
                        }
                    )
                }
            }

        }
    }
}

/**
 * This chat's notifications: on or off, off for how long, the words in the
 * shade or not, a sound or not — Telegram's per-chat settings, as Material
 * list items with switches, and the durations in a menu from their row.
 */
@Composable
private fun NotificationSettingsSection(
    settings: ChatNotificationSettings,
    onChange: (ChatNotificationSettings) -> Unit
) {
    val now = System.currentTimeMillis() / 1000
    val on = !settings.isMuted(now)
    var choosing by remember { mutableStateOf(false) }
    Column {
        Text(
            "Notifications",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
        )
        ListItem(
            headlineContent = { Text("Notifications") },
            supportingContent = { Text(notificationStatusLabel(settings, now, ZoneId.systemDefault())) },
            leadingContent = {
                Icon(
                    if (on) Symbols.Notifications else Symbols.NotificationsOff,
                    contentDescription = null
                )
            },
            trailingContent = {
                Switch(
                    checked = on,
                    onCheckedChange = { turnOn ->
                        onChange(settings.copy(mutedUntil = if (turnOn) 0L else ChatNotificationSettings.MUTED_FOREVER))
                    }
                )
            }
        )
        Box {
            ListItem(
                headlineContent = { Text("Mute for…") },
                leadingContent = { Icon(Symbols.Snooze, contentDescription = null) },
                modifier = Modifier.clickable { choosing = true }
            )
            DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }) {
                MuteDuration.entries.forEach { duration ->
                    DropdownMenuItem(
                        text = { Text(duration.label) },
                        onClick = {
                            choosing = false
                            onChange(settings.copy(mutedUntil = duration.until(System.currentTimeMillis() / 1000)))
                        }
                    )
                }
            }
        }
        ListItem(
            headlineContent = { Text("Message preview") },
            supportingContent = { Text("Show what the message says in the notification") },
            leadingContent = { Icon(Symbols.Visibility, contentDescription = null) },
            trailingContent = {
                Switch(
                    checked = settings.showPreview,
                    onCheckedChange = { onChange(settings.copy(showPreview = it)) }
                )
            }
        )
        ListItem(
            headlineContent = { Text("Sound") },
            leadingContent = { Icon(Symbols.VolumeUp, contentDescription = null) },
            trailingContent = {
                Switch(
                    checked = settings.sound,
                    onCheckedChange = { onChange(settings.copy(sound = it)) }
                )
            }
        )
    }
}
