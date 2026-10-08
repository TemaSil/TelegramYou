package com.telegramyou.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.telegramyou.app.notifications.NotificationScope
import com.telegramyou.app.notifications.ScopeNotifications
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.ui.icons.Symbols
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Settings → Notifications (2.0): Telegram's defaults for private chats,
 * groups and channels, each a group of three stock switches. A chat given
 * its own settings in its info keeps them; every other chat follows these.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    settings: Map<NotificationScope, ScopeNotifications>,
    onBack: () -> Unit,
    onChange: (NotificationScope, ScopeNotifications) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Scaffold(
        containerColor = settingsBackground(),
        topBar = {
            TopAppBar(
                title = { Text("Notifications") },
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
            NotificationScope.entries.forEach { scope ->
                val current = settings[scope] ?: ScopeNotifications()
                SettingsGroup(scope.label) {
                    switch(
                        title = "Notifications",
                        summary = current.summaryFor(scope),
                        checked = current.enabled
                    ) { on -> onChange(scope, current.copy(enabled = on)) }
                    switch(
                        title = "Message preview",
                        summary = "Show what the message says in the notification",
                        checked = current.showPreview,
                        enabled = current.enabled
                    ) { on -> onChange(scope, current.copy(showPreview = on)) }
                    switch(
                        title = "Sound",
                        checked = current.sound,
                        enabled = current.enabled
                    ) { on -> onChange(scope, current.copy(sound = on)) }
                    // The rest is Android's: this kind of chat has a channel
                    // of its own there (2.1), with the tone, the vibration
                    // and Do Not Disturb.
                    link(
                        title = "More in Android's settings",
                        summary = "Tone, vibration, and what reaches you in Do Not Disturb",
                        onClick = { openChannelSettings(context, scope) }
                    )
                }
            }
        }
    }
}

/** Loads the three kinds' defaults, and writes each change through at once. */
class NotificationsViewModel(private val repository: TelegramRepository) : ViewModel() {
    private val _settings = MutableStateFlow<Map<NotificationScope, ScopeNotifications>>(emptyMap())
    val settings: StateFlow<Map<NotificationScope, ScopeNotifications>> = _settings.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { repository.scopeNotifications() }.onSuccess { _settings.value = it }
        }
    }

    fun onChange(scope: NotificationScope, settings: ScopeNotifications) {
        // Drawn at once; a refusal puts back what the server still has.
        _settings.update { it + (scope to settings) }
        viewModelScope.launch {
            val saved = runCatching { repository.setScopeNotifications(scope, settings) }.isSuccess
            if (!saved) runCatching { repository.scopeNotifications() }.onSuccess { _settings.value = it }
        }
    }
}

/** Android's own page for [scope]'s channel; see TelegramYouApp.messagesChannel. */
private fun openChannelSettings(context: android.content.Context, scope: NotificationScope) {
    val intent = android.content.Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
        .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, com.telegramyou.app.TelegramYouApp.messagesChannel(scope))
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
