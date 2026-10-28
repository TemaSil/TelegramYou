package com.telegramyou.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.telegramyou.app.i18n.tr
import com.telegramyou.app.ui.icons.Symbols

/**
 * How wide the window must be, in dp, for the chat to open beside the list
 * rather than over it — Material's medium width, which an unfolded phone
 * held upright reaches — and how tall, so a phone on its side does not.
 */
const val TWO_PANE_WIDTH_DP = 600
const val TWO_PANE_HEIGHT_DP = 480

/**
 * A store of its own for the conversation in the pane beside the list
 * (2.8), made for [chatId] and cleared when another takes its place or the
 * pane closes — so a chat left behind lets go of what it held, as one
 * popped off the back stack does. Its view models are given [chatId] as a
 * route's arguments would give it, which is where ChatViewModel reads it.
 */
@Composable
fun ChatPaneStore(chatId: Long, content: @Composable () -> Unit) {
    val parent = LocalViewModelStoreOwner.current
    val owner = remember(chatId) { PaneOwner(parent, chatId) }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { content() }
}

private class PaneOwner(
    private val parent: ViewModelStoreOwner?,
    chatId: Long
) : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
    override val viewModelStore = ViewModelStore()

    private val parentDefaults = parent as? HasDefaultViewModelProviderFactory

    override val defaultViewModelProviderFactory = parentDefaults?.defaultViewModelProviderFactory
        ?: error("The chat pane needs a back stack entry around it")

    override val defaultViewModelCreationExtras: CreationExtras =
        MutableCreationExtras(parentDefaults?.defaultViewModelCreationExtras ?: CreationExtras.Empty).apply {
            set(VIEW_MODEL_STORE_OWNER_KEY, this@PaneOwner)
            set(DEFAULT_ARGS_KEY, bundleOf(Route.Chat.ARG_CHAT_ID to chatId))
        }
}

/** The pane before a chat is picked: what it is for, in a line. */
@Composable
fun NoChatPane() {
    Box(
        Modifier
            .fillMaxSize()
            .semantics { contentDescription = tr("No chat open", "Чат не открыт") },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Symbols.Chat,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                tr("Pick a chat to read it here", "Выберите чат, чтобы читать его здесь"),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
