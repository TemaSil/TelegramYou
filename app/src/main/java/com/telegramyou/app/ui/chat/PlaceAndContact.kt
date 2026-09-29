package com.telegramyou.app.ui.chat

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.telegramyou.app.telegram.model.ContactContent
import com.telegramyou.app.telegram.model.LocationContent
import com.telegramyou.app.telegram.model.coordinatesLabel
import com.telegramyou.app.telegram.model.geoUri
import com.telegramyou.app.ui.auth.PhoneEntry
import com.telegramyou.app.ui.icons.Symbols

/** The bubble's own ink: onPrimary in ours, onSurface in theirs. */
@Composable
private fun ink(outgoing: Boolean): Color =
    if (outgoing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

/**
 * A contact card in a message: who, their number, and what can be done —
 * write to them where they are on Telegram, keep them as a contact.
 */
@Composable
internal fun ContactCard(
    contact: ContactContent,
    outgoing: Boolean,
    onOpen: () -> Unit,
    onAdd: () -> Unit
) {
    val color = ink(outgoing)
    Column(modifier = Modifier.widthIn(min = 220.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .background(color.copy(alpha = 0.14f), CircleShape)
            ) {
                Icon(Symbols.Person, contentDescription = null, tint = color)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    contact.displayName.ifBlank { "Contact" },
                    color = color,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    PhoneEntry.format(contact.phoneNumber),
                    color = color.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Spacer(Modifier.size(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (contact.isOnTelegram) {
                FilledTonalButton(onClick = onOpen) { Text("View") }
            }
            FilledTonalButton(onClick = onAdd) { Text("Add") }
        }
    }
}

/**
 * A place in a message: its name and address for a venue, its coordinates
 * otherwise, and Open in Maps — whichever maps app the phone has, through
 * a `geo:` link. No map is drawn: that needs a maps provider, which is a
 * decision about somebody else's service, not one this card makes.
 */
@Composable
internal fun LocationCard(location: LocationContent, outgoing: Boolean) {
    val context = LocalContext.current
    val color = ink(outgoing)
    Column(modifier = Modifier.widthIn(min = 220.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .background(color.copy(alpha = 0.14f), CircleShape)
            ) {
                Icon(Symbols.LocationOn, contentDescription = null, tint = color)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    location.title.ifBlank { if (location.isLive) "Live location" else "Location" },
                    color = color,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    location.address.ifBlank { coordinatesLabel(location) },
                    color = color.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.size(10.dp))
        FilledTonalButton(
            onClick = {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(geoUri(location))))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, "No maps app to open it", Toast.LENGTH_SHORT).show()
                }
            }
        ) { Text("Open in Maps") }
    }
}
