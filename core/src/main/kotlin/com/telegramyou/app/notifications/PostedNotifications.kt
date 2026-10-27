package com.telegramyou.app.notifications

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

    /** By account and chat (2.6): the same chat can be in two accounts. */
    private val byChat = mutableMapOf<Pair<Int, Long>, MutableList<NotifiableMessage>>()

    /**
     * Adds a message to a chat's entry and answers with what to draw — or
     * null when that message is already there, which means there is nothing
     * new to post.
     *
     * Trimmed rather than unbounded: the shade shows a handful of lines, and
     * a chat left unread overnight would otherwise grow this forever.
     */
    @Synchronized
    fun add(message: NotifiableMessage, maxLines: Int): List<NotifiableMessage>? {
        val lines = byChat.getOrPut(message.accountId to message.chatId) { mutableListOf() }
        // Already there: the same message offered twice — a repeated update
        // from the server, or two collectors racing, which is what a service
        // started twice used to have. Checking here, under the same lock as
        // the add, is what makes "have we shown this" and "show it" one step
        // rather than two a second caller can slip between.
        if (lines.any { it.messageId == message.messageId }) return null
        lines.add(message)
        while (lines.size > maxLines) lines.removeAt(0)
        return lines.toList()
    }

    /** Every message id one account has on show, so none is announced twice. */
    @Synchronized
    fun shownMessageIds(accountId: Int = 0): Set<Long> =
        byChat.filterKeys { it.first == accountId }.values.flatten().map { it.messageId }.toSet()

    /**
     * Forgets a chat, because it has been read.
     *
     * Called when its notification is answered: the next message from that
     * chat should start a fresh conversation in the shade rather than
     * re-listing what was already replied to.
     */
    /**
     * The id a chat's notification is posted under.
     *
     * Chat ids are 64-bit and notification ids 32, and `toInt()` keeps only
     * the low half — which for supergroups, whose ids share their high digits,
     * is where the difference is least likely to be. Folding both halves in,
     * the way `Long.hashCode` does, keeps all of it in play.
     */
    fun notificationId(chatId: Long, accountId: Int = 0): Int =
        // Another account's chat moved off by a large odd multiple, so the
        // same chat in two accounts is two notifications; the first account
        // keeps the ids it always had.
        (chatId xor (chatId ushr 32)).toInt() + accountId * ACCOUNT_STRIDE

    @Synchronized
    fun clear(chatId: Long, accountId: Int = 0) {
        byChat.remove(accountId to chatId)
    }

    /**
     * Forgets one account's chats, and answers with the ones that were on
     * show, so their notifications can be taken down: the account has left
     * the phone. Switching away from one no longer does this (2.6) — every
     * signed-in account notifies.
     */
    @Synchronized
    fun clearAccount(accountId: Int): Set<Long> {
        val shown = byChat.keys.filter { it.first == accountId }
        shown.forEach(byChat::remove)
        return shown.map { it.second }.toSet()
    }

    private const val ACCOUNT_STRIDE = 0x3B9ACA07
}
