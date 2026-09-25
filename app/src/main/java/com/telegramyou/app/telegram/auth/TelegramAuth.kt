package com.telegramyou.app.telegram.auth

import com.telegramyou.app.telegram.model.AuthUiState
import kotlinx.coroutines.flow.StateFlow

/** Getting in, and getting out. */
interface TelegramAuth {
    val authState: StateFlow<AuthUiState>

    suspend fun submitPhoneNumber(phone: String)
    suspend fun submitCode(code: String)
    suspend fun submitPassword(password: String)
    suspend fun resendCode()

    /**
     * Sign in by scanning a QR code with Telegram on a phone that is already
     * signed in, instead of by a code. The link to draw arrives in
     * [com.telegramyou.app.telegram.model.AuthUiState.qrLink]; submitting a
     * phone number afterwards goes back to the code route.
     */
    suspend fun requestQrLogin()

    /**
     * The login email Telegram asked for, while the state is
     * [com.telegramyou.app.telegram.model.AuthState.WaitEmailAddress]. A code
     * is sent to it and the state moves on to the email code.
     */
    suspend fun submitEmailAddress(email: String)

    /** The code from the login email, while the state is `WaitEmailCode`. */
    suspend fun submitEmailCode(code: String)

    /**
     * Give up on the login email for a code by SMS — for a mailbox that is
     * gone. Takes effect after the server's wait; see
     * [com.telegramyou.app.telegram.model.EmailReset].
     */
    suspend fun resetEmail()
    suspend fun logout()
}
