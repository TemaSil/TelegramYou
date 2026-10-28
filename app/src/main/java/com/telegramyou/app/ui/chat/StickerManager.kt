package com.telegramyou.app.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.telegramyou.app.i18n.counted
import com.telegramyou.app.i18n.tr
import com.telegramyou.app.telegram.model.StickerSetPreview
import com.telegramyou.app.ui.icons.Symbols

/**
 * The sticker panel's last tab: the account's sets in its order,
 * each with a menu to move it or take it off, and under them the sets
 * Telegram is suggesting, with Add. Here where stickers are picked: 2.6
 * had it as Settings → Stickers, and the owner moved it to where they are
 * used. A row tapped opens the set's sheet.
 */
@Composable
internal fun StickerManagePage(
    sets: List<StickerSetPreview>,
    trending: List<StickerSetPreview>,
    onOpen: (Long) -> Unit,
    onMove: (Long, Int) -> Unit,
    onInstalledChange: (Long, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "mine-header") { Header(tr("Your stickers", "Ваши стикеры")) }
        if (sets.isEmpty()) {
            item(key = "mine-empty") {
                Text(
                    tr("No stickers yet. Add a set below, or from a sticker in a chat.", "Стикеров пока нет. Добавьте набор ниже или из стикера в чате."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
        itemsIndexed(sets, key = { _, set -> "mine-${set.id}" }) { index, set ->
            SetRow(set, onClick = { onOpen(set.id) }) {
                SetMenu(
                    set = set,
                    canMoveUp = index > 0,
                    canMoveDown = index < sets.lastIndex,
                    onMove = onMove,
                    onRemove = { onInstalledChange(set.id, false) }
                )
            }
        }
        if (trending.isNotEmpty()) {
            item(key = "trending-header") { Header(tr("Trending", "Популярные")) }
            items(trending, key = { "trending-${it.id}" }) { set ->
                SetRow(set, onClick = { onOpen(set.id) }) {
                    FilledTonalButton(onClick = { onInstalledChange(set.id, true) }) {
                        Text(tr("Add", "Добавить"))
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun SetRow(set: StickerSetPreview, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    ListItem(
        headlineContent = { Text(set.title) },
        supportingContent = set.count.takeIf { it > 0 }?.let {
            { Text(counted(it, "sticker", "stickers", "стикер", "стикера", "стикеров")) }
        },
        leadingContent = {
            val cover = set.cover
            if (cover != null) {
                StickerView(cover, size = 40.dp, animate = false)
            } else {
                Icon(Symbols.Sticker, contentDescription = null)
            }
        },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick)
    )
}

/** Move up, Move down and Remove, behind the row's own More. */
@Composable
private fun SetMenu(
    set: StickerSetPreview,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Long, Int) -> Unit,
    onRemove: () -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Symbols.MoreVert, contentDescription = tr("${set.title} options", "${set.title}: действия"))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(tr("Move up", "Выше")) },
                enabled = canMoveUp,
                leadingIcon = { Icon(Symbols.ArrowUpward, contentDescription = null) },
                onClick = {
                    open = false
                    onMove(set.id, -1)
                }
            )
            DropdownMenuItem(
                text = { Text(tr("Move down", "Ниже")) },
                enabled = canMoveDown,
                leadingIcon = { Icon(Symbols.ArrowDownward, contentDescription = null) },
                onClick = {
                    open = false
                    onMove(set.id, 1)
                }
            )
            DropdownMenuItem(
                text = { Text(tr("Remove", "Удалить")) },
                leadingIcon = { Icon(Symbols.Delete, contentDescription = null) },
                onClick = {
                    open = false
                    onRemove()
                }
            )
        }
    }
}
