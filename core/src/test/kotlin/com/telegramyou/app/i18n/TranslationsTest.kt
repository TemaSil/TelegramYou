package com.telegramyou.app.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The app's Russian (2.6) against its English, read straight from :app's
 * resources: every string English has, Russian has, with the same
 * placeholders, and each plural with the forms Russian needs. A string
 * added in English alone fails here rather than showing up in English on a
 * Russian phone; an apostrophe left unescaped fails here rather than in
 * aapt, which only says the build broke.
 */
class TranslationsTest {

    private val res: File = listOf("../app/src/main/res", "app/src/main/res")
        .map(::File)
        .first { it.isDirectory }

    private data class Entry(val kind: String, val texts: Map<String, String>)

    private fun read(folder: String): Map<String, Entry> {
        val file = File(res, "$folder/strings.xml")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val out = LinkedHashMap<String, Entry>()
        val nodes = doc.documentElement.childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i) as? org.w3c.dom.Element ?: continue
            if (node.getAttribute("translatable") == "false") continue
            val name = node.getAttribute("name")
            when (node.tagName) {
                "string" -> out[name] = Entry("string", mapOf("" to node.textContent))
                "plurals" -> {
                    val items = node.getElementsByTagName("item")
                    out[name] = Entry(
                        "plurals",
                        (0 until items.length).associate { j ->
                            val item = items.item(j) as org.w3c.dom.Element
                            item.getAttribute("quantity") to item.textContent
                        }
                    )
                }
            }
        }
        return out
    }

    private val english = read("values")
    private val russian = read("values-ru")

    private fun placeholders(text: String): List<String> =
        Regex("""%(\d+\$)?[sd]""").findAll(text).map { it.value }.sorted().toList()

    @Test
    fun russianHasEveryStringAndNothingElse() {
        val missing = english.keys - russian.keys
        val extra = russian.keys - english.keys
        assertTrue("not in Russian: $missing", missing.isEmpty())
        assertTrue("in Russian only: $extra", extra.isEmpty())
    }

    @Test
    fun placeholdersMatch() {
        english.forEach { (name, entry) ->
            val ru = russian[name] ?: return@forEach
            assertEquals("$name is a ${entry.kind} in English", entry.kind, ru.kind)
            val en = placeholders(entry.texts.values.first())
            ru.texts.forEach { (quantity, text) ->
                // A Russian "one" may say the number in words; every other
                // form carries what English carries.
                if (quantity == "one" && placeholders(text).isEmpty()) return@forEach
                assertEquals("$name ($quantity)", en, placeholders(text))
            }
        }
    }

    @Test
    fun russianPluralsHaveTheirFourForms() {
        russian.filterValues { it.kind == "plurals" }.forEach { (name, entry) ->
            assertEquals("$name", setOf("one", "few", "many", "other"), entry.texts.keys)
        }
        english.filterValues { it.kind == "plurals" }.forEach { (name, entry) ->
            assertTrue("$name has no other", "other" in entry.texts.keys)
        }
    }

    @Test
    fun apostrophesAreEscaped() {
        listOf("values", "values-ru").forEach { folder ->
            val raw = File(res, "$folder/strings.xml").readText()
            Regex("""<(string|item)[^>]*>([^<]*)</""").findAll(raw).forEach { match ->
                val text = match.groupValues[2]
                assertTrue(
                    "$folder: an unescaped apostrophe in \"$text\"",
                    !Regex("""(?<!\\)'""").containsMatchIn(text)
                )
            }
        }
    }
}
