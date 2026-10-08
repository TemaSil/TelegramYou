package com.telegramyou.app.telegram.model

import com.telegramyou.app.ui.chat.ALBUM_LIMIT

/**
 * One thing handed over by Android's share sheet (2.1): where a copy of it
 * now is, the name it came with and its type, as the sending app gave them.
 */
data class SharedItem(val uri: String, val name: String, val mime: String?)

/**
 * What another app shared into this one: words, things, or both, and the
 * chat it was shared to when it came through a chat's own Direct Share
 * target — null when the app itself was chosen, and a chat is still to pick.
 */
data class IncomingShare(
    val chatId: Long?,
    val text: String,
    val items: List<SharedItem>
) {
    val isEmpty: Boolean get() = text.isBlank() && items.isEmpty()
}

/**
 * What a share becomes in the composer: pictures as an album, up to its
 * ten, when everything shared is a picture — a screenshot shared from the
 * gallery is a photo, not a file — and files otherwise, each with its name.
 */
fun shareDraft(items: List<SharedItem>): AttachmentDraft? = when {
    items.isEmpty() -> null
    items.all { it.mime?.startsWith("image/") == true } ->
        AttachmentDraft.Photos(items.take(ALBUM_LIMIT).map { it.uri })
    else -> AttachmentDraft.Files(items.map { it.uri }, items.map { it.name })
}
