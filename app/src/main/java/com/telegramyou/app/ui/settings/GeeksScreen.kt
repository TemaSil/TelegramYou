package com.telegramyou.app.ui.settings

import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.automirrored.rounded.Forward
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material.icons.rounded.KeyboardHide
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.telegramyou.app.settings.DoubleTapAction
import com.telegramyou.app.settings.GeekSettings

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
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
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
                    icon = Icons.Rounded.TouchApp,
                    onClick = { choosingDoubleTap = true }
                )
                switch(
                    title = "Seconds in message times",
                    summary = "14:03:27 rather than 14:03",
                    checked = settings.showSeconds,
                    icon = Icons.Rounded.Schedule
                ) { on -> onChange { it.copy(showSeconds = on) } }
                switch(
                    title = "Message details",
                    summary = "Its exact time and the ids of the message, its sender and the chat, in the menu",
                    checked = settings.messageDetails,
                    icon = Icons.Rounded.Info
                ) { on -> onChange { it.copy(messageDetails = on) } }
                switch(
                    title = "Save and copy media",
                    summary = "Save to Downloads, and copy a photo, from a message's menu",
                    checked = settings.saveMedia,
                    icon = Icons.Rounded.Download
                ) { on -> onChange { it.copy(saveMedia = on) } }
                switch(
                    title = "Forward without quoting",
                    summary = "Forwarded messages arrive as yours, without \"Forwarded from\"",
                    checked = settings.forwardWithoutQuote,
                    icon = Icons.AutoMirrored.Rounded.Forward
                ) { on -> onChange { it.copy(forwardWithoutQuote = on) } }
            }

            SettingsGroup("Chat list and search") {
                switch(
                    title = "Hide stories",
                    summary = "No stories above the folders",
                    checked = settings.hideStories,
                    icon = Icons.Rounded.VisibilityOff,
                    tone = IconTone.Secondary
                ) { on -> onChange { it.copy(hideStories = on) } }
                switch(
                    title = "Hide the All tab",
                    summary = "Start on your first folder, when you have folders",
                    checked = settings.hideAllChatsTab,
                    icon = Icons.Rounded.Tab,
                    tone = IconTone.Secondary
                ) { on -> onChange { it.copy(hideAllChatsTab = on) } }
                switch(
                    title = "Open search without the keyboard",
                    summary = "Show recent chats and people first; tap the field to type",
                    checked = settings.searchWithoutKeyboard,
                    icon = Icons.Rounded.KeyboardHide,
                    tone = IconTone.Secondary
                ) { on -> onChange { it.copy(searchWithoutKeyboard = on) } }
            }

            SettingsGroup("Connection") {
                switch(
                    title = "Prefer IPv6",
                    summary = "Reach Telegram over IPv6 where the network offers both",
                    checked = settings.preferIpv6,
                    icon = Icons.Rounded.Lan,
                    tone = IconTone.Tertiary
                ) { on -> onChange { it.copy(preferIpv6 = on) } }
            }
        }
    }
}
