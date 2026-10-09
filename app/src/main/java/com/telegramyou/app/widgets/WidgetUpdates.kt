package com.telegramyou.app.widgets

import android.content.Context
import android.os.Build
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import com.telegramyou.app.BuildConfig
import com.telegramyou.app.TelegramYouApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Keeps the home screen's widgets current (2.1). A widget draws from what
 * the app holds only while its own session lasts; after that it is a
 * picture until told to redraw — so it is told, when what it shows has
 * changed, and only if one of its kind is on a home screen at all.
 */
object WidgetUpdates {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @OptIn(FlowPreview::class)
    fun start(app: TelegramYouApp) {
        scope.launch {
            app.telegramRepository.chats
                // What a row shows, and nothing else: the list changes for
                // reasons a widget does not draw — typing, online dots.
                .map { chats ->
                    recentChats(chats).map {
                        listOf(it.id, it.title, it.lastMessage, it.draft, it.unreadCount, it.isMuted, it.photoPath, it.timestampLabel)
                    }
                }
                .distinctUntilChanged()
                .debounce(RECENT_SETTLE_MS)
                .collect { refresh(app, RecentChatsWidget()) }
        }
        scope.launch {
            // Shaped avatars on or off (Appearance) re-shapes the list's.
            app.appearance.settings
                .map { it.shapedAvatars }
                .distinctUntilChanged()
                .drop(1)
                .collect { refresh(app, RecentChatsWidget()) }
        }
        scope.launch {
            app.music.state
                .map { Triple(it.track?.messageId, it.isPlaying, it.track?.coverPath) }
                .distinctUntilChanged()
                .collect { refresh(app, NowPlayingWidget()) }
        }
        scope.launch {
            // Someone on a photo widget set a new photo (2.2): only the list's
            // pictures matter, and each widget checks its own person.
            app.telegramRepository.chats
                .map { chats -> chats.map { it.id to it.photoPath } }
                .distinctUntilChanged()
                .debounce(RECENT_SETTLE_MS)
                .collect {
                    val chats = app.telegramRepository.chats.value
                    runCatching {
                        GlanceAppWidgetManager(app).getGlanceIds(PersonWidget::class.java).forEach { glanceId ->
                            PersonWidget.refreshIfChanged(app, glanceId, chats) { chatId ->
                                runCatching { app.telegramRepository.chatPhoto(chatId) }.getOrNull()
                            }
                        }
                    }
                }
        }
        publishPreviews(app)
    }

    private suspend fun refresh(context: Context, widget: GlanceAppWidget) {
        runCatching {
            if (GlanceAppWidgetManager(context).getGlanceIds(widget.javaClass).isNotEmpty()) widget.updateAll(context)
        }
    }

    /**
     * The widget picker's pictures (Android 15 and later): each widget drawn
     * by its own code, so the chats, the player and the photo read as three
     * different things there rather than as one icon in three sizes — which
     * is how they looked in 2.1 (from the owner, 9 October).
     *
     * Once a version, after the chat list has arrived so the chats widget is
     * not pictured empty: Android limits how often an app may set these.
     */
    private fun publishPreviews(app: TelegramYouApp) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(PREVIEWS_FOR, 0) == BuildConfig.VERSION_CODE) return
        scope.launch(Dispatchers.Default) {
            withTimeoutOrNull(CHATS_WAIT_MS) { app.telegramRepository.chats.first { it.isNotEmpty() } }
            val manager = GlanceAppWidgetManager(app)
            val published = runCatching {
                listOf(
                    manager.setWidgetPreviews(RecentChatsWidgetReceiver::class),
                    manager.setWidgetPreviews(NowPlayingWidgetReceiver::class),
                    manager.setWidgetPreviews(PersonWidgetReceiver::class)
                )
            }.isSuccess
            if (published) prefs.edit().putInt(PREVIEWS_FOR, BuildConfig.VERSION_CODE).apply()
        }
    }

    /** A burst of list changes — the list loading — redrawn once. */
    private const val RECENT_SETTLE_MS = 1_500L
    private const val CHATS_WAIT_MS = 30_000L
    private const val PREFS = "widgets"
    private const val PREVIEWS_FOR = "previews_for_version"
}
