package com.telegramyou.app.ui.chat

import com.telegramyou.app.i18n.tr
import com.telegramyou.app.i18n.counted

/**
 * What the button under a channel post says (2.0): an invitation while
 * there are none, the count once there are.
 */
fun commentsLabel(count: Int): String = when {
    count <= 0 -> tr("Leave a comment", "Оставить комментарий")
    else -> counted(count, "comment", "comments", "комментарий", "комментария", "комментариев")
}
