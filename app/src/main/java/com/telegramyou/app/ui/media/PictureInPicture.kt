package com.telegramyou.app.ui.media

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.util.Rational
import androidx.compose.runtime.mutableStateOf

/**
 * Picture-in-picture for a video being watched: the platform's own small
 * window, which a video player on Android is expected to go into when the
 * person leaves it playing.
 *
 * The player says what it wants through [wanted] — the aspect of the video
 * playing, or null — and MainActivity, which owns the window, acts on it:
 * from Android 12 by letting the system enter PiP by itself on the way out
 * (auto-enter), before that on onUserLeaveHint. [active] is whether the
 * window is in PiP now, so the player can hide its controls, which have no
 * room there.
 */
object PictureInPicture {
    val wanted = mutableStateOf<Rational?>(null)
    val active = mutableStateOf(false)

    /** A video's width over height as PiP takes it, inside the range Android allows. */
    fun aspectOf(aspect: Float): Rational {
        val clamped = aspect.coerceIn(MIN_ASPECT, MAX_ASPECT)
        return Rational((clamped * 1000).toInt(), 1000)
    }

    /** The window's PiP settings for [aspect]: auto-enter only while a video wants it. */
    fun params(aspect: Rational?): PictureInPictureParams =
        PictureInPictureParams.Builder()
            .setAspectRatio(aspect ?: Rational(16, 9))
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setAutoEnterEnabled(aspect != null)
                    setSeamlessResizeEnabled(true)
                }
            }
            .build()

    /**
     * Into PiP now, from the player's own button. False where the device has
     * no PiP or refuses it, which the button then does nothing about.
     */
    fun enter(context: Context, aspect: Rational): Boolean {
        val activity = context.findActivity() ?: return false
        return try {
            activity.enterPictureInPictureMode(params(aspect))
        } catch (_: IllegalStateException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    // Android's limits: from 1:2.39 to 2.39:1.
    private const val MIN_ASPECT = 1f / 2.39f
    private const val MAX_ASPECT = 2.39f
}

/** The activity behind a context — a Dialog's is wrapped at least once. */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
