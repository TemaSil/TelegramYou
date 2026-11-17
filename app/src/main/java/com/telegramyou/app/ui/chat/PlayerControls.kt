package com.telegramyou.app.ui.chat

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.unit.dp

data class PlayerBackdrop(val layer: GraphicsLayer, val origin: Offset)

/**
 * Owner-approved experiment, 17 November: blur only behind player controls.
 * Replay the video layer through Android's render effect; no per-frame bitmap
 * copies, no blur on the glyph, and no new blurred material elsewhere.
 */
@Composable
internal fun PlayerControlButton(icon: ImageVector, description: String, onClick: () -> Unit,
    modifier: Modifier = Modifier.size(48.dp), backdrop: PlayerBackdrop? = null, enabled: Boolean = true) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val blurred = rememberGraphicsLayer()
    Box(modifier.clip(CircleShape).onGloballyPositioned { origin = it.positionOnScreen() }, contentAlignment = Alignment.Center) {
        if (Build.VERSION.SDK_INT >= 31 && backdrop != null) {
            Canvas(Modifier.matchParentSize()) {
                blurred.renderEffect = BlurEffect(18.dp.toPx(), 18.dp.toPx(), TileMode.Clamp)
                blurred.record {
                    val offset = origin - backdrop.origin
                    translate(-offset.x, -offset.y) { drawLayer(backdrop.layer) }
                }
                drawLayer(blurred)
            }
        }
        OutlinedIconButton(
            onClick = onClick, enabled = enabled,
            colors = IconButtonDefaults.outlinedIconButtonColors(
                containerColor = Color.Black.copy(alpha = if (backdrop != null && Build.VERSION.SDK_INT >= 31) 0.22f else 0.4f),
                contentColor = Color.White, disabledContentColor = Color.White.copy(alpha = 0.4f)
            ),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
            modifier = Modifier.matchParentSize()
        ) { Icon(icon, description) }
    }
}
