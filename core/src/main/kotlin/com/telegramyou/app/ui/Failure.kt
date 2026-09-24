package com.telegramyou.app.ui

/**
 * What a snackbar says when the server refused something.
 *
 * [action] is this client's own words for what was being done — "Could not
 * send" — and [detail] is the server's reason, when it gave one. TDLib's
 * reasons come two ways: sentences ("Too Many Requests: retry after 17") and
 * error codes ("MESSAGE_ID_INVALID"). A code is turned into words, because a
 * snackbar shouting in capitals with underscores reads as a crash report.
 */
fun failureText(action: String, detail: String?): String {
    val reason = detail?.trim().orEmpty()
    if (reason.isEmpty()) return action
    val readable = if (reason.matches(Regex("[A-Z0-9_]+"))) {
        reason.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
    } else {
        reason
    }
    return "$action: $readable"
}
