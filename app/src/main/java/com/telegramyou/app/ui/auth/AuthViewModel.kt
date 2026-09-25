package com.telegramyou.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.AuthState
import com.telegramyou.app.telegram.model.AuthUiState
import com.telegramyou.app.telegram.model.isPlausibleEmail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Which step the screen shows. In the order they come, so the screen can tell
 * forward from back: a login email, where Telegram asks for one, is set up
 * after the number and before any code.
 */
enum class AuthStep { Phone, Email, EmailCode, Code, Password, Qr }

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
    /** A plus and digits only; see PhoneEntry. Drawn formatted. */
    val phone: String = "+",
    val code: String = "",
    val password: String = "",
    val email: String = "",
    /** Back on the phone step from the code step, to fix a mistyped number. */
    val isChangingNumber: Boolean = false
) {
    val step: AuthStep
        get() = when {
            isChangingNumber -> AuthStep.Phone
            auth.state == AuthState.WaitCode -> AuthStep.Code
            auth.state == AuthState.WaitEmailAddress -> AuthStep.Email
            auth.state == AuthState.WaitEmailCode -> AuthStep.EmailCode
            auth.state == AuthState.WaitPassword -> AuthStep.Password
            auth.state == AuthState.WaitQrScan -> AuthStep.Qr
            else -> AuthStep.Phone
        }

    /**
     * Whether the step's button does anything. A phone number has to be long
     * enough to exist, a code has to be complete where its length is known,
     * and nothing is sent twice while a request is out.
     */
    val canSubmit: Boolean
        get() = !auth.isLoading && when (step) {
            AuthStep.Phone -> PhoneEntry.isPossible(phone)
            AuthStep.Code, AuthStep.EmailCode -> code.isNotEmpty() &&
                (auth.codeLength == 0 || code.length >= auth.codeLength)
            AuthStep.Email -> isPlausibleEmail(email)
            AuthStep.Password -> password.isNotEmpty()
            AuthStep.Qr -> false
        }
}

class AuthViewModel(
    private val repository: TelegramRepository
) : ViewModel() {

    private val phone = MutableStateFlow("+")
    private val code = MutableStateFlow("")
    private val password = MutableStateFlow("")
    private val email = MutableStateFlow("")
    private val changingNumber = MutableStateFlow(false)

    // What has been typed, as one form; combine takes five flows at most.
    private val typed = combine(phone, code, password, email) { phoneValue, codeValue, passwordValue, emailValue ->
        AuthFormState(phone = phoneValue, code = codeValue, password = passwordValue, email = emailValue)
    }

    val uiState: StateFlow<AuthFormState> = combine(
        repository.observeAuth(),
        typed,
        changingNumber
    ) { auth, form, changing ->
        form.copy(auth = auth, isChangingNumber = changing)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AuthFormState()
    )

    /**
     * The country the phone is in, from its SIM or its network, so the field
     * starts with "+7" or "+44" rather than an empty plus. Only while nothing
     * has been typed: this must never overwrite a number.
     */
    fun onDefaultRegion(region: String?) {
        if (phone.value.length <= 1) phone.value = PhoneEntry.startingValue(region)
    }

    fun onPhoneChange(value: String) {
        phone.value = PhoneEntry.normalize(value)
    }

    /**
     * Digits only, cut at the code's length, and sent the moment it is
     * complete — which is what every Telegram client does, and what makes a
     * code pasted or filled in from the SMS a single step.
     */
    fun onCodeChange(value: String) {
        val length = uiState.value.auth.codeLength
        val digits = value.filter(Char::isDigit).let { if (length > 0) it.take(length) else it }
        val grew = digits.length > code.value.length
        code.value = digits
        if (grew && length > 0 && digits.length == length) {
            if (uiState.value.step == AuthStep.EmailCode) submitEmailCode() else submitCode()
        }
    }

    fun onEmailChange(value: String) {
        email.value = value.trim()
    }

    fun submitEmail() {
        if (!isPlausibleEmail(email.value)) return
        viewModelScope.launch {
            repository.submitEmailAddress(email.value)
            // The email code arrives next, into the same field the SMS code
            // uses; whatever was in it belonged to another step.
            if (repository.observeAuth().value.errorMessage == null) code.value = ""
        }
    }

    fun submitEmailCode() {
        if (code.value.isEmpty()) return
        viewModelScope.launch { repository.submitEmailCode(code.value) }
    }

    fun resetEmail() {
        viewModelScope.launch {
            repository.resetEmail()
            // A reset that lands goes on to a code by SMS: a fresh field.
            if (repository.observeAuth().value.state == AuthState.WaitCode) code.value = ""
        }
    }

    fun onPasswordChange(value: String) {
        password.value = value
    }

    fun submitPhone() {
        if (!PhoneEntry.isPossible(phone.value)) return
        viewModelScope.launch {
            repository.submitPhoneNumber(phone.value)
            // Refused — a mistyped number, a flood wait — keeps the phone step
            // up with the reason under the field.
            if (repository.observeAuth().value.errorMessage != null) return@launch
            // Back to the code step with a fresh code on its way; the old one
            // in the field belonged to the number that was just corrected.
            code.value = ""
            changingNumber.value = false
        }
    }

    fun submitCode() {
        if (code.value.isEmpty()) return
        viewModelScope.launch { repository.submitCode(code.value) }
    }

    fun submitPassword() {
        if (password.value.isEmpty()) return
        viewModelScope.launch { repository.submitPassword(password.value) }
    }

    fun resendCode() {
        viewModelScope.launch { repository.resendCode() }
    }

    /**
     * Sign in by scanning instead: the phone step's other way in. Back from
     * it is [onChangeNumber] — the same "not this way, the number" that the
     * code step has, and TDLib takes a phone number from the QR state too.
     */
    fun onQrLogin() {
        changingNumber.value = false
        viewModelScope.launch { repository.requestQrLogin() }
    }

    /** From the code step back to the number, which stays as typed. */
    fun onChangeNumber() {
        changingNumber.value = true
    }

    /** Back from correcting the number without sending it again. */
    fun onChangeNumberCancelled() {
        changingNumber.value = false
    }
}
