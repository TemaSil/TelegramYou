package com.telegramyou.app.telegram.model

/**
 * Where each chat stands in TDLib's chat lists: the main list, the archive,
 * and every folder.
 *
 * TDLib has no "is this chat in my list" flag. A chat is in a list when it
 * has a position there with a non-zero order, and leaves it when that order
 * goes to zero; a pinned chat is one whose position says `is_pinned`. The
 * backend feeds every position it hears about into [apply] and asks this
 * what to draw.
 *
 * The rules it keeps, each of which the TDLib backend once got wrong:
 *
 * - **Only listed chats are shown.** TDLib also announces chats the account
 *   is not in — search results, a group just left, a channel previewed from
 *   a link — and those have no position anywhere.
 * - **Pinned comes from the position.** Guessing it from the size of the
 *   order marked every chat pinned: an ordinary order is a date shifted into
 *   the top bits, far above any fixed threshold.
 * - **Order is per list.** A chat in the archive is sorted by its archive
 *   position, not by a main-list order it no longer has.
 *
 * Synchronised, because positions arrive on TDLib's thread and are read from
 * wherever the chat list is built.
 */
class ChatPositions {

    /** One of TDLib's chat lists. */
    sealed interface ChatList {
        data object Main : ChatList
        data object Archive : ChatList
        data class Folder(val id: Int) : ChatList
    }

    private data class Position(val order: Long, val isPinned: Boolean)

    private val main = HashMap<Long, Position>()
    private val archive = HashMap<Long, Position>()
    private val folders = HashMap<Long, MutableSet<Int>>()

    @Synchronized
    fun apply(chatId: Long, list: ChatList, order: Long, isPinned: Boolean) {
        when (list) {
            ChatList.Main -> put(main, chatId, order, isPinned)
            ChatList.Archive -> put(archive, chatId, order, isPinned)
            is ChatList.Folder -> {
                val ids = folders.getOrPut(chatId) { HashSet() }
                if (order == 0L) ids.remove(list.id) else ids.add(list.id)
                if (ids.isEmpty()) folders.remove(chatId)
            }
        }
    }

    private fun put(into: HashMap<Long, Position>, chatId: Long, order: Long, isPinned: Boolean) {
        if (order == 0L) into.remove(chatId) else into[chatId] = Position(order, isPinned)
    }

    /** In the main list or the archive — somewhere this client draws it. */
    @Synchronized
    fun isListed(chatId: Long): Boolean = chatId in main || chatId in archive

    /** In the archive and not in the main list; TDLib never has it in both. */
    @Synchronized
    fun isArchived(chatId: Long): Boolean = chatId !in main && chatId in archive

    /** Pinned in whichever of the two lists it is drawn in. */
    @Synchronized
    fun isPinned(chatId: Long): Boolean =
        (main[chatId] ?: archive[chatId])?.isPinned == true

    @Synchronized
    fun folderIds(chatId: Long): Set<Int> = folders[chatId]?.toSet().orEmpty()

    /**
     * [chatIds], keeping only the listed ones, in TDLib's order: by order
     * descending, and by id descending where two orders tie — which is what
     * TDLib's documentation says a client must do.
     */
    @Synchronized
    fun listed(chatIds: Collection<Long>): List<Long> = chatIds
        .mapNotNull { id -> (main[id] ?: archive[id])?.let { id to it.order } }
        .sortedWith(compareByDescending<Pair<Long, Long>> { it.second }.thenByDescending { it.first })
        .map { it.first }

    /** Forgets everything, for a sign-out: the next account starts empty. */
    @Synchronized
    fun clear() {
        main.clear()
        archive.clear()
        folders.clear()
    }
}
