package com.telegramyou.app.telegram.tdlib

import com.telegramyou.app.telegram.model.ButtonAction
import com.telegramyou.app.telegram.model.InlineButton
import com.telegramyou.app.telegram.model.PollContent
import com.telegramyou.app.telegram.model.PollOption
import com.telegramyou.app.telegram.model.ReplyKey
import com.telegramyou.app.telegram.model.ReplyKeyboard
import com.telegramyou.app.ui.format.Presence
import com.telegramyou.app.telegram.model.ChatMessage
import com.telegramyou.app.telegram.model.EntityType
import com.telegramyou.app.telegram.model.TextEntity
import com.telegramyou.app.telegram.model.clampEntities
import com.telegramyou.app.telegram.model.MessageReaction
import com.telegramyou.app.telegram.model.LinkPreview
import com.telegramyou.app.telegram.model.ProxyKind
import com.telegramyou.app.telegram.model.ProxyServer
import com.telegramyou.app.telegram.model.StickerContent
import com.telegramyou.app.telegram.model.StickerFormat
import com.telegramyou.app.telegram.model.StoryFrame
import com.telegramyou.app.telegram.model.VideoContent
// Aliased: this class has a toggleReaction of its own, with a different job.
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * TDLib's JSON read into this app's model, and the few requests built
 * from plain values: functions of what they are handed and nothing else.
 * Out of TdLibTelegramClient, which keeps the state — the chats, the users,
 * the engine — and was near four thousand lines with these inside it.
 */

internal fun defaultNotificationSettings(): JSONObject = JSONObject()
    .put("@type", "chatNotificationSettings")
    .put("use_default_sound", true)
    .put("use_default_show_preview", true)
    .put("use_default_mute_stories", true)
    .put("use_default_story_sound", true)
    .put("use_default_show_story_poster", true)
    .put("use_default_disable_pinned_message_notifications", true)
    .put("use_default_disable_mention_notifications", true)

/**
 * TDLib expects a reply as an inputMessageReplyToMessage on the send, not
 * a bare id. Absent when nothing is being answered — passing a null
 * message_id would be rejected.
 */
internal fun JSONObject.withReplyTo(replyToId: Long?): JSONObject = apply {
    if (replyToId != null) {
        put(
            "reply_to",
            JSONObject()
                .put("@type", "inputMessageReplyToMessage")
                .put("message_id", replyToId)
        )
    }
}

internal fun storyFrame(story: JSONObject, isSeen: Boolean): StoryFrame {
    val content = story.optJSONObject("content")
    val caption = story.optJSONObject("caption")?.optString("text").orEmpty()
    val base = StoryFrame(
        id = story.optInt("id"),
        caption = caption,
        date = story.optLong("date"),
        isSeen = isSeen
    )
    return when (content?.optString("@type")) {
        "storyContentPhoto" -> {
            // The largest size: a story fills the screen.
            val sizes = content.optJSONObject("photo")?.optJSONArray("sizes")
            val file = sizes?.optJSONObject(sizes.length() - 1)?.optJSONObject("photo")
            base.copy(fileId = file?.optInt("id"), localPath = file?.localPathIfDownloaded())
        }
        "storyContentVideo" -> {
            val video = content.optJSONObject("video")
            val file = video?.optJSONObject("video")
            base.copy(
                fileId = file?.optInt("id"),
                localPath = file?.localPathIfDownloaded(),
                isVideo = true,
                durationSeconds = video?.optDouble("duration") ?: 0.0
            )
        }
        else -> base
    }
}

/** A `proxy` — server, port and a type carrying that type's credentials. */
internal fun proxyObject(proxy: ProxyServer): JSONObject {
    val type = when (proxy.kind) {
        ProxyKind.Socks5 -> JSONObject()
            .put("@type", "proxyTypeSocks5")
            .put("username", proxy.username)
            .put("password", proxy.password)
        ProxyKind.Http -> JSONObject()
            .put("@type", "proxyTypeHttp")
            .put("username", proxy.username)
            .put("password", proxy.password)
            .put("http_only", false)
        ProxyKind.MtProto -> JSONObject()
            .put("@type", "proxyTypeMtproto")
            .put("secret", proxy.secret)
    }
    return JSONObject()
        .put("@type", "proxy")
        .put("server", proxy.server)
        .put("port", proxy.port)
        .put("type", type)
}

