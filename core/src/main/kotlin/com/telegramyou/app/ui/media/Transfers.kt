package com.telegramyou.app.ui.media

/**
 * A file on its way in or out, as a bar and a label.
 *
 * Both directions are the same shape — bytes done out of bytes expected —
 * so they share a type and differ only in which way [isUpload] points. What
 * the screen does with them differs: a download has something to show behind
 * it, an upload has something already on screen that is not sent yet.
 */
data class FileTransfer(
    val fileId: Int,
    val doneBytes: Long,
    val totalBytes: Long,
    val isUpload: Boolean = false
) {
    val isFinished: Boolean get() = totalBytes > 0 && doneBytes >= totalBytes
}

/**
 * How far along, as 0..1, or null when the size is not known yet.
 *
 * Null rather than zero, for the reason a video's progress bar takes the same
 * answer: zero is a real value — nothing transferred — and a determinate bar
 * sitting at zero claims a size the server has not given. The caller draws an
 * indeterminate bar instead, which is the honest shape for "started, length
 * unknown".
 */
fun transferProgress(transfer: FileTransfer): Float? {
    if (transfer.totalBytes <= 0L) return null
    return (transfer.doneBytes.toFloat() / transfer.totalBytes).coerceIn(0f, 1f)
}

/**
 * A size in bytes, as a person would write it.
 *
 * Binary units under decimal names, which is what every file manager on a
 * phone does: 1 KB is 1024 bytes here. Being right about the prefix and
 * wrong about the number people expect would be the worse of the two.
 */
fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "0 B"
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    // One decimal below ten, none above: "9.4 MB" reads, "94.3 MB" is noise.
    return if (value < 10) {
        val rounded = kotlin.math.round(value * 10) / 10
        if (rounded % 1.0 == 0.0) "${rounded.toInt()} ${units[unit]}"
        else "$rounded ${units[unit]}"
    } else {
        "${kotlin.math.round(value).toInt()} ${units[unit]}"
    }
}

/**
 * The line under the bar: how much of how much, and which way it is going.
 *
 * The direction is in the words rather than in an icon, because "2.1 MB of
 * 8.0 MB" says nothing about whether this phone is sending or receiving, and
 * those are different things to be waiting for.
 */
fun transferLabel(transfer: FileTransfer): String {
    val done = formatBytes(transfer.doneBytes)
    val verb = if (transfer.isUpload) "Sending" else "Downloading"
    if (transfer.totalBytes <= 0L) return "$verb $done"
    return "$verb $done of ${formatBytes(transfer.totalBytes)}"
}
