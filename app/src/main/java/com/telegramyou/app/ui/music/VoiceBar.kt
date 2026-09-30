package com.telegramyou.app.ui.music

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.telegramyou.app.music.VoiceNow
import com.telegramyou.app.ui.icons.Symbols

/**
 * A voice message playing, over whichever screen is open — the chat it came
 * from may be closed by now. The mini player's shape, in the tertiary
 * colour so that a voice and a song are not mistaken for each other when
 * both are there: who is talking and how many more are coming, the speed,
 * pause, and stop.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VoiceBar(
    state: VoiceNow,
    onToggle: () -> Unit,
    onSpeed: () -> Unit,
    onStop: () -> Unit
) {
    AnimatedVisibility(
        visible = state.messageId != null,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        val who = state.senderName.ifBlank { "Voice message" }
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .semantics { contentDescription = "Voice message from $who" }
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 2.dp)
                ) {
                    Icon(Symbols.Mic, contentDescription = null, modifier = Modifier.size(24.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    ) {
                        Text(who, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (state.following > 0) "Voice message · ${state.following} more" else "Voice message",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    val speed = speedText(state.speed)
                    TextButton(
                        onClick = onSpeed,
                        modifier = Modifier.semantics { contentDescription = "Voice speed $speed" }
                    ) { Text(speed) }
                    FilledIconButton(onClick = onToggle, modifier = Modifier.size(40.dp)) {
                        Icon(
                            if (state.isPlaying) Symbols.PauseFilled else Symbols.PlayArrowFilled,
                            contentDescription = if (state.isPlaying) "Pause the voice bar" else "Resume the voice bar"
                        )
                    }
                    IconButton(onClick = onStop) { Icon(Symbols.Close, contentDescription = "Stop voice message") }
                }
                LinearWavyProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }
    }
}

private fun speedText(speed: Float): String =
    (if (speed == speed.toInt().toFloat()) speed.toInt().toString() else speed.toString()) + "×"
