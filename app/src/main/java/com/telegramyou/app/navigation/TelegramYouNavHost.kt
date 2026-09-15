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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.AuthState
import com.telegramyou.app.ui.auth.AuthScreen
import com.telegramyou.app.ui.chat.ChatScreen
import com.telegramyou.app.ui.common.telegramViewModelFactory
import com.telegramyou.app.ui.home.HomeScreen
import com.telegramyou.app.ui.home.HomeViewModel
import com.telegramyou.app.ui.stories.StoryViewerScreen

object Routes {
    const val Auth = "auth"
    const val Home = "home"
    const val Chat = "chat/{chatId}"
    const val Story = "story/{storyId}"
    fun chat(chatId: Long) = "chat/$chatId"
    fun story(storyId: Long) = "story/$storyId"
}

@Composable
fun TelegramYouNavHost(repository: TelegramRepository) {
    val navController = rememberNavController()
    val auth by repository.observeAuth().collectAsStateWithLifecycle()
    val stories by repository.observeStories().collectAsStateWithLifecycle()
    val viewModelFactory = remember(repository) { telegramViewModelFactory(repository) }

    LaunchedEffect(auth.state) {
        when (auth.state) {
            AuthState.Ready -> {
                val current = navController.currentDestination?.route
                if (current != Routes.Home &&
                    current?.startsWith("chat") != true &&
                    current?.startsWith("story") != true
                ) {
                    navController.navigate(Routes.Home) {
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
                if (navController.currentDestination?.route != Routes.Auth) {
                    navController.navigate(Routes.Auth) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.Auth,
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
        composable(Routes.Auth) {
            AuthScreen(auth = auth, repository = repository)
        }
        composable(Routes.Home) {
            val homeViewModel: HomeViewModel = viewModel(factory = viewModelFactory)
            val state by homeViewModel.uiState.collectAsStateWithLifecycle()
            HomeScreen(
                state = state,
                onRefresh = homeViewModel::refresh,
                onOpenChat = { id -> navController.navigate(Routes.chat(id)) },
                onOpenStory = { story -> navController.navigate(Routes.story(story.id)) }
            )
        }
        composable(
            route = Routes.Chat,
            arguments = listOf(navArgument("chatId") { type = NavType.LongType })
        ) { entry ->
            val chatId = entry.arguments?.getLong("chatId") ?: return@composable
            ChatScreen(
                chatId = chatId,
                repository = repository,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.Story,
            arguments = listOf(navArgument("storyId") { type = NavType.LongType })
        ) { entry ->
            // The story arrives as an id and is looked up, rather than being
            // held in a variable in this graph. An argument that survives
            // recreation is the difference between a screen that can be
            // rebuilt and one that quietly pops itself on rotation.
            val storyId = entry.arguments?.getLong("storyId") ?: return@composable
            val story = stories.firstOrNull { it.id == storyId }
            if (story == null) {
                LaunchedEffect(storyId) { navController.popBackStack() }
            } else {
                StoryViewerScreen(
                    story = story,
                    repository = repository,
                    onClose = { navController.popBackStack() }
                )
            }
        }
    }
}
