package com.telegramyou.app.telegram.stickers

import com.telegramyou.app.telegram.model.VideoContent
import com.telegramyou.app.telegram.model.GifItem
import com.telegramyou.app.telegram.model.StickerContent
import com.telegramyou.app.telegram.model.StickerSetPreview

/** The account's stickers, for the picker, and sending one. */
interface TelegramStickers {
    /** The sticker sets this account has added, in its own order. */
    suspend fun stickerSets(): List<StickerSetPreview>

    /**
     * The Premium custom-emoji sets this account has added, for the emoji
     * tab; their stickers come from [stickerSet] like any other set's, each
     * with its customEmojiId.
     */
    suspend fun customEmojiSets(): List<StickerSetPreview> = emptyList()

    /** Every sticker in set [setId]. */
    suspend fun stickerSet(setId: Long): List<StickerContent>

    /** The stickers sent most recently, newest first. */
    suspend fun recentStickers(): List<StickerContent>

    suspend fun sendSticker(chatId: Long, sticker: StickerContent, replyToId: Long? = null)

    /** The GIFs this account saved, newest first — the GIF tab before anything is typed. */
    suspend fun savedGifs(): List<GifItem> = emptyList()

    /** GIFs for [query], found by Telegram's own @gif bot, as every client finds them. */
    suspend fun searchGifs(query: String): List<GifItem> = emptyList()

    suspend fun sendGif(chatId: Long, gif: GifItem, replyToId: Long? = null)

    /** A GIF from a message kept among the saved ones — the GIF tab's first page. */
    suspend fun saveGif(video: VideoContent) {}

    /**
     * The stickers behind custom emoji, by id — what a custom-emoji reaction
     * is drawn as. Those not found are simply missing from the answer.
     */
    suspend fun customEmoji(ids: List<Long>): Map<Long, StickerContent> = emptyMap()
}
