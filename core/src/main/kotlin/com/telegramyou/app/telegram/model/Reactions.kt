package com.telegramyou.app.telegram.model

/**
 * What a reaction row looks like the instant after a tap, before the server
 * has agreed.
 *
 * A reaction is a round trip to Telegram, and a chip that waits for it feels
 * broken — the tap is the fastest thing a user does and the slowest thing to
 * confirm. So the list is recomputed locally and replaced when the update
 * arrives. That makes the arithmetic worth testing rather than trusting: an
 * optimistic update that is wrong shows a lie until the server corrects it.
 *
 * One reaction per message, which is Telegram's rule for an account without
 * Premium; Premium raises the limit, and the server enforces whichever applies
 * regardless of what is done here. Choosing a second emoji therefore moves the
 * choice rather than adding to it.
 */
fun toggleReaction(
    reactions: List<MessageReaction>,
    emoji: String
): List<MessageReaction> {
    val alreadyChosen = reactions.any { it.emoji == emoji && it.isChosen }

    // Withdraw whatever was chosen before — including this same emoji, which is
    // what makes a second tap an undo.
    val withdrawn = reactions.map { reaction ->
        if (reaction.isChosen) {
            reaction.copy(count = reaction.count - 1, isChosen = false)
        } else {
            reaction
        }
    }

    val updated = if (alreadyChosen) {
        withdrawn
    } else if (withdrawn.any { it.emoji == emoji }) {
        withdrawn.map { reaction ->
            if (reaction.emoji == emoji) {
                reaction.copy(count = reaction.count + 1, isChosen = true)
            } else {
                reaction
            }
        }
    } else {
        // A new emoji goes on the end rather than in sorted position. The
        // server orders by popularity and will resend the row that way; doing
        // it here as well would slide the chips around under the finger that
        // just tapped one.
        withdrawn + MessageReaction(emoji, count = 1, isChosen = true)
    }

    // A chip with nobody behind it is not a chip. This is also why the count is
    // never allowed to go negative: it cannot reach zero and stay.
    return updated.filter { it.count > 0 }
}

/**
 * A custom-emoji reaction as the rest of this client carries it: a key in
 * the same string the emoji reactions use, so the row, the toggle above and
 * the repository take both alike. Telegram names one by its custom emoji's
 * id; drawing it means fetching that emoji's sticker (see the chips).
 */
fun customReactionKey(customEmojiId: Long): String = "$CUSTOM_REACTION_PREFIX$customEmojiId"

/** The custom emoji's id behind [key], or null for a plain emoji reaction. */
fun customEmojiIdOf(key: String): Long? =
    if (key.startsWith(CUSTOM_REACTION_PREFIX)) key.removePrefix(CUSTOM_REACTION_PREFIX).toLongOrNull() else null

private const val CUSTOM_REACTION_PREFIX = "custom:"
