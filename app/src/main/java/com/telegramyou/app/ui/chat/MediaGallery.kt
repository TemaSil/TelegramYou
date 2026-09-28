package com.telegramyou.app.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.telegramyou.app.telegram.model.ChatMessage
import com.telegramyou.app.ui.media.FileTransfer
import com.telegramyou.app.telegram.model.MessageContentType

/**
 * A chat's photos and videos, full-screen, one swipe apart — as the official
 * client pages through them. [items] come from galleryOf; the pager opens on
 * [startId] and keeps each page by its message id, so older media loading in
 * behind does not move the one being looked at.
 *
 * Each page is the viewer it always was: a photo zooms, pans and is thrown
 * away by a drag down (PhotoPage), a video plays with its own controls
 * (VideoPage) — only the page in view, so a neighbour holds no player. The
 * pager is still while a photo is zoomed: a sideways drag then moves the
 * photo, not the gallery.
 *
 * [onPage] is told which message came into view, so what it needs — a
 * photo's full size, a video's file — is fetched as it is reached.
 */
@Composable
fun MediaGallery(
    items: List<ChatMessage>,
    startId: Long,
    transfers: Map<Int, FileTransfer>,
    onPage: (ChatMessage) -> Unit,
    onDismiss: () -> Unit
) {
    if (items.isEmpty()) return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val pager = rememberPagerState(
            initialPage = items.indexOfFirst { it.id == startId }.coerceAtLeast(0)
        ) { items.size }
        var zoomed by remember { mutableStateOf(false) }
        val latestItems by rememberUpdatedState(items)
        val latestOnPage by rememberUpdatedState(onPage)
        LaunchedEffect(pager.currentPage) {
            zoomed = false
            latestItems.getOrNull(pager.currentPage)?.let(latestOnPage)
        }
        Box(Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pager,
                userScrollEnabled = !zoomed,
                key = { page -> items[page].id },
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val message = items[page]
                val video = message.video
                if (message.contentType == MessageContentType.Video && video != null) {
                    VideoPage(
                        video = video,
                        title = message.text,
                        transfer = video.fileId?.let { transfers[it] },
                        active = page == pager.currentPage,
                        onClose = onDismiss
                    )
                } else {
                    PhotoPage(
                        path = message.photoPath,
                        caption = message.text,
                        onDismiss = onDismiss,
                        onZoomChanged = { if (page == pager.currentPage) zoomed = it }
                    )
                }
            }
            if (items.size > 1) {
                Text(
                    "${pager.currentPage + 1} of ${items.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 20.dp)
                )
            }
        }
    }
}
