package com.telegramyou.app.ui.chat

import com.telegramyou.app.i18n.tr
import com.telegramyou.app.telegram.model.ChatMessage
import com.telegramyou.app.telegram.model.MessageContentType

/**
 * Saved Messages as notes (2.9): each saved message a card in a grid, as
 * Google Keep lays out notes, newest first. What a card says is decided
 * here, on the JVM, because the edge cases are words — the "Photo" a
 * caption-less photo carries is a marker, not a note.
 */

/** Words that stand in for a caption nobody wrote, by the kind of message. */
private val MARKERS = setOf("Photo", "Video", "GIF", "Voice", "Video message", "Sticker", "Audio", "File")

/**
 * The words on a note: what was written, or nothing when the text is only
 * the marker a caption-less photo, video or file carries.
 */
fun noteBody(message: ChatMessage): String {
    val text = message.text.trim()
    if (text in MARKERS) return ""
    if (message.contentType != MessageContentType.Text && message.mediaEmoji != null && text.startsWith(message.mediaEmoji)) {
        // "🎤 Voice"-style labels from the chat list.
        return ""
    }
    return text
}

/**
 * A line saying what else the note holds, for the kinds a card has no
 * picture for — a voice message, a file, a song, a place — or null.
 */
fun noteAttachment(message: ChatMessage): String? = when (message.contentType) {
    MessageContentType.Voice -> tr("Voice message", "Голосовое") +
        message.voiceDurationSeconds.takeIf { it > 0 }?.let { " · ${formatDuration(it.toLong())}" }.orEmpty()
    MessageContentType.Document -> message.fileName ?: tr("File", "Файл")
    MessageContentType.Audio -> message.audio?.let { listOf(it.performer, it.title).filter(String::isNotBlank).joinToString(" — ") }
        ?.ifBlank { null } ?: tr("Music", "Музыка")
    MessageContentType.Location -> tr("Location", "Место")
    MessageContentType.Contact -> message.contact?.let { "${it.firstName} ${it.lastName}".trim() } ?: tr("Contact", "Контакт")
    MessageContentType.Poll -> message.poll?.question ?: tr("Poll", "Опрос")
    else -> null
}

/** Whether a note shows a picture at its top: a photo, a video's frame, a GIF's. */
fun noteHasPicture(message: ChatMessage): Boolean = when (message.contentType) {
    MessageContentType.Photo -> true
    MessageContentType.Video, MessageContentType.Animation, MessageContentType.VideoNote -> message.video != null
    else -> false
}
