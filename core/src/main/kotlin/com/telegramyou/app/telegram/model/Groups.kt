package com.telegramyou.app.telegram.model

/**
 * Running a group: who is in charge of it, what everybody else may do, the
 * links that let people in, and — in a forum — its topics.
 *
 * Everything here is what the screens decide from; the backends fill it from
 * TDLib's `chatMember`, `chatPermissions`, `chatInviteLink` and `forumTopic`.
 */

/** Where somebody stands in a group. */
enum class MemberRole {
    /** Made the group. Nobody can change what the owner may do. */
    Owner,
    Admin,
    Member,
    /** A member who may not write — Telegram's "restricted". */
    Restricted
}

/** One person in a group, with their standing in it. */
data class GroupMember(
    val user: TelegramUser,
    val role: MemberRole,
    /** An admin's own title — "Moderator", "Design lead" — or empty. */
    val title: String = "",
    /**
     * Whether this account may change an admin's standing. Telegram answers
     * it per admin: one promoted by somebody else is not yours to demote.
     * Irrelevant for anybody who is not an admin.
     */
    val canBeEdited: Boolean = false,
    /** An admin's rights; null for anybody who is not one. */
    val adminRights: AdminRights? = null
)

/**
 * What an admin may do, as the rights editor shows it — Telegram's
 * `chatAdministratorRights`, the ones a group has. The defaults are the
 * official client's for a new admin: everything but making more admins,
 * which stays with whoever chose to hand it on.
 */
data class AdminRights(
    val changeInfo: Boolean = true,
    val deleteMessages: Boolean = true,
    val restrictMembers: Boolean = true,
    val inviteUsers: Boolean = true,
    val pinMessages: Boolean = true,
    val manageTopics: Boolean = true,
    val promoteMembers: Boolean = false
)

/** One switch in the rights editor, and how it reads. */
enum class AdminRight(val label: String) {
    ChangeInfo("Change group info"),
    DeleteMessages("Delete messages"),
    RestrictMembers("Ban and restrict members"),
    InviteUsers("Invite people"),
    PinMessages("Pin messages"),
    ManageTopics("Manage topics"),
    PromoteMembers("Add new admins");

    fun isOn(rights: AdminRights): Boolean = when (this) {
        ChangeInfo -> rights.changeInfo
        DeleteMessages -> rights.deleteMessages
        RestrictMembers -> rights.restrictMembers
        InviteUsers -> rights.inviteUsers
        PinMessages -> rights.pinMessages
        ManageTopics -> rights.manageTopics
        PromoteMembers -> rights.promoteMembers
    }

    fun set(rights: AdminRights, on: Boolean): AdminRights = when (this) {
        ChangeInfo -> rights.copy(changeInfo = on)
        DeleteMessages -> rights.copy(deleteMessages = on)
        RestrictMembers -> rights.copy(restrictMembers = on)
        InviteUsers -> rights.copy(inviteUsers = on)
        PinMessages -> rights.copy(pinMessages = on)
        ManageTopics -> rights.copy(manageTopics = on)
        PromoteMembers -> rights.copy(promoteMembers = on)
    }

    companion object {
        /**
         * The switches for an admin of this group: topics only in a forum,
         * and none at all in a basic group, where Telegram gives every
         * admin everything and only the title can be chosen.
         */
        fun shownFor(isForum: Boolean, isBasicGroup: Boolean): List<AdminRight> = when {
            isBasicGroup -> emptyList()
            else -> entries.filter { it != ManageTopics || isForum }
        }
    }
}

/** An admin's title as Telegram takes it: 16 characters, no emoji. */
const val ADMIN_TITLE_MAX = 16

/** [title] cut to what Telegram accepts. */
fun adminTitle(title: String): String =
    title.filter { Character.getType(it) != Character.SURROGATE.toInt() && Character.getType(it) != Character.OTHER_SYMBOL.toInt() }
        .trim()
        .take(ADMIN_TITLE_MAX)

/**
 * What this account may do to the group, as an admin — Telegram's
 * `chatAdministratorRights`, cut to what the screens offer. Everything is
 * false for a plain member.
 */
