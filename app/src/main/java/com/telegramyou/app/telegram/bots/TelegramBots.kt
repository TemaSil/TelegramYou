package com.telegramyou.app.telegram.bots

import com.telegramyou.app.telegram.model.InlineBot
import com.telegramyou.app.telegram.model.InlineResults
import com.telegramyou.app.telegram.model.WebAppSession
import com.telegramyou.app.telegram.model.WebAppTheme

/**
 * Inline bots and Mini Apps (2.0); see Bots.kt in :core. Each has a default
 * that does nothing, so a backend without them still builds and the
 * composer simply finds no bot.
 */
interface TelegramBots {

    /** The inline bot called [username], or null when there is no such bot or it answers no queries. */
    suspend fun inlineBot(username: String): InlineBot? = null

    /** What [botId] answers to [query] typed in [chatId], from [offset] for the pages after the first. */
    suspend fun inlineResults(botId: Long, chatId: Long, query: String, offset: String = ""): InlineResults? = null

    /** Sends the result [resultId] of the query [queryId] into [chatId], as the bot made it. */
    suspend fun sendInlineResult(chatId: Long, queryId: Long, resultId: String, replyToId: Long? = null) {}

    /**
     * Opens [url], a Mini App of [botId] from a button in [chatId], in this
     * client's [theme]; null where it cannot be, and the link is then opened
     * as a link.
     */
    suspend fun openWebApp(chatId: Long, botId: Long, url: String, theme: WebAppTheme): WebAppSession? = null

    /** Tells Telegram the Mini App [launchId] was closed. */
    suspend fun closeWebApp(launchId: Long) {}
}
