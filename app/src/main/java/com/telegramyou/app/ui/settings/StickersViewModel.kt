package com.telegramyou.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.telegramyou.app.i18n.tr
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.StickerSetInfo
import com.telegramyou.app.telegram.model.StickerSetPreview
import com.telegramyou.app.telegram.model.movedSet
import com.telegramyou.app.ui.failureText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StickersUiState(
    /** The account's sets, in its order; null until read. */
    val mine: List<StickerSetPreview>? = null,
    /** What Telegram is suggesting, without the ones already added. */
    val trending: List<StickerSetPreview> = emptyList(),
    /** A set's sheet, its info null while it loads. */
    val open: Long? = null,
    val openInfo: StickerSetInfo? = null,
    val message: String? = null
)

/**
 * Settings → Stickers (2.6): the account's sets, moved up and down and taken
 * off, and the ones Telegram is suggesting, added — as the official client's
 * sticker settings do.
 */
class StickersViewModel(
    private val repository: TelegramRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StickersUiState())
    val uiState: StateFlow<StickersUiState> = _uiState.asStateFlow()

    init {
        reload()
    }

    private fun reload() {
        viewModelScope.launch {
            var mine = emptyList<StickerSetPreview>()
            var trending = emptyList<StickerSetPreview>()
            attempt(tr("Could not load your stickers", "Не удалось загрузить стикеры")) {
                mine = repository.stickerSets()
                trending = repository.trendingStickerSets()
            }
            val ids = mine.map { it.id }.toSet()
            _uiState.update { it.copy(mine = mine, trending = trending.filterNot { set -> set.id in ids }) }
        }
    }

    /** One of the account's sets moved [by] places; the whole order goes back to Telegram. */
    fun onMove(setId: Long, by: Int) {
        val mine = _uiState.value.mine ?: return
        val order = movedSet(mine.map { it.id }, setId, by)
        if (order == mine.map { it.id }) return
        val byId = mine.associateBy { it.id }
        _uiState.update { it.copy(mine = order.mapNotNull(byId::get)) }
        viewModelScope.launch {
            if (!attempt(tr("Could not move the set", "Не удалось переместить набор")) { repository.reorderStickerSets(order) }) {
                reload()
            }
        }
    }

    /** A set added or taken off, from its row or its sheet. */
    fun onInstalledChange(setId: Long, installed: Boolean) {
        _uiState.update { it.copy(open = null, openInfo = null) }
        viewModelScope.launch {
            val done = attempt(tr("Could not change your stickers", "Не удалось изменить стикеры")) {
                repository.setStickerSetInstalled(setId, installed)
            }
            if (done) {
                _uiState.update {
                    it.copy(message = if (installed) tr("Stickers added", "Стикеры добавлены") else tr("Stickers removed", "Стикеры удалены"))
                }
            }
            reload()
        }
    }

    fun onOpen(setId: Long) {
        _uiState.update { it.copy(open = setId, openInfo = null) }
        viewModelScope.launch {
            var info: StickerSetInfo? = null
            attempt(tr("Could not open the sticker set", "Не удалось открыть набор стикеров")) {
                info = repository.stickerSetInfo(setId)
            }
            _uiState.update { state ->
                if (state.open != setId) state
                else if (info == null) state.copy(open = null)
                else state.copy(openInfo = info)
            }
        }
    }

    fun onClose() = _uiState.update { it.copy(open = null, openInfo = null) }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    private suspend fun attempt(action: String, request: suspend () -> Unit): Boolean =
        try {
            request()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.update { it.copy(message = failureText(action, e.message)) }
            false
        }
}
