package com.telegramyou.app.telegram.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {

    private fun chat(id: Long, group: Boolean = false, channel: Boolean = false, bot: Boolean = false) =
        ChatPreview(id, "c$id", "", "", isGroup = group, isChannel = channel, isBot = bot)

    private val person = chat(1)
    private val group = chat(2, group = true)
    private val channel = chat(3, channel = true)
    private val bot = chat(4, bot = true)

    @Test
    fun `each tab admits its own kind of chat`() {
        val all = listOf(person, group, channel, bot)
        assertEquals(all, all.filter(SearchScope.All::admits))
        assertEquals(listOf(person), all.filter(SearchScope.Chats::admits))
        assertEquals(listOf(group), all.filter(SearchScope.Groups::admits))
        assertEquals(listOf(channel), all.filter(SearchScope.Channels::admits))
        assertEquals(listOf(bot), all.filter(SearchScope.Bots::admits))
        assertTrue(all.none(SearchScope.Posts::admits))
    }

    @Test
    fun `only the word tabs search words`() {
        assertTrue(SearchScope.All.showsMessages)
        assertTrue(SearchScope.Messages.showsMessages)
        assertFalse(SearchScope.Messages.showsChats)
        assertTrue(SearchScope.Posts.showsPosts)
        assertFalse(SearchScope.All.showsPosts)
    }

    @Test
    fun `a chat found both ways is listed once, as one of ours`() {
        val merged = mergeChatResults(known = listOf(person, channel), global = listOf(channel, bot))
        assertEquals(listOf(person, channel), merged.mine)
        assertEquals(listOf(bot), merged.global)
        assertEquals(SearchChats(listOf(channel), emptyList()), merged.filteredBy(SearchScope.Channels))
        assertTrue(merged.filteredBy(SearchScope.Groups).isEmpty)
    }

    @Test
    fun `a finished query replaces the prefixes typed on the way`() {
        val history = listOf("mat").remembering("material")
        assertEquals(listOf("material"), history)
        assertEquals(listOf("kotlin", "material"), history.remembering("kotlin"))
    }

    @Test
    fun `a repeated query moves to the front, whatever its case`() {
        assertEquals(listOf("Compose", "kotlin"), listOf("kotlin", "compose").remembering("Compose"))
    }

    @Test
    fun `one letter is not a search worth keeping, and the list is capped`() {
        assertEquals(emptyList<String>(), emptyList<String>().remembering(" a "))
        val long = (1..20).fold(emptyList<String>()) { list, n -> list.remembering("query $n x") }
        assertEquals(RECENT_QUERY_LIMIT, long.size)
        assertEquals("query 20 x", long.first())
    }

    @Test
    fun `the post limit is said plainly`() {
        assertNull(PostSearch().limitLabel)
        assertEquals("1 free search left today", PostSearch(freeLeft = 1).limitLabel)
        assertEquals("7 free searches left today", PostSearch(freeLeft = 7).limitLabel)
        assertEquals(
            "Free searches used up for today — next in 3 hours",
            PostSearch(limitReached = true, nextFreeInSeconds = 9000).limitLabel
        )
    }
}
