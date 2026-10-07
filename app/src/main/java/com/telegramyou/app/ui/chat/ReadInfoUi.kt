package com.telegramyou.app.ui.chat

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.telegramyou.app.telegram.model.Viewer
import com.telegramyou.app.ui.icons.Symbols
import java.util.Date

/**
 * When something was read, in words (2.0): "at 14:03" today, "yesterday at
 * 9:12", "on 8 July at 9:12" before that — the clock in the phone's own
 * 12- or 24-hour form.
 */
internal fun readTimeLabel(context: Context, date: Long): String {
    val clock = DateFormat.getTimeFormat(context).format(Date(date * 1000))
    return when (val day = dayLabel(date)) {
        "Today" -> "at $clock"
        "Yesterday" -> "yesterday at $clock"
        else -> "on $day at $clock"
    }
}

/** Who in a group has seen one's message, and when; opened from the message's menu (2.0). */
@Composable
internal fun SeenByDialog(viewers: List<Viewer>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Symbols.DoneAll, contentDescription = null) },
        title = { Text("Seen by ${viewers.size}") },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(viewers, key = { it.userId }) { viewer ->
                    ListItem(
                        headlineContent = { Text(viewer.name) },
                        supportingContent = { Text(readTimeLabel(context, viewer.date).replaceFirstChar { it.uppercase() }) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
