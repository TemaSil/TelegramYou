package com.telegramyou.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val TelegramYouShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

// No bubble shapes here any more. A bubble's corners depend on where it sits
// in a run — tight against its neighbours, tailed on the last of them — so
// MessageBubble builds its own RoundedCornerShape per message. A pair of
// fixed shapes alongside that would be a second answer to the same question,
// and the wrong one.
// A fixed radius, which is the reverse of the decision before it, and on the
// owner's word from a phone. Fifty percent of the shorter side kept the
// capsule a capsule at any height — but at five lines its ends were half-discs
// five lines tall, and they cut into the buttons sitting in its corners.
// 28.dp is fully round on one line (the composer is 56dp there; the field
// inside, shorter, clamps to its own half-height) and a rounded rectangle as
// the text grows, so the curve gets proportionally smaller the taller it is.
val ComposerShape = RoundedCornerShape(28.dp)
