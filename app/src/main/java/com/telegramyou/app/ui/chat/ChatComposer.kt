package com.telegramyou.app.ui.chat

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.animation.core.animateDpAsState
import com.telegramyou.app.ui.icons.Symbols
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.PointerEventPass
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.telegramyou.app.telegram.model.AttachmentDraft
import com.telegramyou.app.telegram.model.ChatMessage
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// The composer at the foot of a conversation and what sits on it: the reply or edit banner, attachment chips, and the camera file it shoots to.

/**
 * Where the camera app writes, in the app's own cache.
 *
 * A real file rather than a gallery entry: nothing is left behind in the
 * user's gallery if the shot is cancelled, and the app owns what it sends.
 */
internal fun newCameraFile(context: Context): File {
    val directory = File(context.cacheDir, "camera").apply { mkdirs() }
    return File(directory, "capture-${System.currentTimeMillis()}.jpg")
}

/**
 * The same file as a Uri the camera app is allowed to write to.
 *
 * The authority matches the provider in the manifest; getting it wrong throws
 * at the launch rather than returning null, which is the right failure — a
 * silent one would look like a camera that does nothing.
 */
internal fun cameraUri(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

/**
 * The banner over the composer: what is being answered, or amended.
 *
 * Joined to the capsule rather than laid across the screen. It is the same
 * width and tone as the capsule, with its large corners on top and small
 * ones where the two meet, two apart — and the capsule's own top corners go
 * small while it is there (see ComposerBar's attachedAbove) — so the pair
 * reads as one control the reply grew out of: Material 3 Expressive's way
 * of joining things, as its segmented list rows are joined.
 *
 * It was a full-width band in the conversation's own colour, opaque, which
 * the owner saw as a strip laid over the chat. Translucency and blur were
 * tried before that and withdrawn — see CLAUDE.md.
 */
@Composable
internal fun ComposerBanner(
    message: ChatMessage,
    isEditing: Boolean,
    onCancel: () -> Unit,
    /** Over the plain composer's field rather than over a capsule (2.0): as wide as the field, in its colour. */
    plain: Boolean = false
) {
    Surface(
        color = if (plain) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(
            topStart = BANNER_OUTER_CORNER,
            topEnd = BANNER_OUTER_CORNER,
            bottomStart = COMPOSER_JOIN_CORNER,
            bottomEnd = COMPOSER_JOIN_CORNER
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (plain) PLAIN_GUTTER else 16.dp,
                end = if (plain) PLAIN_GUTTER + PLAIN_BUTTON + PLAIN_GAP else 16.dp,
                bottom = COMPOSER_JOIN_GAP
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 20.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
        ) {
            Icon(
                if (isEditing) Symbols.Edit else Symbols.Reply,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isEditing) "Edit message" else (message.senderName ?: "You"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    message.text.ifBlank { "Attachment" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onCancel) {
                Icon(
                    Symbols.Close,
                    contentDescription = if (isEditing) "Cancel edit" else "Cancel reply"
                )
            }
        }
    }
}

@Composable
internal fun AttachmentChip(draft: AttachmentDraft?, onClear: () -> Unit) {
    if (draft == null) return
    val label = when (draft) {
        is AttachmentDraft.Files -> "${draft.names.size} file(s): ${draft.names.firstOrNull().orEmpty()}"
        is AttachmentDraft.Photos -> "${draft.uris.size} photo(s)"
        // A recording is sent the moment the finger lifts, so this chip is
        // only ever seen for the instant between the two — named anyway,
        // because a `when` over a sealed type is where a new case should
        // announce itself rather than fall into an else.
        is AttachmentDraft.Voice -> "Voice message ${formatDuration(draft.durationSeconds.toLong())}"
        is AttachmentDraft.VideoNote -> "Video message ${formatDuration(draft.durationSeconds.toLong())}"
    }
    // An InputChip, which is the Material component for "one item you have
    // added and can take back". It was a Row painted to look like a chip,
    // with a "Clear" TextButton where the dismiss icon belongs.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        InputChip(
            selected = false,
            onClick = onClear,
            label = { Text(label, maxLines = 1) },
            leadingIcon = {
                Icon(
                    if (draft is AttachmentDraft.Photos) Symbols.Image
                    else Symbols.AttachFile,
                    contentDescription = null
                )
            },
            trailingIcon = {
                Icon(Symbols.Close, contentDescription = "Remove attachment")
            }
        )
    }
}

