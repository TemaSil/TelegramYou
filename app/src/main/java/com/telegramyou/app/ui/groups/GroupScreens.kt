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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.telegramyou.app.telegram.model.ADMIN_TITLE_MAX
import com.telegramyou.app.telegram.model.AdminRight
import com.telegramyou.app.telegram.model.AdminRights
import com.telegramyou.app.telegram.model.ForumTopic
import com.telegramyou.app.telegram.model.JoinRequest
import com.telegramyou.app.telegram.model.MemberRole
import com.telegramyou.app.telegram.model.TopicAction
import com.telegramyou.app.telegram.model.topicActions
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
import com.telegramyou.app.ui.format.chatListTimeLabel
import com.telegramyou.app.ui.icons.Symbols
import java.time.ZoneId
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
    onCreate: (String, LinkExpiry, LinkLimit, Boolean) -> Unit,
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
            onCreate = { name, expiry, limit, asksFirst ->
                creating = false
                onCreate(name, expiry, limit, asksFirst)
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
                // Named explicitly: the alpha's extended button publishes an
                // empty node otherwise — see ProxyScreen.
                modifier = Modifier.semantics { contentDescription = "New link" },
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
private fun NewLinkDialog(onDismiss: () -> Unit, onCreate: (String, LinkExpiry, LinkLimit, Boolean) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var expiry by rememberSaveable { mutableStateOf(LinkExpiry.Never) }
    var limit by rememberSaveable { mutableStateOf(LinkLimit.Unlimited) }
    var asksFirst by rememberSaveable { mutableStateOf(false) }
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
                // Asking first and a head count do not go together: Telegram
                // refuses a limit on a link an admin approves.
                ListItem(
                    headlineContent = { Text("Admins approve new members") },
                    trailingContent = { Switch(checked = asksFirst, onCheckedChange = { asksFirst = it }) },
                    modifier = Modifier.clickable { asksFirst = !asksFirst }
                )
                if (!asksFirst) {
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
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name, expiry, limit, asksFirst) }) { Text("Create") } },
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
    onErrorShown: () -> Unit,
    onTopicAction: (ForumTopic, TopicAction) -> Unit = { _, _ -> },
    onTopicRenamed: (String) -> Unit = {},
    onTopicDeleteConfirmed: () -> Unit = {},
    onTopicDialogDismissed: () -> Unit = {}
) {
    val host = remember { SnackbarHostState() }
    var creating by rememberSaveable { mutableStateOf(false) }
    Announce(host, state.notice, state.errorMessage, onNoticeShown, onErrorShown)

    state.renamingTopic?.let { topic ->
        var name by rememberSaveable(topic.id) { mutableStateOf(topic.name) }
        AlertDialog(
            onDismissRequest = onTopicDialogDismissed,
            icon = { Icon(Symbols.Edit, contentDescription = null) },
            title = { Text("Rename topic") },
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
                TextButton(enabled = name.isNotBlank(), onClick = { onTopicRenamed(name) }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = onTopicDialogDismissed) { Text("Cancel") } }
        )
    }
    state.deletingTopic?.let { topic ->
        AlertDialog(
            onDismissRequest = onTopicDialogDismissed,
            icon = { Icon(Symbols.Delete, contentDescription = null) },
            title = { Text("Delete ${topic.name}?") },
            text = { Text("The topic and every message in it go, for everyone. It cannot be undone.") },
            confirmButton = { TextButton(onClick = onTopicDeleteConfirmed) { Text("Delete") } },
            dismissButton = { TextButton(onClick = onTopicDialogDismissed) { Text("Cancel") } }
        )
    }

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
                // Named explicitly: the alpha's extended button publishes an
                // empty node otherwise — see ProxyScreen.
                modifier = Modifier.semantics { contentDescription = "New topic" },
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
            val canManage = state.management?.rights?.canManageTopics == true
            items(topics, key = { it.id }) { topic ->
                TopicRow(
                    topic = topic,
                    actions = topicActions(canManage, topic),
                    onClick = { onOpenTopic(topic) },
                    onAction = { action -> onTopicAction(topic, action) }
                )
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
private fun TopicRow(
    topic: ForumTopic,
    actions: List<TopicAction>,
    onClick: () -> Unit,
    onAction: (TopicAction) -> Unit
) {
    val colour = Color(0xFF000000 or topic.iconColor.toLong())
    var menu by remember { mutableStateOf(false) }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(topic.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            // A draft first, as the chat list shows one: it is what the
            // person was in the middle of.
            if (topic.draft.isNotBlank()) {
                Text(
                    "Draft: ${topic.draft}",
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    topic.lastMessage.ifBlank { "No messages yet" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
            Row(verticalAlignment = Alignment.CenterVertically) {
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
            if (actions.isNotEmpty()) {
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Symbols.MoreVert, contentDescription = "Manage ${topic.name}")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        actions.forEach { action ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        action.label,
                                        color = if (action == TopicAction.Delete) MaterialTheme.colorScheme.error else Color.Unspecified
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
            }
        }
    )
}

/**
 * An admin's rights and title, chosen when they are made one or later:
 * Material's bottom sheet, a field for the title and a switch a right —
 * the official client's screen, as the one component Android uses for a
 * short form over the screen it belongs to. A basic group has no rights to
 * choose, only the title.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminRightsSheet(
    member: GroupMember,
    isForum: Boolean,
    isBasicGroup: Boolean,
    onDismiss: () -> Unit,
    onSave: (AdminRights, String) -> Unit
) {
    var rights by remember(member.user.id) { mutableStateOf(member.adminRights ?: AdminRights()) }
    var title by rememberSaveable(member.user.id) { mutableStateOf(member.title) }
    val promoting = member.role != MemberRole.Admin
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Text(
                if (promoting) "Make ${member.user.displayName} an admin" else "${member.user.displayName}'s rights",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(ADMIN_TITLE_MAX) },
                label = { Text("Title") },
                placeholder = { Text("Admin") },
                supportingText = { Text("${title.length} / $ADMIN_TITLE_MAX · shown beside their name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            )
            val shown = AdminRight.shownFor(isForum, isBasicGroup)
            if (shown.isNotEmpty()) {
                Text(
                    "What they can do",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp)
                )
            }
            shown.forEach { right ->
                val on = right.isOn(rights)
                ListItem(
                    headlineContent = { Text(right.label) },
                    trailingContent = { Switch(checked = on, onCheckedChange = { rights = right.set(rights, it) }) },
                    modifier = Modifier
                        .clickable { rights = right.set(rights, !on) }
                        .padding(horizontal = 8.dp)
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = { onSave(rights, title) }) { Text(if (promoting) "Make admin" else "Save") }
            }
        }
    }
}

/**
 * People asking to join through a link that asks first: who they are,
 * what they say about themselves and when they asked, with Add and Dismiss
 * as a filled tonal and a text button each.
 */
@Composable
fun JoinRequestsScreen(
    state: GroupUiState,
    onBack: () -> Unit,
    onAnswer: (JoinRequest, Boolean) -> Unit,
    onPersonClick: (Long) -> Unit,
    onNoticeShown: () -> Unit,
    onErrorShown: () -> Unit
) {
    val host = remember { SnackbarHostState() }
    Announce(host, state.notice, state.errorMessage, onNoticeShown, onErrorShown)
    GroupScaffold("Join requests", state.title, onBack, host) { padding ->
        val requests = state.joinRequests ?: return@GroupScaffold Loading(padding)
        if (requests.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Nobody is waiting to join",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@GroupScaffold
        }
        val now = System.currentTimeMillis() / 1000
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            items(requests, key = { it.user.id }) { request ->
                Column {
                    ListItem(
                        modifier = Modifier.clickable { onPersonClick(request.user.id) },
                        headlineContent = { Text(request.user.displayName) },
                        supportingContent = request.bio.takeIf { it.isNotBlank() }?.let { bio -> { Text(bio) } },
                        overlineContent = {
                            Text("Asked " + chatListTimeLabel(request.date, now, ZoneId.systemDefault()))
                        },
                        leadingContent = {
                            AvatarBubble(
                                title = request.user.displayName,
                                seed = request.user.avatarColor,
                                size = 40.dp,
                                shape = personShape(request.user.avatarColor),
                                photoPath = request.user.photoPath
                            )
                        }
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(start = 72.dp, end = 16.dp, bottom = 8.dp)
                    ) {
                        FilledTonalButton(onClick = { onAnswer(request, true) }) { Text("Add to group") }
                        TextButton(onClick = { onAnswer(request, false) }) { Text("Dismiss") }
                    }
                }
            }
        }
    }
}
