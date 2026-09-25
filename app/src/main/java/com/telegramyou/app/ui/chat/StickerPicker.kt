package com.telegramyou.app.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.telegramyou.app.telegram.model.StickerContent
import com.telegramyou.app.telegram.model.StickerSetPreview

/** What the sticker sheet shows: the sets as tabs, and the chosen one's stickers. */
data class StickerPickerState(
    val sets: List<StickerSetPreview> = emptyList(),
    /** [RECENT_STICKERS] for the recent tab, else a set's id. */
    val selected: Long = RECENT_STICKERS,
    val stickers: List<StickerContent> = emptyList(),
    val isLoading: Boolean = true
)

/** The picker's first tab, which is not a set: what was sent lately. */
const val RECENT_STICKERS = -1L

/**
 * The stickers, in a sheet from the composer: a scrollable tab row of the
 * account's sets — recent first — over a grid of the chosen one. A tap sends
 * and closes, which is what every Telegram client does; a sticker is not
 * something anybody captions.
 *
 * Material's own parts for it: `ModalBottomSheet`, `PrimaryScrollableTabRow`
 * with the set's cover as each tab's icon, and a lazy grid.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StickerPickerSheet(
    state: StickerPickerState,
    onSetSelected: (Long) -> Unit,
    onPick: (StickerContent) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        val tabs = listOf(RECENT_STICKERS) + state.sets.map { it.id }
        PrimaryScrollableTabRow(
            selectedTabIndex = tabs.indexOf(state.selected).coerceAtLeast(0),
            edgePadding = 12.dp,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Tab(
                selected = state.selected == RECENT_STICKERS,
                onClick = { onSetSelected(RECENT_STICKERS) },
                icon = { Icon(Icons.Rounded.History, contentDescription = "Recent stickers") }
            )
            state.sets.forEach { set ->
                Tab(
                    selected = state.selected == set.id,
                    onClick = { onSetSelected(set.id) },
                    modifier = Modifier.semantics { contentDescription = set.title },
                    icon = {
                        val cover = set.cover
                        if (cover != null) {
                            StickerView(cover, size = 28.dp, animate = false)
                        } else {
                            Text(set.title.take(1))
                        }
                    }
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                state.isLoading -> LoadingIndicator()
                state.stickers.isEmpty() -> Text(
                    if (state.selected == RECENT_STICKERS) {
                        "Stickers you send show up here"
                    } else {
                        "This set is empty"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp)
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 76.dp),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    items(state.stickers, key = { it.id to it.fileId }) { sticker ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .padding(4.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onPick(sticker) }
                                .semantics { contentDescription = "${sticker.emoji} sticker" }
                                .padding(4.dp)
                        ) {
                            StickerView(sticker, size = 64.dp, animate = false)
                        }
                    }
                }
            }
        }
    }
}
