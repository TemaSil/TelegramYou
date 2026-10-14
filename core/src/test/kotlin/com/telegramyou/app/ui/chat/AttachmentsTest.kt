package com.telegramyou.app.ui.chat

import com.telegramyou.app.telegram.model.AttachmentDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AttachmentsTest {

    private fun photos(vararg uris: String) = AttachmentDraft.Photos(uris.toList())

    @Test
    fun `photos picked again join the ones waiting, each once, up to ten`() {
        assertEquals(photos("a", "b", "c"), photos("a", "b").plus(photos("b", "c")))
        val many = photos(*Array(8) { "p$it" }).plus(photos("x", "y", "z"))
        assertEquals(ALBUM_LIMIT, many.count)
        assertEquals(photos("a"), (null as AttachmentDraft?).plus(photos("a", "a")))
    }

    @Test
    fun `a different kind replaces what was waiting`() {
        val files = AttachmentDraft.Files(listOf("f"), listOf("f.pdf"))
        assertEquals(files, photos("a").plus(files))
    }

    @Test
    fun `a photo tapped twice in the strip is in and then out`() {
        val once = photos("a").toggled("b")
        assertEquals(photos("a", "b"), once)
        assertEquals(photos("a"), once.toggled("b"))
        assertNull(photos("a").toggled("a"))
    }

    @Test
    fun `taking one out closes the gap, and the last leaves nothing`() {
        assertEquals(photos("a", "c"), photos("a", "b", "c").without(1))
        assertNull(photos("a").without(0))
        val files = AttachmentDraft.Files(listOf("1", "2"), listOf("one", "two"))
        assertEquals(AttachmentDraft.Files(listOf("2"), listOf("two")), files.without(0))
    }

    @Test
    fun `a photo moves, its neighbours close up, and a move past the end stops there`() {
        assertEquals(photos("b", "c", "a"), photos("a", "b", "c").moved(0, 2))
        assertEquals(photos("c", "a", "b"), photos("a", "b", "c").moved(2, 0))
        assertEquals(photos("b", "c", "a"), photos("a", "b", "c").moved(0, 9))
        assertEquals(photos("a", "b"), photos("a", "b").moved(5, 0))
    }

    @Test
    fun `a spoiler stays with its photo through a move and another's removal`() {
        val draft = photos("a", "b", "c").withSpoiler("b", on = true)
        assertEquals(setOf("b"), (draft.moved(1, 0) as AttachmentDraft.Photos).spoilered)
        assertEquals(setOf("b"), (draft.without(0) as AttachmentDraft.Photos).spoilered)
        assertEquals(emptySet<String>(), (draft.without(1) as AttachmentDraft.Photos).spoilered)
        assertEquals(emptySet<String>(), draft.withSpoiler("b", on = false).spoilered)
    }

    @Test
    fun `an edited photo takes the place and the spoiler of the one it replaces`() {
        val draft = photos("a", "b").withSpoiler("a", on = true).replaced("a", "a-edited")
        assertEquals(listOf("a-edited", "b"), draft.uris)
        assertEquals(setOf("a-edited"), draft.spoilered)
        assertEquals(draft, draft.replaced("missing", "x"))
    }

    @Test
    fun `photos as files keep their order and are named by it`() {
        val files = photos("a", "b").asFiles()
        assertEquals(listOf("a", "b"), files.uris)
        assertEquals(listOf("photo-1.jpg", "photo-2.jpg"), files.names)
    }

    @Test
    fun `photos joined keep the spoilers of both`() {
        val joined = photos("a").withSpoiler("a", true).plus(photos("b").withSpoiler("b", true)) as AttachmentDraft.Photos
        assertEquals(setOf("a", "b"), joined.spoilered)
    }
}
