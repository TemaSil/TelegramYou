package com.telegramyou.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.demo.DemoTelegramClient
import com.telegramyou.app.telegram.tdlib.TdLibTelegramClient

class TelegramYouApp : Application() {
    lateinit var telegramRepository: TelegramRepository
        private set

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val client = if (BuildConfig.USE_DEMO_CLIENT) {
            DemoTelegramClient()
        } else {
            TdLibTelegramClient(
                context = this,
                apiId = BuildConfig.TELEGRAM_API_ID,
                apiHash = BuildConfig.TELEGRAM_API_HASH
            )
        }
        telegramRepository = TelegramRepository(client)
        telegramRepository.start()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_SYNC,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_SYNC = "telegram_sync"
    }
}
