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
