package com.telegramyou.app.telegram.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextEntitiesTest {

    @Test
    fun `markdown markers become entities over the words between them`() {
        val (text, entities) = parseMarkdown("say **hi** and __bye__, ~~no~~ ||shh|| `x`")
        assertEquals("say hi and bye, no shh x", text)
        assertEquals(
            listOf(
                TextEntity(4, 2, EntityType.Bold),
                TextEntity(11, 3, EntityType.Italic),
                TextEntity(16, 2, EntityType.Strikethrough),
                TextEntity(19, 3, EntityType.Spoiler),
                TextEntity(23, 1, EntityType.Code)
            ),
            entities
        )
    }

    @Test
    fun `an unclosed or empty marker stays as typed`() {
        assertEquals("2 ** 3" to emptyList<TextEntity>(), parseMarkdown("2 ** 3"))
        assertEquals("a **** b" to emptyList<TextEntity>(), parseMarkdown("a **** b"))
    }

    @Test
    fun `entities past the text are clipped, empty ones dropped`() {
        val text = "hello"
        val clamped = clampEntities(
            text,
            listOf(TextEntity(3, 10, EntityType.Bold), TextEntity(9, 2, EntityType.Italic), TextEntity(-2, 3, EntityType.Code))
        )
        assertEquals(listOf(TextEntity(3, 2, EntityType.Bold), TextEntity(0, 1, EntityType.Code)), clamped)
    }

    @Test
    fun `links lead where the entity says`() {
        val text = "see example.com or mail a@b.c, call +7 900 000-00-00, or docs"
        fun at(part: String, type: EntityType) = TextEntity(text.indexOf(part), part.length, type)
        assertEquals("https://example.com", linkTarget(text, at("example.com", EntityType.Url)))
        assertEquals("mailto:a@b.c", linkTarget(text, at("a@b.c", EntityType.Email)))
        assertEquals("tel:+79000000000", linkTarget(text, at("+7 900 000-00-00", EntityType.Phone)))
        assertEquals("https://d.ev", linkTarget(text, at("docs", EntityType.TextUrl("https://d.ev"))))
        assertNull(linkTarget(text, at("docs", EntityType.Bold)))
    }

    @Test
    fun `an album is the run of neighbours sharing its id`() {
        val messages = listOf(1 to null, 2 to 7L, 3 to 7L, 4 to 8L, 5 to 7L, 6 to null)
        assertEquals(
            listOf(listOf(1), listOf(2, 3), listOf(4), listOf(5), listOf(6)),
            albumRuns(messages) { it.second }.map { run -> run.map { it.first } }
        )
    }
}
