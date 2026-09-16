package com.telegramyou.app.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.telegramyou.app.settings.AppearanceStore
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.AuthState
import com.telegramyou.app.ui.auth.AuthScreen
import com.telegramyou.app.ui.auth.AuthViewModel
import com.telegramyou.app.ui.chat.ChatScreen
import com.telegramyou.app.ui.chat.ChatViewModel
import com.telegramyou.app.ui.common.telegramViewModelFactory
import com.telegramyou.app.ui.home.HomeScreen
import com.telegramyou.app.ui.home.HomeViewModel
import com.telegramyou.app.ui.settings.SettingsScreen
import com.telegramyou.app.ui.stories.StoryViewModel
import com.telegramyou.app.ui.stories.StoryViewerScreen

@Composable
fun TelegramYouNavHost(
    repository: TelegramRepository,
    appearance: AppearanceStore
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
                    navController.navigate(Route.Home) {
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
                    navController.navigate(Route.Auth) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        }
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
            HomeScreen(
                state = state,
                onRefresh = homeViewModel::refresh,
                onOpenChat = { id -> navController.navigate(Route.Chat(id)) },
                onOpenStory = { story -> navController.navigate(Route.Story(story.id)) },
                onSearchExpandedChange = homeViewModel::onSearchExpandedChange,
                onSearchQueryChange = homeViewModel::onSearchQueryChange,
                onOpenSettings = { navController.navigate(Route.Settings) },
                onMutedChange = homeViewModel::onMutedChange
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
            route = Route.Chat.PATTERN,
            arguments = Route.Chat.arguments
        ) {
            // chatId is not read here: ChatViewModel takes it from the saved
            // state, so the conversation survives process death with the rest
            // of its state rather than only as long as this composition.
            val chatViewModel: ChatViewModel = viewModel(factory = viewModelFactory)
            val state by chatViewModel.uiState.collectAsStateWithLifecycle()
            ChatScreen(
                state = state,
                onBack = { navController.popBackStack() },
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
                onVoiceToggled = chatViewModel::onVoiceToggled
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
