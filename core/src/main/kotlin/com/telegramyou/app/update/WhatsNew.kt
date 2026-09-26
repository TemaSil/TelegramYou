package com.telegramyou.app.update

/**
 * What one update brings, as the App update screen shows it: a title and a
 * few short lines. Only the update itself — what came before is in git, for
 * the people building this, and nobody installing it reads a history.
 *
 * Written once, in `app/src/main/assets/whats-new.md`, and read in two
 * places: the app shows the copy it was built with, and CI puts the same
 * text into the release's description between [WHATS_NEW_START] and
 * [WHATS_NEW_END], which is how an older app learns what the *incoming*
 * update brings before downloading it.
 */
data class WhatsNew(val title: String, val items: List<String>)

const val WHATS_NEW_START = "<!-- whats-new -->"
const val WHATS_NEW_END = "<!-- /whats-new -->"

/** How many lines one update may have; more is a wall nobody reads. */
const val WHATS_NEW_MAX_ITEMS = 4

/**
 * The file's format: `# Title` on the first line that has one, then a
 * `- line` for each thing. Anything else is ignored. Null without a title
 * or without a single line.
 */
fun parseWhatsNew(text: String): WhatsNew? {
    val lines = text.lines().map(String::trim)
    val title = lines.firstOrNull { it.startsWith("# ") }?.removePrefix("# ")?.trim() ?: return null
    val items = lines.filter { it.startsWith("- ") }.map { it.removePrefix("- ").trim() }.filter(String::isNotEmpty)
    return if (title.isEmpty() || items.isEmpty()) null else WhatsNew(title, items)
}

/** The notes CI wrote into a release's [body], or null if it wrote none. */
fun whatsNewIn(body: String): WhatsNew? {
    val start = body.indexOf(WHATS_NEW_START).takeIf { it >= 0 } ?: return null
    val end = body.indexOf(WHATS_NEW_END, start).takeIf { it >= 0 } ?: return null
    return parseWhatsNew(body.substring(start + WHATS_NEW_START.length, end))
}
