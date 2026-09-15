package com.telegramyou.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.telegramyou.app.telegram.model.StoryItem

@Composable
fun StoriesRail(
    stories: List<StoryItem>,
    onStoryClick: (StoryItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Stories",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            stories.forEach { story ->
                StoryOrb(story = story, onClick = { onStoryClick(story) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StoryOrb(story: StoryItem, onClick: () -> Unit) {
    val alpha by animateFloatAsState(
        targetValue = if (story.hasUnseen || story.isOwn) 1f else 0.72f,
        // Fading a seen story is a change of appearance, not of position,
        // so it takes an effects spec rather than a spatial one.
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "storyAlpha"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .alpha(alpha)
    ) {
        AvatarBubble(
            title = if (story.isOwn) "＋" else story.authorName,
            seed = story.avatarColor,
            size = 64.dp,
            ring = !story.isOwn,
            ringSeen = !story.hasUnseen,
            onClick = onClick
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (story.isOwn) "My story" else story.authorName,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
