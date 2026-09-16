package com.telegramyou.app.ui.chat

import android.media.MediaPlayer
import android.util.Log

/**
 * Plays one voice message at a time.
 *
 * One at a time is the whole design: starting a second recording while the
 * first is talking produces two people speaking over each other, which is
 * never what a tap on a second bubble means. Starting anything stops whatever
 * was playing.
 *
 * A class rather than composable state, for the same reason as VoiceRecorder:
 * a MediaPlayer holds a file handle and an audio focus request, and both have
 * to be released on every path out — including the one where the screen goes
 * away mid-sentence.
 */
class VoicePlayer {

    private var player: MediaPlayer? = null

    /** The message currently playing, so the right bubble can show it. */
    var playingId: Long? = null
        private set

    /**
     * Starts [path], and answers whether it did.
     *
     * [onFinished] runs when the audio ends by itself — the bubble has to stop
     * showing a pause button for something that is no longer playing, and
     * nothing else would tell it.
     */
    fun play(messageId: Long, path: String, onFinished: () -> Unit): Boolean {
        stop()
        return try {
            player = MediaPlayer().apply {
                setDataSource(path)
                setOnCompletionListener {
                    stop()
                    onFinished()
                }
                prepare()
                start()
            }
            playingId = messageId
            true
        } catch (e: Exception) {
            // A file that was deleted under us, or one the codec will not
            // open. The button goes back to idle rather than pretending.
            Log.w(TAG, "play($path): ${e.message}")
            stop()
            false
        }
    }

    /**
     * Whether audio is actually coming out, as opposed to a player existing.
     *
     * Not the same question as [playingId] being set: a player can be
     * finished, released under us, or never have started. Anything ticking
     * alongside playback should stop on this rather than on its own
     * bookkeeping, or it keeps ticking after the sound has gone.
     */
    fun isPlaying(): Boolean = try {
        player?.isPlaying == true
    } catch (e: IllegalStateException) {
        Log.w(TAG, "isPlaying: ${e.message}")
        false
    }

    /**
     * How far through the current message is, as 0..1.
     *
     * Zero when nothing is playing, and zero for a file whose duration the
     * decoder does not know — a progress bar that fills at a rate unrelated to
     * the sound is worse than one that does not move.
     */
    fun progress(): Float {
        val active = player ?: return 0f
        return try {
            val total = active.duration
            if (total <= 0) 0f else (active.currentPosition.toFloat() / total).coerceIn(0f, 1f)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "progress: ${e.message}")
            0f
        }
    }

    /**
     * Jumps to [fraction] of the way through, if something is playing.
     *
     * Ignored when nothing is: a tap on a bar of a message that is not playing
     * means play it, and that is the caller's decision rather than a seek to
     * nowhere.
     */
    fun seekTo(fraction: Float) {
        val active = player ?: return
        try {
            val total = active.duration
            if (total > 0) active.seekTo((total * fraction.coerceIn(0f, 1f)).toInt())
        } catch (e: IllegalStateException) {
            Log.w(TAG, "seekTo: ${e.message}")
        }
    }

    fun stop() {
        player?.let { active ->
            try {
                active.stop()
            } catch (e: IllegalStateException) {
                // Already finished; release is still what matters.
                Log.w(TAG, "stop: ${e.message}")
            }
            active.release()
        }
        player = null
        playingId = null
    }

    private companion object {
        const val TAG = "VoicePlayer"
    }
}
