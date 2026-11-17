package com.telegramyou.app.ui.chat

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.snapshots.SnapshotStateMap

/**
 * Small, account-scoped profile positions, independent of navigation entry ids.
 * A new visit gets a new entry and new automatic rememberSaveable keys; explicit
 * chat/page keys keep the selected tab and scroll after that entry is popped.
 */
@Stable
class ProfileNavigationState private constructor(
    private val values: SnapshotStateMap<String, List<Int>>
) {
    constructor() : this(mutableStateMapOf())

    fun page(chatId: Long): Int = values["$chatId:page"]?.firstOrNull() ?: 0

    fun select(chatId: Long, page: Int) {
        values["$chatId:page"] = listOf(page)
    }

    /** Info, SharedMediaKind pages, then common groups for private chats. */
    fun scroll(chatId: Long, page: Int): Pair<Int, Int> {
        val value = values["$chatId:scroll:$page"]
        return (value?.getOrNull(0) ?: 0) to (value?.getOrNull(1) ?: 0)
    }

    fun scrollTo(chatId: Long, page: Int, position: Pair<Int, Int>) {
        values["$chatId:scroll:$page"] = listOf(position.first, position.second)
    }

    companion object {
        val Saver = mapSaver(
            save = { state: ProfileNavigationState -> state.values.toMap() },
            restore = { saved ->
                ProfileNavigationState(mutableStateMapOf<String, List<Int>>().apply {
                    saved.forEach { (key, value) ->
                        val numbers = (value as? List<*>)?.mapNotNull { it as? Int }
                        if (!numbers.isNullOrEmpty()) put(key, numbers)
                    }
                })
            }
        )
    }
}
