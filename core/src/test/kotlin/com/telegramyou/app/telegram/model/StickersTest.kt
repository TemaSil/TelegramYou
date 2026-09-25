package com.telegramyou.app.telegram.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StickersTest {

    @Test
    fun `a video sticker is drawn from its still picture`() {
        val video = StickerContent(emoji = "😀", format = StickerFormat.Webm, fileId = 1, thumbFileId = 2)
        assertEquals(2, video.drawnFileId)
        assertEquals("without a still, the video's own file", 1, video.copy(thumbFileId = null).drawnFileId)
    }

    @Test
    fun `pictures and animations draw their own file`() {
        assertEquals(1, StickerContent(emoji = "😀", fileId = 1, thumbFileId = 2).drawnFileId)
        assertEquals(1, StickerContent(emoji = "😀", format = StickerFormat.Tgs, fileId = 1).drawnFileId)
    }

    @Test
    fun `only Lottie stickers animate`() {
        assertTrue(StickerContent(emoji = "😀", format = StickerFormat.Tgs).isAnimated)
        assertFalse(StickerContent(emoji = "😀", format = StickerFormat.Webp).isAnimated)
        assertFalse(StickerContent(emoji = "😀", format = StickerFormat.Webm).isAnimated)
    }
}
