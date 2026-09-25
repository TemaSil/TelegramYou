package com.telegramyou.app.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.telegramyou.app.telegram.model.StickerContent
import com.telegramyou.app.telegram.model.StickerFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * Fetches a file by TDLib's id and answers with where it landed, for anything
 * on the conversation screen that draws one without its own state holder —
 * stickers in a bubble or in the picker. Provided by ChatScreen; null in
 * previews, where a sticker falls back to its emoji.
 */
val LocalFileLoader = staticCompositionLocalOf<(suspend (Int) -> String?)?> { null }

/**
 * One sticker, [size] square: a picture drawn by Coil, a Lottie animation
 * played by Lottie — a `.tgs` is a gzipped Lottie file, which is all the
 * format is — or, for a video sticker, its still picture for now.
 *
 * Whatever is not on this device yet is fetched through [LocalFileLoader];
 * until it arrives, and for the demo's stickers that have no file at all,
 * the sticker is its emoji. [animate] is off in the picker, where a grid of
 * forty animations playing at once is a phone running hot for a menu.
 */
@Composable
fun StickerView(
    sticker: StickerContent,
    size: Dp,
    modifier: Modifier = Modifier,
    animate: Boolean = true
) {
    val loader = LocalFileLoader.current
    val known = if (sticker.format == StickerFormat.Webm) sticker.thumbPath else sticker.path
    val path by produceState(known, sticker.drawnFileId) {
        if (value == null) {
            val id = sticker.drawnFileId
            if (id != null && loader != null) value = loader(id)
        }
    }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        val file = path
        when {
            file == null || (sticker.format == StickerFormat.Webm && sticker.thumbFileId == null) ->
                EmojiSticker(sticker.emoji, size)
            sticker.isAnimated -> LottieSticker(file, size, animate, sticker.emoji)
            else -> AsyncImage(
                model = File(file),
                contentDescription = "${sticker.emoji} sticker",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(size)
            )
        }
    }
}

@Composable
private fun LottieSticker(path: String, size: Dp, animate: Boolean, emoji: String) {
    // Read off the main thread: a sticker's JSON is tens of kilobytes
    // gunzipped, and a chat can scroll a dozen past in a second.
    val json by produceState<String?>(null, path) {
        value = withContext(Dispatchers.IO) {
            try {
                GZIPInputStream(File(path).inputStream()).bufferedReader().use { it.readText() }
            } catch (_: Exception) {
                null
            }
        }
    }
    val text = json
    if (text == null) {
        EmojiSticker(emoji, size)
        return
    }
    val composition by rememberLottieComposition(LottieCompositionSpec.JsonString(text))
    LottieAnimation(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        isPlaying = animate,
        modifier = Modifier.size(size)
    )
}

@Composable
private fun EmojiSticker(emoji: String, size: Dp) {
    val fontSize = with(LocalDensity.current) { (size * 0.72f).toSp() }
    Text(emoji.ifBlank { "🙂" }, fontSize = fontSize)
}
