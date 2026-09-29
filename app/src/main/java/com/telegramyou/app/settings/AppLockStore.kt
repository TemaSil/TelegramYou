package com.telegramyou.app.settings

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The app lock: its settings, kept in their own preferences file, and
 * whether the app is locked right now.
 *
 * Locked from the start whenever a PIN is set — a cold start always asks —
 * and locked again when the activity comes back after longer away than
 * [AppLockSettings.autoLock] allows; see [onLeft] and [onReturned]. The time
 * away is measured on the clock that does not jump when the wall clock is
 * changed, so setting the phone's time back is not a way past it.
 */
class AppLockStore(context: Context) {

    private val preferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppLockSettings> = _settings.asStateFlow()

    private val _locked = MutableStateFlow(_settings.value.enabled)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var leftAt: Long? = null

    /** A new PIN, or a changed one. The app stays unlocked: whoever set it knows it. */
    fun setPin(pin: String) {
        require(AppLock.isValidPin(pin))
        val salt = AppLock.newSalt()
        val hash = AppLock.hash(pin, salt)
        _settings.update { it.copy(pinHash = hash, pinSalt = salt, pinLength = pin.length) }
        preferences.edit()
            .putString(KEY_HASH, hash)
            .putString(KEY_SALT, salt)
            .putInt(KEY_LENGTH, pin.length)
            .apply()
    }

    /** The lock off, and the PIN forgotten with it. */
    fun disable() {
        _settings.update { it.copy(pinHash = null, pinSalt = null, pinLength = 0) }
        _locked.value = false
        preferences.edit().remove(KEY_HASH).remove(KEY_SALT).remove(KEY_LENGTH).apply()
    }

    fun setBiometric(enabled: Boolean) {
        _settings.update { it.copy(biometric = enabled) }
        preferences.edit().putBoolean(KEY_BIOMETRIC, enabled).apply()
    }

    fun setAutoLock(autoLock: AutoLock) {
        _settings.update { it.copy(autoLock = autoLock) }
        preferences.edit().putString(KEY_AUTO_LOCK, autoLock.name).apply()
    }

    fun setHideInRecents(hide: Boolean) {
        _settings.update { it.copy(hideInRecents = hide) }
        preferences.edit().putBoolean(KEY_HIDE, hide).apply()
    }

    /** Unlocks if [pin] is the one; answers whether it was. Slow on purpose — call it off the main thread. */
    fun tryUnlock(pin: String): Boolean {
        val right = AppLock.matches(pin, _settings.value)
        if (right) _locked.value = false
        return right
    }

    /** The fingerprint, or whatever the phone's own prompt accepted. */
    fun unlockWithBiometric() {
        _locked.value = false
    }

    /** The activity has left the screen — not for a rotation, which is not leaving. */
    fun onLeft() {
        leftAt = SystemClock.elapsedRealtime()
    }

    /** The activity is back: locked again if it was away long enough. */
    fun onReturned() {
        val away = leftAt ?: return
        if (AppLock.shouldLock(_settings.value, away, SystemClock.elapsedRealtime())) _locked.value = true
    }

    private fun read(): AppLockSettings {
        val hash = preferences.getString(KEY_HASH, null)
        val salt = preferences.getString(KEY_SALT, null)
        return AppLockSettings(
            pinHash = hash,
            pinSalt = salt,
            pinLength = preferences.getInt(KEY_LENGTH, 0),
            biometric = preferences.getBoolean(KEY_BIOMETRIC, true),
            autoLock = preferences.getString(KEY_AUTO_LOCK, null)
                .let { name -> AutoLock.entries.firstOrNull { it.name == name } }
                ?: AutoLock.Immediately,
            hideInRecents = preferences.getBoolean(KEY_HIDE, true)
        )
    }

    private companion object {
        const val NAME = "app_lock"
        const val KEY_HASH = "pin_hash"
        const val KEY_SALT = "pin_salt"
        const val KEY_LENGTH = "pin_length"
        const val KEY_BIOMETRIC = "biometric"
        const val KEY_AUTO_LOCK = "auto_lock"
        const val KEY_HIDE = "hide_in_recents"
    }
}
