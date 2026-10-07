package com.telegramyou.app.telegram.groups

import com.telegramyou.app.telegram.model.CommentThread
import com.telegramyou.app.telegram.model.AdminRights
import com.telegramyou.app.telegram.model.ForumTopic
import com.telegramyou.app.telegram.model.GroupMember
import com.telegramyou.app.telegram.model.JoinRequest
import com.telegramyou.app.telegram.model.GroupManagement
import com.telegramyou.app.telegram.model.GroupPermissions
import com.telegramyou.app.telegram.model.InviteLink
import com.telegramyou.app.telegram.model.MemberAction

/**
 * Running a group: its admins and what members may do, its invite links,
 * and a forum's topics. Everything here the server refuses to anybody
 * without the right; the screens only offer what [GroupManagement.rights]
 * says this account has, so a refusal is the rare case and is said as an
 * error, not prevented twice.
 */
interface TelegramGroups {

    /**
     * This account's rights in [chatId], its members with their standing and
     * what members may do; null for a chat that is not a group.
     */
    suspend fun groupManagement(chatId: Long): GroupManagement? = null

    /** Does [action] to [userId] in [chatId]. */
    suspend fun applyMemberAction(chatId: Long, userId: Long, action: MemberAction) {}

    /**
     * Makes [userId] an admin of [chatId] with [rights] — or changes an
     * admin's — and gives them [title], which may be empty.
     */
    suspend fun promoteMember(chatId: Long, userId: Long, rights: AdminRights, title: String) {}

    /**
     * Members of [chatId] whose name or username matches [query], from the
     * server: a big group lists only its recent members, and the one being
     * looked for is often not among them.
     */
    suspend fun searchGroupMembers(chatId: Long, query: String): List<GroupMember> = emptyList()

    /** People asking to join [chatId] through a link that asks first. */
    suspend fun joinRequests(chatId: Long): List<JoinRequest> = emptyList()

    /** Lets [userId] in, or turns them away. */
    suspend fun processJoinRequest(chatId: Long, userId: Long, approve: Boolean) {}

    /** What every member of [chatId] may do from now on. */
    suspend fun setGroupPermissions(chatId: Long, permissions: GroupPermissions) {}

    /**
     * This account's links into [chatId]: the primary one and every other,
     * working ones first, then the revoked.
     */
    suspend fun inviteLinks(chatId: Long): List<InviteLink> = emptyList()

    /**
     * A new link into [chatId] — named, lasting until [expiresAt] (epoch
     * seconds, 0 for ever) and for up to [memberLimit] people (0 for any).
     */
    suspend fun createInviteLink(
        chatId: Long,
        name: String,
        expiresAt: Long,
        memberLimit: Int,
        /** Whoever uses it asks, and an admin lets them in; no limit then. */
        createsJoinRequest: Boolean = false
    ): InviteLink? = null

    /** Stops [link] working. The primary one is replaced by a new one as it goes. */
    suspend fun revokeInviteLink(chatId: Long, link: String) {}

    /** A forum's topics, General among them. */
    suspend fun forumTopics(chatId: Long): List<ForumTopic> = emptyList()

    /** Starts a topic called [name] and answers with it. */
    suspend fun createForumTopic(chatId: Long, name: String): ForumTopic? = null

    suspend fun renameForumTopic(chatId: Long, topicId: Int, name: String) {}

    /** Closes a topic to new messages, or opens it again. */
    suspend fun setForumTopicClosed(chatId: Long, topicId: Int, closed: Boolean) {}

    /** Deletes a topic and everything in it. */
    suspend fun deleteForumTopic(chatId: Long, topicId: Int) {}

    /**
     * Keeps what was being written in one topic as that topic's draft —
     * its own, not the chat's, so it follows the person back to where it
     * was started and nowhere else.
     */
    suspend fun saveTopicDraft(chatId: Long, topicId: Int, text: String) {}

    /**
     * Which topic of forum [chatId] the conversation screen is showing, or
     * null when it shows none: while one is set, [chatId]'s history is that
     * topic's, and what is sent there goes into it.
     *
     * Set by the screen rather than passed to every send and every page of
     * history. There are a dozen of those, each written twice, and a forum
     * chat is only ever open on one topic at a time — the topic list is the
     * way between them — so the conversation says once where it is.
     */
    fun setOpenTopic(chatId: Long, topicId: Int?) {}

    /**
     * The screen on [topicId] has gone — but only if that is still the open
     * one: leaving a topic for another, the new screen says where it is
     * before the old one is torn down, and must not be undone by it.
     */
    fun closeOpenTopic(chatId: Long, topicId: Int) {}

    /**
     * Where a channel post's comments live (2.0): its discussion group and
     * the thread in it, or null for a post without them.
     */
    suspend fun commentThread(chatId: Long, messageId: Long): CommentThread? = null

    /**
     * Which comment thread of discussion group [chatId] the conversation
     * screen is showing — the same contract as [setOpenTopic], for comments.
     */
    fun setOpenThread(chatId: Long, threadId: Long?) {}

    /** The screen on [threadId] has gone, if that is still the open one. */
    fun closeOpenThread(chatId: Long, threadId: Long) {}
}
