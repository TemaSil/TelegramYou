package com.telegramyou.app.ui.proxy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.ProxyDraft
import com.telegramyou.app.telegram.model.ProxyServer
import com.telegramyou.app.telegram.model.parseProxyLink
import com.telegramyou.app.ui.failureText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How a proxy answered being pinged. */
sealed interface ProxyPing {
    data object Checking : ProxyPing
    data class Answered(val millis: Long) : ProxyPing
    data object Silent : ProxyPing
}

data class ProxyUiState(
    val proxies: List<ProxyServer> = emptyList(),
    val pings: Map<Int, ProxyPing> = emptyMap(),
    val isLoading: Boolean = true,
    /** The add sheet is up, with this in it. */
    val draft: ProxyDraft? = null,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
) {
    val isUsingProxy: Boolean get() = proxies.any { it.isEnabled }
}

class ProxyViewModel(
    private val repository: TelegramRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProxyUiState())
    val uiState: StateFlow<ProxyUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /**
     * The list again, then a ping for each. Pinged every time rather than
     * remembered: whether a proxy answers is exactly what changes.
     */
    private fun refresh() {
        viewModelScope.launch {
            var list: List<ProxyServer> = emptyList()
            attempt("Could not read the proxies") { list = repository.proxies() }
            _uiState.update { state ->
                state.copy(
                    proxies = list,
                    isLoading = false,
                    pings = list.associate { it.id to (state.pings[it.id] ?: ProxyPing.Checking) }
                )
            }
            list.forEach { proxy ->
                launch {
                    val millis = try {
                        repository.pingProxy(proxy)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                    val ping = millis?.let { ProxyPing.Answered(it) } ?: ProxyPing.Silent
                    _uiState.update { it.copy(pings = it.pings + (proxy.id to ping)) }
                }
            }
        }
    }

    /**
     * The switch at the top. Off connects directly; on goes back to the
     * first proxy on the list, since TDLib keeps no "last used" to return to
     * once it has been switched off.
     */
    fun onUseProxyChange(use: Boolean) {
        val first = _uiState.value.proxies.firstOrNull() ?: return
        change("Could not switch the proxy") {
            if (use) repository.enableProxy(first.id) else repository.disableProxy()
        }
    }

    fun onSelect(id: Int) = change("Could not switch the proxy") { repository.enableProxy(id) }

    fun onRemove(id: Int) = change("Could not remove the proxy") { repository.removeProxy(id) }

    fun onAddOpen() = _uiState.update { it.copy(draft = ProxyDraft()) }

    fun onAddDismiss() = _uiState.update { it.copy(draft = null) }

    /**
     * An edit to the form, as a change rather than a finished draft. Applied
     * to the draft as it is now, so two fields changed before the screen has
     * redrawn — autofill, a paste, a test typing fast — cannot each write
     * back a copy from before the other and undo it. That happened: the
     * smoke test filled three fields and only the last one stayed.
     */
    fun onDraftChange(edit: (ProxyDraft) -> ProxyDraft) =
        _uiState.update { state -> state.copy(draft = state.draft?.let(edit)) }

    /** A link pasted into the sheet fills every field it names. */
    fun onLinkPasted(text: String) {
        val parsed = parseProxyLink(text)
        if (parsed == null) {
            _uiState.update { it.copy(errorMessage = "That is not a proxy link") }
            return
        }
        _uiState.update { it.copy(draft = parsed) }
    }

    /** Saved and switched to at once: a proxy is added to be used. */
    fun onSave() {
        val server = _uiState.value.draft?.toServer() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val saved = attempt("Could not add the proxy") { repository.addProxy(server, enable = true) }
            _uiState.update { it.copy(isSaving = false, draft = if (saved) null else it.draft) }
            refresh()
        }
    }

    fun onErrorShown() = _uiState.update { it.copy(errorMessage = null) }

    private fun change(failure: String, request: suspend () -> Unit) {
        viewModelScope.launch {
            attempt(failure, request)
            refresh()
        }
    }

    private suspend fun attempt(failure: String, request: suspend () -> Unit): Boolean =
        try {
            request()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = failureText(failure, e.message)) }
            false
        }
}
