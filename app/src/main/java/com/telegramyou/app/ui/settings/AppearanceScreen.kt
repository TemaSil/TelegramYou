package com.telegramyou.app.ui.settings

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedCard
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.telegramyou.app.settings.AppearanceSettings
import com.telegramyou.app.settings.BubbleCorners
import com.telegramyou.app.settings.ChatWallpaper
import com.telegramyou.app.settings.OutgoingTone
import com.telegramyou.app.ui.chat.LocalChatStyle
import com.telegramyou.app.ui.chat.StyledMessage
import com.telegramyou.app.ui.chat.chatWallpaper
import com.telegramyou.app.settings.TextSize
import com.telegramyou.app.settings.ThemeChoice
import com.telegramyou.app.settings.dynamicColorAvailable
import com.telegramyou.app.ui.icons.Symbols
import com.telegramyou.app.ui.theme.Accents
import com.telegramyou.app.ui.theme.schemeFromSeed

/** What Appearance can change; the screen hands each to its setter. */
class AppearanceActions(
    val onThemeChange: (ThemeChoice) -> Unit = {},
    val onDynamicColorChange: (Boolean) -> Unit = {},
    val onAccentChange: (Int) -> Unit = {},
    val onPureBlackChange: (Boolean) -> Unit = {},
    val onChatColorsFromAvatarChange: (Boolean) -> Unit = {},
    val onShapedAvatarsChange: (Boolean) -> Unit = {},
    val onTextScaleChange: (Float) -> Unit = {},
    val onChatWallpaperChange: (ChatWallpaper) -> Unit = {},
    val onOutgoingToneChange: (OutgoingTone) -> Unit = {},
    val onBubbleCornersChange: (Int) -> Unit = {},
    val onMessageTextScaleChange: (Float) -> Unit = {},
    val onTwoLinePreviewsChange: (Boolean) -> Unit = {},
    val onReduceMotionChange: (Boolean) -> Unit = {}
)

