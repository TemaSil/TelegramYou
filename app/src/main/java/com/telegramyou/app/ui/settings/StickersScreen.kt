@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.telegramyou.app.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.telegramyou.app.i18n.counted
import com.telegramyou.app.i18n.tr
import com.telegramyou.app.telegram.model.StickerSetPreview
import com.telegramyou.app.ui.chat.StickerSetSheet
import com.telegramyou.app.ui.chat.StickerView
import com.telegramyou.app.ui.icons.Symbols

/**
 * Settings → Stickers (2.6): your sets in your order, each with a menu to
 * move it up or down or take it off — the same menu Chat folders has for
 * the same job — and below them the sets Telegram is suggesting, a tap to
 * look through and Add to keep.
 */
@Composable
fun StickersScreen(
    state: StickersUiState,
    onBack: () -> Unit,
    onMove: (Long, Int) -> Unit,
    onInstalledChange: (Long, Boolean) -> Unit,
    onOpen: (Long) -> Unit,
    onClose: () -> Unit,
    onMessageShown: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onMessageShown()
    }
    state.open?.let { id ->
        StickerSetSheet(
            set = state.openInfo,
            onDismiss = onClose,
            onInstalledChange = { installed -> onInstalledChange(id, installed) }
        )
    }
    Scaffold(
        containerColor = settingsBackground(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(tr("Stickers", "Стикеры")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Symbols.ArrowBack, contentDescription = tr("Back", "Назад"))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = settingsBackground())
            )
        }
    ) { padding ->
        val mine = state.mine
        if (mine == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingIndicator() }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item(key = "mine") {
                if (mine.isEmpty()) {
                    Text(
                        tr("You have no stickers yet. Add a set below, or from a sticker in a chat.", "Стикеров пока нет. Добавьте набор ниже или из стикера в чате."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    SettingsGroup(tr("Your stickers", "Ваши стикеры")) {
                        mine.forEachIndexed { index, set ->
                            item(
                                title = set.title,
                                summary = countOf(set),
                                leading = { SetCover(set) },
                                trailing = {
                                    SetMenu(
                                        set = set,
                                        canMoveUp = index > 0,
                                        canMoveDown = index < mine.lastIndex,
                                        onMove = onMove,
                                        onRemove = { onInstalledChange(set.id, false) }
                                    )
                                },
                                onClick = { onOpen(set.id) }
                            )
                        }
                    }
                }
            }
            if (state.trending.isNotEmpty()) {
                item(key = "trending") {
                    SettingsGroup(tr("Trending", "Популярные")) {
                        state.trending.forEach { set ->
                            item(
                                title = set.title,
                                summary = countOf(set),
                                leading = { SetCover(set) },
                                trailing = {
                                    FilledTonalButton(onClick = { onInstalledChange(set.id, true) }) {
                                        Text(tr("Add", "Добавить"))
                                    }
                                },
                                onClick = { onOpen(set.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun countOf(set: StickerSetPreview): String? =
    set.count.takeIf { it > 0 }?.let { counted(it, "sticker", "stickers", "стикер", "стикера", "стикеров") }

@Composable
private fun SetCover(set: StickerSetPreview) {
    val cover = set.cover
    if (cover != null) {
        StickerView(cover, size = 40.dp, animate = false)
    } else {
        Icon(Symbols.Sticker, contentDescription = null)
    }
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
