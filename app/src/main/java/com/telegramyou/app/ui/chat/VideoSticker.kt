package com.telegramyou.app.ui.chat

import android.content.Context
import android.graphics.Bitmap
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.telegramyou.app.media.WebmVideo
import com.telegramyou.app.media.parseWebm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A video sticker, playing, with its transparency.
 *
 * Android's players decode the picture of a VP9 WebM and drop the alpha that
 * travels beside it, so a video sticker through them is a black square with
 * a sticker in it. Here the file is read by [parseWebm], its picture and its
 * alpha each go through the platform's own VP9 decoder, and the two are put
 * back together into frames at the size the sticker is drawn — which is also
 * what keeps a 512-pixel sticker at 160dp from costing 512-pixel work.
 *
 * [fallback] stands in until the first frame, and for good if the file will
 * not decode. With [animate] off — the picker — only the first frame.
 */
@Composable
internal fun VideoSticker(
    path: String,
    size: Dp,
    animate: Boolean,
    description: String,
    fallback: @Composable () -> Unit
) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }.coerceIn(16, MAX_SIDE)
    var shown by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path, animate, px) {
        withContext(Dispatchers.Default) {
            val video = readVideo(context, path) ?: return@withContext
            if (video.codec != "V_VP9" || video.width <= 0 || video.height <= 0) return@withContext
            val scale = px.toFloat() / maxOf(video.width, video.height)
            val w = (video.width * scale).toInt().coerceAtLeast(1)
            val h = (video.height * scale).toInt().coerceAtLeast(1)
            // Two, taking turns: the one on screen is never the one being
            // written.
            val pages = Array(2) { Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888) }
            try {
                StickerDecoder(video, w, h).use { decoder ->
                    var turn = 0
                    var index = 0
                    var loopStart = SystemClock.uptimeMillis()
                    while (isActive) {
                        val page = pages[turn]
                        if (decoder.frame(index, page)) {
                            val wait = loopStart + video.frames[index].timeMs - SystemClock.uptimeMillis()
                            if (wait > 0) delay(wait)
                            val image = page.asImageBitmap()
                            withContext(Dispatchers.Main) { shown = image }
                            turn = 1 - turn
                        }
                        if (!animate) break
                        index++
                        if (index == video.frames.size) {
                            index = 0
                            loopStart = SystemClock.uptimeMillis() + FRAME_GAP_MS
                        }
                    }
                }
            } catch (_: Exception) {
                // A decoder the device refuses, or a file it cannot read: the
                // last frame shown stays, or the fallback if there was none.
            }
        }
    }
    val frame = shown
    if (frame == null) {
        fallback()
    } else {
        Image(
            bitmap = frame,
            contentDescription = description,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(size)
        )
    }
}

/** A file path, or a resource or content URI — the demo's stickers are resources. */
private fun readVideo(context: Context, path: String): WebmVideo? {
    val bytes = if (path.contains("://")) {
        context.contentResolver.openInputStream(Uri.parse(path))?.use { it.readBytes() }
    } else {
        File(path).takeIf { it.length() in 1..MAX_FILE_BYTES }?.readBytes()
    } ?: return null
    return parseWebm(bytes)
}

/**
 * The two VP9 streams of a [WebmVideo] through two platform decoders, frame
 * by frame, into ARGB at [outW] × [outH]. Frames are asked for in order;
 * asking for the first again starts the loop over.
 */
