package com.telegramyou.app.ui.chat

import com.telegramyou.app.telegram.model.MessageContentType

/**
 * Whether a video's file is fetched whole, rather than left to stream.
 *
 * A video proper opens in the player, which plays it as it downloads where
 * the backend can stream (VideoPage); fetching it whole as well would fight
 * the stream for the connection. A GIF and a round video message play in
 * their bubble, from a file — nothing streams there — so they are fetched
 * whole whatever the backend can do. They were not, for live Telegram,
 * which streams: every round video message spun for ever and never played
 * (2.8.1), while the demo, which cannot stream, fetched them and passed.
 */
fun fetchesVideoWhole(type: MessageContentType, canStream: Boolean): Boolean =
    !canStream || type != MessageContentType.Video
