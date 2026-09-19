package com.telegramyou.app.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
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
import com.telegramyou.app.ui.chat.ChatScreen
import com.telegramyou.app.ui.chat.ChatMediaScreen
import com.telegramyou.app.ui.chat.ChatViewModel
import com.telegramyou.app.ui.common.telegramViewModelFactory
import com.telegramyou.app.ui.components.RequestNotificationPermission
import com.telegramyou.app.ui.home.HomeScreen
import com.telegramyou.app.ui.home.ArchiveScreen
import com.telegramyou.app.ui.home.HomeViewModel
import com.telegramyou.app.ui.home.HomeTab
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import com.telegramyou.app.ui.settings.SettingsScreen
import com.telegramyou.app.ui.stories.StoryViewModel
import com.telegramyou.app.ui.stories.StoryViewerScreen

@Composable
fun TelegramYouNavHost(
    repository: TelegramRepository,
    appearance: AppearanceStore,
    /** A chat a notification asked to open, or null. */
    openChatId: Long? = null,
    /** Called once the request above has been acted on. */
    onChatOpened: () -> Unit = {}
) {
    val navController = rememberNavController()
    // Only the auth state is read here, and only to decide where to send the
    // person. Everything else a screen needs it asks its own state holder for.
    val auth by repository.observeAuth().collectAsStateWithLifecycle()
    val viewModelFactory = remember(repository) { telegramViewModelFactory(repository) }

    LaunchedEffect(auth.state) {
        when (auth.state) {
            AuthState.Ready -> {
                // Compared against the registered patterns rather than by
                // prefix: "starts with chat" would also match a future
                // chat-settings route and quietly stop redirecting.
                val current = navController.currentDestination?.route
                val alreadyInside = current in setOf(
                    Route.Home.PATTERN,
                    Route.Chat.PATTERN,
                    Route.Story.PATTERN,
                    Route.Settings.PATTERN
                )
                if (!alreadyInside) {
                    navController.navigateTo(Route.Home) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
            AuthState.WaitPhoneNumber,
            AuthState.WaitCode,
            AuthState.WaitPassword,
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

    NavHost(
        navController = navController,
        startDestination = Route.Auth.PATTERN,
        enterTransition = {
            fadeIn(spring()) + slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = spring()
            )
        },
        exitTransition = {
            fadeOut(spring()) + slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = spring()
            )
        },
        popEnterTransition = {
            fadeIn(spring()) + slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = spring()
            )
        },
        popExitTransition = {
            fadeOut(spring()) + slideOutOfContainer(
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
                onResendCode = authViewModel::resendCode
            )
        }
        composable(Route.Home.PATTERN) {
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

            HomeScreen(
                state = state,
                tab = tab,
                onTabSelected = { picked ->
                    tab = picked
                    // The Search tab is the search bar, so selecting it opens
                    // it and leaving it closes it — otherwise the bar would
                    // stay expanded over the Profile tab.
                    homeViewModel.onSearchExpandedChange(picked == HomeTab.Search)
                },
                settings = appearanceSettings,
                onRefresh = homeViewModel::refresh,
                onOpenChat = { id -> navController.navigateTo(Route.Chat(id)) },
                onOpenStory = { story -> navController.navigateTo(Route.Story(story.id)) },
                onSearchExpandedChange = { expanded ->
                    homeViewModel.onSearchExpandedChange(expanded)
                    // Closing the search bar by its own X or back arrow has to
                    // move the tab too, or the bar underneath would still be
                    // lit while the list is no longer being searched.
                    if (!expanded && tab == HomeTab.Search) tab = HomeTab.Chats
                },
                onSearchQueryChange = homeViewModel::onSearchQueryChange,
                onMutedChange = homeViewModel::onMutedChange,
                onPinnedChange = homeViewModel::onPinnedChange,
                onMarkRead = homeViewModel::onMarkRead,
                onArchivedChange = homeViewModel::onArchivedChange,
                onOpenArchive = { navController.navigateTo(Route.Archive) },
                onThemeChange = appearance::setTheme,
                onDynamicColorChange = appearance::setDynamicColor,
                onProfileDraftChange = homeViewModel::onProfileDraftChange,
                onProfileSave = homeViewModel::saveProfile,
                onProfileErrorShown = homeViewModel::onProfileErrorShown,
                onComposeOpen = homeViewModel::onComposeOpen,
                onComposeDismiss = homeViewModel::onComposeDismiss,
                onContactPicked = homeViewModel::onContactPicked,
                onComposeNavigated = homeViewModel::onComposeNavigated,
                onLogout = homeViewModel::logout
            )
        }
        composable(Route.Archive.PATTERN) {
            val homeViewModel: HomeViewModel = viewModel(factory = viewModelFactory)
            val state by homeViewModel.uiState.collectAsStateWithLifecycle()
            ArchiveScreen(
                chats = state.archivedChats,
                onBack = { navController.popBackStack() },
                onOpenChat = { id -> navController.navigateTo(Route.Chat(id)) },
                onMutedChange = homeViewModel::onMutedChange,
                onUnarchive = { id -> homeViewModel.onArchivedChange(id, archived = false) },
                onMarkRead = homeViewModel::onMarkRead
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
                onThemeChange = appearance::setTheme,
                onDynamicColorChange = appearance::setDynamicColor,
                onLogout = {
                    // The auth redirect above takes it from here: logging out
                    // moves the client's state, and the graph follows state
                    // rather than being navigated by hand.
                    homeViewModel.logout()
                }
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
                onOpen = chatViewModel::onPhotoOpened,
                viewingPhoto = state.viewingPhoto,
                onPhotoClosed = chatViewModel::onPhotoClosed
            )
        }

        composable(
            route = Route.Chat.PATTERN,
            arguments = Route.Chat.arguments
        ) {
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
            DisposableEffect(openChat) {
                AppVisibility.openChatId = openChat
                onDispose { AppVisibility.openChatId = null }
            }
            ChatScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onOpenMedia = {
                    state.detail?.chat?.id?.let {
                        navController.navigateTo(Route.ChatMedia(it))
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
                onDeleteRequested = chatViewModel::onDeleteRequested,
                onDeleteDismissed = chatViewModel::onDeleteDismissed,
                onDeleteConfirmed = chatViewModel::onDeleteConfirmed,
                onReactionsRequested = chatViewModel::onReactionsRequested,
                onReactionPickerDismissed = chatViewModel::onReactionPickerDismissed,
                onReactionToggled = chatViewModel::onReactionToggled,
                onSelectionToggled = chatViewModel::onSelectionToggled,
                onSelectionCleared = chatViewModel::onSelectionCleared,
                onSelectionDeleteRequested = chatViewModel::onSelectionDeleteRequested,
                onSelectionDeleteDismissed = chatViewModel::onSelectionDeleteDismissed,
                onSelectionDeleted = chatViewModel::onSelectionDeleted,
                onSearchOpenChange = chatViewModel::onSearchOpenChange,
                onSearchQueryChange = chatViewModel::onSearchQueryChange,
                onAttachmentSheetOpenChange = chatViewModel::onAttachmentSheetOpenChange,
                onForwardRequested = chatViewModel::onForwardRequested,
                onForwardDismissed = chatViewModel::onForwardDismissed,
                onForwardTo = chatViewModel::onForwardTo,
                onVoiceToggled = chatViewModel::onVoiceToggled,
                onVoiceSeek = chatViewModel::onVoiceSeek,
                onPhotoVisible = chatViewModel::onPhotoVisible,
                onPhotoOpened = chatViewModel::onPhotoOpened,
                onPhotoClosed = chatViewModel::onPhotoClosed
            )
        }
        composable(
            route = Route.Story.PATTERN,
            arguments = Route.Story.arguments
        ) {
            // The story arrives as an id in the route and is looked up by its
            // state holder, not held in a variable in this graph. An argument
            // that survives recreation is the difference between a screen that
            // can be rebuilt and one that quietly pops itself on rotation.
            val storyViewModel: StoryViewModel = viewModel(factory = viewModelFactory)
            val state by storyViewModel.uiState.collectAsStateWithLifecycle()
            val story = state.story
            if (story == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
                StoryViewerScreen(
                    story = story,
                    onSeen = storyViewModel::markSeen,
                    onClose = { navController.popBackStack() }
                )
            }
        }
    }
}
