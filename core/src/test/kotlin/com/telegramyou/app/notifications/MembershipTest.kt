package com.telegramyou.app.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MembershipTest {

    @Test
    fun `a discussion group only looked at is not the account's`() {
        assertFalse(isAccountsChat("chatTypeSupergroup", "chatMemberStatusLeft", isMember = false, listed = false))
        assertFalse(isAccountsChat("chatTypeSupergroup", "chatMemberStatusBanned", isMember = false, listed = false))
    }

    @Test
    fun `a joined group or channel is`() {
        assertTrue(isAccountsChat("chatTypeSupergroup", "chatMemberStatusMember", isMember = false, listed = true))
        assertTrue(isAccountsChat("chatTypeSupergroup", "chatMemberStatusAdministrator", isMember = false, listed = true))
        assertTrue(isAccountsChat("chatTypeSupergroup", "chatMemberStatusCreator", isMember = false, listed = true))
    }

    @Test
    fun `a restricted member counts only while a member`() {
        assertTrue(isAccountsChat("chatTypeSupergroup", "chatMemberStatusRestricted", isMember = true, listed = true))
        assertFalse(isAccountsChat("chatTypeSupergroup", "chatMemberStatusRestricted", isMember = false, listed = false))
    }

    @Test
    fun `without a status the list decides`() {
        assertTrue(isAccountsChat("chatTypeSupergroup", null, isMember = false, listed = true))
        assertFalse(isAccountsChat("chatTypeSupergroup", null, isMember = false, listed = false))
        assertFalse(isAccountsChat(null, null, isMember = false, listed = false))
    }

    @Test
    fun `a person's first message counts before the chat is listed`() {
        assertTrue(isAccountsChat("chatTypePrivate", null, isMember = false, listed = false))
        assertTrue(isAccountsChat("chatTypeSecret", null, isMember = false, listed = false))
    }
}
