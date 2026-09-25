package com.telegramyou.app.ui.settings

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
        topBar = {
            TopAppBar(
                title = { Text("For geeks") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SectionHeader("In chats")
            ListItem(
                headlineContent = { Text("Double tap a message") },
                supportingContent = { Text(settings.doubleTap.label) },
                modifier = Modifier.clickable { choosingDoubleTap = true }
            )
            GeekSwitch(
                title = "Seconds in message times",
                summary = "14:03:27 rather than 14:03",
                checked = settings.showSeconds
            ) { on -> onChange { it.copy(showSeconds = on) } }
            GeekSwitch(
                title = "Message details",
                summary = "Its exact time and the ids of the message, its sender and the chat, in the menu",
                checked = settings.messageDetails
            ) { on -> onChange { it.copy(messageDetails = on) } }
            GeekSwitch(
                title = "Save and copy media",
                summary = "Save to Downloads, and copy a photo, from a message's menu",
                checked = settings.saveMedia
            ) { on -> onChange { it.copy(saveMedia = on) } }
            GeekSwitch(
                title = "Forward without quoting",
                summary = "Forwarded messages arrive as yours, without \"Forwarded from\"",
                checked = settings.forwardWithoutQuote
            ) { on -> onChange { it.copy(forwardWithoutQuote = on) } }

            SectionHeader("Chat list")
            GeekSwitch(
                title = "Hide stories",
                summary = "No stories above the folders",
                checked = settings.hideStories
            ) { on -> onChange { it.copy(hideStories = on) } }
            GeekSwitch(
                title = "Hide the All tab",
                summary = "Start on your first folder, when you have folders",
                checked = settings.hideAllChatsTab
            ) { on -> onChange { it.copy(hideAllChatsTab = on) } }

            SectionHeader("Connection")
            GeekSwitch(
                title = "Prefer IPv6",
                summary = "Reach Telegram over IPv6 where the network offers both",
                checked = settings.preferIpv6
            ) { on -> onChange { it.copy(preferIpv6 = on) } }
        }
    }
}

/** A switch row whose whole width toggles, as every Android settings screen does. */
@Composable
private fun GeekSwitch(title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
    )
}
