package com.telegramyou.app.ui.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * [content] as a QR code, [size] square, in the theme's colours.
 *
 * ZXing works out which modules are dark; drawing them is done here, as
 * rounded squares, so the code sits with the rest of an Expressive screen
 * rather than looking pasted in from a printer. Always dark modules on a
 * light ground, in either theme: a phone's camera reads that, and many
 * scanners — Telegram's among them, on some versions — do not read the
 * inverse.
 */
@Composable
fun QrCode(content: String, size: Dp, modifier: Modifier = Modifier) {
    val matrix = remember(content) {
        QRCodeWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            0,
            0,
            mapOf(
                EncodeHintType.MARGIN to 0,
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M
            )
        )
    }
    val scheme = MaterialTheme.colorScheme
    val light = scheme.surface.luminance() > 0.5f
    val ground = if (light) scheme.primaryContainer else scheme.onPrimaryContainer
    val ink = if (light) scheme.onPrimaryContainer else scheme.primaryContainer
    Box(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .background(ground)
            .padding(20.dp)
            .semantics { contentDescription = "QR code to sign in" }
    ) {
        Canvas(Modifier.size(size)) {
            val cell = this.size.width / matrix.width
            val inset = cell * 0.08f
            val radius = CornerRadius(cell * 0.32f)
            // The three corner squares are what a scanner looks for first,
            // and it finds them fastest solid. Drawn as dots like the rest,
            // they were a pattern a camera has to assemble.
            val finders = listOf(0 to 0, matrix.width - FINDER to 0, 0 to matrix.height - FINDER)
            fun inFinder(x: Int, y: Int) = finders.any { (fx, fy) ->
                x in fx until fx + FINDER && y in fy until fy + FINDER
            }
            for (x in 0 until matrix.width) {
                for (y in 0 until matrix.height) {
                    if (!matrix[x, y] || inFinder(x, y)) continue
                    drawRoundRect(
                        color = ink,
                        topLeft = Offset(x * cell + inset, y * cell + inset),
                        size = Size(cell - 2 * inset, cell - 2 * inset),
                        cornerRadius = radius
                    )
                }
            }
            finders.forEach { (fx, fy) ->
                val origin = Offset(fx * cell, fy * cell)
                // The ring: seven modules across, one thick.
                drawRoundRect(
                    color = ink,
                    topLeft = origin + Offset(cell / 2, cell / 2),
                    size = Size(cell * (FINDER - 1), cell * (FINDER - 1)),
                    cornerRadius = CornerRadius(cell * 1.6f),
                    style = Stroke(width = cell)
                )
                // The eye: three across, in the middle.
                drawRoundRect(
                    color = ink,
                    topLeft = origin + Offset(cell * 2, cell * 2),
                    size = Size(cell * 3, cell * 3),
                    cornerRadius = CornerRadius(cell * 0.9f)
                )
            }
        }
    }
}

/** A QR code's corner squares are seven modules across. */
private const val FINDER = 7
