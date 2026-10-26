package com.telegramyou.app.ui.home

import android.content.Context

/**
 * Which day's birthday card was put away, so it stays away for that day and
 * the next day's still shows; see birthdayCardKey.
 */
interface BirthdayDismissals {
    fun isDismissed(key: String): Boolean
    fun dismiss(key: String)
}

/** For tests and previews: forgotten with the process. */
class InMemoryBirthdayDismissals : BirthdayDismissals {
    private var dismissed: String? = null
    override fun isDismissed(key: String) = dismissed == key
    override fun dismiss(key: String) {
        dismissed = key
    }
}

/** On this device: one key, the last day put away, replaced the next time. */
class PrefsBirthdayDismissals(context: Context) : BirthdayDismissals {
    private val prefs = context.applicationContext.getSharedPreferences("birthday-card", Context.MODE_PRIVATE)
    override fun isDismissed(key: String) = prefs.getString(KEY, null) == key
    override fun dismiss(key: String) {
        prefs.edit().putString(KEY, key).apply()
    }

    private companion object {
        const val KEY = "dismissed"
    }
}
