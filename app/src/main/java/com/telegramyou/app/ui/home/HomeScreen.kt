package com.telegramyou.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Constraints
import com.telegramyou.app.telegram.model.ChatPreview
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
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
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.graphics.Color
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.telegramyou.app.telegram.model.StoryItem
import com.telegramyou.app.telegram.model.MessageHit
import com.telegramyou.app.ui.components.AvatarBubble
import com.telegramyou.app.ui.components.ChatListRow
import com.telegramyou.app.ui.components.StoriesRail
import com.telegramyou.app.ui.theme.AppTitleFontFamily
import com.telegramyou.app.ui.theme.AppTitleSize

/**
 * The chat list.
 *
 * Takes a [HomeUiState] and callbacks — no repository, no coroutine scope,
 * nothing remembered that a rotation would lose. Everything it needs to draw
 * itself arrives in [state]; everything it wants to happen leaves through a
 * callback.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
    onShapedAvatarsChange: (Boolean) -> Unit,
    onProfileDraftChange: (ProfileDraft) -> Unit,
    onProfileSave: () -> Unit,
    onProfileErrorShown: () -> Unit,
    onComposeOpen: () -> Unit,
    onNewGroup: () -> Unit,
    onNewChannel: () -> Unit,
    onJoinLink: () -> Unit,
    onComposeDismiss: () -> Unit,
    onContactPicked: (Long) -> Unit,
    onComposeNavigated: () -> Unit,
    onLogout: () -> Unit,
    /** Called once a refusal in [HomeUiState.errorMessage] has been shown. */
    onErrorShown: () -> Unit = {}
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

    state.errorMessage?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            onErrorShown()
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
            // On the chat list the bar is the top of a header that scrolls
            // away with the chats, so it is drawn with them, below. The other
            // tabs have nothing to scroll it away and keep it fixed here.
            if (tab == HomeTab.Chats || tab == HomeTab.Search) return@Scaffold
            HomeTitleBar(modifier = Modifier.statusBarsPadding())
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
                onPick = onContactPicked,
                onNewGroup = {
                    onComposeDismiss()
                    onNewGroup()
                },
                onNewChannel = {
                    onComposeDismiss()
                    onNewChannel()
                },
                onJoinLink = {
                    onComposeDismiss()
                    onJoinLink()
                }
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
                    onShapedAvatarsChange = onShapedAvatarsChange,
                    onLogout = onLogout,
                    contentPadding = padding
                )
                return@Scaffold
            }
            // Chats and Search share the list below: searching narrows what
            // is on screen rather than replacing it with somewhere else.
            HomeTab.Chats, HomeTab.Search -> Unit
        }

        // Expanded or hidden, the header's position is Material's own state:
        // the one a TopAppBar keeps, driven by the enterAlways behaviour. It
        // goes as the chats scroll down and comes back the moment they scroll
        // up, from anywhere in the list rather than only at its top, and when
        // the finger lifts halfway it settles to one end or the other on the
        // motion scheme's spatial spring — the bounce Expressive gives
        // anything that moves — so it never stops half-hidden.
        //
        // The whole header goes, folders included. Those were pinned while
        // the header was only the bar, on the argument that a filter should
        // stay in reach; they still are — one flick up, or a swipe sideways
        // on the list itself, which is what the pager below is for.
        val header = TopAppBarDefaults.enterAlwaysScrollBehavior(
            snapAnimationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
        )
        val haptics = LocalHapticFeedback.current
        // One light tick as the header finishes going, and none as it comes
        // back. The tick marks the screen being handed to the list — a detent,
        // which is what the segment tick is for — and a second one on the way
        // back would make every scroll up and down buzz twice.
        LaunchedEffect(header.state) {
            snapshotFlow { header.state.collapsedFraction >= 1f }
                .distinctUntilChanged()
                .drop(1)
                .collect { hidden ->
                    if (hidden) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                }
        }

        // The folders are pages. The view model still owns which one is
        // chosen — it survives a rotation there, and it filters — so the
        // pager starts on that page and reports each page it settles on.
        val tabs = state.folderTabs
        val selectedIndex = tabs.indexOfFirst { it.id == state.selectedFolderId }
            .coerceAtLeast(0)
        val pager = rememberPagerState(initialPage = selectedIndex) { tabs.size }
        val scope = rememberCoroutineScope()
        LaunchedEffect(pager, tabs) {
            snapshotFlow { pager.settledPage }.collect { page ->
                tabs.getOrNull(page)?.let { onFolderSelected(it.id) }
            }
        }
        // And the other way, for a selection that moved without the pager:
        // a folder deleted on another device, which the view model answers
        // by falling back to All.
        LaunchedEffect(selectedIndex) {
            if (!pager.isScrollInProgress && pager.settledPage != selectedIndex) {
                pager.scrollToPage(selectedIndex)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                // The darker of the two tones on this screen: what sits
                // behind the header, and what shows if the list is
                // overscrolled past its top.
                //
                // Before the padding, not after: a modifier chain paints
                // where it stands, and insetting first would leave the
                // system bars sitting over bare window colour.
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(padding)
        ) {
            // On the column, so the scroll of whichever page is showing
            // reaches the header on its way up. Only the vertical half is
            // taken: a sideways drag passes through to the pager untouched.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(header.nestedScrollConnection)
            ) {
                CollapsingHeader(header.state) {
                    HomeTitleBar(windowInsets = WindowInsets(0))
                    FolderTabs(
                        tabs = tabs,
                        unread = state.folderUnread,
                        // The page being swiped towards, not the one last
                        // settled on: the indicator moves with the finger.
                        selectedIndex = pager.targetPage,
                        onSelected = { index ->
                            scope.launch { pager.animateScrollToPage(index) }
                        }
                    )
                    // In the header rather than as the list's first item, and
                    // that is what makes the panel below look like a panel:
                    // as a row inside the list it painted itself back to the
                    // header's tone across the full width, which squared off
                    // the rounded corners it was sitting on.
                    StoriesRail(
                        stories = state.stories,
                        onStoryClick = onOpenStory
                    )
                    Spacer(Modifier.height(12.dp))
                }
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize()
                ) {
                    val page: @Composable (List<ChatPreview>, Boolean, Boolean) -> Unit =
                        { chats, isAll, inPager ->
                            ChatListPage(
                                chats = chats,
                                archiveSummary = if (isAll) state.archiveSummary else null,
                                shapedAvatars = settings.shapedAvatars,
                                // A sideways drag is the pager's where there
                                // is one; see ChatListRow.
                                swipeActions = !inPager,
                                onOpenArchive = onOpenArchive,
                                onOpenChat = onOpenChat,
                                onMutedChange = onMutedChange,
                                onPinnedChange = onPinnedChange,
                                onMarkRead = onMarkRead,
                                onArchivedChange = onArchivedChange
                            )
                        }
                    if (tabs.isEmpty()) {
                        // No folders, nothing to page between: one list, and
                        // the rows keep their own swipes.
                        page(state.chats, /* isAll = */ true, /* inPager = */ false)
                    } else {
                        HorizontalPager(
                            state = pager,
                            modifier = Modifier.fillMaxSize(),
                            // Each page is its own list with its own scroll
                            // position, so they are told apart by folder
                            // rather than by where they happen to sit.
                            key = { index -> tabs.getOrNull(index)?.id ?: -1 },
                            verticalAlignment = Alignment.Top
                        ) { index ->
                            page(
                                state.folderChats.getOrElse(index) { emptyList() },
                                /* isAll = */ tabs.getOrNull(index)?.id == null,
                                /* inPager = */ true
                            )
                        }
                    }
                }
            }
        }
    }
    }
}

