package com.telegramyou.app

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
import kotlinx.coroutines.MainScope
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
     * The demo, entered from the login screen of the live client by tapping
     * its mark ten times — see [setDemoMode]. False in the demo build, which
     * has nothing else to go back to.
     */
    var isSwitchedToDemo: Boolean = false
        private set

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        appearance = AppearanceStore(this)

        isSwitchedToDemo = !BuildConfig.USE_DEMO_CLIENT &&
            getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_DEMO, false)
        val client = if (BuildConfig.USE_DEMO_CLIENT) {
            DemoTelegramClient()
        } else if (isSwitchedToDemo) {
            DemoTelegramClient(signedIn = true)
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
        telegramRepository = TelegramRepository(client)
        telegramRepository.start()

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
        if (BuildConfig.USE_DEMO_CLIENT) return
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
     * The sync channel is IMPORTANCE_LOW: it exists because Android requires
     * a foreground service to show something, and nobody wants to be told
     * their messenger is connected. Messages are IMPORTANCE_HIGH, since a
     * message is the thing a person installed this for.
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
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
        }
        val messages = NotificationChannel(
            CHANNEL_MESSAGES,
            getString(R.string.notification_channel_messages_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.notification_channel_messages_desc)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(sync)
        manager.createNotificationChannel(messages)
    }

    companion object {
        private const val PREFS = "backend"
        private const val KEY_DEMO = "demo"
        const val CHANNEL_SYNC = "telegram_sync"
        const val CHANNEL_MESSAGES = "telegram_messages"
    }
}
