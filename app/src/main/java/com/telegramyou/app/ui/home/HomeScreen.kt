package com.telegramyou.app.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import com.telegramyou.app.update.LocalAppUpdates
import com.telegramyou.app.update.UpdateState
import androidx.compose.material3.BadgedBox
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.BackHandler
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.telegramyou.app.settings.isDark
import com.telegramyou.app.ui.components.personShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.telegramyou.app.ui.theme.AppTitleWeight
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
    onErrorShown: () -> Unit = {},
    /** A list is near its end: the main one for null, else that folder. */
    onListEndReached: (Int?) -> Unit = {},
    onOpenProxy: () -> Unit = {},
    onOpenSavedMessages: () -> Unit = {}
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
    // A dot on Settings when a newer build is out — found by the quiet
    // check at launch, and offered in the row under About.
    val updates = LocalAppUpdates.current
    val updateState = updates?.state?.collectAsStateWithLifecycle()?.value
    val updateWaiting = updateState is UpdateState.Available || updateState is UpdateState.Ready
    val navigationItems: NavigationSuiteScope.() -> Unit = {
        HomeTab.entries.forEach { entry ->
            item(
                selected = tab == entry,
                onClick = { onTabSelected(entry) },
                icon = {
                    if (entry == HomeTab.Settings && updateWaiting) {
                        BadgedBox(badge = { Badge() }) {
                            Icon(entry.icon, contentDescription = "${entry.label}, update available")
                        }
                    } else {
                        Icon(entry.icon, contentDescription = entry.label)
                    }
                },
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
            ComposeFabMenu(
                onNewMessage = onComposeOpen,
                onNewGroup = onNewGroup,
                onNewChannel = onNewChannel,
                onJoinLink = onJoinLink
            )
        }
    ) { padding ->
        if (state.compose.sheetOpen) {
            ContactPickerSheet(
                compose = state.compose,
                onDismiss = onComposeDismiss,
                onPick = onContactPicked
            )
        }

        // Fade through between the bottom tabs, which is what Material's
        // motion guidance gives navigation-bar destinations: they are
        // separate places rather than neighbours, so nothing slides — the
        // old one fades out, and the new one fades in growing slightly into
        // place, on the theme's springs. Chats and Search are one place here:
        // searching opens over the same list.
        val motion = MaterialTheme.motionScheme
        AnimatedContent(
            targetState = tab,
            contentKey = { if (it == HomeTab.Search) HomeTab.Chats else it },
            transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) +
                    scaleIn(motion.defaultSpatialSpec(), initialScale = FADE_THROUGH_SCALE)) togetherWith
                    fadeOut(motion.fastEffectsSpec())
            },
            label = "homeTab"
        ) { shownTab ->
            // Profile and Settings are their own content, not another list with
            // a gradient behind it, so they take the padding and stop there.
            when (shownTab) {
                HomeTab.Profile -> {
                    ProfileContent(
                        me = state.me,
                        profile = state.profile,
                        onDraftChange = onProfileDraftChange,
                        onSave = onProfileSave,
                        contentPadding = padding
                    )
                    return@AnimatedContent
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
                    return@AnimatedContent
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
                        HomeTitleBar(
                            windowInsets = WindowInsets(0),
                            isDark = isDark(settings.theme, isSystemInDarkTheme()),
                            onThemeChange = onThemeChange,
                            onOpenProxy = onOpenProxy,
                            onOpenSavedMessages = onOpenSavedMessages
                        )
                        // Stories first, then the folders: the tabs choose what
                        // the list below shows, so they sit against it, and the
                        // stories — which are not a filter of anything — sit
                        // above, with the name.
                        //
                        // In the header rather than as the list's first item, and
                        // that is what makes the panel below look like a panel:
                        // as a row inside the list it painted itself back to the
                        // header's tone across the full width, which squared off
                        // the rounded corners it was sitting on.
                        StoriesRail(
                            stories = state.stories,
                            onStoryClick = onOpenStory
                        )
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
                        Spacer(Modifier.height(8.dp))
                    }
                    PullToRefreshBox(
                        isRefreshing = state.isRefreshing,
                        onRefresh = onRefresh,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val page: @Composable (List<ChatPreview>, Int?, Boolean, Boolean) -> Unit =
                            { chats, folderId, inPager, current ->
                                val isAll = folderId == null
                                ChatListPage(
                                    chats = chats,
                                    onNearEnd = { onListEndReached(folderId) },
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
                            page(state.chats, /* folderId = */ null, /* inPager = */ false, /* current = */ true)
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
                                    /* folderId = */ tabs.getOrNull(index)?.id,
                                    /* inPager = */ true,
                                    /* current = */ index == pager.currentPage
                                )
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
    onNearEnd: () -> Unit,
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
    val listState = rememberLazyListState()
    // Near the end rather than at it, so the next chats are on their way
    // before the last row is reached. derivedStateOf keeps a scroll from
    // recomposing the page for every pixel.
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            last >= info.totalItemsCount - LOAD_MORE_AHEAD
        }
    }
    LaunchedEffect(nearEnd, chats.size) {
        if (nearEnd && chats.isNotEmpty()) onNearEnd()
    }
    LazyColumn(
        state = listState,
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
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    isDark: Boolean = false,
    onThemeChange: (ThemeChoice) -> Unit = {},
    onOpenProxy: () -> Unit = {},
    onOpenSavedMessages: () -> Unit = {}
) {
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        // The overflow: three things reached for from the chat list that are
        // not chats — the light, a way round a block, and the chat with
        // oneself. A menu rather than three icons, which would crowd the
        // name the bar exists to carry.
        actions = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "More")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(if (isDark) "Light theme" else "Dark theme") },
                        leadingIcon = {
                            Icon(
                                if (isDark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            menuOpen = false
                            // A choice, not "follow the system" — it is what
                            // was asked for from here. Settings still offers
                            // System to go back to.
                            onThemeChange(if (isDark) ThemeChoice.Light else ThemeChoice.Dark)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Proxy") },
                        leadingIcon = { Icon(Icons.Rounded.VpnKey, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onOpenProxy()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Saved Messages") },
                        leadingIcon = { Icon(Icons.Rounded.Bookmark, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onOpenSavedMessages()
                        }
                    )
                }
            }
        },
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
                    // Not the scale's ExtraBold, which read as shouting;
                    // the face's own title weight, which with its round
                    // ends and extra width is expressive rather than loud.
                    fontWeight = AppTitleWeight
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
                            //
                            // In primary, not the badge's default error red:
                            // unread is not a fault, and on the dark schemes
                            // error's container reads as brown. Loud on the
                            // folder being looked at, tonal on the others,
                            // as the chat rows' own counts are.
                            val selected = index == selectedIndex
                            Badge(
                                containerColor = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.secondaryContainer
                                },
                                contentColor = if (selected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                }
                            ) { Text(count.toString()) }
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

/**
 * The pencil, opened into what it can start: Material 3 Expressive's FAB
 * menu. A toggle FAB whose pencil turns to a close mark, and the choices
 * rising from it — where they used to be three rows at the top of a sheet
 * that opened first, which was one tap and one sheet too many for anything
 * but a message.
 *
 * Back closes it, as it closes every other thing that opens over the list.
 * The semantics are the sample's: a screen reader hears one toggle with its
 * state, then the items.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ComposeFabMenu(
    onNewMessage: () -> Unit,
    onNewGroup: () -> Unit,
    onNewChannel: () -> Unit,
    onJoinLink: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    BackHandler(expanded) { expanded = false }
    val items = listOf(
        Triple(Icons.Rounded.Edit, "New message", onNewMessage),
        Triple(Icons.Rounded.Group, "New group", onNewGroup),
        Triple(Icons.Rounded.Campaign, "New channel", onNewChannel),
        Triple(Icons.Rounded.Link, "Join with a link", onJoinLink)
    )
    FloatingActionButtonMenu(
        expanded = expanded,
        button = {
            ToggleFloatingActionButton(
                checked = expanded,
                onCheckedChange = { expanded = it },
                modifier = Modifier.semantics {
                    traversalIndex = -1f
                    stateDescription = if (expanded) "Expanded" else "Collapsed"
                    // "Compose" still, so it is found by the name it has
                    // always had.
                    contentDescription = "Compose"
                }
            ) {
                val icon by remember {
                    derivedStateOf { if (checkedProgress > 0.5f) Icons.Rounded.Close else Icons.Rounded.Edit }
                }
                Icon(
                    painter = rememberVectorPainter(icon),
                    contentDescription = null,
                    modifier = Modifier.animateIcon({ checkedProgress })
                )
            }
        }
    ) {
        items.forEach { (icon, label, action) ->
            FloatingActionButtonMenuItem(
                onClick = {
                    expanded = false
                    action()
                },
                icon = { Icon(icon, contentDescription = null) },
                text = { Text(label) }
            )
        }
    }
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
        // Rows take the sheet's tone rather than painting their own: a
        // ListItem defaults to `surface`, a sheet is `surfaceContainerLow`,
        // and the difference reads as pale slabs with seams between them.
        val sheetRow = ListItemDefaults.colors(containerColor = Color.Transparent)
        Text(
            "New message",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
        )
        // Only people. A group, a channel and a link are the FAB menu's
        // other items, one step earlier — see ComposeFabMenu.
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
                                    seed = person.avatarColor,
                                    shape = personShape(person.avatarColor),
                                    photoPath = person.photoPath
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

/** How many rows from the end a chat list asks for its next page. */
private const val LOAD_MORE_AHEAD = 8

/** How far a tab's content grows into place as it fades in. */
private const val FADE_THROUGH_SCALE = 0.92f
