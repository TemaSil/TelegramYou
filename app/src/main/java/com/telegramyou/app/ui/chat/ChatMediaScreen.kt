package com.telegramyou.app.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.size
import com.telegramyou.app.telegram.model.VideoContent
import com.telegramyou.app.telegram.model.ChatMessage

/**
 * Every photo in one conversation, newest first.
 *
 * A `LazyVerticalGrid` of squares, which is the shape this content is read in
 * — a grid that kept each photo's aspect ratio would be a ragged column of
 * different widths, and the point of a grid is to scan it.
 *
 * Adaptive rather than a fixed column count: three on a phone, more on
 * anything wider, without this screen deciding what device it is on.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChatMediaScreen(
    title: String,
    media: List<ChatMessage>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onOpen: (ChatMessage) -> Unit,
    /** Non-null while one of these is open full screen. */
    viewingPhoto: ChatMessage? = null,
    onPhotoClosed: () -> Unit = {},
    /** Non-null while a video from the grid is playing. */
    viewingVideo: ChatMessage? = null,
    onVideoClosed: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        when {
            isLoading && media.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
            }
            media.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No photos in this chat yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 112.dp),
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(media, key = { it.id }) { message ->
                        MediaTile(message = message, onClick = { onOpen(message) })
                    }
                }
            }
        }

        // The conversation's viewer, not a second one: a photo opened from
        // here zooms and is thrown away exactly as it is from a bubble, and
        // two implementations would drift.
        viewingPhoto?.let { photo ->
            PhotoViewer(
                path = photo.photoPath.orEmpty(),
                caption = photo.text,
                onDismiss = onPhotoClosed
            )
        }

        // And the conversation's player, for the same reason.
        viewingVideo?.video?.let { video ->
            VideoPlayerScreen(
                video = video,
                title = viewingVideo.text,
                onClose = onVideoClosed
            )
        }
    }
}

/** A video's square in the grid: its poster, with a play badge over it. */
@Composable
private fun VideoTile(video: VideoContent, caption: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .semantics(mergeDescendants = true) {
                contentDescription = if (caption.isBlank()) "Video" else "Video, $caption"
            }
    ) {
        video.thumbPath?.let { poster ->
            AsyncImage(
                model = poster,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.45f),
            contentColor = Color.White,
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * One square.
 *
 * Draws a placeholder rather than nothing when the file has not arrived,
 * which is the normal state for anything not scrolled past recently: a tile
 * that appeared only once its bytes did would make the grid rearrange itself
 * while being read.
 */
@Composable
private fun MediaTile(message: ChatMessage, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick)
    ) {
        // A video tile is the poster with a play badge on it. Without the
        // badge the grid would claim a still photo and then start playing
        // when it was tapped, which is the kind of surprise a list of
        // thumbnails should never contain.
        message.video?.let { video ->
            VideoTile(video = video, caption = message.text)
            return@Surface
        }
        val path = message.photoPath
        if (path.isNullOrBlank()) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.Image,
                    contentDescription = message.text.ifBlank { "Photo" },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            AsyncImage(
                model = path,
                contentDescription = message.text.ifBlank { "Photo" },
                // Crop, not Fit: a square tile showing a letterboxed photo is
                // mostly container, and the grid is for finding a picture
                // rather than for looking at one.
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
