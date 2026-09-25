package com.telegramyou.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LaptopMac
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.TabletMac
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.telegramyou.app.telegram.model.ActiveSession
import com.telegramyou.app.telegram.model.DeviceKind
import com.telegramyou.app.telegram.model.activityLabel
import com.telegramyou.app.telegram.model.appLine
import com.telegramyou.app.telegram.model.title
import com.telegramyou.app.ui.format.chatListTimeLabel
import java.time.ZoneId

/**
 * Everywhere this account is signed in: this phone at the top, then the
 * others by when they were last used, each one ended with a tap and a
 * confirmation, and all of them at once from the row between the two.
 *
 * Stock list items throughout. The only thing drawn is the tonal circle
 * behind each device's icon, which is Material's own leading-avatar shape.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DevicesScreen(
    state: DevicesUiState,
    onBack: () -> Unit,
    onSessionSelected: (ActiveSession) -> Unit,
    onTerminateAllRequested: () -> Unit,
    onDismiss: () -> Unit,
    onTerminateConfirmed: () -> Unit,
    onTerminateAllConfirmed: () -> Unit,
    onMessageShown: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onMessageShown()
    }
    val now = System.currentTimeMillis() / 1000
    val zone = ZoneId.systemDefault()
    val timeLabel = { at: Long -> chatListTimeLabel(at, now, zone) }

    state.confirming?.let { session ->
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("End this session?") },
            text = { Text("${session.title()} will be signed out. ${session.appLine()}".trim()) },
            confirmButton = {
                TextButton(onClick = onTerminateConfirmed) {
                    Text("End session", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        )
    }
    if (state.confirmingAll) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("End all other sessions?") },
            text = { Text("Every device but this one will be signed out.") },
            confirmButton = {
                TextButton(onClick = onTerminateAllConfirmed) {
                    Text("End all", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Devices") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            state.current?.let { current ->
                item(key = "this-heading") { SectionHeader("This device") }
                item(key = "this") { SessionRow(current, current.activityLabel(now, timeLabel), onClick = null) }
            }
            if (state.others.isNotEmpty()) {
                // Between this phone and the others, where it reads as what it
                // is: everything below goes, everything above stays.
                item(key = "end-all") {
                    ListItem(
                        headlineContent = { Text("End all other sessions") },
                        supportingContent = { Text("Signs out every device but this one") },
                        leadingContent = {
                            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                        },
                        colors = ListItemDefaults.colors(
                            headlineColor = MaterialTheme.colorScheme.error,
                            leadingIconColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.clickable(enabled = !state.isWorking, onClick = onTerminateAllRequested)
                    )
                }
                item(key = "others-heading") { SectionHeader("Active sessions") }
                items(state.others, key = { it.id }) { session ->
                    SessionRow(
                        session,
                        session.activityLabel(now, timeLabel),
                        onClick = if (state.isWorking) null else ({ onSessionSelected(session) })
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionRow(session: ActiveSession, activity: String, onClick: (() -> Unit)?) {
    ListItem(
        headlineContent = { Text(session.title()) },
        supportingContent = {
            Column {
                session.appLine().takeIf { it.isNotBlank() }?.let { Text(it) }
                if (session.isPasswordPending) Text("Waiting for the two-step password")
            }
        },
        leadingContent = {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(deviceIcon(session.kind), contentDescription = null)
                }
            }
        },
        trailingContent = { Text(activity, style = MaterialTheme.typography.labelMedium) },
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    )
}

private fun deviceIcon(kind: DeviceKind): ImageVector = when (kind) {
    DeviceKind.Android -> Icons.Rounded.PhoneAndroid
    DeviceKind.Iphone -> Icons.Rounded.PhoneIphone
    DeviceKind.Ipad -> Icons.Rounded.TabletMac
    DeviceKind.Mac -> Icons.Rounded.LaptopMac
    DeviceKind.Windows -> Icons.Rounded.DesktopWindows
    DeviceKind.Linux -> Icons.Rounded.Computer
    DeviceKind.Browser -> Icons.Rounded.Language
    DeviceKind.Console -> Icons.Rounded.SportsEsports
    DeviceKind.Unknown -> Icons.Rounded.Devices
}
