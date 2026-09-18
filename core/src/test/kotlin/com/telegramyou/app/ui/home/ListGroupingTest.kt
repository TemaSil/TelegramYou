package com.telegramyou.app.ui.home

import org.junit.Assert.assertEquals
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
}
