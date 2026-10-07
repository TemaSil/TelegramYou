package com.telegramyou.app.telegram.model

/**
 * Inline bots and Mini Apps (2.0).
 *
 * An inline bot answers what is typed after its name in any chat's composer
 * — "@gif cats" — with results to pick from, and the one picked is sent
 * into the chat as the bot made it. A Mini App is a web page a bot opens
 * inside the client, from one of its buttons, which talks back to the
 * client through a small bridge of named events.
 */

/** A bot that answers inline queries, as Telegram describes it. */
data class InlineBot(
    val userId: Long,
    val username: String,
    /** The bot's own hint for its query, shown in the empty field after its name. */
    val placeholder: String = ""
)

/** What an inline result is, which decides how it is drawn: a row, or a tile. */
enum class InlineResultKind { Article, Photo, Gif, Video, Sticker, Audio, Voice, Document, Place, Contact, Game }

data class InlineResult(
    val id: String,
    val kind: InlineResultKind,
    val title: String = "",
    val description: String = "",
    /** TDLib's id for the thumbnail, which a download is asked for by. */
    val thumbFileId: Int? = null,
    /** The thumbnail on this device, once it is here. */
    val thumbPath: String? = null
) {
    /** A picture first and words second: drawn as a tile rather than a row. */
    val isVisual: Boolean
        get() = kind == InlineResultKind.Photo || kind == InlineResultKind.Gif ||
            kind == InlineResultKind.Video || kind == InlineResultKind.Sticker
}

data class InlineResults(
    /** The query's id, which sending a result names. */
    val queryId: Long,
    val results: List<InlineResult>,
    /** Where the next page starts; empty when there is none. */
    val nextOffset: String = ""
)

/** "@bot query" in the composer, read apart. */
data class InlineQuery(val botUsername: String, val query: String)

/**
 * The inline query in [draft], or null when it is not one: an @username at
 * the very start — letters, digits and underscores, not starting with a
 * digit, three at least, since Telegram's own inline bots are @gif, @pic
 * and @vid — and a space after it. Until
 * the space it may still be a mention being typed, so it is not a query.
 */
fun inlineQueryOf(draft: String): InlineQuery? {
    if (!draft.startsWith("@")) return null
    val space = draft.indexOf(' ')
    if (space < 0) return null
    val username = draft.substring(1, space)
    if (!USERNAME.matches(username)) return null
    return InlineQuery(username, draft.substring(space + 1))
}

private val USERNAME = Regex("[A-Za-z][A-Za-z0-9_]{2,31}")

/** A Mini App opened: the page to load, and the id it is closed by. */
data class WebAppSession(val launchId: Long, val url: String)

/**
 * The client's colours as a Mini App is told them, each an RGB integer:
 * the page draws itself in them, so an app opened here looks like this
 * client — the wallpaper's colours, through Material You — and not like
 * another one.
 */
data class WebAppTheme(
    val background: Int,
    val secondaryBackground: Int,
    val headerBackground: Int,
    val bottomBarBackground: Int,
    val sectionBackground: Int,
    val sectionSeparator: Int,
    val text: Int,
    val accentText: Int,
    val sectionHeaderText: Int,
    val subtitleText: Int,
    val destructiveText: Int,
    val hint: Int,
    val link: Int,
    val button: Int,
    val buttonText: Int
) {
    /**
     * As the page's bridge names them, "#rrggbb" each: what the
     * `theme_changed` event carries, and what the page's
     * `Telegram.WebApp.themeParams` then holds.
     */
    fun toParams(): Map<String, String> = linkedMapOf(
        "bg_color" to hex(background),
        "secondary_bg_color" to hex(secondaryBackground),
        "header_bg_color" to hex(headerBackground),
        "bottom_bar_bg_color" to hex(bottomBarBackground),
        "section_bg_color" to hex(sectionBackground),
        "section_separator_color" to hex(sectionSeparator),
        "text_color" to hex(text),
        "accent_text_color" to hex(accentText),
        "section_header_text_color" to hex(sectionHeaderText),
        "subtitle_text_color" to hex(subtitleText),
        "destructive_text_color" to hex(destructiveText),
        "hint_color" to hex(hint),
        "link_color" to hex(link),
        "button_color" to hex(button),
        "button_text_color" to hex(buttonText)
    )

    private fun hex(rgb: Int) = "#%06x".format(rgb and 0xFFFFFF)
}

/**
 * A Mini App's main button as the page set it up: shown along the bottom,
 * in the page's words, and pressed back to the page as an event.
 */
data class WebAppMainButton(
    val text: String,
    val isVisible: Boolean,
    val isActive: Boolean = true,
    val isProgressVisible: Boolean = false
)
