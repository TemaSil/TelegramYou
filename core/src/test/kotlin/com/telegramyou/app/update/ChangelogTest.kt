package com.telegramyou.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangelogTest {

    @Test
    fun `newest first, one entry a day, none empty`() {
        val dates = CHANGELOG.map { it.date }
        assertEquals(dates.sortedDescending(), dates)
        assertEquals("one entry per day, so the screen's keys stay unique", dates.size, dates.toSet().size)
        assertTrue(CHANGELOG.all { it.title.isNotBlank() && it.items.isNotEmpty() && it.items.none(String::isBlank) })
    }

    @Test
    fun `dates read as a person says them`() {
        assertEquals("26 September", CHANGELOG.first().dateLabel)
    }
}
