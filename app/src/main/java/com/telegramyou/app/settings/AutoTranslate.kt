package com.telegramyou.app.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The chats whose incoming messages are shown translated (2.0). Turned on
 * in a chat's info and read by its conversation, which are two screens with
 * two view models: kept here, process-wide, rather than passed between
 * them. On the phone only — Telegram has no such setting to sync.
 */
object AutoTranslate {
    private const val NAME = "auto_translate"
    private const val KEY_CHATS = "chats"
    private var preferences: SharedPreferences? = null
    private val _chats = MutableStateFlow<Set<Long>>(emptySet())
    val chats: StateFlow<Set<Long>> = _chats.asStateFlow()

    fun install(context: Context) {
        val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        preferences = prefs
        _chats.value = prefs.getStringSet(KEY_CHATS, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
    }

    fun set(chatId: Long, on: Boolean) {
        val now = if (on) _chats.value + chatId else _chats.value - chatId
        _chats.value = now
        preferences?.edit()?.putStringSet(KEY_CHATS, now.map { it.toString() }.toSet())?.apply()
    }
}
