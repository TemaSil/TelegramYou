package com.telegramyou.app.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.telegramyou.app.telegram.model.ChatPreview
import com.telegramyou.app.ui.home.RowPosition
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * One chat in the list.
 *
 * A [ListItem], not a hand-built Row. The previous version laid out its own
 * paddings, heights and text styles and then switched Material's press
 * feedback off with `indication = null` — so a row lit up nowhere on touch.
 * ListItem brings the spec's metrics and the ripple back, and the unread
 * count is a [Badge] rather than a Box with a fifty-percent corner radius.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatListRow(
    chat: ChatPreview,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Where this row sits in its container, which decides which of its
     * corners are rounded. A row on its own keeps all four; a run shares one
     * container and only the ends are cut.
     */
    position: RowPosition = RowPosition.Single,
    onMutedChange: ((Boolean) -> Unit)? = null
) {
    var menuOpen by remember { mutableStateOf(false) }
    val corner = 20.dp
    val shape = RoundedCornerShape(
        topStart = if (position.roundedTop) corner else 0.dp,
        topEnd = if (position.roundedTop) corner else 0.dp,
        bottomStart = if (position.roundedBottom) corner else 0.dp,
        bottomEnd = if (position.roundedBottom) corner else 0.dp
    )

    Box {
    Column {
    ListItem(
        modifier = modifier
            .clip(shape)
            .combinedClickable(
                onClick = onClick,
                // Long press only where there is something to offer, so a row
                // without actions does not grow a menu with nothing in it.
                onLongClick = if (onMutedChange != null) {
                    { menuOpen = true }
                } else {
                    null
                }
            ),
        // One colour for every row, pinned or not. The pinned ones used to
        // be tinted, which was a second way of saying what their own
        // container already says — and it made the group look striped rather
        // than whole.
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
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

    // Inside a container, not under it: the divider separates two rows that
    // share a surface, so it stops short of the ends where the container's
    // own edge already does the separating.
    if (!position.roundedBottom) {
        HorizontalDivider(
            modifier = Modifier.padding(start = 76.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
    }

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
