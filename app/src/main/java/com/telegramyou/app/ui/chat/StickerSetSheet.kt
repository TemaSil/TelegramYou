@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.telegramyou.app.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.telegramyou.app.i18n.counted
import com.telegramyou.app.i18n.tr
import com.telegramyou.app.telegram.model.StickerContent
import com.telegramyou.app.telegram.model.StickerSetInfo

/**
 * A sticker set whole (2.6), opened from one of its stickers: its name, how
 * many it holds, every one of them — a tap sends it where there is a chat
 * to send to — and Add or Remove, as the official client's sheet offers.
 * Material's bottom sheet, its filled button for Add and outlined for
 * Remove, which takes something away.
 */
@Composable
internal fun StickerSetSheet(
    set: StickerSetInfo?,
    onDismiss: () -> Unit,
    onInstalledChange: (Boolean) -> Unit,
    onSend: ((StickerContent) -> Unit)? = null
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            if (set == null) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                return@Column
            }
            Text(set.title, style = MaterialTheme.typography.titleLarge)
            Text(
                counted(set.stickers.size, "sticker", "stickers", "стикер", "стикера", "стикеров"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(CELL),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .padding(vertical = 16.dp)
            ) {
                items(set.stickers, key = { it.id }) { sticker ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(CELL)
                            .then(if (onSend != null) Modifier.clickable { onSend(sticker) } else Modifier)
                    ) {
                        StickerView(sticker, size = CELL - 8.dp, animate = false)
                    }
                }
            }
            if (set.isInstalled) {
                OutlinedButton(onClick = { onInstalledChange(false) }, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Remove stickers", "Удалить стикеры"))
                }
            } else {
                Button(onClick = { onInstalledChange(true) }, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Add stickers", "Добавить стикеры"))
                }
            }
        }
    }
}

private val CELL = 72.dp
