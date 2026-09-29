package com.telegramyou.app.telegram.groups

import com.telegramyou.app.telegram.model.ForumTopic
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
    suspend fun createInviteLink(chatId: Long, name: String, expiresAt: Long, memberLimit: Int): InviteLink? = null

    /** Stops [link] working. The primary one is replaced by a new one as it goes. */
    suspend fun revokeInviteLink(chatId: Long, link: String) {}

    /** A forum's topics, General among them. */
    suspend fun forumTopics(chatId: Long): List<ForumTopic> = emptyList()

    /** Starts a topic called [name] and answers with it. */
    suspend fun createForumTopic(chatId: Long, name: String): ForumTopic? = null

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
}
