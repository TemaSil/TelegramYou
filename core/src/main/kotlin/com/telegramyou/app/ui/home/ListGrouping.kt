package com.telegramyou.app.ui.home

/**
 * Where a row sits inside its group, which decides how it is cornered.
 *
 * Material's fourth expressive principle is to contain content rather than
 * let it float: a run of similar rows reads as one informative grouping when
 * it shares a container, and as a pile of unrelated cards when each row
 * carries its own. The corners are what say which of the two it is — rounded
 * at the ends of a run, square in the middle.
 */
enum class RowPosition {
    /** The only row in its group: rounded on every corner. */
    Single,

    /** Rounded at the top, square at the bottom. */
    First,

    /** Square at both ends. */
    Middle,

    /** Square at the top, rounded at the bottom. */
    Last;

    val roundedTop: Boolean get() = this == Single || this == First
    val roundedBottom: Boolean get() = this == Single || this == Last
}

/** Where [index] falls in a group of [size] rows. */
fun rowPosition(index: Int, size: Int): RowPosition {
    require(index in 0 until size) { "row $index is outside a group of $size" }
    return when {
        size == 1 -> RowPosition.Single
        index == 0 -> RowPosition.First
        index == size - 1 -> RowPosition.Last
        else -> RowPosition.Middle
    }
}

/**
 * Splits chats into the groups the list draws, in the order it draws them.
 *
 * Pinned chats are a group of their own because they are a different kind of
 * thing — chosen rather than recent — and a container around each says so
 * without a heading. Telegram itself separates them; this only makes the
 * separation visible in the way Material asks for.
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
