package com.telegramyou.app.widgets

import android.content.Context
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
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.telegramyou.app.MainActivity
import com.telegramyou.app.TelegramYouApp
import com.telegramyou.app.notifications.ConversationShortcuts
import com.telegramyou.app.telegram.model.ChatPreview

/**
 * The home screen's recent chats (2.1): the newest few, each with its
 * picture, its last line and what is unread, a tap away from the chat —
 * in the colours Android takes from the wallpaper, as the app's own are.
 *
 * Drawn from the chat list the app already holds; while the app has not
 * signed in, it says so rather than standing empty. Kept current by
 * WidgetUpdates, as the list changes.
 */
class RecentChatsWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as TelegramYouApp
        provideContent {
            val chats by app.telegramRepository.chats.collectAsState()
            GlanceTheme { Content(context, recentChats(chats)) }
        }
    }

    @Composable
    private fun Content(context: Context, chats: List<ChatPreview>) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(24.dp)
                .padding(12.dp)
        ) {
            Text(
                "Chats",
                style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                modifier = GlanceModifier
                    .padding(start = 4.dp, bottom = 8.dp)
                    .clickable(actionStartActivity<MainActivity>())
            )
            if (chats.isEmpty()) {
                Text(
                    "Open TelegramYou to see your chats here",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                    modifier = GlanceModifier.padding(4.dp).clickable(actionStartActivity<MainActivity>())
                )
            } else {
                LazyColumn {
                    items(chats, itemId = { it.id }) { chat -> ChatRow(context, chat) }
                }
            }
        }
    }

    @Composable
    private fun ChatRow(context: Context, chat: ChatPreview) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .cornerRadius(16.dp)
                .clickable(actionStartActivity(ConversationShortcuts.openIntent(context, chat.id)))
        ) {
            Image(
                provider = ImageProvider(ConversationShortcuts.avatar(context, chat, sizeDp = AVATAR_DP)),
                contentDescription = null,
                modifier = GlanceModifier.size(AVATAR_DP.dp)
            )
            Spacer(GlanceModifier.width(12.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    chat.title,
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                )
                Text(
                    chat.draft.takeIf { it.isNotBlank() }?.let { "Draft: $it" } ?: chat.lastMessage,
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp)
                )
            }
            if (chat.unreadCount > 0) {
                Spacer(GlanceModifier.width(8.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = GlanceModifier
                        .background(if (chat.isMuted) GlanceTheme.colors.outline else GlanceTheme.colors.primary)
                        .cornerRadius(12.dp)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        if (chat.unreadCount > 99) "99+" else chat.unreadCount.toString(),
                        style = TextStyle(color = GlanceTheme.colors.onPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    )
                }
            }
        }
    }

    private companion object {
        const val AVATAR_DP = 40
    }
}

/** The chats a widget shows: the list's own order, archived ones left out, the first few. */
internal fun recentChats(chats: List<ChatPreview>): List<ChatPreview> =
    chats.filter { !it.isArchived }.take(RECENT_CHATS)

private const val RECENT_CHATS = 8

/** What puts [RecentChatsWidget] on the home screen. */
class RecentChatsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RecentChatsWidget()
}
