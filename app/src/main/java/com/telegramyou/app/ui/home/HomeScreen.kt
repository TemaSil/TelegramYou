package com.telegramyou.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.telegramyou.app.telegram.model.StoryItem
import com.telegramyou.app.ui.components.AvatarBubble
import com.telegramyou.app.ui.components.ChatListRow
import com.telegramyou.app.ui.components.StoriesRail

/**
 * The chat list.
 *
 * Takes a [HomeUiState] and callbacks — no repository, no coroutine scope,
 * nothing remembered that a rotation would lose. Everything it needs to draw
 * itself arrives in [state]; everything it wants to happen leaves through a
 * callback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onRefresh: () -> Unit,
    onOpenChat: (Long) -> Unit,
    onOpenStory: (StoryItem) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "TelegramYou",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            state.me?.displayName ?: "Material You",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Rounded.Search, contentDescription = "Search")
                    }
                    AvatarBubble(
                        title = state.me?.displayName ?: "You",
                        seed = state.me?.avatarColor ?: 1,
                        size = 36.dp,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.01f)
                ),
                modifier = Modifier.statusBarsPadding()
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { state.chats.firstOrNull()?.let { onOpenChat(it.id) } },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.large
            ) {
                Icon(Icons.Rounded.Edit, contentDescription = "Compose")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.06f)
                        )
                    )
                )
        ) {
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        StoriesRail(
                            stories = state.stories,
                            onStoryClick = onOpenStory
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    // No AnimatedVisibility here. It wrapped every row with
                    // visible = true, which never transitions, so the enter
                    // animation could not run — a composition layer that cost
                    // something and did nothing.
                    itemsIndexed(state.chats, key = { _, chat -> chat.id }) { _, chat ->
                        ChatListRow(
                            chat = chat,
                            onClick = { onOpenChat(chat.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                        )
                    }
                }
            }
        }
    }
}
