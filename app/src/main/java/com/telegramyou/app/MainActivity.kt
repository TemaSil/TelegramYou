package com.telegramyou.app

import com.telegramyou.app.ui.media.PictureInPicture
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.snapshotFlow
import android.content.res.Configuration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import android.view.WindowManager
import com.telegramyou.app.ui.lock.AppLockScreen
import com.telegramyou.app.ui.chat.ChatStyle
import com.telegramyou.app.ui.chat.LocalChatStyle
import androidx.compose.runtime.CompositionLocalProvider
import com.telegramyou.app.ui.components.LocalShapedAvatars
import com.telegramyou.app.ui.components.LocalTwoLinePreviews
import com.telegramyou.app.ui.motion.LocalReduceMotion
import android.os.Build
import com.telegramyou.app.settings.LocalGeekSettings
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.telegramyou.app.settings.isDark
import androidx.compose.ui.Modifier
import android.content.Intent
import com.telegramyou.app.navigation.TelegramYouNavHost
import com.telegramyou.app.notifications.IncomingShares
import com.telegramyou.app.telegram.model.IncomingShare
import com.telegramyou.app.telegram.AppVisibility
import com.telegramyou.app.telegram.TelegramForegroundService
import com.telegramyou.app.ui.theme.TelegramYouTheme
import com.telegramyou.app.update.LocalAppUpdates

open class MainActivity : ComponentActivity() {

    /**
     * Shown in a conversation's bubble rather than as the app (2.1): one
     * chat and nothing behind it, so Back folds the bubble. See BubbleActivity.
     */
    protected open val isBubble: Boolean = false

    /**
     * The chat a notification asked for, consumed once.
     *
     * Held here rather than passed straight down because onNewIntent can
     * deliver one while the composition is already running — a second
     * notification tapped without leaving the app.
     */
    private var pendingChatId by mutableStateOf<Long?>(null)

    /** The downloads notification was tapped: the Downloads screen, once. */
    private var pendingDownloads by mutableStateOf(false)

    /**
     * Something another app shared here (2.1), until it is in a chat's
     * composer: to the chat its Direct Share target named, or to the one
     * picked from the list when the app itself was chosen.
     */
    private var pendingShare by mutableStateOf<IncomingShare?>(null)

