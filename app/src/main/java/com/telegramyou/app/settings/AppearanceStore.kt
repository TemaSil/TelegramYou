package com.telegramyou.app.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Where the appearance choice is kept between launches.
 *
 * SharedPreferences rather than DataStore: a handful of values, read once at startup
 * and written when a switch is flipped. DataStore would be a dependency and a
 * coroutine boundary for something the platform already does synchronously in
 * memory after the first read.
 *
 * The flow is the source of truth for the whole app — the activity collects it
 * to pick a colour scheme, and the settings screen collects it to draw the
 * controls — so a change is on screen before the write to disk finishes.
 */
class AppearanceStore(context: Context) {

    private val preferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppearanceSettings> = _settings.asStateFlow()

    fun setTheme(choice: ThemeChoice) {
        _settings.update { it.copy(theme = choice) }
        preferences.edit().putString(KEY_THEME, choice.name).apply()
    }

    fun setDynamicColor(enabled: Boolean) {
        _settings.update { it.copy(dynamicColor = enabled) }
        preferences.edit().putBoolean(KEY_DYNAMIC, enabled).apply()
    }

    fun setShapedAvatars(enabled: Boolean) {
        _settings.update { it.copy(shapedAvatars = enabled) }
        preferences.edit().putBoolean(KEY_SHAPED_AVATARS, enabled).apply()
    }

    fun setTextScale(scale: Float) {
        val step = TextSize.nearest(scale)
        _settings.update { it.copy(textScale = step) }
        preferences.edit().putFloat(KEY_TEXT_SCALE, step).apply()
    }

    private fun read(): AppearanceSettings {
        val stored = preferences.getString(KEY_THEME, null)
        return AppearanceSettings(
            // An unrecognised value means a downgrade or a corrupt file, and
            // the default is a better answer than a crash on startup.
            theme = ThemeChoice.entries.firstOrNull { it.name == stored } ?: ThemeChoice.System,
            dynamicColor = preferences.getBoolean(KEY_DYNAMIC, true),
            shapedAvatars = preferences.getBoolean(KEY_SHAPED_AVATARS, true),
            textScale = TextSize.nearest(preferences.getFloat(KEY_TEXT_SCALE, 1f))
        )
    }

    private companion object {
        const val NAME = "appearance"
        const val KEY_THEME = "theme"
        const val KEY_DYNAMIC = "dynamic_color"
        const val KEY_SHAPED_AVATARS = "shaped_avatars"
        const val KEY_TEXT_SCALE = "text_scale"
    }
}