/**
 * The header over the chat list — bar, folders, stories — shrinking as
 * [state] says.
 *
 * Measured at its full height and then laid out shorter, with its content
 * slid up by the same amount and clipped, so what goes first is the top of
 * it and the list below grows into the room as it is made. Reading the offset
 * in the layout pass rather than in composition keeps a scroll to a relayout:
 * nothing here is recomposed for each pixel the finger moves.
 *
 * It also tells [state] how far it can go, which a TopAppBar would normally
 * do for itself — here the height is whatever the folders and the stories
 * add up to, so only the measurement knows it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollapsingHeader(
    state: TopAppBarState,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clipToBounds()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(
                    constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
                )
                val full = placeable.height
                if (state.heightOffsetLimit != -full.toFloat()) {
                    state.heightOffsetLimit = -full.toFloat()
                }
                val offset = state.heightOffset.roundToInt().coerceIn(-full, 0)
                layout(placeable.width, full + offset) {
                    placeable.place(0, offset)
                }
            }
            // Fading as it goes, the way a large app bar's title does, so the
            // last few points under the status bar leave as a dissolve rather
            // than as a hard edge sliding out of sight.
            .graphicsLayer { alpha = 1f - state.collapsedFraction },
        content = content
    )
}

/**
 * One folder's chats, or all of them, on the lighter panel.
 */
