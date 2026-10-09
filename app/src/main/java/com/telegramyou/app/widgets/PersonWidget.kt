package com.telegramyou.app.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.telegramyou.app.R
import com.telegramyou.app.notifications.ConversationShortcuts
import com.telegramyou.app.telegram.model.ChatPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Someone's photo on the home screen, in a shape (2.2) — the way Google
 * Photos puts a person there: no frame and no words, the photo itself cut to
 * one of Material's shapes, a tap away from the chat with them.
 *
 * Who and which shape are chosen in PersonWidgetSetup as the widget is
 * placed, and again later from the launcher's reconfigure. Both live in the
 * widget's own Glance state, with a copy of the photo at full size in the
 * app's files: the widget draws without the app having signed in or even
 * started, and without asking Telegram for the photo every time.
 */
class PersonWidget : GlanceAppWidget() {

    /** The size it really is: the photo is drawn to fit it, not one size for all. */
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        provideContent {
            val state = currentState<Preferences>()
            GlanceTheme { Content(context, appWidgetId, state) }
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent {
            GlanceTheme { Placeholder(context, appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID, preview = true) }
        }
    }

    override suspend fun onDelete(context: Context, glanceId: GlanceId) {
        photoFile(context, GlanceAppWidgetManager(context).getAppWidgetId(glanceId)).delete()
    }

    @Composable
    private fun Content(context: Context, appWidgetId: Int, state: Preferences) {
        val chatId = state[CHAT_ID]
        if (chatId == null) {
            Placeholder(context, appWidgetId, preview = false)
            return
        }
        val size = LocalSize.current
        val side = minOf(size.width.value, size.height.value).toInt().coerceAtLeast(MIN_SIDE_DP)
        Box(
            contentAlignment = Alignment.Center,
            modifier = GlanceModifier
                .fillMaxSize()
                .clickable(actionStartActivity(ConversationShortcuts.openIntent(context, chatId)))
        ) {
            Image(
                provider = ImageProvider(
                    picture(
                        context,
                        photoPath = state[PHOTO]?.takeIf { File(it).exists() },
                        title = state[TITLE].orEmpty(),
                        colorSeed = state[COLOR_SEED] ?: chatId,
                        shape = state[SHAPE] ?: DEFAULT_SHAPE,
                        sideDp = side
                    )
                ),
                contentDescription = state[TITLE],
                modifier = GlanceModifier.size(side.dp)
            )
        }
    }

