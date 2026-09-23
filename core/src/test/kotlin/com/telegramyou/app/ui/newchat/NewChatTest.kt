package com.telegramyou.app.ui.newchat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewChatTest {

    @Test
    fun `a name is needed before anything can be created`() {
        assertFalse(canCreate(""))
        assertFalse(canCreate("   "))
        assertTrue(canCreate("Design crit"))
    }

    @Test
    fun `an empty name disables the button without an error under the field`() {
        assertNull(titleProblem(""))
        assertNull(titleProblem("   "))
    }

    @Test
    fun `a name over the limit is refused, and says why`() {
        val long = "x".repeat(MAX_CHAT_TITLE + 1)
        assertFalse(canCreate(long))
        assertEquals("At most $MAX_CHAT_TITLE characters", titleProblem(long))
    }

    @Test
    fun `the limit is measured after trimming`() {
        val exactly = " " + "x".repeat(MAX_CHAT_TITLE) + " "
        assertTrue(canCreate(exactly))
        assertNull(titleProblem(exactly))
    }

    @Test
    fun `a channel description has its own limit`() {
        assertTrue(canCreate("News", "x".repeat(MAX_CHAT_DESCRIPTION)))
        assertFalse(canCreate("News", "x".repeat(MAX_CHAT_DESCRIPTION + 1)))
    }

    @Test
    fun `every spelling of an invite link comes out the same`() {
        val canonical = "https://t.me/+AbCd_12-xyz"
        listOf(
            "https://t.me/+AbCd_12-xyz",
            "t.me/+AbCd_12-xyz",
            "  http://t.me/+AbCd_12-xyz/  ",
            "https://telegram.me/+AbCd_12-xyz",
            "https://www.t.me/+AbCd_12-xyz",
            "https://t.me/joinchat/AbCd_12-xyz",
            "tg://join?invite=AbCd_12-xyz"
        ).forEach { spelling ->
            assertEquals(spelling, canonical, canonicalInviteLink(spelling))
        }
    }

    @Test
    fun `a public username is not an invite link`() {
        assertNull(canonicalInviteLink("https://t.me/telegram"))
        assertNull(canonicalInviteLink("@telegram"))
    }

    @Test
    fun `things that are not links at all are not links`() {
        assertNull(canonicalInviteLink(""))
        assertNull(canonicalInviteLink("hello"))
        assertNull(canonicalInviteLink("https://example.com/+AbCd1234"))
        // Too short to be a hash; it is somebody's half-finished paste.
        assertNull(canonicalInviteLink("t.me/+Ab"))
    }
}
