package com.telegramyou.app.ui.settings

import com.telegramyou.app.i18n.tr

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
                title = { Text(tr("Settings", "Настройки")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Symbols.ArrowBack,
                            contentDescription = tr("Back", "Назад")
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
                tr("Wallpaper colours", "Цвета обоев")
            } else {
                Accents.nameOf(settings.accent) ?: tr("Custom colour", "Свой цвет")
            },
            settings.theme.label + if (settings.pureBlack) tr(", black", ", чёрная") else ""
        ).joinToString(" · ")
        // The app's language (2.6) is Android's to choose, as for any app:
        // on Android 13 its own page for this app, before it the phone's.
        val context = androidx.compose.ui.platform.LocalContext.current
        SettingsGroup {
            link(
                title = tr("Appearance", "Оформление"),
                summary = appearanceSummary,
                icon = Symbols.Palette,
                onClick = onOpenAppearance
            )
            link(
                title = tr("Chat folders", "Папки с чатами"),
                summary = tr("Sort chats into tabs", "Разложите чаты по вкладкам"),
                icon = Symbols.Folder,
                onClick = onOpenFolders
            )
            link(
                title = tr("Notifications", "Уведомления"),
                summary = tr("Sound and previews for chats, groups and channels", "Звук и превью для чатов, групп и каналов"),
                icon = Symbols.Notifications,
                onClick = onOpenNotifications
            )
        }

        SettingsGroup(tr("Privacy and security", "Конфиденциальность")) {
            link(
                title = tr("Privacy", "Конфиденциальность"),
                summary = tr("Who can see your number, your last seen and more", "Кто видит ваш номер, время в сети и другое"),
                icon = Symbols.Lock,
                tone = IconTone.Secondary,
                onClick = onOpenPrivacy
            )
            link(
                title = tr("Devices", "Устройства"),
                summary = tr("Where you are signed in", "Где выполнен вход"),
                icon = Symbols.Devices,
                tone = IconTone.Secondary,
                onClick = onOpenDevices
            )
        }

        SettingsGroup(tr("Data and network", "Данные и сеть")) {
            link(
                title = tr("Data and storage", "Данные и память"),
                summary = tr("The cache, and clearing it", "Кэш и его очистка"),
                icon = Symbols.Storage,
                tone = IconTone.Tertiary,
                onClick = onOpenStorage
            )
            link(
                title = tr("Proxy", "Прокси"),
                summary = tr("Connect through SOCKS5, HTTP or MTProto", "Подключение через SOCKS5, HTTP или MTProto"),
                icon = Symbols.VpnKey,
                tone = IconTone.Tertiary,
                onClick = onOpenProxy
            )
        }

        // One row, and everything behind it off until turned on: the
        // settings most people never need, kept out of their way.
        // Language beside it, the owner's call: set once, like the rest of
        // this group, and out of the way of what is changed often.
        SettingsGroup {
            link(
                title = tr("Language", "Язык"),
                summary = java.util.Locale.getDefault().let { it.getDisplayLanguage(it) }
                    .replaceFirstChar { it.titlecase(java.util.Locale.getDefault()) },
                icon = Symbols.Language,
                onClick = { openLanguageSettings(context) }
            )
            link(
                title = tr("For geeks", "Для гиков"),
                summary = tr("Small things for people who like to tinker", "Мелочи для тех, кто любит покопаться"),
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
                title = tr("App update", "Обновление"),
                summary = when (state) {
                    is UpdateState.Available -> tr("Version ${state.release.label} is out", "Вышла версия ${state.release.label}")
                    is UpdateState.Ready -> tr("Version ${state.release.label} is ready to install", "Версия ${state.release.label} готова к установке")
                    else -> updates?.let { tr("Version ${it.installed} · what's new", "Версия ${it.installed} · что нового") } ?: tr("What's new", "Что нового")
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
                title = tr("Log out", "Выйти"),
                titleColor = error,
                onClick = { confirmLogout = true }
            )
        }
        if (confirmLogout) {
            AlertDialog(
                onDismissRequest = { confirmLogout = false },
                icon = { Icon(Symbols.Logout, contentDescription = null) },
                title = { Text(tr("Log out of Telegram?", "Выйти из Telegram?")) },
                text = { Text(tr("Your chats stay on Telegram. You can sign back in with your phone number.", "Чаты останутся в Telegram. Войти снова можно по номеру телефона.")) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmLogout = false
                        onLogout()
                    }) { Text(tr("Log out", "Выйти")) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmLogout = false }) { Text(tr("Cancel", "Отмена")) }
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

/**
 * Android's language settings for this app: from Android 13 the page that
 * picks a language for one app, which is where a phone's owner expects it;
 * before that the phone's own, which this app follows.
 */
private fun openLanguageSettings(context: android.content.Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        android.content.Intent(android.provider.Settings.ACTION_APP_LOCALE_SETTINGS)
            .setData(android.net.Uri.fromParts("package", context.packageName, null))
    } else {
        android.content.Intent(android.provider.Settings.ACTION_LOCALE_SETTINGS)
    }
    runCatching { context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
