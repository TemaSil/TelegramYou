package com.telegramyou.app.notifications

import com.telegramyou.app.i18n.tr

/**
 * The three kinds of chat Telegram keeps notification defaults for (2.0):
 * every chat of a kind follows these until it is given its own.
 */
enum class NotificationScope(private val en: Pair<String, String>, private val ru: Pair<String, String>) {
    PrivateChats("Private chats" to "Messages from one person", "Личные чаты" to "Сообщения от одного человека"),
    Groups("Groups" to "Messages in groups", "Группы" to "Сообщения в группах"),
    Channels("Channels" to "New posts in channels", "Каналы" to "Новые публикации в каналах");

    /** Its name and what it covers, in the language in use. */
    val label: String get() = com.telegramyou.app.i18n.tr(en.first, ru.first)
    val summary: String get() = com.telegramyou.app.i18n.tr(en.second, ru.second)
}

/**
 * The kind a chat is, for its notifications (2.1): a channel, a group, or
 * one person. A chat the list does not hold is one being looked at without
 * having joined, which is a channel as a rule.
 */
fun notificationScopeOf(isGroup: Boolean, isChannel: Boolean, inList: Boolean = true): NotificationScope = when {
    !inList || isChannel -> NotificationScope.Channels
    isGroup -> NotificationScope.Groups
    else -> NotificationScope.PrivateChats
}

/** One kind of chat's defaults, as Settings → Notifications sets them. */
data class ScopeNotifications(
    val enabled: Boolean = true,
    val showPreview: Boolean = true,
    val sound: Boolean = true
) {
    /** What the main switch says under it: the kind of chat, or that it is silenced. */
    fun summaryFor(scope: NotificationScope): String =
        if (enabled) scope.summary else tr("Off for ${scope.label.lowercase()}", "Выкл. для: ${scope.label.lowercase()}")
}
