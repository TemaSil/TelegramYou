package com.telegramyou.app

import com.telegramyou.app.settings.AppLockStore
import android.app.Application
import android.content.Intent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.telegramyou.app.settings.AppearanceStore
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.AuthState
import com.telegramyou.app.telegram.unmaskApiHash
import com.telegramyou.app.telegram.demo.DemoTelegramClient
import com.telegramyou.app.telegram.tdlib.TdLibTelegramClient
import com.telegramyou.app.update.AppUpdates
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.telegramyou.app.settings.GeekStore
import com.telegramyou.app.settings.QueryHistoryStore
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TelegramYouApp : Application() {
    lateinit var telegramRepository: TelegramRepository
        private set

    /** Read once here so the activity does not touch disk on every recreate. */
    lateinit var appearance: AppearanceStore
        private set

    /**
     * The demo backend, where that is what runs — reached only by the UI
     * test, which runs in this process and asks it to speak (see
     * DemoTelegramClient.speakNow). Null in a live build.
     */
    var demoClient: DemoTelegramClient? = null
        private set

    /** Settings → For geeks. */
    lateinit var geeks: GeekStore
        private set

    /** Settings → Privacy and security → App lock; see AppLockStore. */
    lateinit var appLock: AppLockStore
        private set

    /** What was typed into search, newest first; see QueryHistory. */
    lateinit var queryHistory: QueryHistoryStore
        private set

    /** Checking for and installing a newer build; see AppUpdates. */
    lateinit var updates: AppUpdates
        private set

    /** Voice messages, one for the app so they outlive a chat; see VoicePlayback. */
    val voice: com.telegramyou.app.music.VoicePlayback by lazy {
        com.telegramyou.app.music.VoicePlayback(telegramRepository, getSharedPreferences("voice", MODE_PRIVATE))
    }

    /** The music player, one for the app; see MusicPlayer. */
    val music: com.telegramyou.app.music.MusicPlayer by lazy {
        com.telegramyou.app.music.MusicPlayer(this, telegramRepository)
    }

    /**
     * The demo, entered from the login screen of the live client by tapping
     * its mark ten times — see [setDemoMode]. False in the demo build, which
     * has nothing else to go back to.
     */
    var isSwitchedToDemo: Boolean = false
        private set

    override fun onCreate() {
        super.onCreate()
        // First, so a crash anywhere after it is kept; see CrashLog.
        CrashLog.install(this)
        com.telegramyou.app.music.PlayerLog.install(this)
        com.telegramyou.app.settings.AutoTranslate.install(this)
        createNotificationChannels()
        com.telegramyou.app.music.AudioFocus.init(this)
        appearance = AppearanceStore(this)
        geeks = GeekStore(this)
        appLock = AppLockStore(this)
        queryHistory = QueryHistoryStore(this)
        updates = AppUpdates(this)
        // Quietly, once a launch: nothing is said unless there is a newer
        // build, and then the Settings tab says so.
        updates.check(quiet = true)

        // Every way into the demo sits behind DEMO_ALLOWED, a constant false in
        // a release build, so R8 can see the demo is unreachable there and
        // drop it — the backend, its seeded chats, its media — whole.
        isSwitchedToDemo = BuildConfig.DEMO_ALLOWED && !BuildConfig.USE_DEMO_CLIENT &&
            getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_DEMO, false)
        val client = if (BuildConfig.DEMO_ALLOWED && BuildConfig.USE_DEMO_CLIENT) {
            DemoTelegramClient(context = this)
        } else if (BuildConfig.DEMO_ALLOWED && isSwitchedToDemo) {
            DemoTelegramClient(signedIn = true, context = this)
        } else {
            TdLibTelegramClient(
                context = this,
                apiId = BuildConfig.TELEGRAM_API_ID,
                apiHash = unmaskApiHash(
                    BuildConfig.TELEGRAM_API_HASH_MASKED,
                    BuildConfig.TELEGRAM_API_HASH_MASK
                )
            )
        }
        demoClient = if (BuildConfig.DEMO_ALLOWED) client as? DemoTelegramClient else null
        telegramRepository = TelegramRepository(client)
        telegramRepository.start()
        // The home screen's widgets, redrawn as what they show changes (2.1).
        // After the repository, which they read at once: on the main thread's
        // immediate dispatcher the first collection runs inside this call.
        com.telegramyou.app.widgets.WidgetUpdates.start(this)
        // The one geek setting that is TDLib's rather than the screens': sent
        // now and again whenever it is switched.
        MainScope().launch {
            geeks.settings.map { it.preferIpv6 }.distinctUntilChanged().collect {
                telegramRepository.setPreferIpv6(it)
            }
        }

        if (isSwitchedToDemo) {
            // Signing out of the demo is leaving it. Otherwise it would land
            // on a login screen that only knows the code 12345, with the real
            // account one secret gesture away.
            MainScope().launch {
                telegramRepository.observeAuth()
                    .drop(1)
                    .first { it.state == AuthState.WaitPhoneNumber }
                setDemoMode(false)
            }
        }
    }

    /**
     * Into or out of the demo, by starting the app again on the other backend.
     *
     * A restart rather than swapping the client underneath: every state
     * holder, the notification service and the reply receiver hold the
     * repository they were given, and a process that starts fresh is the one
     * way to be sure none of them is left talking to the old one. The live
     * account is not touched — TDLib's database stays where it is — so
     * coming back out of the demo is still signed in.
     */
    fun setDemoMode(enabled: Boolean) {
        if (!BuildConfig.DEMO_ALLOWED || BuildConfig.USE_DEMO_CLIENT) return
        // commit, not apply: the process ends two lines down.
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_DEMO, enabled).commit()
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(launch)
        Runtime.getRuntime().exit(0)
    }

    /**
     * Two channels, because they are two different promises.
     *
     * The connection channel is IMPORTANCE_MIN: it exists because Android
     * requires a foreground service to show something, and nobody wants to
     * be told their messenger is connected — the owner, 30 September 2026:
     * "no need to see it in the shade". Minimum importance keeps it out of
     * the status bar and folded at the foot of the shade, where it can be
     * swiped away. It cannot go altogether: this client has no Google push,
     * so the service is what delivers messages while the app is closed.
     * Messages are IMPORTANCE_HIGH, since a message is the thing a person
     * installed this for.
     *
     * Separate channels also hand the settings app the right knobs: someone
     * can silence the connection notice without silencing their messages,
     * which one shared channel would make impossible.
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val sync = NotificationChannel(
            CHANNEL_SYNC,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }
        // Messages in three channels, one per kind of chat (2.1), so that
        // Android's own settings for the app — sound, vibration, what comes
        // through Do Not Disturb — are set per kind, as Settings →
        // Notifications sets the rest. Private chats keep the channel all
        // messages had before, and with it whatever was set there.
        val messagesGroup = android.app.NotificationChannelGroup(
            CHANNEL_GROUP_MESSAGES,
            getString(R.string.notification_channel_messages_name)
        )
        val messages = com.telegramyou.app.notifications.NotificationScope.entries.map { scope ->
            NotificationChannel(messagesChannel(scope), scope.label, NotificationManager.IMPORTANCE_HIGH).apply {
                description = scope.summary
                group = CHANNEL_GROUP_MESSAGES
            }
        }
        // Low, like sync: a bar filling up is something to glance at, not to
        // be interrupted by.
        val downloads = NotificationChannel(
            CHANNEL_DOWNLOADS,
            getString(R.string.notification_channel_downloads_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_downloads_desc)
        }
        val manager = getSystemService(NotificationManager::class.java)
        // The channel it replaces, which was LOW: a channel's importance
        // cannot be lowered by the app once made, so it is a new channel.
        manager.deleteNotificationChannel(OLD_CHANNEL_SYNC)
        manager.createNotificationChannelGroup(messagesGroup)
        manager.createNotificationChannel(sync)
        messages.forEach(manager::createNotificationChannel)
        manager.createNotificationChannel(downloads)
    }

    companion object {
        private const val PREFS = "backend"
        private const val KEY_DEMO = "demo"
        const val CHANNEL_SYNC = "telegram_connection"
        private const val OLD_CHANNEL_SYNC = "telegram_sync"
        const val CHANNEL_MESSAGES = "telegram_messages"
        private const val CHANNEL_GROUP_MESSAGES = "telegram_messages_group"

        /** The channel a kind of chat's messages go to (2.1); private chats keep the old one. */
        fun messagesChannel(scope: com.telegramyou.app.notifications.NotificationScope): String = when (scope) {
            com.telegramyou.app.notifications.NotificationScope.PrivateChats -> CHANNEL_MESSAGES
            com.telegramyou.app.notifications.NotificationScope.Groups -> "telegram_messages_groups"
            com.telegramyou.app.notifications.NotificationScope.Channels -> "telegram_messages_channels"
        }
        const val CHANNEL_DOWNLOADS = "telegram_downloads"
    }
}
