package com.telegramyou.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.ActiveSession
import com.telegramyou.app.telegram.model.inDisplayOrder
import com.telegramyou.app.telegram.model.title
import com.telegramyou.app.ui.failureText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DevicesUiState(
    val sessions: List<ActiveSession> = emptyList(),
    val isLoading: Boolean = true,
    /** The session whose "end it?" dialog is up. */
    val confirming: ActiveSession? = null,
    /** The "end all the others?" dialog is up. */
    val confirmingAll: Boolean = false,
    val isWorking: Boolean = false,
    /** Said once, in a snackbar, then cleared. */
    val message: String? = null
) {
    val current: ActiveSession? get() = sessions.firstOrNull { it.isCurrent }
    val others: List<ActiveSession> get() = sessions.filterNot { it.isCurrent }
}

class DevicesViewModel(
    private val repository: TelegramRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DevicesUiState())
    val uiState: StateFlow<DevicesUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch {
            var list = emptyList<ActiveSession>()
            attempt("Could not read the devices") { list = repository.activeSessions() }
            _uiState.update { it.copy(sessions = list.inDisplayOrder(), isLoading = false) }
        }
    }

    fun onSessionSelected(session: ActiveSession) {
        if (session.isCurrent) return
        _uiState.update { it.copy(confirming = session) }
    }

    fun onTerminateAllRequested() = _uiState.update { it.copy(confirmingAll = true) }

    fun onDismiss() = _uiState.update { it.copy(confirming = null, confirmingAll = false) }

    fun onTerminateConfirmed() {
        val session = _uiState.value.confirming ?: return
        _uiState.update { it.copy(confirming = null, isWorking = true) }
        viewModelScope.launch {
            val done = attempt("Could not end that session") { repository.terminateSession(session.id) }
            _uiState.update { state ->
                if (done) {
                    state.copy(
                        sessions = state.sessions.filterNot { it.id == session.id },
                        isWorking = false,
                        message = "Signed out of ${session.title()}"
                    )
                } else {
                    state.copy(isWorking = false)
                }
            }
        }
    }

    fun onTerminateAllConfirmed() {
        _uiState.update { it.copy(confirmingAll = false, isWorking = true) }
        viewModelScope.launch {
            val done = attempt("Could not end the other sessions") { repository.terminateOtherSessions() }
            _uiState.update { state ->
                if (done) {
                    state.copy(
                        sessions = state.sessions.filter { it.isCurrent },
                        isWorking = false,
                        message = "Signed out of every other device"
                    )
                } else {
                    state.copy(isWorking = false)
                }
            }
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    private suspend fun attempt(failure: String, request: suspend () -> Unit): Boolean =
        try {
            request()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.update { it.copy(message = failureText(failure, e.message)) }
            false
        }
}
