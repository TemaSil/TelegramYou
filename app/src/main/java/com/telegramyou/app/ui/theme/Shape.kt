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
// The composer's shape is not here either: it is round on one line and
// rounded once the text grows, animated between the two from its measured
// height — see ComposerBar.
