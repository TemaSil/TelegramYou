package com.telegramyou.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListGroupingTest {

    @Test
    fun `pinned chats come first, in their own group`() {
        val grouped = groupChats(listOf("a" to true, "b" to false, "c" to true)) { it.second }
        assertEquals(2, grouped.size)
        assertEquals(listOf("a", "c"), grouped[0].map { it.first })
        assertEquals(listOf("b"), grouped[1].map { it.first })
    }

    @Test
    fun `order inside a group is the order given`() {
        val grouped = groupChats(listOf("b" to false, "a" to false)) { it.second }
        assertEquals(listOf("b", "a"), grouped.single().map { it.first })
    }

    @Test
    fun `with nothing pinned there is one group`() {
        val grouped = groupChats(listOf("a" to false, "b" to false)) { it.second }
        assertEquals(1, grouped.size)
    }

    @Test
    fun `with everything pinned there is one group`() {
        // The other group would be an empty container: a rounded rectangle
        // with nothing in it.
        val grouped = groupChats(listOf("a" to true, "b" to true)) { it.second }
        assertEquals(1, grouped.size)
    }

    @Test
    fun `no chats means no groups`() {
        assertEquals(emptyList<List<String>>(), groupChats(emptyList<String>()) { false })
    }

    // ── the archive row ──────────────────────────────────────────────────

    private data class Chat(val archived: Boolean, val unread: Int = 0)

    private fun summary(vararg chats: Chat) =
        archiveSummary(chats.toList(), { it.archived }, { it.unread })

    @Test
    fun `an empty archive has no row at all`() {
        // Null rather than an empty string, so a caller that forgets to check
        // cannot draw an entry for nothing.
        assertNull(summary(Chat(archived = false)))
        assertNull(summary())
    }

    @Test
    fun `one archived chat is not pluralised`() {
        assertEquals("1 chat", summary(Chat(archived = true)))
    }

    @Test
    fun `several archived chats are counted`() {
        assertEquals(
            "3 chats",
            summary(Chat(true), Chat(true), Chat(true), Chat(false))
        )
    }

    @Test
    fun `unread archived chats are counted separately`() {
        // Chats with something unread, not messages: the archive is where
        // things go to stop asking, so what matters is how many ask anyway.
        assertEquals(
            "3 chats, 2 unread",
            summary(Chat(true, unread = 5), Chat(true, unread = 1), Chat(true))
        )
    }

    @Test
    fun `unread chats outside the archive do not count`() {
        assertEquals("1 chat", summary(Chat(true), Chat(false, unread = 9)))
    }

}
