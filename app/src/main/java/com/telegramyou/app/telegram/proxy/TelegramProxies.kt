package com.telegramyou.app.telegram.proxy

import com.telegramyou.app.telegram.model.ProxyServer

/**
 * The proxies Telegram connects through, where it is blocked or slow.
 *
 * TDLib keeps the list itself, across restarts, and every call here works
 * before sign-in as well as after — a person who cannot reach Telegram
 * without a proxy cannot sign in without one either.
 */
interface TelegramProxies {
    /** Every proxy TDLib has been given, the one in use marked. */
    suspend fun proxies(): List<ProxyServer>

    /** Adds [proxy] and, if [enable], switches to it; answers with its id. */
    suspend fun addProxy(proxy: ProxyServer, enable: Boolean): Int

    /** Connects through proxy [id] — and so through no other. */
    suspend fun enableProxy(id: Int)

    /** Connects directly again. The proxies stay listed. */
    suspend fun disableProxy()

    suspend fun removeProxy(id: Int)

    /** The round trip through [proxy] in milliseconds, or null when it answered nothing. */
    suspend fun pingProxy(proxy: ProxyServer): Long?
}
