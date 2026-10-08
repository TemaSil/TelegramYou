package com.telegramyou.app.telegram.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharesTest {

    private fun item(n: Int, mime: String?) = SharedItem("file:///s/$n", "f$n", mime)

    @Test
    fun `nothing shared is no attachment`() {
        assertNull(shareDraft(emptyList()))
        assertTrue(IncomingShare(null, "  ", emptyList()).isEmpty)
    }

    @Test
    fun `pictures go as an album, up to ten`() {
        val draft = shareDraft((1..12).map { item(it, "image/jpeg") })
        assertEquals(AttachmentDraft.Photos((1..10).map { "file:///s/$it" }), draft)
    }

    @Test
    fun `anything else, or a mix, goes as files with their names`() {
        val draft = shareDraft(listOf(item(1, "image/png"), item(2, "application/pdf"), item(3, null)))
        assertEquals(
            AttachmentDraft.Files(listOf("file:///s/1", "file:///s/2", "file:///s/3"), listOf("f1", "f2", "f3")),
            draft
        )
    }
}
