package com.telegramyou.app.widgets

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.appwidget.components.SquareIconButton
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
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
 * What is playing, on the home screen (2.1, Expressive in 2.2): the cover
 * cut to one of Material's shapes, the track and its performer, and
 * previous, play or pause, and next — the same player the app's own mini
 * player drives.
 *
 * Laid out for the size it is given. A row four cells wide puts it all on
 * one line; two rows or more give the cover room and the buttons a row of
 * their own; narrower than a row it keeps the cover and the one button that
 * matters. With nothing playing it says so, and opens the app.
 * Kept current by WidgetUpdates.
 */
class NowPlayingWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(NARROW, ROW, CARD))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as TelegramYouApp
        provideContent {
            val playing by app.music.state.collectAsState()
            GlanceTheme { Content(context, playing) }
        }
    }

    /** The widget picker's picture of it (Android 15 and later), with what is playing if anything is. */
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val app = context.applicationContext as TelegramYouApp
        provideContent {
            GlanceTheme { Content(context, app.music.state.value) }
        }
    }

    @Composable
    private fun Content(context: Context, playing: NowPlaying) {
        val size = LocalSize.current
        Scaffold(modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>())) {
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = GlanceModifier.fillMaxSize().padding(vertical = 12.dp)
            ) {
                when {
                    size.height >= CARD.height -> Card(context, playing)
                    size.width >= ROW.width -> Line(context, playing)
                    else -> Narrow(context, playing)
                }
            }
        }
    }

    /** Two rows or more: the cover large, the words beside it, the buttons beneath. */
    @Composable
    private fun Card(context: Context, playing: NowPlaying) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = GlanceModifier.fillMaxWidth().defaultWeight()
            ) {
                Cover(context, playing, 72.dp)
                Spacer(GlanceModifier.width(14.dp))
                Words(playing, titleSize = 18)
            }
            if (playing.track != null) {
                Spacer(GlanceModifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = GlanceModifier.fillMaxWidth()
                ) {
                    Skip(R.drawable.ic_widget_previous, "Previous", PlayerControl.ACTION_PREVIOUS)
                    Spacer(GlanceModifier.width(10.dp))
                    PlayPause(playing)
                    Spacer(GlanceModifier.width(10.dp))
                    Skip(R.drawable.ic_widget_next, "Next", PlayerControl.ACTION_NEXT)
                }
            }
        }
    }

    /** One row: cover, words, then the buttons. */
    @Composable
    private fun Line(context: Context, playing: NowPlaying) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxSize()) {
            Cover(context, playing, 52.dp)
            Spacer(GlanceModifier.width(12.dp))
            Words(playing, titleSize = 15, modifier = GlanceModifier.defaultWeight())
            if (playing.track != null) {
                Skip(R.drawable.ic_widget_previous, "Previous", PlayerControl.ACTION_PREVIOUS)
                Spacer(GlanceModifier.width(4.dp))
                PlayPause(playing)
                Spacer(GlanceModifier.width(4.dp))
                Skip(R.drawable.ic_widget_next, "Next", PlayerControl.ACTION_NEXT)
            }
        }
    }

    /** Too narrow for words: the cover and play or pause. */
    @Composable
    private fun Narrow(context: Context, playing: NowPlaying) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxSize()) {
            Cover(context, playing, 48.dp)
            if (playing.track != null) {
                Spacer(GlanceModifier.defaultWeight())
                PlayPause(playing)
            }
        }
    }

    @Composable
    private fun Words(playing: NowPlaying, titleSize: Int, modifier: GlanceModifier = GlanceModifier) {
        val track = playing.track
        Column(modifier = modifier) {
            Text(
                track?.title ?: "Nothing playing",
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = titleSize.sp, fontWeight = FontWeight.Bold)
            )
            Text(
                track?.performer?.takeIf { it.isNotBlank() } ?: if (track == null) "Music from your chats" else "",
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp)
            )
        }
    }

    /**
     * The cover in the eight-scalloped cookie, Expressive's signature shape;
     * without one, a note on the primary container in the same shape.
     */
    @Composable
    private fun Cover(context: Context, playing: NowPlaying, size: Dp) {
        val cover = playing.track?.coverPath
        if (cover != null) {
            Image(
                ImageProvider(
                    WidgetArt.shaped(
                        photoPath = cover,
                        title = playing.track?.title.orEmpty(),
                        colorSeed = 0,
                        shapeIndex = COVER_SHAPE,
                        sizePx = WidgetArt.px(context, size.value.toInt())
                    )
                ),
                contentDescription = null,
                modifier = GlanceModifier.size(size)
            )
        } else {
            Box(contentAlignment = Alignment.Center, modifier = GlanceModifier.size(size)) {
                Image(
                    ImageProvider(WidgetArt.tile(context, COVER_SHAPE, size.value.toInt())),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.primaryContainer),
                    modifier = GlanceModifier.size(size)
                )
                Image(
                    ImageProvider(R.drawable.ic_widget_music),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer),
                    modifier = GlanceModifier.size(size * 0.45f)
                )
            }
        }
    }

    /**
     * The button that matters, filled in the primary colour — Glance's
     * square icon button, the larger of its two, so it outranks the round
     * ones beside it the way Expressive's player does.
     */
    @Composable
    private fun PlayPause(playing: NowPlaying) {
        SquareIconButton(
            imageProvider = ImageProvider(if (playing.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play),
            contentDescription = if (playing.isPlaying) "Pause" else "Play",
            onClick = actionRunCallback<PlayerControl>(actionParametersOf(PlayerControl.KEY to PlayerControl.ACTION_TOGGLE)),
            backgroundColor = GlanceTheme.colors.primary,
            contentColor = GlanceTheme.colors.onPrimary
        )
    }

    /** Previous and next, as tonal circles beside it. */
    @Composable
    private fun Skip(icon: Int, label: String, action: String) {
        CircleIconButton(
            imageProvider = ImageProvider(icon),
            contentDescription = label,
            onClick = actionRunCallback<PlayerControl>(actionParametersOf(PlayerControl.KEY to action)),
            backgroundColor = GlanceTheme.colors.secondaryContainer,
            contentColor = GlanceTheme.colors.onSecondaryContainer
        )
    }

    private companion object {
        val NARROW = DpSize(110.dp, 48.dp)
        val ROW = DpSize(250.dp, 48.dp)
        val CARD = DpSize(180.dp, 130.dp)

        /** The eight-scalloped cookie of the avatar set; see AvatarCluster. */
        const val COVER_SHAPE = 5
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
