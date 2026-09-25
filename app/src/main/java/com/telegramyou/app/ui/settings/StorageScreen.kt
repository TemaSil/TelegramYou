package com.telegramyou.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
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
import com.telegramyou.app.telegram.model.StorageKind
import com.telegramyou.app.telegram.model.bytesLabel

/**
 * What Telegram keeps on this phone, by kind, and clearing the kinds that
 * are not wanted. The figure at the top is the files alone; the database is
 * named under it but is not offered, since it is the chats themselves.
 *
 * Checkbox list items and one filled button. Material ships no chart, and
 * a bar drawn to stand in for one would be the kind of hand-made component
 * this client does not make: the sizes, largest first, say the same thing.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StorageScreen(
    state: StorageUiState,
    onBack: () -> Unit,
    onKindToggle: (StorageKind) -> Unit,
    onClearRequested: () -> Unit,
    onDismiss: () -> Unit,
    onClearConfirmed: () -> Unit,
    onMessageShown: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onMessageShown()
    }

    if (state.confirming) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Clear ${bytesLabel(state.selectedBytes)}?") },
            text = {
                Text(
                    "These files are only removed from this phone. They stay in " +
                        "Telegram and download again when they are opened."
                )
            },
            confirmButton = { TextButton(onClick = onClearConfirmed) { Text("Clear") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Data and storage") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            val usage = state.usage
            if (usage != null && usage.slices.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (state.isClearing) LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = onClearRequested,
                        enabled = !state.isClearing && state.selectedBytes > 0,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (state.selectedBytes > 0) "Clear ${bytesLabel(state.selectedBytes)}"
                            else "Nothing selected"
                        )
                    }
                }
            }
        }
    ) { padding ->
        val usage = state.usage
        if (usage == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item(key = "total") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                    Text(bytesLabel(usage.filesBytes), style = MaterialTheme.typography.displaySmall)
                    Text(
                        "Cached on this phone",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (usage.databaseBytes > 0) {
                        Text(
                            "Plus ${bytesLabel(usage.databaseBytes)} of chats, which stay",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (usage.slices.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "Nothing is cached yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                item(key = "heading") { SectionHeader("Clear from this phone") }
                items(usage.slices, key = { it.kind }) { slice ->
                    val checked = slice.kind in state.selected
                    ListItem(
                        headlineContent = { Text(slice.kind.label) },
                        supportingContent = { Text(filesLabel(slice.count)) },
                        leadingContent = { Checkbox(checked = checked, onCheckedChange = null) },
                        trailingContent = { Text(bytesLabel(slice.bytes)) },
                        // The whole row toggles, as a checkbox row does in every
                        // Android settings screen; the box itself takes no
                        // separate tap.
                        modifier = Modifier.toggleable(
                            value = checked,
                            enabled = !state.isClearing,
                            role = Role.Checkbox,
                            onValueChange = { onKindToggle(slice.kind) }
                        )
                    )
                }
            }
        }
    }
}

private fun filesLabel(count: Int): String = if (count == 1) "1 file" else "$count files"