/** A TDLib `sticker`, read into the model the screens draw. */
internal fun stickerOf(sticker: JSONObject): StickerContent {
    val file = sticker.optJSONObject("sticker")
    val thumbnail = sticker.optJSONObject("thumbnail")?.optJSONObject("file")
    return StickerContent(
        id = sticker.optInt64("id"),
        emoji = sticker.optString("emoji"),
        format = when (sticker.optJSONObject("format")?.optString("@type")) {
            "stickerFormatTgs" -> StickerFormat.Tgs
            "stickerFormatWebm" -> StickerFormat.Webm
            else -> StickerFormat.Webp
        },
        width = sticker.optInt("width", 512),
        height = sticker.optInt("height", 512),
        fileId = file?.optInt("id")?.takeIf { it != 0 },
        path = file?.localPathIfDownloaded(),
        thumbFileId = thumbnail?.optInt("id")?.takeIf { it != 0 },
        thumbPath = thumbnail?.localPathIfDownloaded()
    )
}

/** A set's own picture, drawn the way a sticker of the same format is. */
internal fun stickerFromThumbnail(thumbnail: JSONObject, title: String): StickerContent? {
    val file = thumbnail.optJSONObject("file") ?: return null
    val fileId = file.optInt("id").takeIf { it != 0 } ?: return null
    return StickerContent(
        emoji = title.take(1),
        format = when (thumbnail.optJSONObject("format")?.optString("@type")) {
            "thumbnailFormatTgs" -> StickerFormat.Tgs
            "thumbnailFormatWebm" -> StickerFormat.Webm
            else -> StickerFormat.Webp
        },
        width = thumbnail.optInt("width"),
        height = thumbnail.optInt("height"),
        fileId = fileId,
        path = file.localPathIfDownloaded(),
        // A webm cover has no still of its own; drawn as its title's
        // first letter until this client plays video stickers.
        thumbFileId = null
    )
}

/**
 * A file on this device, as TDLib takes one.
 *
 * Photos, documents and voice notes each wrap it in an object of their
 * own — `inputPhoto`, `inputDocument`, `inputVoiceNote` — since the TDLib
 * this app is built with. Handing TDLib the bare file where one of those
 * belongs is what made every photo fail with "Input file is not
 * specified": it looked inside for a file and found none.
 */
internal fun localFile(path: String): JSONObject =
    JSONObject().put("@type", "inputFileLocal").put("path", path)

internal fun inputPhoto(path: String): JSONObject =
    JSONObject().put("@type", "inputPhoto").put("photo", localFile(path))

/** A TDLib `userStatus`, read into the tested model in :core. */
internal fun presenceOf(user: JSONObject): Presence {
    val status = user.optJSONObject("status") ?: return Presence.Unknown
    return when (status.optString("@type")) {
        "userStatusOnline" -> Presence.Online(status.optLong("expires"))
        "userStatusOffline" -> Presence.Offline(status.optLong("was_online"))
        "userStatusRecently" -> Presence.Recently
        "userStatusLastWeek" -> Presence.WithinWeek
        "userStatusLastMonth" -> Presence.WithinMonth
        else -> Presence.Unknown
    }
}

internal fun nowSeconds(): Long = System.currentTimeMillis() / 1000

internal fun previewText(message: JSONObject?): String {
    message ?: return ""
    val content = message.optJSONObject("content") ?: return ""
    return when (content.optString("@type")) {
        "messageText" -> content.optJSONObject("text")?.optString("text").orEmpty()
        "messagePhoto" -> "🖼 Photo"
        "messageVideo" -> "🎬 Video"
        "messageAnimation" -> "GIF"
        "messageVideoNote" -> "Video message"
        "messageDocument" -> "📎 ${content.optJSONObject("document")?.optString("file_name") ?: "File"}"
        "messageVoiceNote" -> "🎤 Voice"
        "messageSticker" -> content.optJSONObject("sticker")?.optString("emoji")
            ?.takeIf { it.isNotBlank() }?.let { "$it Sticker" } ?: "Sticker"
        "messageAudio" -> "🎵 " + (content.optJSONObject("audio")?.let { audio ->
            audio.optString("title").ifBlank { audio.optString("file_name") }
        }?.takeIf { it.isNotBlank() } ?: "Audio")
        "messagePoll" -> "📊 " + (content.optJSONObject("poll")?.textOf("question")
            ?.takeIf { it.isNotBlank() } ?: "Poll")
        else -> content.optString("@type").removePrefix("message")
    }
}

