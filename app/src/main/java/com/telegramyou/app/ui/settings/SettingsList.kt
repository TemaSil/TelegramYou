package com.telegramyou.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/**
 * Settings the way Android's own Settings app draws them since Material 3
 * Expressive: rows in rounded groups with a hairline gap between them, a
 * title and a line under it.
 *
 * Icons, in a tonal circle, only where Android's own Settings puts them: on
 * a row that leads to another screen, or where the picture says something —
 * a device's kind, a storage category. A switch, a slider or a choice goes
 * without; an icon on every row was the owner's "too many".
 *
 * Built from [SegmentedListItem] and `ListItemDefaults.segmentedShapes` —
 * Material's list, which is what the chat list already uses — rather than
 * from cards around plain rows. The group's outer corners are the shapes'
 * own; nothing here draws a corner by hand.
 *
 * A group is declared as a list of rows because the shape of each one
 * depends on where it sits and how many there are, which a composable
 * cannot know about its siblings.
 */
class SettingsGroupScope internal constructor() {
    // Rows as data, drawn by the group itself. They were composable lambdas
    // first, called from the group with their place in it, and Compose
    // skipped any whose place had not changed — so a row went on showing
    // what it was first given: a privacy rule changed in its dialog kept its
    // old summary, an ended session stayed in the list. Data has no such
    // trap: the group redraws every row from what it holds now.
    internal val rows = mutableListOf<SettingsRow>()

    /** Any row; [link] and [switch] are the common shapes of it. */
    fun item(
        title: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        summary: String? = null,
        leading: (@Composable () -> Unit)? = null,
        trailing: (@Composable () -> Unit)? = null,
        titleColor: Color = Color.Unspecified,
        below: (@Composable () -> Unit)? = null
    ) {
        rows += SettingsRow(title, onClick, modifier, summary, leading, trailing, titleColor, below)
    }

    /** A row that opens somewhere else. */
    fun link(
        title: String,
        summary: String? = null,
        icon: ImageVector? = null,
        tone: IconTone = IconTone.Primary,
        titleColor: Color = Color.Unspecified,
        trailing: (@Composable () -> Unit)? = null,
        onClick: () -> Unit
    ) = item(
        title = title,
        onClick = onClick,
        summary = summary,
        leading = icon?.let { { SettingsIcon(it, tone) } },
        trailing = trailing,
        titleColor = titleColor
    )

    /** A row that is a switch — the whole row toggles it, as on Android. */
    fun switch(
        title: String,
        checked: Boolean,
        summary: String? = null,
        icon: ImageVector? = null,
        tone: IconTone = IconTone.Primary,
        enabled: Boolean = true,
        leading: (@Composable () -> Unit)? = null,
        onChange: (Boolean) -> Unit
    ) = item(
        title = title,
        onClick = { if (enabled) onChange(!checked) },
        modifier = Modifier.semantics { stateDescription = if (checked) "On" else "Off" },
        summary = summary,
        leading = leading ?: icon?.let { { SettingsIcon(it, tone) } },
        trailing = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) }
    )
}

/** One row of a [SettingsGroup], as data; see [SettingsGroupScope]. */
internal class SettingsRow(
    val title: String,
    val onClick: () -> Unit,
    val modifier: Modifier,
    val summary: String?,
    val leading: (@Composable () -> Unit)?,
    val trailing: (@Composable () -> Unit)?,
    val titleColor: Color,
    val below: (@Composable () -> Unit)?
)

/**
 * One group, with an optional heading above it in the primary colour, as
 * Android's Settings heads its sections.
 */
@Composable
fun SettingsGroup(title: String? = null, build: SettingsGroupScope.() -> Unit) {
    // A plain lambda, run here, on purpose. As a composable one it had a
    // recompose scope of its own: a change to what it read re-ran only the
    // builder, which added rows to a list this function had already drawn,
    // and the group never redrew — rows went on showing their old values.
    // Anything the builder needs from the theme or from state is read by
    // the caller, before the group.
    val scope = SettingsGroupScope().apply(build)
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp)
            )
        } else {
            Box(Modifier.padding(top = 16.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            scope.rows.forEachIndexed { index, row ->
                SettingsItem(
                    index = index,
                    count = scope.rows.size,
                    title = row.title,
                    onClick = row.onClick,
                    modifier = row.modifier,
                    summary = row.summary,
                    leading = row.leading,
                    trailing = row.trailing,
                    titleColor = row.titleColor,
                    below = row.below
                )
            }
        }
    }
}

/** A single row in a group; see [SettingsGroup]. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsItem(
    index: Int,
    count: Int,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    titleColor: Color = Color.Unspecified,
    below: (@Composable () -> Unit)? = null
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        modifier = modifier,
        colors = ListItemDefaults.segmentedColors(containerColor = settingsRowColor()),
        leadingContent = leading,
        trailingContent = trailing,
        supportingContent = if (summary != null || below != null) {
            {
                Column {
                    if (summary != null) Text(summary)
                    below?.invoke()
                }
            }
        } else null,
        content = { Text(title, color = titleColor) }
    )
}

/** The rows' fill: the lightest container, on the screen's darker one. */
@Composable
@ReadOnlyComposable
fun settingsRowColor(): Color = MaterialTheme.colorScheme.surfaceContainerLowest

/** The screen behind the groups, one step darker than the rows. */
@Composable
@ReadOnlyComposable
fun settingsBackground(): Color = MaterialTheme.colorScheme.surfaceContainer

/**
 * Which of the theme's container colours an icon's circle takes. Android's
 * Settings gives each section its own colour; here they are the theme's
 * tonal roles, so they follow the wallpaper like everything else.
 */
enum class IconTone { Primary, Secondary, Tertiary, Error }

/** The icon at the start of a settings row, in a tonal circle. */
@Composable
fun SettingsIcon(icon: ImageVector, tone: IconTone = IconTone.Primary) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (tone) {
        IconTone.Primary -> colors.primaryContainer to colors.onPrimaryContainer
        IconTone.Secondary -> colors.secondaryContainer to colors.onSecondaryContainer
        IconTone.Tertiary -> colors.tertiaryContainer to colors.onTertiaryContainer
        IconTone.Error -> colors.errorContainer to colors.onErrorContainer
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(40.dp).background(container, CircleShape)
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
    }
}
