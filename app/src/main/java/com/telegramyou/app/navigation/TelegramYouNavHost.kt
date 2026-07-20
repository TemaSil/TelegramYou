package com.telegramyou.app.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.AuthState
import com.telegramyou.app.telegram.model.StoryItem
import com.telegramyou.app.ui.auth.AuthScreen
import com.telegramyou.app.ui.chat.ChatScreen
import com.telegramyou.app.ui.home.HomeScreen
import com.telegramyou.app.ui.stories.StoryViewerScreen

object Routes {
    const val Auth = "auth"
    const val Home = "home"
    const val Chat = "chat/{chatId}"
    const val Story = "story"
    fun chat(chatId: Long) = "chat/$chatId"
}

@Composable
fun TelegramYouNavHost(repository: TelegramRepository) {
    val navController = rememberNavController()
    val auth by repository.observeAuth().collectAsStateWithLifecycle()
    val chats by repository.observeChats().collectAsStateWithLifecycle()
    val stories by repository.observeStories().collectAsStateWithLifecycle()
    var activeStory by remember { mutableStateOf<StoryItem?>(null) }

    LaunchedEffect(auth.state) {
        when (auth.state) {
            AuthState.Ready -> {
                val current = navController.currentDestination?.route
                if (current != Routes.Home && current?.startsWith("chat") != true && current != Routes.Story) {
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
            HomeScreen(
                me = auth.me,
                chats = chats,
                stories = stories,
                repository = repository,
                onOpenChat = { id -> navController.navigate(Routes.chat(id)) },
                onOpenStory = { story ->
                    activeStory = story
                    navController.navigate(Routes.Story)
                }
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
        composable(Routes.Story) {
            val story = activeStory
            if (story == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
                StoryViewerScreen(
                    story = story,
                    repository = repository,
                    onClose = {
                        activeStory = null
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
