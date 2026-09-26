package com.telegramyou.app.ui.chat

import com.telegramyou.app.telegram.model.ChatMessage
import com.telegramyou.app.telegram.model.MessageUpdate

/**
 * This window of messages with [update] applied, or the same list when the
 * update is about a message that is not in it.
 *
 * [appendNew] says whether an [MessageUpdate.Added] belongs at the end of this
 * list. The conversation keeps its newest page and the pages scrolled back
 * through as two lists; a new message goes on the end of the newest one and
 * has no business in the history above it.
 *
 * Every case is idempotent. TDLib repeats updates after a reconnect, and the
 * same change can arrive both from the screen's own optimistic edit and from
 * the server confirming it — applying it twice must look like applying it
 * once.
 */
fun List<ChatMessage>.applying(
    update: MessageUpdate,
    appendNew: Boolean = true
): List<ChatMessage> = when (update) {
    is MessageUpdate.Added ->
        if (!appendNew || any { it.id == update.message.id }) this else this + update.message

    is MessageUpdate.Replaced -> replacing(update.oldId, update.message)

    // The same swap: a refused message changes id as it fails, just as a
    // delivered one does.
    is MessageUpdate.SendFailed -> replacing(update.oldId, update.message)

    is MessageUpdate.Deleted ->
        if (none { it.id in update.messageIds }) this
        else filterNot { it.id in update.messageIds }

    is MessageUpdate.Edited -> mapMessage(update.messageId) {
        if (it.text == update.text && it.isEdited) it
        else it.copy(text = update.text, isEdited = true)
    }

    is MessageUpdate.ReactionsChanged -> mapMessage(update.messageId) {
        it.copy(reactions = update.reactions)
    }

    is MessageUpdate.PollChanged -> mapMessage(update.messageId) {
        if (it.poll == update.poll) it else it.copy(poll = update.poll)
    }

    is MessageUpdate.ButtonsChanged -> mapMessage(update.messageId) {
        if (it.inlineKeyboard == update.buttons) it else it.copy(inlineKeyboard = update.buttons)
    }

    is MessageUpdate.ReadUpTo -> map {
        if (it.isOutgoing && !it.isRead && it.id <= update.lastReadId) it.copy(isRead = true)
        else it
    }
}

private fun List<ChatMessage>.replacing(oldId: Long, message: ChatMessage): List<ChatMessage> {
    val index = indexOfFirst { it.id == oldId }
    return when {
        index == -1 -> this
        // The real id already arrived by another route — a reload, or a
        // repeated update — so the temporary copy is the one to drop.
        any { it.id == message.id } -> filterIndexed { i, _ -> i != index }
        else -> toMutableList().also { it[index] = message }
    }
}

private inline fun List<ChatMessage>.mapMessage(
    id: Long,
    transform: (ChatMessage) -> ChatMessage
): List<ChatMessage> {
    val index = indexOfFirst { it.id == id }
    if (index == -1) return this
    return toMutableList().also { it[index] = transform(it[index]) }
}
