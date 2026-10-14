package com.telegramyou.app.ui.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.telegramyou.app.ui.icons.Symbols
import com.telegramyou.app.ui.theme.DarkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A photo waiting in the composer, edited before it goes (2.2): turned a
 * quarter at a time, cropped by its corners or moved whole, and put under a
 * spoiler — the things the official client offers in the same place.
 *
 * Opened by a tap on the photo in the tray. Done writes the edit into a new
 * picture in the app's cache and hands back its uri, which takes the
 * original's place in the draft; a photo only put under a spoiler keeps its
 * own file. The frame is drawn by hand because Material ships no cropper —
 * the one place on this screen nothing stock would do.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PhotoEditor(
    uri: String,
    spoiler: Boolean,
    /** The edited picture's uri, or null when only the spoiler changed; and the spoiler. */
    onDone: (edited: String?, spoiler: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var edit by remember(uri) { mutableStateOf(PhotoEdit()) }
    var hidden by rememberSaveable(uri) { mutableStateOf(spoiler) }
    var saving by remember { mutableStateOf(false) }
    // Decoded once, upright as the camera meant it (Coil reads the EXIF),
    // and large enough to be sent from.
    val original by produceState<Bitmap?>(null, uri) { value = decodeForEditing(context, uri) }
    // The picture as it stands after the turns: what the frame is drawn on.
    val turned = remember(original, edit.quarterTurns) { original?.let { turnedLeft(it, edit.quarterTurns) } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        // Black in either theme, so dark in either theme: in the light one
        // its buttons were dark on the black.
        DarkTheme {
        Scaffold(
            containerColor = Color.Black,
            contentColor = MaterialTheme.colorScheme.onSurface,
            topBar = {
                TopAppBar(
                    title = { Text("Edit photo") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Symbols.Close, contentDescription = "Cancel") }
                    },
                    actions = {
                        TextButton(
                            enabled = original != null && !saving,
                            onClick = {
                                val picture = original ?: return@TextButton
                                if (edit.isNone) {
                                    onDone(null, hidden)
                                    return@TextButton
                                }
                                saving = true
                                scope.launch {
                                    val written = withContext(Dispatchers.Default) { render(context, picture, edit) }
                                    saving = false
                                    onDone(written, hidden)
                                }
                            }
                        ) { Text("Done") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
                )
            },
            bottomBar = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp)
                ) {
                    FilledTonalIconButton(onClick = { edit = edit.turnedLeft() }) {
                        Icon(Symbols.Rotate90DegreesCcw, contentDescription = "Rotate")
                    }
                    TextButton(enabled = !edit.isNone, onClick = { edit = PhotoEdit() }) { Text("Reset") }
                    FilterChip(
                        selected = hidden,
                        onClick = { hidden = !hidden },
                        label = { Text("Spoiler") },
                        leadingIcon = {
                            Icon(
                                if (hidden) Symbols.VisibilityOff else Symbols.Visibility,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    )
                }
            }
        ) { padding ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp)
            ) {
                val picture = turned
                if (picture == null || saving) {
                    LoadingIndicator()
                } else {
                    CropFrame(picture, edit.crop, onCrop = { edit = edit.copy(crop = it) })
                }
            }
        }
        }
    }
}

/**
 * The picture fitted into the space, the part outside the frame dimmed, and
 * the frame with its corners — dragged by a corner, or moved by its inside.
 */
