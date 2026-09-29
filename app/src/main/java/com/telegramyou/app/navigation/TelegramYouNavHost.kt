package com.telegramyou.app.navigation

import com.telegramyou.app.ui.lock.AppLockSettingsScreen
import com.telegramyou.app.settings.AppLockStore
import com.telegramyou.app.ui.chat.LocalCustomEmojiLoader
import com.telegramyou.app.ui.folders.FolderEditScreen
import com.telegramyou.app.ui.folders.FolderEditViewModel
import com.telegramyou.app.ui.folders.FoldersScreen
import com.telegramyou.app.ui.folders.FoldersViewModel
import com.telegramyou.app.ui.chat.LocalFileLoader
import com.telegramyou.app.ui.stories.NewStoryViewModel
import com.telegramyou.app.ui.stories.NewStoryScreen
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.telegramyou.app.ui.motion.LocalNavAnimatedScope
import com.telegramyou.app.ui.motion.LocalSharedTransitionScope
import com.telegramyou.app.ui.motion.containerTransform
import com.telegramyou.app.ui.motion.LocalReduceMotion
import com.telegramyou.app.ui.motion.chatContainerKey
import com.telegramyou.app.ui.motion.storyContainerKey
import com.telegramyou.app.ui.motion.ChatContainerShape
import com.telegramyou.app.ui.motion.ChatContainerSpring
import com.telegramyou.app.ui.motion.StoryContainerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import com.telegramyou.app.notifications.PostedNotifications
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.core.app.NotificationManagerCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.telegramyou.app.settings.AppearanceStore
import com.telegramyou.app.telegram.AppVisibility
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.AuthState
import com.telegramyou.app.ui.auth.AuthScreen
import com.telegramyou.app.ui.auth.AuthViewModel
import com.telegramyou.app.ui.chat.ChatInfoScreen
import com.telegramyou.app.ui.theme.ChatColors
import com.telegramyou.app.ui.settings.AppearanceActions
import com.telegramyou.app.ui.settings.AppearanceScreen
import com.telegramyou.app.ui.people.BlockedScreen
import com.telegramyou.app.ui.people.BlockedViewModel
import com.telegramyou.app.ui.people.ContactsScreen
import com.telegramyou.app.ui.people.ContactsViewModel
import com.telegramyou.app.ui.people.PersonScreen
import com.telegramyou.app.ui.people.PersonViewModel
import com.telegramyou.app.ui.chat.ChatScreen
import com.telegramyou.app.ui.chat.ChatMediaScreen
import com.telegramyou.app.ui.chat.ChatViewModel
import com.telegramyou.app.ui.common.telegramViewModelFactory
import com.telegramyou.app.ui.components.RequestNotificationPermission
import com.telegramyou.app.ui.home.HomeScreen
import com.telegramyou.app.ui.newchat.JoinLinkScreen
import com.telegramyou.app.ui.newchat.NewChatKind
import com.telegramyou.app.ui.newchat.NewChatScreen
import com.telegramyou.app.ui.newchat.NewChatViewModel
import com.telegramyou.app.ui.home.ArchiveScreen
import com.telegramyou.app.ui.home.HomeViewModel
import com.telegramyou.app.ui.home.HomeTab
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import com.telegramyou.app.ui.settings.SettingsScreen
import com.telegramyou.app.ui.settings.GeeksScreen
import com.telegramyou.app.update.AppUpdateScreen
import com.telegramyou.app.settings.GeekStore
import com.telegramyou.app.settings.InMemoryQueryHistory
import com.telegramyou.app.settings.QueryHistory
import com.telegramyou.app.ui.home.SearchActions
import com.telegramyou.app.settings.LocalGeekSettings
import com.telegramyou.app.ui.settings.DevicesScreen
import com.telegramyou.app.ui.settings.PrivacyScreen
import com.telegramyou.app.ui.settings.PrivacyViewModel
import com.telegramyou.app.ui.settings.DevicesViewModel
import com.telegramyou.app.ui.settings.StorageScreen
import com.telegramyou.app.ui.settings.StorageViewModel
import com.telegramyou.app.ui.proxy.ProxyScreen
import com.telegramyou.app.ui.proxy.ProxyViewModel
import com.telegramyou.app.ui.stories.StoryViewModel
import com.telegramyou.app.ui.stories.StoryViewerScreen

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun TelegramYouNavHost(
    repository: TelegramRepository,
    appearance: AppearanceStore,
    /** Settings → Privacy → App lock; absent in previews. */
    appLock: AppLockStore? = null,
    /** Settings → For geeks; defaults where none is given, as in previews. */
    geeks: GeekStore? = null,
    /** Search's recent queries; kept in memory where none is given. */
    queryHistory: QueryHistory? = null,
    /** A chat a notification asked to open, or null. */
    openChatId: Long? = null,
    /** Called once the request above has been acted on. */
    onChatOpened: () -> Unit = {},
    /** The login screen's mark was tapped ten times; see TelegramYouApp.setDemoMode. */
    onDemoRequested: () -> Unit = {}
) {
    val navController = rememberNavController()
    // Only the auth state is read here, and only to decide where to send the
    // person. Everything else a screen needs it asks its own state holder for.
    val auth by repository.observeAuth().collectAsStateWithLifecycle()
    val viewModelFactory = remember(repository) {
        telegramViewModelFactory(repository, queryHistory ?: InMemoryQueryHistory())
    }
    val geekSettings = LocalGeekSettings.current

    // A chat opened from a list: its messages first, then the screen — so the
    // container transform grows a finished conversation out of the row
    // instead of an empty one that fills in mid-flight. See warmChat. One at
    // a time, or a second tap during the wait would open the chat twice.
    val openScope = rememberCoroutineScope()
    var opening by remember { mutableStateOf(false) }
    val openChat: (Long) -> Unit = { id ->
        if (!opening) {
            opening = true
            openScope.launch {
                try {
                    repository.warmChat(id)
                    navController.navigateTo(Route.Chat(id))
                } finally {
                    opening = false
                }
            }
        }
    }

    LaunchedEffect(auth.state) {
        when (auth.state) {
            AuthState.Ready -> {
                // Anywhere but the login screen is already inside. This was a
                // list of the routes that count, and it fell behind as screens
                // were added: this effect runs again on every recreation, so
                // turning the phone on chat info, the media grid, the archive
                // or a new-group form threw the person back to the chat list.
                val current = navController.currentDestination?.route
                val alreadyInside = current != null && current != Route.Auth.PATTERN
                if (!alreadyInside) {
                    navController.navigateTo(Route.Home) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
            AuthState.WaitPhoneNumber,
            AuthState.WaitCode,
            AuthState.WaitEmailAddress,
            AuthState.WaitEmailCode,
            AuthState.WaitPassword,
            AuthState.WaitQrScan,
            AuthState.Bootstrapping,
            AuthState.Error,
            AuthState.Closed -> {
                if (navController.currentDestination?.route != Route.Auth.PATTERN) {
                    navController.navigateTo(Route.Auth) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        }
    }

    // Keyed on the id, so two notifications for the same chat in a row do not
    // navigate twice, and one for a different chat still does. Gated on the
    // auth state because a notification can be tapped before the client has
    // finished signing in, and navigating to a chat from the login screen
    // would strand someone on a conversation they are not authorised to read.
    LaunchedEffect(openChatId, auth.state) {
        val chatId = openChatId ?: return@LaunchedEffect
        if (auth.state != AuthState.Ready) return@LaunchedEffect
        navController.navigateTo(Route.Chat(chatId)) {
            // Home underneath, so back from a chat opened out of the shade
            // lands on the chat list rather than leaving the app.
            popUpTo(Route.Home.PATTERN)
        }
        onChatOpened()
    }

    // One layout around the whole graph, so a screen can open out of an
    // element on the one before it — a chat out of its row, a story out of
    // its circle. See containerTransform.
    // Less motion: screens cross-fade in place rather than slide.
    val reduceMotion = LocalReduceMotion.current
    SharedTransitionLayout {
    CompositionLocalProvider(LocalSharedTransitionScope provides this) {
    NavHost(
        navController = navController,
        startDestination = Route.Auth.PATTERN,
        enterTransition = {
            if (reduceMotion) fadeIn(spring()) else fadeIn(spring()) + slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = spring()
            )
        },
        exitTransition = {
            if (reduceMotion) fadeOut(spring()) else fadeOut(spring()) + slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = spring()
            )
        },
        popEnterTransition = {
            if (reduceMotion) fadeIn(spring()) else fadeIn(spring()) + slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = spring()
            )
        },
        popExitTransition = {
            if (reduceMotion) fadeOut(spring()) else fadeOut(spring()) + slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = spring()
            )
        }
    ) {
        composable(Route.Auth.PATTERN) {
            val authViewModel: AuthViewModel = viewModel(factory = viewModelFactory)
            val state by authViewModel.uiState.collectAsStateWithLifecycle()
            AuthScreen(
                state = state,
                onPhoneChange = authViewModel::onPhoneChange,
                onCodeChange = authViewModel::onCodeChange,
                onPasswordChange = authViewModel::onPasswordChange,
                onSubmitPhone = authViewModel::submitPhone,
                onSubmitCode = authViewModel::submitCode,
                onSubmitPassword = authViewModel::submitPassword,
                onResendCode = authViewModel::resendCode,
                onChangeNumber = authViewModel::onChangeNumber,
                onQrLogin = authViewModel::onQrLogin,
                onOpenProxy = { navController.navigateTo(Route.Proxy) },
                onChangeNumberCancelled = authViewModel::onChangeNumberCancelled,
                onDefaultRegion = authViewModel::onDefaultRegion,
                onDemoRequested = onDemoRequested,
                onEmailChange = authViewModel::onEmailChange,
                onSubmitEmail = authViewModel::submitEmail,
                onSubmitEmailCode = authViewModel::submitEmailCode,
                onResetEmail = authViewModel::resetEmail
            )
        }
        composable(
            Route.Home.PATTERN,
            // Under a chat or a story opening out of this screen, the list
            // fades where it is rather than sliding away: the container is
            // the movement, and a second one beside it would fight it.
            exitTransition = {
                if (targetState.destination.route in containerRoutes) fadeOut(spring()) else null
            },
            popEnterTransition = {
                if (initialState.destination.route in containerRoutes) fadeIn(spring()) else null
            }
        ) {
            val homeViewModel: HomeViewModel = viewModel(factory = viewModelFactory)
            val state by homeViewModel.uiState.collectAsStateWithLifecycle()
            val appearanceSettings by appearance.settings.collectAsStateWithLifecycle()
            // Here rather than at launch: by now there is a chat list on
            // screen, so "let us tell you when these people write" explains
            // itself. See the composable for why it is asked only once.
            RequestNotificationPermission()

            // Saveable, so the tab survives a rotation. Held here rather than
            // in the ViewModel because it is a fact about this composition,
            // not about the account: nothing outside the screen asks which
            // tab is showing.
            var tab by rememberSaveable { mutableStateOf(HomeTab.Chats) }

            CompositionLocalProvider(LocalNavAnimatedScope provides this@composable) {
            HomeScreen(
                state = state,
                tab = tab,
                onTabSelected = { picked ->
                    tab = picked
                    // Search is active while its tab is: the front page loads
                    // on arrival, and leaving the tab ends the search.
                    homeViewModel.onSearchExpandedChange(picked == HomeTab.Search)
                },
                settings = appearanceSettings,
                onRefresh = homeViewModel::refresh,
                onOpenChat = openChat,
                // "My story" makes one; every other circle is watched.
                onOpenStory = { story ->
                    navController.navigateTo(if (story.isOwn) Route.NewStory else Route.Story(story.id))
                },
                onSearchExpandedChange = { expanded ->
                    homeViewModel.onSearchExpandedChange(expanded)
                    // Back on an empty search page leaves for the chat list,
                    // and the tab has to move with it.
                    if (!expanded && tab == HomeTab.Search) tab = HomeTab.Chats
                },
                onSearchQueryChange = homeViewModel::onSearchQueryChange,
                searchActions = SearchActions(
                    onScopeChange = homeViewModel::onSearchScopeChange,
                    onSubmit = homeViewModel::onSearchSubmit,
                    onSearchPosts = homeViewModel::onSearchPosts,
                    onResultOpened = homeViewModel::onSearchResultOpened,
                    onRecentQueryPicked = homeViewModel::onRecentQueryPicked,
                    onRecentQueriesCleared = homeViewModel::onRecentQueriesCleared,
                    onRecentChatRemoved = homeViewModel::onRecentChatRemoved,
                    onRecentChatsCleared = homeViewModel::onRecentChatsCleared
                ),
                onMutedChange = homeViewModel::onMutedChange,
                onPinnedChange = homeViewModel::onPinnedChange,
                onMarkRead = homeViewModel::onMarkRead,
                onArchivedChange = homeViewModel::onArchivedChange,
                onClearHistory = homeViewModel::onClearHistory,
                onDeleteChat = homeViewModel::onDeleteChat,
                onOpenArchive = { navController.navigateTo(Route.Archive) },
                onFolderSelected = homeViewModel::onFolderSelected,
                onThemeChange = appearance::setTheme,
                onProfileDraftChange = homeViewModel::onProfileDraftChange,
                onProfileSave = homeViewModel::saveProfile,
                onProfileErrorShown = homeViewModel::onProfileErrorShown,
                onProfilePhotoPicked = homeViewModel::onProfilePhotoPicked,
                onComposeOpen = homeViewModel::onComposeOpen,
                onNewGroup = { navController.navigateTo(Route.NewGroup) },
                onNewChannel = { navController.navigateTo(Route.NewChannel) },
                onJoinLink = { navController.navigateTo(Route.JoinLink) },
                onComposeDismiss = homeViewModel::onComposeDismiss,
                onContactPicked = homeViewModel::onContactPicked,
                onComposeNavigated = homeViewModel::onComposeNavigated,
                onLogout = homeViewModel::logout,
                onErrorShown = homeViewModel::onErrorShown,
                onListEndReached = homeViewModel::onListEndReached,
                onOpenProxy = { navController.navigateTo(Route.Proxy) },
                onOpenSavedMessages = homeViewModel::onOpenSavedMessages,
                onOpenContacts = { navController.navigateTo(Route.Contacts) },
                onOpenAppearance = { navController.navigateTo(Route.Appearance) },
                onOpenFolders = { navController.navigateTo(Route.Folders) },
                onOpenDevices = { navController.navigateTo(Route.Devices) },
                onOpenStorage = { navController.navigateTo(Route.Storage) },
                onOpenPrivacy = { navController.navigateTo(Route.Privacy) },
                onOpenGeeks = { navController.navigateTo(Route.Geeks) },
                onOpenUpdates = { navController.navigateTo(Route.Updates) }
            )
            }
        }
        composable(Route.Proxy.PATTERN) {
            val proxyViewModel: ProxyViewModel = viewModel(factory = viewModelFactory)
            val state by proxyViewModel.uiState.collectAsStateWithLifecycle()
            ProxyScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onUseProxyChange = proxyViewModel::onUseProxyChange,
                onSelect = proxyViewModel::onSelect,
                onRemove = proxyViewModel::onRemove,
                onAddOpen = proxyViewModel::onAddOpen,
                onAddDismiss = proxyViewModel::onAddDismiss,
                onDraftChange = proxyViewModel::onDraftChange,
                onLinkPasted = proxyViewModel::onLinkPasted,
                onSave = proxyViewModel::onSave,
                onErrorShown = proxyViewModel::onErrorShown
            )
        }
        // Group and channel share a screen and a state holder; only what the
        // form asks for differs. Each gets its own back-stack entry, so its
        // own holder — a name typed into one does not appear in the other.
        listOf(
            Route.NewGroup.PATTERN to NewChatKind.Group,
            Route.NewChannel.PATTERN to NewChatKind.Channel
        ).forEach { (pattern, kind) ->
            composable(pattern) {
                val newChatViewModel: NewChatViewModel = viewModel(factory = viewModelFactory)
                val state by newChatViewModel.uiState.collectAsStateWithLifecycle()
                // Into the new chat, replacing this form on the back stack:
                // Back from the conversation should land on the list, not on
                // a form for a group that already exists.
                LaunchedEffect(state.openChatId) {
                    state.openChatId?.let { id ->
                        newChatViewModel.onNavigated()
                        navController.popBackStack()
                        navController.navigateTo(Route.Chat(id))
                    }
                }
                NewChatScreen(
                    kind = kind,
                    state = state,
                    onBack = { navController.popBackStack() },
                    onTitleChange = newChatViewModel::onTitleChange,
                    onDescriptionChange = newChatViewModel::onDescriptionChange,
                    onMemberToggled = newChatViewModel::onMemberToggled,
                    onCreate = { newChatViewModel.create(kind) },
                    onErrorShown = newChatViewModel::onErrorShown
                )
            }
        }
        composable(Route.JoinLink.PATTERN) {
            val newChatViewModel: NewChatViewModel = viewModel(factory = viewModelFactory)
            val state by newChatViewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(state.openChatId) {
                state.openChatId?.let { id ->
                    newChatViewModel.onNavigated()
                    navController.popBackStack()
                    navController.navigateTo(Route.Chat(id))
                }
            }
            JoinLinkScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onLinkChange = newChatViewModel::onLinkChange,
                onJoin = newChatViewModel::join,
                onErrorShown = newChatViewModel::onErrorShown
            )
        }
        composable(Route.Archive.PATTERN) {
            val homeViewModel: HomeViewModel = viewModel(factory = viewModelFactory)
            val state by homeViewModel.uiState.collectAsStateWithLifecycle()
            ArchiveScreen(
                chats = state.archivedChats,
                onBack = { navController.popBackStack() },
                onOpenChat = openChat,
                onMutedChange = homeViewModel::onMutedChange,
                onUnarchive = { id -> homeViewModel.onArchivedChange(id, archived = false) },
                onMarkRead = homeViewModel::onMarkRead,
                errorMessage = state.errorMessage,
                onErrorShown = homeViewModel::onErrorShown
            )
        }

        composable(Route.Settings.PATTERN) {
            val homeViewModel: HomeViewModel = viewModel(factory = viewModelFactory)
            val home by homeViewModel.uiState.collectAsStateWithLifecycle()
            // Collected here rather than passed down as a value: the switch
            // has to redraw the screen it is on, not only the theme around it.
            val settings by appearance.settings.collectAsStateWithLifecycle()
            SettingsScreen(
                settings = settings,
                me = home.me,
                onBack = { navController.popBackStack() },
                onLogout = {
                    // The auth redirect above takes it from here: logging out
                    // moves the client's state, and the graph follows state
                    // rather than being navigated by hand.
                    homeViewModel.logout()
                },
                onOpenDevices = { navController.navigateTo(Route.Devices) },
                onOpenStorage = { navController.navigateTo(Route.Storage) },
                onOpenPrivacy = { navController.navigateTo(Route.Privacy) },
                onOpenGeeks = { navController.navigateTo(Route.Geeks) },
                onOpenProxy = { navController.navigateTo(Route.Proxy) },
                onOpenUpdates = { navController.navigateTo(Route.Updates) },
                onOpenAppearance = { navController.navigateTo(Route.Appearance) },
                onOpenFolders = { navController.navigateTo(Route.Folders) }
            )
        }
        composable(Route.Appearance.PATTERN) {
            // Collected here so the screen redraws with each change — the
            // theme around it redraws from MainActivity's own collection.
            val settings by appearance.settings.collectAsStateWithLifecycle()
            AppearanceScreen(
                settings = settings,
                onBack = { navController.popBackStack() },
                actions = AppearanceActions(
                    onThemeChange = appearance::setTheme,
                    onDynamicColorChange = appearance::setDynamicColor,
                    onAccentChange = appearance::setAccent,
                    onPureBlackChange = appearance::setPureBlack,
                    onChatColorsFromAvatarChange = appearance::setChatColorsFromAvatar,
                    onShapedAvatarsChange = appearance::setShapedAvatars,
                    onTextScaleChange = appearance::setTextScale,
                    onChatWallpaperChange = appearance::setChatWallpaper,
                    onOutgoingToneChange = appearance::setOutgoingTone,
                    onBubbleCornersChange = appearance::setBubbleCorners,
                    onMessageTextScaleChange = appearance::setMessageTextScale,
                    onTwoLinePreviewsChange = appearance::setTwoLinePreviews,
                    onReduceMotionChange = appearance::setReduceMotion
                )
            )
        }
        composable(Route.Updates.PATTERN) {
            AppUpdateScreen(onBack = { navController.popBackStack() })
        }
        composable(Route.Geeks.PATTERN) {
            val store = geeks ?: return@composable
            val settings by store.settings.collectAsStateWithLifecycle()
            GeeksScreen(
                settings = settings,
                onBack = { navController.popBackStack() },
                onChange = store::update
            )
        }
        composable(Route.Devices.PATTERN) {
            val devicesViewModel: DevicesViewModel = viewModel(factory = viewModelFactory)
            val state by devicesViewModel.uiState.collectAsStateWithLifecycle()
            DevicesScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onSessionSelected = devicesViewModel::onSessionSelected,
                onTerminateAllRequested = devicesViewModel::onTerminateAllRequested,
                onDismiss = devicesViewModel::onDismiss,
                onTerminateConfirmed = devicesViewModel::onTerminateConfirmed,
                onTerminateAllConfirmed = devicesViewModel::onTerminateAllConfirmed,
                onMessageShown = devicesViewModel::onMessageShown
            )
        }
        composable(Route.Privacy.PATTERN) {
            val privacyViewModel: PrivacyViewModel = viewModel(factory = viewModelFactory)
            val state by privacyViewModel.uiState.collectAsStateWithLifecycle()
            PrivacyScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onEdit = privacyViewModel::onEdit,
                onDismiss = privacyViewModel::onDismiss,
                onAudienceChosen = privacyViewModel::onAudienceChosen,
                onMessageShown = privacyViewModel::onMessageShown,
                onOpenBlocked = { navController.navigateTo(Route.Blocked) },
                onOpenAppLock = { navController.navigateTo(Route.AppLock) },
                appLockSummary = appLock?.settings?.collectAsStateWithLifecycle()?.value?.let { lock ->
                    if (lock.enabled) "On · ${lock.autoLock.label.replaceFirstChar(Char::lowercase)}" else "Off"
                } ?: "Off"
            )
        }
        composable(Route.NewStory.PATTERN) {
            val newStoryViewModel: NewStoryViewModel = viewModel(factory = viewModelFactory)
            val state by newStoryViewModel.uiState.collectAsStateWithLifecycle()
            NewStoryScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onPicked = newStoryViewModel::onPicked,
                onCaptionChange = newStoryViewModel::onCaptionChange,
                onAudienceChange = newStoryViewModel::onAudienceChange,
                onPost = newStoryViewModel::onPost,
                onErrorShown = newStoryViewModel::onErrorShown,
                onPosted = { navController.popBackStack() }
            )
        }
        composable(Route.AppLock.PATTERN) {
            val store = appLock ?: return@composable
            val settings by store.settings.collectAsStateWithLifecycle()
            AppLockSettingsScreen(
                settings = settings,
                onBack = { navController.popBackStack() },
                onPinSet = store::setPin,
                onDisable = store::disable,
                onBiometricChange = store::setBiometric,
                onAutoLockChange = store::setAutoLock,
                onHideInRecentsChange = store::setHideInRecents
            )
        }
        composable(Route.Blocked.PATTERN) {
            val blockedViewModel: BlockedViewModel = viewModel(factory = viewModelFactory)
            val state by blockedViewModel.uiState.collectAsStateWithLifecycle()
            // On every visit: see BlockedViewModel.refresh.
            LaunchedEffect(Unit) { blockedViewModel.refresh() }
            BlockedScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onUnblock = blockedViewModel::onUnblock,
                onOpenPerson = { navController.navigateTo(Route.Person(it)) },
                onMessageShown = blockedViewModel::onMessageShown
            )
        }
        composable(Route.Folders.PATTERN) {
            val foldersViewModel: FoldersViewModel = viewModel(factory = viewModelFactory)
            val state by foldersViewModel.uiState.collectAsStateWithLifecycle()
            FoldersScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onOpenFolder = { navController.navigateTo(Route.FolderEdit(it)) },
                onNewFolder = { navController.navigateTo(Route.FolderEdit(Route.FolderEdit.NEW)) },
                onMove = foldersViewModel::onMove,
                onDeleteRequested = foldersViewModel::onDeleteRequested,
                onDeleteDismissed = foldersViewModel::onDeleteDismissed,
                onDeleteConfirmed = foldersViewModel::onDeleteConfirmed,
                onMessageShown = foldersViewModel::onMessageShown
            )
        }
        composable(
            route = Route.FolderEdit.PATTERN,
            arguments = Route.FolderEdit.arguments
        ) {
            val editViewModel: FolderEditViewModel = viewModel(factory = viewModelFactory)
            val state by editViewModel.uiState.collectAsStateWithLifecycle()
            FolderEditScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onNameChange = editViewModel::onNameChange,
                onRulesChange = editViewModel::onRulesChange,
                onPickerOpen = editViewModel::onPickerOpen,
                onPickerDismiss = editViewModel::onPickerDismiss,
                onChatToggled = editViewModel::onChatToggled,
                onSave = editViewModel::onSave,
                onDeleteRequested = editViewModel::onDeleteRequested,
                onDeleteDismissed = editViewModel::onDeleteDismissed,
                onDeleteConfirmed = editViewModel::onDeleteConfirmed,
                onMessageShown = editViewModel::onMessageShown
            )
        }
        composable(Route.Contacts.PATTERN) {
            val contactsViewModel: ContactsViewModel = viewModel(factory = viewModelFactory)
            val state by contactsViewModel.uiState.collectAsStateWithLifecycle()
            ContactsScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onContactClick = contactsViewModel::onContactClick,
                onAddRequested = contactsViewModel::onAddRequested,
                onDraftChange = contactsViewModel::onDraftChange,
                onAddDismissed = contactsViewModel::onAddDismissed,
                onAddConfirmed = contactsViewModel::onAddConfirmed,
                onOpenChat = { navController.navigateTo(Route.Chat(it)) },
                onChatOpened = contactsViewModel::onChatOpened,
                onMessageShown = contactsViewModel::onMessageShown
            )
        }
        composable(
            route = Route.Person.PATTERN,
            arguments = Route.Person.arguments
        ) {
            val personViewModel: PersonViewModel = viewModel(factory = viewModelFactory)
            val state by personViewModel.uiState.collectAsStateWithLifecycle()
            PersonScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onBlockedChange = personViewModel::onBlockedChange,
                onSendMessage = personViewModel::onSendMessage,
                onOpenChat = { navController.navigateTo(Route.Chat(it)) },
                onChatOpened = personViewModel::onChatOpened,
                onMessageShown = personViewModel::onMessageShown
            )
        }
        composable(Route.Storage.PATTERN) {
            val storageViewModel: StorageViewModel = viewModel(factory = viewModelFactory)
            val state by storageViewModel.uiState.collectAsStateWithLifecycle()
            StorageScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onKindToggle = storageViewModel::onKindToggle,
                onClearRequested = storageViewModel::onClearRequested,
                onDismiss = storageViewModel::onDismiss,
                onClearConfirmed = storageViewModel::onClearConfirmed,
                onMessageShown = storageViewModel::onMessageShown
            )
        }
        composable(
            route = Route.ChatMedia.PATTERN,
            arguments = Route.ChatMedia.arguments
        ) {
            val chatViewModel: ChatViewModel = viewModel(factory = viewModelFactory)
            val state by chatViewModel.uiState.collectAsStateWithLifecycle()
            // On every visit, not once: photos arrive while this screen is
            // closed, and a grid showing yesterday's set is quietly wrong.
            LaunchedEffect(Unit) { chatViewModel.loadMedia() }
            ChatMediaScreen(
                title = state.detail?.chat?.title ?: "Media",
                media = state.media,
                isLoading = state.isLoadingMedia,
                onBack = { navController.popBackStack() },
                // One entry point, two kinds of thing behind it: the grid
                // holds photos and videos alike, and which viewer opens is
                // the message's business rather than the tile's.
                onOpen = { message ->
                    if (message.video != null) {
                        chatViewModel.onVideoOpened(message)
                    } else {
                        chatViewModel.onPhotoOpened(message)
                    }
                },
                viewingPhoto = state.viewingPhoto,
                onPhotoClosed = chatViewModel::onPhotoClosed,
                viewingVideo = state.viewingVideo,
                onVideoClosed = chatViewModel::onVideoClosed,
                transfers = state.transfers,
                onGalleryPage = chatViewModel::onGalleryPage
            )
        }

        composable(
            route = Route.ChatInfo.PATTERN,
            arguments = Route.ChatInfo.arguments
        ) {
            val chatViewModel: ChatViewModel = viewModel(factory = viewModelFactory)
            val state by chatViewModel.uiState.collectAsStateWithLifecycle()
            // On every visit: an invite link can be revoked, and a member can
            // join, while this screen is closed.
            LaunchedEffect(Unit) {
                chatViewModel.loadInviteLink()
                chatViewModel.loadPerson()
            }
            // Out of the conversation as well as out of this screen — the
            // chat behind it is one this account is no longer in. Popping to
            // the list rather than back one step, which would land there.
            LaunchedEffect(state.hasLeft) {
                if (state.hasLeft) {
                    chatViewModel.onLeaveNavigated()
                    navController.popBackStack(Route.Home.PATTERN, inclusive = false)
                }
            }
            ChatInfoScreen(
                detail = state.detail,
                inviteLink = state.inviteLink,
                confirmingLeave = state.confirmingLeave,
                onBack = { navController.popBackStack() },
                onLeaveRequested = chatViewModel::onLeaveRequested,
                onLeaveDismissed = chatViewModel::onLeaveDismissed,
                onLeaveConfirmed = chatViewModel::onLeaveConfirmed,
                onNotificationsChange = chatViewModel::onNotificationsChange,
                person = state.person,
                onBlockedChange = chatViewModel::onBlockedChange,
                onOpenMedia = {
                    state.detail?.chat?.id?.let { navController.navigateTo(Route.ChatMedia(it)) }
                },
                onMemberClick = { navController.navigateTo(Route.Person(it)) },
                onRenewInviteLink = chatViewModel::onRenewInviteLink,
                onDeleteAllMine = chatViewModel::onDeleteAllMine,
                errorMessage = state.errorMessage,
                onErrorShown = chatViewModel::onErrorShown,
                notice = state.notice,
                onNoticeShown = chatViewModel::onNoticeShown
            )
        }

        composable(
            route = Route.Chat.PATTERN,
            arguments = Route.Chat.arguments,
            // The row it opened out of does the moving; see containerTransform.
            enterTransition = { fadeIn(spring()) },
            // Kept on screen until the container transform has shrunk back
            // into its row. A fade here finished in a fifth of a second
            // while the container took half a second to close, so it went
            // transparent halfway and the row seemed to grow out of an
            // empty box — the "buggy" close. The container's own crossfade
            // is the only fade the way back needs.
            popExitTransition = { ExitTransition.KeepUntilTransitionsFinished }
        ) { entry ->
            val openedChatId = entry.arguments?.getLong(Route.Chat.ARG_CHAT_ID) ?: 0L
            // chatId is not read here: ChatViewModel takes it from the saved
            // state, so the conversation survives process death with the rest
            // of its state rather than only as long as this composition.
            val chatViewModel: ChatViewModel = viewModel(factory = viewModelFactory)
            val state by chatViewModel.uiState.collectAsStateWithLifecycle()
            // Tells the notification service which chat is being read, so it
            // does not announce a message the person is looking at. Cleared on
            // leaving rather than on the next chat's arrival: between two
            // conversations there is no open chat, and claiming the old one
            // would silence a message that belongs in the shade.
            val openChat = state.detail?.chat?.id
            val context = LocalContext.current
            DisposableEffect(openChat) {
                AppVisibility.openChatId = openChat
                // What the shade holds for this chat is being read right now.
                openChat?.let { id ->
                    NotificationManagerCompat.from(context)
                        .cancel(PostedNotifications.notificationId(id))
                    PostedNotifications.clear(id)
                }
                onDispose { AppVisibility.openChatId = null }
            }
            // Read while in front, and again as each newer message lands —
            // but not while the app is behind something else, which is what
            // repeatOnLifecycle(RESUMED) keeps out. See ChatViewModel.onSeen.
            val lifecycleOwner = LocalLifecycleOwner.current
            val newest = state.messages.lastOrNull()?.id
            LaunchedEffect(newest, lifecycleOwner) {
                if (newest == null) return@LaunchedEffect
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    chatViewModel.onSeen()
                }
            }
            CompositionLocalProvider(
                LocalNavAnimatedScope provides this@composable,
                // Stickers fetch their own files; see StickerView.
                LocalFileLoader provides chatViewModel::loadFile,
                LocalCustomEmojiLoader provides chatViewModel::loadCustomEmoji
            ) {
            // Collected here as well as at the top: the setting is read by
            // the conversation, and a switch flipped in Appearance has to
            // recolour a chat opened after it without a restart.
            val chatAppearance by appearance.settings.collectAsStateWithLifecycle()
            Box(
                Modifier
                    .fillMaxSize()
                    .containerTransform(chatContainerKey(openedChatId), ChatContainerShape, isScreen = true, bounds = ChatContainerSpring)
            ) {
            ChatColors(chat = state.detail?.chat, enabled = chatAppearance.chatColorsFromAvatar) {
            ChatScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onOpenMedia = {
                    state.detail?.chat?.id?.let {
                        navController.navigateTo(Route.ChatMedia(it))
                    }
                },
                onOpenInfo = {
                    state.detail?.chat?.id?.let {
                        navController.navigateTo(Route.ChatInfo(it))
                    }
                },
                onDraftChange = chatViewModel::onDraftChange,
                onAttachmentPicked = chatViewModel::onAttachmentPicked,
                onAttachmentCleared = chatViewModel::onAttachmentCleared,
                onReplyTo = chatViewModel::onReplyTo,
                onEdit = chatViewModel::onEdit,
                onComposerBannerCancelled = chatViewModel::onComposerBannerCancelled,
                onSend = chatViewModel::onSend,
                onLoadOlder = chatViewModel::onLoadOlder,
                onJumpToMessage = chatViewModel::onJumpToMessage,
                onJumpToLatest = chatViewModel::onJumpToLatest,
                onLoadNewer = chatViewModel::onLoadNewer,
                onScrollTargetReached = chatViewModel::onScrollTargetReached,
                onDeleteRequested = chatViewModel::onDeleteRequested,
                onDeleteDismissed = chatViewModel::onDeleteDismissed,
                onDeleteConfirmed = chatViewModel::onDeleteConfirmed,
                onReactionsRequested = chatViewModel::onReactionsRequested,
                onReactionPickerDismissed = chatViewModel::onReactionPickerDismissed,
                onReactionToggled = chatViewModel::onReactionToggled,
                onSelectionToggled = chatViewModel::onSelectionToggled,
                onMessageMenuOpened = chatViewModel::onMessageActionsNeeded,
                onForwardOne = chatViewModel::onForwardOne,
                onContentOpened = { chatViewModel.onContentOpened(it.id) },
                onSelectionCleared = chatViewModel::onSelectionCleared,
                onSelectionDeleteRequested = chatViewModel::onSelectionDeleteRequested,
                onSelectionDeleteDismissed = chatViewModel::onSelectionDeleteDismissed,
                onSelectionDeleted = chatViewModel::onSelectionDeleted,
                onSearchOpenChange = chatViewModel::onSearchOpenChange,
                onSearchQueryChange = chatViewModel::onSearchQueryChange,
                onAttachmentSheetOpenChange = chatViewModel::onAttachmentSheetOpenChange,
                onForwardRequested = chatViewModel::onForwardRequested,
                onForwardDismissed = chatViewModel::onForwardDismissed,
                onForwardTo = { target ->
                    chatViewModel.onForwardTo(target, withoutQuote = geekSettings.forwardWithoutQuote)
                },
                onVoiceToggled = chatViewModel::onVoiceToggled,
                onVoiceSeek = chatViewModel::onVoiceSeek,
                onPhotoVisible = chatViewModel::onPhotoVisible,
                onPhotoOpened = chatViewModel::onPhotoOpened,
                onPhotoClosed = chatViewModel::onPhotoClosed,
                onVideoOpened = chatViewModel::onVideoOpened,
                onVideoClosed = chatViewModel::onVideoClosed,
                onGalleryPage = chatViewModel::onGalleryPage,
                onSaveGif = chatViewModel::onSaveGif,
                onContactOpen = { contact -> navController.navigateTo(Route.Person(contact.userId)) },
                onContactAdd = chatViewModel::onContactAdd,
                onContactPickerOpen = chatViewModel::onContactPickerOpen,
                onContactPickerDismiss = chatViewModel::onContactPickerDismiss,
                onContactPicked = chatViewModel::onContactPicked,
                onErrorShown = chatViewModel::onErrorShown,
                onExpressionsOpen = chatViewModel::onExpressionsOpen,
                onExpressionsClose = chatViewModel::onExpressionsClose,
                onExpressionTab = chatViewModel::onExpressionTab,
                onStickerSetSelected = chatViewModel::onStickerSetSelected,
                onStickerPicked = chatViewModel::onStickerPicked,
                onStickerImage = chatViewModel::onStickerImage,
                onSendLocation = chatViewModel::onSendLocation,
                onCustomEmojiSetSelected = chatViewModel::onCustomEmojiSetSelected,
                onCustomEmojiPicked = chatViewModel::onCustomEmojiPicked,
                onGifQueryChange = chatViewModel::onGifQueryChange,
                onGifVisible = chatViewModel::onGifVisible,
                onGifPicked = chatViewModel::onGifPicked,
                onVote = chatViewModel::onVote,
                onBotButton = chatViewModel::onBotButton,
                onReplyKey = chatViewModel::onReplyKey,
                onBotAnswerShown = chatViewModel::onBotAnswerShown,
                onPollOpen = chatViewModel::onPollOpen,
                onPollChange = chatViewModel::onPollChange,
                onPollSend = chatViewModel::onPollSend,
                onPollDismiss = chatViewModel::onPollDismiss,
                onSchedule = { at -> chatViewModel.onSchedule(at) },
                onScheduledOpen = chatViewModel::onScheduledOpen,
                onScheduledSendNow = chatViewModel::onScheduledSendNow,
                onScheduledDelete = chatViewModel::onScheduledDelete,
                onScheduledDismiss = chatViewModel::onScheduledDismiss,
                onNoticeShown = chatViewModel::onNoticeShown,
                onPinToggled = chatViewModel::onPinToggled,
                onMention = { username ->
                    openScope.launch {
                        val found = repository.chatByUsername(username)
                        if (found != null) openChat(found)
                    }
                }
            )
            }
            }
            }
        }
        composable(
            route = Route.Story.PATTERN,
            arguments = Route.Story.arguments,
            enterTransition = { fadeIn(spring()) },
            // Kept on screen until the container transform has shrunk back
            // into its row. A fade here finished in a fifth of a second
            // while the container took half a second to close, so it went
            // transparent halfway and the row seemed to grow out of an
            // empty box — the "buggy" close. The container's own crossfade
            // is the only fade the way back needs.
            popExitTransition = { ExitTransition.KeepUntilTransitionsFinished }
        ) { entry ->
            val openedStoryId = entry.arguments?.getLong(Route.Story.ARG_STORY_ID) ?: 0L
            // The story arrives as an id in the route and is looked up by its
            // state holder, not held in a variable in this graph. An argument
            // that survives recreation is the difference between a screen that
            // can be rebuilt and one that quietly pops itself on rotation.
            val storyViewModel: StoryViewModel = viewModel(factory = viewModelFactory)
            val state by storyViewModel.uiState.collectAsStateWithLifecycle()
            val story = state.story
            CompositionLocalProvider(LocalNavAnimatedScope provides this@composable) {
            Box(
                Modifier
                    .fillMaxSize()
                    .containerTransform(storyContainerKey(openedStoryId), StoryContainerShape, isScreen = true)
            ) {
            when {
                // Closed only once the lookup has answered: the first state is
                // "not looked up yet", and closing on that is what made every
                // live story shut the moment it opened.
                state.isGone -> LaunchedEffect(Unit) { navController.popBackStack() }
                story == null || state.isLoading -> Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator(color = Color.White)
                }
                else -> StoryViewerScreen(
                    story = story,
                    state = state,
                    onSeen = storyViewModel::markSeen,
                    onNext = storyViewModel::next,
                    onPrevious = storyViewModel::previous,
                    onClose = { navController.popBackStack() }
                )
            }
            }
            }
        }
    }
    }
    }
}

/** Destinations that open out of an element on the chat list; see containerTransform. */
private val containerRoutes = setOf(Route.Chat.PATTERN, Route.Story.PATTERN)
