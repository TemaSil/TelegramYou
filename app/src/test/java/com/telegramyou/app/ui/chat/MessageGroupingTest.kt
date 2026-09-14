package com.telegramyou.app.ui.chat

import com.telegramyou.app.telegram.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale

class MessageGroupingTest {

    private fun message(
        id: Long,
        date: Long,
        outgoing: Boolean = false
    ) = ChatMessage(
        id = id,
        chatId = 1,
        text = "m$id",
        isOutgoing = outgoing,
        timeLabel = "",
        date = date
    )

    /** Midday on a fixed day, so nothing here depends on when it runs. */
    private fun at(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long {
        val cal = Calendar.getInstance()
        cal.set(year, month - 1, day, hour, minute, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis / 1000
    }

    // ── runs ──────────────────────────────────────────────────────────────

    @Test
    fun `a run continues while the same side keeps talking`() {
        val first = message(1, at(2026, 9, 14, 12, 0))
        val second = message(2, at(2026, 9, 14, 12, 1))
        assertFalse(endsRun(first, second))
    }

    @Test
    fun `a run ends when the other side replies`() {
        val incoming = message(1, at(2026, 9, 14, 12, 0))
        val outgoing = message(2, at(2026, 9, 14, 12, 1), outgoing = true)
        assertTrue(endsRun(incoming, outgoing))
    }

    @Test
    fun `a gap longer than five minutes ends the run`() {
        val first = message(1, at(2026, 9, 14, 12, 0))
        val justInside = message(2, at(2026, 9, 14, 12, 5))
        val justOutside = message(3, at(2026, 9, 14, 12, 6))

        assertFalse("five minutes exactly still counts as one run", endsRun(first, justInside))
        assertTrue(endsRun(first, justOutside))
    }

    @Test
    fun `the last message of a thread ends its run`() {
        assertTrue(endsRun(message(1, at(2026, 9, 14)), null))
    }

    @Test
    fun `a message with no date never joins a run`() {
        // Grouping on an unknown instant would collapse unrelated messages
        // together, which is worse than showing them apart.
        val undated = message(1, 0)
        val dated = message(2, at(2026, 9, 14))
        assertTrue(endsRun(undated, dated))
        assertTrue(endsRun(dated, undated))
    }

    // ── days ──────────────────────────────────────────────────────────────

    @Test
    fun `the first message of a thread opens a day`() {
        assertTrue(startsNewDay(null, message(1, at(2026, 9, 14))))
    }

    @Test
    fun `a separator appears only when the day changes`() {
        val monday = message(1, at(2026, 9, 14, 23, 59))
        val stillMonday = message(2, at(2026, 9, 14, 23, 59))
        val tuesday = message(3, at(2026, 9, 15, 0, 1))

        assertFalse(startsNewDay(monday, stillMonday))
        assertTrue("a minute later but a different day", startsNewDay(monday, tuesday))
    }

    @Test
    fun `the same day in different years is not the same day`() {
        assertFalse(sameDay(at(2025, 9, 14), at(2026, 9, 14)))
    }

    @Test
    fun `an undated message opens nothing`() {
        assertFalse(startsNewDay(null, message(1, 0)))
    }

    // ── labels ────────────────────────────────────────────────────────────

    @Test
    fun `labels read as today and yesterday relative to now`() {
        val now = at(2026, 9, 14, 15, 0)
        assertEquals("Today", dayLabel(at(2026, 9, 14, 9, 0), now))
        assertEquals("Yesterday", dayLabel(at(2026, 9, 13, 23, 0), now))
    }

    @Test
    fun `anything older gets a date`() {
        val now = at(2026, 9, 14, 15, 0)
        assertEquals("1 September", dayLabel(at(2026, 9, 1), now, Locale.ENGLISH))
    }

    @Test
    fun `no date gives no label`() {
        assertEquals("", dayLabel(0, at(2026, 9, 14)))
    }
}
