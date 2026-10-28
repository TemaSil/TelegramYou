package com.telegramyou.app.ui.saved

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.telegramyou.app.i18n.tr
import com.telegramyou.app.navigation.Route
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.ChatPreview
import com.telegramyou.app.telegram.model.SavedTopic
import com.telegramyou.app.telegram.model.SavedTopicKind
import com.telegramyou.app.ui.components.AvatarBubble
import com.telegramyou.app.ui.icons.Symbols
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A Saved Messages topic's name: the chat's, or the screen's words for the two that are not chats. */
fun savedTopicTitle(topic: SavedTopic): String = when (topic.kind) {
    SavedTopicKind.MyNotes -> tr("My notes", "Мои заметки")
    SavedTopicKind.AuthorHidden -> tr("Author hidden", "Автор скрыт")
    SavedTopicKind.FromChat -> topic.title.ifBlank { tr("Chat", "Чат") }
}

data class SavedTopicsUiState(
    /** Null until read. */
    val topics: List<SavedTopic>? = null,
    /** The chats saved from, by id, for their pictures. */
    val chats: Map<Long, ChatPreview> = emptyMap(),
    val error: String? = null
)

class SavedTopicsViewModel(
    private val repository: TelegramRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    val chatId: Long = savedStateHandle[Route.Chat.ARG_CHAT_ID] ?: 0L

    private val _uiState = MutableStateFlow(SavedTopicsUiState())
    val uiState: StateFlow<SavedTopicsUiState> = _uiState.asStateFlow()

    /** Read on every return too: a topic opened may have a new last line. */
    fun load() {
        viewModelScope.launch {
            val topics = try {
                repository.savedTopics()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(topics = it.topics ?: emptyList(), error = e.message) }
                return@launch
            }
            val chats = repository.chats.value.associateBy { it.id }
            _uiState.update { it.copy(topics = topics, chats = chats, error = null) }
        }
    }
}

/**
 * Saved Messages by chat (2.7): every chat something was saved from, and My
 * notes for what was written there, each opened as a conversation of its
 * own — Telegram files saved messages this way, and the official client
 * lists them so. A plain list of Material's ListItems, the chat list's
 * reading of a row without its extras.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SavedTopicsScreen(
    state: SavedTopicsUiState,
    onBack: () -> Unit,
    onOpen: (SavedTopic) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("Saved Messages", "Избранное")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Symbols.ArrowBack, contentDescription = tr("Back", "Назад"))
                    }
                }
            )
        }
    ) { padding ->
        val topics = state.topics
        when {
            topics == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
            topics.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    state.error ?: tr("Nothing saved yet", "Пока ничего не сохранено"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(topics, key = { it.id }) { topic ->
                    val title = savedTopicTitle(topic)
                    ListItem(
                        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = topic.preview.takeIf { it.isNotBlank() }?.let {
                            { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        },
                        trailingContent = topic.timeLabel.takeIf { it.isNotBlank() }?.let {
                            { Text(it, style = MaterialTheme.typography.labelMedium) }
                        },
                        leadingContent = { TopicAvatar(topic, title, state.chats[topic.chatId]) },
                        modifier = Modifier.clickable { onOpen(topic) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TopicAvatar(topic: SavedTopic, title: String, chat: ChatPreview?) {
    when (topic.kind) {
        SavedTopicKind.MyNotes -> AvatarBubble(title = title, seed = 0, size = 48.dp, savedMessages = true)
        SavedTopicKind.AuthorHidden -> Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Symbols.VisibilityOff, contentDescription = null)
            }
        }
        SavedTopicKind.FromChat -> AvatarBubble(
            title = title,
            seed = chat?.avatarColor ?: (topic.chatId ?: 0L),
            size = 48.dp,
            photoPath = chat?.photoPath
        )
    }
}