/**
 * Settings → Appearance: how the app looks, on a screen of its own, as
 * Android's Display settings are — with a conversation at the top that is
 * redrawn by every change below it, the way the official client's Chat
 * settings show one. The app's own theme is what redraws it: this screen
 * sits inside it like everything else.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    settings: AppearanceSettings,
    onBack: () -> Unit,
    actions: AppearanceActions
) {
    Scaffold(
        containerColor = settingsBackground(),
        topBar = {
            TopAppBar(
                title = { Text("Appearance") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Symbols.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = settingsBackground())
            )
        }
    ) { padding ->
        // Read before the groups: their builders are plain lambdas and cannot
        // read the theme themselves (see SettingsGroup).
        val available = dynamicColorAvailable(Build.VERSION.SDK_INT)
        val wallpaper = settings.dynamicColor && available
        val dark = MaterialTheme.colorScheme.surface.run { red + green + blue } < 1.5f
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            ChatPreview(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            SettingsGroup("Colour") {
                // The switch this client exists for, so it says what it does.
                switch(
                    title = "Colour from your wallpaper",
                    summary = if (available) {
                        "Material You: the palette comes from your wallpaper"
                    } else {
                        "Needs Android 12 or newer"
                    },
                    checked = wallpaper,
                    enabled = available,
                    onChange = actions.onDynamicColorChange
                )
                // The accents stand for the wallpaper when it is off. Shown,
                // dimmed, while it is on, so the choice is visible and it is
                // plain what turns it on.
                item(
                    title = "Accent colour",
                    summary = if (wallpaper) {
                        "Turn off wallpaper colour to choose one"
                    } else {
                        Accents.nameOf(settings.accent) ?: "Custom"
                    },
                    onClick = {},
                    below = {
                        AccentSwatches(
                            selected = settings.accent,
                            enabled = !wallpaper,
                            dark = dark,
                            onSelect = actions.onAccentChange
                        )
                    }
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
                            ThemeChoice.entries.forEachIndexed { index, choice ->
                                SegmentedButton(
                                    selected = settings.theme == choice,
                                    onClick = { actions.onThemeChange(choice) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = ThemeChoice.entries.size
                                    )
                                ) {
                                    Text(choice.name)
                                }
                            }
                        }
                    }
                )
                switch(
                    title = "Pure black",
                    summary = "A black background in the dark theme, for OLED screens",
                    checked = settings.pureBlack,
                    onChange = actions.onPureBlackChange
                )
                switch(
                    title = "Colours from the avatar",
                    summary = "Each chat takes its colours from the other person's picture",
                    checked = settings.chatColorsFromAvatar,
                    onChange = actions.onChatColorsFromAvatarChange
                )
            }

            SettingsGroup("Chat") {
                // "Chat background", not "Wallpaper": on Android that word
                // means the home screen's picture, which is also what Material
                // You takes its colours from, two items up.
                item(
                    title = "Chat background",
                    onClick = {},
                    below = {
                        WallpaperPicker(
                            selected = settings.chatWallpaper,
                            onSelect = actions.onChatWallpaperChange
                        )
                    }
                )
                item(
                    title = "Your messages",
                    onClick = {},
                    below = {
                        Choices(
                            options = OutgoingTone.entries,
                            selected = settings.outgoingTone,
                            label = { it.label },
                            onSelect = actions.onOutgoingToneChange
                        )
                    }
                )
                item(
                    title = "Message corners",
                    summary = "${settings.bubbleCorners} dp",
                    onClick = {},
                    below = {
                        Slider(
                            value = settings.bubbleCorners.toFloat(),
                            onValueChange = { actions.onBubbleCornersChange(BubbleCorners.settle(it)) },
                            valueRange = BubbleCorners.MIN.toFloat()..BubbleCorners.MAX.toFloat(),
                            steps = BubbleCorners.SLIDER_STEPS,
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { contentDescription = "Message corners" }
                        )
                    }
                )
                item(
                    title = "Message text size",
                    summary = TextSize.label(settings.messageTextScale),
                    onClick = {},
                    below = {
                        Slider(
                            value = TextSize.nearest(settings.messageTextScale),
                            onValueChange = { actions.onMessageTextScaleChange(TextSize.nearest(it)) },
                            valueRange = TextSize.steps.first()..TextSize.steps.last(),
                            steps = TextSize.steps.size - 2,
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { contentDescription = "Message text size" }
                        )
                    }
                )
            }

            SettingsGroup("Chat list and text") {
                switch(
                    title = "Shaped avatars",
                    summary = "Everyone gets one of Material's shapes as well as a colour",
                    checked = settings.shapedAvatars,
                    onChange = actions.onShapedAvatarsChange
                )
                switch(
                    title = "Two-line previews",
                    summary = "Show more of the last message in the chat list",
                    checked = settings.twoLinePreviews,
                    onChange = actions.onTwoLinePreviewsChange
                )
                // Four stops, the way Android's own display settings offer it.
                // The preview above follows as it moves.
                item(
                    title = "Text size",
                    summary = TextSize.label(settings.textScale),
                    onClick = {},
                    below = {
                        Slider(
                            value = TextSize.nearest(settings.textScale),
                            onValueChange = { actions.onTextScaleChange(TextSize.nearest(it)) },
                            valueRange = TextSize.steps.first()..TextSize.steps.last(),
                            steps = TextSize.steps.size - 2,
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { contentDescription = "Text size" }
                        )
                    }
                )
            }

            SettingsGroup("Motion") {
                switch(
                    title = "Less motion",
                    summary = "Screens fade instead of opening out, and nothing bounces",
                    checked = settings.reduceMotion,
                    onChange = actions.onReduceMotionChange
                )
            }
        }
    }
}

/**
 * Three messages as a chat will draw them — the wallpaper, the bubbles'
 * corners and tone and the message text size come from the same
 * LocalChatStyle and StyledMessage the conversation uses — so a change below
 * shows here before anybody leaves the screen.
 */
