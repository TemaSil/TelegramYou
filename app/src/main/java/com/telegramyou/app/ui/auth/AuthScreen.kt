package com.telegramyou.app.ui.auth

import android.content.Context
import android.telephony.TelephonyManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telegramyou.app.telegram.model.AuthState
import com.telegramyou.app.ui.components.ExpressiveLoadingOverlay
import com.telegramyou.app.ui.components.cyclingShape
import com.telegramyou.app.ui.theme.AppTitleFontFamily
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Logging in: phone, then the confirmation code, then a password if the
 * account has two-step verification.
 *
 * Renders an [AuthFormState] and reports every keystroke and submission; what
 * has been typed lives in [AuthViewModel], so a rotation partway through a
 * code does not lose it.
 *
 * Rebuilt on 24 September 2026 to the rules the rest of the app follows, and
 * against what Telegram's own clients do on the same screen (Nekogram read as
 * a reference for behaviour only):
 *
 * - **One phone field with the flag in it.** Telegram splits country, code
 *   and number into three; one field whose leading flag opens the country
 *   list is the Material shape of it, and it takes a pasted "+44 20 …" whole.
 *   It starts with the SIM's calling code, formats as it is typed and names
 *   the country under it — all of it from libphonenumber, in `PhoneEntry`.
 * - **The action at the bottom, where the thumb is**, as an Expressive
 *   button of the medium size — its own type size, padding and a shape that
 *   morphs when pressed — rather than a default button stretched to 56dp
 *   with 14sp text in it. It carries its own loading indicator, so waiting
 *   no longer greys out the whole screen.
 * - **A code is sent on its last digit**, the way every Telegram client
 *   does, and filled in from the SMS by autofill. Resending waits out the
 *   server's timer, visibly, and the number can be corrected.
 * - **The mark is a Material shape** and the name is set in Google Sans
 *   Flex, as on the chat list.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AuthScreen(
    state: AuthFormState,
    onPhoneChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmitPhone: () -> Unit,
    onSubmitCode: () -> Unit,
    onSubmitPassword: () -> Unit,
    onResendCode: () -> Unit,
    onChangeNumber: () -> Unit = {},
    onChangeNumberCancelled: () -> Unit = {},
    onDefaultRegion: (String?) -> Unit = {}
) {
    val auth = state.auth
    val context = LocalContext.current
    LaunchedEffect(Unit) { onDefaultRegion(deviceRegion(context)) }
    BackHandler(enabled = state.isChangingNumber, onBack = onChangeNumberCancelled)

    val submit: () -> Unit = when (state.step) {
        AuthStep.Phone -> onSubmitPhone
        AuthStep.Code -> onSubmitCode
        AuthStep.Password -> onSubmitPassword
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // The container tone reaches under the system bars; the content
            // stays clear of them and of the keyboard, which pushes the
            // button up with it rather than covering it.
            .background(MaterialTheme.colorScheme.surface)
            .safeDrawingPadding()
            .imePadding()
            .padding(horizontal = 24.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BrandMark()
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "TelegramYou",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontFamily = AppTitleFontFamily,
                        fontSize = 32.sp,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(40.dp))

                // Forward slides in from the end, back from the start, on
                // the motion scheme's springs — the same motion the rest of
                // the app moves with.
                val motion = MaterialTheme.motionScheme
                AnimatedContent(
                    targetState = state.step,
                    transitionSpec = {
                        val forward = targetState.ordinal > initialState.ordinal
                        val sign = if (forward) 1 else -1
                        (fadeIn(motion.defaultEffectsSpec()) +
                            slideInHorizontally(motion.defaultSpatialSpec()) { sign * it / 4 }) togetherWith
                            (fadeOut(motion.fastEffectsSpec()) +
                                slideOutHorizontally(motion.defaultSpatialSpec()) { -sign * it / 4 })
                    },
                    label = "authStep"
                ) { step ->
                    when (step) {
                        AuthStep.Phone -> PhoneStep(
                            phone = state.phone,
                            error = auth.errorMessage,
                            onPhoneChange = onPhoneChange,
                            onSubmit = { if (state.canSubmit) onSubmitPhone() }
                        )
                        AuthStep.Code -> CodeStep(
                            state = state,
                            onCodeChange = onCodeChange,
                            onSubmit = { if (state.canSubmit) onSubmitCode() },
                            onResendCode = onResendCode,
                            onChangeNumber = onChangeNumber
                        )
                        AuthStep.Password -> PasswordStep(
                            password = state.password,
                            hint = auth.codeHint,
                            error = auth.errorMessage,
                            onPasswordChange = onPasswordChange,
                            onSubmit = { if (state.canSubmit) onSubmitPassword() }
                        )
                    }
                }
            }
        }

        SubmitButton(
            label = when (state.step) {
                AuthStep.Phone -> "Continue"
                AuthStep.Code -> "Sign in"
                AuthStep.Password -> "Unlock"
            },
            isLoading = auth.isLoading && auth.state != AuthState.Bootstrapping,
            enabled = state.canSubmit,
            onClick = submit,
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }

    // Only while TDLib itself is starting: there is nothing to type into yet.
    // Everything after that waits inside the button instead.
    ExpressiveLoadingOverlay(visible = auth.state == AuthState.Bootstrapping && auth.isLoading)
}

