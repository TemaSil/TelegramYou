package com.telegramyou.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.AuthUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

/**
 * What the login screen shows, and what has been typed into it.
 *
 * [auth] comes from the client and says which step we are on. The three
 * fields are what the person has entered and were previously kept in the
 * composition, so rotating the device halfway through a confirmation code
 * lost it — and the code cannot be asked for again without another SMS.
 */
data class AuthFormState(
    val auth: AuthUiState = AuthUiState(),
    val phone: String = "",
    val code: String = "",
    val password: String = ""
)

class AuthViewModel(
    private val repository: TelegramRepository
) : ViewModel() {

    private val phone = MutableStateFlow("")
    private val code = MutableStateFlow("")
    private val password = MutableStateFlow("")

    val uiState: StateFlow<AuthFormState> = combine(
        repository.observeAuth(),
        phone,
        code,
        password
    ) { auth, phoneValue, codeValue, passwordValue ->
        AuthFormState(
            auth = auth,
            phone = phoneValue,
            code = codeValue,
            password = passwordValue
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AuthFormState()
    )

    fun onPhoneChange(value: String) { phone.value = value }
    fun onCodeChange(value: String) { code.value = value }
    fun onPasswordChange(value: String) { password.value = value }

    fun submitPhone() {
        viewModelScope.launch { repository.submitPhoneNumber(phone.value) }
    }

    fun submitCode() {
        viewModelScope.launch { repository.submitCode(code.value) }
    }

    fun submitPassword() {
        viewModelScope.launch { repository.submitPassword(password.value) }
    }

    fun resendCode() {
        viewModelScope.launch { repository.resendCode() }
    }
}