@Composable
private fun ChatPreview(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(28.dp),
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Preview" }
    ) {
        Column(
            modifier = Modifier
                .chatWallpaper(LocalChatStyle.current.wallpaper)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PreviewBubble("Did the new colours land?", outgoing = false)
            PreviewBubble("They did — everything follows the accent now ✨", outgoing = true)
            PreviewBubble("Looks like home", outgoing = false)
        }
    }
}

@Composable
private fun PreviewBubble(text: String, outgoing: Boolean) = StyledMessage(outgoing) {
    val colors = MaterialTheme.colorScheme
    val corners = LocalChatStyle.current.bubbleCorners.dp
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (outgoing) colors.onPrimary else colors.onSurface,
            modifier = Modifier
                .widthIn(max = 260.dp)
                .background(
                    if (outgoing) colors.primary else colors.surfaceContainerHighest,
                    RoundedCornerShape(corners)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

/**
 * The accents as circles in their own primary — the colour each would
 * actually give the app, not the raw seed, so what is tapped is what
 * arrives. The chosen one ringed and ticked.
 */
@Composable
private fun AccentSwatches(selected: Int, enabled: Boolean, dark: Boolean, onSelect: (Int) -> Unit) {
    val swatches = remember(dark) {
        Accents.all.map { (name, seed) -> Triple(name, seed, Color(schemeFromSeed(seed, dark).primary)) }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .alpha(if (enabled) 1f else 0.38f),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        swatches.forEach { (name, seed, color) ->
            val chosen = seed == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .then(
                        if (chosen) {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        } else {
                            Modifier
                        }
                    )
                    .padding(4.dp)
                    .background(color, CircleShape)
                    .selectable(
                        selected = chosen,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onSelect(seed) }
                    )
                    .semantics { contentDescription = name }
            ) {
                if (chosen) {
                    Icon(
                        Symbols.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * The chat backgrounds as small cards, each showing what it draws with two
 * bubbles on it — the way Android's own wallpaper picker and the official
 * client offer backgrounds. A picture is the choice here; a segmented row of
 * names said nothing about how any of them looks. Stock cards: the chosen
 * one takes the primary outline and a tick, and nothing moves.
 *
 * Four, in one row, all on screen. There were nine in a grid, and each
 * choice set off a moment of motion — the card giving, its corners opening,
 * the pattern settling in; the owner found both too much for choosing a
 * background (29 September), and they are gone.
 */
@Composable
private fun WallpaperPicker(selected: ChatWallpaper, onSelect: (ChatWallpaper) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ChatWallpaper.entries.forEach { wallpaper ->
            val chosen = wallpaper == selected
            OutlinedCard(
                onClick = { onSelect(wallpaper) },
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    if (chosen) 2.dp else 1.dp,
                    if (chosen) colors.primary else colors.outlineVariant
                ),
                modifier = Modifier
                    .weight(1f)
                    .semantics { this.selected = chosen }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(104.dp)
                        .chatWallpaper(wallpaper)
                        .padding(8.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(
                            Modifier
                                .size(width = 36.dp, height = 14.dp)
                                .background(colors.surfaceContainerHighest, RoundedCornerShape(7.dp))
                        )
                        Box(
                            Modifier
                                .align(Alignment.End)
                                .size(width = 40.dp, height = 14.dp)
                                .background(colors.primary, RoundedCornerShape(7.dp))
                        )
                    }
                    if (chosen) {
                        Icon(
                            Symbols.Check,
                            contentDescription = null,
                            tint = colors.onPrimary,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(22.dp)
                                .background(colors.primary, CircleShape)
                                .padding(3.dp)
                        )
                    }
                }
                Text(
                    wallpaper.label,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** A few short choices side by side: Material's segmented button row. */
@Composable
private fun <T> Choices(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
            ) {
                Text(label(option), maxLines = 1)
            }
        }
    }
}
