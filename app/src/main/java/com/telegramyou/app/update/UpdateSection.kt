package com.telegramyou.app.update

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The app's one update checker, for the settings row that shows it.
 *
 * A composition local rather than a parameter, because the settings content
 * has two homes — the Home tab and its own route — and threading one more
 * object through both, and through Home's thirty parameters, to reach a
 * single row would be plumbing for its own sake. Null in previews and tests,
 * where the row is simply not drawn.
 */
val LocalAppUpdates = staticCompositionLocalOf<AppUpdates?> { null }

/**
 * "Check for updates", and whatever that turns into: a newer version to
 * download, a wavy bar while it comes, and the installer at the end.
 *
 * One row that changes rather than a dialog, so leaving settings mid-download
 * loses nothing — the state lives in [AppUpdates], not here.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UpdateSection() {
    val updates = LocalAppUpdates.current ?: return
    val state by updates.state.collectAsStateWithLifecycle()
    val installed = "Version ${updates.installed}"

    when (val current = state) {
        UpdateState.Idle, UpdateState.UpToDate, is UpdateState.Failed -> ListItem(
            headlineContent = { Text("Check for updates") },
            supportingContent = {
                Text(
                    when (current) {
                        UpdateState.UpToDate -> "$installed — the newest there is"
                        is UpdateState.Failed -> current.message
                        else -> installed
                    },
                    color = if (current is UpdateState.Failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            },
            leadingContent = { Icon(Icons.Rounded.SystemUpdate, contentDescription = null) },
            modifier = Modifier.clickable { updates.check() }
        )
        UpdateState.Checking -> ListItem(
            headlineContent = { Text("Checking for updates…") },
            supportingContent = { Text(installed) },
            leadingContent = { LoadingIndicator() }
        )
        is UpdateState.Available -> ListItem(
            headlineContent = { Text("Version ${current.release.version} is out") },
            supportingContent = { Text("You have ${updates.installed}") },
            leadingContent = { Icon(Icons.Rounded.SystemUpdate, contentDescription = null) },
            trailingContent = {
                FilledTonalButton(onClick = updates::download) {
                    Icon(Icons.Rounded.Download, contentDescription = null)
                    Text(
                        listOf("Update", sizeLabel(current.release.size))
                            .filter { it.isNotEmpty() }
                            .joinToString(" · "),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        )
        is UpdateState.Downloading -> Column(Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text("Downloading ${current.release.version}") },
                supportingContent = {
                    Text(current.progress?.let { "${(it * 100).toInt()}%" } ?: "Starting…")
                },
                leadingContent = { Icon(Icons.Rounded.Download, contentDescription = null) }
            )
            // Expressive's own progress bar: the wave is the movement, and a
            // download is the one place in this app that waits long enough
            // to deserve it.
            val progress = current.progress
            if (progress == null) {
                LinearWavyProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            } else {
                LinearWavyProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
        is UpdateState.Ready -> ListItem(
            headlineContent = { Text("Version ${current.release.version} is ready") },
            supportingContent = { Text("Android will ask to confirm the install") },
            leadingContent = { Icon(Icons.Rounded.InstallMobile, contentDescription = null) },
            trailingContent = {
                FilledTonalButton(onClick = updates::install) { Text("Install") }
            }
        )
    }
}
