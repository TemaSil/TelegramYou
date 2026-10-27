package com.telegramyou.app.ui.lock

import com.telegramyou.app.i18n.tr

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
import androidx.compose.material3.MaterialTheme
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
     * Whether a PIN is this one (2.4): asked before the lock is turned
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
                title = { Text(tr("App lock", "Блокировка приложения")) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Symbols.ArrowBack, contentDescription = tr("Back", "Назад")) }
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
                tr("A PIN in front of the app, asked for when it opens and when you come back to it.", "PIN перед приложением — при открытии и при возвращении в него."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
            )
            SettingsGroup {
                switch(
                    title = tr("Lock with a PIN", "Блокировка PIN-кодом"),
                    checked = settings.enabled,
                    onChange = { on -> if (on) settingPin = true else checking = PinCheck.TurnOff }
                )
                if (settings.enabled) {
                    item(title = tr("Change PIN", "Сменить PIN"), onClick = { checking = PinCheck.Change })
                }
            }
            if (settings.enabled) {
                SettingsGroup {
                    switch(
                        title = tr("Unlock with fingerprint", "Разблокировка отпечатком"),
                        summary = if (biometricHere) null else tr("No fingerprint set up on this phone", "На этом телефоне не настроен отпечаток"),
                        checked = settings.biometric && biometricHere,
                        enabled = biometricHere,
                        onChange = onBiometricChange
                    )
                    item(
                        title = tr("Lock", "Блокировать"),
                        summary = settings.autoLock.label,
                        onClick = { choosingAutoLock = true }
                    )
                    switch(
                        title = tr("Hide in recent apps", "Скрывать в недавних"),
                        summary = tr("Recents shows a blank card instead of your chats. Screenshots are off too", "В недавних видна пустая карточка вместо чатов. Скриншоты тоже отключены"),
                        checked = settings.hideInRecents,
                        onChange = onHideInRecentsChange
                    )
                }
            }
        }
    }
}

/**
 * A new PIN, typed twice on the lock's own keypad (2.4.1), full screen as
 * the Pixel sets one — not a text field in a dialog. Chosen at any length
 * from AppLock.MIN_PIN to MAX_PIN, a dot per digit as it goes in, then
 * Continue; repeated against that length, checked on its last digit.
 */
@Composable
private fun PinDialog(changing: Boolean, onDismiss: () -> Unit, onSet: (String) -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    var typed by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val chosen = first
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
                    when {
                        chosen != null -> tr("Repeat the PIN", "Повторите PIN")
                        changing -> tr("New PIN", "Новый PIN")
                        else -> tr("Choose a PIN", "Придумайте PIN")
                    },
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    error ?: if (chosen == null) tr("${AppLock.MIN_PIN} to ${AppLock.MAX_PIN} digits", "От ${AppLock.MIN_PIN} до ${AppLock.MAX_PIN} цифр") else " ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 24.dp))
                PinPad(
                    pinLength = chosen?.length,
                    typed = typed,
                    enabled = true,
                    onDigit = { digit ->
                        val limit = chosen?.length ?: AppLock.MAX_PIN
                        if (typed.length < limit) {
                            typed += digit
                            error = null
                            if (chosen != null && typed.length == chosen.length) {
                                if (typed == chosen) {
                                    onSet(typed)
                                } else {
                                    first = null
                                    typed = ""
                                    error = tr("The two did not match. Start again", "PIN не совпали. Начните заново")
                                }
                            }
                        }
                    },
                    onBackspace = { typed = typed.dropLast(1) }
                )
                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 24.dp))
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = onDismiss) { Text(tr("Cancel", "Отмена")) }
                    if (chosen == null) {
                        androidx.compose.material3.Button(
                            enabled = AppLock.isValidPin(typed),
                            onClick = {
                                first = typed
                                typed = ""
                            }
                        ) { Text(tr("Continue", "Продолжить")) }
                    }
                }
            }
        }
    }
}

/** How long away before it asks again: the four choices, as radio rows. */
@Composable
private fun AutoLockDialog(selected: AutoLock, onDismiss: () -> Unit, onSelect: (AutoLock) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Lock", "Блокировать")) },
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
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Done", "Готово")) } }
    )
}

/** What the current PIN is asked for. */
private enum class PinCheck(private val en: String, private val ru: String) {
    TurnOff("Enter your PIN to turn the lock off", "Введите PIN, чтобы выключить блокировку"),
    Change("Enter your current PIN", "Введите текущий PIN");

    val title: String get() = com.telegramyou.app.i18n.tr(en, ru)
}

/**
 * The current PIN, on the lock's own keypad, before the lock is turned off
 * or given a new PIN (2.4). Checked as its last digit goes in; a wrong one
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
                    if (wrong) tr("Wrong PIN, try again", "Неверный PIN, попробуйте ещё") else purpose.title,
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
                TextButton(onClick = onDismiss) { Text(tr("Cancel", "Отмена")) }
            }
        }
    }
}
