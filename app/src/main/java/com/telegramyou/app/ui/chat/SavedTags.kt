package com.telegramyou.app.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Spacer
import com.telegramyou.app.i18n.tr
import com.telegramyou.app.telegram.model.MAX_SAVED_TAG_LABEL
import com.telegramyou.app.telegram.model.SavedTag
import com.telegramyou.app.ui.icons.Symbols

/**
 * Saved Messages' tags (2.7) as a row of Material's filter chips under the
 * header: each the reaction that is the tag, its name or its count, and a
 * tap to show only what carries it. The chip chosen has a pencil beside it,
 * to name it — Telegram keeps one name per tag across Saved Messages.
 */
@Composable
internal fun SavedTagRow(
    tags: List<SavedTag>,
    selected: String?,
    onSelect: (String) -> Unit,
    onName: (SavedTag) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(tags, key = { it.reaction }) { tag ->
            val chosen = tag.reaction == selected
            FilterChip(
                selected = chosen,
                onClick = { onSelect(tag.reaction) },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ReactionGlyph(tag.reaction, size = 18.dp, animate = false)
                        Spacer(Modifier.width(6.dp))
                        Text(tag.label.ifBlank { tag.count.toString() })
                    }
                },
                modifier = Modifier.semantics {
                    contentDescription = tr("Tag ${tag.label.ifBlank { tag.reaction }}", "Тег ${tag.label.ifBlank { tag.reaction }}")
                }
            )
        }
        // Beside the chosen tag, its own button rather than an icon inside
        // the chip, which would be merged into the chip's one action.
        val chosenTag = tags.firstOrNull { it.reaction == selected }
        if (chosenTag != null) {
            item(key = "name") {
                IconButton(onClick = { onName(chosenTag) }) {
                    Icon(Symbols.Edit, contentDescription = tr("Name this tag", "Назвать тег"))
                }
            }
        }
    }
}

/** A tag's name, typed; blank takes it off. */
@Composable
internal fun SavedTagNameDialog(tag: SavedTag, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember(tag.reaction) { mutableStateOf(tag.label) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { ReactionGlyph(tag.reaction, size = 32.dp, animate = false) },
        title = { Text(tr("Name this tag", "Назвать тег")) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_SAVED_TAG_LABEL) },
                singleLine = true,
                label = { Text(tr("Name", "Название")) },
                supportingText = { Text("${name.length} / $MAX_SAVED_TAG_LABEL") }
            )
        },
        confirmButton = { TextButton(onClick = { onSave(name) }) { Text(tr("Save", "Сохранить")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "Отмена")) } }
    )
}
