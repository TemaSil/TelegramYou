package com.telegramyou.app.telegram

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import com.telegramyou.app.MainActivity
import com.telegramyou.app.R
import com.telegramyou.app.TelegramYouApp
import com.telegramyou.app.notifications.NotifiableMessage
import com.telegramyou.app.notifications.NotificationContext
import com.telegramyou.app.notifications.NotificationDecision
import com.telegramyou.app.notifications.decideNotification
import com.telegramyou.app.notifications.groupForShade
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Holds the connection to Telegram while the app is not on screen, and turns
 * the messages that arrive over it into notifications.
 *
 * The service existed before this — declared in the manifest, showing its own
 * ongoing notice — but nothing ever started it and it listened to nothing.
 * The ongoing notice is still the price Android charges for staying alive;
 * the point is what happens underneath it.
 *
 * What to notify about is not decided here. `decideNotification` in :core
 * owns that, with tests, because "do not buzz for the chat being read" and
 * "do not buzz twice after a reconnect" are exactly the rules that go wrong
 * quietly and cannot be checked by looking at a screen.
 */
class TelegramForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Messages already posted, per chat, so a shade entry can show a
     * conversation rather than only its latest line.
     *
     * Held in memory deliberately: a notification does not outlive the
     * process that posted it, so persisting this would restore entries for
     * notifications Android has already dropped.
     */
    private val posted = mutableMapOf<Long, MutableList<NotifiableMessage>>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(ONGOING_NOTIFICATION_ID, ongoingNotification())
        observeArrivals()
        // START_STICKY so a process killed for memory comes back: a messenger
        // that stops delivering after the first low-memory moment is worse
        // than one that never claimed to.
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun observeArrivals() {
        val app = application as TelegramYouApp
        scope.launch {
            app.telegramRepository.incomingMessages.collect { message ->
                val chat = app.telegramRepository.chats.value
                    .firstOrNull { it.id == message.chatId }
                val notifiable = NotifiableMessage(
                    chatId = message.chatId,
                    messageId = message.id,
                    chatTitle = chat?.title ?: message.senderName.orEmpty(),
                    senderName = message.senderName,
                    text = message.text,
                    timestampMillis = message.date * 1000L,
                    isOutgoing = message.isOutgoing,
                    isChatMuted = chat?.isMuted == true
                )
                val decision = decideNotification(
                    notifiable,
                    NotificationContext(
                        openChatId = AppVisibility.openChatId,
                        isAppInForeground = AppVisibility.isInForeground,
                        alreadyNotified = posted.values.flatten()
                            .map { it.messageId }
                            .toSet()
                    )
                )
                if (decision is NotificationDecision.Notify) post(decision.message)
            }
        }
    }

    private fun post(message: NotifiableMessage) {
        val forChat = posted.getOrPut(message.chatId) { mutableListOf() }
        forChat.add(message)
        // Trimmed rather than unbounded: the shade shows a handful of lines
        // and a chat left unread overnight would otherwise grow this forever.
        while (forChat.size > MAX_LINES_PER_CHAT) forChat.removeAt(0)

        val manager = NotificationManagerCompat.from(this)
        if (!canPost()) return
        groupForShade(forChat.toList()).forEach { entry ->
            manager.notify(entry.chatId.toInt(), conversationNotification(entry.messages))
        }
    }

    /**
     * POST_NOTIFICATIONS is a runtime permission from Android 13, and a
     * service cannot ask for it. Posting without it throws, so this checks
     * and stays quiet — the app asks when a screen is in front of someone.
     */
    private fun canPost(): Boolean =
        NotificationManagerCompat.from(this).areNotificationsEnabled()

    private fun conversationNotification(messages: List<NotifiableMessage>): Notification {
        val latest = messages.last()
        val me = Person.Builder().setName(getString(R.string.app_name)).build()
        val style = NotificationCompat.MessagingStyle(me)
            .setConversationTitle(latest.chatTitle)
            // Only for a group: in a one-to-one chat the sender is the chat,
            // and the title would be repeated above every line.
            .setGroupConversation(messages.any { it.senderName != null })
        messages.forEach { message ->
            val sender = message.senderName
                ?.takeIf { it.isNotBlank() }
                ?.let { Person.Builder().setName(it).build() }
            style.addMessage(message.text, message.timestampMillis, sender)
        }
        return NotificationCompat.Builder(this, TelegramYouApp.CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setStyle(style)
            .setAutoCancel(true)
            .setContentIntent(openChatIntent(latest.chatId))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()
    }

    /**
     * Opens the conversation the notification is about.
     *
     * The chat id rides on the launch intent rather than on a deep link URI:
     * the graph's routes are plain strings built in `Route`, and a second
     * spelling of `chat/{chatId}` in a manifest would be one more place for
     * the two to drift apart.
     */
    private fun openChatIntent(chatId: Long): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_CHAT_ID, chatId)
        return PendingIntent.getActivity(
            this,
            // A request code per chat: one shared code would have every
            // notification reuse the first chat's extras, however
            // FLAG_UPDATE_CURRENT is spelled.
            chatId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun ongoingNotification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, TelegramYouApp.CHANNEL_SYNC)
            .setContentTitle(getString(R.string.notification_title))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        const val EXTRA_CHAT_ID = "com.telegramyou.app.EXTRA_CHAT_ID"

        private const val ONGOING_NOTIFICATION_ID = 42
        private const val MAX_LINES_PER_CHAT = 6

        fun start(context: android.content.Context) {
            context.startForegroundService(Intent(context, TelegramForegroundService::class.java))
        }

        fun stop(context: android.content.Context) {
            context.stopService(Intent(context, TelegramForegroundService::class.java))
        }
    }
}