/**
 * How far the composer's icon buttons sit above the bottom of their row.
 *
 * Not a nudge by eye. A filled `TextField` is 56dp tall by the spec and an
 * `IconButton` is 48dp, and the row aligns them at the bottom so that a field
 * grown to several lines keeps its buttons beside the last one. Aligning the
 * boxes at the bottom leaves their centres eight apart, so the icons draw four
 * low — measured off an emulator screenshot as ten pixels at 2.75x, which is
 * exactly this.
 *
 * Lifting the buttons rather than centring the row fixes every line count at
 * once: the offset between the field's last line and the row's bottom does not
 * change as the field grows.
 */
internal val ComposerButtonLift = 4.dp

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ComposerBar(
    value: String,
    onValueChange: (String) -> Unit,
    onAttach: () -> Unit,
    /**
     * The emoji, GIF and sticker panel, from the smiley at the end of the
     * field — and [onKeyboard], from the keyboard key that stands there
     * while the panel is up, back to the keyboard.
     */
    onExpressions: () -> Unit,
    expressionsOpen: Boolean = false,
    onKeyboard: () -> Unit = {},
    /** The camera, straight from the composer rather than through the sheet. */
    onCamera: () -> Unit,
    /**
     * The camera button held rather than tapped: a round video message,
     * recorded while the finger stays down and sent when it lifts, as the
     * microphone does a voice message (1.7). Leaving — a scroll, a slide off
     * the button — throws it away.
     */
    onVideoNoteStart: () -> Unit = {},
    onVideoNoteStop: () -> Unit = {},
    onVideoNoteCancel: () -> Unit = {},
    /**
     * Slid up while held: the recording goes on with the finger off the
     * screen, and is sent or thrown away from the circle's own buttons.
     */
    onVideoNoteLock: () -> Unit = {},
    onSend: () -> Unit,
    /** Held send button's "Schedule message"; null where it is not offered. */
    onSchedule: (() -> Unit)? = null,
    recordingSince: Long?,
    onRecordStart: () -> Unit,
    onRecordStop: () -> Unit,
    onRecordCancel: () -> Unit,
    /** One reading of the microphone, 0 to 1: kept for the waveform, and answered for the button. */
    onSampleAmplitude: () -> Float,
    /**
     * Whether something is already attached and waiting to go.
     *
     * The right-hand button used to be chosen from the text alone, so a
     * photo picked with nothing typed left a microphone where send should
     * have been. What decides that button is whether there is anything to
     * send, and a photo is.
     */
    hasAttachment: Boolean,
    /** Held by the screen, so replying can put the caret in the field. */
    focusRequester: FocusRequester,
    /** The bot's placeholder, when it set one. */
    placeholder: String = "Message",
    /**
     * Null in a chat with no bot keyboard; otherwise whether it is up, and
     * the field grows a button to raise or lower it.
     */
    botKeyboardShown: Boolean? = null,
    onBotKeyboardToggle: () -> Unit = {},
    /**
     * A reply or edit banner sits on top, joined to the field: its top
     * corners go small to meet it. See ComposerBanner.
     */
    attachedAbove: Boolean = false,
    /**
     * For geeks → Composer in a capsule: the field and every button in one
     * rounded bar, as it was until 2.0. Off, the field and a round button
     * stand on the conversation's own background, as Google Messages has
     * them — the owner found the capsule heavy.
     */
    capsule: Boolean = false,
    /**
     * What waits to be sent, drawn inside the plain field above the text
     * (2.0) — the photos' tray; null when nothing does, or with the capsule,
     * which keeps it above.
     */
    inField: (@Composable () -> Unit)? = null
) {
    // A voice message as the official client records one (2.0): held, the
    // button swells and breathes with the voice; slid up past the lock, it
    // goes on with the finger off the screen and the button becomes Send;
    // slid aside, it is thrown away. Held here, since the button, the row
    // in the field's place and the lock above all read it.
    val voice = remember { VoiceHold() }
    LaunchedEffect(recordingSince) {
        if (recordingSince == null) voice.reset()
    }
    // Locked, Back throws the recording away rather than leaving the chat
    // with the microphone still on.
    BackHandler(enabled = recordingSince != null && voice.locked) { onRecordCancel() }

    // Closer over the keyboard than over the bottom of the screen; eased
    // between the two rather than switched, since the switch lands the moment
    // the keyboard is gone and read as a last small hop.
    val bottomMargin by animateDpAsState(
        targetValue = if (WindowInsets.isImeVisible || expressionsOpen) COMPOSER_MARGIN_OVER_KEYBOARD else COMPOSER_MARGIN,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "composerBottomMargin"
    )
    val sendable = value.isNotBlank() || hasAttachment

    // The field's own shape comes in with its fill: the capsule's field is
    // drawn in it, where clipping a padded field cut its round ends flat
    // (2.0, fixed in 2.0.1); the plain one sits in a column that draws it.
    val field: @Composable (Modifier, Color, Shape) -> Unit = { modifier, fill, shape ->
        if (recordingSince != null) {
            RecordingRow(
                since = recordingSince,
                voice = voice,
                onSampleAmplitude = onSampleAmplitude,
                onCancel = onRecordCancel,
                modifier = modifier
            )
        } else {
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = modifier
                    .padding(vertical = if (capsule) 0.dp else 2.dp)
                    .focusRequester(focusRequester),
                placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                // Where Telegram keeps it, and every messenger since:
                // inside the field, at its end.
                trailingIcon = {
                    Row {
                        if (botKeyboardShown != null) {
                            IconButton(onClick = onBotKeyboardToggle) {
                                Icon(
                                    if (botKeyboardShown) Symbols.KeyboardHide else Symbols.Keyboard,
                                    contentDescription = if (botKeyboardShown) "Hide bot keyboard" else "Bot keyboard"
                                )
                            }
                        }
                        if (expressionsOpen) {
                            IconButton(onClick = onKeyboard) {
                                Icon(Symbols.Keyboard, contentDescription = "Keyboard")
                            }
                        } else {
                            IconButton(onClick = onExpressions) {
                                Icon(Symbols.EmojiEmotions, contentDescription = "Emoji, GIFs and stickers")
                            }
                        }
                        // Without a capsule, the camera sits at the end of the
                        // field, as the picture button does in Messages, and
                        // steps aside once there is something to send.
                        if (!capsule && !sendable) {
                            CameraButton(
                                enabled = true,
                                onCamera = onCamera,
                                onVideoNoteStart = onVideoNoteStart,
                                onVideoNoteStop = onVideoNoteStop,
                                onVideoNoteCancel = onVideoNoteCancel,
                                onVideoNoteLock = onVideoNoteLock
                            )
                        }
                    }
                },
                shape = shape,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = fill,
                    unfocusedContainerColor = fill,
                    disabledContainerColor = fill,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                maxLines = 5
            )
        }
    }

    val button: @Composable (Dp) -> Unit = { size ->
        VoiceSendButton(
            size = size,
            sendable = sendable,
            recording = recordingSince != null,
            voice = voice,
            onSend = onSend,
            onSchedule = onSchedule,
            onRecordStart = onRecordStart,
            onRecordStop = onRecordStop,
            onRecordCancel = onRecordCancel,
            // Bright at rest in the capsule, as it was before 2.0: a quieter
            // container sat too close to the capsule round it to be seen.
            quietAtRest = !capsule,
            // In the capsule, the 48dp an icon button stands in, as the
            // capsule's other buttons do: four round the 40dp circle, and
            // the same lift. Without it the circle sat six low of the
            // field's middle and four from the capsule's edge (2.0.1).
            modifier = if (capsule) {
                Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = ComposerButtonLift + 4.dp)
            } else {
                Modifier
            }
        )
    }

    if (capsule) {
        CapsuleComposer(
            attachedAbove = attachedAbove,
            bottomMargin = bottomMargin,
            recording = recordingSince != null,
            onAttach = onAttach,
            camera = {
                CameraButton(
                    enabled = recordingSince == null,
                    onCamera = onCamera,
                    onVideoNoteStart = onVideoNoteStart,
                    onVideoNoteStop = onVideoNoteStop,
                    onVideoNoteCancel = onVideoNoteCancel,
                    onVideoNoteLock = onVideoNoteLock,
                    modifier = Modifier.padding(bottom = ComposerButtonLift)
                )
            },
            field = field,
            button = { button(40.dp) }
        )
        return
    }

    // The field and the button on the conversation's own background, with
    // nothing round them — the shape Google Messages has settled on. What
    // scrolls under them fades into the background's own bottom colour, so
    // a bubble passing behind goes out of sight instead of meeting an edge.
    val ground = chatBackgroundBottom()
    val topCorner by animateDpAsState(
        targetValue = if (attachedAbove) COMPOSER_JOIN_CORNER else FIELD_CORNER,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "fieldTop"
    )
    val fieldShape = RoundedCornerShape(
        topStart = topCorner.coerceAtLeast(0.dp),
        topEnd = topCorner.coerceAtLeast(0.dp),
        bottomStart = FIELD_CORNER,
        bottomEnd = FIELD_CORNER
    )
    val fill = MaterialTheme.colorScheme.surfaceContainerHigh
    Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(0f to Color.Transparent, 0.45f to ground.copy(alpha = 0.92f), 1f to ground))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = PLAIN_GUTTER,
                    end = PLAIN_GUTTER,
                    top = if (attachedAbove) 0.dp else COMPOSER_MARGIN,
                    bottom = bottomMargin
                ),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(fieldShape)
                    .background(fill)
                    // The field growing up round the pictures, and back,
                    // rather than jumping to its new height.
                    .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())
            ) {
                if (recordingSince == null) inField?.invoke()
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    IconButton(
                        onClick = onAttach,
                        enabled = recordingSince == null,
                        modifier = Modifier.padding(bottom = ComposerButtonLift)
                    ) {
                        Icon(Symbols.AddCircle, contentDescription = "Attach")
                    }
                    // Pulled in towards the plus: a TextField keeps sixteen
                    // inside its start, which after the plus's own twelve
                    // left "Message" far from it (2.0.2, on the owner's
                    // word). Its fill is the column's, so it draws none of
                    // its own over the plus it now reaches under.
                    field(
                        Modifier
                            .weight(1f)
                            .pullStart(FIELD_PULL),
                        Color.Transparent,
                        RoundedCornerShape(0.dp)
                    )
                }
            }
            Spacer(Modifier.width(PLAIN_GAP))
            button(PLAIN_BUTTON)
        }
    }
}

