package com.telegramyou.app.media

/**
 * A video sticker's frames, read out of its WebM file.
 *
 * Telegram's video stickers are VP9 with an alpha channel, and WebM keeps
 * that alpha as a second VP9 stream: each frame's picture is the block, and
 * its transparency rides beside it in the block's `BlockAdditional`. Android's
 * own extractor and players take the first and drop the second, which is why
 * a video sticker played through them is a picture on a black square. So the
 * file is read here — just enough of Matroska to find the two streams — and
 * both are handed to the platform's VP9 decoder separately.
 */
class WebmVideo(
    val width: Int,
    val height: Int,
    /** "V_VP9" for every sticker Telegram accepts. */
    val codec: String,
    val frames: List<WebmFrame>
) {
    /** Whether the frames carry transparency. */
    val hasAlpha: Boolean get() = frames.any { it.alpha != null }
}

class WebmFrame(
    /** From the start of the file's first frame. */
    val timeMs: Long,
    val key: Boolean,
    /** The frame's picture, one VP9 frame. */
    val color: ByteArray,
    /** Its transparency, a second VP9 frame whose brightness is the alpha. */
    val alpha: ByteArray?
)

/**
 * The first video track of [bytes], or null if it is not a WebM file this can
 * read. Laced blocks are skipped: no encoder laces video, and a sticker is
 * video only.
 */
fun parseWebm(bytes: ByteArray): WebmVideo? = try {
    WebmReader(bytes).read()
} catch (_: IndexOutOfBoundsException) {
    null
} catch (_: IllegalArgumentException) {
    null
}

private class WebmReader(private val b: ByteArray) {
    private var width = 0
    private var height = 0
    private var codec = ""
    private var track = -1L
    private var scaleNs = 1_000_000L
    private var clusterTime = 0L
    private val frames = mutableListOf<WebmFrame>()

    fun read(): WebmVideo? {
        walk(0, b.size)
        if (codec.isEmpty() || frames.isEmpty()) return null
        val start = frames.first().timeMs
        return WebmVideo(
            width, height, codec,
            frames.map { WebmFrame(it.timeMs - start, it.key, it.color, it.alpha) }
        )
    }

    /** The elements between [from] and [to], descending into the ones that hold others. */
    private fun walk(from: Int, to: Int) {
        var at = from
        while (at < to) {
            val (id, idLen) = elementId(at)
            val (size, sizeLen) = vint(at + idLen)
            val body = at + idLen + sizeLen
            // An unknown size runs to the end of whatever holds it.
            val end = if (size < 0) to else minOf(to.toLong(), body + size).toInt()
            when (id) {
                SEGMENT, INFO, TRACKS, VIDEO, CLUSTER -> walk(body, end)
                TRACK_ENTRY -> trackEntry(body, end)
                TIMECODE_SCALE -> scaleNs = uint(body, end)
                CLUSTER_TIME -> clusterTime = uint(body, end)
                SIMPLE_BLOCK -> block(body, end, simple = true, alpha = null, reference = false)
                BLOCK_GROUP -> blockGroup(body, end)
            }
            at = end
        }
    }

    private fun trackEntry(from: Int, to: Int) {
        var number = -1L
        var type = 0L
        var id = ""
        var w = 0
        var h = 0
        forEach(from, to) { eid, body, end ->
            when (eid) {
                TRACK_NUMBER -> number = uint(body, end)
                TRACK_TYPE -> type = uint(body, end)
                CODEC_ID -> id = String(b, body, end - body, Charsets.US_ASCII).trimEnd('\u0000')
                VIDEO -> forEach(body, end) { vid, vbody, vend ->
                    when (vid) {
                        PIXEL_WIDTH -> w = uint(vbody, vend).toInt()
                        PIXEL_HEIGHT -> h = uint(vbody, vend).toInt()
                    }
                }
            }
        }
        // The first video track; type 1 is video.
        if (track < 0 && type == 1L) {
            track = number
            codec = id
            width = w
            height = h
        }
    }

