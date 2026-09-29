package com.telegramyou.app

import com.telegramyou.app.ui.media.PictureInPicture
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.snapshotFlow
import android.content.res.Configuration
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import com.telegramyou.app.telegram.AppVisibility
import com.telegramyou.app.telegram.TelegramForegroundService
import com.telegramyou.app.ui.theme.TelegramYouTheme
import com.telegramyou.app.update.LocalAppUpdates

class MainActivity : ComponentActivity() {

    /**
     * The chat a notification asked for, consumed once.
     *
     * Held here rather than passed straight down because onNewIntent can
     * deliver one while the composition is already running — a second
     * notification tapped without leaving the app.
     */
    private var pendingChatId by mutableStateOf<Long?>(null)

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
        pendingChatId = intent.chatIdExtra()
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
            TelegramYouTheme(
                darkTheme = isDark(appearance.theme, isSystemInDarkTheme()),
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
                            messageTextScale = appearance.messageTextScale
                        ),
                        LocalAppUpdates provides app.updates,
                        LocalGeekSettings provides geekSettings,
                        LocalDensity provides Density(density.density, density.fontScale * appearance.textScale)
                    ) {
                        val lockSettings by app.appLock.settings.collectAsStateWithLifecycle()
                        val locked by app.appLock.locked.collectAsStateWithLifecycle()
                        // Behind the lock the app stays composed but says
                        // nothing: without this, TalkBack could read the
                        // chats straight through it.
                        Box(if (locked) Modifier.clearAndSetSemantics {} else Modifier) {
                        TelegramYouNavHost(
                            repository = app.telegramRepository,
                            appearance = app.appearance,
                            appLock = app.appLock,
                            geeks = app.geeks,
                            queryHistory = app.queryHistory,
                            openChatId = pendingChatId,
                            onChatOpened = { pendingChatId = null },
                            onDemoRequested = { app.setDemoMode(!app.isSwitchedToDemo) }
                        )
                        }
                        // Over everything, the app still composed beneath:
                        // unlocking puts back exactly the screen that was
                        // left, as the system's own lock screen does.
                        if (locked) {
                            AppLockScreen(
                                settings = lockSettings,
                                tryPin = app.appLock::tryUnlock,
                                onBiometricUnlock = app.appLock::unlockWithBiometric
                            )
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // setIntent so a recreation after this — a rotation, say — does not
        // reopen the chat from the intent this activity was first launched
        // with.
        setIntent(intent)
        pendingChatId = intent.chatIdExtra()
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

private fun Intent.chatIdExtra(): Long? =
    getLongExtra(TelegramForegroundService.EXTRA_CHAT_ID, -1L).takeIf { it != -1L }
