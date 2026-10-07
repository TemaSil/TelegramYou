package com.telegramyou.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.telegramyou.app.settings.BubbleCorners
import com.telegramyou.app.settings.ChatWallpaper
import com.telegramyou.app.settings.OutgoingTone
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * How conversations look, from Settings → Appearance: what they are drawn
 * on, the tone of the account's own messages, how round the bubbles are and
 * how large their text is. Provided once, by MainActivity, and read where a
 * conversation is drawn — and by the preview on the Appearance screen, so
 * it shows what a chat will.
 */
data class ChatStyle(
    val wallpaper: ChatWallpaper = ChatWallpaper.Plain,
    val outgoingTone: OutgoingTone = OutgoingTone.Accent,
    val bubbleCorners: Int = BubbleCorners.DEFAULT,
    val messageTextScale: Float = 1f
)

val LocalChatStyle = staticCompositionLocalOf { ChatStyle() }

/**
 * The conversation's background, in the theme's own colours — the wallpaper
 * is on CLAUDE.md's short list of things this client draws by hand, and a
 * picture in somebody else's colours would fight the scheme.
 *
 * Each pattern is one mark in one ink over the same quiet base: the surface
 * at the top, warming towards the primary container at the bottom. That
 * base and that ink are the ones Dots had when the owner called it the good
 * one; the three patterns share them, so the mark is the only difference.
 */
@Composable
fun Modifier.chatWallpaper(wallpaper: ChatWallpaper): Modifier {
    val colors = MaterialTheme.colorScheme
    val quiet = Brush.verticalGradient(
        listOf(colors.surfaceContainerLow, colors.surface, colors.primaryContainer.copy(alpha = WARMTH).compositeOver(colors.surface))
    )
    val ink = colors.primary.copy(alpha = PATTERN_INK)
    return when (wallpaper) {
        ChatWallpaper.Plain -> background(colors.surface)
        ChatWallpaper.Dots -> background(quiet).drawBehind { dots(ink) }
        ChatWallpaper.Sparkles -> background(quiet).drawBehind { sparkles(ink) }
        // Lines cover more of the page than dots do, so they are drawn
        // lighter to sit as quietly.
        ChatWallpaper.Grid -> background(quiet).drawBehind { grid(ink.copy(alpha = GRID_INK)) }
    }
}

private fun DrawScope.dots(ink: Color) {
    val step = 20.dp.toPx()
    val radius = 2.dp.toPx()
    var row = 0
    var y = step / 2
    while (y < size.height) {
        // Every other row shifted by half a step, so the grid reads as a
        // texture rather than as graph paper.
        var x = if (row % 2 == 0) step / 2 else step
        while (x < size.width) {
            drawCircle(ink, radius, Offset(x, y))
            x += step
        }
        y += step
        row++
    }
}

/**
 * Four-pointed sparkles, the star Expressive draws, scattered: each nudged
 * and sized by its place in a loose grid, so the pattern does not repeat in
 * rows.
 */
private fun DrawScope.sparkles(ink: Color) {
    val cell = 44.dp.toPx()
    var index = 0
    var y = 0f
    while (y < size.height) {
        var x = 0f
        while (x < size.width) {
            val hash = (index * 2654435761L).toInt() ushr 8
            val reach = (5 + hash % 6).dp.toPx()
            val dx = reach + (hash % 7) / 7f * (cell - 2 * reach)
            val dy = reach + (hash / 7 % 7) / 7f * (cell - 2 * reach)
            drawPath(sparkle(Offset(x + dx, y + dy), reach), ink)
            x += cell
            index++
        }
        y += cell
    }
}

/** A four-pointed star: an astroid, whose sides curve in to a pinch. */
private fun sparkle(center: Offset, reach: Float): Path = Path().apply {
    for (step in 0..SPARKLE_STEPS) {
        val t = 2 * PI * step / SPARKLE_STEPS
        val c = cos(t)
        val s = sin(t)
        val x = center.x + reach * (c * c * c).toFloat()
        val y = center.y + reach * (s * s * s).toFloat()
        if (step == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** Squared paper: hairlines both ways, a square [GRID_STEP] on a side. */
private fun DrawScope.grid(ink: Color) {
    val step = GRID_STEP.toPx()
    val width = 1.dp.toPx()
    var x = step / 2
    while (x < size.width) {
        drawLine(ink, Offset(x, 0f), Offset(x, size.height), width)
        x += step
    }
    var y = step / 2
    while (y < size.height) {
        drawLine(ink, Offset(0f, y), Offset(size.width, y), width)
        y += step
    }
}

private const val SPARKLE_STEPS = 32
private val GRID_STEP = 24.dp

/** How strongly a pattern is drawn: the primary colour at this alpha. */
private const val PATTERN_INK = 0.16f

/** The grid's lines, lighter than a mark, since they run the whole page. */
private const val GRID_INK = 0.10f

/**
 * One message in the chosen style: text at the message size, and — for the
 * account's own — the chosen tone. The tone is given by swapping the theme's
 * primary pair for the chosen container pair around the bubble, because
 * everything inside an outgoing bubble (text, links, ticks, the time) is
 * already drawn in primary and onPrimary; one swap recolours all of it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StyledMessage(outgoing: Boolean, content: @Composable () -> Unit) {
    val style = LocalChatStyle.current
    val density = LocalDensity.current
    val sized: @Composable () -> Unit = {
        CompositionLocalProvider(
            LocalDensity provides Density(density.density, density.fontScale * style.messageTextScale),
            content = content
        )
    }
    if (!outgoing || style.outgoingTone == OutgoingTone.Accent) {
        sized()
        return
    }
    val scheme = MaterialTheme.colorScheme
    val toned = when (style.outgoingTone) {
        OutgoingTone.Soft -> scheme.copy(primary = scheme.primaryContainer, onPrimary = scheme.onPrimaryContainer)
        OutgoingTone.Tertiary -> scheme.copy(primary = scheme.tertiaryContainer, onPrimary = scheme.onTertiaryContainer)
        OutgoingTone.Accent -> scheme
    }
    MaterialExpressiveTheme(
        colorScheme = toned,
        motionScheme = MaterialTheme.motionScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = sized
    )
}

/**
 * How much of the primary container the base warms to at the bottom. It was
 * 0.35, and with a saturated wallpaper colour in the dark theme the bottom of
 * the chat ran into neon — the owner's word, 2.0 — so it was taken down to
 * where it still warms and no longer glows.
 */
private const val WARMTH = 0.2f
