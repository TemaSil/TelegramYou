package com.telegramyou.app.ui.profile

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.telegramyou.app.telegram.model.TelegramUser
import com.telegramyou.app.ui.auth.QrCode
import com.telegramyou.app.ui.common.rememberTextCopier
import com.telegramyou.app.ui.components.AvatarBubble
import com.telegramyou.app.ui.home.ProfileUiState
import com.telegramyou.app.ui.settings.IconTone
import com.telegramyou.app.ui.settings.SettingsGroup
import com.telegramyou.app.ui.settings.SettingsIcon
import com.telegramyou.app.ui.settings.SettingsItem
import com.telegramyou.app.ui.settings.settingsBackground

/**
 * The account this app is signed in as, as the official client shows it:
 * the photo large with the name and status under it, the three things done
 * from here as one Expressive [ButtonGroup] — a new photo, editing, Settings
 * — and what people can find you by as a segmented list. A QR code to share
 * the profile is in the corner, and the less common actions are in the
 * overflow menu.
 *
 * Editing is a full-screen dialog behind Edit rather than the fields on the
 * page itself, which is what this used to be: a profile that is a form reads
 * as a settings screen, not as you.
 *
 * Nothing here decides what is valid or whether saving is worth offering —
 * that lives in `:core` with tests, and arrives as [ProfileUiState].
 *
 * Content rather than a screen of its own, because it is a tab inside Home.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProfileContent(
    me: TelegramUser?,
    profile: ProfileUiState,
    onDraftChange: (ProfileDraft) -> Unit,
    onSave: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onPhotoPicked: (String) -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    if (me == null) {
        // Before the client has answered. Not an error and not an empty
        // state: the account is on its way.
        Box(modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
            Text("Loading your account…")
        }
        return
    }
    var editing by rememberSaveable { mutableStateOf(false) }
    var showingQr by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val copy = rememberTextCopier()
    val link = me.username?.takeIf { it.isNotBlank() }?.let { "https://t.me/$it" }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { onPhotoPicked(it.toString()) }
    }
    val pickPhoto: () -> Unit = {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    // A save that went through closes the editor; the snackbar says so.
    LaunchedEffect(profile.savedCount) {
        if (profile.savedCount > 0) editing = false
    }
    if (editing) {
        ProfileEditor(
            profile = profile,
            onDraftChange = onDraftChange,
            onSave = onSave,
            onDismiss = { editing = false }
        )
    }
    if (showingQr) {
        ProfileQr(
            me = me,
            link = link,
            onSetUsername = {
                showingQr = false
                editing = true
            },
            onDismiss = { showingQr = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            IconButton(onClick = { showingQr = true }) {
                Icon(Icons.Rounded.QrCode2, contentDescription = "QR code")
            }
            Spacer(Modifier.weight(1f))
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "More")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(if (link == null) "Set a username" else "Change username") },
                        leadingIcon = { Icon(Icons.Rounded.AlternateEmail, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            editing = true
                        }
                    )
                    if (link != null) {
                        DropdownMenuItem(
                            text = { Text("Copy link to profile") },
                            leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                copy(link)
                            }
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AvatarBubble(
                title = me.displayName,
                seed = me.avatarColor,
                size = 120.dp,
                photoPath = me.photoPath,
                modifier = Modifier.clip(CircleShape).clickable(onClick = pickPhoto)
            )
            Spacer(Modifier.height(16.dp))
            // The account's name, not the draft's: what Telegram currently
            // thinks you are called, so it changing is the proof of a save.
            Text(me.displayName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text("online", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(20.dp))

        // Three actions that belong together — an Expressive ButtonGroup,
        // where the button under the finger widens and its neighbours give
        // way. Every item carries weight 1: without it the group sizes
        // each button to its label, three labelled buttons overflow a phone,
        // and the alpha's overflow path (ButtonGroup.kt:712) builds
        // constraints narrower than fillMaxWidth's minimum and throws. With
        // weights the row is divided, everything fits, and that path is
        // never taken. See ROADMAP, "ButtonGroup".
        val actions = listOf(
            Triple("Set photo", Icons.Rounded.AddAPhoto, pickPhoto),
            Triple("Edit", Icons.Rounded.Edit, { editing = true }),
            Triple("Settings", Icons.Rounded.Settings, onOpenSettings)
        )
        ButtonGroup(
            overflowIndicator = { menuState -> ButtonGroupDefaults.OverflowIndicator(menuState = menuState) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            actions.forEach { (label, icon, onClick) ->
                customItem(
                    buttonGroupContent = {
                        val source = remember { MutableInteractionSource() }
                        ProfileAction(
                            label = label,
                            icon = icon,
                            onClick = onClick,
                            interactionSource = source,
                            modifier = Modifier.weight(1f).animateWidth(source)
                        )
                    },
                    // Only if the row ever cannot fit them; with the weights
                    // above it always can.
                    menuContent = {
                        DropdownMenuItem(
                            text = { Text(label) },
                            leadingIcon = { Icon(icon, contentDescription = null) },
                            onClick = onClick
                        )
                    }
                )
            }
        }

        // What people find you by, the value first and what it is under it,
        // as the official client and Android's contact card both put it.
        SettingsGroup {
            me.phoneNumber?.takeIf { it.isNotBlank() }?.let { phone ->
                custom { index, count ->
                    SettingsItem(
                        index = index,
                        count = count,
                        title = phone,
                        summary = "Mobile",
                        leading = { SettingsIcon(Icons.Rounded.Phone) },
                        onClick = { copy(phone) }
                    )
                }
            }
            custom { index, count ->
                SettingsItem(
                    index = index,
                    count = count,
                    title = me.username?.takeIf { it.isNotBlank() }?.let { "@$it" } ?: "No username",
                    summary = if (link != null) "Username · tap to copy the link" else "Username · set one so people can find you",
                    leading = { SettingsIcon(Icons.Rounded.AlternateEmail) },
                    onClick = { if (link != null) copy(link) else editing = true }
                )
            }
            custom { index, count ->
                SettingsItem(
                    index = index,
                    count = count,
                    title = me.bio.ifBlank { "Add a few words about yourself" },
                    summary = "Bio",
                    leading = { SettingsIcon(Icons.Rounded.Info) },
                    onClick = { editing = true }
                )
            }
        }
        if (me.isPremium) {
            SettingsGroup {
                custom { index, count ->
                    SettingsItem(
                        index = index,
                        count = count,
                        title = "Telegram Premium",
                        leading = { SettingsIcon(Icons.Rounded.WorkspacePremium, IconTone.Tertiary) },
                        onClick = {}
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    interactionSource: MutableInteractionSource,
    modifier: Modifier
) {
    FilledTonalButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        contentPadding = PaddingValues(vertical = 12.dp),
        interactionSource = interactionSource,
        modifier = modifier
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * Name, bio and username in Material's full-screen dialog, Save in its bar.
 * The rules for what can be saved are `:core`'s, arriving in [profile].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileEditor(
    profile: ProfileUiState,
    onDraftChange: (ProfileDraft) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val draft = profile.draft
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            containerColor = settingsBackground(),
            topBar = {
                TopAppBar(
                    title = { Text("Edit profile") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = "Close") }
                    },
                    actions = {
                        if (profile.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(end = 16.dp).size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            TextButton(onClick = onSave, enabled = profile.canSave) { Text("Save") }
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    // The bio is the last field and the keyboard is tall.
                    .imePadding()
                    .padding(vertical = 8.dp)
            ) {
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
                ProfileTextField(
                    value = draft.bio,
                    onValueChange = { onDraftChange(draft.copy(bio = it)) },
                    label = "Bio",
                    error = profile.problemFor(ProfileField.Bio),
                    enabled = !profile.isSaving,
                    singleLine = false,
                    // A counter rather than silence: seventy characters is
                    // short enough that running out is a normal thing to do.
                    counter = "${draft.bio.trim().length}/$MAX_BIO_LENGTH"
                )
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
            }
        }
    }
}

/**
 * The profile as a QR code: the photo, the code for its t.me link and the
 * username under it, and Share. Without a username there is no link to
 * encode, and the screen says so and offers to set one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileQr(me: TelegramUser, link: String?, onSetUsername: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            topBar = {
                TopAppBar(
                    title = { Text("QR code") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = "Close") }
                    }
                )
            }
        ) { padding ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        AvatarBubble(title = me.displayName, seed = me.avatarColor, size = 72.dp, photoPath = me.photoPath)
                        Spacer(Modifier.height(16.dp))
                        if (link != null) {
                            QrCode(content = link, size = 220.dp)
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "@${me.username.orEmpty().uppercase()}",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                "Set a username, and your QR code will open your profile for anyone who scans it",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                if (link != null) {
                    Button(
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, link)
                            context.startActivity(Intent.createChooser(send, null))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Share QR code")
                    }
                } else {
                    Button(onClick = onSetUsername, modifier = Modifier.fillMaxWidth()) { Text("Set a username") }
                }
            }
        }
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
            .padding(horizontal = 16.dp, vertical = 4.dp)
            // Named after its label, for TalkBack and the UI test alike.
            .semantics { contentDescription = label },
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
