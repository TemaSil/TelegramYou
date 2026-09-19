package com.telegramyou.app.ui.home

/**
 * Splits chats into the groups the list draws, in the order it draws them.
 *
 * Pinned chats are a group of their own because they are a different kind of
 * thing — chosen rather than recent — and a container around each says so
 * without a heading. Telegram itself separates them; this only makes the
 * separation visible in the way Material asks for.
 *
 * How a group is then *drawn* is not decided here. An earlier version of this
 * file carried a `RowPosition` enum that turned an index into a set of corner
 * radii, which was a hand-rolled copy of something Material already ships:
 * `ListItemDefaults.segmentedShapes(index, count)` with `SegmentedListItem`.
 * The index and the size are all the UI needs from this module.
 *
 * Generic over the chat type, which keeps this module free of Android and
 * lets the test use something smaller than a ChatPreview.
 */
fun <T> groupChats(chats: List<T>, isPinned: (T) -> Boolean): List<List<T>> {
    val pinned = chats.filter(isPinned)
    val rest = chats.filterNot(isPinned)
    // Empty groups are dropped rather than drawn: a container with nothing in
    // it is a rounded rectangle with no purpose.
    return listOf(pinned, rest).filter { it.isNotEmpty() }
}

/**
 * What the archive row should say, or null when there is no archive row.
 *
 * Null rather than a blank string, so the caller cannot draw an entry for an
 * empty archive by forgetting to check — which is the mistake this exists to
 * make impossible. Telegram hides the row entirely when nothing is in there,
 * and so does this.
 *
 * The count is of chats with something unread, not of chats: the archive is
 * where things go to stop asking for attention, so the number that matters is
 * how many are asking anyway.
 */
fun <T> archiveSummary(
    chats: List<T>,
    isArchived: (T) -> Boolean,
    unreadCount: (T) -> Int
): String? {
    val archived = chats.filter(isArchived)
    if (archived.isEmpty()) return null
    val unread = archived.count { unreadCount(it) > 0 }
    return when {
        unread == 0 -> "${archived.size} chat${plural(archived.size)}"
        else -> "${archived.size} chat${plural(archived.size)}, $unread unread"
    }
}

private fun plural(count: Int) = if (count == 1) "" else "s"