@Composable
private fun ChatListPage(
    chats: List<ChatPreview>,
    archiveSummary: String?,
    shapedAvatars: Boolean,
    swipeActions: Boolean,
    onOpenArchive: () -> Unit,
    onOpenChat: (Long) -> Unit,
    onMutedChange: (Long, Boolean) -> Unit,
    onPinnedChange: (Long, Boolean) -> Unit,
    onMarkRead: (Long) -> Unit,
    onArchivedChange: (Long, Boolean) -> Unit
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
        // On the list rather than around it: wrapping the chats in a
        // panel of their own would mean one `item` holding every row,
        // and a chat list is exactly the thing that must stay lazy.
        modifier = Modifier
            .fillMaxSize()
            // Rounded where it meets the header, so the chats
            // read as sitting in a panel rather than as the
            // screen carrying on in another colour. Clipped
            // before the background, or the corners would be
            // painted over by it.
            .clip(
                RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            )
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        // Twenty above, so the first row sits inside the panel
        // rather than wedged into its rounded corner. Below, room for
        // the floating button and only for it: the navigation bar is
        // outside this Scaffold, under the suite, so clearing it too
        // would leave a gap below the last chat.
        contentPadding = PaddingValues(top = 20.dp, bottom = 96.dp),
        // The hairline Material leaves between segmented list
        // items, through which the panel behind them shows.
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
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
        //
        // Above the chats and below the stories, which is where
        // Telegram puts it — and absent entirely when the archive
        // is empty, which the summary being null already says.
        archiveSummary?.let { summary ->
            item(key = "archive-entry") {
                ArchiveEntryRow(
                    summary = summary,
                    onClick = onOpenArchive,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                Spacer(Modifier.height(12.dp))
            }
        }
        groupChats(chats) { it.isPinned }.forEach { group ->
            itemsIndexed(group, key = { _, chat -> chat.id }) { index, chat ->
                ChatListRow(
                    chat = chat,
                    index = index,
                    count = group.size,
                    shapedAvatar = shapedAvatars,
                    swipeActions = swipeActions,
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

/**
 * The app's name, as the bar the home screen hangs from.
 *
 * Its own function because it stands in two places: fixed at the top of
 * Profile and Settings, and at the top of the chat list's header, where it
 * scrolls away with the folders and the stories. [windowInsets] is empty
 * there — the screen has already stepped below the status bar, and the bar
 * padding itself for it a second time would open a gap the height of one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTitleBar(
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets
) {
    TopAppBar(
        title = {
            // One line, and it is the app's name. Expressive's
            // argument for type is contrast — a display size set
            // tight, against body text that stays quiet — so the
            // name is `displayMedium` with its tracking pulled in
            // rather than a headline shouted in Black.
            //
            // What used to sit under it — "You Expressive" — said
            // nothing the screen does not already show, and the
            // avatar that sat beside it repeated the Profile tab two
            // inches below.
            Text(
                "TelegramYou",
                style = MaterialTheme.typography.displayMedium.copy(
                    // Google Sans Flex, the face Google's own apps set
                    // their names in — see AppTitleFontFamily. Only the
                    // name: the rest of the screen stays on the system
                    // face, which is what makes this read as a title.
                    fontFamily = AppTitleFontFamily,
                    // A step down from 30, asked for once it was in Google
                    // Sans: this face runs wider than the system one, and
                    // at 30 the name read louder than the chats under it.
                    fontSize = AppTitleSize,
                    lineHeight = 32.sp,
                    // Looser than the -1.2 the system face needed. This
                    // one's display cut is already drawn tight, and
                    // pulling it in as far again ran the letters together.
                    letterSpacing = (-0.5).sp,
                    // Medium, not the scale's ExtraBold. At display
                    // size the weight does not have to carry the
                    // emphasis — the size already does, and the
                    // heavier cut read as shouting.
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
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
        windowInsets = windowInsets,
        modifier = modifier
    )
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
    selectedIndex: Int,
    onSelected: (Int) -> Unit
) {
    if (tabs.isEmpty()) return

    PrimaryScrollableTabRow(
        selectedTabIndex = selectedIndex.coerceIn(0, tabs.lastIndex),
        // Flush with everything else on the screen. The default edge padding
        // for a scrollable tab row is 52dp, which is Material's allowance for
        // a row that starts under a navigation icon — this one starts under
        // the app's name, and the gap read as the strip having slipped
        // sideways.
        edgePadding = 16.dp,
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
                onClick = { onSelected(index) },
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
    onPick: (Long) -> Unit,
    onNewGroup: () -> Unit,
    onNewChannel: () -> Unit,
    onJoinLink: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        // Rows take the sheet's tone rather than painting their own: a
        // ListItem defaults to `surface`, a sheet is `surfaceContainerLow`,
        // and the difference reads as pale slabs with seams between them.
        val sheetRow = ListItemDefaults.colors(containerColor = Color.Transparent)
        Text(
            "New message",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
        )
        // The three ways to a chat that is not a person, above the people.
        // Where Telegram puts them too, and for its reason: the pencil means
        // "start something", and a group is something to start.
        ListItem(
            headlineContent = { Text("New group") },
            leadingContent = { Icon(Icons.Rounded.Group, contentDescription = null) },
            colors = sheetRow,
            modifier = Modifier.clickable(onClick = onNewGroup)
        )
        ListItem(
            headlineContent = { Text("New channel") },
            leadingContent = { Icon(Icons.Rounded.Campaign, contentDescription = null) },
            colors = sheetRow,
            modifier = Modifier.clickable(onClick = onNewChannel)
        )
        ListItem(
            headlineContent = { Text("Join with a link") },
            leadingContent = { Icon(Icons.Rounded.Link, contentDescription = null) },
            colors = sheetRow,
            modifier = Modifier.clickable(onClick = onJoinLink)
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
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
                    },
                    colors = sheetRow
                )
            }
            else -> {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(compose.contacts, key = { it.id }) { person ->
                        ListItem(
                            colors = sheetRow,
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