/** The geek's composer: one capsule with everything inside it, as before 2.0. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CapsuleComposer(
    attachedAbove: Boolean,
    bottomMargin: Dp,
    recording: Boolean,
    onAttach: () -> Unit,
    camera: @Composable () -> Unit,
    field: @Composable (Modifier, Color, Shape) -> Unit,
    button: @Composable () -> Unit
) {
    // Round on one line, and the same curve however tall the text makes it:
    // the capsule grows upward out of its round ends rather than changing
    // shape. The radius is half its height at rest, measured; recording swaps
    // the field for a shorter row and is left out of the measure.
    val density = LocalDensity.current
    var restingHeight by remember { mutableIntStateOf(0) }
    val corner = if (restingHeight == 0) COMPOSER_PILL_CORNER else with(density) { (restingHeight / 2).toDp() }
    val topCorner by animateDpAsState(
        targetValue = if (attachedAbove) COMPOSER_JOIN_CORNER else corner,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "capsuleTop"
    )
    // Held at zero: the spring overshoots, and a shape with a negative
    // corner throws rather than drawing square.
    val capsuleShape = RoundedCornerShape(
        topStart = topCorner.coerceAtLeast(0.dp),
        topEnd = topCorner.coerceAtLeast(0.dp),
        bottomStart = corner,
        bottomEnd = corner
    )
    // Concentric with the capsule: the field sits eight in from its edge.
    val fieldShape = RoundedCornerShape((corner - COMPOSER_FIELD_INSET).coerceAtLeast(0.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = if (attachedAbove) 0.dp else COMPOSER_MARGIN,
                bottom = bottomMargin
            )
    ) {
        // The capsule at the top of the container ladder and the field inside
        // it at the bottom, so each stands away from what is behind it. A
        // background rather than a Surface, which would clip: the voice
        // button swells past the capsule's edge while it records.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, capsuleShape)
                .onSizeChanged {
                    if (!recording && (restingHeight == 0 || it.height < restingHeight)) restingHeight = it.height
                }
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(
                onClick = onAttach,
                enabled = !recording,
                modifier = Modifier.padding(bottom = ComposerButtonLift)
            ) {
                Icon(Symbols.AttachFile, contentDescription = "Attach")
            }
            camera()
            field(
                Modifier.weight(1f),
                MaterialTheme.colorScheme.surfaceContainerLowest,
                fieldShape
            )
            button()
        }
    }
}

/** The camera: tapped, a photo; held past a long press, a round video. */
@Composable
private fun CameraButton(
    enabled: Boolean,
    onCamera: () -> Unit,
    onVideoNoteStart: () -> Unit,
    onVideoNoteStop: () -> Unit,
    onVideoNoteCancel: () -> Unit,
    onVideoNoteLock: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onCamera,
        enabled = enabled,
        modifier = modifier
            // Read on the Initial pass, as the microphone's hold is, and the
            // release after a hold swallowed there so the button's own click
            // — the photo — never sees it.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val released = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        waitForUpOrCancellation(PointerEventPass.Initial)
                    }
                    if (released == null) {
                        onVideoNoteStart()
                        // Followed by hand rather than with
                        // waitForUpOrCancellation, which gives up the moment
                        // the finger leaves the button — and both gestures
                        // here leave it: up past LOCK_SLIDE locks, aside past
                        // CANCEL_SLIDE throws the recording away.
                        var locked = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) {
                                change?.consume()
                                if (!locked) onVideoNoteStop()
                                break
                            }
                            change.consume()
                            if (locked) continue
                            val moved = change.position - down.position
                            if (moved.y < -LOCK_SLIDE.toPx()) {
                                locked = true
                                onVideoNoteLock()
                            } else if (kotlin.math.abs(moved.x) > CANCEL_SLIDE.toPx()) {
                                onVideoNoteCancel()
                                spendTheRest()
                                break
                            }
                        }
                    }
                }
            }
    ) {
        Icon(Symbols.PhotoCamera, contentDescription = "Camera, hold for a video message")
    }
}

