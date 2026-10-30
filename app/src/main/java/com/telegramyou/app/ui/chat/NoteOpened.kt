package com.telegramyou.app.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.telegramyou.app.i18n.tr
import com.telegramyou.app.telegram.model.ChatMessage
import com.telegramyou.app.ui.icons.Symbols
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A note opened (2.9): it grows out of its card to the whole screen, as a
 * chat grows out of its row — Material's container transform — and shrinks
 * back into the card to close: by its close button, by back, by a tap
 * beside it while it is still growing, or by being swiped up or down.
 *
 * Done here rather than with the shared-transition API the chat uses,
 * because a finger drives this one: the swipe moves the container itself,
 * and letting go far enough or fast enough carries on into the card. The
 * rules are the chat's: the full note is laid out once, at full size, and
 * scaled into the growing container; the card is scaled up from its own
 * size; and the two cross-fade.
 *
 * [from] is where the card is in the window; [note] null closes it.
 */
@Composable
internal fun NoteOpened(
    note: List<ChatMessage>?,
    from: Rect?,
    onClosed: () -> Unit,
    onShowInChat: (List<ChatMessage>) -> Unit,
    onOpenPicture: (ChatMessage) -> Unit,
    onCopy: (List<ChatMessage>) -> Unit,
    onEdit: ((ChatMessage) -> Unit)?,
    onTag: (List<ChatMessage>) -> Unit,
    onRemind: (List<ChatMessage>) -> Unit,
    onDelete: (List<ChatMessage>) -> Unit
) {
    var shown by remember { mutableStateOf<List<ChatMessage>?>(null) }
    var origin by remember { mutableStateOf<Rect?>(null) }
    var box by remember { mutableStateOf(Rect.Zero) }
    val progress = remember { Animatable(0f) }
    val drag = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(note) {
        if (note == null || note.first().id == shown?.first()?.id) return@LaunchedEffect
        shown = note
        origin = from
        closing = false
        drag.snapTo(0f)
        progress.snapTo(0f)
        progress.animateTo(1f, OPEN)
    }

    val current = shown ?: return

    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            coroutineScope {
                launch { drag.animateTo(0f, OPEN) }
                launch { progress.animateTo(0f, OPEN) }
            }
            shown = null
            onClosed()
        }
    }

    // Done as itself, without the way back into the card: what it opens
    // takes the screen next.
    fun leave(action: () -> Unit) {
        shown = null
        onClosed()
        action()
    }

    BackHandler { close() }

    val density = LocalDensity.current
    val height = box.height.coerceAtLeast(1f)
    // A swipe past an eighth of the screen, or a flick, closes it.
    val dismiss = remember(height) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val pulled = drag.value
                if (pulled == 0f || source != NestedScrollSource.UserInput) return Offset.Zero
                // Back towards the middle first, before the note scrolls.
                val next = pulled + available.y
                val settled = if (pulled > 0f) next.coerceAtLeast(0f) else next.coerceAtMost(0f)
                scope.launch { drag.snapTo(settled) }
                return Offset(0f, settled - pulled)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero
                scope.launch { drag.snapTo(drag.value + available.y) }
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val pulled = drag.value
                if (pulled == 0f) return Velocity.Zero
                val flicked = abs(available.y) > FLICK && (available.y > 0f) == (pulled > 0f)
                if (abs(pulled) > height * DISMISS_SHARE || flicked) close() else drag.animateTo(0f, OPEN)
                return available
            }
        }
    }

    val p = progress.value
    val pulledShare = (abs(drag.value) / height).coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { box = it.boundsInWindow() }
    ) {
        // The conversation dimmed behind it, less as it is swiped away.
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = SCRIM * p * (1f - pulledShare * 2f).coerceIn(0f, 1f)))
                .pointerInput(Unit) { detectTapGestures { close() } }
        )
        if (box.width <= 0f) return@Box
        val full = Rect(0f, 0f, box.width, box.height)
        val start = (origin ?: Rect(Offset(box.width / 4f, box.height / 3f), androidx.compose.ui.geometry.Size(box.width / 2f, box.height / 4f)))
            .translate(-box.left, -box.top)
        val rect = Rect(
            left = lerpF(start.left, full.left, p),
            top = lerpF(start.top, full.top, p),
            right = lerpF(start.right, full.right, p),
            bottom = lerpF(start.bottom, full.bottom, p)
        )
        val corner = lerp(CARD_CORNER, 0.dp, p)
        val (tone, onTone) = noteTone(current)
        Box(
            Modifier
                .offset { IntOffset(rect.left.roundToInt(), (rect.top + drag.value).roundToInt()) }
                .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
                .graphicsLayer {
                    val shrink = 1f - pulledShare * 0.25f
                    scaleX = shrink
                    scaleY = shrink
                }
                .clip(RoundedCornerShape(corner))
                .background(tone)
                .nestedScroll(dismiss)
        ) {
            // The card, scaled up from its own size, giving way early.
            if (start.width > 0f) {
                Scaled(
                    width = with(density) { start.width.toDp() },
                    height = with(density) { start.height.toDp() },
                    scale = rect.width / start.width,
                    alpha = (1f - p / 0.35f).coerceIn(0f, 1f)
                ) {
                    NoteCard(note = current, onOpen = {}, onPhotoVisible = {})
                }
            }
            // The note, laid out once at full size and scaled into the container.
            Scaled(
                width = with(density) { full.width.toDp() },
                height = with(density) { full.height.toDp() },
                scale = rect.width / full.width,
                alpha = ((p - 0.15f) / 0.5f).coerceIn(0f, 1f)
            ) {
                NoteFull(
                    note = current,
                    contentColor = onTone,
                    onClose = ::close,
                    onShowInChat = { leave { onShowInChat(current) } },
                    onOpenPicture = { picture -> leave { onOpenPicture(picture) } },
                    onCopy = { leave { onCopy(current) } },
                    onEdit = onEdit?.let { edit ->
                        current.singleOrNull()
                            ?.takeIf { it.canBeEdited && noteBody(it).isNotEmpty() }
                            ?.let { single -> { leave { edit(single) } } }
                    },
                    onTag = { leave { onTag(current) } },
                    onRemind = { leave { onRemind(current) } },
                    onDelete = { leave { onDelete(current) } }
                )
            }
        }
    }
}

