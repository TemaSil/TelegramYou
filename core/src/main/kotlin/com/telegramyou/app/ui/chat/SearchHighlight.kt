package com.telegramyou.app.ui.chat

/**
 * Where a search term falls inside a message.
 *
 * Returned as ranges rather than as a marked-up string so the caller decides
 * how to draw them — the same answer serves a bold span in a result row and a
 * background wash in a bubble.
 *
 * Case-insensitive, because nobody types a search with the shift key. Matches
 * do not overlap: searching "aa" in "aaaa" finds two, not three, since a
 * highlight that starts inside another one has nothing to draw.
 */
fun matchRanges(text: String, query: String): List<IntRange> {
    if (query.isBlank() || text.isEmpty()) return emptyList()
    val ranges = mutableListOf<IntRange>()
    var from = 0
    while (from <= text.length - query.length) {
        val at = text.indexOf(query, startIndex = from, ignoreCase = true)
        if (at < 0) break
        ranges += at until (at + query.length)
        from = at + query.length
    }
    return ranges
}

/**
 * A single line of context around the first match.
 *
 * A result row has one line to say why it matched, and a hit four hundred
 * characters into a message would otherwise be off the end of it. The window
 * keeps [before] characters ahead of the match and fills the rest after,
 * marking each cut end with an ellipsis so a trimmed line does not read as the
 * whole message.
 *
 * The returned offset is where the match now sits, so the caller can still
 * highlight it after the text has moved.
 */
data class Snippet(val text: String, val matchStart: Int, val matchLength: Int)

fun snippet(
    text: String,
    query: String,
    before: Int = 24,
    length: Int = 96
): Snippet {
    val first = matchRanges(text, query).firstOrNull()
        ?: return Snippet(text.take(length), matchStart = 0, matchLength = 0)

    val start = maxOf(0, first.first - before)
    val end = minOf(text.length, start + length)
    val head = if (start > 0) "…" else ""
    val tail = if (end < text.length) "…" else ""
    return Snippet(
        text = head + text.substring(start, end) + tail,
        matchStart = first.first - start + head.length,
        matchLength = query.length
    )
}
