package com.telegramyou.app.telegram.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class ComposingTest {

    @Test
    fun `a poll needs a question and two different answers`() {
        assertEquals("Ask a question", PollDraft().problem)
        val asked = PollDraft(question = "Lunch?")
        assertEquals("Add at least two answers", asked.problem)
        assertEquals("Two answers are the same", asked.copy(options = listOf("Soup", "soup ")).problem)
        assertNull(asked.copy(options = listOf("Soup", "", "Salad")).problem)
        assertEquals(listOf("Soup", "Salad"), asked.copy(options = listOf(" Soup ", "", "Salad")).filledOptions)
    }

    @Test
    fun `typing into the last answer adds the next row, up to the limit`() {
        val draft = PollDraft(question = "Q").withOption(0, "A").withOption(1, "B")
        assertEquals(listOf("A", "B", ""), draft.options)
        val full = (0 until 20).fold(PollDraft()) { d, i -> d.withOption(d.options.lastIndex, "o$i") }
        assertEquals(POLL_OPTION_MAX, full.options.size)
        assertFalse(full.canAddOption)
    }

    @Test
    fun `removing an answer keeps the quiz's right answer pointing at the same words`() {
        val quiz = PollDraft(question = "Q", options = listOf("A", "B", "C")).asQuiz(true).copy(correctOption = 2)
        val fewer = quiz.withoutOption(0)
        assertEquals(listOf("B", "C"), fewer.options)
        assertEquals(1, fewer.correctOption)
        assertNull("removing the right answer unsets it", quiz.withoutOption(2).correctOption)
    }

    @Test
    fun `a quiz needs its right answer, counted past blank rows, and allows only one`() {
        val quiz = PollDraft(question = "Q", options = listOf("A", "", "C"), allowsMultiple = true).asQuiz(true)
        assertFalse(quiz.allowsMultiple)
        assertEquals("Choose the right answer", quiz.problem)
        val answered = quiz.copy(correctOption = 2)
        assertEquals(1, answered.correctIndex)
        assertTrue(answered.canSend)
        assertNull("a blank row cannot be the right answer", quiz.copy(correctOption = 1).correctIndex)
    }

    @Test
    fun `a scheduled time is the picked day at the picked time, where the person is`() {
        val zone = ZoneId.of("Europe/Moscow")
        val picked = LocalDateTime.of(2026, 10, 3, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val at = scheduleInstant(picked, 18, 30, zone)
        assertEquals(LocalDateTime.of(2026, 10, 3, 18, 30).atZone(zone).toEpochSecond(), at)
    }

    @Test
    fun `only the future within a year can be scheduled`() {
        val now = 1_000_000L
        assertFalse(isValidSchedule(now - 60, now))
        assertFalse(isValidSchedule(now + 5, now))
        assertTrue(isValidSchedule(now + 3600, now))
        assertFalse(isValidSchedule(now + SCHEDULE_MAX_AHEAD_SECONDS + 1, now))
    }

    @Test
    fun `schedule labels say today, tomorrow or the date`() {
        val zone = ZoneId.of("UTC")
        val now = LocalDateTime.of(2026, 9, 26, 12, 0).toEpochSecond(ZoneOffset.UTC)
        fun at(day: Int, month: Int = 9, year: Int = 2026) =
            LocalDateTime.of(year, month, day, 18, 5).toEpochSecond(ZoneOffset.UTC)
        assertEquals("Today at 18:05", scheduleLabel(at(26), now, zone))
        assertEquals("Tomorrow at 18:05", scheduleLabel(at(27), now, zone))
        assertEquals("12 Oct at 18:05", scheduleLabel(at(12, 10), now, zone))
        assertEquals("3 Jan 2027 at 18:05", scheduleLabel(at(3, 1, 2027), now, zone))
    }

    @Test
    fun `music goes as audio, everything else as a file`() {
        assertTrue(isAudioFileName("Song.MP3"))
        assertTrue(isAudioFileName("take.flac"))
        assertFalse(isAudioFileName("notes.pdf"))
        assertFalse(isAudioFileName("mp3"))
        assertEquals("Song", AudioContent(title = "", fileName = "Song.mp3").displayTitle)
        assertEquals("Title", AudioContent(title = "Title", fileName = "x.mp3").displayTitle)
    }
}
