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

val BubbleOutgoingShape = RoundedCornerShape(22.dp, 22.dp, 8.dp, 22.dp)
val BubbleIncomingShape = RoundedCornerShape(22.dp, 22.dp, 22.dp, 8.dp)
val StoryRingShape = RoundedCornerShape(50)
val ComposerShape = RoundedCornerShape(28.dp)
