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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.telegramyou.app.settings.BubbleCorners
import com.telegramyou.app.settings.ChatWallpaper
import com.telegramyou.app.settings.OutgoingTone
import kotlin.math.PI
import kotlin.math.sin

/**
 * How conversations look, from Settings → Appearance: what they are drawn
 * on, the tone of the account's own messages, how round the bubbles are and
 * how large their text is. Provided once, by MainActivity, and read where a
 * conversation is drawn — and by the preview on the Appearance screen, so
 * it shows what a chat will.
 */
data class ChatStyle(
    val wallpaper: ChatWallpaper = ChatWallpaper.Gradient,
    val outgoingTone: OutgoingTone = OutgoingTone.Accent,
    val bubbleCorners: Int = BubbleCorners.DEFAULT,
    val messageTextScale: Float = 1f
)

val LocalChatStyle = staticCompositionLocalOf { ChatStyle() }

/**
 * The conversation's background: the theme's surface into a breath of the
 * accent, as it always was, and on top of it — for Dots and Waves — a
 * pattern in the theme's own colours, faint enough to stay behind the
 * messages. Drawn by hand, which is what CLAUDE.md keeps the wallpaper for.
 */
@Composable
fun Modifier.chatWallpaper(wallpaper: ChatWallpaper): Modifier {
    val colors = MaterialTheme.colorScheme
    if (wallpaper == ChatWallpaper.Plain) return background(colors.surface)
    val gradient = Brush.verticalGradient(
        listOf(colors.surfaceContainerLow, colors.surface, colors.primary.copy(alpha = 0.08f))
    )
    val ink = colors.onSurface.copy(alpha = 0.06f)
    val wave = colors.primary.copy(alpha = 0.10f)
    return background(gradient).drawBehind {
        when (wallpaper) {
            ChatWallpaper.Dots -> {
                val step = 22.dp.toPx()
                val radius = 1.6.dp.toPx()
                var row = 0
                var y = step / 2
                while (y < size.height) {
                    // Every other row shifted by half a step, so the grid
                    // reads as a texture rather than as graph paper.
                    var x = if (row % 2 == 0) step / 2 else step
                    while (x < size.width) {
                        drawCircle(ink, radius, Offset(x, y))
                        x += step
                    }
                    y += step
                    row++
                }
            }
            ChatWallpaper.Waves -> {
                val spacing = 36.dp.toPx()
                val amplitude = 6.dp.toPx()
                val length = 120.dp.toPx()
                val stroke = Stroke(width = 1.5.dp.toPx())
                var baseline = spacing / 2
                while (baseline < size.height + amplitude) {
                    val path = Path()
                    var x = 0f
                    path.moveTo(0f, baseline)
                    while (x <= size.width) {
                        path.lineTo(x, baseline + amplitude * sin(2 * PI * x / length).toFloat())
                        x += 6f
                    }
                    drawPath(path, wave, style = stroke)
                    baseline += spacing
                }
            }
            else -> Unit
        }
    }
}

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
