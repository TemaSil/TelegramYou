package com.telegramyou.app.telegram.model

/**
 * The tabs of search, once something has been typed.
 *
 * Telegram's own search splits the same way: people and groups and channels
 * and bots are all chats underneath, and what a tab does is choose which of
 * them to show. [Messages] and [Posts] are the two that search words rather
 * than names — messages in the chats this account is in, and posts in public
 * channels it may never have opened.
 */
enum class SearchScope(val label: String) {
    // In this order so the four used most fit across a phone without the
    // tab row scrolling.
    All("All"),
    Chats("Chats"),
    Messages("Messages"),
    Posts("Posts"),
    Channels("Channels"),
    Groups("Groups"),
    Bots("Bots");

    /** Whether [chat] belongs under this tab. */
    fun admits(chat: ChatPreview): Boolean = when (this) {
        All -> true
        Chats -> !chat.isGroup && !chat.isChannel && !chat.isBot
        Groups -> chat.isGroup
        Channels -> chat.isChannel
        Bots -> chat.isBot
        Messages, Posts -> false
    }

    val showsChats: Boolean get() = this != Messages && this != Posts
    val showsMessages: Boolean get() = this == All || this == Messages
    val showsPosts: Boolean get() = this == Posts
}

/**
 * Chats found by name: the ones this account is in first, then public ones
 * it is not — each once, and in the server's order within each half.
 *
 * The same chat can come back from both searches, a joined channel being
 * public as well, and a result list that repeats a row reads as a bug.
 */
fun mergeChatResults(known: List<ChatPreview>, global: List<ChatPreview>): SearchChats {
    val seen = HashSet<Long>()
    val mine = known.filter { seen.add(it.id) }
    val elsewhere = global.filter { seen.add(it.id) }
    return SearchChats(mine, elsewhere)
}

/** [mine] are chats this account is in; [global] are public ones it is not. */
data class SearchChats(
    val mine: List<ChatPreview> = emptyList(),
    val global: List<ChatPreview> = emptyList()
) {
    fun filteredBy(scope: SearchScope) = SearchChats(
        mine = mine.filter(scope::admits),
        global = global.filter(scope::admits)
    )

    val isEmpty: Boolean get() = mine.isEmpty() && global.isEmpty()
}

/**
 * Public channel posts found by words, and how many more searches the
 * server will allow today.
 *
 * Telegram limits this search: a few free queries a day and then Stars per
 * query. This client never pays, so once [limitReached] is set the tab says
 * when the next free one comes rather than offering to spend anything.
 */
data class PostSearch(
    val hits: List<MessageHit> = emptyList(),
    val limitReached: Boolean = false,
    /** Free searches left today, when the server said. */
    val freeLeft: Int? = null,
    /** Seconds until another free search, when none are left. */
    val nextFreeInSeconds: Int = 0
) {
    /** "3 free searches left today", "… next in 3 hours" — see durationLabel. */
    val limitLabel: String?
        get() = when {
            limitReached -> "Free searches used up for today" +
                (nextFreeInSeconds.takeIf { it > 0 }?.let { " — next in ${durationLabel(it)}" } ?: "")
            freeLeft == null -> null
            freeLeft == 1 -> "1 free search left today"
            else -> "$freeLeft free searches left today"
        }
}

/**
 * The queries typed into search, newest first — what a person looked for,
 * as opposed to the chats they found, which Telegram itself keeps.
 *
 * Only a query that led somewhere is kept (see [remembering]): the history of
 * every prefix typed on the way to a word is noise.
 */
fun List<String>.remembering(query: String, limit: Int = RECENT_QUERY_LIMIT): List<String> {
    val trimmed = query.trim()
    if (trimmed.length < 2) return this
    // A longer query that starts with an old one replaces it: "mat" then
    // "material" is one search that was finished, not two.
    val rest = filterNot {
        it.equals(trimmed, ignoreCase = true) || trimmed.startsWith(it, ignoreCase = true)
    }
    return (listOf(trimmed) + rest).take(limit)
}

const val RECENT_QUERY_LIMIT = 8
