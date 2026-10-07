package com.telegramyou.app.notifications

/**
 * The three kinds of chat Telegram keeps notification defaults for (2.0):
 * every chat of a kind follows these until it is given its own.
 */
enum class NotificationScope(val label: String, val summary: String) {
    PrivateChats("Private chats", "Messages from one person"),
    Groups("Groups", "Messages in groups"),
    Channels("Channels", "New posts in channels")
}

/** One kind of chat's defaults, as Settings → Notifications sets them. */
data class ScopeNotifications(
    val enabled: Boolean = true,
    val showPreview: Boolean = true,
    val sound: Boolean = true
) {
    /** What the main switch says under it: the kind of chat, or that it is silenced. */
    fun summaryFor(scope: NotificationScope): String =
        if (enabled) scope.summary else "Off for ${scope.label.lowercase()}"
}
