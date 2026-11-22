package com.telegramyou.app.ui.people

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.telegramyou.app.i18n.tr
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.ProfilePhoto
import com.telegramyou.app.telegram.model.TelegramUser
import com.telegramyou.app.ui.chat.PhotoPage
import com.telegramyou.app.ui.chat.PlayerControlButton
import com.telegramyou.app.ui.icons.Symbols
import kotlinx.coroutines.CancellationException

/** A contact's visible photos, loaded in Telegram's order as the pager advances. */
@Composable
internal fun ProfilePhotosViewer(user: TelegramUser, repository: TelegramRepository, onClose: () -> Unit) {
    var photos by remember(user.id) { mutableStateOf<List<ProfilePhoto>>(emptyList()) }
    var nextOffset by remember(user.id) { mutableStateOf<Int?>(0) }
    var error by remember(user.id) { mutableStateOf(false) }
    var retry by remember(user.id) { mutableIntStateOf(0) }
    val pager = rememberPagerState { photos.size }
    var zoomed by remember(pager.currentPage) { mutableStateOf(false) }
    var controls by remember { mutableStateOf(true) }

    LaunchedEffect(repository, user.id, pager.settledPage, retry) {
        error = false
        while (pager.settledPage >= photos.size - 3) {
            val offset = nextOffset ?: break
            try {
                val page = repository.profilePhotos(user.id, offset)
                photos = (photos + page.photos).distinctBy { it.id }
                nextOffset = page.nextOffset?.takeIf { it > offset }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = true
                break
            }
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (photos.isNotEmpty()) {
                HorizontalPager(state = pager, key = { photos[it].id }, userScrollEnabled = !zoomed,
                    modifier = Modifier.fillMaxSize()) { index ->
                    val photo = photos[index]
                    var path by remember(photo) { mutableStateOf(photo.path) }
                    var failed by remember(photo) { mutableStateOf(false) }
                    var retryPhoto by remember(photo) { mutableIntStateOf(0) }
                    val selected = index == pager.currentPage
                    LaunchedEffect(photo, selected, retryPhoto) {
                        if (selected && path == null) {
                            failed = false
                            path = try {
                                photo.fileId?.let { repository.downloadFile(it) }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                null
                            }
                            failed = path == null
                        }
                    }
                    if (failed) {
                        PhotoLoadError(Modifier.fillMaxSize()) { retryPhoto++ }
                    } else {
                        PhotoPage(path, "", onClose, onZoomChanged = { if (index == pager.currentPage) zoomed = it },
                            showClose = false, onTap = { controls = !controls }, showCaption = false, mini = photo.mini)
                    }
                }
            } else if (error) {
                PhotoLoadError(Modifier.align(Alignment.Center)) { retry++ }
            } else if (nextOffset != null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            } else {
                Text(tr("No profile photos", "Нет фотографий профиля"), Modifier.align(Alignment.Center), color = Color.White)
            }

            if (controls || photos.isEmpty()) {
                PlayerControlButton(Symbols.Close, tr("Close", "Закрыть"), onClose,
                    Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp).size(48.dp))
                Text(user.displayName, Modifier.align(Alignment.TopCenter).statusBarsPadding()
                    .padding(horizontal = 72.dp, vertical = 24.dp),
                    color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                if (photos.isNotEmpty()) {
                    Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        if (error) TextButton(onClick = { retry++ }) {
                            Text(tr("Load more photos", "Загрузить ещё фотографии"), color = Color.White)
                        }
                        if (photos.size > 1) Text(
                            tr("${pager.currentPage + 1} of ${photos.size}", "${pager.currentPage + 1} из ${photos.size}"),
                            color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoLoadError(modifier: Modifier, onRetry: () -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(tr("Could not load profile photos", "Не удалось загрузить фотографии профиля"), color = Color.White)
            TextButton(onClick = onRetry) { Text(tr("Retry", "Повторить"), color = Color.White) }
        }
    }
}
