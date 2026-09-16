package com.telegramyou.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.telegramyou.app.settings.isDark
import androidx.compose.ui.Modifier
import com.telegramyou.app.navigation.TelegramYouNavHost
import com.telegramyou.app.ui.theme.TelegramYouTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as TelegramYouApp
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
                        appearance = app.appearance
                    )
                }
            }
        }
    }
}
