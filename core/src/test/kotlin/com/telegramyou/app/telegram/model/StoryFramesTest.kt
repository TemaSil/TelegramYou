package com.telegramyou.app.telegram.model

import com.telegramyou.app.ui.format.storyAgeLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class StoryFramesTest {

    @Test
    fun `the viewer starts at the first story not yet seen`() {
        val frames = listOf(
            StoryFrame(id = 1, isSeen = true),
            StoryFrame(id = 2, isSeen = false),
            StoryFrame(id = 3, isSeen = false)
        )
        assertEquals(1, frames.startIndex())
        assertEquals("all seen: from the start", 0, frames.map { it.copy(isSeen = true) }.startIndex())
        assertEquals(0, emptyList<StoryFrame>().startIndex())
    }

    @Test
    fun `a photo stays five seconds and a video as long as it runs`() {
        assertEquals(STORY_PHOTO_MILLIS, storyFrameMillis(StoryFrame(id = 1)))
        assertEquals(12_500L, storyFrameMillis(StoryFrame(id = 1, isVideo = true, durationSeconds = 12.5)))
        assertEquals(
            "a video with no length is timed as a photo",
            STORY_PHOTO_MILLIS,
            storyFrameMillis(StoryFrame(id = 1, isVideo = true))
        )
        assertEquals(60_000L, storyFrameMillis(StoryFrame(id = 1, isVideo = true, durationSeconds = 600.0)))
    }

    @Test
    fun `a story's age reads in minutes, then hours`() {
        val now = 1_000_000L
        assertEquals("just now", storyAgeLabel(now - 20, now))
        assertEquals("12m", storyAgeLabel(now - 12 * 60, now))
        assertEquals("5h", storyAgeLabel(now - 5 * 3600 - 100, now))
        assertEquals("", storyAgeLabel(0, now))
    }
}
