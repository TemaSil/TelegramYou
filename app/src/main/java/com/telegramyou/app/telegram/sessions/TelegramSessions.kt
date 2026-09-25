package com.telegramyou.app.telegram.sessions

import com.telegramyou.app.telegram.model.ActiveSession

/** Everywhere this account is signed in, and ending those that should not be. */
interface TelegramSessions {
    /** Every session, this one included; see ActiveSession.isCurrent. */
    suspend fun activeSessions(): List<ActiveSession>

    /** Signs session [id] out. Not this one — that is logging out. */
    suspend fun terminateSession(id: Long)

    /** Signs out every session but this one. */
    suspend fun terminateOtherSessions()
}
