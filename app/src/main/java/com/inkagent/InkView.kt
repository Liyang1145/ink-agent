package com.inkagent

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** One handwritten stroke: points with per-point width. */
class Stroke(val color: Int, val baseWidth: Float) {
    val xs = ArrayList<Float>()
    val ys = ArrayList<Float>()
    val ws = ArrayList<Float>()
    val bounds = RectF(Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)

    fun add(x: Float, y: Float, pressure: Float) {
        val p = if (pressure <= 0f) 0.5f else pressure.coerceIn(0.05f, 1f)
        xs.add(x); ys.add(y); ws.add(baseWidth * (0.45f + p * 1.1f))
        bounds.union(x, y)
    }
}

class InkView(context: Context) : View(context) {

    enum class Tool { PEN, ERASER, LASSO }

    var tool = Tool.PEN
        set(v) { field = v; lassoPath.reset(); invalidate() }
    var fingerCanDraw = false
    var penColor = Color.BLACK
    var penWidth = 4f
    /** Called with the cropped image of the lasso selection. */
    var onLassoSelected: ((Bitmap) -> Unit)? = null

    private val strokes = ArrayList<Stroke>()
    private val undoStack = ArrayList<List<Stroke>>()
    private var current: Stroke? = null

    private val lassoPath = Path()
    private val lassoPts = ArrayList<PointF>()

