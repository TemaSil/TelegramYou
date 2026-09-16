package com.telegramyou.app.ui.chat

import com.telegramyou.app.telegram.model.ChatMessage

/**
 * Which messages are selected, and what may be done to them.
 *
 * Held as ids rather than messages because the messages themselves are
 * replaced on every reload — a selection holding stale copies would act on
 * text that has since been edited, or on a message that is gone.
 */
@JvmInline
value class MessageSelection(val ids: Set<Long> = emptySet()) {

    val isActive: Boolean get() = ids.isNotEmpty()

    val count: Int get() = ids.size

    operator fun contains(id: Long): Boolean = id in ids

    /**
     * Adds or removes one message.
     *
     * Deselecting the last one ends the selection, which is what closes the
     * toolbar: there is no separate "cancel" to remember.
     */
    fun toggle(id: Long): MessageSelection =
        MessageSelection(if (id in ids) ids - id else ids + id)

    fun cleared(): MessageSelection = MessageSelection()

    /**
     * Drops ids that are no longer on screen.
     *
     * A reload can remove a message someone else deleted while it was
     * selected. Keeping its id would leave the toolbar counting something
     * that cannot be acted on, and the count is the only thing telling the
     * user what they are about to affect.
     */
    fun retaining(present: Collection<ChatMessage>): MessageSelection {
        val alive = present.mapTo(mutableSetOf()) { it.id }
        return MessageSelection(ids.intersect(alive))
    }
}

/**
 * What the toolbar may offer for a given selection.
 *
 * Every flag is an "all", not an "any". Offering Delete for a set where one
 * message cannot be deleted means a button that half works, and a failure the
 * UI could have predicted — the same reason a single message's menu is built
 * from its own can_be_* flags rather than from whether it is ours.
 */
data class SelectionActions(
    val canCopy: Boolean = false,
    val canDeleteForSelf: Boolean = false,
    val canDeleteForEveryone: Boolean = false
)

fun selectionActions(selected: List<ChatMessage>): SelectionActions {
    if (selected.isEmpty()) return SelectionActions()
    return SelectionActions(
        // A selection of photos with no captions has nothing to put on the
        // clipboard, and an empty copy is worse than no button.
        canCopy = selected.any { it.text.isNotBlank() },
        canDeleteForSelf = selected.all { it.canBeDeletedForSelf },
        canDeleteForEveryone = selected.all { it.canBeDeletedForEveryone }
    )
}

/**
 * The text several messages copy as.
 *
 * Newline-separated, in the order they appear, each prefixed with its sender
 * where there is one — copying a stretch of a group conversation and losing
 * who said what makes the result useless for the thing it is usually for,
 * which is quoting it somewhere else.
 */
fun copyText(selected: List<ChatMessage>): String = selected
    .filter { it.text.isNotBlank() }
    .joinToString("\n") { message ->
        val sender = message.senderName
        if (sender.isNullOrBlank()) message.text else "$sender: ${message.text}"
    }
