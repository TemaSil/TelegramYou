package com.telegramyou.app.notifications

/**
 * Whether a new message in a chat is this account's business at all (2.1.3):
 * from one person, or in a group or channel the account is in.
 *
 * TDLib announces messages from chats the account only looked at, too — a
 * channel's discussion group stays live after its comments were opened once
 * — and each of those reached the shade as if it had been joined.
 *
 * [chatType] is TDLib's `@type` of the chat's type; [memberStatus] the
 * `@type` of the account's status in a supergroup or channel, when TDLib has
 * said, with [isMember] for the one status that carries it; [listed] whether
 * the chat is in the main list or the archive, which is what is left to go
 * on when the status has not arrived.
 */
fun isAccountsChat(chatType: String?, memberStatus: String?, isMember: Boolean, listed: Boolean): Boolean =
    when (chatType) {
        null -> listed
        "chatTypeSupergroup" -> when (memberStatus) {
            null -> listed
            "chatMemberStatusLeft", "chatMemberStatusBanned" -> false
            "chatMemberStatusRestricted" -> isMember
            else -> true
        }
        // A person's message is always theirs to send, the first one from a
        // stranger included — which can arrive before its chat is listed.
        // A basic group a person has left stops sending.
        else -> true
    }
