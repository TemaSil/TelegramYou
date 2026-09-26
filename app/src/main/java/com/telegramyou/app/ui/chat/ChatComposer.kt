package com.telegramyou.app.ui.chat

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardHide
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoCamera
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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

/** The banner over the composer: what is being answered, or amended. */
@Composable
internal fun ComposerBanner(
    message: ChatMessage,
    isEditing: Boolean,
    onCancel: () -> Unit
) {
    // The conversation's own colour, opaque — not transparent, and not a
    // container tone.
    //
    // Two versions of this were wrong in opposite directions. A filled
    // surfaceContainerHigh strip read as a bar welded to the top of the
    // composer, a second band where there should be one floating control.
    // Making it transparent fixed the band and broke something worse: the
    // messages behind it showed through the text.
    //
    // So it paints what is behind it. Two layers rather than one, because the
    // conversation's background is a gradient and this is the bottom of it:
    // `surface` with primary at eight percent over it is exactly what that
    // gradient ends on, so the banner disappears into it while still hiding
    // whatever it covers.
    //
    // Not blur. Material 3 Expressive ships no blurred material — the effect
    // exists in Compose as Modifier.blur, and it is the one thing CLAUDE.md
    // rules out by name: glass belongs to another platform's design language
    // and to the sibling project, not here.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // The conversation's own background, opaque. Not a tone of its
            // own: the banner is not a strip attached to the composer, it is
            // the place the reply is being written. Translucency was tried
            // with the conversation blurred behind it and the owner asked for
            // both to go — see CLAUDE.md.
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp)
        ) {
            Icon(
                if (isEditing) Icons.Rounded.Edit else Icons.AutoMirrored.Rounded.Reply,
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
                    Icons.Rounded.Close,
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
                    if (draft is AttachmentDraft.Photos) Icons.Rounded.Image
                    else Icons.Rounded.AttachFile,
                    contentDescription = null
                )
            },
            trailingIcon = {
                Icon(Icons.Rounded.Close, contentDescription = "Remove attachment")
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

@Composable
internal fun ComposerBar(
    value: String,
    onValueChange: (String) -> Unit,
    onAttach: () -> Unit,
    /** The sticker sheet, from the smiley at the end of the field. */
    onStickers: () -> Unit,
    /** The camera, straight from the composer rather than through the sheet. */
    onCamera: () -> Unit,
    onSend: () -> Unit,
    /** Held send button's "Schedule message"; null where it is not offered. */
    onSchedule: (() -> Unit)? = null,
    recordingSince: Long?,
    onRecordStart: () -> Unit,
    onRecordStop: () -> Unit,
    onRecordCancel: () -> Unit,
    onSampleAmplitude: () -> Unit,
    /**
     * Whether something is already attached and waiting to go.
     *
     * The right-hand button used to be chosen from the text alone, so a
     * photo picked with nothing typed left a microphone where send should
     * have been — the attachment was on screen as a chip and there was no
     * way to send it without also writing something. What decides that
     * button is whether there is anything to send, and a photo is.
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
    onBotKeyboardToggle: () -> Unit = {}
) {
    // Floating, not a bar. It used to be a full-width surface welded to the
    // bottom of the screen with the buttons outside the field; this is one
    // capsule held clear of the edges, with everything inside it — the shape
    // Android's own messaging apps have settled on.
    //
    // The version before this grouped the two buttons into a ButtonGroup and
    // left them beside the field, which read as a split button sitting next
    // to a text box: three things in a row rather than one control. The
    // capsule is what makes it read as one, so the buttons are plain icon
    // buttons inside it and the group is gone. ButtonGroup is still the right
    // component for a segmented choice — see ROADMAP.md — just not for this.
    // No navigationBarsPadding here, and its absence is the fix. The Scaffold
    // this sits inside already applies the bottom inset through the padding
    // it hands its content, so adding it again spaced the capsule off the
    // navigation bar twice. The call was inherited from the full-width bar
    // this replaced, where it went unnoticed: that bar was painted to the
    // bottom of the screen, so a doubled inset only made it look tall. Give
    // it a shape and lift it off the edges and the gap becomes a hole.
    // Round on one line, and the same curve however tall the text makes it:
    // the capsule grows upward out of its round ends rather than changing
    // shape. The radius is half its height at rest — measured, since the
    // capsule is nearer 76dp than the 56 of the field inside it, and a fixed
    // 28.dp was round on paper only.
    //
    // It used to switch to 28.dp from the second line, on a spring, and the
    // owner found the switch a change nobody needed. Keeping the resting
    // radius costs nothing below: the buttons sit at the bottom, and the
    // bottom corners are the same at any height as on one line, so nothing
    // is cut. Fifty percent of the height, the rule before that, is what did
    // cut them — five-line half-discs.
    //
    // The height at rest is the smallest seen while the field is showing;
    // recording swaps the field for a shorter row and is left out.
    val density = LocalDensity.current
    var restingHeight by remember { mutableIntStateOf(0) }
    val corner = if (restingHeight == 0) {
        // Before the first measure: anything past half the height draws as a
        // full round end, since a shape clamps its corners to fit.
        COMPOSER_PILL_CORNER
    } else {
        with(density) { (restingHeight / 2).toDp() }
    }
    val capsuleShape = RoundedCornerShape(corner)
    // Concentric with the capsule: the field sits eight in from its edge, so
    // its corners are eight less. On one line that clamps to a round end;
    // taller, it is the capsule's curve followed inwards.
    val fieldShape = RoundedCornerShape((corner - COMPOSER_FIELD_INSET).coerceAtLeast(0.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Sixteen, the side gutter Material uses everywhere else in this
            // app. It was twelve, and the gap was reported as missing
            // entirely — correctly, but not for the reason it looked like.
            // The inset was there; the capsule's edge was not visible, so
            // there was nothing for the inset to hold clear of. See the
            // colour below. Sixteen once the edge shows is simply the right
            // number.
            .padding(horizontal = 16.dp, vertical = COMPOSER_MARGIN)
    ) {
        // No shadow, and that is the correction rather than an omission. The
        // first version of this carried shadowElevation = 6.dp, inherited
        // from the full-width bar it replaced and then nudged by eye. Material
        // 3 expresses depth as tone — the surfaceContainer ladder — and keeps
        // shadows for the few things that genuinely hover, like a FAB. The
        // apps this shape was taken from have no shadow under their composer
        // either: theirs reads as lifted because it is plainly darker than the
        // conversation, not because something is cast beneath it.
        //
        // tonalElevation is gone with the shadow: Compose only applies it when
        // the colour is `surface`, so on an explicit container colour it was
        // doing nothing at all.
        //
        // The capsule sits one step below the field inside it, which is why
        // it is not at the top of the ladder. See the field's colours below.
        // The darkest container, and the field inside it the lightest —
        // which is the reverse of what this used to be, on measurement
        // rather than on taste. Read off a screenshot from the emulator:
        //
        //   conversation background         (239, 240, 246)
        //   capsule, surfaceContainer       (239, 237, 241)   <- 5 apart
        //   field, surfaceContainerHighest  (227, 226, 230)
        //
        // Five units is nothing. The capsule had a border, a 28.dp corner
        // and a 12.dp inset, and none of the three could be seen, so the
        // composer read as a full-width band with a pill floating in it.
        // The cause is the conversation's own gradient: its bottom stop is
        // primary at 8% alpha, which lands almost exactly on
        // surfaceContainer — a decorative tint placed under the one control
        // that has to stand away from the background.
        //
        // Moving the capsule to the top of the container ladder puts twelve
        // units between it and the conversation, and the field then has to
        // go the other way to stay visible inside it. In dark mode the two
        // tones swap ends by construction, so the arrangement holds without
        // a second branch.
        Surface(
            shape = capsuleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged {
                    if (recordingSince == null && (restingHeight == 0 || it.height < restingHeight)) {
                        restingHeight = it.height
                    }
                }
        ) {
            Row(
                // Eight rather than four, so the field inside has room to
                // breathe instead of meeting the capsule's edge. The capsule
                // grows with it, which is the intent: it is a container, and
                // a container whose contents touch its sides looks like a
                // mistake rather than like a frame.
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                // Bottom, so a field grown to several lines keeps the buttons
                // beside its last line rather than floating them in the middle.
                // Centring instead would fix the single-line case and break
                // every other one, which is why the buttons are lifted rather
                // than the row re-aligned — see ComposerButtonLift.
                verticalAlignment = Alignment.Bottom
            ) {
                // Three, not one, and that reverses an earlier decision in
                // this project: the argument was that a composer growing an
                // icon per attachment type runs out of room before it runs
                // out of types. True in general, and beside the point here —
                // these three are not "types of attachment" but the three
                // things people actually reach for, which is why the messaging
                // app this was modelled on puts exactly these three here. The
                // sheet still exists behind the plus for everything else.
                IconButton(
                    onClick = onAttach,
                    enabled = recordingSince == null,
                    modifier = Modifier.padding(bottom = ComposerButtonLift)
                ) {
                    Icon(Icons.Rounded.AttachFile, contentDescription = "Attach")
                }
                IconButton(
                    onClick = onCamera,
                    enabled = recordingSince == null,
                    modifier = Modifier.padding(bottom = ComposerButtonLift)
                ) {
                    Icon(Icons.Rounded.PhotoCamera, contentDescription = "Camera")
                }

                if (recordingSince != null) {
                    // The field is replaced rather than covered: nothing can be
                    // typed one-handed while the other thumb is holding the
                    // microphone down, and a running clock is the one thing worth
                    // knowing at that moment.
                    var elapsed by remember(recordingSince) { mutableLongStateOf(0L) }
                    LaunchedEffect(recordingSince) {
                        while (true) {
                            elapsed = (System.currentTimeMillis() - recordingSince) / 1000
                            // The same beat takes an amplitude reading, because
                            // getMaxAmplitude answers for the time since the last
                            // call — an irregular tick makes bars that stand for
                            // different lengths of recording.
                            onSampleAmplitude()
                            delay(RECORDING_TICK_MS)
                        }
                    }
                    Text(
                        "Recording  ${formatDuration(elapsed)}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp, vertical = 14.dp)
                    )
                } else {
                    TextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 2.dp)
                            .focusRequester(focusRequester),
                        placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        // Where Telegram keeps it, and every messenger since:
                        // inside the field, at its end.
                        trailingIcon = {
                            Row {
                                if (botKeyboardShown != null) {
                                    IconButton(onClick = onBotKeyboardToggle) {
                                        Icon(
                                            if (botKeyboardShown) Icons.Rounded.KeyboardHide
                                            else Icons.Rounded.Keyboard,
                                            contentDescription = if (botKeyboardShown) "Hide bot keyboard"
                                            else "Bot keyboard"
                                        )
                                    }
                                }
                                IconButton(onClick = onStickers) {
                                    Icon(Icons.Rounded.EmojiEmotions, contentDescription = "Stickers")
                                }
                            }
                        },
                        shape = fieldShape,
                        // The field carries its own fill, at the opposite
                        // end of the container ladder from the capsule
                        // around it. An earlier version made every
                        // container colour transparent on the argument that a
                        // filled field inside a filled surface draws a second
                        // shape nobody asked for — which is true about shapes
                        // and wrong about people. With nothing to fill it, the
                        // field was invisible: the words "Message" floated in
                        // a bar whose tappable part could not be told from its
                        // buttons, and the first person to look at it said so.
                        //
                        // A text field has to look like somewhere to type.
                        // That is what the second shape is for.
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor =
                                MaterialTheme.colorScheme.surfaceContainerLowest,
                            unfocusedContainerColor =
                                MaterialTheme.colorScheme.surfaceContainerLowest,
                            disabledContainerColor =
                                MaterialTheme.colorScheme.surfaceContainerLowest,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        ),
                        maxLines = 5
                    )
                }

                if (value.isBlank() && !hasAttachment) {
                    FilledIconButton(
                        // onClick stays empty because this is a hold, not a tap:
                        // the gesture below owns press, release and cancel, and a
                        // tap that fired as well would send an empty recording.
                        onClick = {},
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (recordingSince != null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            }
                        ),
                        modifier = Modifier
                            .padding(bottom = ComposerButtonLift)
                            // The press is read on the Initial pass, before
                            // the button's own clickable sees it. Read on the
                            // Main pass, as this was, it came after the
                            // clickable had already taken the touch — and a
                            // tap detector waits for a touch nobody has
                            // taken, so holding the microphone did nothing
                            // at all. The button keeps its ripple either way.
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(
                                        requireUnconsumed = false,
                                        pass = PointerEventPass.Initial
                                    )
                                    onRecordStart()
                                    // Until the finger lifts, or leaves for
                                    // somewhere else — a scroll, a slide off
                                    // the button — which throws it away.
                                    val lifted = waitForUpOrCancellation(PointerEventPass.Initial)
                                    if (lifted != null) onRecordStop() else onRecordCancel()
                                }
                            }
                    ) {
                        Icon(Icons.Rounded.Mic, contentDescription = "Hold to record")
                    }
                } else {
                    var scheduleMenu by remember { mutableStateOf(false) }
                    Box {
                    FilledIconButton(
                        onClick = onSend,
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .padding(bottom = ComposerButtonLift)
                            .then(
                                if (onSchedule == null) Modifier
                                else Modifier.pointerInput(Unit) {
                                    // Held rather than tapped: the menu opens,
                                    // and the release is swallowed on the
                                    // Initial pass so the button's own click
                                    // never sees it — a hold must not also send.
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                        val released = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                            waitForUpOrCancellation(PointerEventPass.Initial)
                                        }
                                        if (released == null) {
                                            scheduleMenu = true
                                            waitForUpOrCancellation(PointerEventPass.Initial)?.consume()
                                        }
                                    }
                                }
                            )
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send")
                    }
                    DropdownMenu(expanded = scheduleMenu, onDismissRequest = { scheduleMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Schedule message") },
                            leadingIcon = { Icon(Icons.Rounded.Schedule, contentDescription = null) },
                            onClick = {
                                scheduleMenu = false
                                onSchedule?.invoke()
                            }
                        )
                    }
                    }
                }
            }
        }
    }
}

/** The composer capsule's margin above and below; the list stops at its bottom edge. */
internal val COMPOSER_MARGIN = 8.dp

/** Past any half-height the composer reaches on one line: a round end. */
internal val COMPOSER_PILL_CORNER = 64.dp

/** How far the field sits in from the capsule's edge, for concentric corners. */
internal val COMPOSER_FIELD_INSET = 8.dp
