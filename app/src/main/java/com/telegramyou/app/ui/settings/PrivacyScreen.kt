package com.telegramyou.app.ui.settings

import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.telegramyou.app.telegram.model.PrivacyAudience
import com.telegramyou.app.telegram.model.PrivacyRules
import com.telegramyou.app.telegram.model.PrivacySetting

/**
 * Telegram's privacy rules, each a list item saying who it is set to, and
 * each changed through Material's radio-button dialog, where picking an
 * option is the confirmation. Grouped the way the questions are asked:
 * what others can see, and who can reach this account.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PrivacyScreen(
    state: PrivacyUiState,
    onBack: () -> Unit,
    onEdit: (PrivacySetting) -> Unit,
    onDismiss: () -> Unit,
    onAudienceChosen: (PrivacySetting, PrivacyAudience) -> Unit,
    onMessageShown: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onMessageShown()
    }

    state.editing?.let { setting ->
        val current = state.rules[setting]
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(setting.question) },
            text = {
                Column {
                    setting.audiences.forEach { audience ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = current?.audience == audience,
                                    role = Role.RadioButton,
                                    onClick = { onAudienceChosen(setting, audience) }
                                )
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(selected = current?.audience == audience, onClick = null)
                            Text(audience.label, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                    // Exceptions are kept, not edited here; say so, or a
                    // change of audience would look as if it overrode them.
                    if (current?.exceptions?.isNotEmpty() == true) {
                        Text(
                            "Exceptions for particular people stay as they are.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        )
    }

    Scaffold(
        containerColor = settingsBackground(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Privacy") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = settingsBackground())
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Two groups, as Android's Settings would draw them.
            item(key = "seen") {
                SettingsGroup("Who can see") {
                    SEEN.forEach { setting -> privacyRow(setting, state.rules[setting], onEdit) }
                }
            }
            item(key = "reach") {
                SettingsGroup("Who can reach me") {
                    REACH.forEach { setting -> privacyRow(setting, state.rules[setting], onEdit) }
                }
            }
        }
    }
}

private fun SettingsGroupScope.privacyRow(
    setting: PrivacySetting,
    rules: PrivacyRules?,
    onEdit: (PrivacySetting) -> Unit
) = link(
    title = setting.title,
    summary = rules?.summary ?: "Could not be read",
    onClick = { if (rules != null) onEdit(setting) }
)

private val SEEN = listOf(
    PrivacySetting.PhoneNumber,
    PrivacySetting.FindByNumber,
    PrivacySetting.LastSeen,
    PrivacySetting.ProfilePhoto,
    PrivacySetting.Bio,
    PrivacySetting.Forwards
)

private val REACH = listOf(PrivacySetting.Calls, PrivacySetting.Invites)
