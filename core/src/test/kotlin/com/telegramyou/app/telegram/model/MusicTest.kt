package com.telegramyou.app.telegram.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MusicTest {

    private fun tracks(vararg ids: Long) = ids.map { Track(messageId = it, chatId = 1, title = "Track $it") }

    private fun queue(vararg ids: Long, complete: Boolean = true) =
        MusicQueue(chatId = 1).withMore(tracks(*ids), complete)

    @Test
    fun `down the list, then nothing with repeat off`() {
        val q = queue(30, 20, 10).startingAt(0)
        assertEquals(1, q.following(auto = true))
        assertNull(q.startingAt(2).following(auto = true))
        assertNull(q.preceding())
    }

    @Test
    fun `reversed plays up the list`() {
        val q = queue(30, 20, 10).startingAt(2).ordered(QueueOrder.Reversed)
        assertEquals(1, q.following(auto = false))
        assertNull(q.startingAt(0).following(auto = false))
    }

    @Test
    fun `repeat one holds the track on its own, not on Next`() {
        val q = queue(30, 20).startingAt(0).copy(repeat = RepeatMode.One)
        assertEquals(0, q.following(auto = true))
        assertEquals(1, q.following(auto = false))
    }

    @Test
    fun `repeat all wraps round, once the whole chat is loaded`() {
        val q = queue(30, 20, 10).startingAt(2).copy(repeat = RepeatMode.All)
        assertEquals(0, q.following(auto = true))
        val partial = queue(30, 20, 10, complete = false).startingAt(2).copy(repeat = RepeatMode.All)
        assertNull("wrapped before older tracks were loaded", partial.following(auto = true))
        assertTrue(partial.needsMore())
    }

    @Test
    fun `more tracks are appended and never reshuffle the list`() {
        val q = queue(30, 20, complete = false).startingAt(1)
        val more = q.withMore(tracks(20, 10, 5), complete = true)
        assertEquals(listOf(30L, 20L, 10L, 5L), more.tracks.map { it.messageId })
        assertEquals(1, more.current)
        assertEquals(2, more.following(auto = true))
        assertFalse(more.needsMore())
    }

    @Test
    fun `shuffle starts from the playing track and covers every one`() {
        val q = queue(50, 40, 30, 20, 10).startingAt(2).ordered(QueueOrder.Shuffled, Random(7))
        assertEquals(2, q.shuffled.first())
        assertEquals((0..4).toSet(), q.shuffled.toSet())
        // The list itself is untouched: only the play order is shuffled.
        assertEquals(listOf(50L, 40L, 30L, 20L, 10L), q.tracks.map { it.messageId })
        val more = q.withMore(tracks(5, 1), complete = true, random = Random(1))
        assertEquals(q.shuffled, more.shuffled.take(5))
        assertEquals(setOf(5, 6), more.shuffled.drop(5).toSet())
    }

    @Test
    fun `a music message becomes a track, others do not`() {
        val song = ChatMessage(
            id = 9, chatId = 1, text = "", isOutgoing = false, timeLabel = "", senderName = "Lina",
            contentType = MessageContentType.Audio,
            audio = AudioContent(title = "Waves", performer = "Material Sound", durationSeconds = 200, fileId = 4)
        )
        val track = song.asTrack()!!
        assertEquals("Waves", track.title)
        assertEquals(4, track.fileId)
        assertEquals("Lina", track.senderName)
        assertNull(song.copy(audio = null).asTrack())
    }

    @Test
    fun `only a long track carries on, and not from its edges`() {
        assertNull(resumeFrom(durationSeconds = 200, savedMs = 60_000))
        assertEquals(300_000L, resumeFrom(durationSeconds = 3_600, savedMs = 300_000))
        assertNull(resumeFrom(durationSeconds = 3_600, savedMs = 3_000))
        assertNull(resumeFrom(durationSeconds = 3_600, savedMs = 3_599_000))
    }

    @Test
    fun `speed steps and comes back to normal`() {
        assertEquals(1.25f, nextMusicSpeed(1f))
        assertEquals(1f, nextMusicSpeed(0.75f))
    }
}
