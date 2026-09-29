package com.telegramyou.app.music

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.telegramyou.app.MainActivity
import com.telegramyou.app.TelegramYouApp

/**
 * The music player as the system sees it: a Media3 session, so the shade,
 * the lock screen, a headset's buttons, a watch and a car all reach it, and
 * Media3's own notification — the platform's media style, nothing drawn
 * here. The player itself is the app's [MusicPlayer]; this only shows it.
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val music = (application as TelegramYouApp).music
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaSession.Builder(this, music.sessionPlayer)
            .setSessionActivity(open)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Swiped away from Recents while paused: nothing left to keep alive. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        // The session goes; the player is the app's and stays with it.
        session?.release()
        session = null
        super.onDestroy()
    }
}
