package com.telegramyou.app.telegram.storage

import com.telegramyou.app.telegram.model.StorageKind
import com.telegramyou.app.telegram.model.StorageUsage

/**
 * The files Telegram keeps on this phone. TDLib downloads into its own
 * directory and never lets go of anything by itself, so without this the
 * cache grows for as long as the app is used.
 */
interface TelegramStorage {
    /** What is stored, by kind. Can take a moment on a large cache. */
    suspend fun storageUsage(): StorageUsage

    /**
     * Deletes every cached file of [kinds] and answers with what is left.
     * Only copies: everything cleared is still in the chats and downloads
     * again when it is next opened.
     */
    suspend fun clearCache(kinds: Set<StorageKind>): StorageUsage
}
