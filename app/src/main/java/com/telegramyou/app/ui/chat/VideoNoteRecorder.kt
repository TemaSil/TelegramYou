package com.telegramyou.app.ui.chat

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import android.util.Rational
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.resume

/**
 * Records one round video message at a time, from the front camera, with
 * the composer's camera button held down.
 *
 * CameraX: a Preview the screen draws as a circle (CameraXViewfinder, in
 * [surfaceRequest]) and a VideoCapture writing a file, bound together under
 * a square ViewPort so what is recorded is the square the circle shows —
 * Telegram's video messages are square, [MAX_SECONDS] long at most, and
 * drawn as a circle on every client.
 *
 * A class rather than composable state for the reason VoiceRecorder gives:
 * it holds the camera and the microphone, and has to let go of both on every
 * way out, the thrown-away ones included.
 */
class VideoNoteRecorder(private val context: Context) {

    /** What the viewfinder draws, while the camera is open. */
    var surfaceRequest by mutableStateOf<SurfaceRequest?>(null)
        private set

    private var provider: ProcessCameraProvider? = null
    private var recording: Recording? = null
    private var target: File? = null
    private var startedAt = 0L
    private var finished: CompletableDeferred<Boolean>? = null
    /** Music paused while the camera listens, as for a voice message; see AudioFocus. */
    private var focus: com.telegramyou.app.music.AudioFocus.Hold? = null

    val isRecording: Boolean get() = recording != null

    /**
     * Opens the camera and starts recording, or answers false if either could
     * not be had — no camera, or one another app holds. The caller has
     * checked both permissions.
     */
    @SuppressLint("MissingPermission")
    suspend fun start(owner: LifecycleOwner): Boolean {
        if (recording != null) return false
        return try {
            val cameras = cameraProvider(context)
            val selector = when {
                cameras.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                cameras.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                else -> return false
            }
            val preview = Preview.Builder().build().apply {
                setSurfaceProvider { request -> surfaceRequest = request }
            }
            // SD: Telegram sends video messages at 384 or so a side, and a
            // larger file is only a slower send of the same circle.
            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.from(Quality.SD, FallbackStrategy.higherQualityOrLowerThan(Quality.SD))
                )
                .build()
            val video = VideoCapture.withOutput(recorder)
            val group = UseCaseGroup.Builder()
                .setViewPort(ViewPort.Builder(Rational(1, 1), Surface.ROTATION_0).build())
                .addUseCase(preview)
                .addUseCase(video)
                .build()
            focus = com.telegramyou.app.music.AudioFocus.pause()
            cameras.unbindAll()
            cameras.bindToLifecycle(owner, selector, group)
            provider = cameras

            val directory = File(context.cacheDir, "video-notes").apply { mkdirs() }
            val file = File(directory, "round-${System.currentTimeMillis()}.mp4")
            val done = CompletableDeferred<Boolean>()
            recording = recorder
                .prepareRecording(
                    context,
                    FileOutputOptions.Builder(file).setDurationLimitMillis(MAX_SECONDS * 1000L).build()
                )
                .withAudioEnabled()
                .start(ContextCompat.getMainExecutor(context)) { event ->
                    // Finished, one way or another. Some errors still leave
                    // a playable file — the time limit, the camera going
                    // away mid-way — so the file is what stop() judges by;
                    // only "nothing usable was written" is a failure here.
                    if (event is VideoRecordEvent.Finalize) {
                        done.complete(event.error != VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA)
                    }
                }
            finished = done
            target = file
            startedAt = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            Log.w(TAG, "start: ${e.message}")
            close()
            false
        }
    }

    /**
     * Stops, waits for the file to be finished, and answers it — or null for
     * a recording too short to be one, or one the camera failed.
     */
    suspend fun stop(): Recorded? {
        val current = recording ?: return null
        val file = target
        val seconds = ((System.currentTimeMillis() - startedAt) / 1000L).toInt()
        current.stop()
        val ok = withTimeoutOrNull(FINALIZE_TIMEOUT_MS) { finished?.await() } == true
        close()
        if (file == null || !ok || seconds < MIN_SECONDS || !file.exists() || file.length() == 0L) {
            file?.delete()
            return null
        }
        return Recorded(file.absolutePath, seconds.coerceAtMost(MAX_SECONDS), sideOf(file))
    }

    /** Throws the recording away. */
    fun cancel() {
        val file = target
        recording?.stop()
        close()
        file?.delete()
    }

    private fun close() {
        recording = null
        target = null
        finished = null
        surfaceRequest = null
        focus?.release()
        focus = null
        runCatching { provider?.unbindAll() }
        provider = null
    }

    /** The side of the square actually written, which Telegram is told. */
    private fun sideOf(file: File): Int {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            minOf(width, height).takeIf { it > 0 } ?: DEFAULT_SIDE
        } catch (e: Exception) {
            DEFAULT_SIDE
        } finally {
            runCatching { retriever.release() }
        }
    }

    data class Recorded(val path: String, val durationSeconds: Int, val length: Int)

    companion object {
        /** Telegram's own limit on a video message. */
        const val MAX_SECONDS = 60
        private const val MIN_SECONDS = 1
        private const val DEFAULT_SIDE = 384
        private const val FINALIZE_TIMEOUT_MS = 5_000L
        private const val TAG = "VideoNoteRecorder"
    }
}

/** CameraX's process-wide camera provider, once it is ready. */
private suspend fun cameraProvider(context: Context): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            { continuation.resume(future.get()) },
            ContextCompat.getMainExecutor(context)
        )
    }
