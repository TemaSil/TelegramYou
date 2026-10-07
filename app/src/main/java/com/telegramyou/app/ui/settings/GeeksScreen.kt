package com.telegramyou.app.ui.settings

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
            title = { Text("Player log") },
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
                    clipboard?.setPrimaryClip(ClipData.newPlainText("TelegramYou player log", newestFirst))
                    showingPlayerLog = false
                }) { Text("Copy") }
            },
            dismissButton = {
                TextButton(onClick = {
                    com.telegramyou.app.music.PlayerLog.clear(context)
                    playerLog = null
                    showingPlayerLog = false
                }) { Text("Clear") }
            }
        )
    }
    if (showingCrash && crash != null) {
        AlertDialog(
            onDismissRequest = { showingCrash = false },
            title = { Text("Last crash") },
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
                    clipboard?.setPrimaryClip(ClipData.newPlainText("TelegramYou crash", crash))
                    showingCrash = false
                }) { Text("Copy") }
            },
            dismissButton = {
                TextButton(onClick = {
                    CrashLog.clear(context)
                    crash = null
                    showingCrash = false
                }) { Text("Clear") }
            }
        )
    }
    if (choosingDoubleTap) {
        AlertDialog(
            onDismissRequest = { choosingDoubleTap = false },
            title = { Text("Double tap a message") },
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
            dismissButton = { TextButton(onClick = { choosingDoubleTap = false }) { Text("Cancel") } }
        )
    }

    Scaffold(
        containerColor = settingsBackground(),
        topBar = {
            TopAppBar(
                title = { Text("For geeks") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Symbols.ArrowBack, contentDescription = "Back")
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
            SettingsGroup("In chats") {
                link(
                    title = "Double tap a message",
                    summary = settings.doubleTap.label,
                    onClick = { choosingDoubleTap = true }
                )
                switch(
                    title = "Seconds in message times",
                    summary = "14:03:27 rather than 14:03",
                    checked = settings.showSeconds
                ) { on -> onChange { it.copy(showSeconds = on) } }
                switch(
                    title = "Message details",
                    summary = "Its exact time and the ids of the message, its sender and the chat, in the menu",
                    checked = settings.messageDetails
                ) { on -> onChange { it.copy(messageDetails = on) } }
                switch(
                    title = "Save and copy media",
                    summary = "Save to Downloads, and copy a photo, from a message's menu",
                    checked = settings.saveMedia
                ) { on -> onChange { it.copy(saveMedia = on) } }
                switch(
                    title = "Forward without quoting",
                    summary = "Forwarded messages arrive as yours, without \"Forwarded from\"",
                    checked = settings.forwardWithoutQuote
                ) { on -> onChange { it.copy(forwardWithoutQuote = on) } }
                switch(
                    title = "More in a message's menu",
                    summary = "Repeat it, save it to Saved Messages, delete its downloaded file",
                    checked = settings.messageExtras
                ) { on -> onChange { it.copy(messageExtras = on) } }
                switch(
                    title = "Translate chats",
                    summary = "A chat's info can show what comes in, in your phone's language",
                    checked = settings.autoTranslate
                ) { on -> onChange { it.copy(autoTranslate = on) } }
                switch(
                    title = "Composer in a capsule",
                    summary = "The field and its buttons in one rounded bar, as before 2.0",
                    checked = settings.composerCapsule
                ) { on -> onChange { it.copy(composerCapsule = on) } }
                switch(
                    title = "Hide blocked people in groups",
                    summary = "Their messages are left out of groups you share",
                    checked = settings.hideBlockedInGroups
                ) { on -> onChange { it.copy(hideBlockedInGroups = on) } }
                switch(
                    title = "Ask before sending a recording",
                    summary = "A voice or video message waits for Send or Delete when you let go",
                    checked = settings.confirmRecordings
                ) { on -> onChange { it.copy(confirmRecordings = on) } }
            }

            SettingsGroup("Chat list and search") {
                switch(
                    title = "Hide stories",
                    summary = "No stories above the folders",
                    checked = settings.hideStories
                ) { on -> onChange { it.copy(hideStories = on) } }
                switch(
                    title = "Hide the All tab",
                    summary = "Start on your first folder, when you have folders",
                    checked = settings.hideAllChatsTab
                ) { on -> onChange { it.copy(hideAllChatsTab = on) } }
                switch(
                    title = "Open search without the keyboard",
                    summary = "Show recent chats and people first; tap the field to type",
                    checked = settings.searchWithoutKeyboard
                ) { on -> onChange { it.copy(searchWithoutKeyboard = on) } }
                switch(
                    title = "Pull down for the archive",
                    summary = "Pull the chat list down past its top to open the archive",
                    checked = settings.openArchiveOnPull
                ) { on -> onChange { it.copy(openArchiveOnPull = on) } }
            }

            SettingsGroup("Notifications") {
                switch(
                    title = "Silence people not in contacts",
                    summary = "Their messages arrive without sound or vibration",
                    checked = settings.silenceNonContacts
                ) { on -> onChange { it.copy(silenceNonContacts = on) } }
            }

            SettingsGroup("Experiments") {
                switch(
                    title = "Music library",
                    summary = "Every chat's music as albums, artists and playlists, in the chat list's menu",
                    checked = settings.musicLibrary
                ) { on -> onChange { it.copy(musicLibrary = on) } }
                switch(
                    title = "Members in a group's header (beta)",
                    summary = "A group or channel shows the faces of the people in it rather than its own picture",
                    checked = settings.groupFaces
                ) { on -> onChange { it.copy(groupFaces = on) } }
            }

            SettingsGroup("Connection") {
                switch(
                    title = "Prefer IPv6",
                    summary = "Reach Telegram over IPv6 where the network offers both",
                    checked = settings.preferIpv6
                ) { on -> onChange { it.copy(preferIpv6 = on) } }
            }

            // The last crash, when there has been one: to read and copy into
            // a report. See CrashLog.
            // And the music player's log (1.7), for music that stops or
            // skips by itself: what it did, and the reason the system gave.
            if (crash != null || playerLog != null) {
                SettingsGroup("Diagnostics") {
                    if (crash != null) {
                        link(
                            title = "Last crash",
                            summary = crash!!.lineSequence().drop(2).firstOrNull().orEmpty().ifBlank { "Tap to see it" },
                            onClick = { showingCrash = true }
                        )
                    }
                    if (playerLog != null) {
                        link(
                            title = "Player log",
                            summary = "Why music last stopped or skipped",
                            onClick = { showingPlayerLog = true }
                        )
                    }
                }
            }
        }
    }
}
