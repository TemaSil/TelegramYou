package com.telegramyou.app.notifications

/**
 * A chat as Android's conversations know it (2.1): one long-lived sharing
 * shortcut per chat, named by this id. The same id ties the chat's
 * notifications, its Direct Share target and its bubble to it, so the
 * system sees them as one conversation.
 */
fun conversationId(chatId: Long): String = "$CONVERSATION_PREFIX$chatId"

/** The chat a conversation id names, or null for any other shortcut. */
fun chatIdOfConversation(id: String?): Long? =
    id?.takeIf { it.startsWith(CONVERSATION_PREFIX) }
        ?.removePrefix(CONVERSATION_PREFIX)
        ?.toLongOrNull()

/**
 * Up to two capitals from a title's first words — what an avatar without a
 * photo shows, in the app and on a shortcut alike — or "?" for none.
 */
fun avatarInitials(title: String): String =
    title.split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "?" }

private const val CONVERSATION_PREFIX = "chat_"
