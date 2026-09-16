package com.telegramyou.app.ui.chat

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Records one voice message at a time.
 *
 * A class rather than a composable's `remember`, because a `MediaRecorder`
 * holds the microphone: it has to be released on the way out of every path,
 * including the ones where the recording is thrown away, and a lifecycle that
 * short lives badly inside a recomposition.
 *
 * OGG/Opus where the platform has it, which is what Telegram's own voice notes
 * are, and 3GP/AMR below that — Opus in an OGG container only became writable
 * in Android 10.
 */
class VoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var target: File? = null
    private var startedAt = 0L

    val isRecording: Boolean get() = recorder != null

    /**
     * Begins recording, or answers false if the microphone could not be taken.
     *
     * False rather than an exception: the caller is a button, and the only
     * thing it can do about a busy microphone is not show a recording state.
     */
    fun start(): Boolean {
        if (recorder != null) return false
        val directory = File(context.cacheDir, "voice").apply { mkdirs() }
        val file = File(directory, "voice-${System.currentTimeMillis()}.$extension")
        return try {
            val created = newRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                if (useOpus) {
                    setOutputFormat(MediaRecorder.OutputFormat.OGG)
                    setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                    // What Telegram uses for voice notes; more than this is
                    // bandwidth spent on a frequency range speech does not use.
                    setAudioSamplingRate(48_000)
                    setAudioEncodingBitRate(32_000)
                } else {
                    setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                }
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = created
            target = file
            startedAt = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            Log.w(TAG, "start: ${e.message}")
            // A half-opened recorder still holds the microphone.
            release()
            file.delete()
            false
        }
    }

    /**
     * Stops and answers the recording, or null if there is nothing worth
     * sending.
     *
     * Under a second is a slip of the thumb rather than a message, and
     * MediaRecorder throws on stop() when it has captured nothing at all —
     * both end the same way, with the file deleted.
     */
    fun stop(): Recording? {
        val active = recorder ?: return null
        val file = target
        val seconds = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
        val stopped = try {
            active.stop()
            true
        } catch (e: RuntimeException) {
            Log.w(TAG, "stop: ${e.message}")
            false
        }
        release()
        if (!stopped || file == null || seconds < 1) {
            file?.delete()
            return null
        }
        return Recording(file.absolutePath, seconds)
    }

    /** Throws the recording away — for a cancelled gesture. */
    fun cancel() {
        val file = target
        try {
            recorder?.stop()
        } catch (e: RuntimeException) {
            Log.w(TAG, "cancel: ${e.message}")
        }
        release()
        file?.delete()
    }

    private fun release() {
        recorder?.release()
        recorder = null
        target = null
    }

    @Suppress("DEPRECATION")
    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            // The no-argument constructor is deprecated from API 31 and is the
            // only one that exists below it.
            MediaRecorder()
        }

    data class Recording(val path: String, val durationSeconds: Int)

    private companion object {
        const val TAG = "VoiceRecorder"

        /** OGG/Opus is writable from Android 10; AMR is the fallback. */
        val useOpus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val extension = if (useOpus) "ogg" else "3gp"
    }
}
