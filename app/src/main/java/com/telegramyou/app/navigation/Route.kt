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

    /**
     * The archive: the same chat rows, filtered to what has been put away.
     *
     * A route rather than a fifth tab. The archive is somewhere you go back
     * out of, which is what a back arrow says and what a tab does not — and
     * it is usually empty, so a permanent seat in the bar would cost one of
     * four places to say "nothing here".
     */
    data object Archive : Route {
        const val PATTERN = "archive"
        override val path = PATTERN
    }

    /**
     * The proxies Telegram connects through. From the chat list's menu, since
     * it is reached for when Telegram will not connect — not somewhere to
     * browse settings to.
     */
    data object Proxy : Route {
        const val PATTERN = "proxy"
        override val path = PATTERN
    }

    data object Settings : Route {
        const val PATTERN = "settings"
        override val path = PATTERN
    }

    /** Where this account is signed in; from Settings. */
    data object Devices : Route {
        const val PATTERN = "settings/devices"
        override val path = PATTERN
    }

    /** The cache on this phone, and clearing it; from Settings. */
    data object Storage : Route {
        const val PATTERN = "settings/storage"
        override val path = PATTERN
    }

    /**
     * Making a group or a channel. Routes rather than sheets: each is a form
     * with a keyboard up and a list under it, and a sheet would be fighting
     * the keyboard for the same half of the screen.
     */
    data object NewGroup : Route {
        const val PATTERN = "new/group"
        override val path = PATTERN
    }

    data object NewChannel : Route {
        const val PATTERN = "new/channel"
        override val path = PATTERN
    }

    /** Getting into somebody else's chat through its invite link. */
    data object JoinLink : Route {
        const val PATTERN = "join"
        override val path = PATTERN
    }

    /**
     * Every photo in one conversation.
     *
     * Its own route rather than a sheet over the chat, because it is a place
     * to browse rather than a step in something — and because opening a photo
     * from it has to come back here rather than to the conversation.
     */
    data class ChatMedia(val chatId: Long) : Route {
        override val path = "chat/$chatId/media"

        companion object {
            const val PATTERN = "chat/{${Chat.ARG_CHAT_ID}}/media"
            val arguments = Chat.arguments
        }
    }

    /**
     * Who is in a conversation, its invite link, and the way out of it.
     *
     * A route rather than a sheet over the chat: leaving is at the bottom of
     * it, and a sheet would put the person back into a conversation they had
     * just left.
     */
    data class ChatInfo(val chatId: Long) : Route {
        override val path = "chat/$chatId/info"

        companion object {
            const val PATTERN = "chat/{${Chat.ARG_CHAT_ID}}/info"
            val arguments = Chat.arguments
        }
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
