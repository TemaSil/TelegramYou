package com.telegramyou.app.ui.avatars

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarShapesTest {

    @Test
    fun `the same person gets the same shape every time`() {
        assertEquals(avatarShapeIndex(4242L, 35), avatarShapeIndex(4242L, 35))
    }

    @Test
    fun `a negative id still indexes into the list`() {
        // Telegram's ids are signed, and Kotlin's % keeps the sign of its
        // left operand — so this is the case that would index backwards off
        // the start of the list.
        val index = avatarShapeIndex(-7L, 35)
        assertTrue("index $index is outside 0..34", index in 0..34)
    }

    @Test
    fun `every index lands inside the list`() {
        val seeds = listOf(Long.MIN_VALUE, -1L, 0L, 1L, Long.MAX_VALUE)
        avatarShapeIndices(seeds, 35).forEach { index ->
            assertTrue("index $index is outside 0..34", index in 0..34)
        }
    }

    @Test
    fun `neighbours with colliding seeds are given different shapes`() {
        // Two identical shapes side by side read as one wider blob rather
        // than as two people.
        val indices = avatarShapeIndices(listOf(5L, 5L), 35)
        assertNotEquals(indices[0], indices[1])
    }

    @Test
    fun `the first of a colliding pair keeps its own shape`() {
        // The leftmost avatar is the one the eye lands on, so it is the
        // second that gives way.
        val indices = avatarShapeIndices(listOf(5L, 5L), 35)
        assertEquals(avatarShapeIndex(5L, 35), indices[0])
    }

    @Test
    fun `a run of three collisions still alternates`() {
        val indices = avatarShapeIndices(listOf(5L, 5L, 5L), 35)
        assertNotEquals(indices[0], indices[1])
        assertNotEquals(indices[1], indices[2])
    }

    @Test
    fun `people who are not neighbours may share a shape`() {
        // Only adjacency matters; forcing global uniqueness would take a
        // person's shape away for no visual gain.
        val indices = avatarShapeIndices(listOf(5L, 6L, 5L), 35)
        assertEquals(indices[0], indices[2])
    }

    @Test
    fun `an empty cluster has no shapes`() {
        assertEquals(emptyList<Int>(), avatarShapeIndices(emptyList(), 35))
    }

    @Test
    fun `one shape available means everyone wears it`() {
        // Nothing to nudge towards; the loop must not spin looking.
        assertEquals(listOf(0, 0, 0), avatarShapeIndices(listOf(1L, 1L, 2L), 1))
    }
}
