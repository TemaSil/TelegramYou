package com.telegramyou.app.ui.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.telegramyou.app.telegram.model.AuthState
import com.telegramyou.app.telegram.model.AuthUiState
import com.telegramyou.app.ui.components.ExpressiveLoadingOverlay
import com.telegramyou.app.ui.theme.CoralPop
import com.telegramyou.app.ui.theme.DeepInk
import com.telegramyou.app.ui.theme.TealSeed

/**
 * Logging in: phone, then the confirmation code, then a password if the
 * account has two-step verification.
 *
 * Renders an [AuthFormState] and reports every keystroke and submission. What
 * has been typed lives in [AuthViewModel], not here — a rotation partway
 * through a confirmation code used to lose it, and a code cannot be asked for
 * again without another SMS.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    state: AuthFormState,
    onPhoneChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmitPhone: () -> Unit,
    onSubmitCode: () -> Unit,
    onSubmitPassword: () -> Unit,
    onResendCode: () -> Unit
) {
    val auth = state.auth

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
                    )
                )
            )
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(TealSeed, CoralPop))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("✈", style = MaterialTheme.typography.displayMedium)
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = "TelegramYou",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Material You Expressive client",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(36.dp))

            AnimatedContent(
                targetState = auth.state,
                transitionSpec = {
                    (fadeIn() + slideInVertically { it / 3 }) togetherWith
                        (fadeOut() + slideOutVertically { -it / 4 })
                },
                label = "authStep"
            ) { state ->
                when (state) {
                    AuthState.WaitPhoneNumber, AuthState.Bootstrapping, AuthState.Error, AuthState.Closed -> {
                        AuthFieldColumn(
                            title = "Your phone",
                            subtitle = "We'll send a login code via Telegram",
                            value = state.phone,
                            onValueChange = onPhoneChange,
                            placeholder = "+1 234 567 8900",
                            keyboardType = KeyboardType.Phone,
                            error = auth.errorMessage,
                            onSubmit = {
                                onSubmitPhone()
                            },
                            submitLabel = "Continue"
                        )
                    }
                    AuthState.WaitCode -> {
                        AuthFieldColumn(
                            title = "Enter code",
                            subtitle = auth.codeHint.ifBlank { "Check Telegram for the code" },
                            value = state.code,
                            onValueChange = onCodeChange,
                            placeholder = "12345",
                            keyboardType = KeyboardType.Number,
                            error = auth.errorMessage,
                            onSubmit = {
                                onSubmitCode()
                            },
                            submitLabel = "Sign in",
                            secondary = {
                                TextButton(onClick = onResendCode) {
                                    Text("Resend code")
                                }
                            }
                        )
                    }
                    AuthState.WaitPassword -> {
                        AuthFieldColumn(
                            title = "2FA password",
                            subtitle = "Cloud password is enabled on this account",
                            value = state.password,
                            onValueChange = onPasswordChange,
                            placeholder = "Password",
                            keyboardType = KeyboardType.Password,
                            isPassword = true,
                            error = auth.errorMessage,
                            onSubmit = {
                                onSubmitPassword()
                            },
                            submitLabel = "Unlock"
                        )
                    }
                    AuthState.Ready -> Unit
                }
            }
        }
        ExpressiveLoadingOverlay(visible = auth.isLoading)
    }
}

@Composable
private fun AuthFieldColumn(
    title: String,
    subtitle: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    error: String?,
    onSubmit: () -> Unit,
    submitLabel: String,
    isPassword: Boolean = false,
    secondary: (@Composable () -> Unit)? = null
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(placeholder) },
            visualTransformation = if (isPassword) PasswordVisualTransformation() else
                androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            shape = MaterialTheme.shapes.large,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.primary
            )
        )
        if (!error.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = DeepInk
            )
        ) {
            Text(submitLabel, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.size(8.dp))
            FilledIconButton(
                onClick = onSubmit,
                modifier = Modifier.size(28.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = DeepInk.copy(alpha = 0.15f),
                    contentColor = DeepInk
                )
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
        secondary?.invoke()
    }
}
