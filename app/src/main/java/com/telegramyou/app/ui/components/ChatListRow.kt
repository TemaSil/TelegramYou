package com.telegramyou.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.MarkChatRead
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
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
 * The component brings the spec's metrics and the ripple back,
 * `segmentedColors` gives the run its container tone, and `onLongClick` is
 * carried by the component itself, so the mute menu needs no
 * `combinedClickable` around it.
 *
 * `ListItemDefaults.segmentedShapes(index, count)` gives the run its corners —
 * each row its own rounded container, with the panel behind showing through
 * between them. That panel is drawn by the list, not by the row: see
 * HomeScreen.
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
    onMutedChange: ((Boolean) -> Unit)? = null,
    /** Pin or unpin. Null where the row is not the chat list's own. */
    onPinnedChange: ((Boolean) -> Unit)? = null,
    /**
     * Renames the right-swipe, which the archive uses to say Unarchive.
     *
     * A label rather than a second callback: the gesture and its wiring
     * are the same, only what it means to that screen differs, and two
     * callbacks would let a caller set one without the other.
     */
    swipeStartLabel: String? = null,
    /** Clear the unread badge without opening the chat. */
    onMarkRead: (() -> Unit)? = null,
    /** Move into the archive, or back out. */
    onArchivedChange: ((Boolean) -> Unit)? = null
) {
    var menuOpen by remember { mutableStateOf(false) }
    val shapes = ListItemDefaults.segmentedShapes(index = index, count = count)

    // Actions, not deletions, so confirmValueChange does the work and then
    // refuses the change: the row springs back and redraws from whatever the
    // client says next. Letting it dismiss would take the row off a list it is
    // still a member of and put it back a moment later when the flow updates,
    // which is a flicker rather than a result.
    val swipe = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> onPinnedChange?.invoke(!chat.isPinned)
                SwipeToDismissBoxValue.EndToStart -> onMutedChange?.invoke(!chat.isMuted)
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false
        }
    )

    Box {
        SwipeToDismissBox(
            state = swipe,
            modifier = modifier,
            // Only where there is something to do. A row that swipes open on
            // an action it cannot perform is a row that lies, and the search
            // results reuse this one.
            enableDismissFromStartToEnd = onPinnedChange != null,
            enableDismissFromEndToStart = onMutedChange != null,
            backgroundContent = {
                SwipeAction(
                    direction = swipe.dismissDirection,
                    isPinned = chat.isPinned,
                    isMuted = chat.isMuted,
                    startLabel = swipeStartLabel,
                    shape = shapes.shape
                )
            }
        ) {
            SegmentedListItem(
                onClick = onClick,
                shapes = shapes,
                // Long press only where there is something to offer, so a row
                // without actions does not grow a menu with nothing in it.
                onLongClick = if (onMutedChange != null) {
                    { menuOpen = true }
                } else {
                    null
                },
                // One colour for every row, pinned or not. The pinned ones used
                // to be tinted, which was a second way of saying what their own
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
                        // Pinned and muted are states of the chat, so they sit
                        // with the preview text rather than competing with the
                        // title.
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
                            // A muted chat still counts, but must not shout:
                            // the badge drops to a surface colour rather than
                            // primary.
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
                content = {
                    Text(
                        text = chat.title,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
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
                if (onArchivedChange != null) {
                    // In the menu, not on a third swipe direction: there are
                    // two, and both are already spoken for.
                    DropdownMenuItem(
                        text = { Text(if (chat.isArchived) "Unarchive" else "Archive") },
                        leadingIcon = {
                            Icon(
                                if (chat.isArchived) Icons.Rounded.Unarchive
                                else Icons.Rounded.Archive,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            onArchivedChange(!chat.isArchived)
                            menuOpen = false
                        }
                    )
                }
                if (onMarkRead != null && chat.unreadCount > 0) {
                    // In the menu rather than on a third swipe direction, which
                    // there is no room for, and hidden when there is nothing to
                    // clear — an entry that would do nothing is worse than none.
                    DropdownMenuItem(
                        text = { Text("Mark as read") },
                        leadingIcon = {
                            Icon(Icons.Rounded.MarkChatRead, contentDescription = null)
                        },
                        onClick = {
                            onMarkRead()
                            menuOpen = false
                        }
                    )
                }
            }
        }
    }
}

/**
 * What shows behind a row being swiped.
 *
 * Takes the row's own shape so the colour appears inside the same rounded
 * container rather than as a rectangle poking out from under it, and the icon
 * sits on the side the finger came from — which is what says the gesture is
 * going to do something rather than move the row somewhere.
 */
@Composable
private fun SwipeAction(
    direction: SwipeToDismissBoxValue,
    isPinned: Boolean,
    isMuted: Boolean,
    startLabel: String?,
    shape: Shape
) {
    val settled = direction == SwipeToDismissBoxValue.Settled
    // Animated, so the colour arrives with the gesture rather than appearing
    // the instant a finger moves. The effects spring, because this is a colour
    // and not a position.
    val container by animateColorAsState(
        targetValue = when (direction) {
            SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.tertiaryContainer
            SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.secondaryContainer
            SwipeToDismissBoxValue.Settled -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "swipeBackground"
    )
    val content = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .background(container)
            .padding(horizontal = 24.dp),
        contentAlignment = if (direction == SwipeToDismissBoxValue.StartToEnd) {
            Alignment.CenterStart
        } else {
            Alignment.CenterEnd
        }
    ) {
        if (settled) return@Box
        val icon: ImageVector
        val label: String
        if (direction == SwipeToDismissBoxValue.StartToEnd) {
            // The archive renames this one, and renaming it changes the icon
            // too: a pin over "Unarchive" would say the opposite of the word.
            icon = if (startLabel == null) Icons.Outlined.PushPin else Icons.Rounded.Unarchive
            label = startLabel ?: if (isPinned) "Unpin" else "Pin"
        } else {
            icon = if (isMuted) Icons.Rounded.NotificationsActive
            else Icons.Rounded.NotificationsOff
            label = if (isMuted) "Unmute" else "Mute"
        }
        Icon(icon, contentDescription = label, tint = content)
    }
}
