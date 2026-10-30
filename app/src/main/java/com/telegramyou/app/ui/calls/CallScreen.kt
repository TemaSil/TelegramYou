package com.telegramyou.app.ui.calls

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.telegramyou.app.i18n.tr
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.CallInfo
import com.telegramyou.app.telegram.model.CallPhase
import com.telegramyou.app.telegram.model.callStatusLabel
import com.telegramyou.app.ui.components.AvatarBubble
import com.telegramyou.app.ui.icons.Symbols
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The call, over the whole app while there is one (2.9) — in the manner of
 * the Pixel's own phone app rather than Telegram's: the person large on the
 * theme's surface, the line under the name saying what is happening, round
 * tonal toggles that turn into rounded squares when on, and the red pill to
 * put it down. Stock Material throughout; nothing drawn by hand.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CallOverlay(
    repository: TelegramRepository,
    /** Whether the bar is up, so the app below moves down by [CALL_BAR_HEIGHT] and nothing is under it. */
    onBarShown: (Boolean) -> Unit = {}
) {
    val call by repository.call.collectAsStateWithLifecycle()
    // The last call shown, kept through the slide away once it is gone.
    var shown by remember { mutableStateOf<CallInfo?>(null) }
    call?.let { shown = it }
    val scope = rememberCoroutineScope()
    val answer = rememberWithMicrophone { scope.launch { repository.acceptCall() } }
    // Put away while it goes on, to use the app; the bar brings it back.
    // A new call always opens full.
    var minimized by rememberSaveable(call?.id) { mutableStateOf(false) }
    val canMinimize = call?.phase.let { it != null && it != CallPhase.Incoming && it != CallPhase.Ended }
    if (!canMinimize) minimized = false
    val barShown = call != null && minimized
    LaunchedEffect(barShown) { onBarShown(barShown) }
    AnimatedVisibility(
        visible = call != null && minimized,
        enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { -it } +
            fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
        exit = slideOutVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { -it } +
            fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec())
    ) {
        shown?.let { current -> CallBar(current, onOpen = { minimized = false }) }
    }
    AnimatedVisibility(
        visible = call != null && !minimized,
        enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it } +
            fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
        exit = slideOutVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it } +
            fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec())
    ) {
        shown?.let { current ->
            CallScreen(
                call = current,
                onAccept = answer,
                onEnd = { scope.launch { repository.endCall() } },
                onMute = repository::setCallMuted,
                onSpeaker = repository::setCallSpeaker,
                onGone = repository::dismissCall,
                onMinimize = { if (canMinimize) minimized = true }
            )
        }
    }
}

@Composable
fun CallScreen(
    call: CallInfo,
    onAccept: () -> Unit,
    onEnd: () -> Unit,
    onMute: (Boolean) -> Unit,
    onSpeaker: (Boolean) -> Unit,
    onGone: () -> Unit,
    onMinimize: () -> Unit = {}
) {
    // Back puts a call away into the bar rather than leaving it; a ringing
    // call is answered or declined, not put away.
    BackHandler(onBack = onMinimize)
    // Said for a moment, and then the screen goes.
    LaunchedEffect(call.phase) {
        if (call.phase == CallPhase.Ended) {
            delay(ENDED_SHOWN_MS)
            onGone()
        }
    }
    val elapsed by produceState(0L, call.connectedAt) {
        val from = call.connectedAt ?: return@produceState
        while (true) {
            value = (System.currentTimeMillis() - from) / 1000
            delay(1000)
        }
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 32.dp)
        ) {
            Box(Modifier.fillMaxWidth().height(48.dp)) {
                if (call.phase != CallPhase.Incoming && call.phase != CallPhase.Ended) {
                    IconButton(onClick = onMinimize, modifier = Modifier.align(Alignment.CenterStart)) {
                        Icon(Symbols.KeyboardArrowDown, contentDescription = tr("Minimize call", "Свернуть звонок"))
                    }
                }
            }
            AvatarBubble(
                title = call.name,
                seed = call.avatarColor,
                photoPath = call.photoPath,
                size = 136.dp
            )
            Spacer(Modifier.height(28.dp))
            Text(
                call.name,
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Text(
                callStatusLabel(call.phase, elapsed, call.end, call.isVideo),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // The key, as four emoji both phones show alike: read out and
            // matched, they say nobody is in between.
            if (call.emojis.isNotEmpty() && (call.phase == CallPhase.Active || call.phase == CallPhase.Connecting)) {
                Spacer(Modifier.height(20.dp))
                Text(
                    call.emojis.joinToString("  "),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics {
                        contentDescription = tr(
                            "Encryption key: ${call.emojis.joinToString(" ")}",
                            "Ключ шифрования: ${call.emojis.joinToString(" ")}"
                        )
                    }
                )
            }
            Spacer(Modifier.weight(1f))
            if (call.phase == CallPhase.Incoming) {
                IncomingButtons(onAccept = onAccept, onDecline = onEnd)
            } else {
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CallToggle(
                        checked = call.isMuted,
                        onCheckedChange = onMute,
                        icon = if (call.isMuted) Symbols.MicOffFilled else Symbols.Mic,
                        label = tr("Mute", "Без звука"),
                        enabled = call.phase != CallPhase.Ended
                    )
                    CallToggle(
                        checked = call.isSpeaker,
                        onCheckedChange = onSpeaker,
                        icon = if (call.isSpeaker) Symbols.VolumeUpFilled else Symbols.VolumeUp,
                        label = tr("Speaker", "Динамик"),
                        enabled = call.phase != CallPhase.Ended
                    )
                    // Video comes after the sound does; the button is here
                    // so the row is the shape it will be.
                    CallToggle(
                        checked = false,
                        onCheckedChange = {},
                        icon = Symbols.VideocamOff,
                        label = tr("Video", "Видео"),
                        enabled = false
                    )
                }
                Spacer(Modifier.height(40.dp))
                FilledIconButton(
                    onClick = onEnd,
                    enabled = call.phase != CallPhase.Ended,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.size(width = 104.dp, height = 64.dp)
                ) {
                    Icon(Symbols.CallEndFilled, contentDescription = tr("End call", "Завершить звонок"))
                }
            }
        }
    }
}

