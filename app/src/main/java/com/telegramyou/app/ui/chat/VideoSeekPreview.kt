package com.telegramyou.app.ui.chat

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.telegramyou.app.i18n.tr
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One decoder and a bounded cache, shared by successive positions of one video. */
internal class VideoPreviewFrames(private val context: Context, private val path: String) {
    private val lock = Mutex()
    private val closed = AtomicBoolean(false)
    private val cache = LruCache<Long, Bitmap>(12)
    private var decoder: MediaMetadataRetriever? = null

    suspend fun frame(bucket: Long): Bitmap? = withContext(Dispatchers.IO) {
        lock.withLock {
            if (closed.get() || Build.VERSION.SDK_INT < 27) return@withLock null
            cache.get(bucket)?.let { return@withLock it }
            try {
                val reader = decoder ?: MediaMetadataRetriever().also { fresh ->
                    try {
                        if (Uri.parse(path).scheme in listOf("content", "android.resource")) fresh.setDataSource(context, Uri.parse(path))
                        else fresh.setDataSource(if (Uri.parse(path).scheme == "file") Uri.parse(path).path else path)
                        decoder = fresh
                    } catch (e: Exception) { fresh.release(); throw e }
                }
                val timeUs = bucket * 500_000L
                val bitmap = reader.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST, 320, 180)
                bitmap?.also { cache.put(bucket, it) }
            } catch (e: CancellationException) { throw e }
              catch (_: Exception) { null }
        }
    }

    fun close() {
        closed.set(true)
        // A native decode may still be returning. Release after it, off the UI
        // thread; cancelling composition must not race release against a frame.
        val cleanup = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        cleanup.launch {
            try { lock.withLock { decoder?.release(); decoder = null; cache.evictAll() } }
            finally { cleanup.cancel() }
        }
    }
}

/** Time always works; a streamed, unavailable frame never forces a download. */
@Composable
internal fun VideoSeekPreview(path: String?, positionMs: Long, offset: IntOffset) {
    val context = LocalContext.current.applicationContext
    val frames = remember(path) {
        path?.takeIf { Uri.parse(it).scheme in listOf(null, "file", "content", "android.resource") }
            ?.let { VideoPreviewFrames(context, it) }
    }
    DisposableEffect(frames) { onDispose { frames?.close() } }
    val bucket = positionMs.coerceAtLeast(0) / 500
    val bitmap by produceState<Bitmap?>(null, frames, bucket) {
        value = null
        value = frames?.frame(bucket)
    }
    val time = formatDuration(positionMs.coerceAtLeast(0) / 1000)
    Popup(alignment = Alignment.BottomStart, offset = offset) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 6.dp,
            modifier = Modifier.width(144.dp).semantics {
                contentDescription = tr("Video preview at $time", "Предпросмотр видео: $time")
            }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                bitmap?.let { frame ->
                    Image(frame.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(90.dp))
                }
                Text(time, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(8.dp))
            }
        }
    }
}
