package com.telegramyou.app.ui.settings

import com.telegramyou.app.ui.components.withoutBottom
import com.telegramyou.app.ui.icons.Symbols
import androidx.compose.material3.TopAppBarDefaults
import com.telegramyou.app.update.LocalAppUpdates
import com.telegramyou.app.update.UpdateState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.material3.Badge
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.telegramyou.app.settings.AppearanceSettings
import com.telegramyou.app.settings.dynamicColorAvailable
import com.telegramyou.app.ui.theme.Accents
import com.telegramyou.app.telegram.model.TelegramUser
import androidx.compose.foundation.shape.CircleShape
import com.telegramyou.app.ui.avatars.avatarShapeIndex
import com.telegramyou.app.ui.components.materialShapeAt
import com.telegramyou.app.ui.components.SHAPE_COUNT
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
    onLogout: () -> Unit,
    onOpenDevices: () -> Unit = {},
    onOpenStorage: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenPrivacy: () -> Unit = {},
    onOpenGeeks: () -> Unit = {},
    onOpenProxy: () -> Unit = {},
    onOpenUpdates: () -> Unit = {},
    onOpenAppearance: () -> Unit = {},
    onOpenFolders: () -> Unit = {}
) {
    Scaffold(
        containerColor = settingsBackground(),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Symbols.ArrowBack,
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
            onLogout = onLogout,
            contentPadding = padding,
            onOpenDevices = onOpenDevices,
            onOpenStorage = onOpenStorage,
            onOpenNotifications = onOpenNotifications,
            onOpenPrivacy = onOpenPrivacy,
            onOpenGeeks = onOpenGeeks,
            onOpenProxy = onOpenProxy,
            onOpenUpdates = onOpenUpdates,
            onOpenAppearance = onOpenAppearance,
            onOpenFolders = onOpenFolders
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
    onLogout: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onOpenDevices: () -> Unit = {},
    onOpenStorage: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenPrivacy: () -> Unit = {},
    onOpenGeeks: () -> Unit = {},
    onOpenProxy: () -> Unit = {},
    onOpenUpdates: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenAppearance: () -> Unit = {},
    onOpenFolders: () -> Unit = {}
) {
    // In the order Android's own Settings uses: who you are, then how it
    // looks, then who can see what, then data and the network, then the
    // things most people never open, then the app itself, and leaving last.
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(settingsBackground())
            // The foot inside the scroll, so the page runs on under a
            // floating mini player rather than stopping above it.
            .padding(contentPadding.withoutBottom())
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp + contentPadding.calculateBottomPadding())
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
                            AvatarBubble(
                                title = me.displayName,
                                seed = me.avatarColor,
                                size = 56.dp,
                                photoPath = me.photoPath,
                                shape = if (settings.shapedAvatars) {
                                    materialShapeAt(avatarShapeIndex(me.avatarColor, SHAPE_COUNT))
                                } else {
                                    CircleShape
                                }
                            )
                        },
                        onClick = onOpenProfile
                    )
            }
        }

        // Its own screen now, like Android's Display: colour, theme,
        // avatars and text, with a live preview — see AppearanceScreen. The
        // row says what is set, so the common answer needs no visit.
        val appearanceSummary = listOf(
            if (settings.dynamicColor && dynamicColorAvailable(Build.VERSION.SDK_INT)) {
                "Wallpaper colours"
            } else {
                Accents.nameOf(settings.accent) ?: "Custom colour"
            },
            settings.theme.name + if (settings.pureBlack) ", black" else ""
        ).joinToString(" · ")
        SettingsGroup {
            link(
                title = "Appearance",
                summary = appearanceSummary,
                icon = Symbols.Palette,
                onClick = onOpenAppearance
            )
            link(
                title = "Chat folders",
                summary = "Sort chats into tabs",
                icon = Symbols.Folder,
                onClick = onOpenFolders
            )
            link(
                title = "Notifications",
                summary = "Sound and previews for chats, groups and channels",
                icon = Symbols.Notifications,
                onClick = onOpenNotifications
            )
        }

        SettingsGroup("Privacy and security") {
            link(
                title = "Privacy",
                summary = "Who can see your number, your last seen and more",
                icon = Symbols.Lock,
                tone = IconTone.Secondary,
                onClick = onOpenPrivacy
            )
            link(
                title = "Devices",
                summary = "Where you are signed in",
                icon = Symbols.Devices,
                tone = IconTone.Secondary,
                onClick = onOpenDevices
            )
        }

        SettingsGroup("Data and network") {
            link(
                title = "Data and storage",
                summary = "The cache, and clearing it",
                icon = Symbols.Storage,
                tone = IconTone.Tertiary,
                onClick = onOpenStorage
            )
            link(
                title = "Proxy",
                summary = "Connect through SOCKS5, HTTP or MTProto",
                icon = Symbols.VpnKey,
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
                icon = Symbols.Science,
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
                    is UpdateState.Available -> "Version ${state.release.label} is out"
                    is UpdateState.Ready -> "Version ${state.release.label} is ready to install"
                    else -> updates?.let { "Version ${it.installed} · what's new" } ?: "What's new"
                },
                icon = Symbols.SystemUpdate,
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
                icon = { Icon(Symbols.Logout, contentDescription = null) },
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