    private fun blockGroup(from: Int, to: Int) {
        var blockAt = -1
        var blockEnd = -1
        var alpha: ByteArray? = null
        var reference = false
        forEach(from, to) { id, body, end ->
            when (id) {
                BLOCK -> {
                    blockAt = body
                    blockEnd = end
                }
                REFERENCE_BLOCK -> reference = true
                BLOCK_ADDITIONS -> forEach(body, end) { aid, abody, aend ->
                    if (aid == BLOCK_MORE) {
                        var addId = 1L
                        var data: ByteArray? = null
                        forEach(abody, aend) { mid, mbody, mend ->
                            when (mid) {
                                BLOCK_ADD_ID -> addId = uint(mbody, mend)
                                BLOCK_ADDITIONAL -> data = b.copyOfRange(mbody, mend)
                            }
                        }
                        // 1 is the alpha channel, as WebM defines it.
                        if (addId == 1L) alpha = data
                    }
                }
            }
        }
        if (blockAt >= 0) block(blockAt, blockEnd, simple = false, alpha = alpha, reference = reference)
    }

    private fun block(from: Int, to: Int, simple: Boolean, alpha: ByteArray?, reference: Boolean) {
        val (number, len) = vint(from)
        if (number != track) return
        val relative = ((b[from + len].toInt() shl 8) or (b[from + len + 1].toInt() and 0xFF)).toShort()
        val flags = b[from + len + 2].toInt() and 0xFF
        if (flags and 0x06 != 0) return // laced: not something a video encoder writes
        val key = if (simple) flags and 0x80 != 0 else !reference
        val timeMs = (clusterTime + relative) * scaleNs / 1_000_000
        frames += WebmFrame(timeMs, key, b.copyOfRange(from + len + 3, to), alpha)
    }

    private inline fun forEach(from: Int, to: Int, visit: (id: Int, body: Int, end: Int) -> Unit) {
        var at = from
        while (at < to) {
            val (id, idLen) = elementId(at)
            val (size, sizeLen) = vint(at + idLen)
            val body = at + idLen + sizeLen
            val end = if (size < 0) to else minOf(to.toLong(), body + size).toInt()
            visit(id, body, end)
            at = end
        }
    }

    /** An element id: its length marker kept, as the ids below are written. */
    private fun elementId(at: Int): Pair<Int, Int> {
        val first = b[at].toInt() and 0xFF
        val len = Integer.numberOfLeadingZeros(first) - 23
        require(len in 1..4) { "bad element id" }
        var id = 0
        for (i in 0 until len) id = (id shl 8) or (b[at + i].toInt() and 0xFF)
        return id to len
    }

    /** A size or a track number: its length marker removed; -1 for "unknown". */
    private fun vint(at: Int): Pair<Long, Int> {
        val first = b[at].toInt() and 0xFF
        val len = Integer.numberOfLeadingZeros(first) - 23
        require(len in 1..8) { "bad size" }
        var value = (first and (0xFF shr len)).toLong()
        var allOnes = value == (0xFF shr len).toLong()
        for (i in 1 until len) {
            val next = b[at + i].toInt() and 0xFF
            if (next != 0xFF) allOnes = false
            value = (value shl 8) or next.toLong()
        }
        return (if (allOnes) -1L else value) to len
    }

    private fun uint(from: Int, to: Int): Long {
        var value = 0L
        for (i in from until to) value = (value shl 8) or (b[i].toLong() and 0xFF)
        return value
    }

    private companion object {
        const val SEGMENT = 0x18538067
        const val INFO = 0x1549A966
        const val TIMECODE_SCALE = 0x2AD7B1
        const val TRACKS = 0x1654AE6B
        const val TRACK_ENTRY = 0xAE
        const val TRACK_NUMBER = 0xD7
        const val TRACK_TYPE = 0x83
        const val CODEC_ID = 0x86
        const val VIDEO = 0xE0
        const val PIXEL_WIDTH = 0xB0
        const val PIXEL_HEIGHT = 0xBA
        const val CLUSTER = 0x1F43B675
        const val CLUSTER_TIME = 0xE7
        const val SIMPLE_BLOCK = 0xA3
        const val BLOCK_GROUP = 0xA0
        const val BLOCK = 0xA1
        const val REFERENCE_BLOCK = 0xFB
        const val BLOCK_ADDITIONS = 0x75A1
        const val BLOCK_MORE = 0xA6
        const val BLOCK_ADD_ID = 0xEE
        const val BLOCK_ADDITIONAL = 0xA5
    }
}
