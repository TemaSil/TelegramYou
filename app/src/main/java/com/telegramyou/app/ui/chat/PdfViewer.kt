package com.telegramyou.app.ui.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.telegramyou.app.i18n.tr
import com.telegramyou.app.ui.icons.Symbols
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal fun FileToOpen.isPdf(): Boolean = mime?.substringBefore(';')?.equals("application/pdf", true) == true ||
    name?.endsWith(".pdf", true) == true || path.substringBefore('?').endsWith(".pdf", true)

/** PDF pixels come from Android's renderer; controls remain stock Material. */
internal class PdfDocument(private val context: Context, private val path: String) {
    private val mutex = Mutex()
    private val closed = AtomicBoolean(false)
    private var descriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private val pages = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private fun reader(): PdfRenderer {
        check(!closed.get())
        return renderer ?: run {
            val uri = Uri.parse(path)
            val fd = if (uri.scheme == "content" || uri.scheme == "android.resource") {
                checkNotNull(context.contentResolver.openFileDescriptor(uri, "r"))
            } else ParcelFileDescriptor.open(File(if (uri.scheme == "file") uri.path.orEmpty() else path), ParcelFileDescriptor.MODE_READ_ONLY)
            try { PdfRenderer(fd).also { descriptor = fd; renderer = it } }
            catch (error: Exception) { fd.close(); throw error }
        }
    }

    suspend fun dimensions(): List<Float> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val pdf = reader()
            List(pdf.pageCount) { index -> pdf.openPage(index).use { it.width.toFloat() / it.height.coerceAtLeast(1) } }
        }
    }

    suspend fun page(index: Int, requestedWidth: Int): Bitmap = withContext(Dispatchers.IO) {
        mutex.withLock {
            val width = requestedWidth.coerceIn(64, 1600)
            val key = "$index:$width"
            pages.get(key) ?: reader().openPage(index).use { page ->
                val aspect = page.width.toFloat() / page.height.coerceAtLeast(1)
                val targetWidth = minOf(width, kotlin.math.sqrt(2_000_000f * aspect).toInt().coerceAtLeast(1))
                val bitmap = Bitmap.createBitmap(targetWidth, (targetWidth / aspect).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(AndroidColor.WHITE)
                try { page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
                catch (error: Exception) { bitmap.recycle(); throw error }
                bitmap.also { pages.put(key, it) }
            }
        }
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        val cleanup = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        cleanup.launch {
            try { mutex.withLock { renderer?.close(); descriptor?.close(); renderer = null; descriptor = null; pages.evictAll() } }
            finally { cleanup.cancel() }
        }
    }
}

private val pdfThumbnails = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = value.byteCount
}

/** Only completed local PDFs are opened; a thumbnail never starts a download. */
@Composable
internal fun PdfThumbnail(file: FileToOpen, modifier: Modifier = Modifier, fallback: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    val bitmap by produceState<Bitmap?>(null, file.path) {
        value = pdfThumbnails.get(file.path)
        if (value == null && file.path.isNotBlank() && file.isPdf()) {
            val document = PdfDocument(context, file.path)
            try {
                value = document.page(0, 256).also { pdfThumbnails.put(file.path, it) }
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (_: Exception) { /* An unreadable or encrypted PDF keeps its file badge. */ }
            finally { document.close() }
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) } ?: fallback()
    }
}

