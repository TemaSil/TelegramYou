package com.telegramyou.app.telegram

import com.telegramyou.app.telegram.auth.TelegramAuth
import com.telegramyou.app.telegram.chats.TelegramChats
import com.telegramyou.app.telegram.messages.TelegramMessages
import com.telegramyou.app.telegram.profile.TelegramProfile
import com.telegramyou.app.telegram.stories.TelegramStories

/**
 * One connection to Telegram, presented as five smaller interfaces.
 *
 * The split is for the callers, not for the backends: both of those still
 * implement the whole of this, because both speak to one TDLib socket — or
 * pretend to. What changes is that a state holder can depend on
 * [TelegramMessages] alone and be unable to touch authentication, and that a
 * new feature adds its method to one domain rather than to a list of
 * everything the app can do.
 *
 * That list is the point. The screen inventory in ARCHITECTURE.md runs past
 * a hundred; a single interface grown a method at a time would end up with
 * hundreds, and every one of them would have to be written twice.
 */
interface TelegramClient :
    TelegramAuth,
    TelegramChats,
    TelegramMessages,
    TelegramProfile,
    TelegramStories {

    /** Opens the connection. Called once, from `TelegramYouApp`. */
    fun start()

    fun shutdown()
}
