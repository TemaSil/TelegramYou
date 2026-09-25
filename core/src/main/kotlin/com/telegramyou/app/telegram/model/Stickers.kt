package com.telegramyou.app.telegram.model

/** How a sticker is drawn: a still picture, a Lottie animation, or a video. */
enum class StickerFormat { Webp, Tgs, Webm }

/**
 * One sticker, wherever it appears — in a message, in the picker, on its way
 * out.
 *
 * [fileId] is TDLib's for the sticker itself and [path] where that file is on
 * this device, once it is. [thumbFileId] and [thumbPath] are its still
 * picture, which is what a video sticker is drawn as until this client plays
 * them. With neither — the demo's stickers — it is drawn as its [emoji].
 */
data class StickerContent(
    val id: Long = 0,
    val emoji: String,
    val format: StickerFormat = StickerFormat.Webp,
    val width: Int = 512,
    val height: Int = 512,
    val fileId: Int? = null,
    val path: String? = null,
    val thumbFileId: Int? = null,
    val thumbPath: String? = null
) {
    /**
     * The file to draw, as TDLib names it: the sticker itself, unless it is a
     * video — those are drawn from their still picture for now.
     */
    val drawnFileId: Int? get() = if (format == StickerFormat.Webm) thumbFileId ?: fileId else fileId

    /** Whether the drawn file is an animation to play rather than a picture. */
    val isAnimated: Boolean get() = format == StickerFormat.Tgs
}

/** A sticker set as the picker's tab row shows it. */
data class StickerSetPreview(
    val id: Long,
    val title: String,
    /** The set's own picture, or its first sticker's; null while unknown. */
    val cover: StickerContent? = null
)
