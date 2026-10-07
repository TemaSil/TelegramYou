package com.telegramyou.app.ui.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import com.telegramyou.app.TelegramYouApp
import com.telegramyou.app.ui.icons.Symbols
import com.telegramyou.app.ui.theme.appColorScheme
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
        // Out to the edges, under the status and navigation bars: inside
        // them the bars showed the chat through, which the owner saw as a
        // transparent navigation bar over the viewer (1.9).
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        // The viewer is dark whatever the app is, in the wallpaper's colours
        // all the same: its buttons are tonal ones of the dark scheme.
        val appearance by (LocalContext.current.applicationContext as TelegramYouApp)
            .appearance.settings.collectAsState()
        MaterialExpressiveTheme(
            colorScheme = appColorScheme(
                darkTheme = true,
                dynamicColor = appearance.dynamicColor,
                accent = appearance.accent,
                pureBlack = appearance.pureBlack
            ),
            motionScheme = MaterialTheme.motionScheme,
            shapes = MaterialTheme.shapes,
            typography = MaterialTheme.typography
        ) {
        // The window's own fade and dim are off: the gallery draws both
        // itself, in step with the photo it grows out of. Left on, the
        // window faded over the top of that, and on a slow phone it could
        // be caught half gone, over a chat that was already back.
        val view = LocalView.current
        val window = (view.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.setWindowAnimations(0)
            window?.setDimAmount(0f)
            window?.let { w ->
                // Light icons on the dark viewer, and no scrim of the
                // system's own behind the navigation bar.
                WindowCompat.getInsetsController(w, view).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
                if (android.os.Build.VERSION.SDK_INT >= 29) w.isNavigationBarContrastEnforced = false
            }
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

        // The controls, up or tapped away on a video (2.0); back with each
        // new page, so a photo swiped to is never left without its Close.
        var chrome by remember { mutableStateOf(true) }
        LaunchedEffect(pager.currentPage) { chrome = true }

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
                            onClose = close,
                            showClose = false,
                            chrome = chrome,
                            onToggleChrome = { chrome = !chrome }
                        )
                    } else {
                        PhotoPage(
                            path = message.photoPath,
                            caption = message.text,
                            onDismiss = close,
                            onZoomChanged = { if (page == pager.currentPage) zoomed = it },
                            closing = closing,
                            showClose = false
                        )
                    }
                }
            }
            // The viewer's own chrome, over every page and still while they
            // swipe: Close as a tonal button, and where this is among the
            // chat's photos as a pill — Expressive's containers rather than
            // white glyphs straight on the picture.
            androidx.compose.animation.AnimatedVisibility(
                visible = chrome,
                enter = androidx.compose.animation.fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = androidx.compose.animation.fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec())
            ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(12.dp)
                    .graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) }
            ) {
                FilledTonalIconButton(
                    onClick = close,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(Symbols.Close, contentDescription = "Close")
                }
                if (items.size > 1) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Text(
                            "${pager.currentPage + 1} of ${items.size}",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
            }
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
