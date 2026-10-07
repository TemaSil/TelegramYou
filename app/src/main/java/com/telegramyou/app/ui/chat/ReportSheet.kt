package com.telegramyou.app.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.telegramyou.app.telegram.model.ReportStep

/**
 * Reporting a message (2.0): the server's own question and reasons as a
 * sheet of Material list rows — a reason can open more of them — and its
 * request for a few words as a dialog. Telegram decides the reasons, so
 * nothing here is a fixed list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReportSheet(step: ReportStep, onAnswer: (optionId: String, text: String) -> Unit, onDismiss: () -> Unit) {
    when (step) {
        is ReportStep.Choose -> ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(Modifier.navigationBarsPadding()) {
                Text(
                    step.title.ifBlank { "Report" },
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                LazyColumn {
                    items(step.options, key = { it.id }) { option ->
                        ListItem(
                            headlineContent = { Text(option.text) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAnswer(option.id, "") }
                        )
                    }
                }
            }
        }
        is ReportStep.Explain -> {
            var text by rememberSaveable(step.optionId) { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Report") },
                text = {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text(if (step.optional) "Add a comment (optional)" else "What is wrong with it") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = { onAnswer(step.optionId, text.trim()) },
                        enabled = step.optional || text.isNotBlank()
                    ) { Text("Send report") }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
            )
        }
        ReportStep.Done -> Unit
    }
}
