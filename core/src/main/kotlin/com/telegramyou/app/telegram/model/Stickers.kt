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
    val thumbPath: String? = null,
    /** For a Premium custom emoji, the id a message's text entity names it by; 0 otherwise. */
    val customEmojiId: Long = 0,
    /** The set it comes from, which a tap on it in a chat opens (2.6); 0 for none. */
    val setId: Long = 0
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
    val cover: StickerContent? = null,
    /** How many stickers it holds, where known; 0 otherwise. */
    val count: Int = 0,
    /** Added to this account — a trending set may not be (2.6). */
    val isInstalled: Boolean = true
)

/**
 * A whole set, as its sheet shows it (2.6): every sticker, and whether this
 * account has it — what Add and Remove turn on and off.
 */
data class StickerSetInfo(
    val id: Long,
    val title: String,
    /** Its short name, the one a t.me/addstickers link carries. */
    val name: String = "",
    val stickers: List<StickerContent> = emptyList(),
    val isInstalled: Boolean = false
)

/**
 * The account's sets after one has moved [by] places — up for a negative
 * step, down for a positive — kept inside the list; unchanged for an id it
 * does not hold. The order is the account's own, sent back to Telegram whole.
 */
fun movedSet(order: List<Long>, id: Long, by: Int): List<Long> {
    val from = order.indexOf(id)
    if (from < 0) return order
    val to = (from + by).coerceIn(0, order.lastIndex)
    if (to == from) return order
    return order.toMutableList().apply { add(to, removeAt(from)) }
}

/**
 * A GIF the picker offers — one the account saved, or one Telegram's @gif
 * bot found. [video] is the same shape a GIF message carries, so the picker
 * draws it the way the chat does; [id] tells two apart, and [width] and
 * [height] go along when it is sent, which TDLib asks for.
 */
data class GifItem(
    val id: String,
    val video: VideoContent,
    val width: Int = 0,
    val height: Int = 0
)
