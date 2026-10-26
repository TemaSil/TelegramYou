package com.telegramyou.app.ui.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.telegramyou.app.telegram.model.ProfileColor
import com.telegramyou.app.telegram.model.customReactionKey
import com.telegramyou.app.ui.chat.ReactionGlyph

/**
 * A name, and after it their emoji status where they have one — a custom
 * emoji, drawn as its sticker, the way Telegram puts it beside a Premium
 * name.
 */
@Composable
fun NameWithStatus(
    name: String,
    emojiStatusId: Long?,
    style: TextStyle,
    statusSize: Dp,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null
) {
    Row(
        modifier = modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            name,
            style = style,
            fontWeight = fontWeight,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (emojiStatusId != null) {
            Box(Modifier.semantics { contentDescription = "Emoji status" }) {
                ReactionGlyph(customReactionKey(emojiStatusId), size = statusSize)
            }
        }
    }
}

/**
 * The top of a profile: on the person's own colour where they chose one,
 * as a flat tonal card — Material's container and on-container roles worked
 * out from that colour, the way the avatars without a photo are — rather
 * than Telegram's gradient. Without one, the page's own surface.
 */
@Composable
fun ProfileHeader(
    color: ProfileColor?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val tones = profileTones(color)
    if (tones == null) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { content() }
        return
    }
    Surface(
        color = tones.first,
        contentColor = tones.second,
        shape = RoundedCornerShape(28.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .semantics { contentDescription = "Profile colour" }
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/** A profile colour's container and on-container, for this theme; null for none. */
@Composable
private fun profileTones(color: ProfileColor?): Pair<Color, Color>? {
    color ?: return null
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return remember(color, dark) {
        val seed = if (dark) color.dark else color.light
        val (container, onContainer) = com.telegramyou.app.ui.theme.avatarTones(seed, dark)
        Color(container) to Color(onContainer)
    }
}
