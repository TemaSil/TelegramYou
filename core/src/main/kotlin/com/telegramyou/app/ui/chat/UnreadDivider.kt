package com.telegramyou.app.ui.chat

import com.telegramyou.app.telegram.model.ChatMessage

/**
 * Which message the "unread messages" line sits above.
 *
 * Counted back from the end rather than matched against a last-read id,
 * because the count is what both backends actually carry. The answer is an
 * index into the list as given, so the caller draws the line before that item
 * and nothing has to be inserted into the list itself.
 *
 * Null when there is no line to draw: nothing unread, or the whole loaded
 * window is unread, in which case a line above the first message says only
 * that the conversation has a beginning.
 *
 * Our own messages do not count. Telegram's unread count never includes them,
 * and a window ending in a burst of our own replies would otherwise push the
 * line up past messages that were read long ago.
 */
fun unreadDividerIndex(messages: List<ChatMessage>, unreadCount: Int): Int? {
    if (unreadCount <= 0 || messages.isEmpty()) return null

    var remaining = unreadCount
    var index = messages.size
    while (index > 0 && remaining > 0) {
        index--
        if (!messages[index].isOutgoing) remaining--
    }
    // remaining > 0 means the window does not reach back far enough to hold
    // the whole unread run, so where it starts is not known yet.
    if (remaining > 0) return null
    return index.takeIf { it > 0 }
}
