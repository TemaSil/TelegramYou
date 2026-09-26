package com.telegramyou.app.settings

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Where Settings → For geeks is kept. SharedPreferences, for the reasons
 * AppearanceStore gives; a separate file so the two screens' settings do not
 * share one namespace of keys.
 */
class GeekStore(context: Context) {

    private val preferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<GeekSettings> = _settings.asStateFlow()

    fun update(change: (GeekSettings) -> GeekSettings) {
        _settings.update(change)
        val now = _settings.value
        preferences.edit()
            .putString(KEY_DOUBLE_TAP, now.doubleTap.name)
            .putBoolean(KEY_SECONDS, now.showSeconds)
            .putBoolean(KEY_DETAILS, now.messageDetails)
            .putBoolean(KEY_SAVE_MEDIA, now.saveMedia)
            .putBoolean(KEY_FORWARD_COPY, now.forwardWithoutQuote)
            .putBoolean(KEY_HIDE_STORIES, now.hideStories)
            .putBoolean(KEY_HIDE_ALL, now.hideAllChatsTab)
            .putBoolean(KEY_SEARCH_NO_KEYBOARD, now.searchWithoutKeyboard)
            .putBoolean(KEY_IPV6, now.preferIpv6)
            .apply()
    }

    private fun read(): GeekSettings {
        val tap = preferences.getString(KEY_DOUBLE_TAP, null)
        return GeekSettings(
            doubleTap = DoubleTapAction.entries.firstOrNull { it.name == tap } ?: DoubleTapAction.Nothing,
            showSeconds = preferences.getBoolean(KEY_SECONDS, false),
            messageDetails = preferences.getBoolean(KEY_DETAILS, false),
            saveMedia = preferences.getBoolean(KEY_SAVE_MEDIA, false),
            forwardWithoutQuote = preferences.getBoolean(KEY_FORWARD_COPY, false),
            hideStories = preferences.getBoolean(KEY_HIDE_STORIES, false),
            hideAllChatsTab = preferences.getBoolean(KEY_HIDE_ALL, false),
            searchWithoutKeyboard = preferences.getBoolean(KEY_SEARCH_NO_KEYBOARD, false),
            preferIpv6 = preferences.getBoolean(KEY_IPV6, false)
        )
    }

    private companion object {
        const val NAME = "geeks"
        const val KEY_DOUBLE_TAP = "double_tap"
        const val KEY_SECONDS = "show_seconds"
        const val KEY_DETAILS = "message_details"
        const val KEY_SAVE_MEDIA = "save_media"
        const val KEY_FORWARD_COPY = "forward_without_quote"
        const val KEY_HIDE_STORIES = "hide_stories"
        const val KEY_HIDE_ALL = "hide_all_chats_tab"
        const val KEY_SEARCH_NO_KEYBOARD = "search_without_keyboard"
        const val KEY_IPV6 = "prefer_ipv6"
    }
}

/** The For geeks settings, for the screens they change. Defaults where nothing provides them. */
val LocalGeekSettings = staticCompositionLocalOf { GeekSettings() }
