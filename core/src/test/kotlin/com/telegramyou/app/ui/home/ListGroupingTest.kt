package com.telegramyou.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListGroupingTest {

    @Test
    fun `a lone row is rounded at both ends`() {
        val position = rowPosition(index = 0, size = 1)
        assertEquals(RowPosition.Single, position)
        assertTrue(position.roundedTop)
        assertTrue(position.roundedBottom)
    }

    @Test
    fun `the first of several rounds only its top`() {
        val position = rowPosition(index = 0, size = 3)
        assertEquals(RowPosition.First, position)
        assertTrue(position.roundedTop)
        assertFalse(position.roundedBottom)
    }

    @Test
    fun `the last of several rounds only its bottom`() {
        val position = rowPosition(index = 2, size = 3)
        assertEquals(RowPosition.Last, position)
        assertFalse(position.roundedTop)
        assertTrue(position.roundedBottom)
    }

    @Test
    fun `a row in the middle is square at both ends`() {
        val position = rowPosition(index = 1, size = 3)
        assertEquals(RowPosition.Middle, position)
        assertFalse(position.roundedTop)
        assertFalse(position.roundedBottom)
    }

    @Test
    fun `two rows are a first and a last, with no middle`() {
        assertEquals(RowPosition.First, rowPosition(0, 2))
        assertEquals(RowPosition.Last, rowPosition(1, 2))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an index outside the group is refused`() {
        rowPosition(index = 3, size = 3)
    }

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
