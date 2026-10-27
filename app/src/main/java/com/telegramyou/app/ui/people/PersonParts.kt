package com.telegramyou.app.ui.people

import com.telegramyou.app.i18n.tr

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.telegramyou.app.telegram.model.PersonProfile
import com.telegramyou.app.telegram.model.firstNameOf
import com.telegramyou.app.ui.auth.PhoneEntry
import com.telegramyou.app.ui.icons.Symbols

/**
 * Who somebody is: their number, username, bio and id, one stock `ListItem`
 * each, the value as the headline and what it is as the overline — the
 * shape the invite link on a group's info screen already has, so a private
 * chat's info and a group's read alike.
 *
 * Only what they have: no row says "no username". Tapping a number or a
 * username copies it, which is all there is to do with one on a phone that
 * is already in the chat; [onCopy] is told what was copied, for the snackbar.
 */
fun LazyListScope.personRows(profile: PersonProfile, onCopy: (what: String, text: String) -> Unit) {
    val user = profile.user
    user.phoneNumber?.takeIf { it.isNotBlank() }?.let { phone ->
        item(key = "person-phone") {
            val shown = PhoneEntry.format(phone)
            DetailRow(Symbols.Phone, tr("Mobile", "Мобильный"), shown) { onCopy(tr("Phone number", "Номер телефона"), shown) }
        }
    }
    user.username?.takeIf { it.isNotBlank() }?.let { handle ->
        item(key = "person-username") {
            DetailRow(Symbols.AlternateEmail, tr("Username", "Имя пользователя"), "@$handle") { onCopy(tr("Username", "Имя пользователя"), "@$handle") }
        }
    }
    user.bio.takeIf { it.isNotBlank() }?.let { bio ->
        item(key = "person-bio") {
            DetailRow(Symbols.Info, tr("Bio", "О себе"), bio, onClick = null)
        }
    }
    // Their birthday where they show it, with their age when the year is
    // there too; on the day itself the row says so.
    user.birthdate?.let { birthdate ->
        item(key = "person-birthday") {
            val today = java.time.LocalDate.now()
            val label = if (birthdate.fallsOn(today)) tr("Birthday · today", "День рождения · сегодня") else tr("Birthday", "День рождения")
            DetailRow(Symbols.Cake, label, birthdate.line(today), onClick = null)
        }
    }
    // Last and quiet, as the forks show it: the number Telegram knows them
    // by, which a bot or a support chat asks for and nothing else in the
    // app says. Copied on a tap like the rest.
    user.id.takeIf { it > 0 }?.let { id ->
        item(key = "person-id") {
            DetailRow(Symbols.Tag, "ID", id.toString()) { onCopy("ID", id.toString()) }
        }
    }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String, onClick: (() -> Unit)?) {
    ListItem(
        headlineContent = { Text(value) },
        overlineContent = { Text(label) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    )
}

/**
 * Block, or unblock — in the error colour when it takes something away,
 * plain when it gives it back.
 */
fun LazyListScope.blockRow(profile: PersonProfile, onClick: () -> Unit) {
    item(key = "person-block") {
        val blocking = !profile.isBlocked
        val error = MaterialTheme.colorScheme.error
        ListItem(
            headlineContent = {
                Text(
                    when {
                        blocking && profile.isBot -> tr("Block bot", "Заблокировать бота")
                        blocking -> tr("Block user", "Заблокировать")
                        profile.isBot -> tr("Unblock bot", "Разблокировать бота")
                        else -> tr("Unblock user", "Разблокировать")
                    }
                )
            },
            leadingContent = { Icon(Symbols.Block, contentDescription = null) },
            colors = if (blocking) {
                ListItemDefaults.colors(headlineColor = error, leadingIconColor = error)
            } else {
                ListItemDefaults.colors()
            },
            modifier = Modifier.clickable(onClick = onClick)
        )
    }
}

/**
 * The yes before a block. Unblocking asks nothing: it gives something back,
 * and a dialog in front of that is a step for its own sake.
 */
@Composable
fun BlockDialog(profile: PersonProfile, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val name = firstNameOf(profile.user.displayName)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Block $name?", "Заблокировать $name?")) },
        text = {
            Text(
                if (profile.isBot) {
                    tr("The bot will not be able to message you. You can unblock it later.", "Бот не сможет вам писать. Разблокировать можно позже.")
                } else {
                    tr("$name will not be able to message or call you. You can unblock them later.", "$name не сможет вам писать и звонить. Разблокировать можно позже.")
                }
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text(tr("Block", "Заблокировать")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "Отмена")) } }
    )
}
