package com.telegramyou.app.ui.chat

import com.telegramyou.app.i18n.tr

import androidx.compose.foundation.selection.toggleable
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
import com.telegramyou.app.telegram.model.GroupMember
import com.telegramyou.app.telegram.model.MemberAction
import com.telegramyou.app.telegram.model.memberActions
import com.telegramyou.app.telegram.model.sortedForList
import com.telegramyou.app.ui.groups.GroupUiState
import com.telegramyou.app.ui.groups.ManagedMemberRow
import com.telegramyou.app.ui.groups.RemoveMemberDialog
import com.telegramyou.app.ui.groups.AdminRightsSheet
import com.telegramyou.app.telegram.model.AdminRights
import com.telegramyou.app.telegram.model.matchingMembers
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Badge
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
    /** Incoming messages shown translated (2.0); see AutoTranslate. */
    autoTranslate: Boolean = false,
    onAutoTranslateChange: (Boolean) -> Unit = {},
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
    onNoticeShown: () -> Unit = {},
    /**
     * A group's running: this account's rights, members with their standing.
     * Null for anything but a group, and until it has loaded — the plain
     * member list above stands in meanwhile.
     */
    group: GroupUiState? = null,
    selfId: Long = 0L,
    onMemberAction: (GroupMember, MemberAction) -> Unit = { _, _ -> },
    onRemoveConfirmed: () -> Unit = {},
    onRemoveDismissed: () -> Unit = {},
    onOpenPermissions: () -> Unit = {},
    onOpenInviteLinks: () -> Unit = {},
    onOpenTopics: () -> Unit = {},
    onGroupNoticeShown: () -> Unit = {},
    onGroupErrorShown: () -> Unit = {},
    onAdminSaved: (AdminRights, String) -> Unit = { _, _ -> },
    onAdminEditDismissed: () -> Unit = {},
    onMemberQuery: (String) -> Unit = {},
    onOpenJoinRequests: () -> Unit = {},
    /** The whole chat reported to Telegram (2.8), and the questions it asks on the way. */
    report: ReportFlow? = null,
    onReport: () -> Unit = {},
    onReportAnswer: (String, String) -> Unit = { _, _ -> },
    onReportDismissed: () -> Unit = {}
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
            title = { Text(tr("Revoke the link?", "Отозвать ссылку?")) },
            text = { Text(tr("The link stops working for anyone who has it, and a new one takes its place.", "Ссылка перестанет работать у всех, у кого она есть, а её место займёт новая.")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingRenew = false
                    onRenewInviteLink()
                }) { Text(tr("Revoke link", "Отозвать")) }
            },
            dismissButton = { TextButton(onClick = { confirmingRenew = false }) { Text(tr("Cancel", "Отмена")) } }
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
    group?.editingAdmin?.let { member ->
        AdminRightsSheet(
            member = member,
            isForum = group?.management?.isForum == true,
            isBasicGroup = group?.management?.isBasicGroup == true,
            onDismiss = onAdminEditDismissed,
            onSave = onAdminSaved
        )
    }
    group?.confirmingRemoval?.let { member ->
        RemoveMemberDialog(
            member = member,
            groupTitle = chat?.title.orEmpty(),
            onDismiss = onRemoveDismissed,
            onConfirm = onRemoveConfirmed
        )
    }
    group?.notice?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            onGroupNoticeShown()
        }
    }
    group?.errorMessage?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            onGroupErrorShown()
        }
    }
    if (confirmingDeleteMine) {
        AlertDialog(
            onDismissRequest = { confirmingDeleteMine = false },
            icon = { Icon(Symbols.DeleteSweep, contentDescription = null) },
            title = { Text(tr("Delete all your messages?", "Удалить все ваши сообщения?")) },
            text = {
                Text(
                    tr(
                        "Everything you have sent in ${chat?.title ?: "this group"} goes, for everyone. It cannot be undone.",
                        "Всё, что вы отправили в ${chat?.title ?: "эту группу"}, удалится у всех. Это не отменить."
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDeleteMine = false
                    onDeleteAllMine()
                }) { Text(tr("Delete all", "Удалить всё")) }
            },
            dismissButton = { TextButton(onClick = { confirmingDeleteMine = false }) { Text(tr("Cancel", "Отмена")) } }
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

    report?.let { flow ->
        ReportSheet(step = flow.step, onAnswer = onReportAnswer, onDismiss = onReportDismissed)
    }
    if (confirmingLeave) {
        AlertDialog(
            onDismissRequest = onLeaveDismissed,
            title = { Text(tr("Leave ${chat?.title ?: "this chat"}?", "Покинуть ${chat?.title ?: "этот чат"}?")) },
            text = {
                Text(tr("You will stop receiving messages from it. You can be added back.", "Сообщения отсюда перестанут приходить. Вас смогут добавить снова."))
            },
            confirmButton = {
                TextButton(onClick = onLeaveConfirmed) { Text(tr("Leave", "Покинуть")) }
            },
            dismissButton = {
                TextButton(onClick = onLeaveDismissed) { Text(tr("Cancel", "Отмена")) }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(tr("Info", "Информация")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Symbols.ArrowBack,
                            contentDescription = tr("Back", "Назад")
                        )
                    }
                }
            )
        }
    ) { padding ->
        // For geeks → Translate chats offers the switch below; see AutoTranslate.
        val translateOffered = com.telegramyou.app.settings.LocalGeekSettings.current.autoTranslate
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                // On the person's own profile colour where they chose one,
                // their emoji status after their name.
                val profileColor = person?.user?.profileColor
                com.telegramyou.app.ui.people.ProfileHeader(
                    color = profileColor,
                    modifier = Modifier.padding(vertical = if (profileColor != null) 12.dp else 0.dp)
                ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AvatarBubble(
                        title = chat?.title ?: tr("Chat", "Чат"),
                        seed = chat?.avatarColor ?: 1,
                        size = 96.dp,
                        shape = personShape(chat?.avatarColor ?: 1),
                        photoPath = chat?.photoPath
                    )
                    com.telegramyou.app.ui.people.NameWithStatus(
                        name = chat?.title ?: tr("Chat", "Чат"),
                        emojiStatusId = person?.user?.emojiStatusId,
                        style = MaterialTheme.typography.headlineSmall,
                        statusSize = 28.dp
                    )
                    // The same line the conversation's header carries — how
                    // many members, or when someone was last seen — so this
                    // screen agrees with the one it was opened from.
                    detail?.memberCountLabel?.let { label ->
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (profileColor != null) {
                                androidx.compose.material3.LocalContentColor.current.copy(alpha = 0.8f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
                }
            }

            // Who they are, right under their name: the number, the
            // username, the bio — before anything this screen can change.
            person?.let { profile ->
                personRows(profile) { what, text ->
                    copyToClipboard(text)
                    scope.launch { snackbarHostState.showSnackbar(tr("$what copied", "$what: скопировано")) }
                }
            }

            inviteLink?.let { link ->
                item(key = "invite") {
                    ListItem(
                        headlineContent = { Text(link) },
                        overlineContent = { Text(tr("Invite link", "Ссылка-приглашение")) },
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
                                snackbarHostState.showSnackbar(tr("Invite link copied", "Ссылка-приглашение скопирована"))
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
                            context.startActivity(Intent.createChooser(send, tr("Share invite link", "Поделиться ссылкой-приглашением")))
                        }) { Text(tr("Share", "Поделиться")) }
                        OutlinedButton(onClick = { confirmingRenew = true }) { Text(tr("Revoke", "Отозвать")) }
                    }
                }
            } ?: run {
                // No link to show: this account may make one where it is an
                // admin; the server says so if it is not.
                if (chat != null && (chat.isGroup || chat.isChannel)) {
                    item(key = "invite-create") {
                        ListItem(
                            headlineContent = { Text(tr("Create invite link", "Создать ссылку-приглашение")) },
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
                if (translateOffered) item(key = "translation") {
                    // Into the phone's language, by Telegram's translator —
                    // the one the menu's Translate already uses.
                    ListItem(
                        headlineContent = { Text(tr("Translate messages", "Переводить сообщения")) },
                        supportingContent = {
                            Text(tr("Show what comes in here in ${phoneLanguageName()}", "Показывать входящие на языке телефона"))
                        },
                        leadingContent = { Icon(Symbols.Translate, contentDescription = null) },
                        trailingContent = { Switch(checked = autoTranslate, onCheckedChange = null) },
                        // The whole row toggles it, as a settings row does.
                        modifier = Modifier.toggleable(
                            value = autoTranslate,
                            role = androidx.compose.ui.semantics.Role.Switch,
                            onValueChange = onAutoTranslateChange
                        )
                    )
                }
            }

            // Everything sent here that is a picture. The chat's bar has a
            // button for it too; this is where a profile is expected to have it.
            if (chat != null) {
                item(key = "media") {
                    ListItem(
                        headlineContent = { Text(tr("Shared media", "Медиа")) },
                        supportingContent = { Text(tr("Photos, files, music, voice and links", "Фото, файлы, музыка, голосовые и ссылки")) },
                        leadingContent = { Icon(Symbols.PhotoLibrary, contentDescription = null) },
                        modifier = Modifier.clickable(onClick = onOpenMedia)
                    )
                }
            }

            // Running the group, for whoever may: what members can do, and
            // the links in. A forum's topics for everybody — it is how one
            // gets back to them from here.
            val management = group?.management
            if (management?.isForum == true) {
                item(key = "topics") {
                    ListItem(
                        headlineContent = { Text(tr("Topics", "Темы")) },
                        leadingContent = { Icon(Symbols.Forum, contentDescription = null) },
                        modifier = Modifier.clickable(onClick = onOpenTopics)
                    )
                }
            }
            if (management?.rights?.canRestrictMembers == true) {
                item(key = "permissions") {
                    ListItem(
                        headlineContent = { Text(tr("Permissions", "Разрешения")) },
                        supportingContent = { Text(tr("What members can do", "Что могут участники")) },
                        leadingContent = { Icon(Symbols.AdminPanelSettings, contentDescription = null) },
                        modifier = Modifier.clickable(onClick = onOpenPermissions)
                    )
                }
            }
            val waiting = group?.joinRequests.orEmpty()
            if (management?.rights?.canInviteUsers == true && waiting.isNotEmpty()) {
                item(key = "join-requests") {
                    ListItem(
                        headlineContent = { Text(tr("Join requests", "Заявки на вступление")) },
                        supportingContent = { Text(tr("People asking to join through a link", "Кто просится вступить по ссылке")) },
                        leadingContent = { Icon(Symbols.PersonAdd, contentDescription = null) },
                        trailingContent = {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ) { Text(waiting.size.toString()) }
                        },
                        modifier = Modifier.clickable(onClick = onOpenJoinRequests)
                    )
                }
            }
            if (management?.rights?.canInviteUsers == true) {
                item(key = "invite-links") {
                    ListItem(
                        headlineContent = { Text(tr("Invite links", "Ссылки-приглашения")) },
                        supportingContent = { Text(tr("Links with a name, a time limit or a number of people", "Ссылки с названием, сроком или лимитом людей")) },
                        leadingContent = { Icon(Symbols.Link, contentDescription = null) },
                        modifier = Modifier.clickable(onClick = onOpenInviteLinks)
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
                        headlineContent = { Text(tr("Delete all my messages", "Удалить все мои сообщения")) },
                        leadingContent = { Icon(Symbols.DeleteSweep, contentDescription = null) },
                        colors = ListItemDefaults.colors(
                            headlineColor = MaterialTheme.colorScheme.error,
                            leadingIconColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.clickable { confirmingDeleteMine = true }
                    )
                }
            }
            // Reported as a whole (2.8) — a message's own Report is in its
            // menu. Not Saved Messages, which is this account itself.
            if (chat != null && !chat.isSavedMessages) {
                item(key = "report") {
                    ListItem(
                        headlineContent = { Text(tr("Report", "Пожаловаться")) },
                        leadingContent = { Icon(Symbols.ErrorOutline, contentDescription = null) },
                        colors = ListItemDefaults.colors(
                            headlineColor = MaterialTheme.colorScheme.error,
                            leadingIconColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.clickable(onClick = onReport)
                    )
                }
            }
            if (chat?.isGroup == true || chat?.isChannel == true) {
                item(key = "leave") {
                    ListItem(
                        headlineContent = {
                            Text(
                                if (chat.isChannel) tr("Leave channel", "Покинуть канал") else tr("Leave group", "Покинуть группу")
                            )
                        },
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

            // With the group's running loaded, the members come with their
            // standing and, for an admin, a menu each; the plain list is what
            // shows before that, and for anybody the server will not tell.
            val managed = group?.management?.members.orEmpty()
            if (managed.isNotEmpty()) {
                val management = group?.management
                item(key = "members-heading") {
                    Text(
                        tr("Members", "Участники"),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
                    )
                }
                // A search over them where there are enough to need one:
                // what is loaded filters at once, and the server's answer
                // follows for the members a big group did not list.
                if (managed.size > MEMBER_SEARCH_FROM) {
                    item(key = "members-search") {
                        OutlinedTextField(
                            value = group?.memberQuery.orEmpty(),
                            onValueChange = onMemberQuery,
                            placeholder = { Text(tr("Search members", "Поиск участников")) },
                            leadingIcon = { Icon(Symbols.Search, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                }
                val query = group?.memberQuery.orEmpty()
                val shown = if (query.isBlank()) {
                    sortedForList(managed)
                } else {
                    // The server's finds and the local ones, once each.
                    val local = matchingMembers(managed, query)
                    val remote = group?.memberResults.orEmpty().filter { found -> local.none { it.user.id == found.user.id } }
                    sortedForList(local + remote)
                }
                if (shown.isEmpty()) {
                    item(key = "members-none") {
                        Text(
                            tr("Nobody by that name", "Никого с таким именем"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                items(shown, key = { it.user.id }) { member ->
                    ManagedMemberRow(
                        member = member,
                        actions = if (management == null) {
                            emptyList()
                        } else {
                            memberActions(management.rights, member, selfId, management.isBasicGroup)
                        },
                        onClick = { onMemberClick(member.user.id) },
                        onAction = { action -> onMemberAction(member, action) }
                    )
                }
            }
            val members = if (managed.isEmpty()) detail?.members.orEmpty() else emptyList()
            if (members.isNotEmpty()) {
                item(key = "members-heading") {
                    Text(
                        tr("Members", "Участники"),
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
            tr("Notifications", "Уведомления"),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
        )
        ListItem(
            headlineContent = { Text(tr("Notifications", "Уведомления")) },
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
                headlineContent = { Text(tr("Mute for…", "Без звука на…")) },
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
            headlineContent = { Text(tr("Message preview", "Текст в уведомлении")) },
            supportingContent = { Text(tr("Show what the message says in the notification", "Показывать текст сообщения в уведомлении")) },
            leadingContent = { Icon(Symbols.Visibility, contentDescription = null) },
            trailingContent = {
                Switch(
                    checked = settings.showPreview,
                    onCheckedChange = { onChange(settings.copy(showPreview = it)) }
                )
            }
        )
        ListItem(
            headlineContent = { Text(tr("Sound", "Звук")) },
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

/** How many members a group has before its list gets a search. */
private const val MEMBER_SEARCH_FROM = 5

/** The phone's language by name, as "Show what comes in here in Russian" says it. */
private fun phoneLanguageName(): String =
    java.util.Locale.getDefault().getDisplayLanguage(java.util.Locale.ENGLISH).ifBlank { "your language" }
