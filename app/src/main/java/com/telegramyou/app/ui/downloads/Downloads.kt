package com.telegramyou.app.ui.downloads

import com.telegramyou.app.i18n.tr

import com.telegramyou.app.ui.components.withoutBottom
import android.content.Context
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.DownloadEntry
import com.telegramyou.app.telegram.model.DownloadOutcome
import com.telegramyou.app.telegram.model.DownloadSections
import com.telegramyou.app.telegram.model.DownloadState
import com.telegramyou.app.telegram.model.fileExtension
import com.telegramyou.app.telegram.model.progress
import com.telegramyou.app.telegram.model.sectionDownloads
import com.telegramyou.app.telegram.model.state
import com.telegramyou.app.telegram.model.statusLine
import com.telegramyou.app.ui.chat.FileToOpen
import com.telegramyou.app.ui.chat.MediaActions
import com.telegramyou.app.ui.icons.Symbols
import com.telegramyou.app.ui.media.FileTransfer
import com.telegramyou.app.ui.media.formatBytes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DownloadsUiState(
    val sections: DownloadSections = DownloadSections(emptyList(), emptyList()),
    /** Bytes arriving now, by file id, for the bars. */
    val transfers: Map<Int, FileTransfer> = emptyMap(),
    val isLoaded: Boolean = false,
    /** A finished file being fetched again, having been cleared from the phone. */
    val refetching: Int? = null,
    val fileToOpen: FileToOpen? = null,
    val notice: String? = null
)

/**
 * The download manager: TDLib's list of the files a person downloaded, with
 * what is still coming and what has come.
 *
 * Built against what people find wrong with Telegram's own (ROADMAP,
 * 1.6.3) — that it is hidden in search, that "downloaded" files are nowhere
 * a file manager looks, that clearing the cache makes them vanish from the
 * list, that "clear" does not say whether it deletes — so it is on the
 * main menu, every row says where its file is, a cleared file stays listed
 * to fetch again, and taking something off the list and deleting it from
 * the phone are two actions with two names.
 */
