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
    suspend fun logout()
}
