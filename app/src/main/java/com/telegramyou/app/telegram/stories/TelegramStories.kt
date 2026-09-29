package com.telegramyou.app.telegram.stories

import com.telegramyou.app.telegram.model.StoryAudience
import com.telegramyou.app.telegram.model.StoryFrame
import com.telegramyou.app.telegram.model.StoryItem
import kotlinx.coroutines.flow.StateFlow

/** The stories rail above the chat list. */
interface TelegramStories {
    val stories: StateFlow<List<StoryItem>>

    /** What the circle [storyId] holds, oldest first; empty when it is gone. */
    suspend fun storyFrames(storyId: Long): List<StoryFrame>

    /** Story [frameId] of the circle [storyId] has been watched. */
    suspend fun markStorySeen(storyId: Long, frameId: Int)

    /**
     * Posts a story from this account: the photo or video at [uri], with
     * [caption], shown to [audience] for a day. Throws with a reason people
     * can read when Telegram will not take it — a limit reached, Premium
     * needed, a video too long.
     */
    suspend fun postStory(uri: String, isVideo: Boolean, caption: String, audience: StoryAudience) {}
}
