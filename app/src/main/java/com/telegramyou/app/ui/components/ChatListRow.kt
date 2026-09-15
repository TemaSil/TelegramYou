package com.telegramyou.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.telegramyou.app.telegram.model.ChatPreview

/**
 * One chat in the list.
 *
 * A [ListItem], not a hand-built Row. The previous version laid out its own
 * paddings, heights and text styles and then switched Material's press
 * feedback off with `indication = null` — so a row lit up nowhere on touch.
 * ListItem brings the spec's metrics and the ripple back, and the unread
 * count is a [Badge] rather than a Box with a fifty-percent corner radius.
 */
@Composable
fun ChatListRow(
    chat: ChatPreview,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = if (chat.isPinned) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest
            }
        ),
        leadingContent = {
            AvatarBubble(
                title = chat.title,
                seed = chat.avatarColor,
                showOnline = chat.isOnline && !chat.isChannel && !chat.isGroup
            )
        },
        headlineContent = {
            Text(
                text = chat.title,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
        }
    )
}
