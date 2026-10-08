package com.telegramyou.app.widgets

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import com.telegramyou.app.TelegramYouApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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
                    recentChats(chats).map { listOf(it.id, it.title, it.lastMessage, it.draft, it.unreadCount, it.isMuted, it.photoPath) }
                }
                .distinctUntilChanged()
                .debounce(RECENT_SETTLE_MS)
                .collect { refresh(app, RecentChatsWidget()) }
        }
        scope.launch {
            app.music.state
                .map { Triple(it.track?.messageId, it.isPlaying, it.track?.coverPath) }
                .distinctUntilChanged()
                .collect { refresh(app, NowPlayingWidget()) }
        }
    }

    private suspend fun refresh(context: Context, widget: GlanceAppWidget) {
        runCatching {
            if (GlanceAppWidgetManager(context).getGlanceIds(widget.javaClass).isNotEmpty()) widget.updateAll(context)
        }
    }

    /** A burst of list changes — the list loading — redrawn once. */
    private const val RECENT_SETTLE_MS = 1_500L
}
