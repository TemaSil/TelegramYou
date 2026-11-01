package com.telegramyou.app.ui.chat

import com.telegramyou.app.telegram.model.ChatMessage
import com.telegramyou.app.telegram.model.MessageContentType

/**
 * Whether a photo's, a video's or a GIF's text is a caption somebody wrote
 * (2.9.1), rather than a word standing in for one.
 *
 * A message with no caption carries one of two such words as its text: the
 * bare marker ("Photo", "Video", "GIF") or, from the live client, the chat
 * list's description ("🎬 Video", "🖼 Фото"), since the notification for it
 * needs words. A bubble that took the description for a caption drew
 * "🎬 Video" under every video sent without one — as voice messages drew
 * "🎤 Voice" until 2.7.1 (isVoiceCaption).
 */
fun isMediaCaption(text: String): Boolean {
    val trimmed = text.trim()
    return trimmed.isNotEmpty() && trimmed !in MEDIA_PLACEHOLDERS
}

/** How a photo, a video or a GIF without a caption is worded, in both languages. */
val MEDIA_PLACEHOLDERS = setOf(
    "Photo", "Video", "GIF",
    "🖼 Photo", "🖼 Фото", "🎬 Video", "🎬 Видео"
)

/**
 * The caption somebody wrote on [message], or "" when its text is only the
 * word that stands in for one — for every kind that draws a caption under
 * itself: a photo, a video, a GIF, a voice message, a song, a file. A round
 * video message never has one; Telegram has nowhere to put it.
 */
fun writtenCaption(message: ChatMessage): String {
    val text = message.text.trim()
    val written = when (message.contentType) {
        MessageContentType.Photo, MessageContentType.Video, MessageContentType.Animation -> isMediaCaption(text)
        MessageContentType.Voice -> isVoiceCaption(text)
        MessageContentType.VideoNote -> false
        MessageContentType.Audio -> {
            // The list says "🎵 " and the title, or the file's name, or "Audio".
            val named = listOfNotNull(message.audio?.title, message.audio?.fileName, message.fileName, "Audio", "Аудио")
                .filter(String::isNotBlank)
            text.isNotEmpty() && named.none { text == it || text == "🎵 $it" }
        }
        MessageContentType.Document -> {
            val name = message.fileName.orEmpty()
            text.isNotEmpty() && text != name && text != "📎 $name"
        }
        else -> text.isNotEmpty()
    }
    return if (written) text else ""
}