data class GroupRights(
    val canChangeInfo: Boolean = false,
    val canInviteUsers: Boolean = false,
    val canRestrictMembers: Boolean = false,
    val canPromoteMembers: Boolean = false,
    val canPinMessages: Boolean = false,
    val canManageTopics: Boolean = false
) {
    /** Anything an admin screen would offer. */
    val isAdmin: Boolean
        get() = canChangeInfo || canInviteUsers || canRestrictMembers || canPromoteMembers ||
            canPinMessages || canManageTopics

    companion object {
        val None = GroupRights()

        /** The owner's, and what a basic group gives any admin. */
        val All = GroupRights(
            canChangeInfo = true,
            canInviteUsers = true,
            canRestrictMembers = true,
            canPromoteMembers = true,
            canPinMessages = true,
            canManageTopics = true
        )
    }
}

/**
 * What every member may do unless an admin says otherwise — Telegram's
 * `chatPermissions`, grouped the way the official client's screen groups
 * them. [sendMedia] stands for all of photos, videos, files, music, voice
 * and round videos, stickers and GIFs: one switch, since a group that wants
 * photos and not videos is rare enough for the official client to fold them
 * too.
 */
data class GroupPermissions(
    val sendMessages: Boolean = true,
    val sendMedia: Boolean = true,
    val sendPolls: Boolean = true,
    val addMembers: Boolean = true,
    val pinMessages: Boolean = false,
    val changeInfo: Boolean = false,
    val createTopics: Boolean = true
)

/** One switch on the permissions screen, and how it reads. */
enum class GroupPermission(val label: String) {
    SendMessages("Send messages"),
    SendMedia("Send photos, videos and files"),
    SendPolls("Send polls"),
    AddMembers("Add members"),
    PinMessages("Pin messages"),
    ChangeInfo("Change group info"),
    CreateTopics("Create topics");

    fun isOn(permissions: GroupPermissions): Boolean = when (this) {
        SendMessages -> permissions.sendMessages
        SendMedia -> permissions.sendMedia
        SendPolls -> permissions.sendPolls
        AddMembers -> permissions.addMembers
        PinMessages -> permissions.pinMessages
        ChangeInfo -> permissions.changeInfo
        CreateTopics -> permissions.createTopics
    }

    /**
     * [permissions] with this switched to [on]. Media and polls depend on
     * being able to write at all, as they do in Telegram: turning messages
     * off turns them off too, and turning either on turns messages back on.
     */
    fun set(permissions: GroupPermissions, on: Boolean): GroupPermissions = when (this) {
        SendMessages -> if (on) {
            permissions.copy(sendMessages = true)
        } else {
            permissions.copy(sendMessages = false, sendMedia = false, sendPolls = false)
        }
        SendMedia -> permissions.copy(sendMedia = on, sendMessages = permissions.sendMessages || on)
        SendPolls -> permissions.copy(sendPolls = on, sendMessages = permissions.sendMessages || on)
        AddMembers -> permissions.copy(addMembers = on)
        PinMessages -> permissions.copy(pinMessages = on)
        ChangeInfo -> permissions.copy(changeInfo = on)
        CreateTopics -> permissions.copy(createTopics = on)
    }

    companion object {
        /** The switches a group shows; topics only where it is a forum. */
        fun shownFor(isForum: Boolean): List<GroupPermission> =
            entries.filter { it != CreateTopics || isForum }
    }
}

/**
 * A group as its info screen needs it for running it: this account's
 * rights, the members with their standing, and the members' permissions.
 */
data class GroupManagement(
    val rights: GroupRights,
    val members: List<GroupMember>,
    val permissions: GroupPermissions,
    /**
     * A basic group — Telegram's small kind — has no restricting: a member
     * there can only be removed. Every group becomes a supergroup the moment
     * something needs one, so this is the rare case, but a real one.
     */
    val isBasicGroup: Boolean = false,
    val isForum: Boolean = false
)

