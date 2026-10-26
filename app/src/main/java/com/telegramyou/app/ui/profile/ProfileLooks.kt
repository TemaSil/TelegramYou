package com.telegramyou.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.telegramyou.app.telegram.model.Birthdate
import com.telegramyou.app.telegram.model.ProfileColor
import com.telegramyou.app.telegram.model.ProfileColorChoice
import com.telegramyou.app.telegram.model.customReactionKey
import com.telegramyou.app.ui.chat.ReactionGlyph
import com.telegramyou.app.ui.icons.Symbols
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * What the profile can change about how this account looks to others
 * beyond its name and photo: the birthday, the profile colour and the
 * emoji status. The last two are Telegram Premium's.
 */
data class ProfileLooksActions(
    val onBirthdateChange: (Birthdate?) -> Unit = {},
    val colorChoices: suspend () -> List<ProfileColorChoice> = { emptyList() },
    val onProfileColorChange: (Int?) -> Unit = {},
    val statusChoices: suspend () -> List<Long> = { emptyList() },
    val onEmojiStatusChange: (Long?) -> Unit = {}
)

/**
 * The birthday on Material's own date picker, with a switch for whether
 * the year shows — Telegram keeps a birthday with or without one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BirthdayDialog(current: Birthdate?, onSave: (Birthdate?) -> Unit, onDismiss: () -> Unit) {
    val thisYear = LocalDate.now().year
    val initial = current?.let { runCatching { LocalDate.of(it.year ?: DEFAULT_YEAR, it.month, it.day) }.getOrNull() }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        yearRange = 1900..thisYear
    )
    var showYear by remember { mutableStateOf(current == null || current.year != null) }
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = {
                    val picked = state.selectedDateMillis ?: return@TextButton
                    val date = Instant.ofEpochMilli(picked).atZone(ZoneOffset.UTC).toLocalDate()
                    onSave(Birthdate(date.dayOfMonth, date.monthValue, date.year.takeIf { showYear }))
                }
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (current != null) TextButton(onClick = { onSave(null) }) { Text("Remove") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    ) {
        // The year's switch in the picker's own title, as a filter chip: the
        // dialog is a fixed height, and a row under the calendar would be
        // pressed out of it.
        DatePicker(
            state = state,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 12.dp, top = 16.dp)
                ) {
                    Text("Your birthday", Modifier.weight(1f))
                    FilterChip(
                        selected = showYear,
                        onClick = { showYear = !showYear },
                        label = { Text("Show year") },
                        leadingIcon = if (showYear) {
                            { Icon(Symbols.Check, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) }
                        } else {
                            null
                        }
                    )
                }
            }
        )
    }
}

/**
 * Telegram's profile colours as swatches, each drawn as the card it makes
 * on a profile here — the container Material works out from it — with the
 * one in use ticked.
 */
@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ProfileColorDialog(
    current: ProfileColor?,
    choices: suspend () -> List<ProfileColorChoice>,
    onPick: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    val loaded by produceState<List<ProfileColorChoice>?>(null) { value = runCatching { choices() }.getOrDefault(emptyList()) }
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Profile colour") },
        text = {
            val list = loaded
            when {
                list == null -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                list.isEmpty() -> Text("No colours to choose from right now.")
                else -> FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    list.forEachIndexed { index, choice ->
                        val (container, onContainer) = remember(choice, dark) {
                            com.telegramyou.app.ui.theme.avatarTones(if (dark) choice.color.dark else choice.color.light, dark)
                        }
                        val chosen = choice.color == current
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(SWATCH)
                                .background(Color(container), CircleShape)
                                .border(2.dp, Color(onContainer).copy(alpha = if (chosen) 1f else 0.25f), CircleShape)
                                .clickable { onPick(choice.id) }
                                .semantics {
                                    contentDescription = "Colour ${index + 1}"
                                    selected = chosen
                                }
                        ) {
                            if (chosen) Icon(Symbols.Check, contentDescription = null, tint = Color(onContainer))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = {
            if (current != null) TextButton(onClick = { onPick(null) }) { Text("No colour") }
        }
    )
}

/** Custom emoji to wear after the name, the one in use marked. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun EmojiStatusDialog(
    currentId: Long?,
    choices: suspend () -> List<Long>,
    onPick: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val loaded by produceState<List<Long>?>(null) { value = runCatching { choices() }.getOrDefault(emptyList()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Emoji status") },
        text = {
            val list = loaded
            when {
                list == null -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                list.isEmpty() -> Text("No statuses to choose from right now.")
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(STATUS_CELL),
                    modifier = Modifier.heightIn(max = 320.dp)
                ) {
                    items(list, key = { it }) { id ->
                        val chosen = id == currentId
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(STATUS_CELL)
                                .background(
                                    if (chosen) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                    CircleShape
                                )
                                .clickable { onPick(id) }
                                .semantics {
                                    contentDescription = "Status $id"
                                    selected = chosen
                                }
                        ) {
                            ReactionGlyph(customReactionKey(id), size = 32.dp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = {
            if (currentId != null) TextButton(onClick = { onPick(null) }) { Text("Remove") }
        }
    )
}

/** Telegram lets only Premium accounts wear a colour or a status. */
@Composable
internal fun PremiumNeededDialog(what: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Symbols.WorkspacePremium, contentDescription = null) },
        title = { Text("Telegram Premium") },
        text = { Text("Telegram lets only Premium accounts set $what.") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
    )
}

/** A birthday picked without a year is still placed on a calendar; a leap year keeps the 29th. */
private const val DEFAULT_YEAR = 2000
private val SWATCH = 48.dp
private val STATUS_CELL = 52.dp
