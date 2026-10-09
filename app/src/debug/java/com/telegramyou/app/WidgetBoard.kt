package com.telegramyou.app

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.widget.LinearLayout
import android.widget.ScrollView

/**
 * A stand-in home screen, in debug builds only (2.2): it shows the widgets
 * whose ids it is handed, as a launcher would, so the smoke test can take a
 * picture of them with the demo's chats in them. A launcher cannot be driven
 * reliably from a test — its layout, its dialogs and its version are not
 * this project's — and a widget drawn off-screen loses its rounded corners,
 * which is most of what there is to judge.
 *
 * The test binds the widgets under [EXTRA_HOST]; a host's widgets belong to
 * the app and the host id together, so this activity can show them. Each is
 * told its size, as a launcher tells it, which is what picks a responsive
 * widget's layout.
 */
class WidgetBoard : Activity() {

    private lateinit var host: AppWidgetHost

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        host = AppWidgetHost(this, intent.getIntExtra(EXTRA_HOST, 0))
        val manager = AppWidgetManager.getInstance(this)
        val ids = intent.getIntArrayExtra(EXTRA_IDS) ?: IntArray(0)
        val heights = intent.getIntArrayExtra(EXTRA_HEIGHTS) ?: IntArray(ids.size) { 160 }
        val widths = intent.getIntArrayExtra(EXTRA_WIDTHS) ?: IntArray(ids.size) { 0 }
        val density = resources.displayMetrics.density
        val screenWidthDp = (resources.displayMetrics.widthPixels / density).toInt()
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val gutter = (16 * density).toInt()
            setPadding(gutter, (48 * density).toInt(), gutter, gutter)
            contentDescription = "Widget board"
        }
        ids.forEachIndexed { index, id ->
            val info = manager.getAppWidgetInfo(id) ?: return@forEachIndexed
            val widthDp = widths[index].takeIf { it > 0 } ?: (screenWidthDp - 32)
            val heightDp = heights[index]
            val view = host.createView(this, id, info)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                view.updateAppWidgetSize(Bundle(), listOf(SizeF(widthDp.toFloat(), heightDp.toFloat())))
            } else {
                @Suppress("DEPRECATION")
                view.updateAppWidgetSize(Bundle(), widthDp, heightDp, widthDp, heightDp)
            }
            column.addView(view, LinearLayout.LayoutParams((widthDp * density).toInt(), (heightDp * density).toInt()).apply {
                bottomMargin = (16 * density).toInt()
            })
        }
        // A wallpaper's worth of colour behind them, as a home screen has.
        val wallpaper = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFF26384A.toInt(), 0xFF141A22.toInt(), 0xFF2B2236.toInt())
        )
        setContentView(ScrollView(this).apply {
            background = wallpaper
            addView(column)
        })
    }

    override fun onStart() {
        super.onStart()
        host.startListening()
    }

    override fun onStop() {
        host.stopListening()
        super.onStop()
    }

    companion object {
        const val EXTRA_HOST = "host"
        const val EXTRA_IDS = "ids"
        const val EXTRA_HEIGHTS = "heights"
        const val EXTRA_WIDTHS = "widths"
    }
}
