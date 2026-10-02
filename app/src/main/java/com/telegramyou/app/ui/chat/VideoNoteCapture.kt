package com.telegramyou.app.ui.chat

import androidx.camera.compose.CameraXViewfinder
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import com.telegramyou.app.ui.icons.Symbols
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * What the screen shows while a round video message is being recorded: the
 * conversation dimmed, the front camera in a circle in the middle — the
 * circle the message will be — with a ring round it filling towards
 * Telegram's minute, the time so far, and how to send or throw it away.
 *
 * The viewfinder is CameraX's own composable, clipped to a circle; until the
 * camera has a frame to give, Expressive's loading indicator holds its
 * place.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun VideoNoteCapture(
    recorder: VideoNoteRecorder,
    /** When the hold began, or null while nothing is being recorded. */
    since: Long?,
    /** The minute is up: send what there is, as lifting the finger would. */
    onLimit: () -> Unit,
    /** Slid up to lock: the finger is off, and these buttons finish it. */
    locked: Boolean = false,
    onSend: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    AnimatedVisibility(
        visible = since != null,
        enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec())
    ) {
        var elapsed by remember { mutableLongStateOf(0L) }
        // From when the camera began recording, not from the finger: it
        // takes a moment to open, and the clock should not run before it.
        val recordingFrom = recorder.recordingSince
        LaunchedEffect(since, recordingFrom) {
            elapsed = 0L
            if (since == null) return@LaunchedEffect
            val start = recordingFrom ?: return@LaunchedEffect
            while (true) {
                withFrameMillis { }
                elapsed = System.currentTimeMillis() - start
                if (elapsed >= VideoNoteRecorder.MAX_SECONDS * 1000L) {
                    onLimit()
                    break
                }
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
                // The conversation under the scrim takes no touches while a
                // recording is up: a stray tap there would act on a chat the
                // person cannot see properly.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false).consume()
                    }
                }
                .semantics { contentDescription = "Recording a video message" }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(RING)) {
                    CircularProgressIndicator(
                        progress = { (elapsed / (VideoNoteRecorder.MAX_SECONDS * 1000f)).coerceIn(0f, 1f) },
                        modifier = Modifier.size(RING),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        strokeWidth = 4.dp
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(CIRCLE)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        val request = recorder.surfaceRequest
                        if (request != null) {
                            CameraXViewfinder(surfaceRequest = request, modifier = Modifier.fillMaxSize())
                        } else {
                            LoadingIndicator()
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error)
                    )
                    Text(
                        formatDuration(elapsed / 1000L),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.inverseOnSurface
                    )
                }
                if (!locked) {
                    Text(
                        "Lift to send · slide up to lock · aside to cancel",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                } else {
                    // Locked, the finger is off the screen and these finish
                    // it: thrown away, the other camera, or sent — Send the
                    // filled one, as in the composer.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        modifier = Modifier.padding(top = 24.dp)
                    ) {
                        FilledTonalIconButton(onClick = onDelete, modifier = Modifier.size(56.dp)) {
                            Icon(Symbols.Delete, contentDescription = "Delete video message")
                        }
                        if (recorder.canFlip) {
                            FilledTonalIconButton(onClick = { recorder.flip() }, modifier = Modifier.size(56.dp)) {
                                Icon(Symbols.SwitchCamera, contentDescription = "Switch camera")
                            }
                        }
                        FilledIconButton(onClick = onSend, modifier = Modifier.size(72.dp)) {
                            Icon(Symbols.SendFilled, contentDescription = "Send video message")
                        }
                    }
                }
            }
        }
    }
}

/** The circle, about as wide as the official client's on a phone. */
private val CIRCLE = 280.dp
private val RING = 296.dp
