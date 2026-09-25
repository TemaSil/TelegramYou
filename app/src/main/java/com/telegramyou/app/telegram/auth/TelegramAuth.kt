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
    suspend fun logout()
}