class DownloadsViewModel(private val repository: TelegramRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            var known = emptySet<Int>()
            repository.observeTransfers().collect { transfers ->
                _uiState.update { it.copy(transfers = transfers) }
                // A download started, finished or stopped: the list itself
                // has changed, not only its bars.
                if (transfers.keys != known) {
                    known = transfers.keys
                    refresh()
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val entries = runCatching { repository.fileDownloads() }.getOrDefault(emptyList())
            _uiState.update { it.copy(sections = sectionDownloads(entries), isLoaded = true) }
        }
    }

    /** After an action, once now and once when the backend has caught up. */
    private fun refreshSoon() {
        refresh()
        viewModelScope.launch {
            delay(SETTLE_MS)
            refresh()
        }
    }

    fun onPause(entry: DownloadEntry) {
        viewModelScope.launch {
            repository.setDownloadPaused(entry.fileId, true)
            refreshSoon()
        }
    }

    fun onResume(entry: DownloadEntry) {
        repository.resumeDownload(entry.chatId, entry.messageId, entry.fileId)
        refreshSoon()
    }

    fun onPauseAll() {
        viewModelScope.launch {
            repository.setAllDownloadsPaused(true)
            refreshSoon()
        }
    }

    fun onResumeAll() {
        _uiState.value.sections.active.filter { it.isPaused }.forEach {
            repository.resumeDownload(it.chatId, it.messageId, it.fileId)
        }
        refreshSoon()
    }

    /** An unfinished one stopped and its part-file deleted. */
    fun onCancel(entry: DownloadEntry) {
        viewModelScope.launch {
            repository.removeDownload(entry.fileId, deleteFile = true)
            _uiState.update { it.copy(notice = tr("Cancelled ${entry.name}", "Отменено: ${entry.name}")) }
            refreshSoon()
        }
    }

    fun onRemoveFromList(entry: DownloadEntry) {
        viewModelScope.launch {
            repository.removeDownload(entry.fileId, deleteFile = false)
            _uiState.update { it.copy(notice = tr("Taken off the list — the file is still on the phone", "Убрано из списка — файл остался на телефоне")) }
            refreshSoon()
        }
    }

    fun onDeleteFromPhone(entry: DownloadEntry) {
        viewModelScope.launch {
            repository.removeDownload(entry.fileId, deleteFile = true)
            _uiState.update { it.copy(notice = tr("Deleted from the phone · ${formatBytes(entry.sizeBytes)} freed", "Удалено с телефона · освобождено ${formatBytes(entry.sizeBytes)}")) }
            refreshSoon()
        }
    }

    fun onClearFinished(deleteFiles: Boolean) {
        val freed = _uiState.value.sections.finishedBytes
        viewModelScope.launch {
            repository.clearFinishedDownloads(deleteFiles)
            _uiState.update {
                it.copy(notice = if (deleteFiles) tr("Cleared · ${formatBytes(freed)} freed", "Очищено · освобождено ${formatBytes(freed)}") else tr("Cleared — the files are still on the phone", "Очищено — файлы остались на телефоне"))
            }
            refreshSoon()
        }
    }

    /** A finished file opened; one cleared from the phone fetched again first. */
    fun onOpen(entry: DownloadEntry) {
        when (entry.state(null)) {
            DownloadState.Done -> entry.path?.let { path ->
                _uiState.update { it.copy(fileToOpen = FileToOpen(path, entry.mimeType, entry.name)) }
            }
            DownloadState.Gone -> {
                if (_uiState.value.refetching != null) return
                _uiState.update { it.copy(refetching = entry.fileId) }
                viewModelScope.launch {
                    val outcome = repository.downloadToList(entry.chatId, entry.messageId, entry.fileId)
                    _uiState.update {
                        when (outcome) {
                            is DownloadOutcome.Done -> it.copy(refetching = null, fileToOpen = FileToOpen(outcome.path, entry.mimeType, entry.name))
                            DownloadOutcome.Stopped -> it.copy(refetching = null)
                            DownloadOutcome.Failed -> it.copy(refetching = null, notice = tr("Could not download ${entry.name}", "Не удалось скачать ${entry.name}"))
                        }
                    }
                    refresh()
                }
            }
            else -> Unit
        }
    }

    fun onFileOpened() = _uiState.update { it.copy(fileToOpen = null) }

    fun onFileRefused(name: String) = _uiState.update {
        it.copy(fileToOpen = null, notice = tr("No app on this phone opens $name", "На этом телефоне нет приложения, которое откроет $name"))
    }

    fun onSaved(name: String, saved: Boolean) = _uiState.update {
        it.copy(notice = if (saved) tr("$name is in Downloads/TelegramYou", "$name — в Downloads/TelegramYou") else tr("Could not save $name", "Не удалось сохранить $name"))
    }

    fun onNoticeShown() = _uiState.update { it.copy(notice = null) }

    private companion object {
        const val SETTLE_MS = 600L
    }
}

/**
 * Which finished downloads have had a copy put in the phone's Downloads —
 * this device's own record, since TDLib knows nothing of the copy.
 */
