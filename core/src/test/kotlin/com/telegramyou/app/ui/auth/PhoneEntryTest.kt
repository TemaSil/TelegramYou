package com.telegramyou.app.ui.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class PhoneEntryTest {

    @Test
    fun `whatever is typed or pasted becomes a plus and digits`() {
        assertEquals("+79123456789", PhoneEntry.normalize("+7 (912) 345-67-89"))
        assertEquals("+", PhoneEntry.normalize(""))
        assertEquals("+", PhoneEntry.normalize("+"))
        assertEquals(
            "fifteen digits at most",
            16,
            PhoneEntry.normalize("1".repeat(30)).length
        )
    }

    @Test
    fun `numbers are formatted as they are typed`() {
        assertEquals("+7 912 345-67-89", PhoneEntry.format("+79123456789"))
        assertEquals("+1 650-253-0000", PhoneEntry.format("+16502530000"))
        assertEquals("+44 20 7946 0958", PhoneEntry.format("+442079460958"))
        assertEquals("+", PhoneEntry.format("+"))
        assertTrue(PhoneEntry.format("+7912").startsWith("+7 912"))
    }

    @Test
    fun `the country comes from the number`() {
        assertEquals("RU", PhoneEntry.region("+79123456789"))
        assertEquals("a complete +7 number can be Kazakh", "KZ", PhoneEntry.region("+77012345678"))
        assertEquals("GB", PhoneEntry.region("+442079460958"))
        assertEquals("the calling code alone gives its main country", "RU", PhoneEntry.region("+7"))
        assertEquals("US", PhoneEntry.region("+1"))
        assertNull(PhoneEntry.region("+"))
    }

    @Test
    fun `possible numbers can be sent and stubs cannot`() {
        assertTrue(PhoneEntry.isPossible("+79123456789"))
        assertFalse(PhoneEntry.isPossible("+7912"))
        assertFalse(PhoneEntry.isPossible("+"))
    }

    @Test
    fun `the field starts with the country's code`() {
        assertEquals("+7", PhoneEntry.startingValue("ru"))
        assertEquals("+44", PhoneEntry.startingValue("GB"))
        assertEquals("+", PhoneEntry.startingValue(null))
        assertEquals("+", PhoneEntry.startingValue("XX"))
    }

    @Test
    fun `picking a country keeps the number typed after the code`() {
        val uk = PhoneEntry.countries(Locale.ENGLISH).first { it.region == "GB" }
        assertEquals("+449123456789", PhoneEntry.withCountry("+79123456789", uk))
        assertEquals("+44", PhoneEntry.withCountry("+", uk))
    }

    @Test
    fun `flags are regional letters`() {
        assertEquals("🇷🇺", PhoneEntry.flag("RU"))
        assertEquals("🇬🇧", PhoneEntry.flag("gb"))
        assertEquals("", PhoneEntry.flag("001"))
    }

    @Test
    fun `the country list is named and complete enough to pick from`() {
        val countries = PhoneEntry.countries(Locale.ENGLISH)
        assertTrue(countries.size > 200)
        val russia = countries.first { it.region == "RU" }
        assertEquals("Russia", russia.name)
        assertEquals(7, russia.callingCode)
        assertEquals("🇷🇺", russia.flag)
    }

    @Test
    fun `the caret moves between typed and drawn text by digits`() {
        val raw = "+79123456789"
        val drawn = PhoneEntry.format(raw) // +7 912 345-67-89
        assertEquals("after the plus", 1, PhoneEntry.toFormatted(raw, drawn, 1))
        assertEquals("after the 7", 2, PhoneEntry.toFormatted(raw, drawn, 2))
        assertEquals("after 912", 6, PhoneEntry.toFormatted(raw, drawn, 5))
        assertEquals("at the end", drawn.length, PhoneEntry.toFormatted(raw, drawn, raw.length))
        for (offset in 0..raw.length) {
            val there = PhoneEntry.toFormatted(raw, drawn, offset)
            assertEquals("round trip at $offset", offset, PhoneEntry.toRaw(raw, drawn, there))
        }
    }

    @Test
    fun `the code's route is said in words`() {
        assertEquals(
            "We've sent an SMS with the code to +7 912 345-67-89",
            codeDeliveryText("authenticationCodeTypeSms", "+79123456789")
        )
        assertEquals(
            "We've sent the code to Telegram on your other device",
            codeDeliveryText("authenticationCodeTypeTelegramMessage", "+79123456789")
        )
        assertTrue(codeDeliveryText("somethingNew", "").contains("your phone"))
    }

    @Test
    fun `countdowns read as minutes and seconds`() {
        assertEquals("0:25", countdownLabel(25))
        assertEquals("1:05", countdownLabel(65))
        assertEquals("0:00", countdownLabel(-3))
    }
}
