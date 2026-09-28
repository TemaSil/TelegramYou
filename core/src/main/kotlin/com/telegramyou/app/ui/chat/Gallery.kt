package com.telegramyou.app.ui.chat

import com.telegramyou.app.telegram.model.ChatMessage
import com.telegramyou.app.telegram.model.MessageContentType

/**
 * What a swipe moves through once [opened] is full-screen: every photo and
 * video in [messages], oldest first, as the official client pages through a
 * chat's media. GIFs and round video messages are not among them — they play
 * where they are. A message not in [messages] (opened from the media grid,
 * outside the loaded window) is shown on its own.
 */
fun galleryOf(messages: List<ChatMessage>, opened: ChatMessage): List<ChatMessage> {
    val media = messages
        .filter { it.isGalleryMedia() }
        .sortedWith(compareBy({ it.date }, { it.id }))
    return if (media.any { it.id == opened.id }) media else listOf(opened)
}

/** A photo, or a video, which the gallery pages through. */
fun ChatMessage.isGalleryMedia(): Boolean = when (contentType) {
    MessageContentType.Photo -> photoPath != null || photoFileId != null
    MessageContentType.Video -> video != null
    else -> false
}
