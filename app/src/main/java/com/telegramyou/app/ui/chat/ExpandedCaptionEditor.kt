package com.telegramyou.app.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.telegramyou.app.i18n.tr
import com.telegramyou.app.ui.icons.Symbols

/** The composer's caption, edited in place: no second copy to lose on Back. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExpandedCaptionEditor(value: String, onValueChange: (String) -> Unit, onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        LaunchedEffect(Unit) { withFrameNanos { }; focus.requestFocus(); keyboard?.show() }
        Scaffold(
            topBar = { TopAppBar(
                title = { Text(tr("Caption", "Подпись")) },
                navigationIcon = { IconButton(onClick = onDismiss) { Icon(Symbols.ArrowBack, tr("Back", "Назад")) } },
                actions = { TextButton(onClick = onDismiss) { Text(tr("Done", "Готово")) } }
            ) }
        ) { padding ->
            TextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(tr("Add a caption", "Добавьте подпись")) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 8.dp)
                    .focusRequester(focus).semantics { contentDescription = tr("Caption text", "Текст подписи") }
            )
        }
    }
}