/**
 * Telegram's own card for a link in a message.
 *
 * Only on a text message: a photo with a link in its caption gets no
 * `web_page` from the server, and inventing one here would mean fetching
 * the page from the phone.
 *
 * The image is deliberately left alone. `photo` arrives as a set of sizes
 * whose files are not downloaded yet, and a card that waits for bytes
 * before drawing is worse than one that shows the words immediately —
 * the text is the part that says whether the link is worth opening.
 */
internal fun linkPreview(content: JSONObject?): LinkPreview? {
    // `link_preview` since TDLib renamed webPage; `web_page` kept for an
    // older library.
    val page = content?.optJSONObject("link_preview")
        ?: content?.optJSONObject("web_page")
        ?: return null
    val preview = LinkPreview(
        url = page.optString("url"),
        siteName = page.optString("site_name"),
        title = page.optString("title"),
        description = page.optJSONObject("description")?.optString("text").orEmpty()
    )
    // A card holding nothing but the URL says less than the link already
    // in the message text.
    return preview.takeIf { it.hasContent }
}

/**
 * Fills in the text and author of quoted messages from the same window.
 *
 * TDLib puts only an id in reply_to, so the quote has to be looked up.
 * Anything referring outside the loaded window stays unresolved and the
 * bubble shows a neutral placeholder — fetching each one separately would
 * mean a request per reply on every chat open.
 */
internal fun resolveReplies(messages: List<ChatMessage>): List<ChatMessage> {
    if (messages.none { it.replyToId != null }) return messages
    val byId = messages.associateBy { it.id }
    return messages.map { message ->
        val target = message.replyToId?.let { byId[it] } ?: return@map message
        message.copy(
            // A quote the sender chose wins over the original's text.
            replyToText = message.replyToText ?: target.text,
            replyToSender = target.senderName
        )
    }
}

/**
 * The words a message's content shows: its text, or a media caption with
 * a fallback where the caption is empty. Null for content with no words
 * of its own — which is how an edit that did not touch them is told
 * apart from one that did.
 */
internal fun contentText(content: JSONObject?): String? = when (content?.optString("@type")) {
    "messageText" -> content.optJSONObject("text")?.optString("text").orEmpty()
    "messagePhoto" -> content.optJSONObject("caption")?.optString("text").orEmpty()
        .ifBlank { "Photo" }
    "messageDocument" -> content.optJSONObject("caption")?.optString("text").orEmpty()
        .ifBlank { content.optJSONObject("document")?.optString("file_name").orEmpty() }
    "messageVideo", "messageVoiceNote", "messageAudio", "messageAnimation" ->
        content.optJSONObject("caption")?.optString("text")
    else -> null
}

/** The formatted text a content carries: a text's own, or a caption. */
internal fun formattedOf(content: JSONObject?): JSONObject? = when (content?.optString("@type")) {
    "messageText" -> content.optJSONObject("text")
    null -> null
    else -> content.optJSONObject("caption")
}

/**
 * A `formattedText`'s entities, for [text] as the bubble shows it. When
 * the bubble shows something other than the formatted text itself — a
 * caption's fallback, a file name — the entities belong to other words
 * and none are kept.
 */
internal fun entitiesOf(formatted: JSONObject?, text: String): List<TextEntity> {
    if (formatted == null || formatted.optString("text") != text) return emptyList()
    val array = formatted.optJSONArray("entities") ?: return emptyList()
    val entities = (0 until array.length()).mapNotNull { index ->
        val entity = array.optJSONObject(index) ?: return@mapNotNull null
        val type = entity.optJSONObject("type") ?: return@mapNotNull null
        val kind = when (type.optString("@type")) {
            "textEntityTypeBold" -> EntityType.Bold
            "textEntityTypeItalic" -> EntityType.Italic
            "textEntityTypeUnderline" -> EntityType.Underline
            "textEntityTypeStrikethrough" -> EntityType.Strikethrough
            "textEntityTypeSpoiler" -> EntityType.Spoiler
            "textEntityTypeCode" -> EntityType.Code
            "textEntityTypePre", "textEntityTypePreCode" -> EntityType.Pre
            "textEntityTypeBlockQuote", "textEntityTypeExpandableBlockQuote" -> EntityType.BlockQuote
            "textEntityTypeUrl" -> EntityType.Url
            "textEntityTypeTextUrl" -> EntityType.TextUrl(type.optString("url"))
            "textEntityTypeEmailAddress" -> EntityType.Email
            "textEntityTypePhoneNumber" -> EntityType.Phone
            "textEntityTypeMention" -> EntityType.Mention
            "textEntityTypeMentionName" -> EntityType.MentionName(type.optLong("user_id"))
            "textEntityTypeHashtag" -> EntityType.Hashtag
            "textEntityTypeCashtag" -> EntityType.Cashtag
            "textEntityTypeBotCommand" -> EntityType.BotCommand
            else -> return@mapNotNull null
        }
        TextEntity(entity.optInt("offset"), entity.optInt("length"), kind)
    }
    return clampEntities(text, entities)
}

