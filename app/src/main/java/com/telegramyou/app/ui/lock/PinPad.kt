@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.telegramyou.app.ui.lock

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.toPath
import com.telegramyou.app.ui.components.SHAPE_COUNT
import com.telegramyou.app.ui.components.materialPolygon
import com.telegramyou.app.ui.icons.Symbols
import com.telegramyou.app.ui.motion.LocalReduceMotion

/**
 * A PIN's dots and keypad, after the lock screen of a Pixel (2.4): round
 * tonal keys that turn into a rounded square and light up under the finger,
 * and a dot for each digit that arrives as one of Material's shapes and
 * settles into a circle. The lock in front of the app and the PIN asked
 * before the lock is changed or turned off both draw this.
 *
 * Delete stands left of 0, and [extraKey] right of it — the fingerprint,
 * where there is one — as on a Pixel.
 */
@Composable
internal fun PinPad(
    pinLength: Int,
    typed: String,
    enabled: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    extraKey: @Composable () -> Unit = {}
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(DOT_ROOM)
                .semantics { contentDescription = "${typed.length} of $pinLength digits" }
        ) {
            repeat(pinLength) { index -> PinDot(filled = index < typed.length, index = index) }
        }
        Spacer(Modifier.height(40.dp))
        Column(verticalArrangement = Arrangement.spacedBy(KEY_GAP)) {
            listOf("123", "456", "789").forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
                    row.forEach { digit -> PinKey(digit, enabled) { onDigit(digit) } }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(KEY_GAP),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Delete left of 0 and the fingerprint right of it, as the
                // Pixel's own keypad has them.
                PinIconKey(Symbols.Backspace, "Delete digit", enabled = typed.isNotEmpty(), onClick = onBackspace)
                PinKey('0', enabled) { onDigit('0') }
                Box(Modifier.size(KEY_SIZE), contentAlignment = Alignment.Center) { extraKey() }
            }
        }
    }
}

/**
 * Delete, or the fingerprint: a plain icon on a key's whole footprint, its
 * glyph sized to read beside the digits — a stock icon button's 24 dp was
 * lost next to 80 dp keys.
 */
@Composable
internal fun PinIconKey(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(KEY_SIZE)) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(PIN_ICON))
    }
}

/**
 * One key: a tonal circle that, held, becomes a rounded square on the
 * primary container — the Pixel's keypad — and springs back on letting go.
 */
@Composable
private fun PinKey(digit: Char, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateIntAsState(
        targetValue = if (pressed) PRESSED_CORNER else 50,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "pinKeyCorner"
    )
    val container by animateColorAsState(
        targetValue = if (pressed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "pinKeyColor"
    )
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(percent = corner),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = container),
        interactionSource = interaction,
        modifier = Modifier
            .size(KEY_SIZE)
            .semantics { contentDescription = "Digit $digit" }
    ) {
        Text(digit.toString(), style = MaterialTheme.typography.headlineMedium)
    }
}

/**
 * One digit's dot: a small ring while empty; typed, one of Material's
 * shapes, a little larger, that settles into a filled circle — the Pixel's
 * way of showing a digit went in without showing which.
 */
@Composable
private fun PinDot(filled: Boolean, index: Int) {
    val still = LocalReduceMotion.current
    val settle = remember { Animatable(1f) }
    LaunchedEffect(filled) {
        if (filled && !still) {
            settle.snapTo(0f)
            settle.animateTo(1f, tween(SETTLE_MILLIS, easing = FastOutSlowInEasing))
        } else {
            settle.snapTo(1f)
        }
    }
    val morph = remember(index) {
        // Any of the set but its plain circle, which is entry 0.
        Morph(materialPolygon(1 + index % (SHAPE_COUNT - 1)), RoundedPolygon.circle(numVertices = 12).normalized())
    }
    val color = MaterialTheme.colorScheme.onSurface
    if (!filled) {
        Box(
            Modifier
                .size(EMPTY_DOT)
                .border(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
        )
    } else {
        val progress = settle.value
        val size = FRESH_DOT + (FILLED_DOT - FRESH_DOT) * progress
        Box(
            Modifier
                .size(size)
                .background(color, if (progress >= 1f) CircleShape else MorphShape(morph, progress))
        )
    }
}

/** One moment of a morph, scaled from its unit square to the dot. */
private class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress)
        path.transform(android.graphics.Matrix().apply { setScale(size.width, size.height) })
        return Outline.Generic(path.asComposePath())
    }
}

private val KEY_SIZE = 80.dp
private val KEY_GAP = 16.dp
private val PIN_ICON = 32.dp
/** Pressed, a key's corners as a percentage of its side: a squircle. */
private const val PRESSED_CORNER = 30
private val DOT_ROOM = 24.dp
private val EMPTY_DOT = 10.dp
private val FILLED_DOT = 14.dp
private val FRESH_DOT = 22.dp
private const val SETTLE_MILLIS = 450
