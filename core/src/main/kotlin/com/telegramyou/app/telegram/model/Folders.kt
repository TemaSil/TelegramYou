package com.telegramyou.app.telegram.model

/**
 * A chat folder as it is edited: its name and the rules that decide what is
 * in it — TDLib's chatFolder, less the parts this client does not offer
 * (colour tags, shareable folders, invite links).
 *
 * A folder is a filter, not a box. Chats come in by type — every group,
 * every contact — or one by one, and the exclusions are taken from what
 * the types let in: a chat added by hand is in whatever they say, unless it
 * is excluded by name.
 * That is Telegram's rule, and [contains] states it so the demo and the
 * tests hold the same one as the server.
 */
data class FolderRules(
    val name: String = "",
    /** TDLib's icon name, kept as it came; blank for the default. */
    val iconName: String = "",
    val includedChatIds: List<Long> = emptyList(),
    val excludedChatIds: List<Long> = emptyList(),
    val pinnedChatIds: List<Long> = emptyList(),
    val includeContacts: Boolean = false,
    val includeNonContacts: Boolean = false,
    val includeGroups: Boolean = false,
    val includeChannels: Boolean = false,
    val includeBots: Boolean = false,
    val excludeMuted: Boolean = false,
    val excludeRead: Boolean = false,
    val excludeArchived: Boolean = false
) {
    /** Lets in anything at all — a folder that does not is refused by Telegram. */
    val includesSomething: Boolean
        get() = includedChatIds.isNotEmpty() || includeContacts || includeNonContacts ||
            includeGroups || includeChannels || includeBots

    /** Named, within Telegram's length, and letting something in. */
    val canSave: Boolean
        get() = name.isNotBlank() && name.trim().length <= MAX_NAME && includesSomething

    /** [chatId] added by hand, or taken out again; never both included and excluded. */
    fun toggleIncluded(chatId: Long): FolderRules =
        if (chatId in includedChatIds) {
            copy(includedChatIds = includedChatIds - chatId, pinnedChatIds = pinnedChatIds - chatId)
        } else {
            copy(includedChatIds = includedChatIds + chatId, excludedChatIds = excludedChatIds - chatId)
        }

    /**
     * Whether [chat] is in the folder. [isContact] is asked separately:
     * a chat preview does not say, and only a private chat with a person
     * can be a contact at all.
     */
    fun contains(chat: ChatPreview, isContact: Boolean): Boolean {
        if (chat.id in excludedChatIds) return false
        if (chat.id in includedChatIds || chat.id in pinnedChatIds) return true
        val person = !chat.isGroup && !chat.isChannel && !chat.isBot && !chat.isSavedMessages
        val byType = (includeGroups && chat.isGroup) ||
            (includeChannels && chat.isChannel) ||
            (includeBots && chat.isBot) ||
            (includeContacts && person && isContact) ||
            (includeNonContacts && person && !isContact)
        if (!byType) return false
        if (excludeMuted && chat.isMuted) return false
        if (excludeRead && chat.unreadCount == 0) return false
        if (excludeArchived && chat.isArchived) return false
        return true
    }

    companion object {
        /** Telegram's limit on a folder's name. */
        const val MAX_NAME = 12
    }
}
