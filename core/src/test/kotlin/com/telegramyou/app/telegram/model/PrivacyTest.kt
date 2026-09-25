package com.telegramyou.app.telegram.model

import com.telegramyou.app.settings.TextSize
import org.junit.Assert.assertEquals
import org.junit.Test

class PrivacyTest {

    private fun rule(type: String, count: Int = 0) = Triple("userPrivacySettingRule$type", count, "{$type}")

    @Test
    fun `the audience is read from the whole-audience rules`() {
        assertEquals(PrivacyAudience.Everybody, privacyRulesOf(listOf(rule("AllowAll"))).audience)
        assertEquals(
            PrivacyAudience.Contacts,
            privacyRulesOf(listOf(rule("AllowContacts"), rule("RestrictAll"))).audience
        )
        assertEquals(PrivacyAudience.Nobody, privacyRulesOf(listOf(rule("RestrictAll"))).audience)
        assertEquals("nothing at all refuses everyone", PrivacyAudience.Nobody, privacyRulesOf(emptyList()).audience)
    }

    @Test
    fun `rules naming people are kept as exceptions, in order and untouched`() {
        val rules = privacyRulesOf(
            listOf(rule("AllowUsers", 3), rule("RestrictChatMembers", 1), rule("AllowContacts"), rule("RestrictAll"))
        )
        assertEquals(
            listOf(
                PrivacyException(allow = true, count = 3, raw = "{AllowUsers}"),
                PrivacyException(allow = false, count = 1, raw = "{RestrictChatMembers}")
            ),
            rules.exceptions
        )
    }

    @Test
    fun `the summary counts exceptions each way`() {
        assertEquals("My contacts", PrivacyRules(PrivacyAudience.Contacts).summary)
        assertEquals(
            "Everybody (−2)",
            PrivacyRules(PrivacyAudience.Everybody, listOf(PrivacyException(false, 2, ""))).summary
        )
        assertEquals(
            "Nobody (+3, −1)",
            PrivacyRules(
                PrivacyAudience.Nobody,
                listOf(PrivacyException(true, 3, ""), PrivacyException(false, 1, ""))
            ).summary
        )
    }

    @Test
    fun `each audience is written the way Telegram writes it`() {
        assertEquals(listOf("userPrivacySettingRuleAllowAll"), audienceRules(PrivacyAudience.Everybody))
        assertEquals(
            listOf("userPrivacySettingRuleAllowContacts", "userPrivacySettingRuleRestrictAll"),
            audienceRules(PrivacyAudience.Contacts)
        )
        assertEquals(listOf("userPrivacySettingRuleRestrictAll"), audienceRules(PrivacyAudience.Nobody))
    }

    @Test
    fun `text size settles on its nearest step`() {
        assertEquals(1f, TextSize.nearest(1.04f))
        assertEquals(1.3f, TextSize.nearest(2f))
        assertEquals("Large", TextSize.label(1.15f))
        assertEquals("Small", TextSize.label(0.5f))
    }
}
