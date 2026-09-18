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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.telegramyou.app.telegram.model.AuthState
import com.telegramyou.app.ui.components.ExpressiveLoadingOverlay

/**
 * Logging in: phone, then the confirmation code, then a password if the
 * account has two-step verification.
 *
 * Renders an [AuthFormState] and reports every keystroke and submission. What
 * has been typed lives in [AuthViewModel], not here — a rotation partway
 * through a confirmation code used to lose it, and a code cannot be asked for
 * again without another SMS.
 *
 * This is the first screen anyone sees, so it is the first chance to be wrong
 * about what the app is. It used to paint its mark with `TealSeed` and
 * `CoralPop` — the two constants that exist only as the palette for Android 11
 * and below — which meant the one screen that introduces a client called
 * **You** ignored the wallpaper it is named after. Every colour here now comes
 * from the scheme, so on Android 12 and up the mark is the phone's own colour
 * and below it falls back with everything else.
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
            // A flat container tone, not a gradient. The gradient here mixed
            // primary at 22% into the top and secondary at 12% into the
            // bottom, and the chat background taught this project what that
            // costs: a tinted haze lands within a few units of the container
            // tones drawn on top of it, and the edges of those containers stop
            // being visible. A login screen has one card-shaped thing on it,
            // so it can least afford that.
            //
            // Background before the insets, so the colour reaches under the
            // status and navigation bars while the content stays clear of
            // them. safeDrawingPadding replaces a hardcoded 48.dp of vertical
            // padding, which was a guess at the size of bars it never asked
            // about.
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .safeDrawingPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // A Surface with a shape and a container colour, rather than a Box
            // clipped to a circle and filled with a linear gradient of two
            // hardcoded constants. The glyph was the text "✈" set in
            // displayMedium, which is a font's idea of a paper plane and
            // changes size with the user's text settings; Material ships the
            // icon.
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(88.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Send,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = "TelegramYou",
                style = MaterialTheme.typography.displaySmall,
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
                // Named for what it is, so it cannot shadow the screen's own
                // state — which is how this went wrong the first time.
            ) { step ->
                when (step) {
                    AuthState.WaitPhoneNumber,
                    AuthState.Bootstrapping,
                    AuthState.Error,
                    AuthState.Closed -> {
                        AuthFieldColumn(
                            title = "Your phone",
                            subtitle = "We'll send a login code via Telegram",
                            value = state.phone,
                            onValueChange = onPhoneChange,
                            placeholder = "+1 234 567 8900",
                            keyboardType = KeyboardType.Phone,
                            error = auth.errorMessage,
                            onSubmit = onSubmitPhone,
                            submitLabel = "Continue"
                        )
                    }
                    AuthState.WaitCode -> {
                        AuthFieldColumn(
                            title = "Enter code",
                            subtitle = auth.codeHint.ifBlank {
                                "Check Telegram for the code"
                            },
                            value = state.code,
                            onValueChange = onCodeChange,
                            placeholder = "12345",
                            keyboardType = KeyboardType.Number,
                            error = auth.errorMessage,
                            onSubmit = onSubmitCode,
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
                            onSubmit = onSubmitPassword,
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

/**
 * One step of the sign-in: a heading, a field, and the button that submits it.
 *
 * The field reports its error through `isError` and `supportingText` rather
 * than through a Text placed under it by hand. That is not tidying: the stock
 * wiring recolours the border and the label together and announces the message
 * to a screen reader as the field's own error, which a loose Text below it
 * does not.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
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
            isError = !error.isNullOrBlank(),
            supportingText = error?.takeIf { it.isNotBlank() }?.let { message ->
                { Text(message) }
            },
            visualTransformation = if (isPassword) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() })
            // No colours and no shape passed. Both were set to what the
            // defaults already are — a primary focused border, a primary
            // cursor, the large shape — so they said nothing and would have
            // gone stale the moment the theme moved.
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onSubmit,
            modifier = Modifier
                .fillMaxWidth()
                // Taller than a Button's 40dp default because this is the
                // screen's only action and it is reached with a thumb, at the
                // bottom of a form, one-handed.
                .height(56.dp)
        ) {
            // One button, not two. This used to hold a FilledIconButton inside
            // it, which put a clickable inside a clickable: the inner one took
            // the touches over its own 28dp, and a screen reader read the row
            // as two separate controls with the same action. The label is the
            // whole button now.
            Text(submitLabel, style = MaterialTheme.typography.labelLarge)
        }
        secondary?.invoke()
    }
}
