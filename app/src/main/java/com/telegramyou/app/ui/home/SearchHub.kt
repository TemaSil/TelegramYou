package com.telegramyou.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.telegramyou.app.settings.LocalGeekSettings
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.telegramyou.app.telegram.model.ChatPreview
import com.telegramyou.app.telegram.model.MessageHit
import com.telegramyou.app.telegram.model.SearchScope
import com.telegramyou.app.ui.components.AvatarBubble
import com.telegramyou.app.ui.components.ChatListRow
import com.telegramyou.app.ui.components.personShape

/**
 * What search can do besides take a query — grouped, because there are
 * eight of them and HomeScreen's parameter list is long enough already.
 * Every one defaults to nothing, for previews and tests.
 */
data class SearchActions(
    val onScopeChange: (SearchScope) -> Unit = {},
    val onSubmit: () -> Unit = {},
    val onSearchPosts: () -> Unit = {},
    val onResultOpened: (Long) -> Unit = {},
    val onRecentQueryPicked: (String) -> Unit = {},
    val onRecentQueriesCleared: () -> Unit = {},
    val onRecentChatRemoved: (Long) -> Unit = {},
    val onRecentChatsCleared: () -> Unit = {}
)

/**
 * Search, as a section of its own rather than a filter over the chat list.
 *
 * Material's [SearchBar], expanded to the whole screen. Empty, it is a front
 * page — the people written to most, the chats found before, the words
 * searched for before, channels Telegram suggests — which is where a search
 * usually ends before anything is typed. With a query, tabs choose between
 * chats of each kind, messages in this account's chats, and posts in public
 * channels anywhere on Telegram.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchSection(
    search: SearchState,
    onQueryChange: (String) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    actions: SearchActions,
    onOpenChat: (Long) -> Unit
) {
    // The field waits with the caret in it and the keyboard up, unless
    // Settings → For geeks says to open search without it.
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val withoutKeyboard = LocalGeekSettings.current.searchWithoutKeyboard
    LaunchedEffect(Unit) {
        if (withoutKeyboard) return@LaunchedEffect
        // A frame first: the bar is still being laid out when this starts,
        // and focus asked of a node not yet attached is dropped.
        withFrameNanos { }
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }
    SearchBar(
        expanded = true,
        onExpandedChange = onExpandedChange,
        inputField = {
            SearchBarDefaults.InputField(
                modifier = Modifier.focusRequester(focus),
                query = search.query,
                onQueryChange = onQueryChange,
                onSearch = { actions.onSubmit() },
                expanded = true,
                onExpandedChange = onExpandedChange,
                placeholder = { Text("Search Telegram") },
                leadingIcon = {
                    IconButton(onClick = { onExpandedChange(false) }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Close search")
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
        if (search.query.isBlank()) {
            SearchFrontPage(search = search, actions = actions, onOpenChat = onOpenChat)
        } else {
            Column {
                SecondaryScrollableTabRow(
                    selectedTabIndex = search.scope.ordinal,
                    edgePadding = 16.dp
                ) {
                    SearchScope.entries.forEach { scope ->
                        Tab(
                            selected = scope == search.scope,
                            onClick = { actions.onScopeChange(scope) },
                            text = { Text(scope.label) }
                        )
                    }
                }
                SearchResults(search = search, actions = actions, onOpenChat = onOpenChat)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchFrontPage(
    search: SearchState,
    actions: SearchActions,
    onOpenChat: (Long) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        if (search.topPeople.isNotEmpty()) {
            item(key = "people-header") { SearchSectionHeader("People") }
            item(key = "people") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(search.topPeople, key = { it.id }) { person ->
                        PersonTile(person = person, onClick = { onOpenChat(person.id) })
                    }
                }
            }
        }
        if (search.recentQueries.isNotEmpty()) {
            item(key = "queries-header") {
                SearchSectionHeader("Recent searches", action = "Clear", onAction = actions.onRecentQueriesCleared)
            }
            item(key = "queries") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    search.recentQueries.forEach { query ->
                        SuggestionChip(
                            onClick = { actions.onRecentQueryPicked(query) },
                            label = { Text(query, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            icon = { Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                }
            }
        }
        if (search.recentChats.isNotEmpty()) {
            item(key = "recent-header") {
                SearchSectionHeader("Recent", action = "Clear", onAction = actions.onRecentChatsCleared)
            }
            items(search.recentChats, key = { "recent-${it.id}" }) { chat ->
                SearchChatRow(
                    chat = chat,
                    onClick = { onOpenChat(chat.id) },
                    trailing = {
                        IconButton(onClick = { actions.onRecentChatRemoved(chat.id) }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Remove ${chat.title} from recent")
                        }
                    }
                )
            }
        }
        if (search.recommended.isNotEmpty()) {
            item(key = "recommended-header") { SearchSectionHeader("Channels for you") }
            items(search.recommended, key = { "recommended-${it.id}" }) { chat ->
                SearchChatRow(chat = chat, onClick = { onOpenChat(chat.id) })
            }
        }
        if (search.topPeople.isEmpty() && search.recentChats.isEmpty() &&
            search.recentQueries.isEmpty() && search.recommended.isEmpty()
        ) {
            item(key = "hint") {
                Text(
                    "Search for people, groups, channels, bots and posts",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(24.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchResults(
    search: SearchState,
    actions: SearchActions,
    onOpenChat: (Long) -> Unit
) {
    val scope = search.scope
    val chats = search.visibleChats
    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (scope.showsChats) {
            if (chats.mine.isNotEmpty()) {
                item(key = "mine-header") { SearchSectionHeader(if (scope == SearchScope.All) "Chats" else "Yours") }
            }
            items(chats.mine, key = { "mine-${it.id}" }) { chat ->
                ChatListRow(
                    chat = chat,
                    onClick = { onOpenChat(chat.id) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                )
            }
            if (chats.global.isNotEmpty()) {
                item(key = "global-header") { SearchSectionHeader("Global search") }
            }
            items(chats.global, key = { "global-${it.id}" }) { chat ->
                SearchChatRow(chat = chat, onClick = { onOpenChat(chat.id) })
            }
        }
        if (scope.showsMessages && search.messages.isNotEmpty()) {
            item(key = "messages-header") { SearchSectionHeader("Messages") }
            items(
                search.messages,
                // A message id is only unique within its chat, so the chat
                // has to be part of the key or two hits can collide.
                key = { "msg-${it.chat.id}-${it.message.id}" }
            ) { hit ->
                MessageHitRow(hit = hit, onClick = { onOpenChat(hit.chat.id) })
            }
        }
        if (scope.showsPosts) {
            val posts = search.posts
            when {
                search.isSearchingPosts -> item(key = "posts-loading") {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }
                }
                posts == null -> item(key = "posts-ask") {
                    // Asked for, not searched as you type: Telegram gives a
                    // few free post searches a day, and each pause in typing
                    // would otherwise spend one.
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(24.dp)
                    ) {
                        Text(
                            "Posts from public channels across Telegram",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.padding(top = 12.dp))
                        Button(onClick = actions.onSearchPosts) { Text("Search posts") }
                    }
                }
                else -> {
                    posts.limitLabel?.let { label ->
                        item(key = "posts-limit") {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                            )
                        }
                    }
                    items(posts.hits, key = { "post-${it.chat.id}-${it.message.id}" }) { hit ->
                        MessageHitRow(hit = hit, onClick = { onOpenChat(hit.chat.id) })
                    }
                    if (posts.hits.isEmpty() && !posts.limitReached) {
                        item(key = "posts-none") { NothingFound("No public posts about “${search.query}”") }
                    }
                }
            }
        }
        // Said only once the search has actually looked, so a slow query
        // does not report failure before it has an answer.
        val nothing = when {
            scope.showsPosts -> false
            scope == SearchScope.Messages -> search.messages.isEmpty()
            scope == SearchScope.All -> search.isEmpty
            else -> chats.isEmpty
        }
        if (!search.isSearching && nothing) {
            item(key = "nothing") { NothingFound("Nothing found for “${search.query}”") }
        }
    }
}

@Composable
private fun NothingFound(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
    )
}

@Composable
private fun SearchSectionHeader(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f).padding(vertical = 12.dp)
        )
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

/** One of the people written to most: a face and a first name. */
@Composable
private fun PersonTile(person: ChatPreview, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
    ) {
        AvatarBubble(
            title = person.title,
            seed = person.avatarColor,
            size = 56.dp,
            shape = personShape(person.avatarColor),
            showOnline = person.isOnline,
            photoPath = person.photoPath
        )
        Spacer(Modifier.padding(top = 6.dp))
        Text(
            person.title.substringBefore(' '),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** What kind of chat a row is, for chats that are not in the list yet. */
private fun ChatPreview.kindLabel(): String = when {
    isBot -> "Bot"
    isChannel -> "Channel"
    isGroup -> "Group"
    else -> "Chat"
}

/**
 * A chat found or suggested, as a [ListItem]: the kind of chat under its
 * name, since for one not yet joined that is the first thing worth knowing,
 * and its last message is often something it has not been sent yet.
 */
@Composable
private fun SearchChatRow(
    chat: ChatPreview,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        leadingContent = {
            AvatarBubble(
                title = chat.title,
                seed = chat.avatarColor,
                size = 40.dp,
                shape = personShape(chat.avatarColor),
                photoPath = chat.photoPath
            )
        },
        headlineContent = { Text(chat.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            val about = chat.lastMessage.takeIf { it.isNotBlank() }
            Text(
                if (about != null) "${chat.kindLabel()} · $about" else chat.kindLabel(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = trailing
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
                size = 40.dp,
                shape = personShape(hit.chat.avatarColor),
                photoPath = hit.chat.photoPath
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