    /**
     * Before anyone is chosen — or in the widget picker: the shape it will
     * take, holding a person, and what to do. The setup opens on a tap, for
     * a widget that came without it.
     */
    @Composable
    private fun Placeholder(context: Context, appWidgetId: Int, preview: Boolean) {
        val tap = if (preview) GlanceModifier else GlanceModifier.clickable(actionStartActivity(setupIntent(context, appWidgetId)))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
            modifier = GlanceModifier.fillMaxSize().then(tap)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = GlanceModifier.size(PLACEHOLDER_DP.dp)) {
                Image(
                    ImageProvider(WidgetArt.tile(context, DEFAULT_SHAPE, PLACEHOLDER_DP, upright = true)),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.primaryContainer),
                    modifier = GlanceModifier.size(PLACEHOLDER_DP.dp)
                )
                Image(
                    ImageProvider(R.drawable.ic_widget_person),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer),
                    modifier = GlanceModifier.size((PLACEHOLDER_DP / 2).dp)
                )
            }
            if (!preview) {
                Spacer(GlanceModifier.height(8.dp))
                Text(
                    "Choose someone",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                )
            }
        }
    }

    companion object {
        private val CHAT_ID = longPreferencesKey("chat_id")
        private val TITLE = stringPreferencesKey("title")
        private val COLOR_SEED = longPreferencesKey("color_seed")
        private val SHAPE = intPreferencesKey("shape")
        private val PHOTO = stringPreferencesKey("photo")

        /** The small photo the full-size copy was taken against; see [refreshIfChanged]. */
        private val PHOTO_SOURCE = stringPreferencesKey("photo_source")

        /** The cookie: Expressive's signature, and the first thing the setup offers. */
        const val DEFAULT_SHAPE = 5
        private const val PLACEHOLDER_DP = 96
        private const val MIN_SIDE_DP = 48

        /**
         * Large enough for the biggest widget a phone will make of it, small
         * enough to stay inside what Android lets one widget's pictures take.
         */
        private const val MAX_SIDE_PX = 720

        /** The setup, for [appWidgetId], as the launcher itself opens it. */
        fun setupIntent(context: Context, appWidgetId: Int): Intent =
            Intent(context, PersonWidgetSetup::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

        /**
         * Shape [shape] of the set, upright, holding [photoPath] or the
         * initials — what the widget draws, and what the setup previews, so
         * the one is exactly the other.
         */
        fun picture(context: Context, photoPath: String?, title: String, colorSeed: Long, shape: Int, sideDp: Int): Bitmap =
            WidgetArt.shaped(
                photoPath = photoPath,
                title = title,
                colorSeed = colorSeed,
                shapeIndex = shape,
                sizePx = WidgetArt.px(context, sideDp).coerceAtMost(MAX_SIDE_PX),
                upright = true
            )

        /** Who and which shape this widget was set to, if it has been. */
        suspend fun chosen(context: Context, glanceId: GlanceId): Pair<Long, Int>? {
            val state = androidx.glance.appwidget.state.getAppWidgetState(
                context, androidx.glance.state.PreferencesGlanceStateDefinition, glanceId
            )
            val chatId = state[CHAT_ID] ?: return null
            return chatId to (state[SHAPE] ?: DEFAULT_SHAPE)
        }

        /**
         * Sets the widget to [chat] in [shape]: the photo fetched at full size
         * through [fullPhoto] and kept, the state written, the widget redrawn.
         */
        suspend fun configure(
            context: Context,
            appWidgetId: Int,
            chat: ChatPreview,
            shape: Int,
            fullPhoto: suspend (Long) -> String?
        ) {
            val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
            val kept = keepPhoto(context, appWidgetId, fullPhoto(chat.id))
            updateAppWidgetState(context, glanceId) { prefs ->
                prefs[CHAT_ID] = chat.id
                prefs[TITLE] = chat.title
                prefs[COLOR_SEED] = chat.avatarColor
                prefs[SHAPE] = shape
                if (kept != null) prefs[PHOTO] = kept else prefs.remove(PHOTO)
                if (chat.photoPath != null) prefs[PHOTO_SOURCE] = chat.photoPath!! else prefs.remove(PHOTO_SOURCE)
            }
            PersonWidget().update(context, glanceId)
        }

        /**
         * The person changed their photo: fetch the new one. Told about every
         * change of the chat list by WidgetUpdates, and does nothing unless
         * the chat's photo is not the one this copy was made from.
         */
        suspend fun refreshIfChanged(
            context: Context,
            glanceId: GlanceId,
            chats: List<ChatPreview>,
            fullPhoto: suspend (Long) -> String?
        ) {
            val state = androidx.glance.appwidget.state.getAppWidgetState(
                context, androidx.glance.state.PreferencesGlanceStateDefinition, glanceId
            )
            val chatId = state[CHAT_ID] ?: return
            val chat = chats.firstOrNull { it.id == chatId } ?: return
            val source = chat.photoPath ?: return
            if (source == state[PHOTO_SOURCE]) return
            val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
            val kept = keepPhoto(context, appWidgetId, fullPhoto(chatId)) ?: return
            updateAppWidgetState(context, glanceId) { prefs ->
                prefs[PHOTO] = kept
                prefs[PHOTO_SOURCE] = source
                prefs[TITLE] = chat.title
            }
            PersonWidget().update(context, glanceId)
        }

        private fun photoFile(context: Context, appWidgetId: Int): File =
            File(File(context.filesDir, "widgets"), "person-$appWidgetId.jpg")

        /** A copy of [source] the widget owns, no larger than it can be drawn. */
        private suspend fun keepPhoto(context: Context, appWidgetId: Int, source: String?): String? =
            withContext(Dispatchers.IO) {
                source ?: return@withContext null
                val photo = WidgetArt.decode(source, MAX_SIDE_PX) ?: return@withContext null
                val file = photoFile(context, appWidgetId)
                file.parentFile?.mkdirs()
                runCatching {
                    file.outputStream().use { photo.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                    file.path
                }.getOrNull()
            }
    }
}

/** What puts [PersonWidget] on the home screen. */
class PersonWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PersonWidget()
}
