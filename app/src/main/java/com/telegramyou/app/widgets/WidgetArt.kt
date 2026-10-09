package com.telegramyou.app.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import androidx.graphics.shapes.toPath
import com.telegramyou.app.notifications.avatarInitials
import com.telegramyou.app.ui.components.materialPolygon
import com.telegramyou.app.ui.theme.avatarColor

/**
 * The pictures the widgets show, drawn here (2.2).
 *
 * A widget is RemoteViews: it can round a box's corners and nothing more, so
 * a person in a clover or a cover in a cookie has to arrive as a bitmap that
 * is already that shape. The shapes are the app's own avatar set —
 * Material's shape library, see AvatarCluster — so someone is the same
 * shape on the home screen as in the chat list.
 */
internal object WidgetArt {

    /**
     * [photoPath] cut to shape [shapeIndex] of the avatar set, [sizePx]
     * square; without a photo, [title]'s initials on [colorSeed]'s colour,
     * as every avatar here falls back.
     *
     * [upright] turns the shape a quarter so a triangle or a star points up
     * rather than to the side: the chat list keeps the set as it is, a photo
     * on a home screen reads better standing.
     */
    fun shaped(
        photoPath: String?,
        title: String,
        colorSeed: Long,
        shapeIndex: Int,
        sizePx: Int,
        upright: Boolean = false
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val outline = outline(shapeIndex, sizePx.toFloat(), upright)
        val photo = photoPath?.let { decode(it, sizePx) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (photo != null) {
            val scale = sizePx.toFloat() / minOf(photo.width, photo.height)
            paint.shader = BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(Matrix().apply {
                    setScale(scale, scale)
                    postTranslate((sizePx - photo.width * scale) / 2f, (sizePx - photo.height * scale) / 2f)
                })
            }
            canvas.drawPath(outline, paint)
        } else {
            paint.color = avatarColor(colorSeed).toArgb()
            canvas.drawPath(outline, paint)
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = sizePx * 0.36f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val centre = sizePx / 2f
            canvas.drawText(avatarInitials(title), centre, centre - (text.descent() + text.ascent()) / 2f, text)
        }
        return bitmap
    }

    /**
     * Shape [shapeIndex] in white, [sizeDp] square, for a widget to tint with
     * a colour of its theme: the bitmap carries the outline, the theme the
     * colour, so it follows the wallpaper as everything else on it does.
     */
    fun tile(context: Context, shapeIndex: Int, sizeDp: Int, upright: Boolean = false): Bitmap {
        val size = px(context, sizeDp)
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
            Canvas(bitmap).drawPath(
                outline(shapeIndex, size.toFloat(), upright),
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            )
        }
    }

    /** Shape [index] of the set as a path filling a [size] square, centred. */
    fun outline(index: Int, size: Float, upright: Boolean = false): Path {
        val path = materialPolygon(index).toPath()
        if (upright) path.transform(Matrix().apply { setRotate(-90f, 0.5f, 0.5f) })
        val bounds = RectF().also { path.computeBounds(it, true) }
        val scale = size / maxOf(bounds.width(), bounds.height())
        path.transform(Matrix().apply {
            setTranslate(-bounds.centerX(), -bounds.centerY())
            postScale(scale, scale)
            postTranslate(size / 2f, size / 2f)
        })
        return path
    }

    /** A photo decoded no larger than it will be drawn, which keeps a widget under its bitmap budget. */
    fun decode(path: String, sizePx: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= sizePx) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    /** [dp] in pixels on this screen. */
    fun px(context: Context, dp: Int): Int = (dp * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
}
