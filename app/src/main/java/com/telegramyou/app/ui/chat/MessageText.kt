package com.telegramyou.app.ui.chat

import com.telegramyou.app.telegram.model.StickerContent
import androidx.compose.runtime.produceState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.unit.em
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.Placeholder
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.InlineTextContent
import com.telegramyou.app.ui.icons.Symbols
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.telegramyou.app.telegram.model.ChatMessage
import com.telegramyou.app.telegram.model.EntityType
import com.telegramyou.app.telegram.model.TextEntity
import com.telegramyou.app.telegram.model.linkTarget

/**
 * A message's words with Telegram's formatting on them: bold, italic and the
 * rest as span styles, and links, mentions and hashtags as Compose's own
 * [LinkAnnotation]s — tappable where they are, with the bubble's long press
 * still the bubble's everywhere else. Nothing is drawn by hand.
 *
 * Links open in the browser through the platform's handler; a mention and a
 * hashtag go to [onMention] and [onHashtag], which know where they lead. A
 * spoiler is covered until tapped, and stays uncovered for as long as the
 * message is on screen.
 */
@Composable
internal fun FormattedText(
    text: String,
    entities: List<TextEntity>,
    color: Color,
    linkColor: Color,
    onMention: (String) -> Unit,
    onHashtag: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (entities.isEmpty()) {
        Text(text, color = color, modifier = modifier)
        return
    }
    var revealed by remember(text) { mutableStateOf(false) }
    val linkStyles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
    // Custom emoji stand in the text as their stickers: the characters under
    // each are handed over as inline content of the same length, so every
    // other entity's offsets still land where they did.
    val custom = entities.filter { it.type is EntityType.CustomEmoji }.sortedBy { it.offset }
    val annotated = buildAnnotatedString {
        var at = 0
        custom.forEach { entity ->
            if (entity.offset < at) return@forEach
            append(text.substring(at, entity.offset))
            appendInlineContent(
                "custom:${(entity.type as EntityType.CustomEmoji).id}",
                text.substring(entity.offset, entity.end)
            )
            at = entity.end
        }
        append(text.substring(at))
        entities.forEach { entity ->
            val start = entity.offset
            val end = entity.end
            val part = text.substring(start, end)
            when (val type = entity.type) {
                EntityType.Bold -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                EntityType.Italic, EntityType.BlockQuote -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                EntityType.Underline -> addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)
                EntityType.Strikethrough -> addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), start, end)
                EntityType.Code, EntityType.Pre -> addStyle(
                    SpanStyle(fontFamily = FontFamily.Monospace, background = color.copy(alpha = 0.12f)),
                    start,
                    end
                )
                EntityType.Spoiler -> if (!revealed) {
                    // The words are there but the colour of their cover —
                    // a tap takes the cover off.
                    addStyle(SpanStyle(color = Color.Transparent, background = color.copy(alpha = 0.35f)), start, end)
                    addLink(LinkAnnotation.Clickable("spoiler") { revealed = true }, start, end)
                }
                EntityType.Url, is EntityType.TextUrl, EntityType.Email, EntityType.Phone ->
                    linkTarget(text, entity)?.let { addLink(LinkAnnotation.Url(it, linkStyles), start, end) }
                EntityType.Mention -> addLink(
                    LinkAnnotation.Clickable("mention", linkStyles) { onMention(part.removePrefix("@")) },
                    start,
                    end
                )
                EntityType.Hashtag, EntityType.Cashtag -> addLink(
                    LinkAnnotation.Clickable("hashtag", linkStyles) { onHashtag(part) },
                    start,
                    end
                )
                // A mention without a username, or a command for a bot:
                // coloured as what they are, nothing to open yet.
                is EntityType.MentionName, EntityType.BotCommand ->
                    addStyle(SpanStyle(color = linkColor), start, end)
                // Drawn as inline content, above.
                is EntityType.CustomEmoji -> Unit
            }
        }
    }
    val inline = custom.associate { entity ->
        val id = (entity.type as EntityType.CustomEmoji).id
        val fallback = text.substring(entity.offset, entity.end)
        "custom:$id" to InlineTextContent(
            Placeholder(CUSTOM_EMOJI_EM.em, CUSTOM_EMOJI_EM.em, PlaceholderVerticalAlign.TextCenter)
        ) {
            CustomEmojiGlyph(id = id, fallback = fallback)
        }
    }
    Text(annotated, color = color, inlineContent = inline, modifier = modifier)
}

/**
 * One custom emoji in a line of text: its sticker once fetched, playing if
 * it moves, and its ordinary emoji until then — the placeholder's size,
 * which the line has already made room for.
 */
@Composable
private fun CustomEmojiGlyph(id: Long, fallback: String) {
    val loader = LocalCustomEmojiLoader.current
    val sticker by produceState<StickerContent?>(null, id) {
        if (loader != null) value = loader(id)
    }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val drawn = sticker
        if (drawn != null) {
            StickerView(drawn, size = maxWidth)
        } else {
            Text(fallback)
        }
    }
}

/** A custom emoji's square in a line, in ems: a little over a letter, as Telegram draws them. */
private const val CUSTOM_EMOJI_EM = 1.25f

/**
 * Photos sent together, as one grid in one bubble — Telegram's album. Two
 * side by side, an odd one out across the top, square cells, and the
 * album's caption under them. Each cell asks for its own photo as it
 * appears and opens it on a tap, exactly as a single photo does.
 */
@Composable
internal fun AlbumGrid(
    photos: List<ChatMessage>,
    onVisible: (ChatMessage) -> Unit,
    onOpen: (ChatMessage) -> Unit,
    captionColor: Color,
    onLongClick: () -> Unit = {}
) {
    val rows = buildList {
        if (photos.size % 2 == 1) add(photos.take(1))
        addAll(photos.drop(photos.size % 2).chunked(2))
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.widthIn(min = 240.dp).clip(MaterialTheme.shapes.medium)
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { photo ->
                    AlbumCell(
                        photo = photo,
                        wide = row.size == 1,
                        onVisible = { onVisible(photo) },
                        onOpen = { onOpen(photo) },
                        onLongClick = onLongClick,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        photos.firstOrNull { it.text.isNotBlank() && it.text != "Photo" }?.let { captioned ->
            Text(captioned.text, color = captionColor, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun AlbumCell(
    photo: ChatMessage,
    wide: Boolean,
    onVisible: () -> Unit,
    onOpen: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier
) {
    val path = photo.photoPath
    LaunchedEffect(path) { if (path == null) onVisible() }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .aspectRatio(if (wide) 16f / 9f else 1f)
            .combinedClickable(enabled = path != null, onClick = onOpen, onLongClick = onLongClick)
    ) {
        val mini = photo.photoMini
        if (mini != null) BlurredMini(mini, Modifier.fillMaxSize())
        if (path != null) {
            AsyncImage(
                model = path,
                contentDescription = "Photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (mini == null) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Symbols.Image,
                    contentDescription = "Photo",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}
