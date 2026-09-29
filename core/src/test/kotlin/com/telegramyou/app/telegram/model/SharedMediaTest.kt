package com.telegramyou.app.telegram.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedMediaTest {

    private fun message(type: MessageContentType, text: String = "") =
        ChatMessage(id = 1, chatId = 1, text = text, isOutgoing = false, timeLabel = "", contentType = type)

    @Test
    fun `each tab takes its own kind`() {
        assertTrue(SharedMediaKind.Media.matches(message(MessageContentType.Photo)))
        assertTrue(SharedMediaKind.Media.matches(message(MessageContentType.Video)))
        assertTrue(SharedMediaKind.Voice.matches(message(MessageContentType.VideoNote)))
        assertTrue(SharedMediaKind.Music.matches(message(MessageContentType.Audio)))
        assertFalse(SharedMediaKind.Files.matches(message(MessageContentType.Audio)))
        assertTrue(SharedMediaKind.Gifs.matches(message(MessageContentType.Animation)))
    }

    @Test
    fun `a message with an address in it is a link`() {
        assertTrue(SharedMediaKind.Links.matches(message(MessageContentType.Text, "see m3.material.io/blog for more")))
        assertFalse(SharedMediaKind.Links.matches(message(MessageContentType.Text, "no address here.")))
    }

    @Test
    fun `the first link loses the punctuation around it`() {
        assertEquals("https://example.com/a", firstLink("Look (https://example.com/a)."))
        assertEquals("m3.material.io/blog", firstLink("the blog: m3.material.io/blog, today"))
        assertNull(firstLink("nothing"))
    }

    @Test
    fun `a host is the site without scheme or www`() {
        assertEquals("github.com", linkHost("https://www.github.com/TemaSil"))
        assertEquals("m3.material.io", linkHost("m3.material.io/blog"))
    }

    @Test
    fun `an extension is short and upper case`() {
        assertEquals("PDF", fileExtension("Brief.pdf"))
        assertNull(fileExtension("README"))
        assertNull(fileExtension(null))
    }
}
