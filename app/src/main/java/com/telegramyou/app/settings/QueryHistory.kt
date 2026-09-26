package com.telegramyou.app.settings

import android.content.Context
import com.telegramyou.app.telegram.model.remembering
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The queries typed into search, newest first. Telegram keeps the chats
 * found from search on its side; what was typed to find them is this
 * device's, so it is kept here. The arithmetic is `remembering` in :core.
 */
interface QueryHistory {
    val queries: StateFlow<List<String>>
    fun remember(query: String)
    fun forget(query: String)
    fun clear()
}

/** For tests, and for anything without a Context: forgets on exit. */
open class InMemoryQueryHistory(initial: List<String> = emptyList()) : QueryHistory {
    protected val state = MutableStateFlow(initial)
    override val queries: StateFlow<List<String>> = state.asStateFlow()
    override fun remember(query: String) = change { it.remembering(query) }
    override fun forget(query: String) = change { list -> list.filterNot { it == query } }
    override fun clear() = change { emptyList() }
    protected open fun change(transform: (List<String>) -> List<String>) = state.update(transform)
}

/** SharedPreferences, one line per query, in a file of its own. */
class QueryHistoryStore(context: Context) : InMemoryQueryHistory() {

    private val preferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    init {
        state.value = preferences.getString(KEY, null)
            ?.split('\n')
            ?.filter { it.isNotBlank() }
            .orEmpty()
    }

    override fun change(transform: (List<String>) -> List<String>) {
        super.change(transform)
        preferences.edit().putString(KEY, state.value.joinToString("\n")).apply()
    }

    private companion object {
        const val NAME = "search"
        const val KEY = "recent_queries"
    }
}
