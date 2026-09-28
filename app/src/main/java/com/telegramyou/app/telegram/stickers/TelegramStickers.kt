package com.telegramyou.app.telegram.stickers

import com.telegramyou.app.telegram.model.GifItem
import com.telegramyou.app.telegram.model.StickerContent
import com.telegramyou.app.telegram.model.StickerSetPreview

/** The account's stickers, for the picker, and sending one. */
interface TelegramStickers {
    /** The sticker sets this account has added, in its own order. */
    suspend fun stickerSets(): List<StickerSetPreview>

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

    /**
     * The stickers behind custom emoji, by id — what a custom-emoji reaction
     * is drawn as. Those not found are simply missing from the answer.
     */
    suspend fun customEmoji(ids: List<Long>): Map<Long, StickerContent> = emptyMap()
}
