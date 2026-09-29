package com.telegramyou.app.ui.lock

import android.app.Activity
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.telegramyou.app.settings.AppLockSettings
import com.telegramyou.app.ui.icons.Symbols
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The lock in front of the app: its mark, a dot for each digit typed, a
 * keypad of stock tonal buttons, and the fingerprint where the phone has
 * one — asked for by the platform's own prompt the moment this appears, as
 * the system's own lock screen does.
 *
 * [tryPin] is slow on purpose (PBKDF2), so it runs off the main thread;
 * the PIN is checked when its last digit is typed. Five wrong in a row and
 * the keypad rests for thirty seconds. Back leaves the app rather than
 * reaching anything behind the lock.
 */
@Composable
fun AppLockScreen(
    settings: AppLockSettings,
    tryPin: (String) -> Boolean,
    onBiometricUnlock: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var typed by remember { mutableStateOf("") }
    var wrong by remember { mutableIntStateOf(0) }
    var restingUntil by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var message by remember { mutableStateOf("Enter your PIN") }
    val biometric = settings.biometric && biometricAvailable(context)

    BackHandler { (context as? Activity)?.moveTaskToBack(true) }

    LaunchedEffect(Unit) {
        if (biometric) askBiometric(context, onBiometricUnlock)
    }
    LaunchedEffect(restingUntil) {
        while (System.currentTimeMillis() < restingUntil) {
            now = System.currentTimeMillis()
            delay(500)
        }
        now = System.currentTimeMillis()
    }
    val resting = now < restingUntil

    fun type(digit: Char) {
        if (resting || typed.length >= settings.pinLength) return
        typed += digit
        if (typed.length == settings.pinLength) {
            val attempt = typed
            scope.launch {
                val right = withContext(Dispatchers.Default) { tryPin(attempt) }
                if (!right) {
                    wrong++
                    typed = ""
                    if (wrong >= MAX_WRONG) {
                        wrong = 0
                        restingUntil = System.currentTimeMillis() + REST_MILLIS
                        message = "Too many tries. Wait a moment"
                    } else {
                        message = "Wrong PIN, try again"
                    }
                }
            }
        }
    }

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(72.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
            ) {
                Icon(
                    Symbols.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                if (resting) "Try again in ${((restingUntil - now) / 1000) + 1} s" else message,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(20.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.semantics { contentDescription = "${typed.length} of ${settings.pinLength} digits" }
            ) {
                repeat(settings.pinLength) { index ->
                    Box(
                        Modifier
                            .size(14.dp)
                            .background(
                                if (index < typed.length) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceContainerHighest,
                                CircleShape
                            )
                    )
                }
            }
            Spacer(Modifier.height(40.dp))
            val rows = listOf("123", "456", "789")
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        row.forEach { digit -> Key(digit, enabled = !resting) { type(digit) } }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(KEY_SIZE), contentAlignment = Alignment.Center) {
                        if (biometric) {
                            IconButton(onClick = { askBiometric(context, onBiometricUnlock) }) {
                                Icon(Symbols.Fingerprint, contentDescription = "Unlock with fingerprint")
                            }
                        }
                    }
                    Key('0', enabled = !resting) { type('0') }
                    Box(Modifier.size(KEY_SIZE), contentAlignment = Alignment.Center) {
                        IconButton(onClick = { typed = typed.dropLast(1) }, enabled = typed.isNotEmpty()) {
                            Icon(Symbols.Backspace, contentDescription = "Delete digit")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Key(digit: Char, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        modifier = Modifier
            .size(KEY_SIZE)
            .semantics { contentDescription = "Digit $digit" }
    ) {
        Text(digit.toString(), style = MaterialTheme.typography.headlineSmall)
    }
}

/** Whether the phone can take a fingerprint (or its face) for this: Android 9 and up, and one enrolled. */
fun biometricAvailable(context: Context): Boolean = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
        context.getSystemService(BiometricManager::class.java)
            ?.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
        @Suppress("DEPRECATION")
        (context.getSystemService(BiometricManager::class.java)?.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS)
    else -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
}

/** The platform's own biometric prompt; the PIN stays on screen behind it. */
private fun askBiometric(context: Context, onUnlocked: () -> Unit) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
    val executor = context.mainExecutor
    val prompt = BiometricPrompt.Builder(context)
        .setTitle("Unlock TelegramYou")
        .setNegativeButton("Use PIN", executor) { _, _ -> }
        .build()
    prompt.authenticate(
        CancellationSignal(),
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onUnlocked()
            }
        }
    )
}

private val KEY_SIZE = 72.dp
private const val MAX_WRONG = 5
private const val REST_MILLIS = 30_000L
