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
