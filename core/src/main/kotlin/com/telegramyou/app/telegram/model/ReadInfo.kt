package com.telegramyou.app.telegram.model

/**
 * Who has read one of the account's own messages (2.0), as the official
 * client shows it at the top of the message's menu: in a private chat the
 * time it was read, in a small group who has seen it and when. Telegram
 * keeps this for a week and only where both sides' privacy allows it.
 */
sealed interface ReadInfo {
    /** Read, at [date] epoch seconds. */
    data class ReadAt(val date: Long) : ReadInfo

    /** Not read yet. */
    data object Unread : ReadInfo

    /** Seen, in a group, by these people, newest first. */
    data class SeenBy(val viewers: List<Viewer>) : ReadInfo

    /** Telegram will not say: too old, or someone's privacy settings. */
    data object Hidden : ReadInfo
}

data class Viewer(val userId: Long, val name: String, val date: Long, val photoPath: String? = null)

/**
 * The line the menu shows for [info], with [time] turning a date into the
 * words for it ("14:03", "yesterday at 9:12"); null where there is nothing
 * worth a line.
 */
fun readLine(info: ReadInfo?, time: (Long) -> String): String? = when (info) {
    is ReadInfo.ReadAt -> "Read ${time(info.date)}"
    ReadInfo.Unread -> "Not read yet"
    is ReadInfo.SeenBy -> when (info.viewers.size) {
        0 -> "Not seen yet"
        1 -> "Seen by ${info.viewers.single().name}"
        else -> "Seen by ${info.viewers.size}"
    }
    ReadInfo.Hidden, null -> null
}
