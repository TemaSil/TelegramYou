package com.telegramyou.app.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.telegramyou.app.ui.theme.AppTitleStyle

/**
 * A page's name in a large top app bar, in the app's title face — Settings',
 * Search's and Music's, which the owner asked to match. Larger than the name
 * on Home while the bar is open, since it is the page's only heading, and
 * shrinking to the folded bar's size as the page scrolls up: [folded] is the
 * bar's collapsed fraction. One lambda draws both of the bar's title slots,
 * so following the fold keeps them the same size.
 */
@Composable
fun LargeTitle(text: String, folded: Float) {
    Text(
        text,
        style = AppTitleStyle.copy(
            fontSize = lerp(36.sp, 22.sp, folded),
            lineHeight = lerp(44.sp, 28.sp, folded)
        ),
        maxLines = 1
    )
}
