package com.telegramyou.app.telegram

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.telegramyou.app.MainActivity
import com.telegramyou.app.R
import com.telegramyou.app.TelegramYouApp
import com.telegramyou.app.notifications.NotifiableMessage
import com.telegramyou.app.notifications.NotificationContext
import com.telegramyou.app.notifications.NotificationDecision
import com.telegramyou.app.notifications.PostedNotifications
import com.telegramyou.app.notifications.decideNotification
import com.telegramyou.app.notifications.groupForShade
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

    /** The one collector of arrivals; see onStartCommand. */
    private var arrivals: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(ONGOING_NOTIFICATION_ID, ongoingNotification())
        // Once per service, not once per start. The activity starts this in
        // onCreate, so every rotation or theme change is another start — and
        // each used to add another collector, until one message was being
        // posted as many times as the screen had been turned.
        if (arrivals == null) arrivals = observeArrivals()
        // START_STICKY so a process killed for memory comes back: a messenger
        // that stops delivering after the first low-memory moment is worse
        // than one that never claimed to.
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun observeArrivals(): Job {
        val app = application as TelegramYouApp
        return scope.launch {
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
                        alreadyNotified = PostedNotifications.shownMessageIds()
                    )
                )
                if (decision is NotificationDecision.Notify) post(decision.message)
            }
        }
    }

    private fun post(message: NotifiableMessage) {
        // Null when it is already on show: nothing new to post.
        val forChat = PostedNotifications.add(message, MAX_LINES_PER_CHAT) ?: return
        if (!canPost()) return
        val manager = NotificationManagerCompat.from(this)
        groupForShade(forChat).forEach { entry ->
            manager.notify(
                PostedNotifications.notificationId(entry.chatId),
                conversationNotification(entry.messages)
            )
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
            .addAction(replyAction(latest.chatId))
            .build()
    }

    /**
     * Answering without opening the app, which is most of what a messenger's
     * notification is for.
     *
     * `setAllowGeneratedReplies` lets the system offer its own suggestions
     * beside the field — on a watch that is often the whole interaction, and
     * it costs nothing to permit.
     *
     * `SEMANTIC_ACTION_REPLY` is not decoration: it is how a watch, a car and
     * Android Auto know this action is the reply one rather than a button
     * that happens to be first.
     */
    private fun replyAction(chatId: Long): NotificationCompat.Action {
        val remoteInput = RemoteInput.Builder(NotificationReplyReceiver.KEY_REPLY)
            .setLabel(getString(R.string.notification_reply_hint))
            .build()
        val intent = Intent(this, NotificationReplyReceiver::class.java)
            .putExtra(NotificationReplyReceiver.EXTRA_CHAT_ID, chatId)
        val pending = PendingIntent.getBroadcast(
            this,
            // Per chat, like the open intent: one shared request code would
            // have every reply land in whichever chat was notified first.
            PostedNotifications.notificationId(chatId),
            intent,
            // MUTABLE, and it has to be: the system writes the typed text
            // into this intent before delivering it. An immutable one arrives
            // with no text at all, which is the classic way this feature
            // fails silently.
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Action.Builder(
            R.drawable.ic_launcher_foreground,
            getString(R.string.notification_reply_label),
            pending
        )
            .addRemoteInput(remoteInput)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setAllowGeneratedReplies(true)
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
            PostedNotifications.notificationId(chatId),
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
