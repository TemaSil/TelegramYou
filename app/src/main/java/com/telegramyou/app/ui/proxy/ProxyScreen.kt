package com.telegramyou.app.ui.proxy

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.telegramyou.app.telegram.model.ProxyDraft
import com.telegramyou.app.telegram.model.ProxyKind
import com.telegramyou.app.telegram.model.ProxyServer
import com.telegramyou.app.telegram.model.kindLabel
import kotlinx.coroutines.launch

/**
 * The proxies Telegram goes through: a switch for using one at all, the list
 * as a radio choice — TDLib uses at most one — and a sheet for adding.
 *
 * Each row says how its proxy answered a ping just now, because "is this one
 * working" is the only question anyone opens this screen with.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxyScreen(
    state: ProxyUiState,
    onBack: () -> Unit,
    onUseProxyChange: (Boolean) -> Unit,
    onSelect: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onAddOpen: () -> Unit,
    onAddDismiss: () -> Unit,
    onDraftChange: ((ProxyDraft) -> ProxyDraft) -> Unit,
    onLinkPasted: (String) -> Unit,
    onSave: () -> Unit,
    onErrorShown: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    state.errorMessage?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            onErrorShown()
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Proxy") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddOpen,
                // Named explicitly: the alpha's extended button publishes an
                // empty node, which TalkBack reads as "button" and UiAutomator
                // cannot find — see the same fix on NewChatScreen.
                modifier = Modifier.semantics { contentDescription = "Add proxy" },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Add proxy") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            item {
                ListItem(
                    headlineContent = { Text("Use proxy") },
                    supportingContent = {
                        Text(
                            if (state.proxies.isEmpty()) "Add one to connect through it"
                            else if (state.isUsingProxy) "Telegram connects through the one chosen below"
                            else "Telegram connects directly"
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = state.isUsingProxy,
                            onCheckedChange = onUseProxyChange,
                            enabled = state.proxies.isNotEmpty()
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                )
            }
            if (!state.isLoading && state.proxies.isEmpty()) {
                item {
                    Text(
                        "No proxies yet. Add one, or paste a link someone shared — " +
                            "tg://proxy and t.me/socks links fill the form by themselves.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 48.dp)
                    )
                }
            }
            items(state.proxies, key = { it.id }) { proxy ->
                ProxyRow(
                    proxy = proxy,
                    ping = state.pings[proxy.id] ?: ProxyPing.Checking,
                    onSelect = { onSelect(proxy.id) },
                    onRemove = { onRemove(proxy.id) }
                )
            }
        }
    }

    state.draft?.let { draft ->
        // Straight to full height: it is a form, and at half height its
        // button sat below the fold — the smoke test could not find it, and
        // a thumb would have had to drag the sheet up to save.
        ModalBottomSheet(
            onDismissRequest = onAddDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            AddProxySheet(
                draft = draft,
                isSaving = state.isSaving,
                onDraftChange = onDraftChange,
                onLinkPasted = onLinkPasted,
                onSave = onSave
            )
        }
    }
}

@Composable
private fun ProxyRow(
    proxy: ProxyServer,
    ping: ProxyPing,
    onSelect: () -> Unit,
    onRemove: () -> Unit
) {
    val answer = when (ping) {
        ProxyPing.Checking -> "checking…"
        is ProxyPing.Answered -> "${ping.millis} ms"
        ProxyPing.Silent -> "not answering"
    }
    ListItem(
        headlineContent = { Text("${proxy.server}:${proxy.port}", maxLines = 1) },
        supportingContent = {
            Text(
                "${proxy.kindLabel()} · $answer",
                color = if (ping == ProxyPing.Silent) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        },
        leadingContent = { RadioButton(selected = proxy.isEnabled, onClick = null) },
        trailingContent = {
            IconButton(onClick = onRemove) {
                Icon(Icons.Rounded.Delete, contentDescription = "Remove ${proxy.server}")
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.clickable(onClick = onSelect)
    )
}

@Composable
private fun AddProxySheet(
    draft: ProxyDraft,
    isSaving: Boolean,
    onDraftChange: ((ProxyDraft) -> ProxyDraft) -> Unit,
    onLinkPasted: (String) -> Unit,
    onSave: () -> Unit
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Add proxy", style = MaterialTheme.typography.titleLarge)
        OutlinedButton(
            onClick = {
                scope.launch {
                    val text = clipboard.getClipEntry()?.clipData?.let { data ->
                        if (data.itemCount > 0) data.getItemAt(0).text?.toString() else null
                    }
                    onLinkPasted(text.orEmpty())
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Rounded.ContentPaste, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Paste a proxy link")
        }
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val kinds = ProxyKind.entries
            kinds.forEachIndexed { index, kind ->
                SegmentedButton(
                    selected = draft.kind == kind,
                    onClick = { onDraftChange { it.copy(kind = kind) } },
                    shape = SegmentedButtonDefaults.itemShape(index, kinds.size),
                    label = {
                        Text(
                            when (kind) {
                                ProxyKind.MtProto -> "MTProto"
                                ProxyKind.Socks5 -> "SOCKS5"
                                ProxyKind.Http -> "HTTP"
                            }
                        )
                    }
                )
            }
        }
        OutlinedTextField(
            value = draft.server,
            onValueChange = { value -> onDraftChange { it.copy(server = value) } },
            label = { Text("Server") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = draft.port,
            onValueChange = { value -> onDraftChange { it.copy(port = value.filter(Char::isDigit).take(5)) } },
            label = { Text("Port") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        if (draft.kind == ProxyKind.MtProto) {
            OutlinedTextField(
                value = draft.secret,
                onValueChange = { value -> onDraftChange { it.copy(secret = value.trim()) } },
                label = { Text("Secret") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            OutlinedTextField(
                value = draft.username,
                onValueChange = { value -> onDraftChange { it.copy(username = value) } },
                label = { Text("Username (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = draft.password,
                onValueChange = { value -> onDraftChange { it.copy(password = value) } },
                label = { Text("Password (optional)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
        }
        // What stands between the form and saving it, said once, under the
        // fields — only after something has been typed, so an empty sheet
        // does not open on a complaint.
        val problem = draft.problem.takeIf { draft != ProxyDraft() && draft.server.isNotBlank() }
        Box(Modifier.height(20.dp)) {
            if (problem != null) {
                Text(problem, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        Button(
            onClick = onSave,
            enabled = draft.problem == null && !isSaving,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save and connect")
        }
    }
}
