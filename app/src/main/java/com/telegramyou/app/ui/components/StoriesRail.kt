package com.telegramyou.app.ui.components

import com.telegramyou.app.i18n.tr

import com.telegramyou.app.ui.motion.containerTransform
import com.telegramyou.app.ui.motion.storyContainerKey
import com.telegramyou.app.ui.motion.StoryContainerShape
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import com.telegramyou.app.ui.icons.Symbols
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
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
    // No heading. A row of faces with rings around them is a thing everyone
    // has seen, and a word above it labelled the obvious.
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(CELL_GAP)
        ) {
            // With a story of this account's up, the add entry says what it
            // does, and "My story" is the one that opens what was posted.
            val hasMine = stories.any { it.isMine }
            stories.forEach { story ->
                StoryOrb(story = story, addLabel = if (hasMine) tr("Add story", "Добавить") else tr("My story", "Моя история"), onClick = { onStoryClick(story) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StoryOrb(story: StoryItem, addLabel: String, onClick: () -> Unit) {
    val alpha by animateFloatAsState(
        targetValue = if (story.hasUnseen || story.isOwn) 1f else 0.72f,
        // Fading a seen story is a change of appearance, not of position,
        // so it takes an effects spec rather than a spatial one.
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "storyAlpha"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        // The whole cell takes the tap, name included: a finger aimed at the
        // name under a circle is aiming at that story, and only the circle
        // used to answer — the smoke test missed it exactly that way.
        // The name kept clear of the cell's rounded corners: a name as wide
        // as the cell lost its first and last letters to them.
        modifier = Modifier
            .width(CELL_WIDTH)
            .alpha(alpha)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = CELL_INSET, vertical = 2.dp)
    ) {
        if (story.isOwn) {
            // The add entry is a button, not somebody's face: the theme's own
            // primary container — the wallpaper's colour — rather than an
            // avatar's seeded hue, which drew it a fixed green.
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Symbols.Add, contentDescription = null, modifier = Modifier.size(28.dp))
                }
            }
        } else {
            AvatarBubble(
                title = story.authorName,
                seed = story.avatarColor,
                size = 64.dp,
                ring = true,
                ringSeen = !story.hasUnseen,
                photoPath = story.photoPath,
                // The circle the viewer opens out of and closes back into.
                modifier = Modifier.containerTransform(storyContainerKey(story.id), StoryContainerShape)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (story.isOwn) addLabel else story.authorName,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * A story's cell: wide enough for a name a little longer than its circle,
 * set in from the cell's corners; the pitch between circles is what it was
 * with 72 dp cells 14 dp apart.
 */
private val CELL_WIDTH = 80.dp
private val CELL_INSET = 4.dp
private val CELL_GAP = 6.dp
