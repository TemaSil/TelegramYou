package com.telegramyou.app.music

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

/**
 * How the app's other sounds share the speaker with music — its own player's
 * and any other app's, the same way.
 *
 * Telegram gets this wrong both ways round: a voice message stops the music
 * and never starts it again, and here, before this, a voice message simply
 * played over it. What a music app does, and what this asks the system for:
 * - a voice message or a round video with its sound on [duck]s — the music
 *   goes quiet under the voice and comes back when it ends;
 * - recording, or a video opened full screen, [pause]s — the music stops,
 *   so the microphone does not take it down, and plays on after.
 *
 * Android's audio focus does both; MusicPlayer's ExoPlayer already answers
 * it (it ducks for a "may duck" loss and resumes after a transient one), and
 * so does every other player on the phone.
 */
object AudioFocus {
    private var manager: AudioManager? = null

    fun init(context: Context) {
        manager = context.applicationContext.getSystemService(AudioManager::class.java)
    }

    /** Held while speech plays: other audio turns down, and back up on [Hold.release]. */
    fun duck(): Hold = hold(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, AudioAttributes.CONTENT_TYPE_SPEECH)

    /** Held while recording or watching: other audio pauses, and plays on after [Hold.release]. */
    fun pause(): Hold = hold(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT, AudioAttributes.CONTENT_TYPE_MOVIE)

    private fun hold(gain: Int, contentType: Int): Hold {
        val audio = manager ?: return Hold(null)
        val request = AudioFocusRequest.Builder(gain)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(contentType)
                    .build()
            )
            // Nothing to do on losing it back: a voice message goes on under
            // a call's ringing as it always has.
            .setOnAudioFocusChangeListener { }
            .build()
        return runCatching { audio.requestAudioFocus(request) }
            .map { Hold(request) }
            .getOrDefault(Hold(null))
    }

    /** One claim on the speaker; released once, however many times it is asked. */
    class Hold internal constructor(private var request: AudioFocusRequest?) {
        fun release() {
            val held = request ?: return
            request = null
            runCatching { manager?.abandonAudioFocusRequest(held) }
        }
    }
}