    /** Reads a share off [intent], if it is one; the copying is off the main thread. */
    private fun takeShare(intent: Intent?) {
        if (!IncomingShares.isShare(intent)) return
        lifecycleScope.launch { pendingShare = IncomingShares.read(this@MainActivity, intent ?: return@launch) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Both bars fully transparent, rather than the default. Left to
        // itself, enableEdgeToEdge puts a translucent scrim behind the
        // navigation bar on a light theme below Android 15, which shows as a
        // pale band across the bottom of a screen whose own background was
        // supposed to run underneath it. The app draws there now, so nothing
        // needs to be laid over it.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                Color.TRANSPARENT,
                Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                Color.TRANSPARENT,
                Color.TRANSPARENT
            )
        )
        // And the one the styles above do not cover. Since Android 10 the
        // system lays its own contrast scrim under a three-button navigation
        // bar unless an app says otherwise — gesture navigation is left
        // transparent, buttons are not. That scrim is the pale band that
        // stayed under the composer after both bars had been made
        // transparent, and this is the only way to be rid of it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        val app = application as TelegramYouApp
        if (savedInstanceState == null) holdFirstFrame(app)
        pendingChatId = intent.chatIdExtra()
        pendingDownloads = intent.opensDownloads()
        // Not again after a rotation: the intent is still this one, and its
        // share has already gone into a composer.
        if (savedInstanceState == null) takeShare(intent)
        // Started from the activity rather than from Application.onCreate:
        // a foreground service begun before anything is on screen is a
        // notification for an app the person has not opened.
        TelegramForegroundService.start(this)
        // What the player wants, handed to the window as it changes: with a
        // video playing, leaving the app takes it into PiP by itself.
        lifecycleScope.launch {
            snapshotFlow { PictureInPicture.wanted.value }.collect { aspect ->
                try {
                    setPictureInPictureParams(PictureInPicture.params(aspect))
                } catch (_: IllegalStateException) {
                } catch (_: IllegalArgumentException) {
                }
            }
        }
        setContent {
            // Collected rather than read: flipping a switch in settings has to
            // change the colours behind it, not on the next launch.
            val appearance by app.appearance.settings.collectAsStateWithLifecycle()
            val geekSettings by app.geeks.settings.collectAsStateWithLifecycle()
            val darkTheme = isDark(appearance.theme, isSystemInDarkTheme())
            // The bars' icons follow the app's theme, not the system's: the
            // styles above judge light and dark by the phone's own setting,
            // so with the app set apart from it — dark here, light there —
            // the status bar's icons were drawn dark on the app's dark top
            // bar, which on a Pixel 6 on Android 12 read as a bar that would
            // not take the app's colour (2.0.3, from users).
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme }
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
                onDispose {}
            }
            TelegramYouTheme(
                darkTheme = darkTheme,
                dynamicColor = appearance.dynamicColor,
                accent = appearance.accent,
                pureBlack = appearance.pureBlack,
                reduceMotion = appearance.reduceMotion
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Shaped avatars are an appearance setting like the two
                    // above, and every screen that draws a person reads it.
                    // Text size multiplies the system's font scale rather
                    // than replacing it: somebody who made the whole phone
                    // larger keeps that, and this setting goes on top.
                    val density = LocalDensity.current
                    CompositionLocalProvider(
                        LocalShapedAvatars provides appearance.shapedAvatars,
                        LocalTwoLinePreviews provides appearance.twoLinePreviews,
                        LocalReduceMotion provides appearance.reduceMotion,
                        LocalChatStyle provides ChatStyle(
                            wallpaper = appearance.chatWallpaper,
                            outgoingTone = appearance.outgoingTone,
                            bubbleCorners = appearance.bubbleCorners,
                            messageTextScale = appearance.messageTextScale,
                            composerCapsule = appearance.composerCapsule
                        ),
                        LocalAppUpdates provides app.updates,
                        LocalGeekSettings provides geekSettings,
                        LocalDensity provides Density(density.density, density.fontScale * appearance.textScale)
                    ) {
                        val lockSettings by app.appLock.settings.collectAsStateWithLifecycle()
                        val locked by app.appLock.locked.collectAsStateWithLifecycle()
                        // The account in use (2.3). Switching builds the
                        // whole graph again on the other account, its screens'
                        // state holders in a store of that account's own, so
                        // nothing of the last account's is left running or on
                        // screen; see AccountViewModelStores.
                        val repository by app.accounts.active.collectAsStateWithLifecycle()
                        val stores: AccountViewModelStores = androidx.lifecycle.viewmodel.compose.viewModel()
                        val owner = androidx.compose.runtime.remember(repository) { stores.ownerFor(repository) }
                        LaunchedEffect(repository) { stores.dropAllBut(repository) }
                        // Behind the lock the app stays composed but says
                        // nothing: without this, TalkBack could read the
                        // chats straight through it.
                        // Unlocking, along Z (2.4.1): the lock flies past the
                        // viewer, growing as it fades, while the app comes up
                        // out of the depth beneath it, as Android's own
                        // launcher opens an app. Locking is instant — the
                        // chats are never seen on the way in — and Less
                        // motion unlocks at once too.
                        val still = LocalReduceMotion.current
                        var lockShown by androidx.compose.runtime.remember { mutableStateOf(locked) }
                        val opened = androidx.compose.runtime.remember { Animatable(if (locked) 0f else 1f) }
                        LaunchedEffect(locked) {
                            if (locked) {
                                opened.snapTo(0f)
                                lockShown = true
                            } else if (lockShown) {
                                if (!still) opened.animateTo(1f, tween(UNLOCK_MILLIS, easing = EmphasizedDecelerate))
                                opened.snapTo(1f)
                                lockShown = false
                            }
                        }
                        Box(
                            (if (locked) Modifier.clearAndSetSemantics {} else Modifier).graphicsLayer {
                                val depth = 1f - opened.value
                                scaleX = 1f - UNLOCK_DEPTH * depth
                                scaleY = 1f - UNLOCK_DEPTH * depth
                            }
                        ) {
                        CompositionLocalProvider(androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner provides owner) {
                        androidx.compose.runtime.key(repository) {
                        TelegramYouNavHost(
                            repository = repository,
                            appearance = app.appearance,
                            appLock = app.appLock,
                            geeks = app.geeks,
                            queryHistory = app.queryHistory,
                            music = app.music,
                            voice = app.voice,
                            openChatId = pendingChatId,
                            onChatOpened = { pendingChatId = null },
                            openDownloads = pendingDownloads,
                            onDownloadsOpened = { pendingDownloads = false },
                            singleChat = isBubble,
                            share = pendingShare,
                            onShareTarget = { chatId -> pendingShare = pendingShare?.copy(chatId = chatId) },
                            onShareHandled = { pendingShare = null },
                            onDemoRequested = { app.setDemoMode(!app.isSwitchedToDemo) },
                            accounts = app.accounts
                        )
                        }
                        }
                        }
                        // Over everything, the app still composed beneath:
                        // unlocking puts back exactly the screen that was
                        // left, as the system's own lock screen does.
                        if (locked || lockShown) {
                            Box(
                                Modifier.graphicsLayer {
                                    val gone = opened.value
                                    scaleX = 1f + UNLOCK_PASS * gone
                                    scaleY = 1f + UNLOCK_PASS * gone
                                    alpha = 1f - gone
                                }
                            ) {
                                AppLockScreen(
                                    settings = lockSettings,
                                    tryPin = app.appLock::tryUnlock,
                                    onBiometricUnlock = app.appLock::unlockWithBiometric
                                )
                            }
                        }
                        // What Recents shows of the app: nothing, while the
                        // lock is on and asked to hide it. Android 13 has a
                        // switch for exactly this; before it the only way is
                        // FLAG_SECURE, which also stops screenshots.
                        val hide = lockSettings.enabled && lockSettings.hideInRecents
                        LaunchedEffect(hide) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                setRecentsScreenshotEnabled(!hide)
                            } else if (hide) {
                                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                            } else {
                                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                            }
                        }
                    }
                }
            }
        }
    }

    // ── picture-in-picture ────────────────────────────────────────────────
    // The player asks through PictureInPicture.wanted; the window is this
    // activity's, so this is where it is acted on. From Android 12 the
    // system enters PiP by itself on the way out (auto-enter, set with the
    // params); before that, leaving is noticed here and PiP asked for.

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val aspect = PictureInPicture.wanted.value ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            try {
                enterPictureInPictureMode(PictureInPicture.params(aspect))
            } catch (_: IllegalStateException) {
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        PictureInPicture.active.value = isInPictureInPictureMode
    }

    /**
     * The system's splash stays until TDLib has said whether this phone is
     * signed in (2.2.3). Until then the start destination is the login
     * screen, and on a slow start — the first after an update, while
     * Android optimises the new code and TDLib opens its database — its
     * phone number field showed for a second before the chats replaced it.
     *
     * Held by cancelling the window's first draws, the platform's own way
     * to keep a splash, which works on every Android this app runs on. Two
     * frames more once the answer is in, for the navigation to the chats to
     * land; and never more than [FIRST_FRAME_HOLD_MS] in all, so a TDLib
     * that never answers still shows the login screen and its error.
     */
    private fun holdFirstFrame(app: TelegramYouApp) {
        val content = findViewById<android.view.View>(android.R.id.content)
        val started = android.os.SystemClock.uptimeMillis()
        var framesSinceKnown = 0
        content.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                val known = app.telegramRepository.observeAuth().value.state !=
                    com.telegramyou.app.telegram.model.AuthState.Bootstrapping
                if (known) framesSinceKnown++
                val done = framesSinceKnown > 2 ||
                    android.os.SystemClock.uptimeMillis() - started > FIRST_FRAME_HOLD_MS
                if (done) content.viewTreeObserver.removeOnPreDrawListener(this)
                return done
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // setIntent so a recreation after this — a rotation, say — does not
        // reopen the chat from the intent this activity was first launched
        // with.
        setIntent(intent)
        pendingChatId = intent.chatIdExtra()
        pendingDownloads = intent.opensDownloads()
        takeShare(intent)
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.isInForeground = true
        (application as TelegramYouApp).telegramRepository.setOnline(true)
        (application as TelegramYouApp).appLock.onReturned()
    }

    override fun onStop() {
        // The notification service reads this to decide whether the chat on
        // screen is being read. Once the activity has stopped it is not,
        // whatever is still composed.
        AppVisibility.isInForeground = false
        (application as TelegramYouApp).telegramRepository.setOnline(false)
        // An icon colour picked in 1.4 goes back to Teal here, as the app
        // leaves the screen; see restoreLauncherIcon.
        if (!isChangingConfigurations) (application as TelegramYouApp).appearance.restoreLauncherIcon()
        // Leaving, for the app lock's clock — not a rotation, which is not.
        if (!isChangingConfigurations) (application as TelegramYouApp).appLock.onLeft()
        super.onStop()
    }
}

