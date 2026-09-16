package com.telegramyou.app.ui.settings

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.telegramyou.app.settings.AppearanceSettings
import com.telegramyou.app.settings.ThemeChoice
import com.telegramyou.app.settings.dynamicColorAvailable
import com.telegramyou.app.telegram.model.TelegramUser
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
    onLogout: () -> Unit
) {
    Scaffold(
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
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            if (me != null) {
                ListItem(
                    headlineContent = { Text(me.displayName) },
                    supportingContent = {
                        Text(me.username?.let { "@$it" } ?: me.phoneNumber.orEmpty())
                    },
                    leadingContent = {
                        AvatarBubble(title = me.displayName, seed = me.avatarColor, size = 48.dp)
                    }
                )
                HorizontalDivider()
            }

            SectionHeader("Appearance")

            // The switch this client exists for, so it says what it does.
            val available = dynamicColorAvailable(Build.VERSION.SDK_INT)
            ListItem(
                headlineContent = { Text("Colour from your wallpaper") },
                supportingContent = {
                    Text(
                        if (available) {
                            "Material You: the app takes its palette from the " +
                                "system, so it looks like this phone rather " +
                                "than like Telegram."
                        } else {
                            // Shown rather than hidden: hiding it would leave
                            // the client's own premise unexplained on the
                            // phones where it does not apply.
                            "Needs Android 12 or newer. Below that the app " +
                                "uses its own palette."
                        }
                    )
                },
                trailingContent = {
                    Switch(
                        checked = settings.dynamicColor && available,
                        onCheckedChange = onDynamicColorChange,
                        enabled = available
                    )
                }
            )

            SectionHeader("Theme")

            // A segmented row rather than three rows of radio buttons: the
            // choice is one of three and they are short enough to sit side by
            // side, which is exactly what the component is for.
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                ThemeChoice.entries.forEachIndexed { index, choice ->
                    SegmentedButton(
                        selected = settings.theme == choice,
                        onClick = { onThemeChange(choice) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = ThemeChoice.entries.size
                        )
                    ) {
                        Text(choice.name)
                    }
                }
            }

            HorizontalDivider()

            ListItem(
                headlineContent = {
                    Text("Log out", color = MaterialTheme.colorScheme.error)
                },
                leadingContent = {
                    Icon(
                        Icons.AutoMirrored.Rounded.Logout,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                modifier = Modifier.clickable(onClick = onLogout)
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}
