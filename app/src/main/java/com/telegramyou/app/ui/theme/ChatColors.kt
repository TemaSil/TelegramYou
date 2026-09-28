package com.telegramyou.app.ui.theme

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.telegramyou.app.telegram.model.ChatPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A conversation in its own colours, taken from the other side's avatar —
 * Appearance → "Colours from the avatar".
 *
 * The seed is the photo's main colour, found the way Android finds a
 * wallpaper's (seedFromPixels in :core), or the placeholder's colour where
 * there is no photo, so a chat without one still has its own. From it the
 * whole Material scheme, light or dark as the app is, and pure black if the
 * app is — so every component in the chat follows, not just the bubbles.
 *
 * Off, or for Saved Messages, the app's own scheme stands. Only the
 * conversation takes it: the list around it stays the app's, which is what
 * makes the chat read as the person's.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChatColors(chat: ChatPreview?, enabled: Boolean, content: @Composable () -> Unit) {
    if (!enabled || chat == null || chat.isSavedMessages) {
        content()
        return
    }
    val current = MaterialTheme.colorScheme
    // Read from the scheme in force rather than asked again: the app's own
    // choice of light, dark and black has already been made there.
    val dark = current.surface.luminance() < 0.5f
    val black = current.surface == Color.Black
    val placeholder = avatarColor(chat.avatarColor).toArgb()
    val seed by produceState(initialValue = placeholder, chat.photoPath, placeholder) {
        val path = chat.photoPath ?: return@produceState
        value = withContext(Dispatchers.Default) { seedFromPhoto(path) } ?: placeholder
    }
    val scheme = remember(seed, dark, black) {
        schemeFromSeed(seed, dark).toColorScheme(dark).let { if (black) it.pureBlack() else it }
    }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MaterialTheme.motionScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content
    )
}

/**
 * The main colour of the picture at [path], or null. Read small — a few
 * dozen pixels a side say as much about a face's colours as the full photo
 * and cost nothing to count.
 */
private fun seedFromPhoto(path: String): Int? = try {
    val options = BitmapFactory.Options().apply { inSampleSize = 4 }
    BitmapFactory.decodeFile(path, options)?.let { decoded ->
        val small = Bitmap.createScaledBitmap(decoded, SAMPLE_SIZE, SAMPLE_SIZE, true)
        val pixels = IntArray(SAMPLE_SIZE * SAMPLE_SIZE)
        small.getPixels(pixels, 0, SAMPLE_SIZE, 0, 0, SAMPLE_SIZE, SAMPLE_SIZE)
        if (small !== decoded) small.recycle()
        decoded.recycle()
        seedFromPixels(pixels)
    }
} catch (_: Exception) {
    null
}

private const val SAMPLE_SIZE = 48
