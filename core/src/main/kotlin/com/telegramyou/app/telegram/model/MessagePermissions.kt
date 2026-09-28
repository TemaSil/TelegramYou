package com.telegramyou.app.telegram.model

/**
 * What may be done to one message right now.
 *
 * TDLib used to put these on the message itself; the version this app is
 * built with (d1085f9) moved them to `messageProperties`, a separate call —
 * and the fields read off the message came back false for every message, so
 * the live client offered neither Edit nor Delete on the account's own
 * messages. They are asked for when a message's menu opens or it is
 * selected, which is when anybody needs them.
 */
data class MessagePermissions(
    val canEdit: Boolean,
    val canDeleteForSelf: Boolean,
    val canDeleteForEveryone: Boolean,
    val canForward: Boolean
)

/** [message] with what [permissions] says may be done to it. */
fun ChatMessage.withPermissions(permissions: MessagePermissions): ChatMessage = copy(
    canBeEdited = permissions.canEdit,
    canBeDeletedForSelf = permissions.canDeleteForSelf,
    canBeDeletedForEveryone = permissions.canDeleteForEveryone
)