/** The rest of a touch whose gesture is over, taken so nothing else acts on it. */
private suspend fun AwaitPointerEventScope.spendTheRest() {
    do {
        val rest = awaitPointerEvent(PointerEventPass.Initial)
        rest.changes.forEach { it.consume() }
    } while (rest.changes.any { it.pressed })
}

/** Where a held voice recording is: locked or not, how far the finger has slid, how loud it is now. */
@Stable
internal class VoiceHold {
    var locked by mutableStateOf(false)
    var slide by mutableStateOf(Offset.Zero)
    var level by mutableFloatStateOf(0f)

    fun reset() {
        locked = false
        slide = Offset.Zero
        level = 0f
    }
}

/**
 * In the field's place while a voice message records: a red dot that
 * breathes, the time, and how to get out of it — slide aside to cancel
 * while held, which follows the finger and fades as it goes, or a Cancel
 * button once locked.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RecordingRow(
    since: Long,
    voice: VoiceHold,
    onSampleAmplitude: () -> Float,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var elapsed by remember(since) { mutableLongStateOf(0L) }
    LaunchedEffect(since) {
        while (true) {
            elapsed = (System.currentTimeMillis() - since) / 1000
            // The same beat takes an amplitude reading, because
            // getMaxAmplitude answers for the time since the last call — an
            // irregular tick makes bars that stand for different lengths of
            // recording.
            voice.level = onSampleAmplitude()
            delay(RECORDING_TICK_MS)
        }
    }
    val pulse = rememberInfiniteTransition(label = "recordingDot")
    val dot by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "recordingDotAlpha"
    )
    val cancelReach = with(LocalDensity.current) { CANCEL_SLIDE.toPx() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(56.dp)
            .padding(start = 8.dp, end = 4.dp)
            .semantics { contentDescription = "Recording a voice message" }
    ) {
        Box(
            Modifier
                .size(10.dp)
                .alpha(dot)
                .background(MaterialTheme.colorScheme.error, CircleShape)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            formatDuration(elapsed),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.weight(1f))
        if (voice.locked) {
            TextButton(onClick = onCancel) { Text("Cancel") }
        } else {
            val gone = (-voice.slide.x / cancelReach).coerceIn(0f, 1f)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .graphicsLayer {
                        translationX = voice.slide.x * 0.5f
                        alpha = 1f - gone
                    }
                    .padding(end = 8.dp)
            ) {
                Icon(
                    Symbols.ChevronLeft,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    "Slide to cancel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * The round button at the end: a microphone to hold, Send once there is
 * something to send, and — held — the voice message's own control. Held,
 * it swells out of the composer with a ring round it that breathes with the
 * voice, a lock rises above it to be slid to, and it follows the finger;
 * locked, it stays swollen and turns into Send.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VoiceSendButton(
    size: Dp,
    sendable: Boolean,
    recording: Boolean,
    voice: VoiceHold,
    onSend: () -> Unit,
    onSchedule: (() -> Unit)?,
    onRecordStart: () -> Unit,
    onRecordStop: () -> Unit,
    onRecordCancel: () -> Unit,
    quietAtRest: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val swell by animateFloatAsState(
        targetValue = if (recording) RECORDING_SWELL else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "voiceSwell"
    )
    val breath by animateFloatAsState(
        targetValue = if (recording) 1f + voice.level * BREATH_REACH else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "voiceBreath"
    )
    val following = recording && !voice.locked
    val lockReach = with(LocalDensity.current) { LOCK_SLIDE.toPx() }
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(size)) {
        // The lock, above, while it is held and not yet locked: slid to, it
        // rises a little with the finger and its arrow fades as it nears.
        AnimatedVisibility(
            visible = following,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it / 2 },
            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = -LOCK_PILL_HEIGHT - LOCK_PILL_GAP)
        ) {
            val near = (-voice.slide.y / lockReach).coerceIn(0f, 1f)
            Surface(
                shape = CircleShape,
                color = colors.surfaceContainerHighest,
                modifier = Modifier
                    .size(width = 40.dp, height = LOCK_PILL_HEIGHT)
                    .graphicsLayer { translationY = voice.slide.y * 0.4f }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Symbols.Lock, contentDescription = "Slide up to lock", modifier = Modifier.size(20.dp))
                    Icon(
                        Symbols.KeyboardArrowUp,
                        contentDescription = null,
                        modifier = Modifier
                            .size(20.dp)
                            .alpha(1f - near)
                    )
                }
            }
        }
        // The ring that breathes with the voice, behind the button.
        if (recording) {
            Box(
                Modifier
                    .size(size)
                    .graphicsLayer {
                        val scale = swell * breath
                        scaleX = scale
                        scaleY = scale
                        translationX = if (following) voice.slide.x else 0f
                        translationY = if (following) voice.slide.y else 0f
                    }
                    .background(colors.primary.copy(alpha = 0.22f), CircleShape)
            )
        }
        var scheduleMenu by remember { mutableStateOf(false) }
        // Read by the gesture, which outlives the composition it started in.
        val recordingNow by rememberUpdatedState(recording)
        val showsSend = sendable || (voice.locked && recording)
        FilledIconButton(
            onClick = {
                when {
                    // Locked: this is Send for the recording.
                    recording && voice.locked -> onRecordStop()
                    sendable && !recording -> onSend()
                }
            },
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (showsSend || recording || !quietAtRest) colors.primary else colors.primaryContainer,
                contentColor = if (showsSend || recording || !quietAtRest) colors.onPrimary else colors.onPrimaryContainer
            ),
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = swell
                    scaleY = swell
                    translationX = if (following) voice.slide.x else 0f
                    translationY = if (following) voice.slide.y else 0f
                }
                .pointerInput(sendable, onSchedule != null) {
                    // Read on the Initial pass, before the button's own
                    // clickable sees the press: read after it, the press was
                    // already taken and holding the microphone did nothing.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        // Locked with a recording running: this press is
                        // Send, and the button's own click handles it. A lock
                        // left over with nothing recording — the microphone
                        // refused, say — would leave the button dead, so it
                        // is cleared and the press starts afresh.
                        if (voice.locked && recordingNow) return@awaitEachGesture
                        if (voice.locked) voice.reset()
                        if (sendable) {
                            // Held rather than tapped: the schedule menu, and
                            // the release swallowed so a hold does not also send.
                            if (onSchedule == null) return@awaitEachGesture
                            val released = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                waitForUpOrCancellation(PointerEventPass.Initial)
                            }
                            if (released == null) {
                                scheduleMenu = true
                                waitForUpOrCancellation(PointerEventPass.Initial)?.consume()
                            }
                            return@awaitEachGesture
                        }
                        onRecordStart()
                        // Followed by hand, as the camera's hold is: the
                        // finger leaves the button both ways it can go.
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) {
                                change?.consume()
                                if (!voice.locked) onRecordStop()
                                break
                            }
                            change.consume()
                            if (voice.locked) continue
                            val moved = change.position - down.position
                            voice.slide = Offset(moved.x.coerceAtMost(0f), moved.y.coerceAtMost(0f))
                            if (moved.y < -LOCK_SLIDE.toPx() && recordingNow) {
                                voice.locked = true
                                voice.slide = Offset.Zero
                            } else if (moved.x < -CANCEL_SLIDE.toPx()) {
                                voice.slide = Offset.Zero
                                onRecordCancel()
                                spendTheRest()
                                break
                            }
                        }
                    }
                }
        ) {
            Icon(
                when {
                    showsSend -> Symbols.SendFilled
                    else -> Symbols.Mic
                },
                contentDescription = when {
                    voice.locked -> "Send voice message"
                    sendable -> "Send"
                    else -> "Hold to record"
                }
            )
        }
        if (onSchedule != null) {
            DropdownMenu(expanded = scheduleMenu, onDismissRequest = { scheduleMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Schedule message") },
                    leadingIcon = { Icon(Symbols.Schedule, contentDescription = null) },
                    onClick = {
                        scheduleMenu = false
                        onSchedule()
                    }
                )
            }
        }
    }
}

/** How far up a held camera button is slid to lock its recording. */
private val LOCK_SLIDE = 72.dp

