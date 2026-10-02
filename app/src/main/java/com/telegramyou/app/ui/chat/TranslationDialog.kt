package com.telegramyou.app.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.telegramyou.app.ui.icons.Symbols
import java.util.Locale

/**
 * A message's translation (1.8): the stock dialog, the language it was put
 * into above the text, Expressive's loading indicator until Telegram
 * answers. The text can be selected, or copied whole.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TranslationDialog(
    translation: Translation,
    onCopy: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val text = translation.text
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Symbols.Translate, contentDescription = null) },
        title = { Text("Translation") },
        text = {
            Column {
                Text(
                    "Into ${languageName(translation.language)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                if (text == null) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }
                } else {
                    SelectionContainer {
                        Text(
                            text,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .heightIn(max = 360.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        dismissButton = {
            TextButton(onClick = { text?.let(onCopy) }, enabled = text != null) { Text("Copy") }
        }
    )
}

/** The phone's language, as the code Telegram's translator takes. */
internal fun translationLanguage(): String = Locale.getDefault().language.ifBlank { "en" }

private fun languageName(code: String): String =
    Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH).ifBlank { code }