private class SavedCopies(context: Context) {
    private val prefs = context.getSharedPreferences("downloads_saved", Context.MODE_PRIVATE)
    fun ids(): Set<Int> = prefs.getStringSet(KEY, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()
    fun add(fileId: Int) {
        prefs.edit().putStringSet(KEY, ids().map { it.toString() }.toSet() + fileId.toString()).apply()
    }

    private companion object {
        const val KEY = "ids"
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel,
    state: DownloadsUiState,
    onBack: () -> Unit,
    onOpenChat: (Long) -> Unit,
    musicBar: @Composable () -> Unit = {},
    /** The mini player, at the foot of the screen. */
    playerBar: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val copies = remember { SavedCopies(context) }
    var savedIds by remember { mutableStateOf(copies.ids()) }
    val host = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<DownloadEntry?>(null) }
    val sections = state.sections

    state.notice?.let { notice ->
        LaunchedEffect(notice) {
            host.showSnackbar(notice)
            viewModel.onNoticeShown()
        }
    }
    state.fileToOpen?.let { file ->
        LaunchedEffect(file) {
            val name = file.name ?: tr("this file", "этот файл")
            if (MediaActions.openFile(context, file.path, file.mime, file.name)) viewModel.onFileOpened()
            else viewModel.onFileRefused(name)
        }
    }

    Scaffold(
        bottomBar = { playerBar() },
        topBar = {
            TopAppBar(
                title = { Text(tr("Downloads", "Загрузки")) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Symbols.ArrowBack, contentDescription = tr("Back", "Назад")) }
                },
                actions = {
                    // One button for the whole queue: pause while anything
                    // runs, resume once everything is paused.
                    if (sections.anyRunning) {
                        IconButton(onClick = viewModel::onPauseAll) { Icon(Symbols.Pause, contentDescription = tr("Pause all", "Приостановить все")) }
                    } else if (sections.anyPaused) {
                        IconButton(onClick = viewModel::onResumeAll) { Icon(Symbols.PlayArrow, contentDescription = tr("Resume all", "Продолжить все")) }
                    }
                    if (sections.finished.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) { Icon(Symbols.MoreVert, contentDescription = tr("More", "Ещё")) }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(tr("Clear finished", "Убрать завершённые")) },
                                    leadingIcon = { Icon(Symbols.DeleteSweep, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        clearing = true
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(host) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                // The foot goes to the list: it runs on under the mini
                // player (see withoutBottom).
                .padding(padding.withoutBottom())
        ) {
            musicBar()
            when {
                !state.isLoaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                sections.isEmpty -> EmptyDownloads()
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp + padding.calculateBottomPadding())) {
                    if (sections.active.isNotEmpty()) {
                        item(key = "active-header") { SectionHeader(tr("Downloading", "Скачивается")) }
                        items(sections.active, key = { "a${it.fileId}" }) { entry ->
                            ActiveRow(
                                entry = entry,
                                transfer = state.transfers[entry.fileId],
                                onPause = { viewModel.onPause(entry) },
                                onResume = { viewModel.onResume(entry) },
                                onCancel = { viewModel.onCancel(entry) }
                            )
                        }
                    }
                    if (sections.finished.isNotEmpty()) {
                        item(key = "done-header") { SectionHeader(tr("Downloaded", "Скачано")) }
                        items(sections.finished, key = { "d${it.fileId}" }) { raw ->
                            val entry = raw.copy(isSavedToDevice = raw.fileId in savedIds)
                            FinishedRow(
                                entry = entry,
                                refetching = state.refetching == entry.fileId,
                                onOpen = { viewModel.onOpen(entry) },
                                onOpenChat = { onOpenChat(entry.chatId) },
                                onSave = save@{
                                    val path = entry.path ?: return@save
                                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@save
                                    val saved = runCatching {
                                        MediaActions.saveToDownloads(context, path, entry.mimeType ?: "application/octet-stream")
                                    }.getOrDefault(false)
                                    if (saved) {
                                        copies.add(entry.fileId)
                                        savedIds = copies.ids()
                                    }
                                    viewModel.onSaved(entry.name, saved)
                                },
                                onRemove = { viewModel.onRemoveFromList(entry) },
                                onDelete = { deleting = entry }
                            )
                        }
                    }
                    item(key = "where") {
                        // The thing people most often get wrong about
                        // Telegram's downloads, said once, where they look.
                        Text(
                            tr(
                                "Downloaded files are kept in Telegram's storage on this phone, where file managers " +
                                    "do not look. Save to Downloads puts a copy in the phone's Downloads folder. " +
                                    "A file cleared from Telegram's storage stays on this list, to download again.",
                                "Скачанное хранится в памяти Telegram на этом телефоне, куда файловые менеджеры " +
                                    "не заглядывают. «Сохранить в Загрузки» кладёт копию в папку Downloads телефона. " +
                                    "Файл, удалённый из памяти Telegram, остаётся в списке, чтобы скачать его снова."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                        )
                    }
                }
            }
        }
    }

    if (clearing) {
        var alsoDelete by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { clearing = false },
            icon = { Icon(Symbols.DeleteSweep, contentDescription = null) },
            title = { Text(tr("Clear finished downloads?", "Убрать завершённые загрузки?")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val count = sections.finished.size
                    Text(
                        tr(
                            "Takes ${if (count == 1) "1 file" else "$count files"} off this list. " +
                                "The files stay on the phone unless you delete them too.",
                            "Уберёт из списка: ${com.telegramyou.app.i18n.counted(count, "", "", "файл", "файла", "файлов")}. " +
                                "Сами файлы останутся на телефоне, если не удалить и их."
                        )
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { alsoDelete = !alsoDelete }
                    ) {
                        Checkbox(checked = alsoDelete, onCheckedChange = { alsoDelete = it })
                        Text(tr("Also delete them from the phone (${formatBytes(sections.finishedBytes)})", "Удалить их и с телефона (${formatBytes(sections.finishedBytes)})"))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clearing = false
                    viewModel.onClearFinished(alsoDelete)
                }) { Text(tr("Clear", "Убрать")) }
            },
            dismissButton = { TextButton(onClick = { clearing = false }) { Text(tr("Cancel", "Отмена")) } }
        )
    }
    deleting?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            icon = { Icon(Symbols.Delete, contentDescription = null) },
            title = { Text(tr("Delete from the phone?", "Удалить с телефона?")) },
            text = {
                Text(
                    tr(
                        "${entry.name} goes from this phone and frees ${formatBytes(entry.sizeBytes)}. " +
                            "It stays in the chat, to download again." +
                            if (entry.isSavedToDevice) " The copy in Downloads is not touched." else "",
                        "${entry.name} удалится с телефона и освободит ${formatBytes(entry.sizeBytes)}. " +
                            "В чате он останется, его можно скачать снова." +
                            if (entry.isSavedToDevice) " Копию в Загрузках это не затронет." else ""
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    viewModel.onDeleteFromPhone(entry)
                }) { Text(tr("Delete", "Удалить")) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(tr("Cancel", "Отмена")) } }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun EmptyDownloads() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        Icon(
            Symbols.Download,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp)
        )
        Text(
            tr("Nothing downloaded yet", "Пока ничего не скачано"),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            tr("Files, music and videos you download from chats are listed here, and stay listed until you remove them.", "Здесь будут файлы, музыка и видео, скачанные из чатов. Они останутся в списке, пока вы их не уберёте."),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

/** The file's kind at a glance: its extension, or a note for music. */
@Composable
private fun FileBadge(entry: DownloadEntry) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.size(48.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            val extension = fileExtension(entry.name)
            when {
                entry.mimeType?.startsWith("audio/") == true -> Icon(Symbols.MusicNote, contentDescription = null)
                extension != null -> Text(extension, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                else -> Icon(Symbols.Description, contentDescription = null)
            }
        }
    }
}

