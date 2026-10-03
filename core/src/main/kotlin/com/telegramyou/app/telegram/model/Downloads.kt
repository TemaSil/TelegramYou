package com.telegramyou.app.telegram.model

import com.telegramyou.app.ui.media.FileTransfer
import com.telegramyou.app.ui.media.formatBytes

/**
 * One file in the download manager: TDLib's own list of downloads, the one
 * the official client's Downloads tab shows, kept in TDLib's database so it
 * outlives the app being closed or updated.
 *
 * What people get wrong about downloads in Telegram, and what the fields here
 * are for (ROADMAP, 1.6.3):
 * - "downloaded" is not the same as "where other apps can see it": the file
 *   is in Telegram's storage, and [isSavedToDevice] says when a copy has
 *   been put in the phone's Downloads as well;
 * - a file cleared from the cache is not a download that never happened:
 *   [path] goes null, the row stays, and says so, instead of vanishing.
 */
data class DownloadEntry(
    val fileId: Int,
    val chatId: Long,
    val messageId: Long,
    val name: String,
    /** The whole file, in bytes; 0 while the server has not said. */
    val sizeBytes: Long,
    /** How much is on the phone — all of it once finished. */
    val downloadedBytes: Long = 0,
    val mimeType: String? = null,
    val chatTitle: String = "",
    /** Seconds since the epoch. */
    val addedAt: Long = 0,
    /** Seconds since the epoch; null until it has finished. */
    val completedAt: Long? = null,
    val isPaused: Boolean = false,
    /** Where it is on the phone, while it is. */
    val path: String? = null,
    val isSavedToDevice: Boolean = false
)

/** Where a download is, as the row says it. */
enum class DownloadState {
    /** Bytes arriving now. */
    Downloading,
    /** Stopped by the person; picks up where it stopped. */
    Paused,
    /** Asked for and not paused, but nothing is arriving: no network, or a queue. */
    Waiting,
    /** All of it on the phone. */
    Done,
    /** Finished once, since cleared from the phone: a tap fetches it again. */
    Gone
}

fun DownloadEntry.state(transfer: FileTransfer?): DownloadState = when {
    completedAt != null -> if (path != null) DownloadState.Done else DownloadState.Gone
    isPaused -> DownloadState.Paused
    transfer != null && !transfer.isUpload -> DownloadState.Downloading
    else -> DownloadState.Waiting
}

/** How far along, 0..1, or null while the size is not known. */
fun DownloadEntry.progress(transfer: FileTransfer?): Float? {
    val total = transfer?.totalBytes?.takeIf { it > 0 } ?: sizeBytes
    if (total <= 0) return null
    val done = transfer?.doneBytes ?: downloadedBytes
    return (done.toFloat() / total).coerceIn(0f, 1f)
}

/**
 * The line under a row's name. Every state in words, since a paused
 * download and a stuck one look alike as a bar: "Paused · 1.2 MB of 4.8 MB",
 * "Waiting for the network", "Removed from the phone — tap to download again".
 */
fun DownloadEntry.statusLine(transfer: FileTransfer?): String {
    val total = transfer?.totalBytes?.takeIf { it > 0 } ?: sizeBytes
    val done = transfer?.doneBytes ?: downloadedBytes
    val ofTotal = if (total > 0) "${formatBytes(done)} of ${formatBytes(total)}" else formatBytes(done)
    return when (state(transfer)) {
        DownloadState.Downloading -> ofTotal
        DownloadState.Paused -> "Paused · $ofTotal"
        DownloadState.Waiting -> if (done > 0) "Waiting for the network · $ofTotal" else "Waiting for the network"
        DownloadState.Done -> listOf(
            formatBytes(sizeBytes.takeIf { it > 0 } ?: done),
            chatTitle.takeIf { it.isNotBlank() }?.let { "from $it" },
            "in Downloads".takeIf { isSavedToDevice }
        ).filterNotNull().joinToString(" · ")
        DownloadState.Gone -> "Removed from the phone — tap to download again"
    }
}

/**
 * The screen's two lists: what is still coming, oldest asked-for first, as
 * a queue reads; and what has come, newest first, as a history reads.
 */
data class DownloadSections(val active: List<DownloadEntry>, val finished: List<DownloadEntry>) {
    val isEmpty: Boolean get() = active.isEmpty() && finished.isEmpty()
    val anyRunning: Boolean get() = active.any { !it.isPaused }
    val anyPaused: Boolean get() = active.any { it.isPaused }

    /** What "Delete from phone" on every finished file would free. */
    val finishedBytes: Long get() = finished.filter { it.path != null }.sumOf { it.sizeBytes }
}

fun sectionDownloads(entries: List<DownloadEntry>): DownloadSections {
    val unique = entries.distinctBy { it.fileId }
    val (finished, active) = unique.partition { it.completedAt != null }
    return DownloadSections(
        active = active.sortedBy { it.addedAt },
        finished = finished.sortedByDescending { it.completedAt }
    )
}

/**
 * The notification's line while downloads run: "Downloading 3 files ·
 * 12 MB of 40 MB", counting only what is in the list, not the thumbnails
 * and stickers the app fetches on its own.
 */
fun downloadNotificationText(transfers: List<FileTransfer>): String {
    val count = transfers.size
    val files = if (count == 1) "1 file" else "$count files"
    val total = transfers.sumOf { it.totalBytes.coerceAtLeast(0) }
    val done = transfers.sumOf { it.doneBytes.coerceAtLeast(0) }
    return if (total > 0) "Downloading $files · ${formatBytes(done)} of ${formatBytes(total)}" else "Downloading $files"
}

/** How a download asked for from a chat ended, for the screen that asked. */
sealed interface DownloadOutcome {
    data class Done(val path: String) : DownloadOutcome
    /** Paused or cancelled in the download manager: nothing to report. */
    data object Stopped : DownloadOutcome
    data object Failed : DownloadOutcome
}

/**
 * A file as it streams (1.9): played while it downloads, as the official
 * client plays a video or a track, instead of fetched whole first.
 *
 * [path] is where its bytes are being written, null until the first of
 * them; [size] the whole file, 0 while unknown; [readyFromOffset] how many
 * bytes are on the phone without a gap from the offset that was asked
 * about. What has arrived stays: the next time, it plays from the phone.
 */
data class FileStream(
    val path: String?,
    val size: Long,
    val readyFromOffset: Long,
    val isComplete: Boolean
)
