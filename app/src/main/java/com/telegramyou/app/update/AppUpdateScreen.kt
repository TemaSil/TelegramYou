package com.telegramyou.app.update

import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.telegramyou.app.ui.settings.settingsBackground
import com.telegramyou.app.ui.settings.settingsRowColor

/**
 * The app's one update checker, for the screens that show it.
 *
 * A composition local rather than a parameter, because it is read from the
 * Settings row, the bottom bar's dot and this screen, and threading one more
 * object through Home's thirty parameters to reach them would be plumbing for
 * its own sake. Null in previews and tests, where those parts are not drawn.
 */
val LocalAppUpdates = staticCompositionLocalOf<AppUpdates?> { null }

/**
 * Settings → App update, laid out as Android's own System update screen: the
 * version and what is happening with it at the top, one button that does the
 * next thing, and below it what the update brings.
 *
 * The state lives in [AppUpdates], so leaving this screen mid-download loses
 * nothing and coming back finds it where it was.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppUpdateScreen(onBack: () -> Unit) {
    val updates = LocalAppUpdates.current
    val state = updates?.state?.collectAsStateWithLifecycle()?.value ?: UpdateState.Idle
    val installed = updates?.installed?.toString() ?: "—"
    // This build's own notes, shipped in its assets.
    val context = LocalContext.current
    val installedNotes = remember {
        runCatching {
            context.assets.open("whats-new.md").bufferedReader().use { parseWhatsNew(it.readText()) }
        }.getOrNull()
    }
    Scaffold(
        containerColor = settingsBackground(),
        topBar = {
            TopAppBar(
                title = { Text("App update") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = settingsBackground())
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "status") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = settingsRowColor()),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(24.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(72.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                        ) {
                            Icon(
                                if (state is UpdateState.Ready) Icons.Rounded.InstallMobile else Icons.Rounded.SystemUpdate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            when (state) {
                                is UpdateState.Available -> "Version ${state.release.version} is out"
                                is UpdateState.Downloading -> "Downloading ${state.release.version}"
                                is UpdateState.Ready -> "Version ${state.release.version} is ready"
                                UpdateState.Checking -> "Checking for updates…"
                                UpdateState.UpToDate -> "You're up to date"
                                else -> "TelegramYou $installed"
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            when (state) {
                                is UpdateState.Failed -> state.message
                                is UpdateState.Ready -> "Android will ask you to confirm the install"
                                is UpdateState.Available -> listOf("You have $installed", sizeLabel(state.release.size))
                                    .filter { it.isNotEmpty() }
                                    .joinToString(" · ")
                                else -> "Version $installed"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (state is UpdateState.Failed) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(20.dp))
                        when (state) {
                            UpdateState.Checking -> LoadingIndicator()
                            is UpdateState.Downloading -> {
                                // Expressive's own bar: a download is the one
                                // wait in this app long enough to deserve it.
                                val progress = state.progress
                                if (progress == null) {
                                    LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                                } else {
                                    LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    state.progress?.let { "${(it * 100).toInt()}%" } ?: "Starting…",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            is UpdateState.Available -> Button(
                                onClick = { updates?.download() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.Download, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text("Download and install")
                            }
                            is UpdateState.Ready -> Button(
                                onClick = { updates?.install() },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Install") }
                            else -> Button(
                                onClick = { updates?.check() },
                                enabled = updates != null,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Check for updates") }
                        }
                    }
                }
            }
            // One card: what the incoming update brings when there is one
            // waiting and its release carries notes, otherwise what this
            // version brought. Never the history — see WhatsNew.
            val incoming = when (state) {
                is UpdateState.Available -> state.release
                is UpdateState.Downloading -> state.release
                is UpdateState.Ready -> state.release
                else -> null
            }
            val notes = incoming?.notes ?: installedNotes
            if (notes != null) {
                item(key = "whats-new") {
                    WhatsNewCard(
                        heading = if (incoming?.notes != null) "What's new in ${incoming.version}"
                        else "What's new in this version",
                        notes = notes
                    )
                }
            }
        }
    }
}

/** An update's notes: a heading, its title, a line for each thing. */
@Composable
private fun WhatsNewCard(heading: String, notes: WhatsNew) {
    Card(
        colors = CardDefaults.cardColors(containerColor = settingsRowColor()),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text(heading, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(notes.title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                notes.items.forEach { line ->
                    Row {
                        Text("•", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp))
                        Text(line, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
