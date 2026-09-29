package com.telegramyou.app.ui.chat

import android.content.ClipData
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.telegramyou.app.telegram.model.StickerContent
import android.graphics.Canvas
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import java.io.File
import java.io.InputStream

/**
 * Settings → For geeks → Save and copy media: a message's file into the
 * phone's Downloads, and a photo onto the clipboard. Both are plain copies
 * of what TDLib already has; nothing is fetched here.
 */
object MediaActions {

    /** Whether Downloads can be written without asking for storage permission. */
    val canSaveToDownloads: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /**
     * Into Downloads through MediaStore, which on Android 10 and later needs
     * no permission for files the app itself adds. Below that it would need
     * the storage permission this app does not ask for, and the menu item is
     * not offered — see [canSaveToDownloads].
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun saveToDownloads(context: Context, path: String, mime: String): Boolean {
        val name = displayName(path)
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/TelegramYou")
        }
        val resolver = context.contentResolver
        val target = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        return runCatching {
            open(context, path)?.use { input ->
                resolver.openOutputStream(target)?.use { output -> input.copyTo(output) }
            } ?: error("nothing to read")
        }.onFailure { resolver.delete(target, null, null) }.isSuccess
    }

    /** A copy of the photo in the app's clipboard directory, handed over by Uri. */
    fun copyPhoto(context: Context, path: String): Boolean = runCatching {
        val directory = File(context.cacheDir, "clipboard").apply { mkdirs() }
        // One at a time: the previous copy goes, so the directory never grows.
        directory.listFiles()?.forEach { it.delete() }
        val copy = File(directory, displayName(path))
        open(context, path)?.use { input -> copy.outputStream().use { input.copyTo(it) } }
            ?: error("nothing to read")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", copy)
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newUri(context.contentResolver, "Photo", uri))
    }.isSuccess

    /**
     * Opens a file from a chat in whichever app on the phone reads it — a
     * PDF reader, a text editor, an archive manager — through Android's own
     * chooser when there are several. A copy in opened/, one at a time, as
     * the clipboard's is: the other app is handed that file and nothing else.
     * False when no app on the phone opens this kind of file.
     */
    fun openFile(context: Context, path: String, mime: String?, name: String?): Boolean {
        val directory = File(context.cacheDir, "opened").apply { mkdirs() }
        directory.listFiles()?.forEach { it.delete() }
        val copy = File(directory, name?.takeIf { it.isNotBlank() } ?: displayName(path))
        val copied = runCatching {
            open(context, path)?.use { input -> copy.outputStream().use { input.copyTo(it) } } ?: error("nothing to read")
        }.isSuccess
        if (!copied) return false
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", copy)
        val type = mime?.takeIf { it.isNotBlank() }
            ?: android.webkit.MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(copy.extension.lowercase())
            ?: "*/*"
        val view = android.content.Intent(android.content.Intent.ACTION_VIEW)
            .setDataAndType(uri, type)
            .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(android.content.Intent.createChooser(view, copy.name).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: android.content.ActivityNotFoundException) {
            false
        }
    }

    /** A file path, or a Uri — the demo's media is packaged as resources. */
    private fun open(context: Context, path: String): InputStream? =
        if (path.contains("://")) context.contentResolver.openInputStream(Uri.parse(path))
        else File(path).takeIf { it.exists() }?.inputStream()

    private fun displayName(path: String): String {
        val last = path.substringAfterLast('/').substringAfterLast(':')
        return if (last.contains('.')) last else "telegramyou-${System.currentTimeMillis()}"
    }
}

/**
 * A still sticker as a JPEG in the cache, flattened onto white — what
 * "Send as image" sends. White rather than kept clear: Telegram turns every
 * photo into a JPEG, and a transparent sticker would arrive on black. Its
 * file is fetched through [loader] when it is not on the phone yet; null
 * when there is none to be had (the demo's stickers have no files) or it
 * will not decode.
 */
internal suspend fun stickerAsPicture(
    context: Context,
    sticker: StickerContent,
    loader: (suspend (Int) -> String?)?
): String? {
    val source = sticker.path ?: sticker.fileId?.let { id -> loader?.invoke(id) } ?: return null
    return withContext(Dispatchers.IO) {
        val drawn = BitmapFactory.decodeFile(source) ?: return@withContext null
        val flat = Bitmap.createBitmap(drawn.width, drawn.height, Bitmap.Config.ARGB_8888)
        Canvas(flat).apply {
            drawColor(android.graphics.Color.WHITE)
            drawBitmap(drawn, 0f, 0f, null)
        }
        val out = File(context.cacheDir, "sticker_${System.currentTimeMillis()}.jpg")
        out.outputStream().use { flat.compress(Bitmap.CompressFormat.JPEG, STICKER_JPEG_QUALITY, it) }
        out.path
    }
}

private const val STICKER_JPEG_QUALITY = 95