/**
 * A `formattedText`'s words, or a plain string where an older TDLib
 * still sends one — a poll's question and options changed from one to
 * the other.
 */
internal fun JSONObject.textOf(key: String): String = when (val value = opt(key)) {
    is JSONObject -> value.optString("text")
    is String -> value
    else -> ""
}

internal fun pollOf(poll: JSONObject?): PollContent? {
    if (poll == null) return null
    val options = poll.optJSONArray("options") ?: JSONArray()
    val type = poll.optJSONObject("type")
    val isQuiz = type?.optString("@type") == "pollTypeQuiz"
    // Newer TDLib lists every right answer; an older one had exactly one.
    val correct = type?.optJSONArray("correct_option_ids")
        ?.let { ids -> (0 until ids.length()).map { ids.optInt(it) }.toSet() }
        ?: type?.optInt("correct_option_id", -1)?.takeIf { it >= 0 }?.let { setOf(it) }
        ?: emptySet()
    return PollContent(
        id = poll.optInt64("id"),
        question = poll.textOf("question"),
        options = (0 until options.length()).mapNotNull { index ->
            val option = options.optJSONObject(index) ?: return@mapNotNull null
            PollOption(
                text = option.textOf("text"),
                voterCount = option.optInt("voter_count"),
                percentage = option.optInt("vote_percentage"),
                isChosen = option.optBoolean("is_chosen")
            )
        },
        totalVoters = poll.optInt("total_voter_count"),
        isAnonymous = poll.optBoolean("is_anonymous", true),
        allowsMultiple = poll.optBoolean("allows_multiple_answers") ||
            type?.optBoolean("allow_multiple_answers") == true,
        allowsRevoting = poll.optBoolean("allows_revoting", true),
        isQuiz = isQuiz,
        correctOptions = correct,
        explanation = type?.optJSONObject("explanation")?.optString("text").orEmpty(),
        isClosed = poll.optBoolean("is_closed")
    )
}

/** A bot's inline buttons; empty for anything that is not a set of them. */
internal fun inlineKeyboardOf(markup: JSONObject?): List<List<InlineButton>> {
    if (markup?.optString("@type") != "replyMarkupInlineKeyboard") return emptyList()
    return rowsOf(markup) { button ->
        val type = button.optJSONObject("type")
        val action = when (type?.optString("@type")) {
            "inlineKeyboardButtonTypeUrl" -> ButtonAction.OpenUrl(type.optString("url"))
            // Opened as the link it is: logging in through it and Mini
            // Apps both need a web view this client does not have.
            "inlineKeyboardButtonTypeLoginUrl", "inlineKeyboardButtonTypeWebApp" ->
                ButtonAction.OpenUrl(type.optString("url"))
            "inlineKeyboardButtonTypeCallback" -> ButtonAction.Callback(type.optString("data"))
            "inlineKeyboardButtonTypeCopyText" -> ButtonAction.CopyText(type.optString("text"))
            else -> ButtonAction.Unsupported
        }
        InlineButton(button.optString("text"), action)
    }
}

/** A bot's keyboard under the composer; null for any other markup. */
internal fun replyKeyboardOf(markup: JSONObject?): ReplyKeyboard? {
    if (markup?.optString("@type") != "replyMarkupShowKeyboard") return null
    return ReplyKeyboard(
        rows = rowsOf(markup) { button ->
            ReplyKey(
                text = button.optString("text"),
                sendsText = button.optJSONObject("type")?.optString("@type")
                    .let { it == null || it == "keyboardButtonTypeText" }
            )
        },
        placeholder = markup.optString("input_field_placeholder"),
        oneTime = markup.optBoolean("one_time")
    )
}

