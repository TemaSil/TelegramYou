package com.telegramyou.app.ui.settings

import com.telegramyou.app.i18n.tr

import com.telegramyou.app.ui.icons.Symbols
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.telegramyou.app.settings.DoubleTapAction
import com.telegramyou.app.settings.GeekSettings
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.text.selection.SelectionContainer
import com.telegramyou.app.CrashLog
import androidx.compose.ui.platform.LocalContext

/**
 * Settings → For geeks: the small things a power user reaches for, each a
 * stock list item with a switch, and the one choice among several in
 * Material's radio-button dialog. Everything starts off.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeeksScreen(
    settings: GeekSettings,
    onBack: () -> Unit,
    onChange: ((GeekSettings) -> GeekSettings) -> Unit
) {
    var choosingDoubleTap by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    var crash by remember { mutableStateOf(CrashLog.read(context)) }
    var showingCrash by remember { mutableStateOf(false) }
    var playerLog by remember { mutableStateOf(com.telegramyou.app.music.PlayerLog.read(context)) }
    var showingPlayerLog by remember { mutableStateOf(false) }
    if (showingPlayerLog && playerLog != null) {
        // The newest at the top: the stop being asked about is the last one.
        val newestFirst = remember(playerLog) { playerLog.orEmpty().lines().filter { it.isNotBlank() }.reversed().joinToString("\n") }
        AlertDialog(
            onDismissRequest = { showingPlayerLog = false },
            title = { Text(tr("Player log", "Журнал плеера")) },
            text = {
                SelectionContainer {
                    Text(
                        newestFirst,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(ClipData.newPlainText(tr("TelegramYou player log", "Журнал плеера TelegramYou"), newestFirst))
                    showingPlayerLog = false
                }) { Text(tr("Copy", "Копировать")) }
            },
            dismissButton = {
                TextButton(onClick = {
                    com.telegramyou.app.music.PlayerLog.clear(context)
                    playerLog = null
                    showingPlayerLog = false
                }) { Text(tr("Clear", "Очистить")) }
            }
        )
    }
    if (showingCrash && crash != null) {
        AlertDialog(
            onDismissRequest = { showingCrash = false },
            title = { Text(tr("Last crash", "Последний сбой")) },
            text = {
                SelectionContainer {
                    Text(
                        crash.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(ClipData.newPlainText(tr("TelegramYou crash", "Сбой TelegramYou"), crash))
                    showingCrash = false
                }) { Text(tr("Copy", "Копировать")) }
            },
            dismissButton = {
                TextButton(onClick = {
                    CrashLog.clear(context)
                    crash = null
                    showingCrash = false
                }) { Text(tr("Clear", "Очистить")) }
            }
        )
    }
    if (choosingDoubleTap) {
        AlertDialog(
            onDismissRequest = { choosingDoubleTap = false },
            title = { Text(tr("Double tap a message", "Двойное касание сообщения")) },
            text = {
                Column {
                    DoubleTapAction.entries.forEach { action ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = settings.doubleTap == action,
                                    role = Role.RadioButton,
                                    onClick = {
                                        onChange { it.copy(doubleTap = action) }
                                        choosingDoubleTap = false
                                    }
                                )
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(selected = settings.doubleTap == action, onClick = null)
                            Text(action.label, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosingDoubleTap = false }) { Text(tr("Cancel", "Отмена")) } }
        )
    }

    Scaffold(
        containerColor = settingsBackground(),
        topBar = {
            TopAppBar(
                title = { Text(tr("For geeks", "Для гиков")) },
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
            SettingsGroup(tr("In chats", "В чатах")) {
                link(
                    title = tr("Double tap a message", "Двойное касание сообщения"),
                    summary = settings.doubleTap.label,
                    onClick = { choosingDoubleTap = true }
                )
                switch(
                    title = tr("Seconds in message times", "Секунды во времени сообщений"),
                    summary = tr("14:03:27 rather than 14:03", "14:03:27 вместо 14:03"),
                    checked = settings.showSeconds
                ) { on -> onChange { it.copy(showSeconds = on) } }
                switch(
                    title = tr("Message details", "Подробности сообщения"),
                    summary = tr("Its exact time and the ids of the message, its sender and the chat, in the menu", "Точное время и id сообщения, отправителя и чата в меню"),
                    checked = settings.messageDetails
                ) { on -> onChange { it.copy(messageDetails = on) } }
                switch(
                    title = tr("Save and copy media", "Сохранение и копирование медиа"),
                    summary = tr("Save to Downloads, and copy a photo, from a message's menu", "Сохранить в «Загрузки» и скопировать фото из меню сообщения"),
                    checked = settings.saveMedia
                ) { on -> onChange { it.copy(saveMedia = on) } }
                switch(
                    title = tr("Forward without quoting", "Пересылка без цитаты"),
                    summary = tr("Forwarded messages arrive as yours, without \"Forwarded from\"", "Пересланное приходит от вас, без «Переслано от»"),
                    checked = settings.forwardWithoutQuote
                ) { on -> onChange { it.copy(forwardWithoutQuote = on) } }
                switch(
                    title = tr("More in a message's menu", "Больше в меню сообщения"),
                    summary = tr("Repeat it, save it to Saved Messages, delete its downloaded file", "Повторить, сохранить в Избранное, удалить скачанный файл"),
                    checked = settings.messageExtras
                ) { on -> onChange { it.copy(messageExtras = on) } }
                switch(
                    title = tr("Translate chats", "Перевод чатов"),
                    summary = tr("A chat's info can show what comes in, in your phone's language", "В инфо чата можно включить перевод входящих на язык телефона"),
                    checked = settings.autoTranslate
                ) { on -> onChange { it.copy(autoTranslate = on) } }
                switch(
                    title = tr("Hide blocked people in groups", "Скрывать заблокированных в группах"),
                    summary = tr("Their messages are left out of groups you share", "Их сообщения не видны в общих группах"),
                    checked = settings.hideBlockedInGroups
                ) { on -> onChange { it.copy(hideBlockedInGroups = on) } }
                switch(
                    title = tr("Ask before sending a recording", "Спрашивать перед отправкой записи"),
                    summary = tr("A voice or video message waits for Send or Delete when you let go", "Голосовое или видеосообщение ждёт «Отправить» или «Удалить», когда вы отпустите кнопку"),
                    checked = settings.confirmRecordings
                ) { on -> onChange { it.copy(confirmRecordings = on) } }
            }

            SettingsGroup(tr("Chat list and search", "Список чатов и поиск")) {
                switch(
                    title = tr("Hide stories", "Скрыть истории"),
                    summary = tr("No stories above the folders", "Без историй над папками"),
                    checked = settings.hideStories
                ) { on -> onChange { it.copy(hideStories = on) } }
                switch(
                    title = tr("Hide the All tab", "Скрыть вкладку «Все»"),
                    summary = tr("Start on your first folder, when you have folders", "Начинать с первой папки, если папки есть"),
                    checked = settings.hideAllChatsTab
                ) { on -> onChange { it.copy(hideAllChatsTab = on) } }
                switch(
                    title = tr("Open search without the keyboard", "Открывать поиск без клавиатуры"),
                    summary = tr("Show recent chats and people first; tap the field to type", "Сначала недавние чаты и люди; коснитесь поля, чтобы печатать"),
                    checked = settings.searchWithoutKeyboard
                ) { on -> onChange { it.copy(searchWithoutKeyboard = on) } }
                switch(
                    title = tr("Pull down for the archive", "Архив жестом вниз"),
                    summary = tr("Pull the chat list down past its top to open the archive", "Потяните список чатов вниз за верх, чтобы открыть архив"),
                    checked = settings.openArchiveOnPull
                ) { on -> onChange { it.copy(openArchiveOnPull = on) } }
            }

            SettingsGroup(tr("Notifications", "Уведомления")) {
                switch(
                    title = tr("Silence people not in contacts", "Без звука от незнакомых"),
                    summary = tr("Their messages arrive without sound or vibration", "Их сообщения приходят без звука и вибрации"),
                    checked = settings.silenceNonContacts
                ) { on -> onChange { it.copy(silenceNonContacts = on) } }
            }

            // What this account does not tell anyone (2.8) — the forks'
            // ghost mode, as three switches rather than one.
            SettingsGroup(tr("Ghost mode", "Режим призрака")) {
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
            }

            SettingsGroup(tr("Experiments", "Эксперименты")) {
                switch(
                    title = tr("Music library", "Музыкальная библиотека"),
                    summary = tr("Every chat's music as albums, artists and playlists, in the chat list's menu", "Музыка из всех чатов — альбомы, исполнители и плейлисты в меню списка чатов"),
                    checked = settings.musicLibrary
                ) { on -> onChange { it.copy(musicLibrary = on) } }
                switch(
                    title = tr("Members in a group's header (beta)", "Участники в шапке группы (бета)"),
                    summary = tr("A group or channel shows the faces of the people in it rather than its own picture", "Группа или канал показывает лица участников вместо своей картинки"),
                    checked = settings.groupFaces
                ) { on -> onChange { it.copy(groupFaces = on) } }
            }

            SettingsGroup(tr("Connection", "Соединение")) {
                switch(
                    title = tr("Prefer IPv6", "Предпочитать IPv6"),
                    summary = tr("Reach Telegram over IPv6 where the network offers both", "Подключаться к Telegram по IPv6, если сеть даёт оба"),
                    checked = settings.preferIpv6
                ) { on -> onChange { it.copy(preferIpv6 = on) } }
            }

            // The last crash, when there has been one: to read and copy into
            // a report. See CrashLog.
            // And the music player's log (1.7), for music that stops or
            // skips by itself: what it did, and the reason the system gave.
            if (crash != null || playerLog != null) {
                SettingsGroup(tr("Diagnostics", "Диагностика")) {
                    if (crash != null) {
                        link(
                            title = tr("Last crash", "Последний сбой"),
                            summary = crash!!.lineSequence().drop(2).firstOrNull().orEmpty().ifBlank { tr("Tap to see it", "Нажмите, чтобы посмотреть") },
                            onClick = { showingCrash = true }
                        )
                    }
                    if (playerLog != null) {
                        link(
                            title = tr("Player log", "Журнал плеера"),
                            summary = tr("Why music last stopped or skipped", "Почему музыка в последний раз остановилась или перескочила"),
                            onClick = { showingPlayerLog = true }
                        )
                    }
                }
            }
        }
    }
}
