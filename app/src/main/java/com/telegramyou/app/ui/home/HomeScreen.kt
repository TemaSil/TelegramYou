package com.telegramyou.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.telegramyou.app.telegram.model.StoryItem
import com.telegramyou.app.telegram.model.MessageHit
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
    onOpenStory: (StoryItem) -> Unit,
    onSearchExpandedChange: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    Scaffold(
        topBar = {
            // Expanded, the SearchBar takes the whole screen and its own
            // results belong to it — so the app bar underneath would only be
            // something to see through it.
            if (state.search.expanded) {
                ChatSearchBar(
                    search = state.search,
                    onQueryChange = onSearchQueryChange,
                    onExpandedChange = onSearchExpandedChange,
                    onOpenChat = { id ->
                        onSearchExpandedChange(false)
                        onOpenChat(id)
                    }
                )
                return@Scaffold
            }
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
                    IconButton(onClick = { onSearchExpandedChange(true) }) {
                        Icon(Icons.Rounded.Search, contentDescription = "Search")
                    }
                    // The avatar is the way into settings, the way it is in
                    // every other app on the phone — a gear beside it would be
                    // a second control for the same destination.
                    AvatarBubble(
                        title = state.me?.displayName ?: "You",
                        seed = state.me?.avatarColor ?: 1,
                        size = 36.dp,
                        onClick = onOpenSettings,
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

/**
 * Search across chats.
 *
 * Material's own [SearchBar], expanded to fill the screen, with the results
 * as [ChatListRow]s — the same row as the list behind it, because a result
 * and a chat are the same thing and should not look like two.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatSearchBar(
    search: SearchState,
    onQueryChange: (String) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    onOpenChat: (Long) -> Unit
) {
    SearchBar(
        expanded = true,
        onExpandedChange = onExpandedChange,
        inputField = {
            SearchBarDefaults.InputField(
                query = search.query,
                onQueryChange = onQueryChange,
                onSearch = {},
                expanded = true,
                onExpandedChange = onExpandedChange,
                placeholder = { Text("Search chats") },
                leadingIcon = {
                    IconButton(onClick = { onExpandedChange(false) }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Close search"
                        )
                    }
                },
                trailingIcon = {
                    if (search.isSearching) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else if (search.query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear")
                        }
                    }
                }
            )
        }
    ) {
        LazyColumn(
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (search.results.isNotEmpty()) {
                item(key = "chats-header") { SearchSectionHeader("Chats") }
            }
            items(search.results, key = { "chat-${it.id}" }) { chat ->
                ChatListRow(
                    chat = chat,
                    onClick = { onOpenChat(chat.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                )
            }
            if (search.messages.isNotEmpty()) {
                item(key = "messages-header") { SearchSectionHeader("Messages") }
            }
            items(
                search.messages,
                // A message id is only unique within its chat, so the chat
                // has to be part of the key or two hits can collide.
                key = { "msg-${it.chat.id}-${it.message.id}" }
            ) { hit ->
                MessageHitRow(hit = hit, onClick = { onOpenChat(hit.chat.id) })
            }
            // Said only once the search has actually looked, so a slow query
            // does not report failure before it has an answer.
            if (search.query.isNotBlank() && !search.isSearching && search.isEmpty) {
                item {
                    Text(
                        text = "Nothing found for \u201C${search.query}\u201D",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
    )
}

/**
 * One message found by search, under the conversation it came from.
 *
 * A [ListItem] like the chat rows, but the roles are swapped: the chat title
 * is the headline and the message text the supporting line, because what
 * identifies a hit is where it was said.
 */
@Composable
private fun MessageHitRow(hit: MessageHit, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        leadingContent = {
            AvatarBubble(
                title = hit.chat.title,
                seed = hit.chat.avatarColor,
                size = 40.dp
            )
        },
        headlineContent = {
            Text(hit.chat.title, fontWeight = FontWeight.Bold, maxLines = 1)
        },
        supportingContent = {
            Text(hit.message.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = {
            Text(
                text = hit.message.timeLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}
