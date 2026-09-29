package com.telegramyou.app.ui.lock

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.telegramyou.app.settings.AppLock
import com.telegramyou.app.settings.AppLockSettings
import com.telegramyou.app.settings.AutoLock
import com.telegramyou.app.ui.icons.Symbols
import com.telegramyou.app.ui.settings.SettingsGroup
import com.telegramyou.app.ui.settings.settingsBackground

/**
 * Settings → Privacy and security → App lock. Off, it offers one switch,
 * which asks for a PIN twice. On, the rest: change it, the fingerprint,
 * how long away before it asks again, and whether Recents shows the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockSettingsScreen(
    settings: AppLockSettings,
    onBack: () -> Unit,
    onPinSet: (String) -> Unit,
    onDisable: () -> Unit,
    onBiometricChange: (Boolean) -> Unit,
    onAutoLockChange: (AutoLock) -> Unit,
    onHideInRecentsChange: (Boolean) -> Unit
) {
    var settingPin by remember { mutableStateOf(false) }
    var choosingAutoLock by remember { mutableStateOf(false) }
    val biometricHere = biometricAvailable(LocalContext.current)

    if (settingPin) {
        PinDialog(
            changing = settings.enabled,
            onDismiss = { settingPin = false },
            onSet = { pin ->
                settingPin = false
                onPinSet(pin)
            }
        )
    }
    if (choosingAutoLock) {
        AutoLockDialog(
            selected = settings.autoLock,
            onDismiss = { choosingAutoLock = false },
            onSelect = {
                choosingAutoLock = false
                onAutoLockChange(it)
            }
        )
    }

    Scaffold(
        containerColor = settingsBackground(),
        topBar = {
            TopAppBar(
                title = { Text("App lock") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Symbols.ArrowBack, contentDescription = "Back") }
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
                "A PIN in front of the app, asked for when it opens and when you come back to it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
            )
            SettingsGroup {
                switch(
                    title = "Lock with a PIN",
                    checked = settings.enabled,
                    onChange = { on -> if (on) settingPin = true else onDisable() }
                )
                if (settings.enabled) {
                    item(title = "Change PIN", onClick = { settingPin = true })
                }
            }
            if (settings.enabled) {
                SettingsGroup {
                    switch(
                        title = "Unlock with fingerprint",
                        summary = if (biometricHere) null else "No fingerprint set up on this phone",
                        checked = settings.biometric && biometricHere,
                        enabled = biometricHere,
                        onChange = onBiometricChange
                    )
                    item(
                        title = "Lock",
                        summary = settings.autoLock.label,
                        onClick = { choosingAutoLock = true }
                    )
                    switch(
                        title = "Hide in recent apps",
                        summary = "Recents shows a blank card instead of your chats",
                        checked = settings.hideInRecents,
                        onChange = onHideInRecentsChange
                    )
                }
            }
        }
    }
}

/** A new PIN, typed twice. */
@Composable
private fun PinDialog(changing: Boolean, onDismiss: () -> Unit, onSet: (String) -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val confirming = first != null
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Symbols.Lock, contentDescription = null) },
        title = {
            Text(
                when {
                    confirming -> "Repeat the PIN"
                    changing -> "New PIN"
                    else -> "Choose a PIN"
                }
            )
        },
        text = {
            OutlinedTextField(
                value = pin,
                onValueChange = { typed ->
                    pin = typed.filter(Char::isDigit).take(AppLock.MAX_PIN)
                    error = null
                },
                label = { Text("${AppLock.MIN_PIN} to ${AppLock.MAX_PIN} digits") },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "PIN" }
            )
        },
        confirmButton = {
            TextButton(
                enabled = AppLock.isValidPin(pin),
                onClick = {
                    val chosen = first
                    when {
                        chosen == null -> {
                            first = pin
                            pin = ""
                        }
                        chosen == pin -> onSet(pin)
                        else -> {
                            first = null
                            pin = ""
                            error = "The two did not match. Start again"
                        }
                    }
                }
            ) { Text(if (confirming) "Save" else "Next") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** How long away before it asks again: the four choices, as radio rows. */
@Composable
private fun AutoLockDialog(selected: AutoLock, onDismiss: () -> Unit, onSelect: (AutoLock) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lock") },
        text = {
            Column {
                AutoLock.entries.forEach { choice ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = choice == selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(choice) }
                            )
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(selected = choice == selected, onClick = null)
                        Text(choice.label, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}
