package com.telegramyou.app.telegram.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupsTest {

    private fun member(id: Long, role: MemberRole, title: String = "", editable: Boolean = false) =
        GroupMember(TelegramUser(id, "Person $id"), role, title, editable)

    @Test
    fun `nothing to do to oneself or to the owner`() {
        assertEquals(emptyList<MemberAction>(), memberActions(GroupRights.All, member(1, MemberRole.Member), selfId = 1))
        assertEquals(emptyList<MemberAction>(), memberActions(GroupRights.All, member(2, MemberRole.Owner), selfId = 1))
    }

    @Test
    fun `a plain member can be promoted, restricted or removed by a full admin`() {
        assertEquals(
            listOf(MemberAction.MakeAdmin, MemberAction.Restrict, MemberAction.Remove),
            memberActions(GroupRights.All, member(2, MemberRole.Member), selfId = 1)
        )
    }

    @Test
    fun `a basic group has no restricting`() {
        assertEquals(
            listOf(MemberAction.MakeAdmin, MemberAction.Remove),
            memberActions(GroupRights.All, member(2, MemberRole.Member), selfId = 1, isBasicGroup = true)
        )
    }

    @Test
    fun `an admin is dismissed only where theirs to dismiss, and never removed first`() {
        assertEquals(
            listOf(MemberAction.RemoveAdmin),
            memberActions(GroupRights.All, member(2, MemberRole.Admin, editable = true), selfId = 1)
        )
        assertEquals(
            emptyList<MemberAction>(),
            memberActions(GroupRights.All, member(2, MemberRole.Admin, editable = false), selfId = 1)
        )
    }

    @Test
    fun `a restricted member is let back or removed`() {
        assertEquals(
            listOf(MemberAction.Unrestrict, MemberAction.Remove),
            memberActions(GroupRights.All, member(2, MemberRole.Restricted), selfId = 1)
        )
    }

    @Test
    fun `a plain member may do nothing to anybody`() {
        assertEquals(emptyList<MemberAction>(), memberActions(GroupRights.None, member(2, MemberRole.Member), selfId = 1))
        assertFalse(GroupRights.None.isAdmin)
        assertTrue(GroupRights(canPinMessages = true).isAdmin)
    }

    @Test
    fun `roles read as titles first`() {
        assertEquals("Owner", memberRoleLabel(member(1, MemberRole.Owner)))
        assertEquals("Design lead", memberRoleLabel(member(1, MemberRole.Admin, title = "Design lead")))
        assertEquals("Admin", memberRoleLabel(member(1, MemberRole.Admin)))
        assertEquals("Can't write", memberRoleLabel(member(1, MemberRole.Restricted)))
        assertNull(memberRoleLabel(member(1, MemberRole.Member)))
    }

    @Test
    fun `the owner and admins come first, in the server's order otherwise`() {
        val list = listOf(
            member(1, MemberRole.Member),
            member(2, MemberRole.Admin),
            member(3, MemberRole.Owner),
            member(4, MemberRole.Member),
            member(5, MemberRole.Admin)
        )
        assertEquals(listOf(3L, 2L, 5L, 1L, 4L), sortedForList(list).map { it.user.id })
    }

    @Test
    fun `media and polls need messages, and bring them back`() {
        val all = GroupPermissions()
        val silent = GroupPermission.SendMessages.set(all, false)
        assertFalse(silent.sendMessages || silent.sendMedia || silent.sendPolls)
        val media = GroupPermission.SendMedia.set(silent, true)
        assertTrue(media.sendMessages && media.sendMedia)
        assertFalse(media.sendPolls)
        assertEquals(all.copy(pinMessages = true), GroupPermission.PinMessages.set(all, true))
    }

    @Test
    fun `topics are a switch only in a forum`() {
        assertFalse(GroupPermission.CreateTopics in GroupPermission.shownFor(isForum = false))
        assertTrue(GroupPermission.CreateTopics in GroupPermission.shownFor(isForum = true))
    }

    @Test
    fun `a link says who joined and what is left`() {
        val now = 1_000_000L
        assertEquals("No one joined yet", inviteLinkSummary(InviteLink("l"), now))
        assertEquals(
            "3 joined · 7 left · expires in 2 days",
            inviteLinkSummary(InviteLink("l", memberCount = 3, memberLimit = 10, expiresAt = now + 2 * 86_400 + 5), now)
        )
        assertEquals("1 joined · expires in 1 hour", inviteLinkSummary(InviteLink("l", memberCount = 1, expiresAt = now + 3_700), now))
        assertEquals("2 joined · expired", inviteLinkSummary(InviteLink("l", memberCount = 2, expiresAt = now - 1), now))
        assertEquals("5 joined · no places left", inviteLinkSummary(InviteLink("l", memberCount = 5, memberLimit = 5), now))
        assertEquals("No one joined yet · revoked", inviteLinkSummary(InviteLink("l", isRevoked = true), now))
    }

    @Test
    fun `a link's expiry counts from now, and never is zero`() {
        assertEquals(1_003_600L, LinkExpiry.Hour.from(1_000_000))
        assertEquals(0L, LinkExpiry.Never.from(1_000_000))
    }

    @Test
    fun `General first, then pinned topics`() {
        val topics = listOf(
            ForumTopic(5, "Bugs"),
            ForumTopic(3, "Releases", isPinned = true),
            ForumTopic(1, "General", isGeneral = true),
            ForumTopic(7, "Ideas")
        )
        assertEquals(listOf(1, 3, 5, 7), sortedTopics(topics).map { it.id })
    }
}