/**
 * The app's mark: the paper plane on one of the shapes the avatars use, in
 * the wallpaper's primary container — morphing slowly through the set and
 * turning, the motion a typing avatar makes but at rest. It starts on the
 * twelve-point cookie. Only the outline moves; the plane stays level.
 */
@Composable
private fun BrandMark() {
    val outline = cyclingShape(startIndex = 9, stepMillis = 1_400, turnMillis = 16_000)
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(outline)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.Send,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(40.dp)
        )
    }
}

/** A step's heading and the line under it. */
@Composable
private fun StepHeader(title: String, subtitle: String) {
    // The full width, so the heading centres on the screen rather than on
    // itself at the start of whatever column holds it — which is how the
    // phone step's heading ended up pushed to the left.
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * The field takes the focus as its step arrives, so the keyboard that was up
 * for the number is still up for the code.
 */
@Composable
private fun rememberStepFocus(): FocusRequester {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    return focus
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneStep(
    phone: String,
    error: String?,
    onPhoneChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val focus = rememberStepFocus()
    var picking by rememberSaveable { mutableStateOf(false) }
    val countries = remember { PhoneEntry.countries(Locale.getDefault()) }
    val region = PhoneEntry.region(phone)
    val country = countries.firstOrNull { it.region == region }

    Column {
        StepHeader("Your phone", "Check the country code and enter your number")
        OutlinedTextField(
            value = phone,
            onValueChange = onPhoneChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus)
                .semantics { contentType = ContentType.PhoneNumber },
            label = { Text("Phone number") },
            leadingIcon = {
                // The country, and the way to choose another — a flag is
                // what every phone field in every messenger puts here.
                IconButton(
                    onClick = { picking = true },
                    modifier = Modifier.semantics {
                        contentDescription = "Country: ${country?.name ?: "not chosen"}. Choose"
                    }
                ) {
                    if (country != null) {
                        Text(country.flag, fontSize = 22.sp)
                    } else {
                        Icon(Icons.Rounded.Public, contentDescription = null)
                    }
                }
            },
            supportingText = {
                Text(error?.takeIf { it.isNotBlank() } ?: country?.name.orEmpty())
            },
            isError = !error.isNullOrBlank(),
            singleLine = true,
            visualTransformation = PhoneTransformation,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() })
        )
    }

    if (picking) {
        CountrySheet(
            countries = countries,
            onPick = { picked ->
                onPhoneChange(PhoneEntry.withCountry(phone, picked))
                picking = false
            },
            onDismiss = { picking = false }
        )
    }
}