    // Finger panning
    private var offsetX = 0f
    private var offsetY = 0f
    private var lastPanX = 0f
    private var lastPanY = 0f
    private var panning = false

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val lassoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.rgb(30, 120, 255); strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(14f, 10f), 0f)
    }
    private val gridPaint = Paint().apply { color = Color.rgb(232, 236, 242); strokeWidth = 1f }

    init { setBackgroundColor(Color.WHITE) }

    private fun snapshot() {
        undoStack.add(ArrayList(strokes))
        if (undoStack.size > 100) undoStack.removeAt(0)
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            strokes.clear(); strokes.addAll(undoStack.removeAt(undoStack.size - 1)); invalidate()
        }
    }

    fun clearAll() { snapshot(); strokes.clear(); invalidate() }

    private fun isStylus(e: MotionEvent, i: Int = 0): Boolean {
        val t = e.getToolType(i)
        return t == MotionEvent.TOOL_TYPE_STYLUS || t == MotionEvent.TOOL_TYPE_ERASER
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val stylus = isStylus(e)
        // Finger: pan the page unless finger drawing is enabled
        if (!stylus && !fingerCanDraw) return handlePan(e)
        val effectiveTool = if (e.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER) Tool.ERASER else tool
        val x = e.x - offsetX
        val y = e.y - offsetY
        when (effectiveTool) {
            Tool.PEN -> handlePen(e)
            Tool.ERASER -> {
                if (e.actionMasked == MotionEvent.ACTION_DOWN) snapshot()
                eraseAt(x, y)
                for (h in 0 until e.historySize) eraseAt(e.getHistoricalX(h) - offsetX, e.getHistoricalY(h) - offsetY)
            }
            Tool.LASSO -> handleLasso(e, x, y)
        }
        return true
    }

    private fun handlePan(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { lastPanX = e.x; lastPanY = e.y; panning = true }
            MotionEvent.ACTION_MOVE -> if (panning) {
                offsetX += e.x - lastPanX; offsetY += e.y - lastPanY
                offsetX = min(0f, offsetX); offsetY = min(0f, offsetY)
                lastPanX = e.x; lastPanY = e.y; invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> panning = false
        }
        return true
    }

    private fun handlePen(e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                snapshot()
                current = Stroke(penColor, penWidth).also {
                    it.add(e.x - offsetX, e.y - offsetY, e.pressure); strokes.add(it)
                }
            }
            MotionEvent.ACTION_MOVE -> current?.let { s ->
                for (h in 0 until e.historySize) {
                    s.add(e.getHistoricalX(h) - offsetX, e.getHistoricalY(h) - offsetY, e.getHistoricalPressure(h))
                }
                s.add(e.x - offsetX, e.y - offsetY, e.pressure)
            }
            MotionEvent.ACTION_UP -> { current?.add(e.x - offsetX, e.y - offsetY, e.pressure); current = null }
            MotionEvent.ACTION_CANCEL -> current = null
        }
        invalidate()
    }

    private fun eraseAt(x: Float, y: Float) {
        val r = 18f
        val it = strokes.iterator()
        var changed = false
        while (it.hasNext()) {
            val s = it.next()
            if (x < s.bounds.left - r || x > s.bounds.right + r || y < s.bounds.top - r || y > s.bounds.bottom + r) continue
            for (i in s.xs.indices) {
                if (hypot(s.xs[i] - x, s.ys[i] - y) < r) { it.remove(); changed = true; break }
            }
        }
        if (changed) invalidate()
    }

    private fun handleLasso(e: MotionEvent, x: Float, y: Float) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lassoPts.clear(); lassoPts.add(PointF(x, y))
                lassoPath.reset(); lassoPath.moveTo(x, y)
            }
            MotionEvent.ACTION_MOVE -> {
                for (h in 0 until e.historySize) {
                    val hx = e.getHistoricalX(h) - offsetX; val hy = e.getHistoricalY(h) - offsetY
                    lassoPts.add(PointF(hx, hy)); lassoPath.lineTo(hx, hy)
                }
                lassoPts.add(PointF(x, y)); lassoPath.lineTo(x, y)
            }
            MotionEvent.ACTION_UP -> {
                lassoPath.close()
                if (lassoPts.size > 5) cropSelection()?.let { onLassoSelected?.invoke(it) }
                postDelayed({ lassoPath.reset(); invalidate() }, 600)
            }
        }
        invalidate()
    }

    private fun insidePolygon(px: Float, py: Float): Boolean {
        var inside = false
        var j = lassoPts.size - 1
        for (i in lassoPts.indices) {
            val a = lassoPts[i]; val b = lassoPts[j]
            if ((a.y > py) != (b.y > py) && px < (b.x - a.x) * (py - a.y) / (b.y - a.y + 1e-6f) + a.x) inside = !inside
            j = i
        }
        return inside
    }

    /** Render every stroke that is mostly inside the lasso into a white bitmap. */
    private fun cropSelection(): Bitmap? {
        val selected = strokes.filter { s ->
            var inCount = 0
            for (i in s.xs.indices) if (insidePolygon(s.xs[i], s.ys[i])) inCount++
            s.xs.isNotEmpty() && inCount * 2 >= s.xs.size
        }
        if (selected.isEmpty()) return null
        val b = RectF(selected[0].bounds)
        selected.forEach { b.union(it.bounds) }
        val pad = 24f
        b.inset(-pad, -pad)
        // Scale so the longer side is at most 1280px and at least 512px
        val longSide = max(b.width(), b.height())
        val scale = when { longSide > 1280f -> 1280f / longSide; longSide < 512f -> 512f / longSide; else -> 1f }
        val w = (b.width() * scale).toInt().coerceAtLeast(1)
        val h = (b.height() * scale).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        c.scale(scale, scale)
        c.translate(-b.left, -b.top)
        selected.forEach { drawStroke(c, it) }
        return bmp
    }

    private fun drawStroke(c: Canvas, s: Stroke) {
        strokePaint.color = s.color
        val n = s.xs.size
        if (n == 1) {
            strokePaint.strokeWidth = s.ws[0]; c.drawPoint(s.xs[0], s.ys[0], strokePaint); return
        }
        for (i in 1 until n) {
            strokePaint.strokeWidth = (s.ws[i - 1] + s.ws[i]) / 2f
            c.drawLine(s.xs[i - 1], s.ys[i - 1], s.xs[i], s.ys[i], strokePaint)
        }
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        c.save()
        c.translate(offsetX, offsetY)
        // light grid
        val step = 48f
        val left = -offsetX; val top = -offsetY
        var gy = top - (top % step)
        while (gy < top + height) { c.drawLine(left, gy, left + width, gy, gridPaint); gy += step }
        strokes.forEach { drawStroke(c, it) }
        if (!lassoPath.isEmpty) c.drawPath(lassoPath, lassoPaint)
        c.restore()
    }
}
