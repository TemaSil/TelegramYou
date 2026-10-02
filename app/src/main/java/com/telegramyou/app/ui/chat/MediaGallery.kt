package com.telegramyou.app.ui.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.DialogWindowProvider
import com.telegramyou.app.ui.motion.LocalReduceMotion
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.telegramyou.app.telegram.model.ChatMessage
import com.telegramyou.app.ui.media.FileTransfer
import com.telegramyou.app.telegram.model.MessageContentType

/**
 * A chat's photos and videos, full-screen, one swipe apart — as the official
 * client pages through them. [items] come from galleryOf; the pager opens on
 * [startId] and keeps each page by its message id, so older media loading in
 * behind does not move the one being looked at.
 *
 * Each page is the viewer it always was: a photo zooms, pans and is thrown
 * away by a drag down (PhotoPage), a video plays with its own controls
 * (VideoPage) — only the page in view, so a neighbour holds no player. The
 * pager is still while a photo is zoomed: a sideways drag then moves the
 * photo, not the gallery.
 *
 * [onPage] is told which message came into view, so what it needs — a
 * photo's full size, a video's file — is fetched as it is reached.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MediaGallery(
    items: List<ChatMessage>,
    startId: Long,
    transfers: Map<Int, FileTransfer>,
    onPage: (ChatMessage) -> Unit,
    onDismiss: () -> Unit
) {
    if (items.isEmpty()) return
    val origins = LocalMediaOrigins.current
    val reduceMotion = LocalReduceMotion.current
    // 0 is the photo where it sits in its bubble, 1 the photo full
    // screen; see MediaTransition. Opened on the theme's spatial spring
    // — Expressive's, with its overshoot, unless Less motion — and
    // closed on the same, back into whichever photo is in view.
    val progress = remember { Animatable(0f) }
    var viewport by remember { mutableStateOf<Rect?>(null) }
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val close: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                progress.animateTo(0f, spatial)
                onDismiss()
            }
        }
    }

    // Back is the dialog's to report; it closes the way a tap on Close does.
    Dialog(
        onDismissRequest = { close() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // The window's own fade and dim are off: the gallery draws both
        // itself, in step with the photo it grows out of. Left on, the
        // window faded over the top of that, and on a slow phone it could
        // be caught half gone, over a chat that was already back.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.setWindowAnimations(0)
            window?.setDimAmount(0f)
        }
        val pager = rememberPagerState(
            initialPage = items.indexOfFirst { it.id == startId }.coerceAtLeast(0)
        ) { items.size }
        var zoomed by remember { mutableStateOf(false) }
        val latestItems by rememberUpdatedState(items)
        val latestOnPage by rememberUpdatedState(onPage)
        LaunchedEffect(pager.currentPage) {
            zoomed = false
            latestItems.getOrNull(pager.currentPage)?.let(latestOnPage)
        }

        LaunchedEffect(viewport != null) {
            if (viewport != null) progress.animateTo(1f, spatial)
        }

        val current = items.getOrNull(pager.currentPage)
        // Where the page in view goes back to: taken when the close begins,
        // since the bubble may have moved while the gallery was open.
        val target = remember(current?.id, closing) {
            current?.let { origins?.get(it.id) }
        }
        val transition = MediaTransition(
            origin = if (reduceMotion) null else target,
            aspect = current?.let(::aspectOf) ?: 1f,
            viewport = viewport
        )
        val backdrop = if (current?.contentType == MessageContentType.Video) 1f else PHOTO_SCRIM

        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { viewport = Rect(it.positionOnScreen(), it.size.toSize()) }
                .drawBehind {
                    drawRect(Color.Black.copy(alpha = backdrop * progress.value.coerceIn(0f, 1f)))
                }
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = progress.value
                        clip = true
                        shape = transition.clipShape(p, size, ORIGIN_CORNER.toPx())
                        // Nothing until the window has been measured: the
                        // first frame does not yet know where to start from.
                        alpha = if (viewport == null) 0f else transition.contentAlpha(p)
                    }
            ) {
                HorizontalPager(
                    state = pager,
                    userScrollEnabled = !zoomed && !closing,
                    key = { page -> items[page].id },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val p = progress.value
                            val scale = transition.scale(p, size)
                            scaleX = scale
                            scaleY = scale
                            val shift = transition.shift(p, size)
                            translationX = shift.x
                            translationY = shift.y
                        }
                ) { page ->
                    val message = items[page]
                    val video = message.video
                    if (message.contentType == MessageContentType.Video && video != null) {
                        VideoPage(
                            video = video,
                            title = message.text,
                            transfer = video.fileId?.let { transfers[it] },
                            active = page == pager.currentPage,
                            onClose = close
                        )
                    } else {
                        PhotoPage(
                            path = message.photoPath,
                            caption = message.text,
                            onDismiss = close,
                            onZoomChanged = { if (page == pager.currentPage) zoomed = it },
                            closing = closing
                        )
                    }
                }
            }
            if (items.size > 1) {
                Text(
                    "${pager.currentPage + 1} of ${items.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 20.dp)
                        .graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) }
                )
            }
        }
    }
}

/** A photo's or a video's width over its height, as the bubble reserved it. */
private fun aspectOf(message: ChatMessage): Float =
    message.video?.takeIf { message.contentType == MessageContentType.Video }?.aspect
        ?: message.photoAspect

/** PhotoPage's own scrim, matched outside the photo so the two meet unseen. */
private const val PHOTO_SCRIM = 0.92f

/** About a bubble's corners, which a photo in one is cut to. */
private val ORIGIN_CORNER = 16.dp
