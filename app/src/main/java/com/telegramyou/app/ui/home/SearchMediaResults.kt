package com.telegramyou.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.telegramyou.app.i18n.tr
import com.telegramyou.app.telegram.model.MediaSearchKind
import com.telegramyou.app.telegram.model.MessageContentType
import com.telegramyou.app.ui.chat.BlurredMini
import com.telegramyou.app.ui.chat.isMediaCaption
import com.telegramyou.app.ui.icons.Symbols

/** The same query, searched on the server by content type, with its own cursor. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SearchMediaResults(search: SearchState, actions: SearchActions, padding: PaddingValues) {
    val page = search.media[search.scope.mediaKind] ?: MediaSearchState()
    val list = rememberLazyListState()
    val last = list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
    LaunchedEffect(last, page.items.size, page.isLoading) {
        if (last >= page.items.size - 5) actions.onMediaNearEnd()
    }
    LazyColumn(state = list, contentPadding = padding) {
        items(page.items, key = { "${it.chat.id}:${it.message.id}" }) { hit ->
            val message = hit.message
            val files = search.scope.mediaKind == MediaSearchKind.Files
            val title = message.fileName?.takeIf { it.isNotBlank() }
                ?: message.text.takeIf(::isMediaCaption)
                ?: if (message.contentType == MessageContentType.Video) tr("Video", "Видео") else tr("Photo", "Фото")
            ListItem(
                headlineContent = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Column {
                    Text(hit.chat.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val date = remember(message.date) {
                        java.time.Instant.ofEpochSecond(message.date).atZone(java.time.ZoneId.systemDefault())
                            .format(java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.SHORT))
                    }
                    Text(date, style = MaterialTheme.typography.labelSmall)
                } },
                leadingContent = {
                    Box(Modifier.size(64.dp).clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                        val image = message.photoPath ?: message.video?.thumbPath
                        val mini = message.photoMini ?: message.video?.mini
                        if (image != null) {
                            AsyncImage(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else if (mini != null && !files) {
                            BlurredMini(mini, Modifier.fillMaxSize())
                        } else {
                            Icon(if (files) Symbols.Description else Symbols.Image, contentDescription = null)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().clickable(onClickLabel = tr("Show in chat", "Показать в чате")) { actions.onOpenMessage(hit) }
            )
        }
        if (page.isLoading || (!page.isLoaded && page.error == null)) item {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
        }
        if (page.error != null) item {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(page.error)
                TextButton(onClick = actions.onMediaRetry) { Text(tr("Retry", "Повторить")) }
            }
        }
        if (page.isLoaded && !page.isLoading && page.error == null && page.items.isEmpty() && page.nextOffset == null) item {
            Text(tr("Nothing found for “${search.query}”", "По запросу «${search.query}» ничего не найдено"),
                Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
