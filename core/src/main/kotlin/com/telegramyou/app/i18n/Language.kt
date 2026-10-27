package com.telegramyou.app.i18n

import java.util.Locale

/**
 * The app's two languages (2.6): English, and Russian.
 *
 * Every line the app shows is written where it is used as a pair —
 * `tr("Profile colour", "Цвет профиля")` — rather than moved into
 * strings.xml under a key. There are more than a thousand of them, many
 * built outside composition (settings' builders, state holders, this pure
 * module, which has no Android resources at all), and a pair read in place
 * keeps the two languages side by side for whoever edits either. What the
 * system itself reads — notification channels, widgets — stays in
 * strings.xml and values-ru, which is also what tells Android 13 to offer
 * Russian among the app's languages.
 *
 * The language is the process's locale: the phone's, or on Android 13 the
 * one picked for this app alone in the system's settings, which Android
 * makes the process's default and recreates the screens for.
 */
fun isRussian(locale: Locale = Locale.getDefault()): Boolean = locale.language == "ru"

/** [en] or [ru], by the language in use. */
fun tr(en: String, ru: String): String = if (isRussian()) ru else en

/** [en] or [ru], by [locale] — for the code that is handed one. */
fun tr(en: String, ru: String, locale: Locale): String = if (isRussian(locale)) ru else en

/**
 * Which of Russian's three forms goes with [n]: 1, 21, 101 take the first
 * ("сообщение"); 2–4, 22–24 the second ("сообщения"); 0, 5–20, 25–30 and
 * every teen the third ("сообщений").
 */
fun ruForm(n: Long, one: String, few: String, many: String): String {
    val abs = kotlin.math.abs(n)
    val lastTwo = abs % 100
    val last = abs % 10
    return when {
        lastTwo in 11..14 -> many
        last == 1L -> one
        last in 2..4 -> few
        else -> many
    }
}

/**
 * A count with its noun, in the language in use: "1 member", "12 members",
 * "1 участник", "3 участника", "12 участников".
 */
fun counted(
    n: Long,
    enOne: String,
    enOther: String,
    ruOne: String,
    ruFew: String,
    ruMany: String
): String = if (isRussian()) "$n ${ruForm(n, ruOne, ruFew, ruMany)}" else "$n ${if (n == 1L) enOne else enOther}"

/** The same for an Int. */
fun counted(n: Int, enOne: String, enOther: String, ruOne: String, ruFew: String, ruMany: String): String =
    counted(n.toLong(), enOne, enOther, ruOne, ruFew, ruMany)