/** What can be done to one member, in the order a menu offers it. */
enum class MemberAction(val label: String) {
    MakeAdmin("Make admin"),
    EditAdmin("Edit admin rights"),
    RemoveAdmin("Dismiss as admin"),
    Restrict("Don't let them write"),
    Unrestrict("Let them write"),
    Remove("Remove from group")
}

/**
 * What this account, holding [rights], may do to [member]. Empty for itself
 * ([selfId]) and for the owner; Telegram refuses the rest anyway, and a menu
 * of refusals is worse than no menu.
 */
fun memberActions(
    rights: GroupRights,
    member: GroupMember,
    selfId: Long,
    isBasicGroup: Boolean = false
): List<MemberAction> {
    if (member.user.id == selfId || member.role == MemberRole.Owner) return emptyList()
    val actions = mutableListOf<MemberAction>()
    when (member.role) {
        MemberRole.Admin -> {
            if (rights.canPromoteMembers && member.canBeEdited) {
                actions += MemberAction.EditAdmin
                actions += MemberAction.RemoveAdmin
            }
        }
        MemberRole.Member -> {
            if (rights.canPromoteMembers) actions += MemberAction.MakeAdmin
            if (rights.canRestrictMembers && !isBasicGroup) actions += MemberAction.Restrict
        }
        MemberRole.Restricted -> {
            if (rights.canRestrictMembers) actions += MemberAction.Unrestrict
        }
        MemberRole.Owner -> Unit
    }
    // An admin is dismissed before being removed, as in Telegram.
    if (rights.canRestrictMembers && member.role != MemberRole.Admin) actions += MemberAction.Remove
    return actions
}

/** The word beside a member's name: their title, their role, or nothing. */
fun memberRoleLabel(member: GroupMember): String? = when (member.role) {
    MemberRole.Owner -> member.title.ifBlank { "Owner" }
    MemberRole.Admin -> member.title.ifBlank { "Admin" }
    MemberRole.Restricted -> "Can't write"
    MemberRole.Member -> null
}

/**
 * The owner first, then admins, then everybody else, keeping the server's
 * order inside each — the order Telegram's own list shows a group in.
 */
fun sortedForList(members: List<GroupMember>): List<GroupMember> =
    members.sortedBy { it.role.ordinal }

/** One of a group's invite links — Telegram's `chatInviteLink`. */
data class InviteLink(
    val link: String,
    /** What its maker called it, or empty. */
    val name: String = "",
    /** Epoch seconds it stops working at; 0 for never. */
    val expiresAt: Long = 0,
    /** How many may join through it; 0 for any number. */
    val memberLimit: Int = 0,
    val memberCount: Int = 0,
    /** The group's own link, the one the info screen shows. */
    val isPrimary: Boolean = false,
    val isRevoked: Boolean = false,
    /** Joining through it asks an admin first. */
    val createsJoinRequest: Boolean = false,
    /** Requests through it waiting for an admin. */
    val pendingRequests: Int = 0
)

/** Somebody asking to join a group, waiting on an admin. */
data class JoinRequest(
    val user: TelegramUser,
    /** Epoch seconds they asked at. */
    val date: Long,
    /** What they say about themselves, if anything. */
    val bio: String = ""
)

/** How long a new link lasts, as the create dialog offers it. */
enum class LinkExpiry(val label: String, val seconds: Long) {
    Hour("1 hour", 3_600),
    Day("1 day", 86_400),
    Week("1 week", 604_800),
    Never("Never", 0);

    /** Epoch seconds a link made at [nowSeconds] stops working; 0 for never. */
    fun from(nowSeconds: Long): Long = if (seconds == 0L) 0 else nowSeconds + seconds
}

/** How many may join through a new link, as the create dialog offers it. */
enum class LinkLimit(val label: String, val count: Int) {
    One("1", 1),
    Ten("10", 10),
    Hundred("100", 100),
    Unlimited("No limit", 0)
}

/**
 * The line under a link: who has joined through it, and what is left of it
 * — "3 joined · 7 left · expires in 2 days". A revoked or run-out link says
 * so instead of counting down.
 */
