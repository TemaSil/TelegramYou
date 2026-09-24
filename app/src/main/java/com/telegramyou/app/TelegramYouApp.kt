package com.telegramyou.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.telegramyou.app.settings.AppearanceStore
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.unmaskApiHash
import com.telegramyou.app.telegram.demo.DemoTelegramClient
import com.telegramyou.app.telegram.tdlib.TdLibTelegramClient

class TelegramYouApp : Application() {
    lateinit var telegramRepository: TelegramRepository
        private set

    /** Read once here so the activity does not touch disk on every recreate. */
    lateinit var appearance: AppearanceStore
        private set

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        appearance = AppearanceStore(this)

        val client = if (BuildConfig.USE_DEMO_CLIENT) {
            DemoTelegramClient()
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
        const val CHANNEL_SYNC = "telegram_sync"
        const val CHANNEL_MESSAGES = "telegram_messages"
    }
}
