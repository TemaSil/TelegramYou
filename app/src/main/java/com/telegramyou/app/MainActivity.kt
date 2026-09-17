package com.telegramyou.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.telegramyou.app.settings.isDark
import androidx.compose.ui.Modifier
import android.content.Intent
import com.telegramyou.app.navigation.TelegramYouNavHost
import com.telegramyou.app.telegram.AppVisibility
import com.telegramyou.app.telegram.TelegramForegroundService
import com.telegramyou.app.ui.theme.TelegramYouTheme

class MainActivity : ComponentActivity() {

    /**
     * The chat a notification asked for, consumed once.
     *
     * Held here rather than passed straight down because onNewIntent can
     * deliver one while the composition is already running — a second
     * notification tapped without leaving the app.
     */
    private var pendingChatId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as TelegramYouApp
        pendingChatId = intent.chatIdExtra()
        // Started from the activity rather than from Application.onCreate:
        // a foreground service begun before anything is on screen is a
        // notification for an app the person has not opened.
        TelegramForegroundService.start(this)
        setContent {
            // Collected rather than read: flipping a switch in settings has to
            // change the colours behind it, not on the next launch.
            val appearance by app.appearance.settings.collectAsStateWithLifecycle()
            TelegramYouTheme(
                darkTheme = isDark(appearance.theme, isSystemInDarkTheme()),
                dynamicColor = appearance.dynamicColor
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TelegramYouNavHost(
                        repository = app.telegramRepository,
                        appearance = app.appearance,
                        openChatId = pendingChatId,
                        onChatOpened = { pendingChatId = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // setIntent so a recreation after this — a rotation, say — does not
        // reopen the chat from the intent this activity was first launched
        // with.
        setIntent(intent)
        pendingChatId = intent.chatIdExtra()
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.isInForeground = true
    }

    override fun onStop() {
        // The notification service reads this to decide whether the chat on
        // screen is being read. Once the activity has stopped it is not,
        // whatever is still composed.
        AppVisibility.isInForeground = false
        super.onStop()
    }
}

private fun Intent.chatIdExtra(): Long? =
    getLongExtra(TelegramForegroundService.EXTRA_CHAT_ID, -1L).takeIf { it != -1L }
