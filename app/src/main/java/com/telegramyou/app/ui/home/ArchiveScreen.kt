package com.telegramyou.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.telegramyou.app.telegram.model.ChatPreview
import com.telegramyou.app.ui.components.ChatListRow

/**
 * The chats that have been put away.
 *
 * The same rows as the main list, on the same panel, with the same swipes —
 * an archive that looked like somewhere else would make putting a chat in it
 * feel like losing it. What differs is the direction of one gesture: swiping
 * right here takes a chat back out rather than pinning it, because pinning
 * inside the archive is a preference about the order of a list nobody is
 * looking at.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(
    chats: List<ChatPreview>,
    onBack: () -> Unit,
    onOpenChat: (Long) -> Unit,
    onMutedChange: (Long, Boolean) -> Unit,
    onUnarchive: (Long) -> Unit,
    onMarkRead: (Long) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Archive") },
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
        if (chats.isEmpty()) {
            // Reachable: the last chat can be taken out while this screen is
            // open, and the row that led here is already gone by then.
            EmptyArchive(contentPadding = padding)
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            itemsIndexed(chats, key = { _, chat -> chat.id }) { index, chat ->
                ChatListRow(
                    chat = chat,
                    index = index,
                    count = chats.size,
                    onClick = { onOpenChat(chat.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    onMutedChange = { muted -> onMutedChange(chat.id, muted) },
                    // Right takes it out of here. Same gesture, opposite
                    // meaning to the main list, which is the one difference
                    // this screen makes.
                    onPinnedChange = { onUnarchive(chat.id) },
                    swipeStartLabel = "Unarchive",
                    onMarkRead = { onMarkRead(chat.id) }
                )
            }
        }
    }
}

@Composable
private fun EmptyArchive(contentPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Nothing is archived",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
