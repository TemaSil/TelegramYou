package com.telegramyou.app.telegram.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoldersTest {

    private fun chat(
        id: Long,
        group: Boolean = false,
        channel: Boolean = false,
        bot: Boolean = false,
        muted: Boolean = false,
        unread: Int = 0,
        archived: Boolean = false
    ) = ChatPreview(
        id = id, title = "Chat $id", lastMessage = "", timestampLabel = "",
        isGroup = group, isChannel = channel, isBot = bot, isMuted = muted,
        unreadCount = unread, isArchived = archived
    )

    @Test
    fun `a folder needs a name and something to let in`() {
        assertFalse(FolderRules().canSave)
        assertFalse(FolderRules(name = "Work").canSave)
        assertFalse(FolderRules(name = "  ", includeGroups = true).canSave)
        assertTrue(FolderRules(name = "Work", includeGroups = true).canSave)
        assertTrue(FolderRules(name = "Work", includedChatIds = listOf(1)).canSave)
        assertFalse(FolderRules(name = "A name too long", includeGroups = true).canSave)
    }

    @Test
    fun `adding a chat by hand takes it off the excluded list`() {
        val rules = FolderRules(excludedChatIds = listOf(5)).toggleIncluded(5)
        assertEquals(listOf(5L), rules.includedChatIds)
        assertTrue(rules.excludedChatIds.isEmpty())
        val removed = rules.toggleIncluded(5)
        assertTrue(removed.includedChatIds.isEmpty())
    }

    @Test
    fun `types let chats in and exclusions take them out`() {
        val groups = FolderRules(name = "Groups", includeGroups = true, excludeMuted = true)
        assertTrue(groups.contains(chat(1, group = true), isContact = false))
        assertFalse(groups.contains(chat(2, group = true, muted = true), isContact = false))
        assertFalse(groups.contains(chat(3, channel = true), isContact = false))
    }

    @Test
    fun `contacts and non-contacts are people only`() {
        val contacts = FolderRules(includeContacts = true)
        assertTrue(contacts.contains(chat(1), isContact = true))
        assertFalse(contacts.contains(chat(1), isContact = false))
        assertFalse(contacts.contains(chat(2, bot = true), isContact = true))
        val others = FolderRules(includeNonContacts = true)
        assertTrue(others.contains(chat(1), isContact = false))
        assertFalse(others.contains(chat(3, group = true), isContact = false))
    }

    @Test
    fun `a chat added by hand is in whatever the exclusions say, unless excluded by id`() {
        val rules = FolderRules(includedChatIds = listOf(7), excludeRead = true)
        assertTrue(rules.contains(chat(7, unread = 0), isContact = false))
        assertFalse(rules.copy(excludedChatIds = listOf(7)).contains(chat(7), isContact = false))
    }
}
