package com.telegramyou.app.telegram.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelPostingTest {

    private fun standing(type: String?, canPost: Boolean = false, member: Boolean = true) =
        channelStanding(type, canPost, member)

    @Test
    fun `the owner posts`() {
        assertEquals(ChannelStanding.Posts, standing("chatMemberStatusCreator"))
    }

    @Test
    fun `an owner who left is outside`() {
        assertEquals(ChannelStanding.Outside, standing("chatMemberStatusCreator", member = false))
    }

    @Test
    fun `an admin posts only with the right to`() {
        assertEquals(ChannelStanding.Posts, standing("chatMemberStatusAdministrator", canPost = true))
        assertEquals(ChannelStanding.Reads, standing("chatMemberStatusAdministrator", canPost = false))
    }

    @Test
    fun `a subscriber reads`() {
        assertEquals(ChannelStanding.Reads, standing("chatMemberStatusMember"))
    }

    @Test
    fun `someone who left, or never joined, is outside`() {
        assertEquals(ChannelStanding.Outside, standing("chatMemberStatusLeft"))
        assertEquals(ChannelStanding.Outside, standing("chatMemberStatusRestricted", member = false))
    }

    @Test
    fun `banned reads, with nothing to join`() {
        assertEquals(ChannelStanding.Reads, standing("chatMemberStatusBanned"))
    }

    @Test
    fun `not known yet counts as posting`() {
        assertEquals(ChannelStanding.Posts, standing(null))
    }
}
