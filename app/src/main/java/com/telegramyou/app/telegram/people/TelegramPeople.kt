package com.telegramyou.app.telegram.people

import com.telegramyou.app.telegram.model.PersonProfile
import com.telegramyou.app.telegram.model.TelegramUser

/**
 * Other people: who they are, whether they may reach this account, and
 * adding them.
 *
 * The contact list itself stays in TelegramChats, where the new-message
 * picker has always read it; this is what the profile, the blocked list and
 * the Contacts screen need on top of it.
 */
interface TelegramPeople {

    /** Somebody by their user id, with their bio; null when unknown. */
    suspend fun person(userId: Long): PersonProfile?

    /**
     * The other side of a private chat. Null for groups, channels and Saved
     * Messages, which have nobody behind them to show.
     */
    suspend fun personInChat(chatId: Long): PersonProfile?

    /**
     * Blocks or unblocks. [blocked] rather than a toggle, for the reason
     * TelegramChats.setChatMuted gives: the caller sends what it drew.
     */
    suspend fun setBlocked(userId: Long, blocked: Boolean)

    /** Everyone this account has blocked, as Telegram keeps the list. */
    suspend fun blockedPeople(): List<TelegramUser>

    /**
     * Adds somebody to the contacts by phone number. Their user id, or null
     * when the number is not on Telegram — which is an answer, not a failure.
     */
    suspend fun addContact(phone: String, firstName: String, lastName: String): Long?
}
