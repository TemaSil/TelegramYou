package com.telegramyou.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.telegramyou.app.telegram.model.TelegramUser
import com.telegramyou.app.ui.components.AvatarBubble
import com.telegramyou.app.ui.home.ProfileUiState

/**
 * The account this app is signed in as, and the three things about it that can
 * be changed: the name, the bio and the username.
 *
 * There is no "edit" button and no second screen behind one. The fields *are*
 * the profile — they open filled with what the account says, and the save
 * button turns on the moment something differs from it. A read-only screen
 * with a pencil that opens an editable copy of itself is two screens to draw
 * and two to keep in step, for a form of four fields.
 *
 * Nothing here decides what is valid or whether saving is worth offering. That
 * lives in `:core` with tests, because a username rule is the kind of thing
 * that fails quietly — the server returns a generic error with no field
 * attached — and arrives as [ProfileUiState] already answered.
 *
 * Content rather than a screen of its own, because it is a tab inside Home:
 * the bar and the window insets belong to the host.
 */
@Composable
fun ProfileContent(
    me: TelegramUser?,
    profile: ProfileUiState,
    onDraftChange: (ProfileDraft) -> Unit,
    onSave: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            // The bio is the last field and the keyboard is tall. Without this
            // the thing being typed into sits behind it.
            .imePadding()
    ) {
        if (me == null) {
            // Before the client has answered. Not an error and not an empty
            // state: the account is on its way.
            ListItem(headlineContent = { Text("Loading your account…") })
            return@Column
        }

        val draft = profile.draft

        Spacer(Modifier.height(24.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AvatarBubble(title = me.displayName, seed = me.avatarColor, size = 96.dp, photoPath = me.photoPath)
            // The account's name, not the draft's. This is what Telegram
            // currently thinks you are called, and it changing is the
            // confirmation that a save went through.
            Text(
                me.displayName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(24.dp))

        SectionHeader("Your name")
        ProfileTextField(
            value = draft.firstName,
            onValueChange = { onDraftChange(draft.copy(firstName = it)) },
            label = "First name",
            error = profile.problemFor(ProfileField.FirstName),
            enabled = !profile.isSaving
        )
        ProfileTextField(
            value = draft.lastName,
            onValueChange = { onDraftChange(draft.copy(lastName = it)) },
            label = "Last name",
            error = profile.problemFor(ProfileField.LastName),
            enabled = !profile.isSaving
        )

        SectionHeader("About")
        ProfileTextField(
            value = draft.bio,
            onValueChange = { onDraftChange(draft.copy(bio = it)) },
            label = "Bio",
            error = profile.problemFor(ProfileField.Bio),
            enabled = !profile.isSaving,
            singleLine = false,
            // A counter rather than silence. Seventy characters is short
            // enough that running out is a normal thing to do, and a limit
            // discovered by being refused is a limit badly explained.
            counter = "${draft.bio.trim().length}/$MAX_BIO_LENGTH"
        )

        SectionHeader("Username")
        ProfileTextField(
            value = draft.username,
            onValueChange = { onDraftChange(draft.copy(username = it)) },
            label = "Username",
            error = profile.problemFor(ProfileField.Username),
            enabled = !profile.isSaving,
            prefix = "@",
            counter = if (draft.username.isBlank()) {
                "People can find you by username. Leave it empty to have none"
            } else {
                null
            }
        )

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onSave,
            enabled = profile.canSave,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(56.dp)
        ) {
            if (profile.isSaving) {
                // Inside the button rather than over the screen: one field is
                // being sent, and an overlay would suggest the whole app is
                // busy.
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Save", style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.height(24.dp))
        HorizontalDivider()

        // What cannot be changed from here. The phone number needs a code sent
        // to the new one, which is its own flow, and Premium is a purchase.
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
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * One field of the form.
 *
 * Named ProfileTextField and not ProfileField because [ProfileField] is the
 * enum naming which field this is, and the two live in the same package — one
 * in `:core`, one here.
 *
 * [error] wins over [counter] in the supporting slot, because a field that is
 * wrong has something more useful to say than how long it is.
 */
@Composable
private fun ProfileTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    enabled: Boolean,
    singleLine: Boolean = true,
    prefix: String? = null,
    counter: String? = null
) {
    val supporting = error ?: counter
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        label = { Text(label) },
        enabled = enabled,
        singleLine = singleLine,
        // isError rather than a red Text underneath: this recolours the
        // border and the label together and announces the message to a screen
        // reader as the field's own error.
        isError = error != null,
        supportingText = supporting?.let { { Text(it) } },
        prefix = prefix?.let { { Text(it) } }
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}
