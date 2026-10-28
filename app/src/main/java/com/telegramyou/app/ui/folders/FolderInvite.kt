package com.telegramyou.app.ui.folders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.telegramyou.app.i18n.counted
import com.telegramyou.app.i18n.tr
import com.telegramyou.app.navigation.Route
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.ChatPreview
import com.telegramyou.app.telegram.model.FolderInvite
import com.telegramyou.app.telegram.model.folderLinkOf
import com.telegramyou.app.ui.components.AvatarBubble
import com.telegramyou.app.ui.failureText
import com.telegramyou.app.ui.icons.Symbols
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FolderInviteUiState(
    val isLoading: Boolean = true,
    /** Null once loaded means the link led nowhere. */
    val invite: FolderInvite? = null,
    /** The chats to join with it, of those not joined yet; all of them to begin with. */
    val chosen: Set<Long> = emptySet(),
    val isAdding: Boolean = false,
    val error: String? = null,
    /** Added: the dialog goes, and says so. */
    val added: Boolean = false
)

/**
 * A shared folder's link opened (2.7) — from a message, or typed into Chat
 * folders: what the folder is and brings, and Add to take it.
 */
class FolderInviteViewModel(
    private val repository: TelegramRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val link = folderLinkOf(savedStateHandle[Route.FolderInvite.ARG_SLUG] ?: "")

    private val _uiState = MutableStateFlow(FolderInviteUiState())
    val uiState: StateFlow<FolderInviteUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val invite = runCatching { repository.folderInvite(link) }.getOrNull()
            _uiState.update {
                it.copy(isLoading = false, invite = invite, chosen = invite?.missingChats.orEmpty().map { c -> c.id }.toSet())
            }
        }
    }

    fun onToggle(chatId: Long) = _uiState.update {
        it.copy(chosen = if (chatId in it.chosen) it.chosen - chatId else it.chosen + chatId)
    }

    fun onAdd() {
        val state = _uiState.value
        if (state.invite == null || state.isAdding) return
        _uiState.update { it.copy(isAdding = true, error = null) }
        viewModelScope.launch {
            try {
                repository.addFolderByLink(link, state.chosen.toList())
                _uiState.update { it.copy(isAdding = false, added = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isAdding = false, error = failureText(tr("Could not add the folder", "Не удалось добавить папку"), e.message))
                }
            }
        }
    }
}

/**
 * The folder, its chats to join ticked — each can be left out, as in the
 * official client — and those already in the chat list under them.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FolderInviteDialog(
    state: FolderInviteUiState,
    onToggle: (Long) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
    onAdded: () -> Unit
) {
    LaunchedEffect(state.added) { if (state.added) onAdded() }
    val invite = state.invite
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Symbols.Folder, contentDescription = null) },
        title = {
            Text(
                when {
                    state.isLoading -> tr("Shared folder", "Общая папка")
                    invite == null -> tr("Link not found", "Ссылка не найдена")
                    else -> tr("Add “${invite.name}”?", "Добавить «${invite.name}»?")
                }
            )
        },
        text = {
            when {
                state.isLoading -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                invite == null -> Text(tr("This folder link has expired or does not exist.", "Ссылка на папку устарела или не существует."))
                else -> Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    if (invite.missingChats.isNotEmpty()) {
                        Text(
                            counted(invite.missingChats.size, "chat to join", "chats to join", "чат, чтобы вступить", "чата, чтобы вступить", "чатов, чтобы вступить"),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        invite.missingChats.forEach { chat ->
                            InviteChat(chat, checked = chat.id in state.chosen, onClick = { onToggle(chat.id) })
                        }
                    }
                    if (invite.addedChats.isNotEmpty()) {
                        Text(
                            tr("Already in your chats", "Уже в ваших чатах"),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        invite.addedChats.forEach { chat -> InviteChat(chat, checked = null, onClick = null) }
                    }
                    state.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            if (invite != null) {
                TextButton(onClick = onAdd, enabled = !state.isAdding) {
                    Text(if (invite.folderId != null) tr("Update folder", "Обновить папку") else tr("Add folder", "Добавить папку"))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "Отмена")) } }
    )
}

@Composable
private fun InviteChat(chat: ChatPreview, checked: Boolean?, onClick: (() -> Unit)?) {
    ListItem(
        headlineContent = { Text(chat.title) },
        leadingContent = { AvatarBubble(title = chat.title, seed = chat.avatarColor, size = 36.dp, photoPath = chat.photoPath) },
        trailingContent = checked?.let { { Checkbox(checked = it, onCheckedChange = { onClick?.invoke() }) } },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    )
}
