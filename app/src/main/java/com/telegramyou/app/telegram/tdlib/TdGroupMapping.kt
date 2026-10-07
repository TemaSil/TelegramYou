package com.telegramyou.app.telegram.tdlib

import com.telegramyou.app.telegram.model.AdminRights
import com.telegramyou.app.telegram.model.GroupPermissions
import com.telegramyou.app.telegram.model.GroupRights
import com.telegramyou.app.telegram.model.InviteLink
import com.telegramyou.app.telegram.model.MemberRole
import org.json.JSONObject

/*
 * TDLib's group objects — member statuses, administrator rights, member
 * permissions, invite links — read into the model, and the requests that
 * go back. Pure, like TdMapping: functions of what they are handed.
 */

/**
 * `chatPermissions` read into the screen's switches. Media is on only when
 * every kind of it is, since the one switch stands for all of them.
 */
internal fun permissionsOf(json: JSONObject?): GroupPermissions {
    json ?: return GroupPermissions()
    val media = MEDIA_PERMISSIONS.all { json.optBoolean(it) }
    return GroupPermissions(
        sendMessages = json.optBoolean("can_send_basic_messages"),
        sendMedia = media,
        sendPolls = json.optBoolean("can_send_polls"),
        addMembers = json.optBoolean("can_invite_users"),
        pinMessages = json.optBoolean("can_pin_messages"),
        changeInfo = json.optBoolean("can_change_info"),
        createTopics = json.optBoolean("can_create_topics")
    )
}

/**
 * The switches written back as `chatPermissions`. Link previews and
 * reactions are not on the screen; they follow messages, which is what the
 * official client does with them when a group turns writing off.
 */
internal fun GroupPermissions.toJson(): JSONObject = JSONObject()
    .put("@type", "chatPermissions")
    .put("can_send_basic_messages", sendMessages)
    .apply { MEDIA_PERMISSIONS.forEach { put(it, sendMedia) } }
    .put("can_send_polls", sendPolls)
    .put("can_add_link_previews", sendMessages)
    .put("can_react_to_messages", true)
    .put("can_edit_tag", false)
    .put("can_change_info", changeInfo)
    .put("can_invite_users", addMembers)
    .put("can_pin_messages", pinMessages)
    .put("can_create_topics", createTopics)

/** Everything `chatPermissions` calls media, which the screen folds into one switch. */
private val MEDIA_PERMISSIONS = listOf(
    "can_send_audios",
    "can_send_documents",
    "can_send_photos",
    "can_send_videos",
    "can_send_video_notes",
    "can_send_voice_notes",
    "can_send_other_messages"
)

/** A member who may not write at all — what "Don't let them write" sets. */
internal fun silencedPermissions(): JSONObject = GroupPermissions(
    sendMessages = false,
    sendMedia = false,
    sendPolls = false,
    addMembers = false,
    pinMessages = false,
    changeInfo = false,
    createTopics = false
).toJson().put("can_react_to_messages", false)

/**
 * This account's rights, from its own `ChatMemberStatus` in the group. The
 * owner has all of them, and so does any admin of a basic group, where
 * Telegram does not split them.
 */
internal fun rightsOf(status: JSONObject?, isBasicGroup: Boolean): GroupRights =
    when (status?.optString("@type")) {
        "chatMemberStatusCreator" -> GroupRights.All
        "chatMemberStatusAdministrator" -> if (isBasicGroup) {
            GroupRights.All
        } else {
            val rights = status.optJSONObject("rights")
            GroupRights(
                canChangeInfo = rights?.optBoolean("can_change_info") == true,
                canInviteUsers = rights?.optBoolean("can_invite_users") == true,
                canRestrictMembers = rights?.optBoolean("can_restrict_members") == true,
                canPromoteMembers = rights?.optBoolean("can_promote_members") == true,
                canPinMessages = rights?.optBoolean("can_pin_messages") == true,
                canManageTopics = rights?.optBoolean("can_manage_topics") == true
            )
        }
        else -> GroupRights.None
    }

/** Where a member stands; null for somebody who has left or was removed. */
internal fun roleOf(status: JSONObject?): MemberRole? = when (status?.optString("@type")) {
    "chatMemberStatusCreator" -> if (status.optBoolean("is_member", true)) MemberRole.Owner else null
    "chatMemberStatusAdministrator" -> MemberRole.Admin
    "chatMemberStatusMember" -> MemberRole.Member
    "chatMemberStatusRestricted" -> {
        // Restricted but still able to write is a member with a limit the
        // screen does not show; only the silenced read as restricted.
        val writes = status.optJSONObject("permissions")?.optBoolean("can_send_basic_messages") == true
        when {
            !status.optBoolean("is_member") -> null
            writes -> MemberRole.Member
            else -> MemberRole.Restricted
        }
    }
    else -> null
}

