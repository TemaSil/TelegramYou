package com.telegramyou.app.ui.groups

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.telegramyou.app.telegram.model.ForumTopic
import com.telegramyou.app.telegram.model.GroupMember
import com.telegramyou.app.telegram.model.GroupPermission
import com.telegramyou.app.telegram.model.InviteLink
import com.telegramyou.app.telegram.model.LinkExpiry
import com.telegramyou.app.telegram.model.LinkLimit
import com.telegramyou.app.telegram.model.MemberAction
import com.telegramyou.app.telegram.model.inviteLinkSummary
import com.telegramyou.app.telegram.model.memberRoleLabel
import com.telegramyou.app.ui.common.rememberTextCopier
import com.telegramyou.app.ui.components.AvatarBubble
import com.telegramyou.app.ui.components.personShape
import com.telegramyou.app.ui.icons.Symbols
import kotlinx.coroutines.launch

/** A screen's one-off lines — done, or refused — said in its snackbar. */
@Composable
private fun Announce(
    host: SnackbarHostState,
    notice: String?,
    error: String?,
    onNoticeShown: () -> Unit,
    onErrorShown: () -> Unit
) {
    notice?.let { message ->
        LaunchedEffect(message) {
            host.showSnackbar(message)
            onNoticeShown()
        }
    }
    error?.let { message ->
        LaunchedEffect(message) {
            host.showSnackbar(message)
            onErrorShown()
        }
    }
}

/** A screen of the group's, with the stock bar and a back arrow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupScaffold(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    host: SnackbarHostState,
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        snackbarHost = { SnackbarHost(host) },
        floatingActionButton = floatingActionButton,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (subtitle.isNotBlank()) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Symbols.ArrowBack, contentDescription = "Back") }
                }
            )
        },
        content = content
    )
}

/** Material's loading indicator, centred, for a list that has not arrived. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Loading(padding: PaddingValues) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center
    ) { LoadingIndicator() }
}

/**
 * What members may do: one switch each, as the official client and every
 * Android settings screen have it — `ListItem`s with a `Switch`, each
 * saved as it is flipped.
 */
@Composable
fun GroupPermissionsScreen(
    state: GroupUiState,
    onBack: () -> Unit,
    onChange: (GroupPermission, Boolean) -> Unit,
    onErrorShown: () -> Unit
) {
    val host = remember { SnackbarHostState() }
    Announce(host, null, state.errorMessage, {}, onErrorShown)
    GroupScaffold("Permissions", state.title, onBack, host) { padding ->
        val management = state.management ?: return@GroupScaffold Loading(padding)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Text(
                    "What members can do",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
                )
            }
            items(GroupPermission.shownFor(management.isForum)) { permission ->
                val on = permission.isOn(management.permissions)
                ListItem(
                    headlineContent = { Text(permission.label) },
                    trailingContent = {
                        Switch(checked = on, onCheckedChange = { onChange(permission, it) })
                    },
                    modifier = Modifier.clickable { onChange(permission, !on) }
                )
            }
            item {
                Text(
                    "Admins can always do all of these.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

/**
 * A group member as a row the group's admin can act on: their standing
 * beside the name, and a menu of what may be done to them — Material's
 * `DropdownMenu` from a trailing icon button, only where there is anything.
 */
@Composable
fun ManagedMemberRow(
    member: GroupMember,
    actions: List<MemberAction>,
    onClick: () -> Unit,
    onAction: (MemberAction) -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(member.user.displayName) },
        supportingContent = member.user.username
            ?.takeIf { it.isNotBlank() }
            ?.let { handle -> { Text("@$handle") } },
        leadingContent = {
            AvatarBubble(
                title = member.user.displayName,
                seed = member.user.avatarColor,
                size = 40.dp,
                shape = personShape(member.user.avatarColor),
                photoPath = member.user.photoPath
            )
        },
        trailingContent = {
            Box {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    memberRoleLabel(member)?.let { role ->
                        Text(
                            role,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (actions.isNotEmpty()) {
                        IconButton(onClick = { menu = true }) {
                            Icon(Symbols.MoreVert, contentDescription = "Manage ${member.user.displayName}")
                        }
                    }
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    actions.forEach { action ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    action.label,
                                    color = if (action == MemberAction.Remove) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        Color.Unspecified
                                    }
                                )
                            },
                            onClick = {
                                menu = false
                                onAction(action)
                            }
                        )
                    }
                }
            }
        }
    )
}