/** Decline on the left, answer on the right, as on every Android phone. */
@Composable
private fun IncomingButtons(onAccept: () -> Unit, onDecline: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
    ) {
        LabelledRound(
            label = tr("Decline", "Отклонить"),
            icon = Symbols.CallEndFilled,
            container = MaterialTheme.colorScheme.error,
            content = MaterialTheme.colorScheme.onError,
            onClick = onDecline
        )
        LabelledRound(
            label = tr("Answer", "Ответить"),
            icon = Symbols.PhoneFilled,
            container = MaterialTheme.colorScheme.primary,
            content = MaterialTheme.colorScheme.onPrimary,
            onClick = onAccept
        )
    }
}

@Composable
private fun LabelledRound(
    label: String,
    icon: ImageVector,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = container, contentColor = content),
            modifier = Modifier.size(76.dp)
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * A round tonal toggle that becomes a rounded square when on, the way the
 * Pixel's call buttons and Expressive's toggles say "this is on".
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CallToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector,
    label: String,
    enabled: Boolean
) {
    val corner by animateDpAsState(
        if (checked) 22.dp else 36.dp,
        MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "corner"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconToggleButton(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            shape = RoundedCornerShape(corner),
            modifier = Modifier.size(72.dp)
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * The call put away (2.9): a band across the top of the app — under the
 * status bar and as deep again as [CALL_BAR_HEIGHT] — with who and how
 * long, as a call keeps a bar over every phone app's screens. The app
 * moves down to make room rather than sitting under it; a tap opens the
 * call again.
 */
@Composable
private fun CallBar(call: CallInfo, onOpen: () -> Unit) {
    val elapsed by produceState(0L, call.connectedAt) {
        val from = call.connectedAt ?: return@produceState
        while (true) {
            value = (System.currentTimeMillis() - from) / 1000
            delay(1000)
        }
    }
    // A Box of its own, at the top: whatever this sits in may hand down its
    // whole size as a minimum, and the band would fill the screen.
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Surface(
        onClick = onOpen,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = tr("Return to call", "Вернуться к звонку") }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            modifier = Modifier
                .statusBarsPadding()
                .height(CALL_BAR_HEIGHT)
                .padding(horizontal = 16.dp)
        ) {
            Icon(Symbols.PhoneFilled, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                call.name,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Text(callStatusLabel(call.phase, elapsed, call.end, call.isVideo), style = MaterialTheme.typography.labelLarge)
        }
    }
    }
}

/** How much deeper than the status bar the call's bar is; the app moves down by this. */
val CALL_BAR_HEIGHT = 40.dp

/** How long "Call ended" stays before the screen slides away. */
private const val ENDED_SHOWN_MS = 1_500L
