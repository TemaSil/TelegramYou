package com.telegramyou.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.telegramyou.app.telegram.model.TelegramUser
import com.telegramyou.app.ui.components.AvatarBubble

/**
 * The account this app is signed in as.
 *
 * Read-only, and that is the honest state of things rather than a design
 * choice: `TelegramClient` can say who you are and cannot yet change it.
 * Editing a name, a bio or a username is a TDLib call this project has not
 * made, and it stays in ROADMAP.md under settings and profile. A screen of
 * editable fields that silently did nothing would be worse than one that
 * shows what it knows.
 *
 * Content rather than a screen of its own, because it is a tab inside Home:
 * the bar and the window insets belong to the host.
 */
@Composable
fun ProfileContent(
    me: TelegramUser?,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
    ) {
        if (me == null) {
            // Before the client has answered. Not an error and not an empty
            // state: the account is on its way.
            ListItem(headlineContent = { Text("Loading your account…") })
            return@Column
        }

        Spacer(Modifier.height(24.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AvatarBubble(title = me.displayName, seed = me.avatarColor, size = 96.dp)
            Text(
                me.displayName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(24.dp))
        HorizontalDivider()

        me.username?.takeIf { it.isNotBlank() }?.let { username ->
            ListItem(
                headlineContent = { Text("@$username") },
                supportingContent = { Text("Username") },
                leadingContent = {
                    Icon(Icons.Rounded.AlternateEmail, contentDescription = null)
                }
            )
        }

        me.phoneNumber?.takeIf { it.isNotBlank() }?.let { phone ->
            ListItem(
                headlineContent = { Text(phone) },
                supportingContent = { Text("Phone") },
                leadingContent = {
                    Icon(Icons.Rounded.Phone, contentDescription = null)
                }
            )
        }

        if (me.isPremium) {
            ListItem(
                headlineContent = { Text("Telegram Premium") },
                leadingContent = {
                    Icon(
                        Icons.Rounded.WorkspacePremium,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            )
        }
    }
}
