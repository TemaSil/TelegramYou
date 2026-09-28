package com.telegramyou.app.settings

import android.content.Context
import com.telegramyou.app.ui.theme.Accents
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

    fun setAccent(seed: Int) {
        _settings.update { it.copy(accent = seed) }
        preferences.edit().putInt(KEY_ACCENT, seed).apply()
    }

    fun setPureBlack(enabled: Boolean) {
        _settings.update { it.copy(pureBlack = enabled) }
        preferences.edit().putBoolean(KEY_PURE_BLACK, enabled).apply()
    }

    fun setChatColorsFromAvatar(enabled: Boolean) {
        _settings.update { it.copy(chatColorsFromAvatar = enabled) }
        preferences.edit().putBoolean(KEY_CHAT_COLORS, enabled).apply()
    }

    fun setChatWallpaper(wallpaper: ChatWallpaper) {
        _settings.update { it.copy(chatWallpaper = wallpaper) }
        preferences.edit().putString(KEY_WALLPAPER, wallpaper.name).apply()
    }

    fun setOutgoingTone(tone: OutgoingTone) {
        _settings.update { it.copy(outgoingTone = tone) }
        preferences.edit().putString(KEY_OUTGOING_TONE, tone.name).apply()
    }

    fun setBubbleCorners(corners: Int) {
        val settled = BubbleCorners.settle(corners.toFloat())
        _settings.update { it.copy(bubbleCorners = settled) }
        preferences.edit().putInt(KEY_BUBBLE_CORNERS, settled).apply()
    }

    fun setMessageTextScale(scale: Float) {
        val step = TextSize.nearest(scale)
        _settings.update { it.copy(messageTextScale = step) }
        preferences.edit().putFloat(KEY_MESSAGE_TEXT_SCALE, step).apply()
    }

    private fun read(): AppearanceSettings {
        val stored = preferences.getString(KEY_THEME, null)
        return AppearanceSettings(
            // An unrecognised value means a downgrade or a corrupt file, and
            // the default is a better answer than a crash on startup.
            theme = ThemeChoice.entries.firstOrNull { it.name == stored } ?: ThemeChoice.System,
            dynamicColor = preferences.getBoolean(KEY_DYNAMIC, true),
            shapedAvatars = preferences.getBoolean(KEY_SHAPED_AVATARS, true),
            textScale = TextSize.nearest(preferences.getFloat(KEY_TEXT_SCALE, 1f)),
            accent = preferences.getInt(KEY_ACCENT, Accents.TEAL),
            pureBlack = preferences.getBoolean(KEY_PURE_BLACK, false),
            chatColorsFromAvatar = preferences.getBoolean(KEY_CHAT_COLORS, false),
            chatWallpaper = preferences.getString(KEY_WALLPAPER, null)
                .let { name -> ChatWallpaper.entries.firstOrNull { it.name == name } }
                ?: ChatWallpaper.Gradient,
            outgoingTone = preferences.getString(KEY_OUTGOING_TONE, null)
                .let { name -> OutgoingTone.entries.firstOrNull { it.name == name } }
                ?: OutgoingTone.Accent,
            bubbleCorners = BubbleCorners.settle(
                preferences.getInt(KEY_BUBBLE_CORNERS, BubbleCorners.DEFAULT).toFloat()
            ),
            messageTextScale = TextSize.nearest(preferences.getFloat(KEY_MESSAGE_TEXT_SCALE, 1f))
        )
    }

    private companion object {
        const val NAME = "appearance"
        const val KEY_THEME = "theme"
        const val KEY_DYNAMIC = "dynamic_color"
        const val KEY_SHAPED_AVATARS = "shaped_avatars"
        const val KEY_TEXT_SCALE = "text_scale"
        const val KEY_ACCENT = "accent"
        const val KEY_PURE_BLACK = "pure_black"
        const val KEY_CHAT_COLORS = "chat_colors_from_avatar"
        const val KEY_WALLPAPER = "chat_wallpaper"
        const val KEY_OUTGOING_TONE = "outgoing_tone"
        const val KEY_BUBBLE_CORNERS = "bubble_corners"
        const val KEY_MESSAGE_TEXT_SCALE = "message_text_scale"
    }
}
