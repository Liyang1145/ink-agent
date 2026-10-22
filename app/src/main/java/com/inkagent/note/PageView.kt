package com.inkagent.note

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Shows one A4 page (page units → screen via scale + offset). Stylus writes; fingers pan and
 * pinch-zoom unless finger writing is on. The AI layer is drawn under the ink and can be hidden.
 */
class PageView(context: Context, val notebook: Notebook) : View(context) {

    enum class Tool { PEN, ERASER, LASSO }

    var tool = Tool.PEN
        set(v) { field = v; lassoPath.reset(); invalidate() }
    var fingerCanDraw = false
    var penColor = Color.BLACK
    var penWidth = 3f
    var showAi = true
        set(v) { field = v; invalidate() }

    var pageIndex = 0
        private set
    val page: Page get() = notebook.pages[pageIndex]

    var onLassoSelected: ((Bitmap) -> Unit)? = null
    var onPageChanged: (() -> Unit)? = null
    var onContentChanged: (() -> Unit)? = null

    // view transform: screen = page * scale + offset
    private var zoom = 1f
    private var offX = 0f
    private var offY = 0f
    private val baseScale: Float get() = if (width == 0) 1f else (width - 32f) / A4_W
    private val scale: Float get() = baseScale * zoom

    private class Snapshot(val strokes: List<Stroke>, val ai: List<AiObject>, val w: Float, val h: Float)
    private val undoStacks = HashMap<Page, ArrayList<Snapshot>>()
    private var current: Stroke? = null

    private val lassoPath = Path()
    private val lassoPts = ArrayList<PointF>()

