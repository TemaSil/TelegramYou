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

/**
 * Saved messages put together into notes (2.9): one after another within
 * [NOTE_GAP_SECONDS] of each other make one note, as a thought jotted in
 * three messages is one thought. Something forwarded stays with what came
 * from the same place — two sources are two notes, however close in time.
 * Oldest first in, and in each note; the notes themselves newest first,
 * as the grid shows them.
 */
fun groupNotes(messages: List<ChatMessage>, gapSeconds: Long = NOTE_GAP_SECONDS): List<List<ChatMessage>> {
    val notes = mutableListOf<MutableList<ChatMessage>>()
    for (message in messages) {
        val last = notes.lastOrNull()?.last()
        val joins = last != null &&
            last.date > 0L && message.date > 0L &&
            message.date - last.date in 0..gapSeconds &&
            last.forwardedFrom == message.forwardedFrom
        if (joins) notes.last().add(message) else notes.add(mutableListOf(message))
    }
    return notes.asReversed()
}

/** The words of a note of several messages, each message its own paragraph. */
fun noteBody(note: List<ChatMessage>): String =
    note.map(::noteBody).filter(String::isNotEmpty).joinToString("\n\n")

/** How close saved messages must be to make one note: two minutes, a pause in a thought. */
const val NOTE_GAP_SECONDS = 120L
