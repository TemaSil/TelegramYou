package com.telegramyou.app.telegram.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadInfoTest {

    private val time: (Long) -> String = { "at $it" }

    @Test
    fun `a private chat says when it was read`() {
        assertEquals("Read at 100", readLine(ReadInfo.ReadAt(100), time))
        assertEquals("Not read yet", readLine(ReadInfo.Unread, time))
    }

    @Test
    fun `a group says who, or how many`() {
        val lina = Viewer(1, "Lina", 10)
        assertEquals("Seen by Lina", readLine(ReadInfo.SeenBy(listOf(lina)), time))
        assertEquals("Seen by 2", readLine(ReadInfo.SeenBy(listOf(lina, Viewer(2, "Ivan", 9))), time))
        assertEquals("Not seen yet", readLine(ReadInfo.SeenBy(emptyList()), time))
    }

    @Test
    fun `nothing is said where Telegram will not say`() {
        assertNull(readLine(ReadInfo.Hidden, time))
        assertNull(readLine(null, time))
    }
}
