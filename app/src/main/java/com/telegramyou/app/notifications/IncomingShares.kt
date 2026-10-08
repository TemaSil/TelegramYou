package com.telegramyou.app.notifications

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.IntentCompat
import androidx.core.content.pm.ShortcutManagerCompat
import com.telegramyou.app.telegram.model.IncomingShare
import com.telegramyou.app.telegram.model.SharedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * What Android's share sheet hands over (2.1), read off the intent.
 *
 * The things shared are copied into the app's cache at once, while it may
 * still read them: a content Uri from another app is lent to the activity
 * that received it, and the composer sends long after — perhaps from
 * another instance of the screen, once the share sheet's task is gone.
 * TDLib wants a file anyway.
 */
object IncomingShares {

    /** Whether [intent] is a share at all. */
    fun isShare(intent: Intent?): Boolean =
        intent?.action == Intent.ACTION_SEND || intent?.action == Intent.ACTION_SEND_MULTIPLE

    /** The share in [intent], with its things copied; null when there is nothing in it. */
    suspend fun read(context: Context, intent: Intent): IncomingShare? = withContext(Dispatchers.IO) {
        if (!isShare(intent)) return@withContext null
        val chatId = chatIdOfConversation(intent.getStringExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID))
        val text = listOfNotNull(
            intent.getStringExtra(Intent.EXTRA_SUBJECT)?.takeIf { it.isNotBlank() },
            intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }
        ).distinct().joinToString("\n")
        val uris = when (intent.action) {
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        }
        val folder = File(context.cacheDir, "shared").apply { mkdirs() }
        val items = uris.mapIndexedNotNull { index, uri -> copy(context, uri, folder, index, intent.type) }
        IncomingShare(chatId = chatId, text = text, items = items).takeUnless { it.isEmpty }
    }

    private fun copy(context: Context, uri: Uri, folder: File, index: Int, intentType: String?): SharedItem? =
        try {
            val resolver = context.contentResolver
            val name = displayName(context, uri) ?: "shared_$index"
            val safe = name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val out = File(folder, "${System.currentTimeMillis()}_${index}_$safe")
            resolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
                ?: return null
            // The type the sending app gave this Uri, else the intent's, which
            // for one thing is its type and for several their common one.
            val mime = resolver.getType(uri) ?: intentType?.takeIf { !it.endsWith("/*") }
            SharedItem(uri = Uri.fromFile(out).toString(), name = name, mime = mime ?: intentType)
        } catch (e: Exception) {
            // A Uri the app was not let read, or one gone already: that thing
            // is left out, the rest still arrive.
            Log.w(TAG, "copy($uri): ${e.message}")
            null
        }

    private fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment

    private const val TAG = "IncomingShares"
}
