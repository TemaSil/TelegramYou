package com.telegramyou.app.ui.chat

import com.telegramyou.app.telegram.model.customEmojiIdOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.telegramyou.app.ui.motion.LocalReduceMotion
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
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
 * The sticker behind a custom emoji, by its id — what a Premium reaction is
 * drawn as. Provided with [LocalFileLoader]; null in previews.
 */
val LocalCustomEmojiLoader = staticCompositionLocalOf<(suspend (Long) -> StickerContent?)?> { null }

/**
 * A reaction by its key: a plain emoji as text, a custom one as its sticker
 * — [preloaded] when the caller has it already, fetched otherwise, and an
 * empty square of the same size until it arrives, so the chip does not
 * change width when it does.
 */
@Composable
fun ReactionGlyph(key: String, size: Dp, preloaded: StickerContent? = null, animate: Boolean = true) {
    val customId = customEmojiIdOf(key)
    if (customId == null) {
        Text(key, fontSize = with(LocalDensity.current) { (size * 0.8f).toSp() })
        return
    }
    val loader = LocalCustomEmojiLoader.current
    val sticker by produceState(preloaded, customId) {
        if (value == null && loader != null) value = loader(customId)
    }
    val drawn = sticker
    if (drawn != null) {
        StickerView(drawn, size = size, animate = animate)
    } else {
        Box(Modifier.size(size))
    }
}

/**
 * One sticker, [size] square: a picture drawn by Coil, a Lottie animation
 * played by Lottie — a `.tgs` is a gzipped Lottie file, which is all the
 * format is — or a video sticker, played with its transparency by
 * [VideoSticker].
 *
 * Whatever is not on this device yet is fetched through [LocalFileLoader];
 * while it is on its way the sticker is a [StickerSkeleton] — its emoji
 * used to stand in, and flashed a row of smileys across the picker before
 * the stickers landed. The emoji is kept for a sticker that has no file to
 * wait for (the demo's) or whose file will not decode. [animate] is off in the picker, where a grid of
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
    // Whether a file is still to come: until the loader has answered there
    // is something worth waiting for, and a skeleton says so.
    var waiting by remember(sticker.fileId) {
        mutableStateOf(sticker.path == null && sticker.fileId != null && loader != null)
    }
    // The sticker's own file for every format now, a video sticker's too:
    // it plays (VideoSticker) rather than standing still on its thumbnail.
    val path by produceState(sticker.path, sticker.fileId) {
        if (value == null) {
            val id = sticker.fileId
            if (id != null && loader != null) value = loader(id)
        }
        waiting = false
    }
    val description = "${sticker.emoji} sticker"
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        val file = path
        // While a video sticker's file is on its way, or if it will not
        // decode: its still, where it has one, or its emoji.
        val still: @Composable () -> Unit = {
            val thumb = sticker.thumbPath
            if (sticker.format == StickerFormat.Webm && thumb != null) {
                AsyncImage(
                    model = File(thumb),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(size)
                )
            } else if (waiting) {
                StickerSkeleton(size)
            } else {
                EmojiSticker(sticker.emoji, size)
            }
        }
        when {
            file == null -> still()
            sticker.format == StickerFormat.Webm -> VideoSticker(file, size, animate, description, still)
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
    // Null while it is read, empty if it would not: a skeleton for the
    // first, the emoji for the second.
    val json by produceState<String?>(null, path) {
        value = withContext(Dispatchers.IO) {
            try {
                GZIPInputStream(File(path).inputStream()).bufferedReader().use { it.readText() }
            } catch (_: Exception) {
                ""
            }
        }
    }
    val text = json
    if (text == null) {
        StickerSkeleton(size)
        return
    }
    if (text.isEmpty()) {
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

/**
 * Where a sticker will be, while it is fetched: a soft rounded square in the
 * surface's container tone, breathing slowly so it reads as on its way
 * rather than as a missing picture. Material ships no skeleton, so this is
 * drawn here; it holds still under Less motion.
 */
@Composable
fun StickerSkeleton(size: Dp, modifier: Modifier = Modifier) {
    val still = LocalReduceMotion.current
    val tone = MaterialTheme.colorScheme.surfaceContainerHighest
    val breath = if (still) {
        SKELETON_REST
    } else {
        val pulse = rememberInfiniteTransition(label = "stickerSkeleton")
        val alpha by pulse.animateFloat(
            initialValue = SKELETON_REST,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(SKELETON_BREATH_MS), RepeatMode.Reverse),
            label = "stickerSkeletonAlpha"
        )
        alpha
    }
    Box(
        modifier
            .size(size)
            .padding(size * SKELETON_INSET)
            .graphicsLayer { alpha = breath }
            .background(tone, RoundedCornerShape(size * SKELETON_CORNER))
    )
}

private const val SKELETON_REST = 0.45f
private const val SKELETON_BREATH_MS = 900
private const val SKELETON_INSET = 0.1f
private const val SKELETON_CORNER = 0.24f
