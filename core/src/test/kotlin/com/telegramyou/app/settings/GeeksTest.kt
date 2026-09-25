package com.telegramyou.app.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GeeksTest {

    @Test
    fun `everything is off until turned on`() {
        val defaults = GeekSettings()
        assertEquals(DoubleTapAction.Nothing, defaults.doubleTap)
        assertFalse(defaults.showSeconds || defaults.messageDetails || defaults.saveMedia)
        assertFalse(defaults.forwardWithoutQuote || defaults.hideStories || defaults.hideAllChatsTab)
        assertFalse(defaults.preferIpv6)
    }

    @Test
    fun `the All tab goes only when asked and when there is another`() {
        assertEquals(1, hiddenLeadingTabs(listOf(null, 1, 2), hideAll = true))
        assertEquals(0, hiddenLeadingTabs(listOf(null, 1, 2), hideAll = false))
        assertEquals("All alone is the whole list", 0, hiddenLeadingTabs(listOf(null), hideAll = true))
        assertEquals(0, hiddenLeadingTabs(emptyList(), hideAll = true))
    }
}
