package com.telegramyou.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.telegramyou.app.telegram.model.ChatPreview
import com.telegramyou.app.telegram.model.chatRemovalOf
import com.telegramyou.app.telegram.model.firstNameOf

/** What the chat list is waiting for a yes on. */
sealed interface PendingRemoval {
    val chat: ChatPreview

    data class Clear(override val chat: ChatPreview) : PendingRemoval
    data class Remove(override val chat: ChatPreview) : PendingRemoval
}

/**
 * The yes-or-no before a chat is emptied, deleted or left.
 *
 * Stock `AlertDialog`, with Material's checkbox row for "also for them"
 * where Telegram allows it — off by default, so the gentler of the two is
 * what a hurried tap does. The confirming button is in the error colour:
 * neither of these can be undone.
 */
@Composable
fun ChatRemovalDialog(
    pending: PendingRemoval,
    onDismiss: () -> Unit,
    onConfirm: (forEveryone: Boolean) -> Unit
) {
    val chat = pending.chat
    val removal = chatRemovalOf(chat)
    val name = firstNameOf(chat.title)
    var forEveryone by rememberSaveable(chat.id) { mutableStateOf(false) }

    val clearing = pending is PendingRemoval.Clear
    val title = when {
        clearing -> "Clear history?"
        removal.leaves -> "Leave ${chat.title}?"
        else -> "Delete the chat with $name?"
    }
    val body = when {
        clearing && chat.isSavedMessages -> "Everything saved here will be deleted."
        clearing -> "Every message with $name will be deleted. The chat stays in your list."
        removal.leaves -> "You will stop receiving its messages. You can join again later."
        else -> "The chat and its messages will be removed from your list."
    }
    val confirm = when {
        clearing -> "Clear"
        removal.leaves -> "Leave"
        else -> "Delete"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(body)
                if (removal.offerForEveryone) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = forEveryone,
                                role = Role.Checkbox,
                                onValueChange = { forEveryone = it }
                            )
                    ) {
                        // Null here: the whole row is the control, so the box
                        // and the words answer to one tap target.
                        Checkbox(checked = forEveryone, onCheckedChange = null)
                        Text(
                            if (clearing) "Also clear for $name" else "Also delete for $name",
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(forEveryone) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text(confirm) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
