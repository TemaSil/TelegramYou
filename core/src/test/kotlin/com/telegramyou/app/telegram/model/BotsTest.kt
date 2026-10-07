package com.telegramyou.app.telegram.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BotsTest {

    @Test
    fun `a bot's name and a space make a query, even an empty one`() {
        assertEquals(InlineQuery("gif", "cats"), inlineQueryOf("@gif cats"))
        assertEquals(InlineQuery("wikibot", "material you"), inlineQueryOf("@wikibot material you"))
        assertEquals(InlineQuery("expressive", ""), inlineQueryOf("@expressive "))
    }

    @Test
    fun `a mention still being typed, or text before the name, is not a query`() {
        assertNull(inlineQueryOf("@expressive"))
        assertNull(inlineQueryOf("hi @expressive motion"))
        assertNull(inlineQueryOf("@ motion"))
        assertNull(inlineQueryOf("@1abcde motion"))
        assertNull(inlineQueryOf("@ab motion"))
    }

    @Test
    fun `the theme goes to the page as hex colours under the bridge's names`() {
        val theme = WebAppTheme(
            background = 0x112233, secondaryBackground = 0, headerBackground = 0, bottomBarBackground = 0,
            sectionBackground = 0, sectionSeparator = 0, text = 0xFFFFFF, accentText = 0, sectionHeaderText = 0,
            subtitleText = 0, destructiveText = 0, hint = 0, link = 0, button = 0xFF6750A4.toInt(), buttonText = 0
        )
        val params = theme.toParams()
        assertEquals("#112233", params["bg_color"])
        assertEquals("#ffffff", params["text_color"])
        assertEquals("#6750a4", params["button_color"])
        assertEquals(15, params.size)
    }
}
