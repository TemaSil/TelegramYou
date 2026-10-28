package com.telegramyou.app.telegram.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedTest {

    @Test
    fun `a folder link is known with or without its scheme`() {
        assertTrue(isFolderLink("https://t.me/addlist/AbC_d-9"))
        assertTrue(isFolderLink("t.me/addlist/AbC"))
        assertTrue(isFolderLink(" https://telegram.me/addlist/xyz/ "))
        assertEquals("AbC_d-9", folderLinkSlug("https://t.me/addlist/AbC_d-9"))
    }

    @Test
    fun `other links are not folder links`() {
        assertFalse(isFolderLink("https://t.me/+Invite"))
        assertFalse(isFolderLink("https://t.me/addlist/"))
        assertFalse(isFolderLink("https://example.org/addlist/abc"))
        assertNull(folderLinkSlug("t.me/addlist/a b"))
    }

    @Test
    fun `a slug makes the link back`() {
        assertEquals("https://t.me/addlist/AbC", folderLinkOf(folderLinkSlug("t.me/addlist/AbC")!!))
    }
}
