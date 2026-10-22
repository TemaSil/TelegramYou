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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

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
    onHideInRecentsChange: (Boolean) -> Unit,
    /**
     * Whether a PIN is this one (2.3.2): asked before the lock is turned
     * off or its PIN changed, which anyone holding the unlocked phone could
     * otherwise do. Slow on purpose; called off the main thread.
     */
    tryPin: (String) -> Boolean = { true }
) {
    var settingPin by remember { mutableStateOf(false) }
    // What the current PIN, once typed, goes on to: off, or a new one.
    var checking by remember { mutableStateOf<PinCheck?>(null) }
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
    checking?.let { purpose ->
        PinCheckDialog(
            pinLength = settings.pinLength,
            purpose = purpose,
            tryPin = tryPin,
            onDismiss = { checking = null },
            onPassed = {
                checking = null
                when (purpose) {
                    PinCheck.TurnOff -> onDisable()
                    PinCheck.Change -> settingPin = true
                }
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
                    onChange = { on -> if (on) settingPin = true else checking = PinCheck.TurnOff }
                )
                if (settings.enabled) {
                    item(title = "Change PIN", onClick = { checking = PinCheck.Change })
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

/** What the current PIN is asked for. */
private enum class PinCheck(val title: String) {
    TurnOff("Enter your PIN to turn the lock off"),
    Change("Enter your current PIN")
}

/**
 * The current PIN, on the lock's own keypad, before the lock is turned off
 * or given a new PIN (2.3.2). Checked as its last digit goes in; a wrong one
 * is said and cleared.
 */
@Composable
private fun PinCheckDialog(
    pinLength: Int,
    purpose: PinCheck,
    tryPin: (String) -> Boolean,
    onDismiss: () -> Unit,
    onPassed: () -> Unit
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var typed by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        androidx.compose.material3.Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Text(
                    if (wrong) "Wrong PIN, try again" else purpose.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (wrong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 28.dp))
                PinPad(
                    pinLength = pinLength,
                    typed = typed,
                    enabled = true,
                    onDigit = { digit ->
                        if (typed.length < pinLength) {
                            typed += digit
                            wrong = false
                            if (typed.length == pinLength) {
                                val attempt = typed
                                scope.launch {
                                    val right = withContext(Dispatchers.Default) { tryPin(attempt) }
                                    if (right) onPassed() else {
                                        typed = ""
                                        wrong = true
                                    }
                                }
                            }
                        }
                    },
                    onBackspace = { typed = typed.dropLast(1) }
                )
                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 24.dp))
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    }
}
