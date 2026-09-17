package com.telegramyou.app.navigation

import androidx.navigation.NamedNavArgument
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.NavType
import androidx.navigation.navArgument

/**
 * Every destination in the app, in one place.
 *
 * A destination has two forms and they are easy to confuse: the **pattern**
 * the graph registers (`chat/{chatId}`) and the **path** a navigation goes to
 * (`chat/42`). Each type below owns both, so a caller writes
 * `navigateTo(Route.Chat(id))` and cannot get either wrong — the argument is
 * checked by the compiler and formatted in exactly one place.
 *
 * The inventory in ARCHITECTURE.md runs past a hundred screens. String
 * routes scattered across that many call sites are typos waiting to happen,
 * and a typo in a route is a crash at runtime rather than a red build.
 */
sealed interface Route {

    /** Where this instance navigates to, arguments filled in. */
    val path: String

    data object Auth : Route {
        const val PATTERN = "auth"
        override val path = PATTERN
    }

    data object Home : Route {
        const val PATTERN = "home"
        override val path = PATTERN
    }

    data object Settings : Route {
        const val PATTERN = "settings"
        override val path = PATTERN
    }

    data class Chat(val chatId: Long) : Route {
        override val path = "chat/$chatId"

        companion object {
            const val ARG_CHAT_ID = "chatId"
            const val PATTERN = "chat/{$ARG_CHAT_ID}"
            val arguments: List<NamedNavArgument> =
                listOf(navArgument(ARG_CHAT_ID) { type = NavType.LongType })
        }
    }

    data class Story(val storyId: Long) : Route {
        override val path = "story/$storyId"

        companion object {
            const val ARG_STORY_ID = "storyId"
            const val PATTERN = "story/{$ARG_STORY_ID}"
            val arguments: List<NamedNavArgument> =
                listOf(navArgument(ARG_STORY_ID) { type = NavType.LongType })
        }
    }
}

/**
 * Navigate to a [Route] rather than to a string.
 *
 * The argument names in the companions above are the same keys the state
 * holders read out of `SavedStateHandle`, so a renamed argument breaks at
 * compile time in both places at once.
 *
 * **Named `navigateTo`, not `navigate`, and that is not a style choice.**
 * Navigation has carried its own `navigate(route: T)` for type-safe routes
 * since 2.8, and it is a member function — members win over extensions, so
 * `navController.navigate(Route.Home)` called Navigation's, which asks
 * kotlinx.serialization for a serializer this project deliberately does not
 * generate. It compiled, and it threw at the first navigation after login:
 *
 *     SerializationException: Serializer for class 'Home' is not found.
 *
 * A different name cannot be shadowed by a library adding an overload.
 */
fun NavController.navigateTo(route: Route, builder: NavOptionsBuilder.() -> Unit = {}) =
    navigate(route.path, builder)