private fun Intent.opensDownloads(): Boolean =
    getBooleanExtra(TelegramForegroundService.EXTRA_OPEN_DOWNLOADS, false)

private fun Intent.chatIdExtra(): Long? =
    getLongExtra(TelegramForegroundService.EXTRA_CHAT_ID, -1L).takeIf { it != -1L }

/** The longest the splash is held for; see MainActivity.holdFirstFrame. */
private const val FIRST_FRAME_HOLD_MS = 3_000L

/**
 * A store of screen state holders for each account (2.3), held by the
 * activity's own ViewModel so it outlives a rotation as the screens' always
 * did. The account switched away from has its store cleared — its state
 * holders stopped and their work cancelled — rather than left running on
 * the account no longer in use.
 */
class AccountViewModelStores : androidx.lifecycle.ViewModel() {
    private val stores = HashMap<com.telegramyou.app.telegram.TelegramRepository, androidx.lifecycle.ViewModelStore>()

    fun ownerFor(repository: com.telegramyou.app.telegram.TelegramRepository): androidx.lifecycle.ViewModelStoreOwner {
        val store = stores.getOrPut(repository) { androidx.lifecycle.ViewModelStore() }
        return object : androidx.lifecycle.ViewModelStoreOwner {
            override val viewModelStore: androidx.lifecycle.ViewModelStore = store
        }
    }

    fun dropAllBut(repository: com.telegramyou.app.telegram.TelegramRepository) {
        stores.keys.filter { it !== repository }.forEach { stores.remove(it)?.clear() }
    }

    override fun onCleared() {
        stores.values.forEach { it.clear() }
        stores.clear()
    }
}

/** How long the unlock takes, and Material's emphasized decelerate it runs on. */
private const val UNLOCK_MILLIS = 450
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
/** How far back the app starts, as a share of its size. */
private const val UNLOCK_DEPTH = 0.08f
/** How much the lock grows as it passes the viewer. */
private const val UNLOCK_PASS = 0.12f
