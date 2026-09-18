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
import com.telegramyou.app.ui.settings.SettingsContent
import com.telegramyou.app.ui.profile.ProfileContent
import com.telegramyou.app.ui.profile.ProfileDraft
import com.telegramyou.app.settings.ThemeChoice
import com.telegramyou.app.settings.AppearanceSettings
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    tab: HomeTab,
    onTabSelected: (HomeTab) -> Unit,
    settings: AppearanceSettings,
    onRefresh: () -> Unit,
    onOpenChat: (Long) -> Unit,
    onOpenStory: (StoryItem) -> Unit,
    onSearchExpandedChange: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onMutedChange: (Long, Boolean) -> Unit,
    onThemeChange: (ThemeChoice) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onProfileDraftChange: (ProfileDraft) -> Unit,
    onProfileSave: () -> Unit,
    onProfileErrorShown: () -> Unit,
    onLogout: () -> Unit
) {
    // A snackbar rather than a banner inside the form, for both halves of what
    // a save has to say. A refusal comes from the server with its own wording
    // and belongs over the screen rather than wedged between two fields, and a
    // success has nothing to show once the form has gone back to matching the
    // account — without this, saving would look like nothing happening.
    val snackbarHostState = remember { SnackbarHostState() }

    state.profile.errorMessage?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            onProfileErrorShown()
        }
    }
    // Keyed on the count, not on a boolean: two saves in a row are two
    // acknowledgements, and a flag would only fire for the first.
    LaunchedEffect(state.profile.savedCount) {
        if (state.profile.savedCount > 0) {
            snackbarHostState.showSnackbar("Profile saved")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    // No magnifier and no avatar here any more: both are tabs
                    // along the bottom now, and a second control for a
                    // destination the bar already carries is one the eye has
                    // to rule out every time.
                    AvatarBubble(
                        title = state.me?.displayName ?: "You",
                        seed = state.me?.avatarColor ?: 1,
                        size = 36.dp,
                        onClick = { onTabSelected(HomeTab.Profile) },
                        modifier = Modifier.padding(end = 12.dp)
                    )
                },
                // Darkest of the three levels on this screen. The bar is the
                // container the screen hangs from, the background sits below
                // it, and the chats are the lightest because they are the
                // content — which is the order Material's fourth principle
                // asks for: the important thing gets the brightest surface.
                //
                // Opaque, unlike the conversation's bar. There the gradient
                // is meant to run behind it; here the bar is a level of its
                // own and has to be seen to be one.
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                modifier = Modifier.statusBarsPadding()
            )
        },
        bottomBar = {
            HomeNavigationBar(selected = tab, onSelected = onTabSelected)
        },
        floatingActionButton = {
            // Only where composing means anything. On Profile or Settings a
            // pencil is a button with nowhere to go.
            if (tab != HomeTab.Chats) return@Scaffold
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
        // Profile and Settings are their own content, not another list with
        // a gradient behind it, so they take the padding and stop there.
        when (tab) {
            HomeTab.Profile -> {
                ProfileContent(
                    me = state.me,
                    profile = state.profile,
                    onDraftChange = onProfileDraftChange,
                    onSave = onProfileSave,
                    contentPadding = padding
                )
                return@Scaffold
            }
            HomeTab.Settings -> {
                SettingsContent(
                    settings = settings,
                    me = state.me,
                    onThemeChange = onThemeChange,
                    onDynamicColorChange = onDynamicColorChange,
                    onLogout = onLogout,
                    contentPadding = padding
                )
                return@Scaffold
            }
            // Chats and Search share the list below: searching narrows what
            // is on screen rather than replacing it with somewhere else.
            HomeTab.Chats, HomeTab.Search -> Unit
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                // The darker of the two tones on this screen: what sits
                // behind the bar and the stories rail, and what shows if the
                // list is overscrolled past its top.
                //
                // Before the padding, not after: a modifier chain paints
                // where it stands, and insetting first would leave the
                // system bars sitting over bare window colour.
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(padding)
        ) {
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    // The lighter panel the chats sit on, and the second half
                    // of Material's "contain content for emphasis": the rows
                    // are the lightest tone, the panel under them is a step
                    // darker, and the bar and the stories rail above are
                    // darker still. Three steps, so the chats read as their
                    // own zone rather than as pills floating on the same grey
                    // as everything else — which is what they did when this
                    // background and the one behind the stories were the same
                    // colour.
                    //
                    // On the list rather than around it because the stories
                    // rail is the list's own first item and has to stay out of
                    // this panel; it paints itself back to the darker tone
                    // below. Wrapping the chats in a panel of their own would
                    // mean one `item` holding every row, and a chat list is
                    // exactly the thing that must stay lazy.
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow),
                    // Room for the floating button, which shares the bottom
                    // of the screen with a navigation bar. Ninety-six was
                    // enough when the button was alone down there; with the
                    // bar under it the last chat ended up behind the pencil.
                    contentPadding = PaddingValues(bottom = 112.dp),
                    // The hairline Material leaves between segmented list
                    // items, through which the panel behind them shows.
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    item {
                        // Painted back to the bar's tone, because the list it
                        // lives in carries the chats' lighter panel. Stories
                        // belong with the header, not with the chats.
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            StoriesRail(
                                stories = state.stories,
                                onStoryClick = onOpenStory
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                    // Grouped into containers rather than laid out as one
                    // card per chat. Material's fourth expressive principle
                    // is to contain content: a run of rows sharing a
                    // container reads as one informative grouping, where the
                    // same rows floating separately read as a pile of
                    // unrelated things. Pinned chats get a container of their
                    // own, because chosen and recent are different kinds of
                    // thing and the separation then needs no heading.
                    //
                    // No AnimatedVisibility here. It wrapped every row with
                    // visible = true, which never transitions, so the enter
                    // animation could not run — a composition layer that cost
                    // something and did nothing.
                    groupChats(state.chats) { it.isPinned }.forEach { group ->
                        itemsIndexed(group, key = { _, chat -> chat.id }) { index, chat ->
                            ChatListRow(
                                chat = chat,
                                index = index,
                                count = group.size,
                                onClick = { onOpenChat(chat.id) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp),
                                onMutedChange = { muted -> onMutedChange(chat.id, muted) }
                            )
                        }
                        // Between containers, not between rows: the gap is
                        // what makes two groups read as two.
                        item(key = "gap-${group.first().id}") {
                            Spacer(Modifier.height(12.dp))
                        }
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

/**
 * Home's bottom bar.
 *
 * `ShortNavigationBar`, which is the Expressive one — and reachable, unlike
 * `MaterialShapes`, which was checked before a line of this was written.
 * Against the plain `NavigationBar` it is shorter, which matters on a screen
 * whose whole job is a list, and its item animates the indicator rather than
 * cross-fading it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HomeNavigationBar(selected: HomeTab, onSelected: (HomeTab) -> Unit) {
    ShortNavigationBar {
        HomeTab.entries.forEach { tab ->
            ShortNavigationBarItem(
                selected = selected == tab,
                onClick = { onSelected(tab) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) }
            )
        }
    }
}
