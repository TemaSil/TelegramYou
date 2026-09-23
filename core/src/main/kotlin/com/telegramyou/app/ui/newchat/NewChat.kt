package com.telegramyou.app.ui.newchat

/**
 * Making a group or a channel, and getting into one somebody else made.
 *
 * Pure, because both halves have rules that are easy to get quietly wrong in
 * a composable: a name made of spaces that the server refuses with an error
 * attached to no field, and invite links that arrive in four spellings, only
 * one of which the rest of the app should ever see.
 */

/** Which of the two a creation screen is making. */
enum class NewChatKind { Group, Channel }

/** Telegram's own limit on a chat's title. */
const val MAX_CHAT_TITLE = 128

/** And on a channel's description. */
const val MAX_CHAT_DESCRIPTION = 255

/**
 * What is wrong with a title, or null when nothing is.
 *
 * Trimmed before it is judged: a name of three spaces is empty to the server,
 * and a screen that let it through would get back an error with no field to
 * point at. Null for an untouched field too — see [canCreate] for why the
 * button waits without the field shouting.
 */
fun titleProblem(title: String): String? =
    if (title.trim().length > MAX_CHAT_TITLE) "At most $MAX_CHAT_TITLE characters" else null

/**
 * Whether the create button does anything.
 *
 * A group may start with nobody else in it — TDLib allows it, and a person
 * setting up a group before inviting anyone is a real case — so the only
 * rule is a name that is there and not too long. An empty name disables the
 * button rather than raising an error under the field: nothing has been done
 * wrong yet, and red text on a form nobody has typed into is an accusation.
 */
fun canCreate(title: String, description: String = ""): Boolean {
    val trimmed = title.trim()
    return trimmed.isNotEmpty() &&
        trimmed.length <= MAX_CHAT_TITLE &&
        description.length <= MAX_CHAT_DESCRIPTION
}

/**
 * An invite link in the one spelling the app uses, or null if this is not one.
 *
 * Telegram hands these out as `https://t.me/+HASH`, older clients as
 * `t.me/joinchat/HASH`, the app itself as `tg://join?invite=HASH`, and people
 * paste them with or without the scheme, with `telegram.me` instead of
 * `t.me`, and with a stray space at either end. All of them are the same
 * link; the canonical form is what TDLib is handed and what is shown back.
 *
 * A public username (`t.me/somegroup`) is not an invite link and answers
 * null: joining a public chat is a different request, and pretending otherwise
 * would send a username where a hash belongs.
 */
fun canonicalInviteLink(text: String): String? {
    val link = text.trim()
    val hash = INVITE_PATTERNS.firstNotNullOfOrNull { pattern ->
        pattern.matchEntire(link)?.groupValues?.get(1)
    } ?: return null
    return "https://t.me/+$hash"
}

private const val HASH = "([A-Za-z0-9_-]{4,})"

private val INVITE_PATTERNS = listOf(
    Regex("""(?i)(?:https?://)?(?:www\.)?(?:t|telegram)\.me/\+$HASH/?"""),
    Regex("""(?i)(?:https?://)?(?:www\.)?(?:t|telegram)\.me/joinchat/$HASH/?"""),
    Regex("""(?i)tg://join\?invite=$HASH""")
)
