package com.telegramyou.app.telegram.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelPostingTest {

    @Test
    fun `the owner posts`() {
        assertTrue(channelCanPost("chatMemberStatusCreator", canPostMessages = false))
    }

    @Test
    fun `an admin posts only with the right to`() {
        assertTrue(channelCanPost("chatMemberStatusAdministrator", canPostMessages = true))
        assertFalse(channelCanPost("chatMemberStatusAdministrator", canPostMessages = false))
    }

    @Test
    fun `a subscriber, or someone who left or was banned, does not`() {
        assertFalse(channelCanPost("chatMemberStatusMember", canPostMessages = false))
        assertFalse(channelCanPost("chatMemberStatusLeft", canPostMessages = false))
        assertFalse(channelCanPost("chatMemberStatusBanned", canPostMessages = false))
        assertFalse(channelCanPost("chatMemberStatusRestricted", canPostMessages = false))
    }

    @Test
    fun `not known yet counts as able`() {
        assertTrue(channelCanPost(null, canPostMessages = false))
    }
}
