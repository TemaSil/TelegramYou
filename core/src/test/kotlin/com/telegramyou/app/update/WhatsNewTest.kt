package com.telegramyou.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WhatsNewTest {

    @Test
    fun `both languages survive release parsing without mixing their bullets`() {
        val body = "<!-- whats-new-ru -->\n# Удобнее\n- Общие группы\n<!-- /whats-new-ru -->\n" +
            "$WHATS_NEW_START\n# Closer\n- Common groups\n$WHATS_NEW_END"
        val release = releaseOf("TelegramYou 3.0", body, mapOf(APK_ASSET to ("https://example.test/app.apk" to 10L)))!!
        assertEquals(WhatsNew("Closer", listOf("Common groups")), release.notes)
        assertEquals(WhatsNew("Удобнее", listOf("Общие группы")), release.notesRu)
    }

    @Test
    fun `older release descriptions remain usable without a Russian block`() {
        val body = "$WHATS_NEW_START\n# Closer\n- Common groups\n$WHATS_NEW_END"
        assertNotNull(whatsNewIn(body))
        assertNull(whatsNewIn(body, "ru"))
    }

    @Test
    fun `Russian shipped notes cover the same update and stay short`() {
        val english = parseWhatsNew(File("../app/src/main/assets/whats-new.md").readText())!!
        val russian = parseWhatsNew(File("../app/src/main/assets/whats-new-ru.md").readText())!!
        assertEquals(english.items.size, russian.items.size)
        assertTrue(russian.items.size <= WHATS_NEW_MAX_ITEMS)
        assertTrue(russian.items.all { it.length <= 90 })
        assertTrue(russian.title.length <= 60)
        assertFalse(russian.title.any(Char::isDigit))
    }

    @Test
    fun `a title and its lines`() {
        val notes = parseWhatsNew("# Smoother\n\n- Chats close in one piece\n- Fewer icons\nstray text")
        assertEquals(WhatsNew("Smoother", listOf("Chats close in one piece", "Fewer icons")), notes)
    }

    @Test
    fun `nothing without a title or a line`() {
        assertNull(parseWhatsNew("- a line with no title"))
        assertNull(parseWhatsNew("# A title with no lines"))
    }

    @Test
    fun `read back out of a release description`() {
        val body = "The newest build.\n\n$WHATS_NEW_START\n# Hi\n- One thing\n$WHATS_NEW_END\n\nBuilt from abc."
        assertEquals(WhatsNew("Hi", listOf("One thing")), whatsNewIn(body))
        assertNull(whatsNewIn("A release from before there were notes."))
    }

    /**
     * The file the app ships and CI publishes: it parses, and it stays
     * short — the owner's rule, and the reason this test exists.
     */
    @Test
    fun `the shipped notes are short`() {
        val file = File("../app/src/main/assets/whats-new.md")
        val notes = parseWhatsNew(file.readText())
        assertNotNull("whats-new.md must have a # title and - lines", notes)
        assertTrue("at most $WHATS_NEW_MAX_ITEMS lines", notes!!.items.size <= WHATS_NEW_MAX_ITEMS)
        assertTrue("one short sentence a line", notes.items.all { it.length <= 90 })
        // The card already says "What's new in 2.4" over it: the title says
        // what the update is, not the app's name and version again (the
        // owner's word, 22 October 2026).
        assertFalse(
            "the title repeats the app and version: \"${notes.title}\"",
            notes.title.startsWith("TelegramYou") || notes.title.any(Char::isDigit)
        )
        assertTrue("a short title", notes.title.length <= 60)
    }
}
