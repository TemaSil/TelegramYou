package com.telegramyou.app.telegram.model

/**
 * The tabs of a chat's shared media, in the official client's order: what
 * was sent there, by kind, newest first. Each is one of Telegram's search
 * filters — the server does the choosing — and [emptyText] is what a tab
 * says with nothing in it.
 */
enum class SharedMediaKind(val label: String, val emptyText: String) {
    Media("Media", "No photos or videos here yet"),
    Files("Files", "No files here yet"),
    Music("Music", "No music here yet"),
    Voice("Voice", "No voice or video messages here yet"),
    Links("Links", "No links here yet"),
    Gifs("GIFs", "No GIFs here yet");

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
