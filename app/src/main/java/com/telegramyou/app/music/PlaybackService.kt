package com.telegramyou.app.music

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.telegramyou.app.MainActivity
import com.telegramyou.app.TelegramYouApp
import com.telegramyou.app.telegram.model.QueueOrder
import com.telegramyou.app.telegram.model.RepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The music player as the system sees it: a Media3 session, so the shade,
 * the lock screen, a headset's buttons, a watch and a car all reach it, and
 * Media3's own notification — the platform's media style, nothing drawn
 * here. The player itself is the app's [MusicPlayer]; this only shows it.
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val music = (application as TelegramYouApp).music
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val built = MediaSession.Builder(this, music.sessionPlayer)
            .setSessionActivity(open)
            .build()
        session = built
        // Added to the service now, not when a controller first connects:
        // the media notification — the shade's player, the lock screen's —
        // is kept only for sessions the service holds, and the service only
        // takes one in by itself when a MediaController binds to it. Nothing
        // in the app does, so the session played with no notification at
        // all, which is how it reached a phone in 1.6.3 to 1.6.5.
        addSession(built)
        // Shuffle and repeat beside the track buttons in the shade and on the
        // lock screen — asked for of Telegram's player, which has neither
        // there. Each button shows the state now and sets the next one.
        scope.launch {
            music.state
                .map { (it.queue.order == QueueOrder.Shuffled) to it.queue.repeat }
                .distinctUntilChanged()
                .collect { (shuffled, repeat) ->
                    runCatching { built.setMediaButtonPreferences(orderButtons(shuffled, repeat)) }
                }
        }
    }

    @OptIn(UnstableApi::class)
    private fun orderButtons(shuffled: Boolean, repeat: RepeatMode): List<CommandButton> = listOf(
        CommandButton.Builder(if (shuffled) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF)
            .setDisplayName(if (shuffled) "Shuffle on" else "Shuffle off")
            .setPlayerCommand(Player.COMMAND_SET_SHUFFLE_MODE, !shuffled)
            .build(),
        CommandButton.Builder(
            when (repeat) {
                RepeatMode.Off -> CommandButton.ICON_REPEAT_OFF
                RepeatMode.All -> CommandButton.ICON_REPEAT_ALL
                RepeatMode.One -> CommandButton.ICON_REPEAT_ONE
            }
        )
            .setDisplayName(repeat.label)
            .setPlayerCommand(
                Player.COMMAND_SET_REPEAT_MODE,
                // The same round the player screen's button goes: off, all, one.
                when (repeat) {
                    RepeatMode.Off -> Player.REPEAT_MODE_ALL
                    RepeatMode.All -> Player.REPEAT_MODE_ONE
                    RepeatMode.One -> Player.REPEAT_MODE_OFF
                }
            )
            .build()
    )

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Swiped away from Recents while paused: nothing left to keep alive. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        // The session goes; the player is the app's and stays with it.
        scope.cancel()
        session?.release()
        session = null
        super.onDestroy()
    }
}
