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
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.appwidget.components.TitleBar
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
import com.telegramyou.app.notifications.ConversationShortcuts
import com.telegramyou.app.telegram.model.ChatPreview
import com.telegramyou.app.ui.avatars.avatarShapeIndex
import com.telegramyou.app.ui.components.SHAPE_COUNT

/**
 * The home screen's chats (2.1, Expressive in 2.2): the newest few, each a
 * tile of its own with its picture in the person's shape, its last line and
 * what is unread, a tap away from the chat — in the colours Android takes
 * from the wallpaper, as the app's own are.
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
            val appearance by app.appearance.settings.collectAsState()
            GlanceTheme { Content(context, recentChats(chats), appearance.shapedAvatars) }
        }
    }

    /**
     * What the widget picker shows (Android 15 and later): the widget itself,
     * drawn from the account's own chats where there are some — which is
     * what tells it apart from the player beside it.
     */
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val app = context.applicationContext as TelegramYouApp
        provideContent {
            GlanceTheme { Content(context, recentChats(app.telegramRepository.chats.value), shaped = true) }
        }
    }

    @Composable
    private fun Content(context: Context, chats: List<ChatPreview>, shaped: Boolean) {
        Scaffold(
            titleBar = {
                TitleBar(
                    startIcon = ImageProvider(R.drawable.ic_widget_mark),
                    title = "Chats",
                    iconColor = GlanceTheme.colors.primary,
                    modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>()),
                    actions = {
                        // The app's own front door.
                        CircleIconButton(
                            imageProvider = ImageProvider(R.drawable.ic_widget_edit),
                            contentDescription = "Open TelegramYou",
                            onClick = actionStartActivity<MainActivity>(),
                            backgroundColor = GlanceTheme.colors.primaryContainer,
                            contentColor = GlanceTheme.colors.onPrimaryContainer
                        )
                    }
                )
            }
        ) {
            if (chats.isEmpty()) {
                Text(
                    "Open TelegramYou to see your chats here",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp),
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .background(GlanceTheme.colors.surface)
                        .cornerRadius(20.dp)
                        .padding(16.dp)
                        .clickable(actionStartActivity<MainActivity>())
                )
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(chats, itemId = { it.id }) { chat ->
                        Column {
                            ChatTile(context, chat, shaped)
                            Spacer(GlanceModifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }

    /**
     * One chat as a tile: the Expressive list, where each item is a rounded
     * surface of its own rather than a line between neighbours, and an
     * unread chat stands out in the secondary container.
     */
    @Composable
    private fun ChatTile(context: Context, chat: ChatPreview, shaped: Boolean) {
        val unread = chat.unreadCount > 0
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(if (unread && !chat.isMuted) GlanceTheme.colors.secondaryContainer else GlanceTheme.colors.surface)
                .cornerRadius(20.dp)
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .clickable(actionStartActivity(ConversationShortcuts.openIntent(context, chat.id)))
        ) {
            Image(
                provider = ImageProvider(
                    WidgetArt.shaped(
                        photoPath = chat.photoPath,
                        title = chat.title,
                        colorSeed = chat.avatarColor,
                        shapeIndex = if (shaped) avatarShapeIndex(chat.avatarColor, SHAPE_COUNT) else 0,
                        sizePx = WidgetArt.px(context, AVATAR_DP)
                    )
                ),
                contentDescription = null,
                modifier = GlanceModifier.size(AVATAR_DP.dp)
            )
            Spacer(GlanceModifier.width(12.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    chat.title,
                    maxLines = 1,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 15.sp,
                        fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium
                    )
                )
                Text(
                    chat.draft.takeIf { it.isNotBlank() }?.let { "Draft: $it" } ?: chat.lastMessage,
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp)
                )
            }
            Spacer(GlanceModifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    chat.timestampLabel,
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp)
                )
                if (unread) {
                    Spacer(GlanceModifier.height(4.dp))
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = GlanceModifier
                            .background(if (chat.isMuted) GlanceTheme.colors.outline else GlanceTheme.colors.primary)
                            .cornerRadius(10.dp)
                            .padding(horizontal = 7.dp, vertical = 1.dp)
                    ) {
                        Text(
                            if (chat.unreadCount > 99) "99+" else chat.unreadCount.toString(),
                            style = TextStyle(color = GlanceTheme.colors.onPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }

    private companion object {
        const val AVATAR_DP = 44
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
