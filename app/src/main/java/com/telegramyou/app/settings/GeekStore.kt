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

    init {
        AutoTranslate.allow(_settings.value.autoTranslate)
    }

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
            .putBoolean(KEY_MUSIC_LIBRARY, now.musicLibrary)
            .putBoolean(KEY_MESSAGE_EXTRAS, now.messageExtras)
            .putBoolean(KEY_ARCHIVE_ON_PULL, now.openArchiveOnPull)
            .putBoolean(KEY_HIDE_BLOCKED, now.hideBlockedInGroups)
            .putBoolean(KEY_CONFIRM_RECORDINGS, now.confirmRecordings)
            .putBoolean(KEY_SILENCE_NON_CONTACTS, now.silenceNonContacts)
            .putBoolean(KEY_AUTO_TRANSLATE, now.autoTranslate)
            .putBoolean(KEY_COMPOSER_CAPSULE, now.composerCapsule)
            .putBoolean(KEY_GROUP_FACES, now.groupFaces)
            .apply()
        AutoTranslate.allow(now.autoTranslate)
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
            preferIpv6 = preferences.getBoolean(KEY_IPV6, false),
            musicLibrary = preferences.getBoolean(KEY_MUSIC_LIBRARY, false),
            messageExtras = preferences.getBoolean(KEY_MESSAGE_EXTRAS, false),
            openArchiveOnPull = preferences.getBoolean(KEY_ARCHIVE_ON_PULL, false),
            hideBlockedInGroups = preferences.getBoolean(KEY_HIDE_BLOCKED, false),
            confirmRecordings = preferences.getBoolean(KEY_CONFIRM_RECORDINGS, false),
            silenceNonContacts = preferences.getBoolean(KEY_SILENCE_NON_CONTACTS, false),
            autoTranslate = preferences.getBoolean(KEY_AUTO_TRANSLATE, false),
            composerCapsule = preferences.getBoolean(KEY_COMPOSER_CAPSULE, false),
            groupFaces = preferences.getBoolean(KEY_GROUP_FACES, false)
        )
    }

    private companion object {
        const val NAME = "geeks"
        const val KEY_DOUBLE_TAP = "double_tap"
        const val KEY_AUTO_TRANSLATE = "auto_translate"
        const val KEY_COMPOSER_CAPSULE = "composer_capsule"
        const val KEY_GROUP_FACES = "group_faces"
        const val KEY_SECONDS = "show_seconds"
        const val KEY_DETAILS = "message_details"
        const val KEY_SAVE_MEDIA = "save_media"
        const val KEY_FORWARD_COPY = "forward_without_quote"
        const val KEY_HIDE_STORIES = "hide_stories"
        const val KEY_HIDE_ALL = "hide_all_chats_tab"
        const val KEY_SEARCH_NO_KEYBOARD = "search_without_keyboard"
        const val KEY_IPV6 = "prefer_ipv6"
        const val KEY_MUSIC_LIBRARY = "music_library"
        const val KEY_MESSAGE_EXTRAS = "message_extras"
        const val KEY_ARCHIVE_ON_PULL = "archive_on_pull"
        const val KEY_HIDE_BLOCKED = "hide_blocked_in_groups"
        const val KEY_CONFIRM_RECORDINGS = "confirm_recordings"
        const val KEY_SILENCE_NON_CONTACTS = "silence_non_contacts"
    }
}

/** The For geeks settings, for the screens they change. Defaults where nothing provides them. */
val LocalGeekSettings = staticCompositionLocalOf { GeekSettings() }