@Composable
private fun CropFrame(picture: Bitmap, crop: CropRect, onCrop: (CropRect) -> Unit) {
    val image = remember(picture) { picture.asImageBitmap() }
    val latest by rememberUpdatedState(crop)
    BoxWithConstraints(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val scale = minOf(constraints.maxWidth.toFloat() / picture.width, constraints.maxHeight.toFloat() / picture.height)
        val shown = Size(picture.width * scale, picture.height * scale)
        val reach = with(density) { HANDLE_REACH.toPx() }
        Box(
            modifier = Modifier
                .size(with(density) { shown.width.toDp() }, with(density) { shown.height.toDp() })
                .semantics { contentDescription = "Photo, cropped by its corners" }
        ) {
            Image(image, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
            var handle by remember { mutableStateOf<CropHandle?>(null) }
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(shown) {
                        detectDragGestures(
                            onDragStart = { at ->
                                handle = latest.handleAt(
                                    at.x / shown.width, at.y / shown.height,
                                    reach / shown.width, reach / shown.height
                                )
                            },
                            onDragEnd = { handle = null },
                            onDragCancel = { handle = null },
                            onDrag = { change, amount ->
                                val taken = handle ?: return@detectDragGestures
                                change.consume()
                                onCrop(latest.dragged(taken, amount.x / shown.width, amount.y / shown.height))
                            }
                        )
                    }
            ) {
                val l = crop.left * size.width
                val t = crop.top * size.height
                val r = crop.right * size.width
                val b = crop.bottom * size.height
                val dim = Color.Black.copy(alpha = 0.6f)
                drawRect(dim, Offset.Zero, Size(size.width, t))
                drawRect(dim, Offset(0f, b), Size(size.width, size.height - b))
                drawRect(dim, Offset(0f, t), Size(l, b - t))
                drawRect(dim, Offset(r, t), Size(size.width - r, b - t))
                // Thirds, as every cropper draws them, to place the subject by.
                val thirds = Color.White.copy(alpha = 0.35f)
                for (i in 1..2) {
                    val x = l + (r - l) * i / 3
                    val y = t + (b - t) * i / 3
                    drawLine(thirds, Offset(x, t), Offset(x, b), strokeWidth = 1.dp.toPx())
                    drawLine(thirds, Offset(l, y), Offset(r, y), strokeWidth = 1.dp.toPx())
                }
                drawRect(Color.White, Offset(l, t), Size(r - l, b - t), style = Stroke(width = 1.5.dp.toPx()))
                // The corners, thicker, where a finger takes them.
                val arm = 18.dp.toPx()
                val thick = 4.dp.toPx()
                listOf(Offset(l, t) to Offset(1f, 1f), Offset(r, t) to Offset(-1f, 1f),
                    Offset(l, b) to Offset(1f, -1f), Offset(r, b) to Offset(-1f, -1f)).forEach { (corner, toward) ->
                    drawLine(Color.White, corner, corner + Offset(arm * toward.x, 0f), strokeWidth = thick)
                    drawLine(Color.White, corner, corner + Offset(0f, arm * toward.y), strokeWidth = thick)
                }
            }
        }
    }
}

/** How far from a corner a finger still takes it. */
private val HANDLE_REACH = 32.dp

/** The longest side a photo is decoded and sent at: Telegram's own limit for a photo. */
private const val EDIT_MAX_SIDE = 2560

private suspend fun decodeForEditing(context: Context, uri: String): Bitmap? {
    val request = ImageRequest.Builder(context)
        .data(uri)
        .size(EDIT_MAX_SIDE)
        .allowHardware(false)
        .build()
    val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return null
    return result.image.toBitmap()
}

/** [bitmap] turned [quarters] quarter turns to the left. */
private fun turnedLeft(bitmap: Bitmap, quarters: Int): Bitmap {
    if (quarters % 4 == 0) return bitmap
    val matrix = Matrix().apply { postRotate(-90f * quarters) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

/** The edit applied to [picture] and written to the cache; its uri, or null if it could not be. */
private fun render(context: Context, picture: Bitmap, edit: PhotoEdit): String? = runCatching {
    val turned = turnedLeft(picture, edit.quarterTurns)
    val (x, y, w, h) = edit.crop.inPixels(turned.width, turned.height).toList()
    val cropped = Bitmap.createBitmap(turned, x, y, w, h)
    val dir = File(context.cacheDir, "edited").apply { mkdirs() }
    val file = File(dir, "photo-${System.currentTimeMillis()}.jpg")
    file.outputStream().use { cropped.compress(Bitmap.CompressFormat.JPEG, EDIT_QUALITY, it) }
    android.net.Uri.fromFile(file).toString()
}.getOrNull()

private const val EDIT_QUALITY = 92