/** "Remove Nadia from the group?" — asked before it is done. */
@Composable
fun RemoveMemberDialog(member: GroupMember, groupTitle: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Symbols.Logout, contentDescription = null) },
        title = { Text("Remove ${member.user.displayName}?") },
        text = { Text("They leave ${groupTitle.ifBlank { "the group" }}. They can be invited back.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Remove") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * The group's invite links: the primary one and any made for a purpose —
 * named, lasting a while, for so many people — with who has joined through
 * each. Tapping one copies it; its menu shares or revokes it; New link
 * makes another.
 */
@Composable
fun InviteLinksScreen(
    state: GroupUiState,
    onBack: () -> Unit,
    onCreate: (String, LinkExpiry, LinkLimit) -> Unit,
    onRevoke: (InviteLink) -> Unit,
    onNoticeShown: () -> Unit,
    onErrorShown: () -> Unit
) {
    val host = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copy = rememberTextCopier()
    val context = LocalContext.current
    var creating by rememberSaveable { mutableStateOf(false) }
    var revoking by remember { mutableStateOf<InviteLink?>(null) }
    Announce(host, state.notice, state.errorMessage, onNoticeShown, onErrorShown)

    if (creating) {
        NewLinkDialog(
            onDismiss = { creating = false },
            onCreate = { name, expiry, limit ->
                creating = false
                onCreate(name, expiry, limit)
            }
        )
    }
    revoking?.let { link ->
        AlertDialog(
            onDismissRequest = { revoking = null },
            icon = { Icon(Symbols.Link, contentDescription = null) },
            title = { Text("Revoke the link?") },
            text = { Text("It stops working for anyone who has it. People who joined through it stay.") },
            confirmButton = {
                TextButton(onClick = {
                    revoking = null
                    onRevoke(link)
                }) { Text("Revoke") }
            },
            dismissButton = { TextButton(onClick = { revoking = null }) { Text("Cancel") } }
        )
    }

    GroupScaffold(
        "Invite links",
        state.title,
        onBack,
        host,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Symbols.Add, contentDescription = null) },
                text = { Text("New link") }
            )
        }
    ) { padding ->
        val links = state.links ?: return@GroupScaffold Loading(padding)
        val now = System.currentTimeMillis() / 1000
        val (working, revoked) = links.partition { !it.isRevoked }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            // Room under the last row for the button standing over it.
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            items(working, key = { it.link }) { link ->
                LinkRow(
                    link = link,
                    now = now,
                    onCopy = {
                        copy(link.link)
                        scope.launch { host.showSnackbar("Link copied") }
                    },
                    onShare = {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link.link)
                        context.startActivity(Intent.createChooser(send, "Share invite link"))
                    },
                    onRevoke = { revoking = link }
                )
            }
            if (revoked.isNotEmpty()) {
                item(key = "revoked-heading") {
                    Text(
                        "Revoked",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
                    )
                }
                items(revoked, key = { "revoked-" + it.link }) { link ->
                    ListItem(
                        headlineContent = {
                            Text(link.name.ifBlank { link.link.removePrefix("https://") }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = { Text(inviteLinkSummary(link, now)) },
                        leadingContent = { Icon(Symbols.Link, contentDescription = null) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LinkRow(link: InviteLink, now: Long, onCopy: () -> Unit, onShare: () -> Unit, onRevoke: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val label = link.name.ifBlank { link.link.removePrefix("https://") }
    ListItem(
        modifier = Modifier.clickable(onClick = onCopy),
        overlineContent = if (link.isPrimary) {
            { Text("Primary link") }
        } else {
            null
        },
        headlineContent = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(inviteLinkSummary(link, now)) },
        leadingContent = { Icon(Symbols.Link, contentDescription = null) },
        trailingContent = {
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Symbols.MoreVert, contentDescription = "More for $label")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Copy") },
                        leadingIcon = { Icon(Symbols.ContentCopy, contentDescription = null) },
                        onClick = {
                            menu = false
                            onCopy()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share") },
                        leadingIcon = { Icon(Symbols.Share, contentDescription = null) },
                        onClick = {
                            menu = false
                            onShare()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Revoke", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(Symbols.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        },
                        onClick = {
                            menu = false
                            onRevoke()
                        }
                    )
                }
            }
        }
    )
}

/**
 * A new link: a name, how long it lasts and for how many — the two choices
 * as Material's segmented buttons, four stops each, which is what the
 * official client's sliders come down to.
 */
@Composable
private fun NewLinkDialog(onDismiss: () -> Unit, onCreate: (String, LinkExpiry, LinkLimit) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var expiry by rememberSaveable { mutableStateOf(LinkExpiry.Never) }
    var limit by rememberSaveable { mutableStateOf(LinkLimit.Unlimited) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Symbols.Link, contentDescription = null) },
        title = { Text("New invite link") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Lasts", style = MaterialTheme.typography.titleSmall)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    LinkExpiry.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = expiry == option,
                            onClick = { expiry = option },
                            shape = SegmentedButtonDefaults.itemShape(index, LinkExpiry.entries.size),
                            icon = {}
                        ) { Text(option.label, maxLines = 1, style = MaterialTheme.typography.labelMedium) }
                    }
                }
                Text("People who can join", style = MaterialTheme.typography.titleSmall)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    LinkLimit.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = limit == option,
                            onClick = { limit = option },
                            shape = SegmentedButtonDefaults.itemShape(index, LinkLimit.entries.size),
                            icon = {}
                        ) { Text(option.label, maxLines = 1, style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name, expiry, limit) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * A forum's topics, where a forum opens: each a row with its colour, its
 * newest message and its unread count, General first and pinned ones after
 * it — the chat list's shape, one level down. New topic starts one.
 */
@Composable
fun TopicsScreen(
    state: GroupUiState,
    onBack: () -> Unit,
    onOpenTopic: (ForumTopic) -> Unit,
    onOpenInfo: () -> Unit,
    onCreateTopic: (String) -> Unit,
    onNoticeShown: () -> Unit,
    onErrorShown: () -> Unit
) {
    val host = remember { SnackbarHostState() }
    var creating by rememberSaveable { mutableStateOf(false) }
    Announce(host, state.notice, state.errorMessage, onNoticeShown, onErrorShown)

    if (creating) {
        var name by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { creating = false },
            icon = { Icon(Symbols.Forum, contentDescription = null) },
            title = { Text("New topic") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Topic name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        creating = false
                        onCreateTopic(name)
                    }
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Cancel") } }
        )
    }

    GroupScaffold(
        state.title.ifBlank { "Topics" },
        "Topics",
        onBack,
        host,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Symbols.Add, contentDescription = null) },
                text = { Text("New topic") }
            )
        }
    ) { padding ->
        val topics = state.topics ?: return@GroupScaffold Loading(padding)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            items(topics, key = { it.id }) { topic ->
                TopicRow(topic, onClick = { onOpenTopic(topic) })
            }
            item(key = "info") {
                ListItem(
                    headlineContent = { Text("Group info") },
                    leadingContent = { Icon(Symbols.Info, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onOpenInfo)
                )
            }
        }
    }
}

@Composable
private fun TopicRow(topic: ForumTopic, onClick: () -> Unit) {
    val colour = Color(0xFF000000 or topic.iconColor.toLong())
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(topic.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                topic.lastMessage.ifBlank { "No messages yet" },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        // The topic's own colour, the one thing that tells two topics apart
        // at a glance; the icon is Material's, not Telegram's drawn bubble.
        leadingContent = {
            Box(
                Modifier
                    .size(40.dp)
                    .background(colour.copy(alpha = 0.22f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (topic.isGeneral) Symbols.Tag else Symbols.Forum,
                    contentDescription = null,
                    tint = colour
                )
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (topic.timestampLabel.isNotBlank()) {
                    Text(
                        topic.timestampLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                when {
                    topic.unreadCount > 0 -> Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) { Text(topic.unreadCount.toString()) }
                    topic.isClosed -> Icon(Symbols.Lock, contentDescription = "Closed", modifier = Modifier.size(16.dp))
                    topic.isPinned -> Icon(Symbols.PushPin, contentDescription = "Pinned", modifier = Modifier.size(16.dp))
                }
            }
        }
    )
}
