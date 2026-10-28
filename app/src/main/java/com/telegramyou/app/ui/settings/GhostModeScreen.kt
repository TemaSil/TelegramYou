package com.telegramyou.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.telegramyou.app.i18n.tr
import com.telegramyou.app.settings.GeekSettings
import com.telegramyou.app.ui.icons.Symbols

/**
 * Ghost mode (2.8): what this account does not tell the people it talks
 * to, as three switches. A screen of its own off Settings, beside Privacy —
 * the owner's call: it is a privacy setting people look for, not a
 * tinkerer's. Kept with the For geeks settings all the same, which is where
 * it began and where GhostMode reads it from.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GhostModeScreen(
    settings: GeekSettings,
    onBack: () -> Unit,
    onChange: ((GeekSettings) -> GeekSettings) -> Unit
) {
    Scaffold(
        containerColor = settingsBackground(),
        topBar = {
            TopAppBar(
                title = { Text(tr("Ghost mode", "Режим призрака")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Symbols.ArrowBack, contentDescription = tr("Back", "Назад"))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = settingsBackground())
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            Text(
                tr(
                    "Read and write without telling anyone. Telegram still marks a chat read once you send something in it.",
                    "Читайте и пишите незаметно. Telegram всё равно отметит чат прочитанным, когда вы в нём что-то отправите."
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
            )
            SettingsGroup {
                switch(
                    title = tr("Don't send “typing…”", "Не показывать «печатает»"),
                    summary = tr("Nobody sees you typing or recording", "Никто не видит, что вы пишете или записываете"),
                    checked = settings.ghostTyping
                ) { on -> onChange { it.copy(ghostTyping = on) } }
                switch(
                    title = tr("Don't mark as read", "Не отмечать прочитанным"),
                    summary = tr("Messages stay unread for their senders until you answer", "Сообщения остаются непрочитанными для отправителя, пока вы не ответите"),
                    checked = settings.ghostReading
                ) { on -> onChange { it.copy(ghostReading = on) } }
                switch(
                    title = tr("Stay offline", "Не показываться в сети"),
                    summary = tr("You are never shown online while you use the app", "Вас не видно в сети, пока вы в приложении"),
                    checked = settings.ghostOnline
                ) { on -> onChange { it.copy(ghostOnline = on) } }
                switch(
                    title = tr("Watch stories unseen", "Смотреть сторис незаметно"),
                    summary = tr(
                        "Always. Or for 15 minutes at a time, from the eye at the top of a story",
                        "Всегда. Или на 15 минут — глазом вверху сторис"
                    ),
                    checked = settings.ghostStories
                ) { on -> onChange { it.copy(ghostStories = on) } }
            }
        }
    }
}

/** Ghost mode's row in Settings: off, or which of the three are on. */
fun ghostModeSummary(settings: GeekSettings): String {
    val on = listOfNotNull(
        tr("typing", "печатает").takeIf { settings.ghostTyping },
        tr("read", "прочитано").takeIf { settings.ghostReading },
        tr("online", "в сети").takeIf { settings.ghostOnline },
        tr("story views", "просмотры сторис").takeIf { settings.ghostStories }
    )
    return if (on.isEmpty()) tr("Off", "Выключен") else tr("Hiding ", "Скрыто: ") + on.joinToString(", ")
}
