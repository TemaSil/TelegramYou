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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.telegramyou.app.settings.BubbleCorners
import com.telegramyou.app.settings.ChatWallpaper
import com.telegramyou.app.settings.OutgoingTone
import com.telegramyou.app.ui.components.SHAPE_COUNT
import com.telegramyou.app.ui.components.materialShapeAt
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
 * The conversation's background, in the theme's own colours — the wallpaper
 * is on CLAUDE.md's short list of things this client draws by hand, and a
 * picture in somebody else's colours would fight the scheme.
 *
 * The first version kept every one of them faint, a breath of the accent,
 * and Gradient could hardly be told from Plain; the owner said so. They are
 * bolder now, and still quieter than the bubbles on them: the tints are the
 * container roles, which Material keeps apart from the fills a message uses.
 */
@Composable
fun Modifier.chatWallpaper(wallpaper: ChatWallpaper): Modifier {
    val colors = MaterialTheme.colorScheme
    val tinted = Brush.linearGradient(
        0f to colors.surface,
        0.55f to colors.primaryContainer.copy(alpha = 0.55f).compositeOver(colors.surface),
        1f to colors.tertiaryContainer.copy(alpha = 0.8f).compositeOver(colors.surface)
    )
    val quiet = Brush.verticalGradient(
        listOf(colors.surfaceContainerLow, colors.surface, colors.primaryContainer.copy(alpha = 0.35f).compositeOver(colors.surface))
    )
    return when (wallpaper) {
        ChatWallpaper.Plain -> background(colors.surface)
        ChatWallpaper.Gradient -> background(tinted)
        ChatWallpaper.Dots -> background(quiet).drawBehind { dots(colors.primary.copy(alpha = 0.16f)) }
        ChatWallpaper.Waves -> background(quiet).drawBehind { waves(colors.primary.copy(alpha = 0.18f)) }
        ChatWallpaper.Aurora -> background(colors.surface).drawBehind {
            aurora(colors.primaryContainer, colors.tertiaryContainer, colors.secondaryContainer)
        }
        ChatWallpaper.Shapes -> {
            // Material's own shapes, the avatars' set, scattered: the
            // Expressive language as a pattern rather than an imitation of
            // some other messenger's doodles.
            val shapes = List(SCATTER_SHAPES) { materialShapeAt((it * 5 + 2) % SHAPE_COUNT) }
            val ink = colors.primary.copy(alpha = 0.14f)
            val fill = colors.tertiaryContainer.copy(alpha = 0.45f)
            background(quiet).drawBehind { scatter(shapes, ink, fill) }
        }
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

private fun DrawScope.waves(ink: Color) {
    val spacing = 28.dp.toPx()
    val amplitude = 7.dp.toPx()
    val length = 110.dp.toPx()
    val stroke = Stroke(width = 2.dp.toPx())
    var baseline = spacing / 2
    var line = 0
    while (baseline < size.height + amplitude) {
        val path = Path()
        // Each line a quarter-wave on from the last, so they flow rather
        // than stack.
        val shift = line * length / 4
        var x = 0f
        path.moveTo(0f, baseline + amplitude * sin(2 * PI * shift / length).toFloat())
        while (x <= size.width) {
            path.lineTo(x, baseline + amplitude * sin(2 * PI * (x + shift) / length).toFloat())
            x += 6f
        }
        drawPath(path, ink, style = stroke)
        baseline += spacing
        line++
    }
}

/** Three soft glows of the scheme's containers, from three corners. */
private fun DrawScope.aurora(first: Color, second: Color, third: Color) {
    val reach = maxOf(size.width, size.height) * 0.75f
    fun glow(color: Color, center: Offset) = drawRect(
        Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center = center, radius = reach)
    )
    glow(first, Offset(0f, 0f))
    glow(second, Offset(size.width, size.height * 0.55f))
    glow(third, Offset(size.width * 0.2f, size.height))
}

/**
 * [shapes] on a loose grid, each nudged, turned and sized by its place, so
 * the pattern does not repeat in rows; every third one filled, the rest
 * outlined.
 */
private fun DrawScope.scatter(shapes: List<Shape>, ink: Color, fill: Color) {
    val cell = 72.dp.toPx()
    val stroke = Stroke(width = 1.5.dp.toPx())
    var index = 0
    var y = 0f
    while (y < size.height) {
        var x = 0f
        while (x < size.width) {
            val hash = (index * 2654435761L).toInt() ushr 8
            val side = cell * (0.30f + (hash % 20) / 100f)
            val dx = (hash % 7) / 7f * (cell - side)
            val dy = (hash / 7 % 7) / 7f * (cell - side)
            val outline = shapes[index % shapes.size].createOutline(Size(side, side), layoutDirection, this)
            translate(x + dx, y + dy) {
                rotate((hash % 360).toFloat(), pivot = Offset(side / 2, side / 2)) {
                    if (index % 3 == 0) drawOutline(outline, fill) else drawOutline(outline, ink, style = stroke)
                }
            }
            x += cell
            index++
        }
        y += cell
    }
}

private const val SCATTER_SHAPES = 6

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
