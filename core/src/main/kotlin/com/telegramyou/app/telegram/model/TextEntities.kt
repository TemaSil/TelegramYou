package com.telegramyou.app.telegram.model

/**
 * A stretch of a message's text that is more than text: bold, a link, a
 * mention. Telegram sends these beside the words rather than inside them.
 *
 * [offset] and [length] count UTF-16 code units, as TDLib does — which is
 * also how a Kotlin string is indexed, so they apply to the text directly.
 */
data class TextEntity(
    val offset: Int,
    val length: Int,
    val type: EntityType
) {
    val end: Int get() = offset + length
}

/** What a [TextEntity] makes of its stretch of text. */
sealed interface EntityType {
    // How it looks.
    data object Bold : EntityType
    data object Italic : EntityType
    data object Underline : EntityType
    data object Strikethrough : EntityType
    /** Hidden until tapped. */
    data object Spoiler : EntityType
    data object Code : EntityType
    data object Pre : EntityType
    data object BlockQuote : EntityType

    // Where it goes.
    /** A link written out in the text. */
    data object Url : EntityType
    /** Words that link somewhere else. */
    data class TextUrl(val url: String) : EntityType
    data object Email : EntityType
    data object Phone : EntityType
    /** "@username". */
    data object Mention : EntityType
    /** A mention of someone without a username, by id. */
    data class MentionName(val userId: Long) : EntityType
    data object Hashtag : EntityType
    data object Cashtag : EntityType
    data object BotCommand : EntityType
}

/**
 * [entities] made safe for [text]: each clipped to the text, the empty ones
 * dropped. A client that trusted offsets blindly would crash on the first
 * message edited under it, where the entities and the text arrive from two
 * different updates.
 */
fun clampEntities(text: String, entities: List<TextEntity>): List<TextEntity> =
    entities.mapNotNull { entity ->
        val start = entity.offset.coerceIn(0, text.length)
        val end = entity.end.coerceIn(start, text.length)
        if (end > start) entity.copy(offset = start, length = end - start) else null
    }

/** Where tapping [entity] in [text] leads, as a URL; null for a style. */
fun linkTarget(text: String, entity: TextEntity): String? {
    val part = text.substring(entity.offset, entity.end)
    return when (val type = entity.type) {
        EntityType.Url -> if (part.contains("://")) part else "https://$part"
        is EntityType.TextUrl -> type.url
        EntityType.Email -> "mailto:$part"
        EntityType.Phone -> "tel:${part.filter { it.isDigit() || it == '+' }}"
        else -> null
    }
}

/**
 * Telegram's own markdown, parsed: **bold**, __italic__, ~~strikethrough~~,
 * ||spoiler|| and `code`, each marker pair around the words. What the demo
 * uses to format a message typed with markers; the live client asks TDLib's
 * `parseMarkdown`, which reads the same markers.
 *
 * Unclosed markers are left as the characters they are.
 */
fun parseMarkdown(input: String): Pair<String, List<TextEntity>> {
    val markers = listOf(
        "**" to EntityType.Bold,
        "__" to EntityType.Italic,
        "~~" to EntityType.Strikethrough,
        "||" to EntityType.Spoiler,
        "`" to EntityType.Code
    )
    val out = StringBuilder()
    val entities = mutableListOf<TextEntity>()
    var index = 0
    while (index < input.length) {
        val marker = markers.firstOrNull { (mark, _) -> input.startsWith(mark, index) }
        if (marker != null) {
            val (mark, type) = marker
            val close = input.indexOf(mark, index + mark.length)
            if (close > index + mark.length) {
                val inner = input.substring(index + mark.length, close)
                entities += TextEntity(out.length, inner.length, type)
                out.append(inner)
                index = close + mark.length
                continue
            }
        }
        out.append(input[index])
        index++
    }
    return out.toString() to entities
}

/**
 * Where a forwarded message first came from, as the line above it says:
 * a person's or a chat's name, or the name someone chose to forward under
 * with their account hidden.
 */
fun forwardedLabel(origin: String): String = "Forwarded from $origin"

/**
 * Photos sent together that should draw as one grid: runs of consecutive
 * messages sharing [albumOf]'s id, in order. Messages outside an album are
 * runs of one. The first message of each run is where the grid draws; the
 * rest draw nothing of their own.
 */
fun <T> albumRuns(messages: List<T>, albumOf: (T) -> Long?): List<List<T>> {
    val runs = mutableListOf<MutableList<T>>()
    for (message in messages) {
        val album = albumOf(message)
        val last = runs.lastOrNull()
        if (album != null && last != null && albumOf(last.first()) == album) last += message
        else runs += mutableListOf(message)
    }
    return runs
}
