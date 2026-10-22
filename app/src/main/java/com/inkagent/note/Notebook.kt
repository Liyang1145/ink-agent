package com.inkagent.note

import android.graphics.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.max

/** Page units: A4 portrait is 1000 x 1414. A page may be extended in either direction. */
const val A4_W = 1000f
const val A4_H = 1414f

class Stroke(val color: Int, val baseWidth: Float) {
    val xs = ArrayList<Float>()
    val ys = ArrayList<Float>()
    val ws = ArrayList<Float>()
    val bounds = RectF(Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)

    fun add(x: Float, y: Float, pressure: Float) {
        val p = if (pressure <= 0f) 0.5f else pressure.coerceIn(0.05f, 1f)
        addRaw(x, y, baseWidth * (0.45f + p * 1.1f))
    }

    fun addRaw(x: Float, y: Float, w: Float) {
        xs.add(x); ys.add(y); ws.add(w); bounds.union(x, y)
    }

    fun toJson(): JSONObject {
        val pts = JSONArray()
        for (i in xs.indices) { pts.put(r(xs[i])); pts.put(r(ys[i])); pts.put(r(ws[i])) }
        return JSONObject().put("c", color).put("b", baseWidth.toDouble()).put("p", pts)
    }

    private fun r(v: Float) = (v * 10).toInt() / 10.0

    companion object {
        fun fromJson(o: JSONObject): Stroke {
            val s = Stroke(o.getInt("c"), o.getDouble("b").toFloat())
            val p = o.getJSONArray("p")
            var i = 0
            while (i + 2 < p.length()) {
                s.addRaw(p.getDouble(i).toFloat(), p.getDouble(i + 1).toFloat(), p.getDouble(i + 2).toFloat()); i += 3
            }
            return s
        }
    }
}

/** An object on the AI layer: rendered LaTeX or SVG, cached as a PNG. */
class AiObject(
    val id: String,
    val kind: String,          // "latex" | "svg"
    val source: String,
    var x: Float, var y: Float, var w: Float, var h: Float,
    val pngPath: String,
) {
    @Transient var bitmap: Bitmap? = null
    fun bitmap(): Bitmap? { if (bitmap == null) bitmap = BitmapFactory.decodeFile(pngPath); return bitmap }
    fun rect() = RectF(x, y, x + w, y + h)

    fun toJson(): JSONObject = JSONObject().put("id", id).put("kind", kind).put("source", source)
        .put("x", x.toDouble()).put("y", y.toDouble()).put("w", w.toDouble()).put("h", h.toDouble()).put("png", pngPath)

    companion object {
        fun fromJson(o: JSONObject) = AiObject(o.getString("id"), o.getString("kind"), o.getString("source"),
            o.getDouble("x").toFloat(), o.getDouble("y").toFloat(), o.getDouble("w").toFloat(), o.getDouble("h").toFloat(),
            o.getString("png"))
    }
}

class Page(var width: Float = A4_W, var height: Float = A4_H) {
    val strokes = ArrayList<Stroke>()
    val ai = ArrayList<AiObject>()

    fun toJson(): JSONObject = JSONObject().put("w", width.toDouble()).put("h", height.toDouble())
        .put("strokes", JSONArray().also { a -> strokes.forEach { a.put(it.toJson()) } })
        .put("ai", JSONArray().also { a -> ai.forEach { a.put(it.toJson()) } })

    /** A short textual inventory for the agent. */
    fun describe(index: Int): String {
        val inkBounds = RectF()
        strokes.forEachIndexed { i, s -> if (i == 0) inkBounds.set(s.bounds) else inkBounds.union(s.bounds) }
        val sb = StringBuilder("第${index + 1}页：尺寸 ${width.toInt()}x${height.toInt()}，笔画 ${strokes.size} 条")
        if (strokes.isNotEmpty()) sb.append("，手写内容范围 x=${inkBounds.left.toInt()}..${inkBounds.right.toInt()} y=${inkBounds.top.toInt()}..${inkBounds.bottom.toInt()}")
        if (ai.isNotEmpty()) {
            sb.append("，AI 图层对象 ${ai.size} 个：")
            ai.forEach { sb.append("\n  - ${it.id} ${it.kind} @(${it.x.toInt()},${it.y.toInt()}) ${it.w.toInt()}x${it.h.toInt()}: ${it.source.take(60).replace("\n", " ")}") }
        }
        return sb.toString()
    }

    companion object {
        fun fromJson(o: JSONObject): Page {
            val p = Page(o.optDouble("w", A4_W.toDouble()).toFloat(), o.optDouble("h", A4_H.toDouble()).toFloat())
            o.optJSONArray("strokes")?.let { a -> for (i in 0 until a.length()) p.strokes.add(Stroke.fromJson(a.getJSONObject(i))) }
            o.optJSONArray("ai")?.let { a -> for (i in 0 until a.length()) p.ai.add(AiObject.fromJson(a.getJSONObject(i))) }
            return p
        }
    }
}

/** A single local notebook of pages, saved as JSON in app storage. */
class Notebook(private val dir: File) {
    val pages = ArrayList<Page>()
    val aiDir = File(dir, "ai").also { it.mkdirs() }
    private val file = File(dir, "notebook.json")

    init {
        dir.mkdirs()
        if (file.exists()) try {
            val a = JSONObject(file.readText()).getJSONArray("pages")
            for (i in 0 until a.length()) pages.add(Page.fromJson(a.getJSONObject(i)))
        } catch (_: Exception) { }
        if (pages.isEmpty()) pages.add(Page())
    }

    @Synchronized
    fun save() {
        val json = JSONObject().put("pages", JSONArray().also { a -> pages.forEach { a.put(it.toJson()) } }).toString()
        val tmp = File(dir, "notebook.json.tmp")
        tmp.writeText(json)
        tmp.renameTo(file)
    }

    companion object {
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }

        fun drawStroke(c: Canvas, s: Stroke) {
            strokePaint.color = s.color
            val n = s.xs.size
            if (n == 0) return
            if (n == 1) { strokePaint.strokeWidth = s.ws[0]; c.drawPoint(s.xs[0], s.ys[0], strokePaint); return }
            for (i in 1 until n) {
                strokePaint.strokeWidth = (s.ws[i - 1] + s.ws[i]) / 2f
                c.drawLine(s.xs[i - 1], s.ys[i - 1], s.xs[i], s.ys[i], strokePaint)
            }
        }

        private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        fun drawAi(c: Canvas, o: AiObject) {
            val b = o.bitmap() ?: return
            c.drawBitmap(b, null, o.rect(), bmpPaint)
        }

        /** Render a region of a page (page units) to a white bitmap, long side ≤ maxSide px. */
        fun render(page: Page, region: RectF, maxSide: Int = 1600, minSide: Int = 0, showAi: Boolean = true,
                   strokes: List<Stroke> = page.strokes, ai: List<AiObject> = page.ai): Bitmap {
            val long = max(region.width(), region.height()).coerceAtLeast(1f)
            var scale = maxSide / long
            if (scale > 2f) scale = 2f
            if (minSide > 0 && long * scale < minSide) scale = minSide / long
            val w = (region.width() * scale).toInt().coerceAtLeast(1)
            val h = (region.height() * scale).toInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawColor(Color.WHITE)
            c.scale(scale, scale)
            c.translate(-region.left, -region.top)
            if (showAi) ai.forEach { drawAi(c, it) }
            strokes.forEach { drawStroke(c, it) }
            return bmp
        }
    }
}