internal fun <T> rowsOf(markup: JSONObject, button: (JSONObject) -> T): List<List<T>> {
    val rows = markup.optJSONArray("rows") ?: return emptyList()
    return (0 until rows.length()).mapNotNull { r ->
        val row = rows.optJSONArray(r) ?: return@mapNotNull null
        (0 until row.length()).mapNotNull { index -> row.optJSONObject(index)?.let { button(it) } }
            .takeIf { it.isNotEmpty() }
    }
}

/**
 * Reads `messageVideo` into the model, or answers null for anything else.
 *
 * Two files, and they arrive on different schedules. The poster is a
 * thumbnail a few kilobytes wide and is fetched as soon as the bubble is
 * on screen; the video behind it is not fetched until somebody asks for
 * it, because a chat scrolled past should not pull down a hundred
 * megabytes of things nobody watched.
 *
 * The duration and the dimensions come with the message itself, so the
 * bubble knows its shape and its length before either file exists —
 * which is what lets it reserve the space rather than jump when the
 * poster lands.
 */
/**
 * A video, a GIF or a round video message: the same four files-and-sizes
 * under three different names. A GIF keeps its file under `animation`,
 * and a video message is square, with one `length` for both sides.
 */
internal fun videoContent(content: JSONObject?): VideoContent? {
    val (video, fileKey) = when (content?.optString("@type")) {
        "messageVideo" -> content.optJSONObject("video") to "video"
        "messageAnimation" -> content.optJSONObject("animation") to "animation"
        "messageVideoNote" -> content.optJSONObject("video_note") to "video"
        else -> null to ""
    }
    if (video == null) return null
    val length = video.optInt("length")
    val width = video.optInt("width").takeIf { it > 0 } ?: length
    val height = video.optInt("height").takeIf { it > 0 } ?: length
    val thumbnail = video.optJSONObject("thumbnail")?.optJSONObject("file")
    val file = video.optJSONObject(fileKey)
    return VideoContent(
        durationSeconds = video.optInt("duration"),
        aspect = if (width > 0 && height > 0) width.toFloat() / height else 16f / 9f,
        thumbFileId = thumbnail?.optInt("id")?.takeIf { it != 0 },
        thumbPath = thumbnail?.localPathIfDownloaded(),
        fileId = file?.optInt("id")?.takeIf { it != 0 },
        path = file?.localPathIfDownloaded()
    )
}

/**
 * The path of a TDLib `file`, but only once all of it is here.
 *
 * A partially downloaded file has a path too, and it points at bytes that
 * are still arriving — handing that to a player is how you get a video
 * that plays for two seconds and stops.
 */
internal fun JSONObject.localPathIfDownloaded(): String? = optJSONObject("local")
    ?.takeIf { it.optBoolean("is_downloading_completed") }
    ?.optString("path")
    ?.takeIf { it.isNotBlank() }

/**
 * Reactions hang off interaction_info, alongside view and forward counts,
 * and are absent on the overwhelming majority of messages.
 *
 * Only emoji reactions are read. A custom reaction is a sticker id that
 * means nothing without fetching the sticker, and a chip showing a
 * numeric id would be worse than showing nothing.
 */
internal fun parseReactions(interactionInfo: JSONObject?): List<MessageReaction> {
    val array = interactionInfo
        ?.optJSONObject("reactions")
        ?.optJSONArray("reactions")
        ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        val reaction = array.optJSONObject(index) ?: return@mapNotNull null
        val emoji = reaction.optJSONObject("type")
            ?.takeIf { it.optString("@type") == "reactionTypeEmoji" }
            ?.optString("emoji")
            ?.takeIf { it.isNotBlank() }
            ?: return@mapNotNull null
        MessageReaction(
            emoji = emoji,
            count = reaction.optInt("total_count"),
            isChosen = reaction.optBoolean("is_chosen")
        )
    }
}

/**
 * The biggest size Telegram offers for a photo.
 *
 * A messagePhoto carries several, smallest first — thumbnails through to
 * the original. The last is the one worth showing: anything smaller is
 * visibly soft at the width a bubble draws it, and TDLib downsamples on
 * request anyway.
 */
internal fun largestPhotoSize(content: JSONObject?): JSONObject? {
    val sizes = content?.optJSONObject("photo")?.optJSONArray("sizes") ?: return null
    return sizes.optJSONObject(sizes.length() - 1)
}

internal fun formatTime(epochSec: Int): String {
    if (epochSec <= 0) return ""
    val date = Date(epochSec * 1000L)
    val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    return fmt.format(date)
}
