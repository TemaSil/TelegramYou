package com.telegramyou.app.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import com.telegramyou.app.ui.icons.Symbols
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.telegramyou.app.TelegramYouApp
import com.telegramyou.app.settings.isDark
import com.telegramyou.app.telegram.model.ChatPreview
import com.telegramyou.app.ui.components.AvatarBubble
import com.telegramyou.app.ui.lock.AppLockScreen
import com.telegramyou.app.ui.theme.TelegramYouTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Choosing whose photo a PersonWidget shows, and in which shape (2.2) —
 * opened by the launcher as the widget is placed, and again when it is
 * reconfigured.
 *
 * The photo at the top is drawn by the widget's own code, so what is chosen
 * here is what lands on the home screen. Behind the app lock when the app
 * has one: it lists chats, and a widget's setup must not be a way round it.
 */
class PersonWidgetSetup : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Backing out leaves no widget behind: the launcher removes one whose
        // setup did not say yes.
        setResult(Activity.RESULT_CANCELED)
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val app = application as TelegramYouApp
        enableEdgeToEdge()
        setContent {
            val appearance by app.appearance.settings.collectAsStateWithLifecycle()
            TelegramYouTheme(
                darkTheme = isDark(appearance.theme, isSystemInDarkTheme()),
                dynamicColor = appearance.dynamicColor,
                accent = appearance.accent,
                pureBlack = appearance.pureBlack,
                reduceMotion = appearance.reduceMotion
            ) {
                val locked by app.appLock.locked.collectAsStateWithLifecycle()
                val lockSettings by app.appLock.settings.collectAsStateWithLifecycle()
                if (locked) {
                    AppLockScreen(
                        settings = lockSettings,
                        tryPin = app.appLock::tryUnlock,
                        onBiometricUnlock = app.appLock::unlockWithBiometric
                    )
                } else {
                    val chats by app.telegramRepository.chats.collectAsStateWithLifecycle()
                    Setup(
                        people = remember(chats) { photoCandidates(chats) },
                        appWidgetId = appWidgetId,
                        onClose = ::finish,
                        onDone = {
                            setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                            finish()
                        }
                    )
                }
            }
        }
    }
}

/** Who can be put on the home screen: people, in the list's order — not groups, channels, bots or oneself. */
internal fun photoCandidates(chats: List<ChatPreview>): List<ChatPreview> =
    chats.filter { !it.isGroup && !it.isChannel && !it.isBot && !it.isSavedMessages }

/** The shapes on offer, in the order offered, each with the name TalkBack reads. */
internal val PHOTO_SHAPES: List<Pair<Int, String>> = listOf(
    5 to "Cookie",
    1 to "Clover",
    3 to "Flower",
    0 to "Circle",
    2 to "Squircle",
    9 to "Scallop",
    7 to "Star",
    10 to "Trefoil",
    8 to "Hexagon",
    6 to "Pentagon",
    4 to "Triangle",
    11 to "Octagon"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Setup(
    people: List<ChatPreview>,
    appWidgetId: Int,
    onClose: () -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as TelegramYouApp
    val scope = rememberCoroutineScope()
    var chosenId by rememberSaveable { mutableStateOf<Long?>(null) }
    var shape by rememberSaveable { mutableIntStateOf(PersonWidget.DEFAULT_SHAPE) }
    var saving by remember { mutableStateOf(false) }
    // Reconfiguring: start from what the widget already shows. Once only —
    // after a rotation the choice on screen is the newer one.
    var started by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(appWidgetId) {
        if (started) return@LaunchedEffect
        runCatching {
            val glanceId = androidx.glance.appwidget.GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
            PersonWidget.chosen(context, glanceId)
        }.getOrNull()?.let { (chatId, chosenShape) ->
            chosenId = chatId
            shape = chosenShape
        }
        started = true
    }
    // Nobody picked yet: the first person in the list, as a radio group starts.
    val chosen = people.firstOrNull { it.id == chosenId } ?: people.firstOrNull()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Photo on the home screen") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Symbols.Close, contentDescription = "Close") }
                }
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), contentAlignment = Alignment.CenterEnd) {
                    Button(
                        enabled = chosen != null && !saving,
                        onClick = {
                            val person = chosen ?: return@Button
                            saving = true
                            scope.launch {
                                PersonWidget.configure(context, appWidgetId, person, shape) { chatId ->
                                    runCatching { app.telegramRepository.chatPhoto(chatId) }.getOrNull()
                                }
                                onDone()
                            }
                        }
                    ) { Text(if (saving) "Adding…" else "Add to home screen") }
                }
            }
        }
    ) { padding ->
        if (people.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Sign in to TelegramYou, then come back to choose someone.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp), modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                Preview(chosen, shape, Modifier.fillMaxWidth().padding(vertical = 16.dp))
            }
            item {
                Text(
                    "Shape",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(PHOTO_SHAPES, key = { it.first }) { (index, name) ->
                        ShapeChoice(index, name, selected = index == shape, onClick = { shape = index })
                    }
                }
            }
            item {
                Text(
                    "Person",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 4.dp)
                )
            }
            items(people, key = { it.id }) { person ->
                val selected = person.id == chosen?.id
                ListItem(
                    headlineContent = { Text(person.title, maxLines = 1) },
                    leadingContent = {
                        AvatarBubble(title = person.title, seed = person.avatarColor, size = 40.dp, photoPath = person.photoPath)
                    },
                    trailingContent = { RadioButton(selected = selected, onClick = null) },
                    modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = { chosenId = person.id })
                )
            }
        }
    }
}

/** The widget as it will be, drawn by its own code. */
@Composable
private fun Preview(person: ChatPreview?, shape: Int, modifier: Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as TelegramYouApp
    // The full-size photo, once fetched; the list's small one meanwhile.
    val photo by produceState(person?.photoPath, person?.id) {
        val id = person?.id ?: return@produceState
        value = runCatching { app.telegramRepository.chatPhoto(id) }.getOrNull() ?: person.photoPath
    }
    val bitmap by produceState<android.graphics.Bitmap?>(null, person?.id, shape, photo) {
        val who = person ?: return@produceState
        value = withContext(Dispatchers.Default) {
            PersonWidget.picture(context, photo, who.title, who.avatarColor, shape, PREVIEW_DP)
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = person?.title,
                modifier = Modifier.size(PREVIEW_DP.dp)
            )
        } ?: Box(Modifier.size(PREVIEW_DP.dp))
    }
}

/** One shape on offer: the outline itself, filled when chosen. */
@Composable
private fun ShapeChoice(index: Int, name: String, selected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val outline = remember(index) { WidgetArt.tile(context, index, SHAPE_DP, upright = true).asImageBitmap() }
    val colors = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(64.dp)
            .background(if (selected) colors.secondaryContainer else colors.surfaceContainerHigh, RoundedCornerShape(20.dp))
            .then(if (selected) Modifier.border(2.dp, colors.primary, RoundedCornerShape(20.dp)) else Modifier)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = name }
    ) {
        Image(
            bitmap = outline,
            contentDescription = null,
            colorFilter = ColorFilter.tint(if (selected) colors.primary else colors.onSurfaceVariant),
            modifier = Modifier.size(SHAPE_DP.dp)
        )
    }
}

private const val PREVIEW_DP = 200
private const val SHAPE_DP = 40
