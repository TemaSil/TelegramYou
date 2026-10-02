package com.telegramyou.app.music

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * What the music player did and why, the last [KEEP] things, for
 * Settings → For geeks → Diagnostics → Player log.
 *
 * There for the owner's report of 1.7: tracks that sometimes skip or stop
 * by themselves. Most of the ways music stops are not this app's — a call
 * takes the audio, headphones come out, a car's button is pressed — and
 * the rest are failures that used to pass in silence. Each is written here
 * with the reason Media3 gives, so a stop can be told from a skip and the
 * system's doing from ours. In a file, so a stop that came with the process
 * being killed still has its lead-up. Kept on the phone, never sent.
 */
object PlayerLog {
    private const val FILE = "player-log.txt"
    private const val KEEP = 300
    private val writer = Executors.newSingleThreadExecutor()
    private val clock = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)
    @Volatile private var file: File? = null

    fun install(context: Context) {
        file = File(context.filesDir, FILE)
    }

    fun add(line: String) {
        val target = file ?: return
        val stamped = "${clock.format(Date())}  $line"
        writer.execute {
            runCatching {
                val lines = if (target.exists()) target.readLines() else emptyList()
                target.writeText((lines + stamped).takeLast(KEEP).joinToString("\n", postfix = "\n"))
            }
        }
    }

    fun read(context: Context): String? =
        File(context.filesDir, FILE).takeIf { it.exists() }?.let { runCatching { it.readText() }.getOrNull() }
            ?.takeIf { it.isNotBlank() }

    fun clear(context: Context) {
        writer.execute { File(context.filesDir, FILE).delete() }
    }
}