/**
 * Every country, searchable by name or by code, in a sheet — the list
 * Telegram opens from its country field, as a Material sheet of list items.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountrySheet(
    countries: List<PhoneCountry>,
    onPick: (PhoneCountry) -> Unit,
    onDismiss: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(query, countries) {
        val needle = query.trim().removePrefix("+")
        if (needle.isEmpty()) countries
        else countries.filter {
            it.name.contains(needle, ignoreCase = true) ||
                it.callingCode.toString().startsWith(needle) ||
                it.region.equals(needle, ignoreCase = true)
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = { Text("Country or code") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn {
            items(shown, key = { it.region }) { country ->
                ListItem(
                    headlineContent = { Text(country.name) },
                    leadingContent = { Text(country.flag, fontSize = 22.sp) },
                    trailingContent = {
                        Text(
                            "+${country.callingCode}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    // The sheet's own tone, as the other sheets' rows have.
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onPick(country) }
                )
            }
        }
    }
}

@Composable
private fun CodeStep(
    state: AuthFormState,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onResendCode: () -> Unit,
    onChangeNumber: () -> Unit
) {
    val auth = state.auth
    val focus = rememberStepFocus()

    // A clock for the resend button, restarted with each code sent.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(auth.codeSentAtMillis) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val waitSeconds = (auth.resendAfterSeconds - (now - auth.codeSentAtMillis) / 1_000)
        .toInt().coerceAtLeast(0)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        StepHeader(
            "Enter code",
            auth.codeHint.ifBlank { "Check Telegram for the code" }
        )
        OutlinedTextField(
            value = state.code,
            onValueChange = onCodeChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus)
                // So the system can offer the code from the SMS itself.
                .semantics { contentType = ContentType.SmsOtpCode },
            label = { Text("Code") },
            supportingText = auth.errorMessage?.takeIf { it.isNotBlank() }?.let { { Text(it) } },
            isError = !auth.errorMessage.isNullOrBlank(),
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge.copy(letterSpacing = 6.sp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() })
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onChangeNumber) { Text("Change number") }
            if (auth.canResend) {
                TextButton(onClick = onResendCode, enabled = waitSeconds == 0) {
                    Text(
                        if (waitSeconds > 0) "Resend in ${countdownLabel(waitSeconds)}"
                        else "Resend code"
                    )
                }
            }
        }
    }
}

@Composable
private fun PasswordStep(
    password: String,
    hint: String,
    error: String?,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val focus = rememberStepFocus()
    var visible by rememberSaveable { mutableStateOf(false) }
    Column {
        StepHeader(
            "Two-step verification",
            "This account has a cloud password. $hint".trim()
        )
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus)
                .semantics { contentType = ContentType.Password },
            label = { Text("Password") },
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (visible) "Hide password" else "Show password"
                    )
                }
            },
            supportingText = error?.takeIf { it.isNotBlank() }?.let { { Text(it) } },
            isError = !error.isNullOrBlank(),
            singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() })
        )
    }
}

/**
 * The step's one action, as Material 3 Expressive sizes it: the medium
 * button's height, padding and type together, and its shape morphing on
 * press. While a request is out it shows Material's loading indicator in
 * place of the label and keeps its colour, rather than greying out.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SubmitButton(
    label: String,
    isLoading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val size = ButtonDefaults.MediumContainerHeight
    Button(
        onClick = { if (!isLoading) onClick() },
        enabled = enabled || isLoading,
        shapes = ButtonDefaults.shapesFor(size),
        contentPadding = ButtonDefaults.contentPaddingFor(size),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = size)
    ) {
        if (isLoading) {
            LoadingIndicator(
                color = LocalContentColor.current,
                modifier = Modifier.size(28.dp)
            )
        } else {
            Text(label, style = ButtonDefaults.textStyleFor(size))
        }
    }
}

/** Draws "+79123456789" as "+7 912 345-67-89"; see PhoneEntry.format. */
private object PhoneTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val formatted = PhoneEntry.format(raw)
        return TransformedText(
            AnnotatedString(formatted),
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int =
                    PhoneEntry.toFormatted(raw, formatted, offset)

                override fun transformedToOriginal(offset: Int): Int =
                    PhoneEntry.toRaw(raw, formatted, offset)
            }
        )
    }
}

/**
 * The country this phone is in: its SIM's, then its network's, then the
 * language setting's — the order Telegram's clients guess in.
 */
private fun deviceRegion(context: Context): String? {
    val telephony = context.getSystemService(TelephonyManager::class.java)
    return listOfNotNull(
        telephony?.simCountryIso,
        telephony?.networkCountryIso,
        Locale.getDefault().country
    ).firstOrNull { it.length == 2 }
}
