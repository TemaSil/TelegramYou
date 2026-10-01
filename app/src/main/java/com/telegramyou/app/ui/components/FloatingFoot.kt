package com.telegramyou.app.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection

/**
 * A Scaffold's padding without its foot.
 *
 * The mini player is a Scaffold's bottomBar, a capsule with nothing behind
 * it. Padded by the whole of the Scaffold's padding, a page stopped above
 * it and left a band of bare background around the capsule — which the
 * owner saw on a phone and asked to be gone (1.6.9). So a page that scrolls
 * takes the top and sides here, runs on under the capsule, and adds the
 * foot to its list's own contentPadding, so its last row can still be
 * scrolled clear of the capsule.
 */
@Composable
fun PaddingValues.withoutBottom(): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(direction),
        top = calculateTopPadding(),
        end = calculateEndPadding(direction)
    )
}
