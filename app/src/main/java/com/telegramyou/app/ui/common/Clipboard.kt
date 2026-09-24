package com.telegramyou.app.ui.common

import android.content.ClipData
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import kotlinx.coroutines.launch

/**
 * Something to call with text to put it on the clipboard.
 *
 * `LocalClipboard` rather than `LocalClipboardManager`, which this Compose
 * deprecates. The new one is suspending — the platform clipboard can block —
 * so the copy runs in the composition's own scope and the call site stays a
 * plain function, the way an `onClick` wants it.
 */
@Composable
fun rememberTextCopier(): (String) -> Unit {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    return remember(clipboard, scope) {
        { text ->
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("text", text)))
            }
        }
    }
}
