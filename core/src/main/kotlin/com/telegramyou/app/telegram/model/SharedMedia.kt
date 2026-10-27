package com.telegramyou.app.telegram.model

/**
 * The tabs of a chat's shared media, in the official client's order: what
 * was sent there, by kind, newest first. Each is one of Telegram's search
 * filters — the server does the choosing — and [emptyText] is what a tab
 * says with nothing in it.
 */
enum class SharedMediaKind(private val en: Pair<String, String>, private val ru: Pair<String, String>) {
    Media("Media" to "No photos or videos here yet", "Медиа" to "Фото и видео здесь пока нет"),
    Files("Files" to "No files here yet", "Файлы" to "Файлов здесь пока нет"),
    Music("Music" to "No music here yet", "Музыка" to "Музыки здесь пока нет"),
    Voice("Voice" to "No voice or video messages here yet", "Голосовые" to "Голосовых и видеосообщений здесь пока нет"),
    Links("Links" to "No links here yet", "Ссылки" to "Ссылок здесь пока нет"),
    Gifs("GIFs" to "No GIFs here yet", "GIF" to "GIF здесь пока нет");

    /** The tab's name and what it says while empty, in the language in use. */
    val label: String get() = com.telegramyou.app.i18n.tr(en.first, ru.first)
    val emptyText: String get() = com.telegramyou.app.i18n.tr(en.second, ru.second)

    /**
     * Whether [message] belongs in this tab — what the demo filters by, and
     * the check the live backend's answers are held to.
     */
    fun matches(message: ChatMessage): Boolean = when (this) {
        Media -> message.contentType == MessageContentType.Photo ||
            message.contentType == MessageContentType.Video
        Files -> message.contentType == MessageContentType.Document
        Music -> message.contentType == MessageContentType.Audio
        Voice -> message.contentType == MessageContentType.Voice ||
            message.contentType == MessageContentType.VideoNote
        Links -> message.linkPreview != null || firstLink(message.text) != null
        Gifs -> message.contentType == MessageContentType.Animation
    }
}

/**
 * The first web address in [text], or null — what the Links tab shows for
 * a message whose link Telegram made no card for.
 */
fun firstLink(text: String): String? =
    LINK.find(text)?.value?.trimEnd('.', ',', ')', '!', '?', ';', ':')

private val LINK = Regex("""(?i)\b(?:https?://|www\.)[^\s<>"]+|\b[a-z0-9-]+(?:\.[a-z0-9-]+)*\.(?:com|org|net|io|dev|app|me|ru|design)(?:/[^\s<>"]*)?""")

/**
 * A link as the Links tab labels it: its site, without the scheme and the
 * "www." the eye skips anyway.
 */
fun linkHost(link: String): String =
    link.substringAfter("://").removePrefix("www.").substringBefore('/').lowercase()

/** A file's extension as its tile shows it — "PDF" — or null without one. */
fun fileExtension(name: String?): String? =
    name?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() && it.length <= 5 }?.uppercase()
