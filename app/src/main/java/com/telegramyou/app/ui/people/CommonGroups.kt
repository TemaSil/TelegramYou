package com.telegramyou.app.ui.people

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.telegramyou.app.i18n.tr
import com.telegramyou.app.navigation.Route
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.ChatPreview
import com.telegramyou.app.telegram.model.PersonProfile
import com.telegramyou.app.ui.components.AvatarBubble
import com.telegramyou.app.ui.components.personShape
import com.telegramyou.app.ui.failureText
import com.telegramyou.app.ui.icons.Symbols
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CommonGroupsUiState(
    val personName: String = "",
    val groups: List<ChatPreview> = emptyList(),
    val isLoading: Boolean = false,
    val isComplete: Boolean = false,
    val error: String? = null
)

/** The cursor belongs to this person's route, not to the chat opened from it. */
class CommonGroupsViewModel(
    private val repository: TelegramRepository,
    savedStateHandle: SavedStateHandle,
    private val userId: Long = checkNotNull(savedStateHandle[Route.Person.ARG_USER_ID])
) : ViewModel() {
    private var cursor = 0L
    private val state = MutableStateFlow(CommonGroupsUiState())
    val uiState = state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val name = repository.person(userId)?.user?.displayName.orEmpty()
                state.update { it.copy(personName = name) }
            } catch (e: CancellationException) { throw e }
              catch (_: Exception) { /* The list still works without a subtitle. */ }
        }
        load()
    }

    fun onNearEnd() {
        if (state.value.error == null) load()
    }

    fun onRetry() = load()

    private fun load() {
        if (state.value.isLoading || state.value.isComplete) return
        state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val page = repository.commonGroups(userId, cursor)
                val previous = cursor
                cursor = page.nextOffset ?: cursor
                state.update { current ->
                    current.copy(
                        groups = (current.groups + page.groups).distinctBy { it.id },
                        isLoading = false,
                        isComplete = page.nextOffset == null || page.nextOffset == previous
                    )
                }
            } catch (e: CancellationException) { throw e }
              catch (e: Exception) {
                state.update { it.copy(isLoading = false, error = failureText(tr("Could not load common groups", "Не удалось загрузить общие группы"), e.message)) }
            }
        }
    }
}

/** Embedded profiles have a chat route; the person id comes from its details. */
fun commonGroupsViewModelFactory(repository: TelegramRepository, userId: Long) = viewModelFactory {
    initializer { CommonGroupsViewModel(repository, createSavedStateHandle(), userId) }
}

/** Entry for standalone contact profiles that do not host media tabs. */
@Composable
fun CommonGroupsRow(profile: PersonProfile, onOpen: () -> Unit) {
    if (profile.isBot) return
    ListItem(
        headlineContent = { Text(tr("Common groups", "Общие группы")) },
        supportingContent = { Text(tr("Groups you are both in", "Группы, в которых вы состоите вместе")) },
        leadingContent = { Icon(Symbols.Group, contentDescription = null) },
        trailingContent = { profile.commonGroupCount?.let { Text(it.toString()) } },
        modifier = Modifier.clickable(onClick = onOpen)
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CommonGroupsScreen(state: CommonGroupsUiState, onBack: () -> Unit, onOpenChat: (Long) -> Unit, onNearEnd: () -> Unit, onRetry: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Column {
                Text(tr("Common groups", "Общие группы"))
                if (state.personName.isNotBlank()) Text(state.personName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Symbols.ArrowBack, tr("Back", "Назад")) } }
        )
    }) { padding ->
        CommonGroupsContent(state, onOpenChat, onNearEnd, onRetry, modifier = Modifier.padding(padding))
    }
}

/** Shared list for the profile tab and a standalone contact destination. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CommonGroupsContent(
    state: CommonGroupsUiState,
    onOpenChat: (Long) -> Unit,
    onNearEnd: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    initialPosition: Pair<Int, Int> = 0 to 0,
    onPositionChanged: (Pair<Int, Int>) -> Unit = {}
) {
    val list = rememberLazyListState(initialPosition.first, initialPosition.second)
    val last = list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
    // Load a saved position's pages before measuring the list; otherwise a
    // short first page clamps the saved index to its last row.
    val restoring = initialPosition.first > 0 && initialPosition.first >= state.groups.size && !state.isComplete
    LaunchedEffect(active, last, state.groups.size, state.isLoading) {
        if (active && (restoring || state.groups.isEmpty() || last >= state.groups.size - 5)) onNearEnd()
    }
    LaunchedEffect(active, restoring, list) {
        if (active && !restoring) snapshotFlow { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }
            .collect { onPositionChanged(it) }
    }
    if (restoring) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (state.error == null) LoadingIndicator() else Column(
                Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(state.error)
                TextButton(onClick = onRetry) { Text(tr("Retry", "Повторить")) }
            }
        }
        return
    }
    LazyColumn(state = list, modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(state.groups, key = { it.id }) { chat ->
                ListItem(
                    headlineContent = { Text(chat.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(chat.lastMessage, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { AvatarBubble(title = chat.title, seed = chat.avatarColor, size = 48.dp, photoPath = chat.photoPath, shape = personShape(chat.avatarColor)) },
                    modifier = Modifier.clickable { onOpenChat(chat.id) }
                )
            }
            if (state.isLoading) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { LoadingIndicator() } }
            if (state.error != null) item {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error)
                    TextButton(onClick = onRetry) { Text(tr("Retry", "Повторить")) }
                }
            }
            if (state.isComplete && state.groups.isEmpty()) item {
                Text(tr("No common groups yet", "Общих групп пока нет"), Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
    }
}
