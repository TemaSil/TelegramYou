package com.telegramyou.app.telegram.model

import com.telegramyou.app.telegram.model.ChatPositions.ChatList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatPositionsTest {

    // What real orders look like: a date in the top bits. Every one of these
    // is far above the 2^50 the backend once used to decide "pinned".
    private val older = 7_300_000_000_000_000_000L
    private val newer = 7_300_000_100_000_000_000L

    @Test
    fun `a chat with no position anywhere is not listed`() {
        val positions = ChatPositions()
        positions.apply(1, ChatList.Main, newer, isPinned = false)

        assertEquals(listOf(1L), positions.listed(listOf(1L, 2L)))
        assertFalse(positions.isListed(2))
    }

    @Test
    fun `an order of zero takes a chat out, the way leaving a group does`() {
        val positions = ChatPositions()
        positions.apply(1, ChatList.Main, newer, isPinned = false)
        positions.apply(1, ChatList.Main, 0, isPinned = false)

        assertTrue(positions.listed(listOf(1L)).isEmpty())
    }

    @Test
    fun `pinned is what the position says, not how big the order is`() {
        val positions = ChatPositions()
        positions.apply(1, ChatList.Main, newer, isPinned = false)
        positions.apply(2, ChatList.Main, older, isPinned = true)

        assertFalse(positions.isPinned(1))
        assertTrue(positions.isPinned(2))
    }

    @Test
    fun `chats are sorted by order, then by id when orders tie`() {
        val positions = ChatPositions()
        positions.apply(1, ChatList.Main, older, isPinned = false)
        positions.apply(2, ChatList.Main, newer, isPinned = false)
        positions.apply(3, ChatList.Main, older, isPinned = false)

        assertEquals(listOf(2L, 3L, 1L), positions.listed(listOf(1L, 2L, 3L)))
    }

    @Test
    fun `an archived chat is listed, archived, and sorted by its archive order`() {
        val positions = ChatPositions()
        positions.apply(1, ChatList.Main, older, isPinned = false)
        positions.apply(2, ChatList.Archive, newer, isPinned = true)

        assertTrue(positions.isArchived(2))
        assertFalse(positions.isArchived(1))
        assertTrue(positions.isPinned(2))
        assertEquals(listOf(2L, 1L), positions.listed(listOf(1L, 2L)))
    }

    @Test
    fun `moving out of the archive leaves it unarchived`() {
        val positions = ChatPositions()
        positions.apply(2, ChatList.Archive, newer, isPinned = false)
        positions.apply(2, ChatList.Archive, 0, isPinned = false)
        positions.apply(2, ChatList.Main, newer, isPinned = false)

        assertFalse(positions.isArchived(2))
        assertTrue(positions.isListed(2))
    }

    @Test
    fun `folders are a set, joined and left by order`() {
        val positions = ChatPositions()
        positions.apply(1, ChatList.Folder(5), newer, isPinned = false)
        positions.apply(1, ChatList.Folder(6), newer, isPinned = false)
        positions.apply(1, ChatList.Folder(5), 0, isPinned = false)

        assertEquals(setOf(6), positions.folderIds(1))
        assertFalse("a folder alone does not put a chat in the list", positions.isListed(1))
    }

    @Test
    fun `clearing forgets every list`() {
        val positions = ChatPositions()
        positions.apply(1, ChatList.Main, newer, isPinned = true)
        positions.apply(1, ChatList.Folder(5), newer, isPinned = false)
        positions.clear()

        assertFalse(positions.isListed(1))
        assertTrue(positions.folderIds(1).isEmpty())
    }
}