/**
 * An admin's rights as `chatAdministratorRights`. Managing the chat and
 * its video chats go with any of them, as Telegram requires of an admin;
 * the rest are the editor's switches.
 */
internal fun AdminRights.toJson(): JSONObject = JSONObject()
    .put("@type", "chatAdministratorRights")
    .put("can_manage_chat", true)
    .put("can_change_info", changeInfo)
    .put("can_delete_messages", deleteMessages)
    .put("can_invite_users", inviteUsers)
    .put("can_restrict_members", restrictMembers)
    .put("can_pin_messages", pinMessages)
    .put("can_manage_topics", manageTopics)
    .put("can_manage_video_chats", true)
    .put("can_promote_members", promoteMembers)
    .put("is_anonymous", false)

/** An admin's rights read back; a basic group's admins have all of them. */
internal fun adminRightsOf(status: JSONObject?, isBasicGroup: Boolean): AdminRights {
    if (isBasicGroup) return AdminRights(promoteMembers = true)
    val rights = status?.optJSONObject("rights") ?: return AdminRights()
    return AdminRights(
        changeInfo = rights.optBoolean("can_change_info"),
        deleteMessages = rights.optBoolean("can_delete_messages"),
        restrictMembers = rights.optBoolean("can_restrict_members"),
        inviteUsers = rights.optBoolean("can_invite_users"),
        pinMessages = rights.optBoolean("can_pin_messages"),
        manageTopics = rights.optBoolean("can_manage_topics"),
        promoteMembers = rights.optBoolean("can_promote_members")
    )
}

/** A draft as `setChatDraftMessage` takes it; none for an empty field. */
internal fun draftOf(text: String): Any = if (text.isBlank()) {
    JSONObject.NULL
} else {
    JSONObject()
        .put("@type", "draftMessage")
        .put(
            "input_message_text",
            JSONObject()
                .put("@type", "inputMessageText")
                .put("text", JSONObject().put("@type", "formattedText").put("text", text))
        )
}

/** A `chatInviteLink`, read. */
internal fun inviteLinkOf(json: JSONObject): InviteLink = InviteLink(
    link = json.optString("invite_link"),
    name = json.optString("name"),
    expiresAt = json.optLong("expiration_date"),
    memberLimit = json.optInt("member_limit"),
    memberCount = json.optInt("member_count"),
    isPrimary = json.optBoolean("is_primary"),
    isRevoked = json.optBoolean("is_revoked"),
    createsJoinRequest = json.optBoolean("creates_join_request"),
    pendingRequests = json.optInt("pending_join_request_count")
)

/**
 * The topic a message was written in: a forum's `messageTopicForum`, and 0
 * for anything else — a reply thread, Saved Messages' own topics.
 */
internal fun topicIdOf(message: JSONObject): Int {
    val topic = message.optJSONObject("topic_id") ?: return 0
    return if (topic.optString("@type") == "messageTopicForum") topic.optInt("forum_topic_id") else 0
}

/** The comment thread [message] was written in, in a discussion group; 0 otherwise. */
internal fun threadIdOf(message: JSONObject): Long {
    val topic = message.optJSONObject("topic_id") ?: return 0
    return if (topic.optString("@type") == "messageTopicThread") topic.optLong("message_thread_id") else 0
}

/** A comment thread as a send addresses it. */
internal fun commentTopic(threadId: Long): JSONObject = JSONObject()
    .put("@type", "messageTopicThread")
    .put("message_thread_id", threadId)

/**
 * A channel post's comment count, or null without a comments section:
 * TDLib gives reply_info only to posts of a channel with a discussion group.
 */
internal fun commentCountOf(message: JSONObject): Int? {
    if (!message.optBoolean("is_channel_post")) return null
    val replies = message.optJSONObject("interaction_info")?.optJSONObject("reply_info") ?: return null
    return replies.optInt("reply_count")
}

/** A forum topic as a send addresses it. */
internal fun forumTopic(topicId: Int): JSONObject = JSONObject()
    .put("@type", "messageTopicForum")
    .put("forum_topic_id", topicId)