@Composable
private fun ActiveRow(
    entry: DownloadEntry,
    transfer: FileTransfer?,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
    val state = entry.state(transfer)
    ListItem(
        headlineContent = { Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(entry.statusLine(transfer), maxLines = 1, overflow = TextOverflow.Ellipsis)
                val progress = entry.progress(transfer)
                if (progress != null) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        leadingContent = { FileBadge(entry) },
        trailingContent = {
            Row {
                if (state == DownloadState.Paused) {
                    IconButton(onClick = onResume) { Icon(Symbols.PlayArrow, contentDescription = tr("Resume ${entry.name}", "Продолжить ${entry.name}")) }
                } else {
                    IconButton(onClick = onPause) { Icon(Symbols.Pause, contentDescription = tr("Pause ${entry.name}", "Приостановить ${entry.name}")) }
                }
                IconButton(onClick = onCancel) { Icon(Symbols.Close, contentDescription = tr("Cancel ${entry.name}", "Отменить ${entry.name}")) }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FinishedRow(
    entry: DownloadEntry,
    refetching: Boolean,
    onOpen: () -> Unit,
    onOpenChat: () -> Unit,
    onSave: () -> Unit,
    onRemove: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val here = entry.path != null
    ListItem(
        modifier = Modifier.clickable(onClick = onOpen),
        headlineContent = { Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                entry.statusLine(null),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (here) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
            )
        },
        leadingContent = { FileBadge(entry) },
        trailingContent = {
            if (refetching) {
                LoadingIndicator(Modifier.size(28.dp))
            } else {
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Symbols.MoreVert, contentDescription = tr("More for ${entry.name}", "Ещё для ${entry.name}"))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(tr("Open chat", "Открыть чат")) },
                            leadingIcon = { Icon(Symbols.Chat, contentDescription = null) },
                            onClick = {
                                menu = false
                                onOpenChat()
                            }
                        )
                        if (here && MediaActions.canSaveToDownloads && !entry.isSavedToDevice) {
                            DropdownMenuItem(
                                text = { Text(tr("Save to Downloads", "Сохранить в Загрузки")) },
                                leadingIcon = { Icon(Symbols.Download, contentDescription = null) },
                                onClick = {
                                    menu = false
                                    onSave()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(tr("Remove from list", "Убрать из списка")) },
                            leadingIcon = { Icon(Symbols.Close, contentDescription = null) },
                            onClick = {
                                menu = false
                                onRemove()
                            }
                        )
                        if (here) {
                            DropdownMenuItem(
                                text = { Text(tr("Delete from phone", "Удалить с телефона")) },
                                leadingIcon = { Icon(Symbols.Delete, contentDescription = null) },
                                onClick = {
                                    menu = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }
        }
    )
}
