package com.telegramyou.app.telegram.model

/**
 * A text longer than Telegram takes in one message, cut into messages that
 * fit — what the official client does instead of refusing it (1.8.1).
 *
 * Cut where a reader would: at the last paragraph break that fits, else the
 * last line break, else the end of a sentence, else a space, and only as a
 * last resort mid-word — never inside a surrogate pair, which would leave
 * half an emoji at each end. The whitespace a cut lands on is dropped, so a
 * part neither ends nor begins with the break between them.
 *
 * Formatting comes along: [entities] are spans by UTF-16 offset, as
 * Telegram counts them; each is moved into the part it falls in, and one
 * that runs across a cut is split into both, so bold stays bold on either
 * side.
 */
data class TextSpan<T>(val offset: Int, val length: Int, val type: T)

data class TextPart<T>(val text: String, val entities: List<TextSpan<T>>)

fun <T> splitLongText(text: String, entities: List<TextSpan<T>>, max: Int): List<TextPart<T>> {
    require(max > 1) { "max must leave room for a character" }
    if (text.length <= max) return listOf(TextPart(text, entities))
    val parts = ArrayList<TextPart<T>>()
    var start = 0
    while (start < text.length) {
        // Whitespace left at the front by the previous cut is not carried over.
        while (start < text.length && text[start].isWhitespace()) start++
        if (start >= text.length) break
        val end = if (text.length - start <= max) text.length else cutBefore(text, start, start + max)
        val piece = text.substring(start, end).trimEnd()
        if (piece.isNotEmpty()) {
            val pieceEnd = start + piece.length
            val spans = entities.mapNotNull { span ->
                val from = maxOf(span.offset, start)
                val to = minOf(span.offset + span.length, pieceEnd)
                if (to > from) TextSpan(from - start, to - from, span.type) else null
            }
            parts += TextPart(piece, spans)
        }
        start = end
    }
    return parts
}

/** Where to end a part that starts at [start] and may run to [limit], exclusive. */
private fun cutBefore(text: String, start: Int, limit: Int): Int {
    // Not in the first fifth of the part: a break that early would leave a
    // stub of a message and push most of the text into the next.
    val floor = start + (limit - start) / 5
    fun lastAfter(token: String): Int? {
        val at = text.lastIndexOf(token, limit - token.length)
        return if (at >= floor) at + token.length else null
    }
    val sentence = listOf(". ", "! ", "? ", "… ").mapNotNull { lastAfter(it) }.maxOrNull()
    val cut = lastAfter("\n\n") ?: lastAfter("\n") ?: sentence ?: lastAfter(" ") ?: limit
    // Never between the two halves of an emoji or other surrogate pair.
    return if (cut in (start + 1) until text.length && text[cut - 1].isHighSurrogate()) cut - 1 else cut
}
