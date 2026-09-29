package com.telegramyou.app.settings

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Settings → Privacy and security → App lock: a PIN in front of the app,
 * the fingerprint instead of it where the phone has one, after how long
 * away it asks again, and whether the app's picture in Recents is hidden.
 *
 * The PIN itself is never kept — only [pinHash], a PBKDF2 of it with its own
 * [pinSalt] — so the file it lives in says nothing about it.
 */
data class AppLockSettings(
    val pinHash: String? = null,
    val pinSalt: String? = null,
    /**
     * How many digits the PIN has, so the lock screen checks it the moment
     * the last one is typed rather than on a button. Says less than it
     * seems: four to six is the whole range.
     */
    val pinLength: Int = 0,
    val biometric: Boolean = true,
    val autoLock: AutoLock = AutoLock.Immediately,
    val hideInRecents: Boolean = true
) {
    val enabled: Boolean get() = pinHash != null && pinSalt != null
}

/** How long the app may be away before it asks again. */
enum class AutoLock(val label: String, val seconds: Long) {
    Immediately("Immediately", 0),
    OneMinute("After 1 minute", 60),
    FiveMinutes("After 5 minutes", 5 * 60),
    OneHour("After 1 hour", 60 * 60)
}

object AppLock {
    const val MIN_PIN = 4
    const val MAX_PIN = 6

    /** A PIN is digits, four to six of them. */
    fun isValidPin(pin: String): Boolean = pin.length in MIN_PIN..MAX_PIN && pin.all(Char::isDigit)

    /** A fresh salt, Base64, for a PIN being set. */
    fun newSalt(random: SecureRandom = SecureRandom()): String =
        Base64.getEncoder().encodeToString(ByteArray(SALT_BYTES).also(random::nextBytes))

    /** [pin] under [salt], as it is kept. */
    fun hash(pin: String, salt: String): String {
        val spec = PBEKeySpec(pin.toCharArray(), Base64.getDecoder().decode(salt), ITERATIONS, KEY_BITS)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return Base64.getEncoder().encodeToString(key)
    }

    /** Whether [pin] is the one [settings] keeps, compared without leaking where it differs. */
    fun matches(pin: String, settings: AppLockSettings): Boolean {
        val hash = settings.pinHash ?: return false
        val salt = settings.pinSalt ?: return false
        val tried = hash(pin, salt).toByteArray()
        val kept = hash.toByteArray()
        if (tried.size != kept.size) return false
        var difference = 0
        for (i in tried.indices) difference = difference or (tried[i].toInt() xor kept[i].toInt())
        return difference == 0
    }

    /**
     * Whether coming back at [nowMillis] asks for the PIN, having left at
     * [leftAtMillis] (null for a cold start, which always asks).
     */
    fun shouldLock(settings: AppLockSettings, leftAtMillis: Long?, nowMillis: Long): Boolean {
        if (!settings.enabled) return false
        if (leftAtMillis == null) return true
        return nowMillis - leftAtMillis >= settings.autoLock.seconds * 1000
    }

    private const val SALT_BYTES = 16
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
}
