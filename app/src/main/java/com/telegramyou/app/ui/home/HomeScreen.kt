package com.telegramyou.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Badge
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Scaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScope
import androidx.compose.material3.SegmentedListItem
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
import androidx.compose.ui.Alignment
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
    onPinnedChange: (Long, Boolean) -> Unit,
    onMarkRead: (Long) -> Unit,
    onArchivedChange: (Long, Boolean) -> Unit,
    onOpenArchive: () -> Unit,
    onFolderSelected: (Int?) -> Unit,
    onThemeChange: (ThemeChoice) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onProfileDraftChange: (ProfileDraft) -> Unit,
    onProfileSave: () -> Unit,
    onProfileErrorShown: () -> Unit,
    onComposeOpen: () -> Unit,
    onComposeDismiss: () -> Unit,
    onContactPicked: (Long) -> Unit,
    onComposeNavigated: () -> Unit,
    onLogout: () -> Unit
) {
    // A snackbar rather than a banner inside the form, for both halves of what
    // a save has to say. A refusal comes from the server with its own wording
    // and belongs over the screen rather than wedged between two fields, and a
    // success has nothing to show once the form has gone back to matching the
    // account — without this, saving would look like nothing happening.
    val snackbarHostState = remember { SnackbarHostState() }

    // One-shot: navigate, then tell the view model it happened. Leaving the id
    // in state would reopen the conversation on the next rotation.
    state.compose.openChatId?.let { chatId ->
        LaunchedEffect(chatId) {
            onComposeNavigated()
            onOpenChat(chatId)
        }
    }

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

    // The bar becomes a rail where there is width for one — in landscape on
    // a phone, and on a tablet at any angle.
    //
    // `NavigationSuiteScaffold` rather than a breakpoint written here: it is
    // Material's own answer to this, it picks the shape from the window
    // itself, and the Expressive shapes are the ones it picks — a short bar
    // in compact, which is exactly what this screen already had, and a wide
    // rail when there is room. The items are declared once for both.
    val navigationItems: NavigationSuiteScope.() -> Unit = {
        HomeTab.entries.forEach { entry ->
            item(
                selected = tab == entry,
                onClick = { onTabSelected(entry) },
                icon = { Icon(entry.icon, contentDescription = entry.label) },
                label = { Text(entry.label) }
            )
        }
    }

    NavigationSuiteScaffold(navigationItems) {
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
        floatingActionButton = {
            // Only where composing means anything. On Profile or Settings a
            // pencil is a button with nowhere to go.
            if (tab != HomeTab.Chats) return@Scaffold
            FloatingActionButton(
                // A contact picker, not the first chat in the list. That is
                // what this used to open, which made the pencil a button that
                // looked like composing and was not.
                onClick = onComposeOpen,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.large
            ) {
                Icon(Icons.Rounded.Edit, contentDescription = "Compose")
            }
        }
    ) { padding ->
        if (state.compose.sheetOpen) {
            ContactPickerSheet(
                compose = state.compose,
                onDismiss = onComposeDismiss,
                onPick = onContactPicked
            )
        }

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
            // A column, so the tabs sit above the list rather than over
            // it. Pinned here rather than scrolling as the list's first item:
            // a filter you have to scroll back to the top to change is one
            // you cannot reach while looking at what it filtered — which is
            // also why Telegram pins its own folder tabs.
            Column(modifier = Modifier.fillMaxSize()) {
                FolderTabs(
                    tabs = state.folderTabs,
                    unread = state.folderUnread,
                    selectedId = state.selectedFolderId,
                    onSelected = onFolderSelected
                )
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
                        // Room for the floating button, and only for it: the
                        // navigation bar is outside this Scaffold now, under
                        // the suite rather than inside the content, so the
                        // sixteen extra points that cleared it would be a gap
                        // below the last chat.
                        contentPadding = PaddingValues(bottom = 96.dp),
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
                        // Above the chats and below the stories, which is where
                        // Telegram puts it — and absent entirely when the archive
                        // is empty, which the summary being null already says.
                        state.archiveSummary?.let { summary ->
                            item(key = "archive-entry") {
                                ArchiveEntryRow(
                                    summary = summary,
                                    onClick = onOpenArchive,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )
                                Spacer(Modifier.height(12.dp))
                            }
                        }
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
                                    onMutedChange = { muted -> onMutedChange(chat.id, muted) },
                                    onPinnedChange = { pinned ->
                                        onPinnedChange(chat.id, pinned)
                                    },
                                    onMarkRead = { onMarkRead(chat.id) },
                                    onArchivedChange = { archived ->
                                        onArchivedChange(chat.id, archived)
                                    }
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
    }
}

/**
 * The account's folders, as a tab strip over the one chat list.
 *
 * `PrimaryScrollableTabRow` because that is what Material ships for exactly
 * this — a row of tabs that may be wider than the screen — and because
 * primary tabs are the ones that belong directly under an app bar. Nothing
 * is drawn by hand here, indicator included.
 *
 * Absent entirely when the account has no folders: [tabs] is empty then, and
 * a strip with a single "All" tab in it would be chrome that filters nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderTabs(
    tabs: List<FolderTab>,
    unread: List<Int>,
    selectedId: Int?,
    onSelected: (Int?) -> Unit
) {
    if (tabs.isEmpty()) return
    // Falls back to the first tab, which is All. A selected folder that is no
    // longer in the list is already turned into null upstream, so this only
    // catches the moment between the two.
    val selectedIndex = tabs.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)

    PrimaryScrollableTabRow(
        selectedTabIndex = selectedIndex,
        // The header's tone, because that is what this is part of: the
        // lighter panel starts below, where the chats do.
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        // No divider. The step down in tone at the top of the list already
        // separates the two, and a line as well would be saying it twice.
        divider = {}
    ) {
        tabs.forEachIndexed { index, folder ->
            val count = unread.getOrElse(index) { 0 }
            Tab(
                selected = index == selectedIndex,
                onClick = { onSelected(folder.id) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(folder.title)
                        if (count > 0) {
                            Spacer(Modifier.width(6.dp))
                            // Material's own badge rather than a number in
                            // brackets: this is the same thing the navigation
                            // bar puts on an icon, and it should look like it.
                            Badge { Text(count.toString()) }
                        }
                    }
                }
            )
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
 * Who to start a conversation with.
 *
 * A `ModalBottomSheet` rather than a screen: picking one name is a step on the
 * way somewhere, and a screen would put a back stack entry between the chat
 * list and the conversation that opens.
 *
 * Contacts, not everyone ever spoken to — the chat list already is the second,
 * and this button exists as the alternative to scrolling it.
 */
// Both opt-ins: ModalBottomSheet is ExperimentalMaterial3Api and
// LoadingIndicator is Expressive, which are separate annotations and separate
// mistakes to make.
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ContactPickerSheet(
    compose: ComposeState,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "New message",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
        )
        when {
            compose.isLoading && compose.contacts.isEmpty() -> {
                // The stock Expressive indicator rather than a spinner drawn
                // here, for the reason ROADMAP.md gives about the last one.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
            }
            compose.contacts.isEmpty() -> {
                ListItem(
                    headlineContent = { Text("No contacts") },
                    supportingContent = {
                        Text("Nobody in this account's contact list to write to yet")
                    }
                )
            }
            else -> {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(compose.contacts, key = { it.id }) { person ->
                        ListItem(
                            headlineContent = { Text(person.displayName) },
                            supportingContent = person.username?.let { name ->
                                { Text("@$name") }
                            },
                            leadingContent = {
                                AvatarBubble(
                                    title = person.displayName,
                                    seed = person.avatarColor
                                )
                            },
                            modifier = Modifier.clickable { onPick(person.id) }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/**
 * The way into the archive.
 *
 * A `ListItem` on the same container as a chat row, because it is one more
 * thing in the same list and dressing it differently would make it look like
 * a setting. Its own shape rather than a segmented one: it is a run of a
 * single row, and it belongs to no group.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ArchiveEntryRow(
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
        modifier = modifier,
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        leadingContent = {
            Icon(
                Icons.Rounded.Archive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        supportingContent = { Text(summary) },
        content = { Text("Archived", fontWeight = FontWeight.Bold) }
    )
}
