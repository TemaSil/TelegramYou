package com.telegramyou.app.ui.chat

import android.content.ClipData
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

    /** A file path, or a Uri — the demo's media is packaged as resources. */
    private fun open(context: Context, path: String): InputStream? =
        if (path.contains("://")) context.contentResolver.openInputStream(Uri.parse(path))
        else File(path).takeIf { it.exists() }?.inputStream()

    private fun displayName(path: String): String {
        val last = path.substringAfterLast('/').substringAfterLast(':')
        return if (last.contains('.')) last else "telegramyou-${System.currentTimeMillis()}"
    }
}
