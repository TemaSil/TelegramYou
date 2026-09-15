package com.telegramyou.app.telegram.stories

import com.telegramyou.app.telegram.model.StoryItem
import kotlinx.coroutines.flow.StateFlow

/** The stories rail above the chat list. */
interface TelegramStories {
    val stories: StateFlow<List<StoryItem>>

    suspend fun markStorySeen(storyId: Long)
}
