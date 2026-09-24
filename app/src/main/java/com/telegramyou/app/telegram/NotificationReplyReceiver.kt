package com.telegramyou.app.telegram

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.telegramyou.app.TelegramYouApp
import com.telegramyou.app.notifications.PostedNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Sends what somebody typed into a notification, without opening the app.
 *
 * A receiver rather than a service or an activity, because that is what the
 * reply action can start without putting anything on screen — which is the
 * entire point of replying from the shade.
 *
 * `goAsync` is what makes the send legal. A receiver is killed as soon as
 * `onReceive` returns, and sending a message is a suspend call over a socket;
 * without it the process is free to die mid-flight, and the reply is lost
 * silently. The result is finished on the coroutine that did the work.
 */
class NotificationReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val chatId = intent.getLongExtra(EXTRA_CHAT_ID, -1L).takeIf { it != -1L } ?: return
        val text = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(KEY_REPLY)
            ?.toString()
            ?.trim()
            .orEmpty()
        // An empty reply is a person who changed their mind, not a message.
        // The notification stays up so the shade does not lose its place.
        if (text.isBlank()) return

        val app = context.applicationContext as TelegramYouApp
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // Bounded: a receiver that holds goAsync past its window is
                // killed, and a client still signing in after a cold start
                // could keep this waiting indefinitely.
                withTimeout(REPLY_TIMEOUT_MS) {
                    app.telegramRepository.sendMessage(chatId = chatId, text = text)
                }
                // Taken down only once the message is away. A notification
                // that vanishes on tap and a message that never sent is the
                // pair of events nobody can tell apart afterwards.
                NotificationManagerCompat.from(context)
                    .cancel(PostedNotifications.notificationId(chatId))
                // Answered means read: the next message from this chat opens
                // a fresh entry rather than re-listing what was replied to.
                PostedNotifications.clear(chatId)
            } catch (e: Exception) {
                // Refused, or timed out. The notification stays, which is
                // the one sign left that the reply did not go — an exception
                // out of here would have taken the whole process down, in
                // the background, with nothing on screen to say why.
                Log.w(TAG, "reply to $chatId failed: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_CHAT_ID = "com.telegramyou.app.REPLY_CHAT_ID"

        /** The key the typed text arrives under, shared with the action. */
        const val KEY_REPLY = "com.telegramyou.app.KEY_REPLY"

        private const val TAG = "NotificationReply"

        /** Inside the ten seconds a receiver gets after goAsync. */
        private const val REPLY_TIMEOUT_MS = 8_000L
    }
}