/** How far aside it is slid to throw the recording away. */
private val CANCEL_SLIDE = 96.dp

/** The composer capsule's margin above and below. */
internal val COMPOSER_MARGIN = 8.dp

/** Below the capsule while the keyboard, or the panel in its place, is up. */
internal val COMPOSER_MARGIN_OVER_KEYBOARD = 4.dp

/** The reply banner's own corners, away from the capsule. */
internal val BANNER_OUTER_CORNER = 24.dp

/** Where the banner and the capsule meet: small corners, and a hairline between. */
internal val COMPOSER_JOIN_CORNER = 6.dp
internal val COMPOSER_JOIN_GAP = 2.dp

/** Past any half-height the composer reaches on one line: a round end. */
internal val COMPOSER_PILL_CORNER = 64.dp

/** How far the field sits in from the capsule's edge, for concentric corners. */
internal val COMPOSER_FIELD_INSET = 8.dp

/** The plain composer's field: round ends on one line, and that curve kept as it grows. */
internal val FIELD_CORNER = 28.dp

/** The plain composer's distance from the screen's sides, and between the field and its button. */
internal val PLAIN_GUTTER = 12.dp

/** How far the plain composer's field reaches back under the plus. */
private val FIELD_PULL = 8.dp

/**
 * Widens what it modifies by [by] and moves it that far towards the start,
 * so its content starts closer to what is before it while its end stays put.
 */
private fun Modifier.pullStart(by: Dp): Modifier = layout { measurable, constraints ->
    val extra = by.roundToPx()
    val wider = constraints.copy(
        minWidth = constraints.minWidth + extra,
        maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + extra else constraints.maxWidth
    )
    val placeable = measurable.measure(wider)
    layout((placeable.width - extra).coerceAtLeast(0), placeable.height) {
        placeable.place(-extra, 0)
    }
}
internal val PLAIN_GAP = 8.dp

/** The round button, as tall as the field beside it. */
internal val PLAIN_BUTTON = 56.dp

/** How much larger the button grows while a voice message records, and how far further it breathes. */
private const val RECORDING_SWELL = 1.6f
private const val BREATH_REACH = 0.6f

/** The lock over a held voice button: its height, and its distance above the swollen button. */
private val LOCK_PILL_HEIGHT = 72.dp
private val LOCK_PILL_GAP = 32.dp
