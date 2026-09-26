package com.telegramyou.app.ui.settings

import androidx.compose.material3.TopAppBarDefaults
import com.telegramyou.app.update.LocalAppUpdates
import com.telegramyou.app.update.UpdateState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.material3.Badge
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Science
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.telegramyou.app.settings.TextSize
import androidx.compose.material3.Slider
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.telegramyou.app.settings.AppearanceSettings
import com.telegramyou.app.settings.ThemeChoice
import com.telegramyou.app.settings.dynamicColorAvailable
import com.telegramyou.app.telegram.model.TelegramUser
import androidx.compose.foundation.shape.CircleShape
import com.telegramyou.app.ui.avatars.avatarShapeIndex
import com.telegramyou.app.ui.components.materialShapeSet
import com.telegramyou.app.ui.components.AvatarBubble

/**
 * Settings, which for this client is mostly one question.
 *
 * The app is called TelegramYou because **You** is Material You — the palette
 * Android takes from the wallpaper. That switch is therefore not a
 * preference among others, and it sits at the top with the explanation
 * attached rather than buried under a heading.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppearanceSettings,
    me: TelegramUser?,
    onBack: () -> Unit,
    onThemeChange: (ThemeChoice) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onShapedAvatarsChange: (Boolean) -> Unit,
    onLogout: () -> Unit,
    onOpenDevices: () -> Unit = {},
    onOpenStorage: () -> Unit = {},
    onOpenPrivacy: () -> Unit = {},
    onTextScaleChange: (Float) -> Unit = {},
    onOpenGeeks: () -> Unit = {},
    onOpenProxy: () -> Unit = {},
    onOpenUpdates: () -> Unit = {}
) {
    Scaffold(
        containerColor = settingsBackground(),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = settingsBackground())
            )
        }
    ) { padding ->
        SettingsContent(
            settings = settings,
            me = me,
            onThemeChange = onThemeChange,
            onDynamicColorChange = onDynamicColorChange,
            onShapedAvatarsChange = onShapedAvatarsChange,
            onLogout = onLogout,
            contentPadding = padding,
            onOpenDevices = onOpenDevices,
            onOpenStorage = onOpenStorage,
            onOpenPrivacy = onOpenPrivacy,
            onTextScaleChange = onTextScaleChange,
            onOpenGeeks = onOpenGeeks,
            onOpenProxy = onOpenProxy,
            onOpenUpdates = onOpenUpdates
        )
    }
}

/**
 * The settings themselves, without a bar or a back arrow around them.
 *
 * Split out because this content has two homes: its own screen, reached from
 * a route, and a tab inside Home. Nesting one Scaffold inside another would
 * apply the window insets twice, which is the same mistake that once left a
 * hole under the composer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    settings: AppearanceSettings,
    me: TelegramUser?,
    onThemeChange: (ThemeChoice) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onShapedAvatarsChange: (Boolean) -> Unit,
    onLogout: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onOpenDevices: () -> Unit = {},
    onOpenStorage: () -> Unit = {},
    onOpenPrivacy: () -> Unit = {},
    onTextScaleChange: (Float) -> Unit = {},
    onOpenGeeks: () -> Unit = {},
    onOpenProxy: () -> Unit = {},
    onOpenUpdates: () -> Unit = {},
    onOpenProfile: () -> Unit = {}
) {
    // In the order Android's own Settings uses: who you are, then how it
    // looks, then who can see what, then data and the network, then the
    // things most people never open, then the app itself, and leaving last.
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(settingsBackground())
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        if (me != null) {
            SettingsGroup {
                item(
                        title = me.displayName,
                        summary = me.username?.let { "@$it" } ?: me.phoneNumber.orEmpty(),
                        leading = {
                            // In its shape when shapes are on, as it is
                            // everywhere else — which also makes it the
                            // preview for that switch, a little further down.
                            val shapes = materialShapeSet()
                            AvatarBubble(
                                title = me.displayName,
                                seed = me.avatarColor,
                                size = 56.dp,
                                photoPath = me.photoPath,
                                shape = if (settings.shapedAvatars) {
                                    shapes[avatarShapeIndex(me.avatarColor, shapes.size)]
                                } else {
                                    CircleShape
                                }
                            )
                        },
                        onClick = onOpenProfile
                    )
            }
        }

        SettingsGroup("Appearance") {
            // The switch this client exists for, so it says what it does.
            val available = dynamicColorAvailable(Build.VERSION.SDK_INT)
            switch(
                title = "Colour from your wallpaper",
                summary = if (available) {
                    "Material You: the palette comes from your wallpaper"
                } else {
                    // Shown rather than hidden: hiding it would leave the
                    // client's own premise unexplained where it does not apply.
                    "Needs Android 12 or newer"
                },
                checked = settings.dynamicColor && available,
                enabled = available,
                onChange = onDynamicColorChange
            )
            // Three short choices side by side: exactly what a segmented
            // button row is for, set under the row's title.
            item(
                    title = "Theme",
                    onClick = {},
                    below = {
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            ThemeChoice.entries.forEachIndexed { choiceIndex, choice ->
                                SegmentedButton(
                                    selected = settings.theme == choice,
                                    onClick = { onThemeChange(choice) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = choiceIndex,
                                        count = ThemeChoice.entries.size
                                    )
                                ) {
                                    Text(choice.name)
                                }
                            }
                        }
                    }
                )
            // No preview of its own: the account's avatar at the top of this
            // screen changes shape as the switch moves, and a second one here
            // was the only thing leading a row in a group of plain controls.
            switch(
                title = "Shaped avatars",
                summary = "Everyone gets one of Material's shapes as well as a colour",
                checked = settings.shapedAvatars,
                onChange = onShapedAvatarsChange
            )
            // Four stops, the way Android's own display settings offer it.
            // The whole app follows as it moves — this screen included.
            item(
                    title = "Text size",
                    summary = TextSize.label(settings.textScale),
                    onClick = {},
                    below = {
                        Slider(
                            value = TextSize.nearest(settings.textScale),
                            onValueChange = { onTextScaleChange(TextSize.nearest(it)) },
                            valueRange = TextSize.steps.first()..TextSize.steps.last(),
                            steps = TextSize.steps.size - 2,
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { contentDescription = "Text size" }
                        )
                    }
                )
        }

        SettingsGroup("Privacy and security") {
            link(
                title = "Privacy",
                summary = "Who can see your number, your last seen and more",
                icon = Icons.Rounded.Lock,
                tone = IconTone.Secondary,
                onClick = onOpenPrivacy
            )
            link(
                title = "Devices",
                summary = "Where you are signed in",
                icon = Icons.Rounded.Devices,
                tone = IconTone.Secondary,
                onClick = onOpenDevices
            )
        }

        SettingsGroup("Data and network") {
            link(
                title = "Data and storage",
                summary = "The cache, and clearing it",
                icon = Icons.Rounded.Storage,
                tone = IconTone.Tertiary,
                onClick = onOpenStorage
            )
            link(
                title = "Proxy",
                summary = "Connect through SOCKS5, HTTP or MTProto",
                icon = Icons.Rounded.VpnKey,
                tone = IconTone.Tertiary,
                onClick = onOpenProxy
            )
        }

        // One row, and everything behind it off until turned on: the
        // settings most people never need, kept out of their way.
        SettingsGroup {
            link(
                title = "For geeks",
                summary = "Small things for people who like to tinker",
                icon = Icons.Rounded.Science,
                onClick = onOpenGeeks
            )
        }

        // Where Android puts "System update": its own screen, with the
        // version here and a dot when a newer one is out.
        val updates = LocalAppUpdates.current
        val state = updates?.state?.collectAsStateWithLifecycle()?.value
        SettingsGroup {
            val waiting = state is UpdateState.Available || state is UpdateState.Ready
            val dot: (@Composable () -> Unit)? = if (waiting) {
                { Badge() }
            } else null
            link(
                title = "App update",
                summary = when (state) {
                    is UpdateState.Available -> "Version ${state.release.version} is out"
                    is UpdateState.Ready -> "Version ${state.release.version} is ready to install"
                    else -> updates?.let { "Version ${it.installed} · what's new" } ?: "What's new"
                },
                icon = Icons.Rounded.SystemUpdate,
                tone = if (waiting) IconTone.Tertiary else IconTone.Primary,
                trailing = dot,
                onClick = onOpenUpdates
            )
        }

        val error = MaterialTheme.colorScheme.error
        // Asked first, as Android asks before anything it cannot undo: one
        // stray tap on this row used to sign the account out.
        var confirmLogout by remember { mutableStateOf(false) }
        SettingsGroup {
            link(
                title = "Log out",
                titleColor = error,
                onClick = { confirmLogout = true }
            )
        }
        if (confirmLogout) {
            AlertDialog(
                onDismissRequest = { confirmLogout = false },
                icon = { Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null) },
                title = { Text("Log out of Telegram?") },
                text = { Text("Your chats stay on Telegram. You can sign back in with your phone number.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmLogout = false
                        onLogout()
                    }) { Text("Log out") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmLogout = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
internal fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}
