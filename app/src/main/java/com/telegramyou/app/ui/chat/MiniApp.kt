package com.telegramyou.app.ui.chat

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.telegramyou.app.telegram.model.WebAppMainButton
import com.telegramyou.app.telegram.model.WebAppTheme
import com.telegramyou.app.ui.icons.Symbols
import org.json.JSONObject

/** The client's colours as a Mini App is told them; see WebAppTheme. */
internal fun ColorScheme.toWebAppTheme(): WebAppTheme = WebAppTheme(
    background = surface.toArgb(),
    secondaryBackground = surfaceContainer.toArgb(),
    headerBackground = surfaceContainerLow.toArgb(),
    bottomBarBackground = surfaceContainerLow.toArgb(),
    sectionBackground = surfaceContainerHigh.toArgb(),
    sectionSeparator = outlineVariant.toArgb(),
    text = onSurface.toArgb(),
    accentText = primary.toArgb(),
    sectionHeaderText = primary.toArgb(),
    subtitleText = onSurfaceVariant.toArgb(),
    destructiveText = error.toArgb(),
    hint = onSurfaceVariant.toArgb(),
    link = primary.toArgb(),
    button = primary.toArgb(),
    buttonText = onPrimary.toArgb()
)

/**
 * A bot's Mini App, open over the chat (2.0): a full-height sheet with the
 * bot's name in a Material top bar, the page in a WebView, and the page's
 * main button — when it sets one up — as a Material button along the
 * bottom. The page draws itself; everything round it is the client's.
 *
 * The page talks to the client the way it does on every Telegram client:
 * it calls `TelegramWebviewProxy.postEvent(type, json)`, and the client
 * answers through `Telegram.WebView.receiveEvent(type, data)`. Only the
 * events that make sense without Telegram's own UI are answered: closing,
 * the theme and the viewport, the main and back buttons, links, popups and
 * haptics. Anything else is ignored, which every page has to expect, since
 * clients differ in what they support.
 *
 * The sheet does not drag: the page scrolls, and a sheet that also moved
 * under the same finger would fight it. The cross closes it, as does Back
 * when the page has no back button of its own.
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MiniAppSheet(app: OpenWebApp, theme: WebAppTheme, onClose: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val close by rememberUpdatedState(onClose)
    var mainButton by remember { mutableStateOf<WebAppMainButton?>(null) }
    var backVisible by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }
    var askingClose by remember { mutableStateOf(false) }
    var popup by remember { mutableStateOf<MiniAppPopup?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    fun send(type: String, data: JSONObject = JSONObject()) {
        val script = "window.Telegram && window.Telegram.WebView && " +
            "window.Telegram.WebView.receiveEvent(${JSONObject.quote(type)}, $data)"
        webView?.evaluateJavascript(script, null)
    }

    fun themeEvent() = JSONObject().put("theme_params", JSONObject(theme.toParams()))

    fun handle(type: String, data: JSONObject) {
        when (type) {
            "web_app_close" -> close()
            "web_app_request_theme" -> send("theme_changed", themeEvent())
            "web_app_request_viewport" -> webView?.let { view ->
                send(
                    "viewport_changed",
                    JSONObject()
                        .put("height", view.height / view.resources.displayMetrics.density)
                        .put("is_state_stable", true)
                        .put("is_expanded", true)
                )
            }
            "web_app_setup_main_button" -> mainButton = WebAppMainButton(
                text = data.optString("text"),
                isVisible = data.optBoolean("is_visible") && data.optString("text").isNotBlank(),
                isActive = data.optBoolean("is_active", true),
                isProgressVisible = data.optBoolean("is_progress_visible")
            )
            "web_app_setup_back_button" -> backVisible = data.optBoolean("is_visible")
            "web_app_setup_closing_behavior" -> confirmClose = data.optBoolean("need_confirmation")
            "web_app_open_link" -> data.optString("url").takeIf { it.startsWith("https://") || it.startsWith("http://") }
                ?.let { runCatching { uriHandler.openUri(it) } }
            "web_app_open_tg_link" -> data.optString("path_full").takeIf { it.startsWith("/") }
                ?.let { runCatching { uriHandler.openUri("https://t.me$it") } }
            "web_app_open_popup" -> popup = MiniAppPopup.of(data)
            "web_app_trigger_haptic_feedback" ->
                webView?.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            else -> Unit
        }
    }
    val latestHandle by rememberUpdatedState<(String, JSONObject) -> Unit>(::handle)

    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val requestClose = { if (confirmClose) askingClose = true else close() }
    ModalBottomSheet(
        onDismissRequest = { requestClose() },
        sheetState = sheet,
        sheetGesturesEnabled = false,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        // The page's own back first, when it has one; otherwise Back closes
        // the sheet. Inside the sheet's content, because the sheet is a window
        // of its own and Back goes to it, not to the screen under it: a
        // handler registered outside never heard Back at all.
        BackHandler(enabled = backVisible) { send("back_button_pressed") }
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(app.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (backVisible) {
                        IconButton(onClick = { send("back_button_pressed") }) {
                            Icon(Symbols.ArrowBack, contentDescription = "Back")
                        }
                    } else {
                        IconButton(onClick = { requestClose() }) {
                            Icon(Symbols.Close, contentDescription = "Close Mini App")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            setBackgroundColor(theme.background or 0xFF000000.toInt())
                            addJavascriptInterface(MiniAppBridge { type, data -> latestHandle(type, data) }, "TelegramWebviewProxy")
                            webViewClient = object : WebViewClient() {
                                // Pages of the app stay in it; a tg: or any
                                // other scheme goes to whatever handles it.
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    val scheme = request.url.scheme.orEmpty()
                                    if (scheme == "https" || scheme == "http" || scheme == "file") return false
                                    runCatching { uriHandler.openUri(request.url.toString()) }
                                    return true
                                }
                            }
                            loadUrl(app.session.url)
                            webView = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            mainButton?.takeIf { it.isVisible }?.let { button ->
                Button(
                    onClick = { send("main_button_pressed") },
                    enabled = button.isActive && !button.isProgressVisible,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(56.dp)
                ) {
                    if (button.isProgressVisible) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(button.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
    popup?.let { shown ->
        AlertDialog(
            onDismissRequest = {
                popup = null
                send("popup_closed", JSONObject())
            },
            title = if (shown.title.isNotBlank()) {
                { Text(shown.title) }
            } else {
                null
            },
            text = { Text(shown.message) },
            confirmButton = {
                shown.buttons.lastOrNull()?.let { (id, label) ->
                    TextButton(onClick = {
                        popup = null
                        send("popup_closed", JSONObject().put("button_id", id))
                    }) { Text(label) }
                }
            },
            dismissButton = {
                shown.buttons.dropLast(1).forEach { (id, label) ->
                    TextButton(onClick = {
                        popup = null
                        send("popup_closed", JSONObject().put("button_id", id))
                    }) { Text(label) }
                }
            }
        )
    }
    if (askingClose) {
        AlertDialog(
            onDismissRequest = { askingClose = false },
            title = { Text("Close ${app.title}?") },
            text = { Text("Changes you made may not be saved.") },
            confirmButton = { TextButton(onClick = { askingClose = false; close() }) { Text("Close") } },
            dismissButton = { TextButton(onClick = { askingClose = false }) { Text("Cancel") } }
        )
    }
    DisposableEffect(Unit) {
        onDispose {
            webView?.destroy()
            webView = null
        }
    }
}

/**
 * What the page calls as `TelegramWebviewProxy.postEvent`. Called on the
 * WebView's own thread, so each event is handed to the main one.
 */
