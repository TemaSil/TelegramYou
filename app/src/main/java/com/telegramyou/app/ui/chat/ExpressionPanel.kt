package com.telegramyou.app.ui.chat

import android.view.ContextThemeWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.emoji2.emojipicker.EmojiPickerView
import coil3.compose.AsyncImage
import com.telegramyou.app.telegram.model.GifItem
import com.telegramyou.app.telegram.model.StickerContent
import com.telegramyou.app.ui.icons.Symbols

/** The panel's three tabs, in the order every Telegram client has them. */
enum class ExpressionTab(val label: String) {
    Emoji("Emoji"),
    Gifs("GIFs"),
    Stickers("Stickers")
}

/** The GIF tab: what is typed into its search, and what came back. */
data class GifPickerState(
    val query: String = "",
    val gifs: List<GifItem> = emptyList(),
    val isLoading: Boolean = true
)

/**
 * Emoji, GIFs and stickers, in the keyboard's place — the smiley in the
 * composer opens it, and the keyboard key that takes the smiley's place
 * brings the keyboard back, as in every Telegram client and in Gboard's
 * own emoji panel.
 *
 * Stock parts throughout: a `SecondaryTabRow` for the three, the Jetpack
 * `EmojiPickerView` for emoji — Android's own picker, with its categories,
 * recents and skin tones, rather than a grid of our own — and lazy grids
 * for the rest.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressionPanel(
    tab: ExpressionTab,
    height: Dp,
    stickers: StickerPickerState?,
    gifs: GifPickerState?,
    onTab: (ExpressionTab) -> Unit,
    onEmoji: (String) -> Unit,
    onBackspace: () -> Unit,
    onStickerSetSelected: (Long) -> Unit,
    onStickerPicked: (StickerContent) -> Unit,
    onGifQueryChange: (String) -> Unit,
    onGifVisible: (GifItem) -> Unit,
    onGifPicked: (GifItem) -> Unit,
    /** The navigation bar's height, which the panel runs under and keeps clear. */
    bottomInset: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        Column(Modifier.padding(bottom = bottomInset)) {
            SecondaryTabRow(
                selectedTabIndex = tab.ordinal,
                containerColor = Color.Transparent
            ) {
                ExpressionTab.entries.forEach { entry ->
                    Tab(
                        selected = entry == tab,
                        onClick = { onTab(entry) },
                        text = { Text(entry.label) }
                    )
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    ExpressionTab.Emoji -> EmojiTab(onEmoji = onEmoji, onBackspace = onBackspace)
                    ExpressionTab.Gifs -> GifTab(
                        state = gifs ?: GifPickerState(),
                        onQueryChange = onGifQueryChange,
                        onVisible = onGifVisible,
                        onPick = onGifPicked
                    )
                    ExpressionTab.Stickers -> {
                        if (stickers == null) {
                            Loading()
                        } else {
                            StickerTab(
                                state = stickers,
                                onSetSelected = onStickerSetSelected,
                                onPick = onStickerPicked,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
}

/**
 * Android's emoji picker, in a View. It takes its colours from the View
 * theme it is inflated with, and the activity's is a light one whatever the
 * app is drawing — so it is given the platform's dark or light Material
 * theme to match, and no background of its own, so the panel's shows.
 *
 * Its own keyboard has no backspace, so the panel adds one, as Gboard's
 * emoji page has.
 */
@Composable
private fun EmojiTab(onEmoji: (String) -> Unit, onBackspace: () -> Unit) {
    val picked = rememberUpdatedState(onEmoji)
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Box(Modifier.fillMaxSize()) {
        // Keyed on the brightness: a View's theme is fixed when it is made.
        androidx.compose.runtime.key(dark) {
            AndroidView(
                factory = { context ->
                    val themed = ContextThemeWrapper(
                        context,
                        if (dark) android.R.style.Theme_Material_NoActionBar
                        else android.R.style.Theme_Material_Light_NoActionBar
                    )
                    EmojiPickerView(themed).apply {
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        setOnEmojiPickedListener { item -> picked.value(item.emoji) }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = "Emoji" }
            )
        }
        FilledTonalIconButton(
            onClick = onBackspace,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Symbols.Backspace, contentDescription = "Backspace")
        }
    }
}

/**
 * GIFs: a search over a two-column grid, each playing on its loop as it
 * does in the chat, at its own height — a staggered grid, so none is cut
 * to fit its neighbour. Saved GIFs until something is typed.
 */
@Composable
private fun GifTab(
    state: GifPickerState,
    onQueryChange: (String) -> Unit,
    onVisible: (GifItem) -> Unit,
    onPick: (GifItem) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        TextField(
            value = state.query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search GIFs") },
            leadingIcon = { Icon(Symbols.Search, contentDescription = null) },
            singleLine = true,
            shape = CircleShape,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .semantics { contentDescription = "Search GIFs" }
        )
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when {
                state.isLoading && state.gifs.isEmpty() -> Loading()
                state.gifs.isEmpty() -> Text(
                    if (state.query.isBlank()) "GIFs you save show up here. Search to find more."
                    else "No GIFs for “${state.query}”",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp)
                )
                else -> LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    contentPadding = PaddingValues(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.gifs, key = { it.id }) { gif ->
                        GifTile(gif, onVisible = { onVisible(gif) }, onPick = { onPick(gif) })
                    }
                }
            }
        }
    }
}

@Composable
private fun GifTile(gif: GifItem, onVisible: () -> Unit, onPick: () -> Unit) {
    val video = gif.video
    LaunchedEffect(gif.id, video.path) { if (video.path == null) onVisible() }
    Box(
        modifier = Modifier
            .padding(4.dp)
            .fillMaxWidth()
            .aspectRatio(video.aspect.coerceIn(0.6f, 1.9f))
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .semantics { contentDescription = "GIF" }
    ) {
        video.thumbPath?.let { poster ->
            AsyncImage(
                model = poster,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        video.path?.let { path ->
            InlineVideo(path = path, playing = true, muted = true, loop = true, modifier = Modifier.fillMaxSize())
        }
        // The tap is taken on top of the clip, not by the tile under it: the
        // player is an Android View, and a View takes the touches that land
        // on it before anything behind it in Compose sees them.
        Box(
            Modifier
                .matchParentSize()
                .clickable(onClick = onPick)
        )
    }
}