fun inviteLinkSummary(link: InviteLink, nowSeconds: Long): String {
    val parts = mutableListOf<String>()
    parts += when (link.memberCount) {
        0 -> "No one joined yet"
        1 -> "1 joined"
        else -> "${link.memberCount} joined"
    }
    if (link.pendingRequests > 0 && !link.isRevoked) {
        parts += if (link.pendingRequests == 1) "1 request" else "${link.pendingRequests} requests"
    }
    val expired = link.expiresAt in 1..nowSeconds
    val full = link.memberLimit > 0 && link.memberCount >= link.memberLimit
    when {
        link.isRevoked -> parts += "revoked"
        expired -> parts += "expired"
        full -> parts += "no places left"
        else -> {
            if (link.createsJoinRequest) parts += "admins approve"
            if (link.memberLimit > 0) parts += "${link.memberLimit - link.memberCount} left"
            if (link.expiresAt > 0) parts += "expires in ${durationWords(link.expiresAt - nowSeconds)}"
        }
    }
    return parts.joinToString(" · ")
}

/** A span of time as it is said, rounded down to its largest unit. */
private fun durationWords(seconds: Long): String {
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    return when {
        days >= 1 -> if (days == 1L) "1 day" else "$days days"
        hours >= 1 -> if (hours == 1L) "1 hour" else "$hours hours"
        minutes >= 1 -> if (minutes == 1L) "1 minute" else "$minutes minutes"
        else -> "less than a minute"
    }
}

/**
 * One topic of a forum — a supergroup whose conversation is split into
 * threads, each with its own name and messages. Telegram's `forumTopic`.
 */
data class ForumTopic(
    /** Telegram's forum_topic_id; General is always 1. */
    val id: Int,
    val name: String,
    /** The topic icon's colour, as Telegram gives it: RGB, no alpha. */
    val iconColor: Int = DEFAULT_TOPIC_COLOR,
    val lastMessage: String = "",
    val timestampLabel: String = "",
    val unreadCount: Int = 0,
    val isPinned: Boolean = false,
    /** Closed to new messages by an admin. */
    val isClosed: Boolean = false,
    /** The topic every forum has, where anything without a topic goes. */
    val isGeneral: Boolean = false,
    /** What this account had started writing there, or empty. */
    val draft: String = ""
)

/** What can be done to one topic, in the order its menu offers it. */
enum class TopicAction(val label: String) {
    Rename("Rename"),
    Close("Close topic"),
    Reopen("Reopen topic"),
    Delete("Delete topic")
}

/**
 * What an admin who may manage topics may do to [topic]. General can be
 * renamed and nothing else; Telegram keeps it open and never deletes it.
 */
fun topicActions(canManageTopics: Boolean, topic: ForumTopic): List<TopicAction> = when {
    !canManageTopics -> emptyList()
    topic.isGeneral -> listOf(TopicAction.Rename)
    else -> listOf(
        TopicAction.Rename,
        if (topic.isClosed) TopicAction.Reopen else TopicAction.Close,
        TopicAction.Delete
    )
}

/**
 * Members whose name or username has [query] in it, for the search over a
 * group's members. Blank finds everybody.
 */
fun matchingMembers(members: List<GroupMember>, query: String): List<GroupMember> {
    val needle = query.trim().removePrefix("@")
    if (needle.isEmpty()) return members
    return members.filter { member ->
        member.user.displayName.contains(needle, ignoreCase = true) ||
            member.user.username?.contains(needle, ignoreCase = true) == true
    }
}

/** Telegram's first topic colour, the blue it gives a topic by default. */
const val DEFAULT_TOPIC_COLOR = 0x6FB9F0

/**
 * The six colours Telegram lets a topic's icon take, in its order. A new
 * topic takes the next one round, so a list of them is not all one colour.
 */
val TOPIC_COLORS = listOf(0x6FB9F0, 0xFFD67E, 0xCB86DB, 0x8EEE98, 0xFF93B2, 0xFB6F5F)

/** Topics as the list shows them: General, then pinned, then the rest, keeping order within. */
fun sortedTopics(topics: List<ForumTopic>): List<ForumTopic> =
    topics.sortedBy {
        when {
            it.isGeneral -> 0
            it.isPinned -> 1
            else -> 2
        }
    }
