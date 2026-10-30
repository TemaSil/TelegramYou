package com.telegramyou.app.telegram.calls

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.telegramyou.app.MainActivity
import com.telegramyou.app.R
import com.telegramyou.app.TelegramYouApp
import com.telegramyou.app.i18n.tr
import com.telegramyou.app.telegram.AppVisibility
import com.telegramyou.app.telegram.model.CallInfo
import com.telegramyou.app.telegram.model.CallPhase
import com.telegramyou.app.telegram.model.callStatusLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

/**
 * A call outside the app's own screen (2.9), the way Android shows a phone
 * call: a ringing call as a call notification — Decline and Answer, and the
 * whole screen when the phone is locked or the app is shut — and a call
 * going on as the ongoing call notification, with its timer in the status
 * bar's chip and a Hang up, kept by a foreground service that holds the
 * microphone while the app is in the background.
 *
 * With the app on screen a ringing call is the call screen itself, and the
 * ring is played here rather than by a notification over it.
 */
object CallNotifier {
    private const val TAG = "CallNotifier"
    private const val ID_INCOMING = 41_001
    const val ID_ONGOING = 41_002
    const val ACTION_ANSWER = "com.telegramyou.app.ANSWER_CALL"
    const val ACTION_END = "com.telegramyou.app.END_CALL"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ringtone: Ringtone? = null
    @Volatile var current: CallInfo? = null
        private set

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun install(app: TelegramYouApp) {
        scope.launch {
            app.accounts.active.flatMapLatest { it.call }.collect { call -> show(app, call) }
        }
    }

    /** The last call there was, for a service asked to start just as it ended. */
    @Volatile var last: CallInfo? = null
        private set

    private fun show(context: Context, call: CallInfo?) {
        current = call
        if (call != null) last = call
        val manager = context.getSystemService(NotificationManager::class.java)
        val ringing = call?.phase == CallPhase.Incoming
        if (ringing && AppVisibility.isInForeground) ring(context) else silence()
        if (ringing && !AppVisibility.isInForeground) {
            runCatching { manager.notify(ID_INCOMING, incoming(context, call!!)) }
                .onFailure { Log.w(TAG, "incoming: ${it.message}") }
        } else {
            manager.cancel(ID_INCOMING)
        }
        val going = call != null && !ringing && call.phase != CallPhase.Ended
        // Only with the microphone allowed: the service is the microphone's,
        // and Android refuses it otherwise.
        val microphone = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (going && microphone) {
            CallService.update(context)
        } else {
            CallService.stop(context)
        }
    }

    private fun ring(context: Context) {
        if (ringtone?.isPlaying == true) return
        ringtone = RingtoneManager.getRingtone(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
            play()
        }
    }

    private fun silence() {
        ringtone?.stop()
        ringtone = null
    }

    private fun person(call: CallInfo): Person {
        val icon = call.photoPath?.let { path -> BitmapFactory.decodeFile(path)?.let(IconCompat::createWithBitmap) }
        return Person.Builder().setName(call.name).setIcon(icon).setImportant(true).build()
    }

    private fun openApp(context: Context, action: String? = null): PendingIntent =
        PendingIntent.getActivity(
            context,
            if (action == null) 0 else 1,
            Intent(context, MainActivity::class.java)
                .setAction(action ?: Intent.ACTION_MAIN)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun endCall(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            2,
            Intent(context, CallActionReceiver::class.java).setAction(ACTION_END),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun incoming(context: Context, call: CallInfo): Notification =
        NotificationCompat.Builder(context, TelegramYouApp.CHANNEL_CALLS_INCOMING)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentText(callStatusLabel(call.phase, 0, isVideo = call.isVideo))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setContentIntent(openApp(context))
            // The whole screen, over the lock screen too, as a phone call has.
            .setFullScreenIntent(openApp(context), true)
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(person(call), endCall(context), openApp(context, ACTION_ANSWER)))
            .build()

    fun ongoing(context: Context, call: CallInfo): Notification =
        NotificationCompat.Builder(context, TelegramYouApp.CHANNEL_CALLS_ONGOING)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentText(
                if (call.phase == CallPhase.Active) tr("Ongoing call", "Идёт звонок")
                else callStatusLabel(call.phase, 0, isVideo = call.isVideo)
            )
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(context))
            .apply {
                call.connectedAt?.let { setWhen(it).setUsesChronometer(true).setShowWhen(true) }
            }
            .setStyle(NotificationCompat.CallStyle.forOngoingCall(person(call), endCall(context)))
            .build()
}

/** Decline and Hang up, from the notification. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != CallNotifier.ACTION_END) return
        val app = context.applicationContext as TelegramYouApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.telegramRepository.endCall()
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * Keeps a call going while the app is out of sight: Android lets the
 * microphone be used from the background only by a foreground service
 * started while the app was in front, which a call always is — it is rung
 * or answered from the app.
 */
class CallService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The last call shown, even one just over: a service started in the
        // foreground has to say so before it may stop.
        val call = CallNotifier.current ?: CallNotifier.last
        try {
            if (call != null) {
                ServiceCompat.startForeground(
                    this,
                    CallNotifier.ID_ONGOING,
                    CallNotifier.ongoing(this, call),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
                )
            }
        } catch (e: Exception) {
            // Refused from the background; the call goes on while the app is up.
            Log.w("CallService", "startForeground: ${e.message}")
        }
        val over = CallNotifier.current?.phase.let { it == null || it == CallPhase.Ended || it == CallPhase.Incoming }
        if (over) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    companion object {
        fun update(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, CallService::class.java))
            } catch (e: Exception) {
                Log.w("CallService", "start: ${e.message}")
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CallService::class.java))
        }
    }
}
