package com.telegramyou.app.ui.home

import com.telegramyou.app.ui.icons.Symbols
import com.telegramyou.app.ui.settings.settingsBackground
import com.telegramyou.app.ui.motion.ChatContainerSpring
import com.telegramyou.app.ui.motion.ChatContainerShape
import com.telegramyou.app.ui.motion.chatContainerKey
import com.telegramyou.app.ui.motion.containerTransform
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.SegmentedListItem
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
import com.telegramyou.app.telegram.model.ChatMessage
import com.telegramyou.app.telegram.model.ChatPreview
import com.telegramyou.app.telegram.model.MessageHit
import com.telegramyou.app.telegram.model.SearchScope
import com.telegramyou.app.ui.components.AvatarBubble
import com.telegramyou.app.ui.components.ChatListRow
import com.telegramyou.app.ui.components.personShape
import com.telegramyou.app.ui.chat.formatDuration
import androidx.compose.material3.FilledTonalIconButton

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
    val onRecentChatsCleared: () -> Unit = {},
    /** A track on the Music tab: it plays, what was found as the queue. */
    val onPlayTrack: (ChatMessage) -> Unit = {}
)

/**
 * The Search tab: a page like Settings — its name, large, folding away as
 * the page scrolls — with the search field standing on it just below, as a
 * pill, the way Gmail and Android's own Settings put theirs. Not a sheet
 * drawn over everything: the field is part of the page, and what it finds
 * is the page's content.
 *
 * Empty, the page is a front page — the people written to most, the chats
 * found before, the words searched for before, channels Telegram suggests.
 * With a query, tabs choose between chats of each kind, messages in this
 * account's chats, and posts in public channels anywhere on Telegram.
 *
 * A chat opened from here grows out of the row or the face that was
 * tapped, as one opened from the chat list does; see [opensChat].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchPage(
    search: SearchState,
    onQueryChange: (String) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    actions: SearchActions,
    onOpenChat: (Long) -> Unit,
    contentPadding: PaddingValues
) {
    // The field waits with the caret in it and the keyboard up, unless
    // Settings → For geeks says to open search without it.
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val withoutKeyboard = LocalGeekSettings.current.searchWithoutKeyboard
    LaunchedEffect(Unit) {
        if (withoutKeyboard) return@LaunchedEffect
        // A frame first: the field is still being laid out when this starts,
        // and focus asked of a node not yet attached is dropped.
        withFrameNanos { }
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }
    // Back clears a query first, then leaves for the chat list.
    BackHandler {
        if (search.query.isNotEmpty()) onQueryChange("") else onExpandedChange(false)
    }
    // Which row or face opened the chat on screen, so that one — and only
    // that one, a chat can be in two sections at once — carries the
    // container the conversation grows out of and shrinks back into.
    // Saveable: Home is composed again under the closing chat.
    var opened by rememberSaveable { mutableStateOf<String?>(null) }
    val open: (String, Long) -> Unit = { section, id ->
        opened = "$section:$id"
        onOpenChat(id)
    }
    val grows: @Composable (String, Long) -> Modifier = { section, id ->
        if (opened == "$section:$id") {
            Modifier.containerTransform(chatContainerKey(id), ChatContainerShape, bounds = ChatContainerSpring)
        } else {
            Modifier
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(settingsBackground())
            .padding(top = contentPadding.calculateTopPadding())
    ) {
        SearchBarDefaults.InputField(
            modifier = Modifier
                .fillMaxWidth()
                // The top of the page, straight under the status bar: no
                // title above it, as in Gmail.
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
                .clip(SearchBarDefaults.inputFieldShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .focusRequester(focus),
            query = search.query,
            onQueryChange = onQueryChange,
            onSearch = {
                actions.onSubmit()
                keyboard?.hide()
            },
            expanded = false,
            onExpandedChange = {},
            placeholder = { Text("Search Telegram") },
            leadingIcon = { Icon(Symbols.Search, contentDescription = null) },
            trailingIcon = {
                if (search.isSearching) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else if (search.query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Symbols.Close, contentDescription = "Clear")
                    }
                }
            }
        )
        val bottom = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 16.dp)
        if (search.query.isBlank()) {
            SearchFrontPage(search = search, actions = actions, open = open, grows = grows, padding = bottom)
        } else {
            SecondaryScrollableTabRow(
                selectedTabIndex = search.scope.ordinal,
                edgePadding = 16.dp,
                containerColor = settingsBackground(),
                // As wide as their names, as the folders are: eight tabs at
                // 90dp each was two screens of strip.
                minTabWidth = 0.dp
            ) {
                SearchScope.entries.forEach { scope ->
                    Tab(
                        selected = scope == search.scope,
                        onClick = { actions.onScopeChange(scope) },
                        text = { Text(scope.label) }
                    )
                }
            }
            SearchResults(search = search, actions = actions, open = open, grows = grows, padding = bottom)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchFrontPage(
    search: SearchState,
    actions: SearchActions,
    open: (String, Long) -> Unit,
    grows: @Composable (String, Long) -> Modifier,
    padding: PaddingValues
) {
    LazyColumn(
        contentPadding = PaddingValues(top = 4.dp, bottom = padding.calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (search.topPeople.isNotEmpty()) {
            item(key = "people-header") { SearchSectionHeader("People") }
            item(key = "people") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(search.topPeople, key = { it.id }) { person ->
                        PersonTile(
                            person = person,
                            onClick = { open("people", person.id) },
                            modifier = grows("people", person.id)
                        )
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
                            icon = { Icon(Symbols.History, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                }
            }
        }
        if (search.recentChats.isNotEmpty()) {
            item(key = "recent-header") {
                SearchSectionHeader("Recent", action = "Clear", onAction = actions.onRecentChatsCleared)
            }
            itemsIndexed(search.recentChats, key = { _, chat -> "recent-${chat.id}" }) { index, chat ->
                SearchChatRow(
                    chat = chat,
                    index = index,
                    count = search.recentChats.size,
                    onClick = { open("recent", chat.id) },
                    modifier = grows("recent", chat.id),
                    trailing = {
                        IconButton(onClick = { actions.onRecentChatRemoved(chat.id) }) {
                            Icon(Symbols.Close, contentDescription = "Remove ${chat.title} from recent")
                        }
                    }
                )
            }
        }
        if (search.recommended.isNotEmpty()) {
            item(key = "recommended-header") { SearchSectionHeader("Channels for you") }
            itemsIndexed(search.recommended, key = { _, chat -> "recommended-${chat.id}" }) { index, chat ->
                SearchChatRow(
                    chat = chat,
                    index = index,
                    count = search.recommended.size,
                    onClick = { open("recommended", chat.id) },
                    modifier = grows("recommended", chat.id)
                )
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
    open: (String, Long) -> Unit,
    grows: @Composable (String, Long) -> Modifier,
    padding: PaddingValues
) {
    val scope = search.scope
    val chats = search.visibleChats
    LazyColumn(
        contentPadding = PaddingValues(top = 8.dp, bottom = padding.calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (scope.showsChats) {
            if (chats.mine.isNotEmpty()) {
                item(key = "mine-header") { SearchSectionHeader(if (scope == SearchScope.All) "Chats" else "Yours") }
            }
            // The account's own chats as the chat list draws them — the
            // same row, in one group, with the group's corners.
            itemsIndexed(chats.mine, key = { _, chat -> "mine-${chat.id}" }) { index, chat ->
                ChatListRow(
                    chat = chat,
                    index = index,
                    count = chats.mine.size,
                    onClick = { open("mine", chat.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .then(grows("mine", chat.id))
                )
            }
            if (chats.global.isNotEmpty()) {
                item(key = "global-header") { SearchSectionHeader("Global search") }
            }
            itemsIndexed(chats.global, key = { _, chat -> "global-${chat.id}" }) { index, chat ->
                SearchChatRow(
                    chat = chat,
                    index = index,
                    count = chats.global.size,
                    onClick = { open("global", chat.id) },
                    modifier = grows("global", chat.id)
                )
            }
        }
        if (scope.showsMessages && search.messages.isNotEmpty()) {
            item(key = "messages-header") { SearchSectionHeader("Messages") }
            itemsIndexed(
                search.messages,
                // A message id is only unique within its chat, so the chat
                // has to be part of the key or two hits can collide.
                key = { _, hit -> "msg-${hit.chat.id}-${hit.message.id}" }
            ) { index, hit ->
                MessageHitRow(
                    hit = hit,
                    index = index,
                    count = search.messages.size,
                    onClick = { open("msg-${hit.message.id}", hit.chat.id) },
                    modifier = grows("msg-${hit.message.id}", hit.chat.id)
                )
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
                    itemsIndexed(posts.hits, key = { _, hit -> "post-${hit.chat.id}-${hit.message.id}" }) { index, hit ->
                        MessageHitRow(
                            hit = hit,
                            index = index,
                            count = posts.hits.size,
                            onClick = { open("post-${hit.message.id}", hit.chat.id) },
                            modifier = grows("post-${hit.message.id}", hit.chat.id)
                        )
                    }
                    if (posts.hits.isEmpty() && !posts.limitReached) {
                        item(key = "posts-none") { NothingFound("No public posts about “${search.query}”") }
                    }
                }
            }
        }
        if (scope.showsMusic) {
            val music = search.music
            when {
                search.isSearchingMusic || music == null -> item(key = "music-loading") {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }
                }
                music.isEmpty() -> item(key = "music-none") { NothingFound("No music called “${search.query}”") }
                else -> itemsIndexed(music, key = { _, m -> "music-${m.chatId}-${m.id}" }) { index, message ->
                    TrackHitRow(
                        message = message,
                        from = search.musicFrom[message.chatId],
                        index = index,
                        count = music.size,
                        onPlay = { actions.onPlayTrack(message) }
                    )
                }
            }
        }
        // Said only once the search has actually looked, so a slow query
        // does not report failure before it has an answer.
        val nothing = when {
            scope.showsPosts || scope.showsMusic -> false
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
        // In line with the rows' content, as Settings heads its groups.
        modifier = Modifier.fillMaxWidth().padding(start = 32.dp, end = 20.dp, top = 8.dp)
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
private fun PersonTile(person: ChatPreview, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
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
 * A chat found or suggested: a [SegmentedListItem] in its section's group,
 * with the group's corners — the list the chat list and Settings are made
 * of. It was a plain ListItem clipped into a card of its own, clicked
 * through a modifier, so each result floated apart from the next and had
 * none of the list's own press state. The kind of chat sits under its name,
 * since for one not yet joined that is the first thing worth knowing.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchChatRow(
    chat: ChatPreview,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .then(modifier),
        leadingContent = {
            AvatarBubble(
                title = chat.title,
                seed = chat.avatarColor,
                size = 40.dp,
                shape = personShape(chat.avatarColor),
                photoPath = chat.photoPath,
                savedMessages = chat.isSavedMessages
            )
        },
        supportingContent = {
            val about = chat.lastMessage.takeIf { it.isNotBlank() }
            Text(
                if (about != null) "${chat.kindLabel()} · $about" else chat.kindLabel(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = trailing,
        content = { Text(chat.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    )
}

/**
 * One message found by search, under the conversation it came from.
 *
 * A segmented row like the chat rows, but the roles are swapped: the chat title
 * is the headline and the message text the supporting line, because what
 * identifies a hit is where it was said.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MessageHitRow(
    hit: MessageHit,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .then(modifier),
        leadingContent = {
            AvatarBubble(
                title = hit.chat.title,
                seed = hit.chat.avatarColor,
                size = 40.dp,
                shape = personShape(hit.chat.avatarColor),
                photoPath = hit.chat.photoPath,
                savedMessages = hit.chat.isSavedMessages
            )
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
        },
        content = { Text(hit.chat.title, fontWeight = FontWeight.Bold, maxLines = 1) }
    )
}

/**
 * A track found on the Music tab: its title, its performer and length, and
 * the chat it was sent in. Tapped, it plays, with the rest of what was found
 * as the queue.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TrackHitRow(
    message: ChatMessage,
    from: String?,
    index: Int,
    count: Int,
    onPlay: () -> Unit
) {
    val audio = message.audio ?: return
    SegmentedListItem(
        onClick = onPlay,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        leadingContent = {
            FilledTonalIconButton(onClick = onPlay, modifier = Modifier.size(40.dp)) {
                Icon(Symbols.PlayArrowFilled, contentDescription = "Play ${audio.displayTitle}")
            }
        },
        supportingContent = {
            Text(
                listOf(
                    audio.performer,
                    audio.durationSeconds.takeIf { it > 0 }?.let { formatDuration(it.toLong()) },
                    from?.let { "from $it" }
                ).filter { !it.isNullOrBlank() }.joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        content = { Text(audio.displayTitle, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    )
}