private class MiniAppBridge(private val onEvent: (String, JSONObject) -> Unit) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun postEvent(eventType: String, eventData: String?) {
        val data = runCatching { JSONObject(eventData.orEmpty().ifBlank { "{}" }) }.getOrDefault(JSONObject())
        main.post { onEvent(eventType, data) }
    }
}

/** A page's `web_app_open_popup`: a title, a message, and up to three buttons by id. */
private data class MiniAppPopup(val title: String, val message: String, val buttons: List<Pair<String, String>>) {
    companion object {
        fun of(data: JSONObject): MiniAppPopup {
            val raw = data.optJSONArray("buttons")
            val buttons = (0 until (raw?.length() ?: 0)).mapNotNull { index ->
                val button = raw?.optJSONObject(index) ?: return@mapNotNull null
                val label = button.optString("text").ifBlank {
                    when (button.optString("type")) {
                        "ok" -> "OK"
                        "close" -> "Close"
                        "cancel" -> "Cancel"
                        else -> ""
                    }
                }
                (button.optString("id") to label).takeIf { label.isNotBlank() }
            }.take(3)
            return MiniAppPopup(
                title = data.optString("title"),
                message = data.optString("message"),
                buttons = buttons.ifEmpty { listOf("" to "OK") }
            )
        }
    }
}
