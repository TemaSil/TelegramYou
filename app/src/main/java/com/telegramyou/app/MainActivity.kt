package com.telegramyou.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.telegramyou.app.navigation.TelegramYouNavHost
import com.telegramyou.app.ui.theme.TelegramYouTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as TelegramYouApp
        setContent {
            TelegramYouTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TelegramYouNavHost(repository = app.telegramRepository)
                }
            }
        }
    }
}
