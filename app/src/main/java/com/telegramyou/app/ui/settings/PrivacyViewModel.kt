package com.telegramyou.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.PrivacyAudience
import com.telegramyou.app.telegram.model.PrivacyRules
import com.telegramyou.app.telegram.model.PrivacySetting
import com.telegramyou.app.ui.failureText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PrivacyUiState(
    val rules: Map<PrivacySetting, PrivacyRules> = emptyMap(),
    val isLoading: Boolean = true,
    /** The setting whose choice dialog is up. */
    val editing: PrivacySetting? = null,
    val message: String? = null
)

class PrivacyViewModel(
    private val repository: TelegramRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrivacyUiState())
    val uiState: StateFlow<PrivacyUiState> = _uiState.asStateFlow()

    init {
        // All at once: eight small requests, and a screen that fills in row by
        // row reads as broken.
        viewModelScope.launch {
            val loaded = PrivacySetting.entries.map { setting ->
                async {
                    try {
                        setting to repository.privacyRules(setting)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                }
            }.awaitAll().filterNotNull().toMap()
            _uiState.update {
                it.copy(
                    rules = loaded,
                    isLoading = false,
                    message = if (loaded.size < PrivacySetting.entries.size) "Some settings could not be read" else null
                )
            }
        }
    }

    fun onEdit(setting: PrivacySetting) = _uiState.update { it.copy(editing = setting) }

    fun onDismiss() = _uiState.update { it.copy(editing = null) }

    /**
     * Drawn at once and sent after; put back with a word if the server
     * refuses. The exceptions go with it unchanged — see TelegramPrivacy.
     */
    fun onAudienceChosen(setting: PrivacySetting, audience: PrivacyAudience) {
        val before = _uiState.value.rules[setting] ?: PrivacyRules(PrivacyAudience.Everybody)
        val after = before.copy(audience = audience)
        _uiState.update { it.copy(editing = null, rules = it.rules + (setting to after)) }
        if (after == before) return
        viewModelScope.launch {
            try {
                repository.setPrivacyRules(setting, after)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        rules = it.rules + (setting to before),
                        message = failureText("Could not change ${setting.title.lowercase()}", e.message)
                    )
                }
            }
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }
}