/** One file-opening path for chat bubbles, profile files and Downloads. */
@Composable
internal fun DocumentOpener(file: FileToOpen, onClosed: () -> Unit, onRefused: (String?) -> Unit) {
    val context = LocalContext.current
    if (file.isPdf()) {
        PdfViewer(file, onClosed)
    } else LaunchedEffect(file) {
        if (MediaActions.openFile(context, file.path, file.mime, file.name)) onClosed() else onRefused(file.name)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PdfViewer(file: FileToOpen, onClose: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val document = remember(file.path) { PdfDocument(context, file.path) }
    DisposableEffect(document) { onDispose { document.close() } }
    var dimensions by remember(document) { mutableStateOf<List<Float>?>(null) }
    var failed by remember(document) { mutableStateOf(false) }
    LaunchedEffect(document) {
        try { dimensions = document.dimensions() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    val preferences = remember { context.getSharedPreferences("pdf-positions", Context.MODE_PRIVATE) }
    val positionKey = remember(file.path) {
        java.security.MessageDigest.getInstance("SHA-256").digest(file.path.toByteArray()).joinToString("") { "%02x".format(it) }
    }
    var zoom by rememberSaveable(file.path) { mutableFloatStateOf(1f) }
    var menu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(file.name ?: "PDF", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = { IconButton(onClick = onClose) { Icon(Symbols.ArrowBack, tr("Back", "Назад")) } },
                    actions = {
                        TextButton(onClick = { zoom = (zoom / 1.25f).coerceAtLeast(1f) }, enabled = zoom > 1f,
                            modifier = Modifier.semantics { contentDescription = tr("Zoom out", "Уменьшить") }) { Text("−") }
                        TextButton(onClick = { zoom = (zoom * 1.25f).coerceAtMost(4f) }, enabled = zoom < 4f,
                            modifier = Modifier.semantics { contentDescription = tr("Zoom in", "Увеличить") }) { Text("+") }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Symbols.MoreVert, tr("More", "Ещё")) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text(tr("Open in…", "Открыть в…")) }, onClick = {
                                    menu = false
                                    MediaActions.openFile(context, file.path, file.mime, file.name)
                                })
                                DropdownMenuItem(text = { Text(tr("Share", "Поделиться")) }, onClick = {
                                    menu = false
                                    scope.launch { withContext(Dispatchers.IO) { MediaActions.share(context, file.path, "application/pdf", file.name ?: "document.pdf", "") } }
                                })
                            }
                        }
                    }
                )
            }
        ) { padding ->
            BoxWithConstraints(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surfaceContainer)) {
                val pageAspects = dimensions
                when {
                    failed -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(tr("Could not open this PDF. It may be damaged or password protected.", "Не удалось открыть PDF. Возможно, он повреждён или защищён паролем."))
                        TextButton(onClick = { MediaActions.openFile(context, file.path, file.mime, file.name) }) { Text(tr("Open in another app", "Открыть в другом приложении")) }
                    }
                    pageAspects == null -> LoadingIndicator(Modifier.align(Alignment.Center))
                    pageAspects.isEmpty() -> Text(tr("This PDF has no pages", "В этом PDF нет страниц"), Modifier.align(Alignment.Center))
                    else -> {
                        val initialPage = remember(document) { preferences.getInt(positionKey, 0).coerceIn(0, pageAspects.lastIndex) }
                        val list = rememberLazyListState(initialPage)
                        val visiblePage by remember(list) { derivedStateOf {
                            val layout = list.layoutInfo
                            layout.visibleItemsInfo.maxByOrNull { item ->
                                minOf(item.offset + item.size, layout.viewportEndOffset) - maxOf(item.offset, layout.viewportStartOffset)
                            }?.index ?: list.firstVisibleItemIndex
                        } }
                        val horizontal = rememberScrollState()
                        val width = maxWidth * zoom
                        val pixels = with(LocalDensity.current) { width.roundToPx() }
                        val gestures = rememberTransformableState { change, pan, _ ->
                            zoom = (zoom * change).coerceIn(1f, 4f)
                            scope.launch { horizontal.scrollBy(-pan.x); list.scrollBy(-pan.y) }
                        }
                        LaunchedEffect(list, positionKey) {
                            snapshotFlow { visiblePage }.collect { preferences.edit().putInt(positionKey, it).apply() }
                        }
                        Box(Modifier.fillMaxSize().transformable(gestures, canPan = { zoom > 1f }).horizontalScroll(horizontal)) {
                            LazyColumn(state = list, modifier = Modifier.width(width).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 56.dp)) {
                                items(pageAspects.size, key = { it }) { page ->
                                    var renderFailed by remember(document, page, pixels) { mutableStateOf(false) }
                                    val bitmap by produceState<Bitmap?>(null, document, page, pixels) {
                                        try { value = document.page(page, pixels) }
                                        catch (cancelled: CancellationException) { throw cancelled }
                                        catch (_: Exception) { renderFailed = true }
                                    }
                                    Box(Modifier.fillMaxWidth().aspectRatio(pageAspects[page]).background(Color.White)
                                        .semantics { contentDescription = tr("Page ${page + 1}", "Страница ${page + 1}") }, contentAlignment = Alignment.Center) {
                                        bitmap?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillWidth) }
                                            ?: if (renderFailed) Text(tr("Could not read this page", "Не удалось прочитать страницу"), color = Color.Black)
                                            else LoadingIndicator()
                                    }
                                }
                            }
                        }
                        Surface(Modifier.align(Alignment.BottomCenter).padding(8.dp), shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Text(tr("${visiblePage + 1} of ${pageAspects.size}", "${visiblePage + 1} из ${pageAspects.size}"), Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                        }
                    }
                }
            }
        }
    }
}
