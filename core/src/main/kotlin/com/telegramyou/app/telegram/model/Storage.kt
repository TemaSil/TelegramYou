package com.telegramyou.app.telegram.model

/**
 * What TDLib's files take on this phone, by kind — the Data and storage
 * screen, and what clearing the cache can give back.
 */
data class StorageUsage(
    val slices: List<StorageSlice>,
    /** The message database. Not clearable from here: it is the chats themselves. */
    val databaseBytes: Long = 0L
) {
    val filesBytes: Long get() = slices.sumOf { it.bytes }
    val totalBytes: Long get() = filesBytes + databaseBytes
}

data class StorageSlice(val kind: StorageKind, val bytes: Long, val count: Int)

/**
 * The kinds of file a person can tell apart and choose to clear. TDLib has
 * two dozen file types; these are the ones a settings screen can name.
 * [tdTypes] are what clearing a kind asks TDLib to delete.
 */
enum class StorageKind(val label: String, val tdTypes: List<String>) {
    Photos("Photos", listOf("fileTypePhoto", "fileTypeThumbnail")),
    Videos("Videos", listOf("fileTypeVideo", "fileTypeVideoNote", "fileTypeAnimation")),
    Voice("Voice messages", listOf("fileTypeVoiceNote")),
    Music("Music", listOf("fileTypeAudio")),
    Files("Files", listOf("fileTypeDocument")),
    Stories("Stories", listOf("fileTypePhotoStory", "fileTypeVideoStory")),
    Stickers("Stickers", listOf("fileTypeSticker")),
    ProfilePhotos("Profile photos", listOf("fileTypeProfilePhoto")),
    Other("Other", listOf("fileTypeUnknown", "fileTypeWallpaper", "fileTypeNotificationSound"))
}

/** A TDLib file type to its kind; secret and self-destructing files count as other. */
fun storageKindOf(tdType: String): StorageKind =
    StorageKind.entries.firstOrNull { tdType in it.tdTypes } ?: StorageKind.Other

/**
 * Per-chat, per-type figures folded into one line per kind, biggest first,
 * and empty kinds left out — TDLib reports by chat, and a person clearing a
 * cache thinks by what the files are.
 */
fun storageSlices(byType: List<Triple<String, Long, Int>>): List<StorageSlice> =
    byType.groupBy { storageKindOf(it.first) }
        .map { (kind, entries) -> StorageSlice(kind, entries.sumOf { it.second }, entries.sumOf { it.third }) }
        .filter { it.bytes > 0 }
        .sortedByDescending { it.bytes }

/**
 * Bytes as a person reads them, in powers of 1000 as Android's own storage
 * settings count: "0 B", "740 KB", "12.4 MB", "1.3 GB".
 */
fun bytesLabel(bytes: Long): String = when {
    bytes < 1_000 -> "${bytes.coerceAtLeast(0)} B"
    bytes < 1_000_000 -> "${(bytes + 999) / 1_000} KB"
    bytes < 1_000_000_000 -> "%.1f MB".format(java.util.Locale.ROOT, bytes / 1_000_000.0)
    else -> "%.1f GB".format(java.util.Locale.ROOT, bytes / 1_000_000_000.0)
}
