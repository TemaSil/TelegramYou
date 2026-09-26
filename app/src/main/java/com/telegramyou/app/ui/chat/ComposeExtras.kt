package com.telegramyou.app.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.telegramyou.app.telegram.model.AudioContent
import com.telegramyou.app.telegram.model.ChatMessage
import com.telegramyou.app.telegram.model.PollDraft
import com.telegramyou.app.telegram.model.SCHEDULE_MAX_AHEAD_SECONDS
import com.telegramyou.app.telegram.model.scheduleInstant
import com.telegramyou.app.telegram.model.scheduleLabel
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.ZoneId

/**
 * Writing a poll: Material's full-screen dialog, since a question and up to
 * ten answers is a form, not something to type into a sheet over the
 * keyboard. Send sits in the app bar, as a full-screen dialog's action does,
 * and stays disabled with the reason written under the fields until the
 * poll can go.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PollComposer(
    draft: PollDraft,
    onChange: (PollDraft) -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("New poll") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = "Discard poll") }
                    },
                    actions = {
                        TextButton(onClick = onSend, enabled = draft.canSend) { Text("Send") }
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item(key = "question") {
                    OutlinedTextField(
                        value = draft.question,
                        onValueChange = { onChange(draft.copy(question = it)) },
                        label = { Text("Question") },
                        // Named for TalkBack, and for the UI test, which
                        // otherwise finds the chat's own field behind this
                        // dialog first.
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Poll question" }
                    )
                }
                item(key = "answers") {
                    Text(
                        if (draft.isQuiz) "Answers — mark the right one" else "Answers",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                itemsIndexed(draft.options, key = { index, _ -> "option-$index" }) { index, option ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (draft.isQuiz) {
                            RadioButton(
                                selected = draft.correctOption == index,
                                onClick = { onChange(draft.copy(correctOption = index)) },
                                enabled = option.isNotBlank()
                            )
                        }
                        OutlinedTextField(
                            value = option,
                            onValueChange = { onChange(draft.withOption(index, it)) },
                            placeholder = { Text("Answer ${index + 1}") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).semantics { contentDescription = "Answer ${index + 1}" },
                            trailingIcon = if (draft.options.size > 2 && option.isNotEmpty()) {
                                {
                                    IconButton(onClick = { onChange(draft.withoutOption(index)) }) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Remove answer ${index + 1}")
                                    }
                                }
                            } else null
                        )
                    }
                }
                item(key = "switches") {
                    Column {
                        PollSwitch("Anonymous voting", draft.isAnonymous) { onChange(draft.copy(isAnonymous = it)) }
                        PollSwitch("Multiple answers", draft.allowsMultiple, enabled = !draft.isQuiz) {
                            onChange(draft.copy(allowsMultiple = it))
                        }
                        PollSwitch("Quiz mode", draft.isQuiz) { onChange(draft.asQuiz(it)) }
                    }
                }
                if (draft.isQuiz) {
                    item(key = "explanation") {
                        OutlinedTextField(
                            value = draft.explanation,
                            onValueChange = { onChange(draft.copy(explanation = it)) },
                            label = { Text("Explanation (optional)") },
                            supportingText = { Text("Shown after someone answers") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                draft.problem?.let { problem ->
                    item(key = "problem") {
                        Text(
                            problem,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PollSwitch(title: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
    )
}

/**
 * Picking when a message goes: Material's date picker, then its time picker
 * — the two stock dialogs, one after the other, as Android's own apps
 * schedule things. [onPicked] gets the moment in epoch seconds.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SchedulePicker(onPicked: (Long) -> Unit, onDismiss: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val nowMillis = remember { System.currentTimeMillis() }
    // An hour from now, on the next five minutes, as a start — and on the
    // day that lands on, so a start after 23:00 does not offer a time that
    // has already passed today.
    val start = remember {
        val later = ZonedDateTime.now(zone).plusHours(1).truncatedTo(ChronoUnit.MINUTES)
        later.plusMinutes(((5 - later.minute % 5) % 5).toLong())
    }
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = start.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = remember {
            object : SelectableDates {
                // Today to a year from now, which is what Telegram accepts.
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis >= nowMillis - DAY_MILLIS && utcTimeMillis <= nowMillis + SCHEDULE_MAX_AHEAD_SECONDS * 1000
            }
        }
    )
    var pickedDate by remember { mutableStateOf<Long?>(null) }
    val date = pickedDate
    if (date == null) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = { pickedDate = dateState.selectedDateMillis }, enabled = dateState.selectedDateMillis != null) {
                    Text("Next")
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        ) {
            DatePicker(state = dateState)
        }
    } else {
        val timeState = rememberTimePickerState(initialHour = start.hour, initialMinute = start.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Send at") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = { onPicked(scheduleInstant(date, timeState.hour, timeState.minute, zone)) }) {
                    Text("Schedule")
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        )
    }
}

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

/**
 * The messages waiting to go in this chat, in a sheet: when each will go,
 * and the two things to do with one — send it now, or drop it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScheduledSheet(
    messages: List<ChatMessage>,
    onSendNow: (ChatMessage) -> Unit,
    onDelete: (ChatMessage) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Text(
            "Scheduled messages",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        if (messages.isEmpty()) {
            Text(
                "Nothing is waiting to be sent",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )
        }
        val now = System.currentTimeMillis() / 1000
        val zone = ZoneId.systemDefault()
        LazyColumn {
            items(messages, key = { it.id }) { message ->
                ListItem(
                    headlineContent = { Text(message.text, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text(message.scheduledAt?.let { scheduleLabel(it, now, zone) } ?: message.timeLabel)
                    },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { onSendNow(message) }) {
                                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send now")
                            }
                            IconButton(onClick = { onDelete(message) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Delete scheduled message")
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
        Spacer(Modifier.padding(bottom = 24.dp))
    }
}

/**
 * A music file in its bubble: the play button, the title and who it is by,
 * its length, and while it plays a bar of how far through it is. The same
 * player as a voice message, and the same one button for play and pause.
 */
@Composable
internal fun AudioMessage(
    audio: AudioContent,
    outgoing: Boolean,
    state: VoiceState,
    progress: Float,
    onToggle: () -> Unit
) {
    val onTint = if (outgoing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Column(modifier = Modifier.widthIn(min = 220.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = onToggle) {
                when (state) {
                    VoiceState.Loading -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    VoiceState.Playing -> Icon(Icons.Rounded.Pause, contentDescription = "Pause")
                    VoiceState.Idle -> Icon(Icons.Rounded.PlayArrow, contentDescription = "Play ${audio.displayTitle}")
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    audio.displayTitle,
                    color = onTint,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val details = listOfNotNull(
                    audio.performer.takeIf { it.isNotBlank() },
                    audio.durationSeconds.takeIf { it > 0 }?.let { formatDuration(it.toLong()) }
                ).joinToString(" · ")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = onTint.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        details.ifBlank { "Audio" },
                        style = MaterialTheme.typography.labelMedium,
                        color = onTint.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (state == VoiceState.Playing) {
            Box(Modifier.padding(top = 8.dp)) {
                LinearProgressIndicator(
                    progress = { progress },
                    color = onTint,
                    trackColor = onTint.copy(alpha = 0.2f),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
