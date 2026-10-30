package com.telegramyou.app.ui.chat

import com.telegramyou.app.telegram.model.MessageContentType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoFetchTest {

    @Test
    fun `a round video message is fetched whole even where videos stream`() {
        assertTrue(fetchesVideoWhole(MessageContentType.VideoNote, canStream = true))
    }

    @Test
    fun `a gif is fetched whole even where videos stream`() {
        assertTrue(fetchesVideoWhole(MessageContentType.Animation, canStream = true))
    }

    @Test
    fun `a video streams where it can, and is fetched where it cannot`() {
        assertFalse(fetchesVideoWhole(MessageContentType.Video, canStream = true))
        assertTrue(fetchesVideoWhole(MessageContentType.Video, canStream = false))
    }
}
