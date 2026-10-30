package com.telegramyou.app.telegram.calls

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import com.telegramyou.app.music.AudioFocus

/**
 * Android's side of a call's sound (2.9): the phone put in its call mode,
 * so the voice goes to the earpiece and the microphone's echo cancelling
 * is on; the speaker when asked; and music paused for the length of it, as
 * a phone call pauses it; and the screen off while the phone is held to an
 * ear, so a cheek does not press Mute. The voice itself is CallMedia's.
 */
class CallAudio(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private var focus: AudioFocus.Hold? = null
    private val proximity: PowerManager.WakeLock? =
        context.getSystemService(PowerManager::class.java)
            .takeIf { it.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK) }
            ?.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "TelegramYou:call")
            ?.apply { setReferenceCounted(false) }

    fun start() {
        if (focus != null) return
        focus = AudioFocus.pause()
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        setSpeaker(false)
    }

    /** The loudspeaker, or back to the earpiece — or to a headset, which Android prefers by itself. */
    fun setSpeaker(on: Boolean) {
        // Only at the ear: on the speaker the phone is looked at.
        if (on || focus == null) proximity?.release() else proximity?.acquire(PROXIMITY_LIMIT_MS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (on) {
                audio.availableCommunicationDevices
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    ?.let(audio::setCommunicationDevice)
            } else {
                audio.clearCommunicationDevice()
            }
        } else {
            @Suppress("DEPRECATION")
            audio.isSpeakerphoneOn = on
        }
    }

    fun stop() {
        val held = focus ?: return
        focus = null
        setSpeaker(false)
        audio.mode = AudioManager.MODE_NORMAL
        held.release()
        proximity?.release()
    }

    private companion object {
        /** A bound on the wake lock, should a call never be told it ended. */
        const val PROXIMITY_LIMIT_MS = 4 * 60 * 60 * 1000L
    }
}
