package com.telegramyou.app.ui.profile

import com.telegramyou.app.telegram.model.TelegramUser

/**
 * Editing your own name, bio and username.
 *
 * Pure, and in `:core` for the usual reason: these rules are Telegram's, they
 * fail silently when they are wrong — a username the server rejects comes back
 * as a generic error with no field attached — and no screenshot shows whether
 * they hold. A test does.
 *
 * The limits are the ones TDLib enforces on the far side. Checking them here
 * is not a substitute for the server's answer: it is what lets the screen say
 * *which* field is wrong before spending a round trip to be told that
 * something was.
 */

/** What is in the form, as typed. */
data class ProfileDraft(
    val firstName: String = "",
    val lastName: String = "",
    val bio: String = "",
    val username: String = ""
)

/** The form filled from the account it is editing. */
fun profileDraftOf(user: TelegramUser): ProfileDraft = ProfileDraft(
    firstName = user.firstName,
    lastName = user.lastName,
    bio = user.bio,
    // Null and blank both mean "no username", and the field shows an empty
    // box either way. Keeping the distinction here would make a draft built
    // from an account differ from the same draft typed by hand.
    username = user.username.orEmpty()
)

enum class ProfileField { FirstName, LastName, Bio, Username }

/** One field, and what is wrong with it, in words a person can act on. */
data class ProfileProblem(val field: ProfileField, val message: String)

const val MAX_NAME_LENGTH = 64
const val MAX_BIO_LENGTH = 70
const val MIN_USERNAME_LENGTH = 5
const val MAX_USERNAME_LENGTH = 32

/**
 * Everything wrong with [draft], or an empty list.
 *
 * Every problem rather than the first: a form that reports one error at a time
 * makes somebody submit four times to find out about four fields.
 */
fun validateProfile(draft: ProfileDraft): List<ProfileProblem> {
    val problems = mutableListOf<ProfileProblem>()

    val first = draft.firstName.trim()
    if (first.isEmpty()) {
        problems += ProfileProblem(ProfileField.FirstName, "A first name is required")
    } else if (first.length > MAX_NAME_LENGTH) {
        problems += ProfileProblem(
            ProfileField.FirstName,
            "At most $MAX_NAME_LENGTH characters"
        )
    }

    if (draft.lastName.trim().length > MAX_NAME_LENGTH) {
        problems += ProfileProblem(
            ProfileField.LastName,
            "At most $MAX_NAME_LENGTH characters"
        )
    }

    if (draft.bio.trim().length > MAX_BIO_LENGTH) {
        problems += ProfileProblem(ProfileField.Bio, "At most $MAX_BIO_LENGTH characters")
    }

    usernameProblem(draft.username.trim())?.let { problems += it }

    return problems
}

/**
 * What is wrong with a username, or null.
 *
 * An empty one is not wrong: clearing the field is how an account gives its
 * username up, and Telegram allows that.
 */
private fun usernameProblem(username: String): ProfileProblem? {
    if (username.isEmpty()) return null

    fun problem(message: String) = ProfileProblem(ProfileField.Username, message)

    if (username.length < MIN_USERNAME_LENGTH) {
        return problem("At least $MIN_USERNAME_LENGTH characters")
    }
    if (username.length > MAX_USERNAME_LENGTH) {
        return problem("At most $MAX_USERNAME_LENGTH characters")
    }
    if (!username.first().isAsciiLetter()) {
        return problem("Must start with a letter")
    }
    if (username.last() == '_') {
        return problem("Cannot end with an underscore")
    }
    if (username.contains("__")) {
        return problem("Cannot contain two underscores in a row")
    }
    if (!username.all { it.isAsciiLetter() || it.isAsciiDigit() || it == '_' }) {
        return problem("Letters, digits and underscores only")
    }
    return null
}

// Kotlin's Char.isLetter and Char.isDigit are Unicode-wide, so they accept
// Cyrillic and Arabic-Indic digits that Telegram does not. The rule is ASCII.
private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'

/**
 * Which fields [draft] would actually change on [user].
 *
 * Drives both the save button's enabled state and what is sent: TDLib takes
 * a name, a bio and a username through three separate calls, and sending a
 * field back unchanged is a round trip that can fail for no reason. Comparison
 * is on the trimmed text, because trailing whitespace is not an edit.
 */
fun profileChanges(user: TelegramUser, draft: ProfileDraft): Set<ProfileField> {
    val current = profileDraftOf(user)
    return buildSet {
        if (draft.firstName.trim() != current.firstName.trim()) add(ProfileField.FirstName)
        if (draft.lastName.trim() != current.lastName.trim()) add(ProfileField.LastName)
        if (draft.bio.trim() != current.bio.trim()) add(ProfileField.Bio)
        if (draft.username.trim() != current.username.trim()) add(ProfileField.Username)
    }
}

/**
 * Whether [draft] is worth sending: something changed and nothing is invalid.
 *
 * Both halves matter. A save button live on an unchanged form invites a round
 * trip that does nothing, and one live on an invalid form invites an error
 * the screen could have explained itself.
 */
fun canSaveProfile(user: TelegramUser, draft: ProfileDraft): Boolean =
    profileChanges(user, draft).isNotEmpty() && validateProfile(draft).isEmpty()