private class StickerDecoder(
    private val video: WebmVideo,
    private val outW: Int,
    private val outH: Int
) : AutoCloseable {
    private val color = Stream { it.color }
    private val alpha = if (video.hasAlpha) Stream { it.alpha } else null
    private val pixels = IntArray(outW * outH)
    private val alphas = ByteArray(outW * outH)
    private var last = -1

    fun frame(index: Int, into: Bitmap): Boolean {
        if (index <= last) {
            color.restart()
            alpha?.restart()
        }
        last = index
        if (alpha == null) {
            alphas.fill(-1)
        } else if (!alpha.take(index) { sample(it, luma = alphas) }) {
            return false
        }
        if (!color.take(index) { toArgb(it) }) return false
        into.setPixels(pixels, 0, outW, 0, 0, outW, outH)
        return true
    }

    override fun close() {
        color.close()
        alpha?.close()
    }

    /** The alpha stream's brightness is the alpha, sampled to the output size. */
    private fun sample(image: Image, luma: ByteArray) {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val row = plane.rowStride
        val step = plane.pixelStride
        val w = image.cropRect.width()
        val h = image.cropRect.height()
        for (y in 0 until outH) {
            val sy = y * h / outH
            for (x in 0 until outW) {
                luma[y * outW + x] = buffer.get(sy * row + (x * w / outW) * step)
            }
        }
    }

    /** BT.601 limited range, the picture's colour with the alpha sampled before it. */
    private fun toArgb(image: Image) {
        val (yp, up, vp) = image.planes
        val yb = yp.buffer
        val ub = up.buffer
        val vb = vp.buffer
        val w = image.cropRect.width()
        val h = image.cropRect.height()
        for (y in 0 until outH) {
            val sy = y * h / outH
            val yRow = sy * yp.rowStride
            val uRow = (sy / 2) * up.rowStride
            val vRow = (sy / 2) * vp.rowStride
            for (x in 0 until outW) {
                val sx = x * w / outW
                val c = (yb.get(yRow + sx * yp.pixelStride).toInt() and 0xFF) - 16
                val d = (ub.get(uRow + (sx / 2) * up.pixelStride).toInt() and 0xFF) - 128
                val e = (vb.get(vRow + (sx / 2) * vp.pixelStride).toInt() and 0xFF) - 128
                val r = ((298 * c + 409 * e + 128) shr 8).coerceIn(0, 255)
                val g = ((298 * c - 100 * d - 208 * e + 128) shr 8).coerceIn(0, 255)
                val b = ((298 * c + 516 * d + 128) shr 8).coerceIn(0, 255)
                val a = alphas[y * outW + x].toInt() and 0xFF
                pixels[y * outW + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
    }

    /** One VP9 stream of the file through one decoder. */
    private inner class Stream(private val data: (com.telegramyou.app.media.WebmFrame) -> ByteArray?) : AutoCloseable {
        private val codec = MediaCodec.createDecoderByType(VP9).apply {
            val format = MediaFormat.createVideoFormat(VP9, video.width, video.height)
            format.setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
            )
            configure(format, null, null, 0)
            start()
        }
        private val info = MediaCodec.BufferInfo()
        private var queued = 0

        fun restart() {
            codec.flush()
            queued = 0
        }

        /** Decodes up to frame [index] and hands its picture to [read]. */
        fun take(index: Int, read: (Image) -> Unit): Boolean {
            repeat(ATTEMPTS) {
                feed(index + LOOKAHEAD)
                val out = codec.dequeueOutputBuffer(info, WAIT_US)
                if (out >= 0) {
                    val pts = info.presentationTimeUs
                    if (pts == index.toLong()) {
                        codec.getOutputImage(out)?.let(read)
                        codec.releaseOutputBuffer(out, false)
                        return true
                    }
                    codec.releaseOutputBuffer(out, false)
                    if (pts > index) return false
                }
            }
            return false
        }

        private fun feed(upTo: Int) {
            while (queued <= upTo && queued < video.frames.size) {
                val input = codec.dequeueInputBuffer(0)
                if (input < 0) return
                val bytes = data(video.frames[queued]) ?: ByteArray(0)
                codec.getInputBuffer(input)?.apply {
                    clear()
                    put(bytes)
                }
                codec.queueInputBuffer(input, 0, bytes.size, queued.toLong(), 0)
                queued++
            }
        }

        override fun close() {
            runCatching { codec.stop() }
            codec.release()
        }
    }

    private companion object {
        const val VP9 = "video/x-vnd.on2.vp9"
        const val ATTEMPTS = 60
        const val WAIT_US = 5_000L
        const val LOOKAHEAD = 2
    }
}

/** Past this a sticker is not a sticker; Telegram caps them at 256 KB. */
private const val MAX_FILE_BYTES = 2L * 1024 * 1024

/** Drawn no larger than a sticker's own 512 pixels. */
private const val MAX_SIDE = 512

/** A breath between the end of a loop and its start again. */
private const val FRAME_GAP_MS = 33L
