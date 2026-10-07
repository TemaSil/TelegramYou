package com.telegramyou.app.telegram

import com.telegramyou.app.telegram.model.ChatPreview
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.app.ServiceCompat
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import com.telegramyou.app.telegram.model.downloadNotificationText

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

    /** The one watcher of downloads, for their notification. */
    private var downloads: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * A foreground service's time is up (Android 15's cap on data sync).
     * Not expected with the type chosen in onStartCommand, but the platform's
     * answer to a service that ignores this is to crash the app, so it stops.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Remote messaging from Android 14, where the type exists: a
        // messenger's connection is what it is for, and it has no time limit.
        // Data sync, the only fitting type before it, is capped at six hours
        // a day from Android 15, and a service still running at the cap is a
        // crash — after hours of use, most likely with music keeping the app
        // alive in the background.
        ServiceCompat.startForeground(
            this,
            ONGOING_NOTIFICATION_ID,
            ongoingNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
        )
        // Once per service, not once per start. The activity starts this in
        // onCreate, so every rotation or theme change is another start — and
        // each used to add another collector, until one message was being
        // posted as many times as the screen had been turned.
        if (arrivals == null) arrivals = observeArrivals()
        if (downloads == null) downloads = observeDownloads()
        if (intent?.action == ACTION_PAUSE_DOWNLOADS) {
            scope.launch { (application as TelegramYouApp).telegramRepository.setAllDownloadsPaused(true) }
        }
        // START_STICKY so a process killed for memory comes back: a messenger
        // that stops delivering after the first low-memory moment is worse
        // than one that never claimed to.
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        running = true
    }

    override fun onDestroy() {
        running = false
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
                    isChatMuted = chat?.isMuted == true,
                    showPreview = chat?.notifications?.showPreview ?: true,
                    sound = (chat?.notifications?.sound ?: true) && !silencedStranger(app, chat, message.senderId),
                    // A chat not in the list is one being looked at without
                    // having joined — a channel, as a rule — so it counts.
                    isGroupOrChannel = chat == null || chat.isGroup || chat.isChannel
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

    /**
     * Settings → For geeks → Silence people not in contacts (1.8, after
     * Nekogram): a private message from someone this account has not saved
     * still reaches the shade, without a sound. Bots are left as they are —
     * nobody adds a bot to contacts, and one that was started was asked for.
     */
    private suspend fun silencedStranger(app: TelegramYouApp, chat: ChatPreview?, senderId: Long?): Boolean {
        if (!app.geeks.settings.value.silenceNonContacts) return false
        if (chat == null || chat.isGroup || chat.isChannel) return false
        val sender = senderId ?: return false
        val person = runCatching { app.telegramRepository.person(sender) }.getOrNull() ?: return false
        return !person.isContact && !person.isBot
    }

    /**
     * A download's progress in the shade while it runs — the files a person
     * asked for, never the app's own thumbnails — with Pause all on it, and
     * gone when nothing is downloading. This service already keeps the
     * process alive, which is what keeps a download going with the screen
     * off; the notification is so that it is not going on unseen.
     */
    private fun observeDownloads(): Job {
        val repository = (application as TelegramYouApp).telegramRepository
        return scope.launch {
            combine(repository.listDownloads, repository.fileTransfers) { ids, transfers ->
                if (ids.isEmpty()) {
                    null
                } else {
                    val running = ids.mapNotNull { transfers[it] }.filter { !it.isUpload }
                    val total = running.sumOf { it.totalBytes.coerceAtLeast(0) }
                    val done = running.sumOf { it.doneBytes.coerceAtLeast(0) }
                    // Whole percent, so the shade is not re-posted for every
                    // few kilobytes — Android drops updates that come faster.
                    val percent = if (total > 0) (done * 100 / total).toInt() else -1
                    val text = if (running.isEmpty()) {
                        if (ids.size == 1) "Downloading 1 file" else "Downloading ${ids.size} files"
                    } else {
                        downloadNotificationText(running)
                    }
                    text to percent
                }
            }.distinctUntilChanged().collect { shown ->
                val manager = NotificationManagerCompat.from(this@TelegramForegroundService)
                if (shown == null) {
                    manager.cancel(DOWNLOADS_NOTIFICATION_ID)
                } else if (canPost()) {
                    runCatching { manager.notify(DOWNLOADS_NOTIFICATION_ID, downloadsNotification(shown.first, shown.second)) }
                }
            }
        }
    }

    private fun downloadsNotification(text: String, percent: Int): Notification {
        val open = PendingIntent.getActivity(
            this,
            DOWNLOADS_NOTIFICATION_ID,
            Intent(this, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_OPEN_DOWNLOADS, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val pause = PendingIntent.getService(
            this,
            DOWNLOADS_NOTIFICATION_ID,
            Intent(this, TelegramForegroundService::class.java).setAction(ACTION_PAUSE_DOWNLOADS),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, TelegramYouApp.CHANNEL_DOWNLOADS)
            .setContentTitle("Downloads")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, percent.coerceAtLeast(0), percent < 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(open)
            .addAction(R.drawable.ic_launcher_foreground, "Pause all", pause)
            .build()
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
            // With previews off for the chat, the shade says that something
            // came and from where, and not what it says — which is the whole
            // point of the setting on a locked phone.
            val text = if (message.showPreview) message.text else getString(R.string.notification_hidden_preview)
            style.addMessage(text, message.timestampMillis, sender)
        }
        return NotificationCompat.Builder(this, TelegramYouApp.CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setStyle(style)
            .setAutoCancel(true)
            .setContentIntent(openChatIntent(latest.chatId))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            // The chat's own "no sound": still posted, still in the shade,
            // just not heard.
            .setSilent(!latest.sound)
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
            // Below Android 8, where there are no channels: as far down as
            // a notification goes. See TelegramYouApp's channels.
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setShowWhen(false)
            .build()
    }

    companion object {
        const val EXTRA_CHAT_ID = "com.telegramyou.app.EXTRA_CHAT_ID"
        /** On the downloads notification's tap: open the Downloads screen. */
        const val EXTRA_OPEN_DOWNLOADS = "com.telegramyou.app.EXTRA_OPEN_DOWNLOADS"
        private const val ACTION_PAUSE_DOWNLOADS = "com.telegramyou.app.PAUSE_DOWNLOADS"
        private const val DOWNLOADS_NOTIFICATION_ID = 43

        private const val ONGOING_NOTIFICATION_ID = 42
        private const val MAX_LINES_PER_CHAT = 6

        /** Whether the service is up, so opening the app does not start it again. */
        @Volatile private var running = false

        fun start(context: android.content.Context) {
            // Every start posts its notification again, and the activity
            // starts this each time it is made: a notice swiped away came
            // straight back on the next open. Once is enough.
            if (running) return
            context.startForegroundService(Intent(context, TelegramForegroundService::class.java))
        }

        fun stop(context: android.content.Context) {
            context.stopService(Intent(context, TelegramForegroundService::class.java))
        }
    }
}
