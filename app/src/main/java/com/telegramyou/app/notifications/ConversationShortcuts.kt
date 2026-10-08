package com.telegramyou.app.notifications

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.util.Log
import androidx.core.app.Person
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.compose.ui.graphics.toArgb
import com.telegramyou.app.MainActivity
import com.telegramyou.app.telegram.TelegramForegroundService
import com.telegramyou.app.telegram.model.ChatPreview
import com.telegramyou.app.ui.theme.avatarColor

/**
 * Each chat as Android means a conversation (2.1): a long-lived sharing
 * shortcut with the chat's name and picture, which is what puts its
 * notifications in the shade's Conversations section, lets it be made a
 * priority conversation, offers it in the system's share sheet as a Direct
 * Share target, and gives it a bubble.
 *
 * Pushed when a chat is opened and when a message from it is notified —
 * the two moments it is in use — and reported as used, so the system ranks
 * the ones talked in most. Android keeps a handful and drops the least used
 * by itself. All of them go when the account signs out: they carry its
 * chats' names and pictures.
 */
object ConversationShortcuts {

    /** What a chat's shortcut is filed under, matched by res/xml/shortcuts.xml's share-target. */
    const val SHARE_CATEGORY = "com.telegramyou.app.category.SHARE_TARGET"

    /**
     * Pushes or refreshes [chat]'s shortcut and returns its id. Off the main
     * thread: the picture is read from disk. Never throws — a shortcut the
     * system refuses is a chat without a conversation, not a crash.
     */
    fun publish(context: Context, chat: ChatPreview): String {
        val id = conversationId(chat.id)
        try {
            val icon = icon(context, chat)
            val title = chat.title.ifBlank { "Chat" }
            val shortcut = ShortcutInfoCompat.Builder(context, id)
                .setShortLabel(title)
                .setLongLived(true)
                .setLocusId(LocusIdCompat(id))
                .setIcon(icon)
                .setCategories(setOf(SHARE_CATEGORY))
                .setIntent(openIntent(context, chat.id))
                .apply {
                    // A person for a person's chat: what the system shows on
                    // the conversation and ranks people by. A group is a
                    // conversation with no one person behind it.
                    if (!chat.isGroup && !chat.isChannel) {
                        setPerson(Person.Builder().setName(title).setKey(id).setIcon(icon).build())
                    }
                }
                .build()
            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
        } catch (e: RuntimeException) {
            Log.w(TAG, "publish(${chat.id}): ${e.message}")
        }
        return id
    }

    /** [chat] is being used — opened, or written in — which raises it among share targets. */
    fun used(context: Context, chat: ChatPreview) {
        publish(context, chat)
        runCatching { ShortcutManagerCompat.reportShortcutUsed(context, conversationId(chat.id)) }
    }

    /** Every chat's shortcut gone, on signing out. */
    fun clear(context: Context) {
        runCatching { ShortcutManagerCompat.removeAllDynamicShortcuts(context) }
    }

    /** The launch a shortcut makes: the app, on the chat, as a notification's tap does. */
    fun openIntent(context: Context, chatId: Long): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(TelegramForegroundService.EXTRA_CHAT_ID, chatId)

    /**
     * The chat's picture, round, or its initials on its colour as the app
     * draws an avatar without one — never a blank where a face should be.
     */
    fun icon(context: Context, chat: ChatPreview): IconCompat =
        IconCompat.createWithBitmap(avatar(context, chat))

    /** [icon]'s picture as a bitmap — what a widget draws (2.1). */
    fun avatar(context: Context, chat: ChatPreview, sizeDp: Int = ICON_DP): Bitmap {
        val size = (sizeDp * context.resources.displayMetrics.density).toInt().coerceAtLeast(48)
        val photo = chat.photoPath?.let { path -> runCatching { BitmapFactory.decodeFile(path) }.getOrNull() }
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val radius = size / 2f
        if (photo != null) {
            val scale = size.toFloat() / minOf(photo.width, photo.height)
            val shader = BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(
                    android.graphics.Matrix().apply {
                        setScale(scale, scale)
                        postTranslate((size - photo.width * scale) / 2f, (size - photo.height * scale) / 2f)
                    }
                )
            }
            canvas.drawCircle(radius, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader })
        } else {
            canvas.drawCircle(
                radius, radius, radius,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = avatarColor(chat.avatarColor).toArgb() }
            )
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = size * 0.4f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val baseline = radius - (text.descent() + text.ascent()) / 2f
            canvas.drawText(avatarInitials(chat.title), radius, baseline, text)
        }
        return bitmap
    }

    private const val ICON_DP = 96
    private const val TAG = "ConversationShortcuts"
}
