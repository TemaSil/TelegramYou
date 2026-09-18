package com.telegramyou.app.telegram

import com.telegramyou.app.notifications.NotifiableMessage

/**
 * What each chat's notification is currently showing.
 *
 * Held outside the service because two components need it and neither can
 * reach the other: the service appends as messages arrive, and the reply
 * receiver clears a chat once it has been answered. A receiver cannot call
 * into a running service, and binding one to the other for two map
 * operations would be machinery around a mutable map.
 *
 * In memory deliberately, like the notifications themselves. A notification
 * does not outlive its process, so persisting this would restore lines for
 * entries Android has already dropped.
 *
 * Synchronised because the service writes from a coroutine and the receiver
 * from the main thread, and a `MutableMap` torn between the two is a crash
 * nobody would reproduce twice.
 */
object PostedNotifications {

    private val byChat = mutableMapOf<Long, MutableList<NotifiableMessage>>()

    /**
     * Adds a message to a chat's entry and answers with what to draw.
     *
     * Trimmed rather than unbounded: the shade shows a handful of lines, and
     * a chat left unread overnight would otherwise grow this forever.
     */
    @Synchronized
    fun add(message: NotifiableMessage, maxLines: Int): List<NotifiableMessage> {
        val lines = byChat.getOrPut(message.chatId) { mutableListOf() }
        lines.add(message)
        while (lines.size > maxLines) lines.removeAt(0)
        return lines.toList()
    }

    /** Every message id currently on show, so none is announced twice. */
    @Synchronized
    fun shownMessageIds(): Set<Long> =
        byChat.values.flatten().map { it.messageId }.toSet()

    /**
     * Forgets a chat, because it has been read.
     *
     * Called when its notification is answered: the next message from that
     * chat should start a fresh conversation in the shade rather than
     * re-listing what was already replied to.
     */
    @Synchronized
    fun clear(chatId: Long) {
        byChat.remove(chatId)
    }
}
