package com.telegramyou.app.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.telegramyou.app.i18n.tr
import com.telegramyou.app.telegram.model.ChecklistContent
import com.telegramyou.app.telegram.model.checklistDraft

/**
 * A checklist in a bubble (2.9.1): its title, how many are done, and each
 * task with Material's checkbox, ticked by a tap where Telegram allows it,
 * saying who ticked it off.
 */
@Composable
internal fun ChecklistMessage(list: ChecklistContent, outgoing: Boolean, onToggle: (Int) -> Unit) {
    val content = if (outgoing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val quiet = content.copy(alpha = 0.7f)
    Column(Modifier.widthIn(min = 220.dp)) {
        Text(list.title, style = MaterialTheme.typography.titleMedium, color = content)
        Text(
            tr("${list.doneCount} of ${list.tasks.size} done", "Готово: ${list.doneCount} из ${list.tasks.size}"),
            style = MaterialTheme.typography.labelMedium,
            color = quiet
        )
        Spacer(Modifier.height(4.dp))
        list.tasks.forEach { task ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = task.isDone,
                        enabled = list.canMarkDone,
                        role = Role.Checkbox,
                        onValueChange = { onToggle(task.id) }
                    )
            ) {
                Checkbox(
                    checked = task.isDone,
                    onCheckedChange = null,
                    enabled = list.canMarkDone,
                    colors = if (outgoing) {
                        CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.onPrimary,
                            checkmarkColor = MaterialTheme.colorScheme.primary,
                            uncheckedColor = MaterialTheme.colorScheme.onPrimary,
                            disabledCheckedColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
                            disabledUncheckedColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                        )
                    } else {
                        CheckboxDefaults.colors()
                    }
                )
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(
                        task.text,
                        color = if (task.isDone) quiet else content,
                        textDecoration = if (task.isDone) TextDecoration.LineThrough else null
                    )
                    task.doneBy?.let { who ->
                        Text(
                            tr("Done by $who", "Отметил(а): $who"),
                            style = MaterialTheme.typography.labelSmall,
                            color = quiet
                        )
                    }
                }
            }
        }
    }
}

/**
 * A checklist written before it goes (2.9.1): a title, and its tasks one
 * a line — the quickest way to type a list on a phone.
 */
@Composable
internal fun ChecklistComposer(onSend: (title: String, lines: String) -> Unit, onDismiss: () -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var lines by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("New checklist", "Новый чек-лист")) },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(tr("Title", "Название")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = lines,
                    onValueChange = { lines = it },
                    label = { Text(tr("Tasks, one per line", "Задачи, по одной в строке")) },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSend(title, lines) },
                enabled = checklistDraft(title, lines) != null
            ) { Text(tr("Send", "Отправить")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "Отмена")) } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        iconContentColor = Color.Unspecified
    )
}
