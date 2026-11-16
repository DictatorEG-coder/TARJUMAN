package app.tarjuman.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View
import android.widget.ScrollView

/** The floating bubble: a burgundy circle with the letter ت. */
class BubbleView(ctx: Context, private val sizePx: Int) : View(ctx) {
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF9B1B30.toInt() }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        alpha = 200
        strokeWidth = 3f * ctx.resources.displayMetrics.density
    }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(sizePx, sizePx)
    }

    override fun onDraw(c: Canvas) {
        val r = width / 2f
        c.drawCircle(r, r, r - ring.strokeWidth, bg)
        c.drawCircle(r, r, r - ring.strokeWidth, ring)
        txt.textSize = width * 0.5f
        val y = r - (txt.descent() + txt.ascent()) / 2f
        c.drawText("ت", r, y, txt)
    }
}

/** Full-screen layer where the user drags a rectangle. A tap without dragging cancels. */
class SelectionView(
    ctx: Context,
    private val hint: String,
    private val onDone: (Rect?) -> Unit
) : View(ctx) {
    private val dim = Paint().apply { color = 0x66000000 }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFFFFFFFF.toInt()
        strokeWidth = 3f * ctx.resources.displayMetrics.density
    }
    private val fill = Paint().apply { color = 0x339B1B30 }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 16f * ctx.resources.displayMetrics.scaledDensity
    }
    private var sx = 0f
    private var sy = 0f
    private var ex = 0f
    private var ey = 0f
    private var active = false

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> { sx = e.x; sy = e.y; ex = e.x; ey = e.y; active = true; invalidate() }
            MotionEvent.ACTION_MOVE -> { ex = e.x; ey = e.y; invalidate() }
            MotionEvent.ACTION_UP -> {
                ex = e.x; ey = e.y; active = false
                val r = Rect(minOf(sx, ex).toInt(), minOf(sy, ey).toInt(), maxOf(sx, ex).toInt(), maxOf(sy, ey).toInt())
                val min = (24 * resources.displayMetrics.density).toInt()
                if (r.width() < min || r.height() < min) {
                    onDone(null)
                } else {
                    val loc = IntArray(2)
                    getLocationOnScreen(loc)
                    r.offset(loc[0], loc[1])
                    onDone(r)
                }
            }
            MotionEvent.ACTION_CANCEL -> { active = false; onDone(null) }
        }
        return true
    }

    override fun onDraw(c: Canvas) {
        c.drawPaint(dim)
        if (active) {
            val r = RectF(minOf(sx, ex), minOf(sy, ey), maxOf(sx, ex), maxOf(sy, ey))
            c.drawRect(r, fill)
            c.drawRect(r, border)
        }
        c.drawText(hint, width / 2f, height * 0.12f, hintPaint)
    }
}

/** Draws each translated block over its original position. Not touchable (the app below stays usable). */
class TranslationOverlayView(ctx: Context) : View(ctx) {
    var items: List<Pair<Rect, String>> = emptyList()
        set(v) { field = v; invalidate() }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF20B1530.toInt() }
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    private fun layoutFor(t: String, w: Int, size: Float): StaticLayout {
        tp.textSize = size
        return StaticLayout.Builder.obtain(t, 0, t.length, tp, w)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .build()
    }

    override fun onDraw(c: Canvas) {
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        c.translate(-loc[0].toFloat(), -loc[1].toFloat())
        val d = resources.displayMetrics.density
        val pad = 4f * d
        for ((r, t) in items) {
            val w = (r.width() - 2 * pad).toInt().coerceAtLeast(20)
            var size = minOf(r.height().toFloat(), 40f * d)
            var l = layoutFor(t, w, size)
            while (l.height > r.height() - pad && size > 9f * d) {
                size *= 0.9f
                l = layoutFor(t, w, size)
            }
            c.drawRoundRect(RectF(r), 8f * d, 8f * d, fill)
            c.save()
            c.translate(r.left + pad, r.top + maxOf(0f, (r.height() - l.height) / 2f))
            l.draw(c)
            c.restore()
        }
    }
}

/** ScrollView that never grows taller than [maxH]. */
class MaxHeightScroll(ctx: Context, private val maxH: Int) : ScrollView(ctx) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST))
    }
}