/** [content] laid out at [width] × [height] from the top-left, and drawn scaled by [scale]. */
@Composable
private fun Scaled(
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    scale: Float,
    alpha: Float,
    content: @Composable () -> Unit
) {
    if (alpha <= 0f) return
    Box(
        Modifier
            .wrapContentSize(Alignment.TopStart, unbounded = true)
            .requiredSize(width, height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
                this.alpha = alpha
            }
    ) { content() }
}

/** The note at full size: close at the top, all of it in the middle, what can be done with it at the foot. */
@Composable
private fun NoteFull(
    note: List<ChatMessage>,
    contentColor: Color,
    onClose: () -> Unit,
    onShowInChat: () -> Unit,
    onOpenPicture: (ChatMessage) -> Unit,
    onCopy: () -> Unit,
    onEdit: (() -> Unit)?,
    onTag: () -> Unit,
    onRemind: () -> Unit,
    onDelete: () -> Unit
) {
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides contentColor) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(Modifier.fillMaxWidth().padding(4.dp)) {
                IconButton(onClick = onClose) {
                    Icon(Symbols.Close, contentDescription = tr("Close note", "Закрыть заметку"))
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                note.filter(::noteHasPicture).forEach { picture ->
                    Box(
                        Modifier
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clip(MaterialTheme.shapes.large)
                            .combinedClickable(onClick = { onOpenPicture(picture) })
                    ) { NotePicture(picture, onPhotoVisible = {}) }
                }
                note.first().forwardedFrom?.let { source ->
                    Text(
                        tr("From $source", "От $source"),
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColor.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                    )
                }
                val body = noteBody(note)
                if (body.isNotEmpty()) {
                    val title = body.substringBefore('\n').takeIf { '\n' in body }
                    SelectionContainer {
                        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                            if (title != null) {
                                Text(title, style = MaterialTheme.typography.headlineSmall)
                                Spacer(Modifier.height(12.dp))
                            }
                            Text(
                                if (title != null) body.substringAfter('\n').trim() else body,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
                note.mapNotNull(::noteAttachment).forEach { line ->
                    Text(line, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
                }
                Text(
                    note.last().timeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
            // A row of actions at the foot, as Keep's note has its own.
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                NoteAction(Symbols.Chat, tr("Show in chat", "Показать в чате"), onShowInChat)
                if (noteBody(note).isNotEmpty()) NoteAction(Symbols.ContentCopy, tr("Copy", "Копировать"), onCopy)
                onEdit?.let { NoteAction(Symbols.Edit, tr("Edit", "Изменить"), it) }
                NoteAction(Symbols.Tag, tr("Tag", "Тег"), onTag)
                NoteAction(Symbols.NotificationsActive, tr("Remind me", "Напомнить"), onRemind)
                NoteAction(Symbols.Delete, tr("Delete", "Удалить"), onDelete)
            }
        }
    }
}

@Composable
private fun NoteAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, contentDescription = label) }
}

private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t

/** As a chat opens: the expressive pace, without its bounce. */
private val OPEN = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 380f)

/** A card's corners, which the note's container starts from. */
private val CARD_CORNER = 16.dp

/** The conversation's dimming behind a note opened whole. */
private const val SCRIM = 0.4f

/** How far a note is swiped before letting go closes it, as a share of the screen. */
private const val DISMISS_SHARE = 0.12f

/** A flick fast enough to close it, in pixels a second. */
private const val FLICK = 1800f
