package com.telegramyou.app.notifications

/**
 * Whether an arriving message should raise a notification, and what the
 * shade should look like afterwards.
 *
 * This is in `:core` on purpose. Deciding not to notify is where a messenger
 * actually goes wrong — it buzzes for a chat you muted, buzzes for the chat
 * you are reading, buzzes twice for one message after a reconnect — and none
 * of those need Android to reproduce. They need a test. The Android side
 * keeps only what genuinely touches the platform: channels, `MessagingStyle`,
 * the PendingIntent.
 */

/**
 * The parts of an incoming message the decision depends on. Deliberately not
 * `ChatMessage`: that carries a rendered timestamp label and drafts and
 * waveforms, none of which say anything about whether to interrupt someone.
 */
data class NotifiableMessage(
    val chatId: Long,
    val messageId: Long,
    val chatTitle: String,
    val senderName: String?,
    val text: String,
    val timestampMillis: Long,
    val isOutgoing: Boolean,
    val isChatMuted: Boolean
)

/** What the app knows about itself at the moment the message lands. */
data class NotificationContext(
    /** The chat on screen right now, or null if none is. */
    val openChatId: Long? = null,
    /** False once the activity has stopped. */
    val isAppInForeground: Boolean = false,
    /** Message ids already shown, so a reconnect does not notify twice. */
    val alreadyNotified: Set<Long> = emptySet()
)

/** Why a message was not notified. Named, because "false" debugs badly. */
enum class SuppressionReason {
    OwnMessage,
    ChatMuted,
    ChatIsOpen,
    AlreadyNotified
}

sealed interface NotificationDecision {
    data class Notify(val message: NotifiableMessage) : NotificationDecision
    data class Suppress(val reason: SuppressionReason) : NotificationDecision
}

/**
 * The order of these checks is the behaviour, not an implementation detail.
 *
 * Own messages first: a message sent from this device arrives back down the
 * update stream like any other, and notifying yourself for it is the most
 * visible bug of the lot. Then mute, which is a standing instruction from
 * the person and outranks everything below it. Then the open chat — reading
 * a chat is not the same as having muted it, so this is separate. Duplicate
 * suppression last, so that a message which would have been suppressed
 * anyway does not consume a slot in [NotificationContext.alreadyNotified].
 */
fun decideNotification(
    message: NotifiableMessage,
    context: NotificationContext
): NotificationDecision = when {
    message.isOutgoing -> NotificationDecision.Suppress(SuppressionReason.OwnMessage)
    message.isChatMuted -> NotificationDecision.Suppress(SuppressionReason.ChatMuted)
    // Only when the app is actually on screen. A chat left open and then
    // backgrounded is not being read, and this is the case that makes the
    // difference between a messenger that works closed and one that does not.
    context.isAppInForeground && context.openChatId == message.chatId ->
        NotificationDecision.Suppress(SuppressionReason.ChatIsOpen)
    message.messageId in context.alreadyNotified ->
        NotificationDecision.Suppress(SuppressionReason.AlreadyNotified)
    else -> NotificationDecision.Notify(message)
}

/**
 * One notification per chat, holding that chat's recent messages — the shape
 * Android's `MessagingStyle` expects, and the shape a person expects: a chat
 * that sends five lines is one conversation, not five interruptions.
 */
data class ChatNotification(
    val chatId: Long,
    val title: String,
    val messages: List<NotifiableMessage>
) {
    val latest: NotifiableMessage get() = messages.last()
    val count: Int get() = messages.size
}

/**
 * Folds the messages that survived [decideNotification] into one entry per
 * chat, oldest first within each, and chats ordered by their most recent
 * message. Ordering matters: the shade shows them in the order they are
 * posted, and a conversation that just spoke belongs at the bottom.
 */
fun groupForShade(messages: List<NotifiableMessage>): List<ChatNotification> =
    messages
        .groupBy { it.chatId }
        .map { (chatId, forChat) ->
            val sorted = forChat.sortedBy { it.timestampMillis }
            ChatNotification(
                chatId = chatId,
                title = sorted.last().chatTitle,
                messages = sorted
            )
        }
        .sortedBy { it.latest.timestampMillis }

/**
 * What a single-line summary should say when `MessagingStyle` is not
 * available — a group names its sender, a one-to-one chat does not, because
 * there the sender is the chat.
 */
fun summaryLine(message: NotifiableMessage): String {
    val sender = message.senderName?.takeIf { it.isNotBlank() }
    return if (sender == null) message.text else "$sender: ${message.text}"
}
