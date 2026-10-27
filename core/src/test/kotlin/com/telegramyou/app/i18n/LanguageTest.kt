package com.telegramyou.app.i18n

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class LanguageTest {

    private fun <T> inLocale(locale: Locale, block: () -> T): T {
        val before = Locale.getDefault()
        Locale.setDefault(locale)
        try {
            return block()
        } finally {
            Locale.setDefault(before)
        }
    }

    @Test
    fun aPairIsReadInTheLanguageInUse() {
        assertEquals("Chats", inLocale(Locale.ENGLISH) { tr("Chats", "Чаты") })
        assertEquals("Чаты", inLocale(Locale("ru", "RU")) { tr("Chats", "Чаты") })
        // Any other language falls back to English, the app's first.
        assertEquals("Chats", inLocale(Locale.GERMAN) { tr("Chats", "Чаты") })
    }

    @Test
    fun russianCountsTakeTheirThreeForms() {
        val forms = listOf(1L, 2L, 4L, 5L, 11L, 12L, 14L, 21L, 22L, 25L, 101L, 111L, 0L)
            .map { ruForm(it, "участник", "участника", "участников") }
        assertEquals(
            listOf(
                "участник", "участника", "участника", "участников", "участников", "участников",
                "участников", "участник", "участника", "участников", "участник", "участников", "участников"
            ),
            forms
        )
    }

    @Test
    fun aCountReadsWithItsNoun() {
        val members = { n: Int -> counted(n, "member", "members", "участник", "участника", "участников") }
        assertEquals("1 member", inLocale(Locale.ENGLISH) { members(1) })
        assertEquals("12 members", inLocale(Locale.ENGLISH) { members(12) })
        assertEquals("3 участника", inLocale(Locale("ru")) { members(3) })
        assertEquals("21 участник", inLocale(Locale("ru")) { members(21) })
    }
}