    private val pagePaint = Paint().apply { color = Color.WHITE }
    private val shadowPaint = Paint().apply { color = Color.rgb(210, 213, 220) }
    private val linePaint = Paint().apply { color = Color.rgb(236, 239, 244); strokeWidth = 1f }
    private val lassoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.rgb(30, 120, 255); strokeWidth = 2.5f
        pathEffect = DashPathEffect(floatArrayOf(14f, 10f), 0f)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.GRAY; textSize = 28f }

    init { setBackgroundColor(Color.rgb(228, 231, 236)) }

    // ---------------- page navigation ----------------
    fun goTo(i: Int) {
        pageIndex = i.coerceIn(0, notebook.pages.size - 1)
        zoom = 1f; offX = 16f; offY = 16f; current = null
        invalidate(); onPageChanged?.invoke()
    }
    fun addPage() { notebook.pages.add(pageIndex + 1, Page()); changed(); goTo(pageIndex + 1) }
    fun extend(down: Boolean) {
        snapshot()
        if (down) page.height += A4_H / 2 else page.width += A4_W / 2
        changed(); invalidate()
    }

    // ---------------- undo ----------------
    fun snapshot(p: Page = page) {
        val st = undoStacks.getOrPut(p) { ArrayList() }
        st.add(Snapshot(ArrayList(p.strokes), ArrayList(p.ai), p.width, p.height))
        if (st.size > 100) st.removeAt(0)
    }
    fun undo() {
        val st = undoStacks[page] ?: return
        if (st.isEmpty()) return
        val s = st.removeAt(st.size - 1)
        page.strokes.clear(); page.strokes.addAll(s.strokes)
        page.ai.clear(); page.ai.addAll(s.ai)
        page.width = s.w; page.height = s.h
        changed(); invalidate()
    }
    fun clearInk() { snapshot(); page.strokes.clear(); changed(); invalidate() }
    fun clearAi() { snapshot(); page.ai.clear(); changed(); invalidate() }
    fun undoLastAi() { if (page.ai.isEmpty()) return; snapshot(); page.ai.removeAt(page.ai.size - 1); changed(); invalidate() }

    private fun changed() { onContentChanged?.invoke() }

    // ---------------- input ----------------
    private fun toPageX(x: Float) = (x - offX) / scale
    private fun toPageY(y: Float) = (y - offY) / scale

    private var lastX = 0f; private var lastY = 0f
    private var lastDist = 0f
    private var gesture = false

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        if (ow == 0) { offX = 16f; offY = 16f }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val type = e.getToolType(0)
        val stylus = type == MotionEvent.TOOL_TYPE_STYLUS || type == MotionEvent.TOOL_TYPE_ERASER
        if (!stylus && (!fingerCanDraw || e.pointerCount > 1 || gesture)) return handleGesture(e)
        val t = if (type == MotionEvent.TOOL_TYPE_ERASER || (e.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0) Tool.ERASER else tool
        when (t) {
            Tool.PEN -> pen(e)
            Tool.ERASER -> {
                if (e.actionMasked == MotionEvent.ACTION_DOWN) snapshot()
                for (h in 0 until e.historySize) eraseAt(toPageX(e.getHistoricalX(h)), toPageY(e.getHistoricalY(h)))
                eraseAt(toPageX(e.x), toPageY(e.y))
                if (e.actionMasked == MotionEvent.ACTION_UP) changed()
            }
            Tool.LASSO -> lasso(e)
        }
        return true
    }

    private fun handleGesture(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { lastX = e.x; lastY = e.y; gesture = true }
            MotionEvent.ACTION_POINTER_DOWN -> if (e.pointerCount == 2) {
                lastDist = hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1))
                lastX = (e.getX(0) + e.getX(1)) / 2; lastY = (e.getY(0) + e.getY(1)) / 2
            }
            MotionEvent.ACTION_MOVE -> {
                if (e.pointerCount >= 2) {
                    val cx = (e.getX(0) + e.getX(1)) / 2; val cy = (e.getY(0) + e.getY(1)) / 2
                    val d = hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1))
                    if (lastDist > 0) {
                        val f = d / lastDist
                        val nz = (zoom * f).coerceIn(0.4f, 5f)
                        val real = nz / zoom
                        offX = cx - (cx - offX) * real; offY = cy - (cy - offY) * real
                        zoom = nz
                    }
                    offX += cx - lastX; offY += cy - lastY
                    lastX = cx; lastY = cy; lastDist = d
                } else {
                    offX += e.x - lastX; offY += e.y - lastY; lastX = e.x; lastY = e.y
                }
                clampOffsets(); invalidate()
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val keep = if (e.actionIndex == 0) 1 else 0
                lastX = e.getX(keep); lastY = e.getY(keep); lastDist = 0f
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { gesture = false; lastDist = 0f }
        }
        return true
    }

    private fun clampOffsets() {
        val pw = page.width * scale; val ph = page.height * scale
        val loX = min(16f, width - pw - 16f); val hiX = max(16f, width - pw - 16f)
        val loY = min(16f, height - ph - 16f); val hiY = max(16f, height - ph - 16f)
        offX = offX.coerceIn(loX, hiX)
        offY = offY.coerceIn(loY, hiY)
    }

    private fun pen(e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                snapshot()
                current = Stroke(penColor, penWidth).also { it.add(toPageX(e.x), toPageY(e.y), e.pressure); page.strokes.add(it) }
            }
            MotionEvent.ACTION_MOVE -> current?.let { s ->
                for (h in 0 until e.historySize) s.add(toPageX(e.getHistoricalX(h)), toPageY(e.getHistoricalY(h)), e.getHistoricalPressure(h))
                s.add(toPageX(e.x), toPageY(e.y), e.pressure)
            }
            MotionEvent.ACTION_UP -> { current?.add(toPageX(e.x), toPageY(e.y), e.pressure); current = null; changed() }
            MotionEvent.ACTION_CANCEL -> current = null
        }
        invalidate()
    }

    private fun eraseAt(x: Float, y: Float) {
        val r = 14f
        val it = page.strokes.iterator()
        var hit = false
        while (it.hasNext()) {
            val s = it.next()
            if (x < s.bounds.left - r || x > s.bounds.right + r || y < s.bounds.top - r || y > s.bounds.bottom + r) continue
            for (i in s.xs.indices) if (hypot(s.xs[i] - x, s.ys[i] - y) < r) { it.remove(); hit = true; break }
        }
        if (hit) invalidate()
    }

    private fun lasso(e: MotionEvent) {
        val x = toPageX(e.x); val y = toPageY(e.y)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { lassoPts.clear(); lassoPts.add(PointF(x, y)); lassoPath.reset(); lassoPath.moveTo(x, y) }
            MotionEvent.ACTION_MOVE -> {
                for (h in 0 until e.historySize) {
                    val hx = toPageX(e.getHistoricalX(h)); val hy = toPageY(e.getHistoricalY(h))
                    lassoPts.add(PointF(hx, hy)); lassoPath.lineTo(hx, hy)
                }
                lassoPts.add(PointF(x, y)); lassoPath.lineTo(x, y)
            }
            MotionEvent.ACTION_UP -> {
                lassoPath.close()
                if (lassoPts.size > 5) cropSelection()?.let { onLassoSelected?.invoke(it) }
                postDelayed({ lassoPath.reset(); invalidate() }, 500)
            }
        }
        invalidate()
    }

    private fun inside(px: Float, py: Float): Boolean {
        var inside = false
        var j = lassoPts.size - 1
        for (i in lassoPts.indices) {
            val a = lassoPts[i]; val b = lassoPts[j]
            if ((a.y > py) != (b.y > py) && px < (b.x - a.x) * (py - a.y) / (b.y - a.y + 1e-6f) + a.x) inside = !inside
            j = i
        }
        return inside
    }

    private fun cropSelection(): Bitmap? {
        val strokes = page.strokes.filter { s ->
            var n = 0; for (i in s.xs.indices) if (inside(s.xs[i], s.ys[i])) n++
            s.xs.isNotEmpty() && n * 2 >= s.xs.size
        }
        val ai = if (showAi) page.ai.filter { inside(it.rect().centerX(), it.rect().centerY()) } else emptyList()
        if (strokes.isEmpty() && ai.isEmpty()) return null
        val b = RectF()
        var first = true
        strokes.forEach { if (first) { b.set(it.bounds); first = false } else b.union(it.bounds) }
        ai.forEach { if (first) { b.set(it.rect()); first = false } else b.union(it.rect()) }
        b.inset(-20f, -20f)
        return Notebook.render(page, b, maxSide = 1280, minSide = 512, strokes = strokes, ai = ai)
    }

    // ---------------- drawing ----------------
    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        c.save()
        c.translate(offX, offY)
        c.scale(scale, scale)
        c.drawRect(4f, 4f, page.width + 4f, page.height + 4f, shadowPaint)
        c.drawRect(0f, 0f, page.width, page.height, pagePaint)
        var y = 60f
        while (y < page.height) { c.drawLine(0f, y, page.width, y, linePaint); y += 60f }
        if (showAi) page.ai.forEach { Notebook.drawAi(c, it) }
        page.strokes.forEach { Notebook.drawStroke(c, it) }
        if (!lassoPath.isEmpty) { lassoPaint.strokeWidth = 2.5f / scale; c.drawPath(lassoPath, lassoPaint) }
        c.restore()
        c.drawText("${pageIndex + 1} / ${notebook.pages.size}", width - 110f, height - 20f, labelPaint)
    }
}
