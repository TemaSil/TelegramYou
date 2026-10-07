package com.telegramyou.app.ui.chat

/**
 * What the button under a channel post says (2.0): an invitation while
 * there are none, the count once there are.
 */
fun commentsLabel(count: Int): String = when {
    count <= 0 -> "Leave a comment"
    count == 1 -> "1 comment"
    else -> "$count comments"
}
