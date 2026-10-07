package com.telegramyou.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.telegramyou.app.telegram.model.InlineResult
import com.telegramyou.app.telegram.model.InlineResultKind
import com.telegramyou.app.ui.icons.Symbols
import java.io.File

/**
 * An inline bot's answers, over the composer (2.0): what is typed after
 * "@bot " in the field, answered by the bot, and the one tapped sent.
 *
 * Two shapes, as the answers are: pictures — photos, GIFs, videos, stickers
 * — as a grid of tiles, since a picture is chosen by looking at it; anything
 * else as Material's list rows, title over description, with a thumbnail
 * where the bot gave one and an icon for the kind where it did not.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun InlineResultsPanel(
    panel: InlinePanel,
    onPick: (InlineResult) -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics { contentDescription = "Results from @${panel.bot.username}" }
    ) {
        Box {
            val results = panel.results
            when {
                results.isEmpty() && panel.isLoading -> Box(Modifier.fillMaxWidth().heightIn(min = 72.dp))
                results.isEmpty() -> Text(
                    if (panel.query.isBlank() && panel.bot.placeholder.isNotBlank()) panel.bot.placeholder
                    else "No results from @${panel.bot.username}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
                )
                results.all { it.isVisual } -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(96.dp),
                    contentPadding = PaddingValues(8.dp),
                    modifier = Modifier.heightIn(max = PANEL_MAX)
                ) {
                    itemsIndexed(results, key = { _, it -> it.id }) { index, result ->
                        if (index == results.lastIndex) LaunchedEffect(results.size) { onMore() }
                        ResultTile(result, onPick)
                    }
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp),
                    modifier = Modifier.heightIn(max = PANEL_MAX)
                ) {
                    itemsIndexed(results, key = { _, it -> it.id }) { index, result ->
                        if (index == results.lastIndex) LaunchedEffect(results.size) { onMore() }
                        ResultRow(result, onPick)
                    }
                }
            }
            if (panel.isLoading) {
                LinearWavyProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ResultTile(result: InlineResult, onPick: (InlineResult) -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(4.dp)
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable { onPick(result) }
            .semantics { contentDescription = result.title.ifBlank { kindLabel(result.kind) } }
    ) {
        if (result.thumbPath != null) {
            AsyncImage(
                model = File(result.thumbPath),
                contentDescription = null,
                contentScale = if (result.kind == InlineResultKind.Sticker) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        } else {
            Icon(kindIcon(result.kind), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ResultRow(result: InlineResult, onPick: (InlineResult) -> Unit) {
    ListItem(
        headlineContent = {
            Text(result.title.ifBlank { kindLabel(result.kind) }, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = if (result.description.isNotBlank()) {
            { Text(result.description, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        } else {
            null
        },
        leadingContent = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
            ) {
                if (result.thumbPath != null) {
                    AsyncImage(
                        model = File(result.thumbPath),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                } else {
                    Icon(kindIcon(result.kind), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.clickable { onPick(result) }
    )
}

private fun kindIcon(kind: InlineResultKind): ImageVector = when (kind) {
    InlineResultKind.Photo -> Symbols.Image
    InlineResultKind.Gif, InlineResultKind.Video -> Symbols.Gif
    InlineResultKind.Sticker -> Symbols.EmojiEmotions
    InlineResultKind.Audio -> Symbols.MusicNote
    InlineResultKind.Voice -> Symbols.Mic
    InlineResultKind.Place -> Symbols.LocationOn
    InlineResultKind.Contact -> Symbols.Person
    InlineResultKind.Game -> Symbols.SportsEsports
    InlineResultKind.Article, InlineResultKind.Document -> Symbols.Description
}

private fun kindLabel(kind: InlineResultKind): String = when (kind) {
    InlineResultKind.Article -> "Article"
    InlineResultKind.Photo -> "Photo"
    InlineResultKind.Gif -> "GIF"
    InlineResultKind.Video -> "Video"
    InlineResultKind.Sticker -> "Sticker"
    InlineResultKind.Audio -> "Audio"
    InlineResultKind.Voice -> "Voice message"
    InlineResultKind.Document -> "File"
    InlineResultKind.Place -> "Place"
    InlineResultKind.Contact -> "Contact"
    InlineResultKind.Game -> "Game"
}

/** About three rows, or two rows of tiles: the conversation stays in sight above it. */
private val PANEL_MAX = 264.dp
