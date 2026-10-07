package com.telegramyou.app.music

import android.media.MediaMetadataRetriever
import com.telegramyou.app.telegram.model.FileTags
import java.io.File

/**
 * Reads a music file's own tags for the library (2.0): its album, the
 * album's artist, the track's place on it, and the embedded cover, written
 * once into [coverDir] under the file's name so a second reading finds it
 * there and the library's tiles can load it like any other picture.
 *
 * Blocking, and slow enough per file to matter over a thousand of them:
 * called off the main thread, and only for files already on the phone.
 */
class FileTagReader(private val coverDir: File?) {

    fun read(path: String): FileTags? {
        val file = File(path)
        if (!file.isFile) return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty().trim()
            val albumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST).orEmpty().trim()
            // "3" or "3/12": the place, whatever the total.
            val number = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                ?.substringBefore('/')?.trim()?.toIntOrNull() ?: 0
            FileTags(
                album = album,
                albumArtist = albumArtist,
                trackNumber = number,
                coverPath = cover(file, retriever)
            )
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun cover(file: File, retriever: MediaMetadataRetriever): String? {
        val dir = coverDir ?: return null
        val target = File(dir, "${file.nameWithoutExtension}-${file.length()}.img")
        if (target.isFile && target.length() > 0) return target.absolutePath
        val bytes = retriever.embeddedPicture ?: return null
        return runCatching {
            dir.mkdirs()
            target.writeBytes(bytes)
            target.absolutePath
        }.getOrNull()
    }
}
