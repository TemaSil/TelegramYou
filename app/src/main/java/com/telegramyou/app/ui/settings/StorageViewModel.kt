package com.telegramyou.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.StorageKind
import com.telegramyou.app.telegram.model.StorageUsage
import com.telegramyou.app.telegram.model.bytesLabel
import com.telegramyou.app.ui.failureText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StorageUiState(
    val usage: StorageUsage? = null,
    /**
     * What clearing would take. Everything but profile photos and stickers
     * to start with — the two that come straight back the moment the chat
     * list or the sticker sheet is opened, so clearing them saves little.
     */
    val selected: Set<StorageKind> = emptySet(),
    val isClearing: Boolean = false,
    /** The "clear this much?" dialog is up. */
    val confirming: Boolean = false,
    val message: String? = null
) {
    val isLoading: Boolean get() = usage == null

    /** What clearing the selection would free. */
    val selectedBytes: Long
        get() = usage?.slices.orEmpty().filter { it.kind in selected }.sumOf { it.bytes }
}

class StorageViewModel(
    private val repository: TelegramRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StorageUiState())
    val uiState: StateFlow<StorageUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            var usage: StorageUsage? = null
            attempt("Could not measure the cache") { usage = repository.storageUsage() }
            val found = usage ?: StorageUsage(emptyList())
            _uiState.update {
                it.copy(
                    usage = found,
                    selected = found.slices.map { slice -> slice.kind }.toSet() - KEPT_BY_DEFAULT
                )
            }
        }
    }

    fun onKindToggle(kind: StorageKind) = _uiState.update {
        it.copy(selected = if (kind in it.selected) it.selected - kind else it.selected + kind)
    }

    fun onClearRequested() {
        if (_uiState.value.selectedBytes > 0) _uiState.update { it.copy(confirming = true) }
    }

    fun onDismiss() = _uiState.update { it.copy(confirming = false) }

    fun onClearConfirmed() {
        val state = _uiState.value
        val kinds = state.selected
        val freeing = state.selectedBytes
        _uiState.update { it.copy(confirming = false, isClearing = true) }
        viewModelScope.launch {
            var after: StorageUsage? = null
            attempt("Could not clear the cache") { after = repository.clearCache(kinds) }
            _uiState.update { current ->
                val usage = after
                if (usage == null) {
                    current.copy(isClearing = false)
                } else {
                    current.copy(
                        usage = usage,
                        isClearing = false,
                        selected = current.selected.filter { kind -> usage.slices.any { it.kind == kind } }.toSet(),
                        message = "Cleared ${bytesLabel(freeing)}"
                    )
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

    private companion object {
        val KEPT_BY_DEFAULT = setOf(StorageKind.ProfilePhotos, StorageKind.Stickers)
    }
}
