package com.telegramyou.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.telegramyou.app.telegram.model.ChatPreview

/**
 * One chat in the list.
 *
 * A [SegmentedListItem], not a hand-built Row. The first version laid out its
 * own paddings, heights and text styles and then switched Material's press
 * feedback off with `indication = null` — so a row lit up nowhere on touch.
 * The second kept the ListItem but computed its own corner radii from an
 * enum, which was the same mistake one layer up: Material already ships the
 * grouped list. `ListItemDefaults.segmentedShapes(index, count)` rounds the
 * ends of a run and squares its middle, `segmentedColors()` gives the run its
 * container tone, and `SegmentedListItem` carries `onLongClick` itself — so
 * the menu no longer needs `combinedClickable` wrapped round the row either.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChatListRow(
    chat: ChatPreview,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Where this row sits in its run and how long the run is. Material turns
     * the pair into corners; a row on its own (the default) keeps all four.
     */
    index: Int = 0,
    count: Int = 1,
    onMutedChange: ((Boolean) -> Unit)? = null
) {
    var menuOpen by remember { mutableStateOf(false) }

    Box {
        SegmentedListItem(
            onClick = onClick,
            shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
            modifier = modifier,
            // Long press only where there is something to offer, so a row
            // without actions does not grow a menu with nothing in it.
            onLongClick = if (onMutedChange != null) {
                { menuOpen = true }
            } else {
                null
            },
            // One colour for every row, pinned or not. The pinned ones used to
            // be tinted, which was a second way of saying what their own
            // container already says — and it made the group look striped
            // rather than whole.
            colors = ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
            ),
            leadingContent = {
                AvatarBubble(
                    title = chat.title,
                    seed = chat.avatarColor,
                    showOnline = chat.isOnline && !chat.isChannel && !chat.isGroup
                )
            },
            supportingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Pinned and muted are states of the chat, so they sit with
                    // the preview text rather than competing with the title.
                    if (chat.isPinned) {
                        Icon(
                            Icons.Outlined.PushPin,
                            contentDescription = "Pinned",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    if (chat.isMuted) {
                        Icon(
                            Icons.AutoMirrored.Outlined.VolumeOff,
                            contentDescription = "Muted",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = chat.lastMessage,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            trailingContent = {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = chat.timestampLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (chat.unreadCount > 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    if (chat.unreadCount > 0) {
                        // A muted chat still counts, but must not shout: the
                        // badge drops to a surface colour rather than primary.
                        Badge(
                            containerColor = if (chat.isMuted) {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            contentColor = if (chat.isMuted) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onPrimary
                            }
                        ) {
                            Text(chat.unreadCount.toString())
                        }
                    }
                }
            },
            headlineContent = {
                Text(
                    text = chat.title,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        )

        if (onMutedChange != null) {
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(if (chat.isMuted) "Unmute" else "Mute") },
                    leadingIcon = {
                        Icon(
                            if (chat.isMuted) Icons.Rounded.NotificationsActive
                            else Icons.Rounded.NotificationsOff,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        // The opposite of what is drawn, not of what the server
                        // last said: the row the finger is on is the one the
                        // person means.
                        onMutedChange(!chat.isMuted)
                        menuOpen = false
                    }
                )
            }
        }
    }
}
