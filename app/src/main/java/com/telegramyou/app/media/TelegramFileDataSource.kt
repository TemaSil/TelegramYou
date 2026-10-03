package com.telegramyou.app.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import com.telegramyou.app.telegram.messages.TelegramMessages
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.io.InterruptedIOException
import java.io.RandomAccessFile

/**
 * A Telegram file played while it downloads (1.9), as the official client
 * plays a video or a track: the player asks for bytes, the backend fetches
 * from wherever the player is reading — the start, or wherever it seeks —
 * and the bytes are read off the phone as soon as they land. What arrives
 * stays, so the second time it plays from the phone.
 *
 * Addressed as `tgfile://<file id>`; see [uriOf]. Everything else goes to
 * Media3's own DataSource, through [Factory].
 *
 * Runs on the player's loading thread, which is allowed to block: each
 * question to the backend is a short suspend call made from there.
 */
@UnstableApi
class TelegramFileDataSource(private val files: TelegramMessages) : BaseDataSource(true) {

    private var uri: Uri? = null
    private var fileId = 0
    private var position = 0L
    /** Where the file's bytes are known to run out for now, as an absolute offset. */
    private var readyEnd = 0L
    private var size = 0L
    private var bytesRemaining = C.LENGTH_UNSET.toLong()
    private var path: String? = null
    private var file: RandomAccessFile? = null
    private var openPath: String? = null
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        fileId = dataSpec.uri.authority?.toIntOrNull()
            ?: throw DataSourceException(PlaybackException.ERROR_CODE_IO_UNSPECIFIED)
        transferInitializing(dataSpec)
        position = dataSpec.position
        val stream = ask { files.streamFile(fileId, position) }
        size = stream.size
        if (size > 0 && position > size) {
            throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
        }
        path = stream.path
        readyEnd = position + stream.readyFromOffset
        bytesRemaining = when {
            dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
            size > 0 -> size - position
            else -> C.LENGTH_UNSET.toLong()
        }
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        while (true) {
            if (bytesRemaining == 0L || (size > 0 && position >= size)) return C.RESULT_END_OF_INPUT
            waitForBytes()
            if (size > 0 && position >= size) return C.RESULT_END_OF_INPUT
            val where = path ?: throw IOException("No file yet for $fileId")
            val reader = file?.takeIf { openPath == where } ?: RandomAccessFile(where, "r").also {
                file?.close()
                file = it
                openPath = where
            }
            var wanted = minOf(length.toLong(), readyEnd - position)
            if (bytesRemaining != C.LENGTH_UNSET.toLong()) wanted = minOf(wanted, bytesRemaining)
            reader.seek(position)
            val read = reader.read(buffer, offset, wanted.toInt())
            if (read <= 0) {
                // Said to be there and is not yet: asked again.
                readyEnd = position
                continue
            }
            position += read
            if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= read
            bytesTransferred(read)
            return read
        }
    }

    /** Until something is ready at [position], or the download has stalled too long. */
    private fun waitForBytes() {
        val deadline = System.currentTimeMillis() + STALL_MS
        while (position >= readyEnd) {
            val stream = ask { files.streamedFrom(fileId, position) }
            stream.path?.let { path = it }
            if (stream.size > 0) size = stream.size
            readyEnd = position + stream.readyFromOffset
            if (position < readyEnd || (size > 0 && position >= size)) return
            if (System.currentTimeMillis() > deadline) {
                throw DataSourceException(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)
            }
            try {
                Thread.sleep(POLL_MS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw InterruptedIOException()
            }
        }
    }

    private fun <T> ask(question: suspend () -> T?): T =
        runBlocking { question() } ?: throw DataSourceException(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        try {
            file?.close()
        } finally {
            file = null
            openPath = null
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }

    /**
     * Media3's DataSource for everything, with `tgfile://` sent here: one
     * factory for a player that plays files on the phone, from the app's
     * resources, and from Telegram while they arrive.
     */
    class Factory(context: Context, private val files: TelegramMessages) : DataSource.Factory {
        private val fallback = DefaultDataSource.Factory(context)

        override fun createDataSource(): DataSource =
            Routing(fallback.createDataSource(), TelegramFileDataSource(files))
    }

    /** Sends each opening to whichever source reads its scheme. */
    private class Routing(private val other: DataSource, private val telegram: DataSource) : DataSource {
        private var current: DataSource? = null

        override fun addTransferListener(transferListener: TransferListener) {
            other.addTransferListener(transferListener)
            telegram.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            val source = if (dataSpec.uri.scheme == SCHEME) telegram else other
            current = source
            return source.open(dataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            current?.read(buffer, offset, length) ?: throw IOException("Not open")

        override fun getUri(): Uri? = current?.uri

        override fun getResponseHeaders(): Map<String, List<String>> = current?.responseHeaders ?: emptyMap()

        override fun close() {
            try {
                current?.close()
            } finally {
                current = null
            }
        }
    }

    companion object {
        const val SCHEME = "tgfile"

        /** How [fileId] is addressed to a player that streams it. */
        fun uriOf(fileId: Int): Uri = Uri.parse("$SCHEME://$fileId")

        /** How often an empty read looks again for bytes that have landed. */
        private const val POLL_MS = 40L

        /** How long with nothing new before the player is told the network failed. */
        private const val STALL_MS = 45_000L
    }
}
