package com.telegramyou.app

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The last crash, kept on the phone so it can be read and copied from
 * Settings → For geeks → Last crash.
 *
 * There for the crashes CI cannot reach: CI drives the demo, and a crash
 * that only the live client has — a real account's music, its covers, its
 * files — was otherwise a report of "it closed after a while" with nothing
 * to go on. Written only to this app's own storage and never sent anywhere;
 * the person decides whether to copy it.
 */
object CrashLog {
    private const val FILE = "last-crash.txt"

    fun install(context: Context) {
        val file = File(context.filesDir, FILE)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                file.writeText(
                    buildString {
                        appendLine("TelegramYou ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
                        appendLine(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
                        appendLine("Thread: ${thread.name}")
                        appendLine()
                        append(error.stackTraceToString())
                    }
                )
            }
            // The system's own handling after, so the app still closes and
            // Android still reports it as it always would.
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? =
        File(context.filesDir, FILE).takeIf { it.exists() }?.let { runCatching { it.readText() }.getOrNull() }

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }
}
