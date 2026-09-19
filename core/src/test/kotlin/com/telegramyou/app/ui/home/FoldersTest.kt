package com.telegramyou.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FoldersTest {

    private data class Folder(val id: Int, val title: String)

    private data class Chat(
        val name: String,
        val folders: Set<Int> = emptySet(),
        val unread: Int = 0
    )

    private val work = Folder(1, "Work")
    private val friends = Folder(2, "Friends")

    private val chats = listOf(
        Chat("Ivan", setOf(1), unread = 3),
        Chat("Noor", setOf(1, 2)),
        Chat("Mum", setOf(2), unread = 1),
        Chat("Nobody", emptySet(), unread = 9)
    )

    @Test
    fun `no folders means no tabs at all`() {
        assertTrue(folderTabs(emptyList<Folder>(), { it.id }, { it.title }).isEmpty())
    }

    @Test
    fun `All comes first and has no id`() {
        val tabs = folderTabs(listOf(work, friends), { it.id }, { it.title })
        assertEquals(3, tabs.size)
        assertEquals(FolderTab(null, ALL_CHATS_TAB), tabs.first())
        assertEquals(listOf(ALL_CHATS_TAB, "Work", "Friends"), tabs.map { it.title })
        assertEquals(listOf(null, 1, 2), tabs.map { it.id })
    }

    @Test
    fun `All is every chat, including those in no folder`() {
        assertEquals(chats, chatsInFolder(chats, null) { it.folders })
    }

    @Test
    fun `a folder is the chats that name it`() {
        assertEquals(
            listOf("Ivan", "Noor"),
            chatsInFolder(chats, 1) { it.folders }.map { it.name }
        )
        assertEquals(
            listOf("Noor", "Mum"),
            chatsInFolder(chats, 2) { it.folders }.map { it.name }
        )
    }

    @Test
    fun `a chat can be in several folders at once`() {
        val noor = chats.first { it.name == "Noor" }
        assertTrue(noor in chatsInFolder(chats, 1) { it.folders })
        assertTrue(noor in chatsInFolder(chats, 2) { it.folders })
    }

    @Test
    fun `a folder nobody is in is empty rather than everything`() {
        assertTrue(chatsInFolder(chats, 99) { it.folders }.isEmpty())
    }

    @Test
    fun `a selection survives while its folder does`() {
        assertEquals(1, selectedFolder(listOf(work, friends), { it.id }, 1))
        assertNull(selectedFolder(listOf(work, friends), { it.id }, null))
    }

    @Test
    fun `a deleted folder falls back to All`() {
        assertNull(selectedFolder(listOf(friends), { it.id }, 1))
        assertNull(selectedFolder(emptyList<Folder>(), { it.id }, 1))
    }

    @Test
    fun `the badge counts chats with something unread`() {
        assertEquals(1, folderUnreadChats(chats, 1, { it.folders }, { it.unread }))
        assertEquals(1, folderUnreadChats(chats, 2, { it.folders }, { it.unread }))
        assertEquals(3, folderUnreadChats(chats, null, { it.folders }, { it.unread }))
        assertEquals(0, folderUnreadChats(chats, 99, { it.folders }, { it.unread }))
    }
}
