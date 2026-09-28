package com.telegramyou.app.telegram.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeopleTest {

    private fun chat(
        isGroup: Boolean = false,
        isChannel: Boolean = false,
        isSaved: Boolean = false,
        forEveryone: Boolean = false
    ) = ChatPreview(
        id = 1, title = "Lina Park", lastMessage = "", timestampLabel = "",
        isGroup = isGroup, isChannel = isChannel, isSavedMessages = isSaved,
        canDeleteForEveryone = forEveryone
    )

    @Test
    fun `a private chat is cleared or deleted, for both sides when Telegram allows`() {
        val removal = chatRemovalOf(chat(forEveryone = true))
        assertTrue(removal.canClearHistory)
        assertEquals("Delete chat", removal.removeLabel)
        assertFalse(removal.leaves)
        assertTrue(removal.offerForEveryone)
        assertFalse(chatRemovalOf(chat(forEveryone = false)).offerForEveryone)
    }

    @Test
    fun `groups and channels are left, not deleted`() {
        val group = chatRemovalOf(chat(isGroup = true, forEveryone = true))
        assertEquals("Leave group", group.removeLabel)
        assertTrue(group.leaves)
        assertFalse(group.canClearHistory)
        assertFalse(group.offerForEveryone)
        assertEquals("Leave channel", chatRemovalOf(chat(isChannel = true)).removeLabel)
    }

    @Test
    fun `saved messages can be emptied and nothing else`() {
        val saved = chatRemovalOf(chat(isSaved = true))
        assertTrue(saved.canClearHistory)
        assertNull(saved.removeLabel)
    }

    @Test
    fun `dialogs call the other side by the first name`() {
        assertEquals("Lina", firstNameOf("Lina Park"))
        assertEquals("Mom", firstNameOf("Mom"))
        assertEquals("  ", firstNameOf("  "))
    }
}
