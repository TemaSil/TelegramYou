package com.telegramyou.app.widgets

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.telegramyou.app.MainActivity
import com.telegramyou.app.R
import com.telegramyou.app.TelegramYouApp
import com.telegramyou.app.music.NowPlaying

/**
 * What is playing, on the home screen (2.1): the cover, the track and its
 * performer, and previous, play or pause, and next — the same player the
 * app's own mini player drives. With nothing playing it says so, and opens
 * the app. Kept current by WidgetUpdates.
 */
class NowPlayingWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as TelegramYouApp
        provideContent {
            val playing by app.music.state.collectAsState()
            GlanceTheme { Content(playing) }
        }
    }

    @Composable
    private fun Content(playing: NowPlaying) {
        val track = playing.track
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(24.dp)
                .padding(12.dp)
                .clickable(actionStartActivity<MainActivity>())
        ) {
            val cover = track?.coverPath?.let { path -> runCatching { BitmapFactory.decodeFile(path) }.getOrNull() }
            Box(
                contentAlignment = Alignment.Center,
                modifier = GlanceModifier
                    .size(COVER_DP.dp)
                    .cornerRadius(16.dp)
                    .background(GlanceTheme.colors.secondaryContainer)
            ) {
                if (cover != null) {
                    Image(ImageProvider(cover), contentDescription = null, modifier = GlanceModifier.size(COVER_DP.dp))
                } else {
                    Image(ImageProvider(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = GlanceModifier.size(40.dp))
                }
            }
            Spacer(GlanceModifier.width(12.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    track?.title ?: "Nothing playing",
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                )
                Text(
                    track?.performer?.takeIf { it.isNotBlank() } ?: if (track == null) "Music from your chats" else "",
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp)
                )
            }
            if (track != null) {
                Control(android.R.drawable.ic_media_previous, "Previous", PlayerControl.ACTION_PREVIOUS)
                Control(
                    if (playing.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (playing.isPlaying) "Pause" else "Play",
                    PlayerControl.ACTION_TOGGLE
                )
                Control(android.R.drawable.ic_media_next, "Next", PlayerControl.ACTION_NEXT)
            }
        }
    }

    @Composable
    private fun Control(icon: Int, label: String, action: String) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = GlanceModifier
                .size(40.dp)
                .cornerRadius(20.dp)
                .clickable(actionRunCallback<PlayerControl>(actionParametersOf(PlayerControl.KEY to action)))
        ) {
            Image(
                ImageProvider(icon),
                contentDescription = label,
                colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.onSurface),
                modifier = GlanceModifier.size(24.dp)
            )
        }
    }

    private companion object {
        const val COVER_DP = 56
    }
}

/** A widget's button, run in the app: the player, told what was pressed. */
class PlayerControl : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val music = (context.applicationContext as TelegramYouApp).music
        // On the main thread, where the player lives.
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            when (parameters[KEY]) {
                ACTION_PREVIOUS -> music.previous()
                ACTION_TOGGLE -> music.toggle()
                ACTION_NEXT -> music.next()
            }
        }
    }

    companion object {
        val KEY = ActionParameters.Key<String>("player_action")
        const val ACTION_PREVIOUS = "previous"
        const val ACTION_TOGGLE = "toggle"
        const val ACTION_NEXT = "next"
    }
}

/** What puts [NowPlayingWidget] on the home screen. */
class NowPlayingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NowPlayingWidget()
}
