package com.telegramyou.app.ui.chat

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.telegramyou.app.ui.icons.Symbols

/**
 * Choosing what to quote of a message (2.0), as the official client does
 * it: the message's text, selectable, and the part between the handles is
 * what the reply quotes. A read-only field rather than selectable text in
 * the bubble, because a field is where Android's own selection handles and
 * their magnifier already are.
 */
@Composable
internal fun QuoteDialog(text: String, onQuote: (start: Int, end: Int) -> Unit, onDismiss: () -> Unit) {
    var value by remember(text) { mutableStateOf(TextFieldValue(text, selection = TextRange(0, text.length))) }
    val focus = remember { FocusRequester() }
    // Focused, so the selection shows with its handles from the start.
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Symbols.FormatQuote, contentDescription = null) },
        title = { Text("Quote") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.copy(text = text) },
                readOnly = true,
                supportingText = { Text("Select the part to quote") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .focusRequester(focus)
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onQuote(value.selection.min, value.selection.max) },
                enabled = !value.selection.collapsed
            ) { Text("Reply with quote") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** A message this long or shorter is answered whole: there is nothing to pick out of it. */
internal const val QUOTE_WORTH_IT = 24

/**
 * Material's date picker, for taking the conversation to a day (2.0): only
 * days up to today can be picked, and the one picked is answered as the
 * local midnight it begins at, in epoch seconds. The picker works in UTC
 * days, so the day is read from it and placed in the phone's own zone.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun JumpToDateDialog(onPick: (dayStart: Long) -> Unit, onDismiss: () -> Unit) {
    val today = remember { System.currentTimeMillis() }
    val state = androidx.compose.material3.rememberDatePickerState(
        initialSelectedDateMillis = today,
        selectableDates = object : androidx.compose.material3.SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= today
        }
    )
    androidx.compose.material3.DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val picked = state.selectedDateMillis ?: return@TextButton
                    val day = java.time.LocalDate.ofEpochDay(picked / MILLIS_PER_DAY)
                    onPick(day.atStartOfDay(java.time.ZoneId.systemDefault()).toEpochSecond())
                },
                enabled = state.selectedDateMillis != null
            ) { Text("Go to date") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        androidx.compose.material3.DatePicker(state = state)
    }
}

private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L
