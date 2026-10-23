package com.inkagent.agent

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import com.inkagent.llm.Block
import com.inkagent.llm.CancelSignal
import com.inkagent.ui.Rasterizer
import com.xnotes.core.geometry.Rect
import com.xnotes.core.model.ImageItem
import com.xnotes.ui.Editor
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.max

private val main = Handler(Looper.getMainLooper())

/** Run on the UI thread and wait for the result (tools run on the agent thread). */
fun <T> onMain(block: () -> T): T {
    if (Looper.myLooper() == Looper.getMainLooper()) return block()
    val latch = CountDownLatch(1)
    var out: Result<T>? = null
    main.post { out = runCatching(block); latch.countDown() }
    if (!latch.await(20, TimeUnit.SECONDS)) throw IllegalStateException("UI 线程超时")
    return out!!.getOrThrow()
}

fun savePng(dir: File, bmp: Bitmap, prefix: String = "img"): Block.Image {
    dir.mkdirs()
    val f = File(dir, "$prefix-${System.currentTimeMillis()}-${(Math.random() * 1e6).toInt()}.png")
    FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return Block.Image(f.absolutePath, bmp.width, bmp.height)
}

fun savePngBytes(dir: File, bytes: ByteArray, prefix: String = "img"): Block.Image {
    dir.mkdirs()
    val f = File(dir, "$prefix-${System.currentTimeMillis()}-${(Math.random() * 1e6).toInt()}.png")
    f.writeBytes(bytes)
    val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeFile(f.path, o)
    return Block.Image(f.absolutePath, o.outWidth, o.outHeight)
}

internal fun obj(vararg props: Pair<String, JSONObject>, required: List<String> = emptyList()): JSONObject {
    val p = JSONObject(); props.forEach { p.put(it.first, it.second) }
    return JSONObject().put("type", "object").put("properties", p).put("required", JSONArray(required))
}
internal fun t(type: String, desc: String) = JSONObject().put("type", type).put("description", desc)

/**
 * Registry of the images the agent placed on the note ("AI 对象"). Each is an ordinary xnotes image item,
 * so the user can move, resize, select and undo it like anything else; the registry remembers which ones
 * are the agent's by a hash of the image bytes, so canvas_remove still finds them after a restart.
 */
class AiRegistry(private val file: File) {
    class Entry(val id: String, val sha1: String, val kind: String, val source: String)
    private val entries = LinkedHashMap<String, Entry>()
    private val hashCache = HashMap<String, String>()

    init {
        runCatching {
            val o = JSONObject(file.readText())
            o.keys().forEach { k -> val e = o.getJSONObject(k); entries[k] = Entry(k, e.getString("sha1"), e.optString("kind"), e.optString("source")) }
        }
    }

    private fun save() {
        val o = JSONObject()
        entries.values.forEach { o.put(it.id, JSONObject().put("sha1", it.sha1).put("kind", it.kind).put("source", it.source.take(4000))) }
        file.parentFile?.mkdirs(); file.writeText(o.toString())
    }

    fun add(id: String, bytes: ByteArray, kind: String, source: String) { entries[id] = Entry(id, sha1(bytes), kind, source); save() }

    fun remove(ids: Collection<String>) { ids.forEach { entries.remove(it) }; save() }

    fun sha1(bytes: ByteArray): String = MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun hashOf(item: ImageItem): String? = hashCache.getOrPut(item.image.file.absolutePath) {
        runCatching { sha1(item.image.file.readBytes()) }.getOrNull() ?: return null
    }

    /** The agent's entries present on [items]: entry → the image item carrying it. */
    fun match(items: List<com.xnotes.core.model.CanvasItem>): List<Pair<Entry, ImageItem>> {
        val bySha = entries.values.associateBy { it.sha1 }
        return items.filterIsInstance<ImageItem>().mapNotNull { img -> hashOf(img)?.let { h -> bySha[h]?.let { it to img } } }
    }
}

private const val MARGIN_FRAC = 0.05 // side/bottom margin, fraction of page width
private const val TOP_FRAC = 0.04    // top margin, fraction of page height
private const val GAP_FRAC = 0.015   // clearance kept around existing content, fraction of page width

class CanvasTools(
    private val editor: () -> Editor?,
    private val raster: Rasterizer,
    private val attachDir: File,
    private val registry: AiRegistry,
) {
    private fun ed(): Editor = editor()?.takeIf { it.noteOpen } ?: throw IllegalStateException("当前没有打开的笔记")

    private fun pageIndex(args: JSONObject): Int {
        val e = ed()
        val count = onMain { e.pageCount }
        val n = if (args.has("page")) args.getInt("page") - 1 else onMain { e.agentCurrentPage() }
        if (n !in 0 until count) throw IllegalArgumentException("没有第 ${n + 1} 页（共 $count 页）")
        return n
    }

    private fun describePage(e: Editor, i: Int): String {
        val p = e.pageAt(i) ?: return ""
        val kinds = p.items.groupingBy { it.kind }.eachCount()
        val sb = StringBuilder("第 ${i + 1} 页：${p.width.toInt()}x${p.height.toInt()}")
        val strokes = p.items.filter { it.kind == "stroke" }
        sb.append("，笔画 ${strokes.size} 条")
        if (strokes.isNotEmpty()) {
            var b = strokes.first().bounds(); strokes.forEach { b = b.union(it.bounds()) }
            sb.append("（范围 x ${b.left.toInt()}–${b.right.toInt()}，y ${b.top.toInt()}–${b.bottom.toInt()}）")
        }
        kinds.filterKeys { it != "stroke" }.forEach { (k, v) -> sb.append("，$k $v 个") }
        val ai = registry.match(p.items)
        if (ai.isNotEmpty()) {
            sb.append("\n  AI 对象：")
            ai.forEach { (en, img) ->
                val r = img.bounds()
                sb.append("\n  - id=${en.id} ${en.kind} 位置(${r.left.toInt()},${r.top.toInt()}) 尺寸${r.w.toInt()}x${r.h.toInt()} ${en.source.replace('\n', ' ').take(60)}")
            }
        }
        return sb.toString()
    }

    /**
     * Where to put a [w]x[h] object on [page] so it stays inside the page margins and overlaps nothing
     * already there. With a preferred (x, y) the nearest free spot at or below it wins (then above it);
     * without one, the spot just below the lowest existing content, else the first free region from the top.
     * Returns null when no free spot exists at this size.
     */
    internal fun findSpot(pageW: Double, pageH: Double, obstacles: List<Rect>, w: Double, h: Double, prefX: Double?, prefY: Double?): Rect? {
        val m = pageW * MARGIN_FRAC
        val gap = pageW * GAP_FRAC
        val minX = m; val maxX = pageW - m - w
        val minY = pageH * TOP_FRAC; val maxY = pageH - m - h
        if (maxX < minX - 0.5 || maxY < minY - 0.5) return null
        val obs = obstacles.map { it.outset(gap) }
        fun free(x: Double, y: Double): Boolean { val r = Rect(x, y, w, h); return obs.none { it.intersects(r) } }
        val xs = (listOf(minX, maxX) + obs.map { it.right } + obs.map { it.left - w })
            .filter { it >= minX - 0.5 && it <= maxX + 0.5 }.map { it.coerceIn(minX, maxOf(minX, maxX)) }.distinct()
        val step = maxOf(6.0, pageH / 300.0)
        fun scan(y0: Double, y1: Double, order: List<Double>): Rect? {
            var y = y0
            while (y <= y1 + 0.01) {
                for (x in order) if (free(x, y)) return Rect(x, y, w, h)
                y += step
            }
            return null
        }
        if (prefX == null && prefY == null) {
            val lowest = obstacles.maxOfOrNull { it.bottom }
            val belowY = if (lowest == null) minY else maxOf(minY, lowest + gap * 1.5)
            if (belowY <= maxY && free(minX, belowY)) return Rect(minX, belowY, w, h)
            return scan(minY, maxY, xs.sorted())
        }
        val px = (prefX ?: minX).coerceIn(minX, maxOf(minX, maxX))
        val py = (prefY ?: minY).coerceIn(minY, maxOf(minY, maxY))
        val order = (listOf(px) + xs).distinct().sortedBy { kotlin.math.abs(it - px) }
        return scan(py, maxY, order) ?: scan(minY, py, order)
    }

    private fun place(idx: Int, kind: String, source: String, args: JSONObject, r: Rasterizer.Result, widthPage: Double?): String {
        val e = ed()
        val bytes = ByteArrayOutputStream().use { r.bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() }
        val id = "ai" + System.currentTimeMillis().toString(36)
        val msg = onMain {
            val page = e.pageAt(idx) ?: throw IllegalStateException("页面不存在")
            val unit = page.width / 1000.0 // the model sees A4-width-1000 units only as a hint; real coords are page px
            val aspect = r.bitmap.height.toDouble() / r.bitmap.width.coerceAtLeast(1)
            val maxW = page.width * (1 - 2 * MARGIN_FRAC)
            val asked = if (args.has("width")) args.optDouble("width").takeIf { it > 1 } else null
            var w = (asked ?: widthPage ?: (r.cssWidth * 0.75 * unit * args.optDouble("scale", 1.0))).coerceIn(page.width * 0.04, maxW)
            // keep it on the page vertically too
            val maxH = page.height * (1 - TOP_FRAC - MARGIN_FRAC)
            if (w * aspect > maxH) w = maxH / aspect
            val obstacles = page.items.map { it.bounds() }
            val prefX = if (args.has("x")) args.optDouble("x") else null
            val prefY = if (args.has("y")) args.optDouble("y") else null
            var spot: Rect? = null
            var shrink = 1.0
            while (spot == null && shrink > 0.39) {
                spot = findSpot(page.width, page.height, obstacles, w * shrink, w * shrink * aspect, prefX, prefY)
                if (spot == null) shrink -= 0.1
            }
            val note = StringBuilder()
            val rect = spot ?: run {
                // page full: put it below everything at the left margin, clamped to the page (may overlap)
                val ww = w * 0.6; val hh = ww * aspect
                val y = ((obstacles.maxOfOrNull { it.bottom } ?: 0.0) + page.width * GAP_FRAC).coerceAtMost(page.height - hh).coerceAtLeast(0.0)
                note.append("（页面已没有足够空白，可能与已有内容重叠，建议换一页或先删除旧对象）")
                Rect(page.width * MARGIN_FRAC, y, ww, hh)
            }
            if (spot != null && shrink < 0.999) note.append("（空白不足，已缩小到 ${(shrink * 100).toInt()}%）")
            if (spot != null && (prefX != null || prefY != null) &&
                (kotlin.math.abs(rect.x - (prefX ?: rect.x)) > 1 || kotlin.math.abs(rect.y - (prefY ?: rect.y)) > 1))
                note.append("（指定位置会与已有内容重叠或超出页面，已自动挪到最近的空白处）")
            e.agentInsertImage(idx, bytes, rect)
            if (e.agentCurrentPage() != idx) e.goToPage(idx)
            "已放到第 ${idx + 1} 页：id=$id，位置 (${rect.x.toInt()}, ${rect.y.toInt()})，尺寸 ${rect.w.toInt()}x${rect.h.toInt()}（页面像素，页面 ${page.width.toInt()}x${page.height.toInt()}）$note。它是普通图片对象，用户可以移动、缩放或撤销。"
        }
        registry.add(id, bytes, kind, source)
        return msg
    }

    fun register(reg: ToolRegistry, tavilyKey: () -> String) {
        reg.register(object : Tool {
            override val name = "list_pages"
            override val description = "列出当前笔记的所有页面：尺寸（页面像素，原点左上角）、手写笔画数量和范围、其他对象，以及你放上去的 AI 对象（id）。也告诉你用户当前在第几页。"
            override val parameters = obj()
            override fun execute(args: JSONObject, signal: CancelSignal): ToolResult {
                val e = ed()
                return onMain {
                    val sb = StringBuilder("笔记「${e.title}」共 ${e.pageCount} 页，用户当前在第 ${e.agentCurrentPage() + 1} 页。\n")
                    for (i in 0 until e.pageCount) sb.append(describePage(e, i)).append('\n')
                    ToolResult.text(sb.toString())
                }
            }
        })
        reg.register(object : Tool {
            override val name = "view_page"
            override val description = "查看画布：把某一页（默认用户当前页）渲染成图片返回，包括手写笔迹和所有对象。可用 region 只看局部以获得更清晰的细节（页面像素坐标）。"
            override val parameters = obj(
                "page" to t("integer", "页码，从 1 开始；省略表示当前页"),
                "region" to JSONObject().put("type", "object").put("description", "可选局部区域（页面像素）")
                    .put("properties", JSONObject().put("x", t("number", "")).put("y", t("number", ""))
                        .put("width", t("number", "")).put("height", t("number", ""))),
            )
            override fun execute(args: JSONObject, signal: CancelSignal): ToolResult {
                val idx = pageIndex(args)
                val e = ed()
                val bmp = onMain {
                    val reg = args.optJSONObject("region")
                    val rect = reg?.let { Rect(it.optDouble("x", 0.0), it.optDouble("y", 0.0), it.optDouble("width", 400.0), it.optDouble("height", 400.0)) }
                    e.agentRenderPage(idx, 1600, rect)
                } ?: return ToolResult.error("渲染失败")
                val img = savePng(attachDir, bmp, "page")
                val size = onMain { e.pageAt(idx)?.let { "${it.width.toInt()}x${it.height.toInt()}" } }
                return ToolResult(listOf(Block.Text("第 ${idx + 1} 页的画面（图片 ${bmp.width}x${bmp.height}px；页面坐标系 $size）"), img))
            }
        })
        reg.register(object : Tool {
            override val name = "canvas_write_latex"
            override val description = "在笔记页面上写一段排版好的 LaTeX 公式（KaTeX 渲染，蓝色），作为一个图片对象放到页面上。用空行分隔可写多行公式。通常不要传坐标：系统自动放在已有内容下方的空白处，不会遮挡笔迹，并缩放到页宽以内。传了 x/y/width 也会被限制在页面内、避开已有内容。"
            override val parameters = obj(
                "latex" to t("string", "LaTeX 源码，不要带 $ 或 \\[ \\] 定界符"),
                "page" to t("integer", "页码，从 1 开始；省略表示当前页"),
                "x" to t("number", "可选：期望的左上角 x（页面像素）"),
                "y" to t("number", "可选：期望的左上角 y（页面像素）"),
                "width" to t("number", "可选：页面上的宽度（页面像素），不超过页宽减页边距"),
                "scale" to t("number", "缩放，默认 1（约等于正常手写大小）"),
                required = listOf("latex"),
            )
            override fun execute(args: JSONObject, signal: CancelSignal): ToolResult {
                val idx = pageIndex(args)
                val src = args.getString("latex")
                val r = raster.render("latex", src)
                val msg = place(idx, "latex", src, args, r, null)
                return ToolResult(listOf(Block.Text(msg + "\n渲染结果如图："), savePng(attachDir, r.bitmap, "latex")))
            }
        })
        reg.register(object : Tool {
            override val name = "canvas_draw_svg"
            override val description = "在笔记页面上画一张 SVG 图（函数图像、几何图形、示意图等），作为图片对象放到页面上。传完整的 <svg> 元素，建议带 viewBox。通常不要传坐标：系统自动放在已有内容下方的空白处、不遮挡笔迹；width 可选（页面像素），会被限制在页宽以内。"
            override val parameters = obj(
                "svg" to t("string", "完整 SVG 源码"),
                "page" to t("integer", "页码，从 1 开始；省略表示当前页"),
                "x" to t("number", "可选：期望的左上角 x（页面像素）"),
                "y" to t("number", "可选：期望的左上角 y（页面像素）"),
                "width" to t("number", "可选：页面上的宽度（页面像素），默认约为页宽的 45%"),
                required = listOf("svg"),
            )
            override fun execute(args: JSONObject, signal: CancelSignal): ToolResult {
                val idx = pageIndex(args)
                val src = args.getString("svg")
                val pageW = onMain { ed().pageAt(idx)?.width ?: 1240.0 }
                val r = raster.render("svg", src, 600)
                val msg = place(idx, "svg", src, args, r, pageW * 0.45)
                return ToolResult(listOf(Block.Text(msg + "\n渲染结果如图："), savePng(attachDir, r.bitmap, "svg")))
            }
        })
        reg.register(object : Tool {
            override val name = "canvas_remove"
            override val description = "删除你放到页面上的 AI 对象：传 id 删除一个，或 all=true 删除该页所有 AI 对象。不会动用户的手写笔迹和用户自己的对象。可撤销。"
            override val parameters = obj(
                "id" to t("string", "对象 id"),
                "all" to t("boolean", "删除该页所有 AI 对象"),
                "page" to t("integer", "页码，从 1 开始；省略表示当前页"),
            )
            override fun execute(args: JSONObject, signal: CancelSignal): ToolResult {
                val idx = pageIndex(args)
                val e = ed()
                return onMain {
                    val page = e.pageAt(idx) ?: return@onMain ToolResult.error("页面不存在")
                    val found = registry.match(page.items)
                    val pick = if (args.optBoolean("all")) found else found.filter { it.first.id == args.optString("id") }
                    if (pick.isEmpty()) return@onMain ToolResult.error("第 ${idx + 1} 页上没有找到要删除的 AI 对象")
                    e.agentRemoveItems(idx, pick.map { it.second })
                    registry.remove(pick.map { it.first.id })
                    ToolResult.text("删除了 ${pick.size} 个对象")
                }
            }
        })
        reg.register(object : Tool {
            override val name = "web_search"
            override val description = "联网搜索（Tavily），返回标题、链接和摘要。"
            override val parameters = obj(
                "query" to t("string", "搜索词"),
                "max_results" to t("integer", "结果数，默认 5"),
                required = listOf("query"),
            )
            override fun execute(args: JSONObject, signal: CancelSignal): ToolResult {
                val key = tavilyKey()
                if (key.isBlank()) return ToolResult.error("没有配置 Tavily API Key（设置里填写）")
                val body = JSONObject().put("query", args.getString("query")).put("max_results", args.optInt("max_results", 5))
                val conn = URL("https://api.tavily.com/search").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"; conn.doOutput = true; conn.connectTimeout = 20_000; conn.readTimeout = 40_000
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer $key")
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                if (conn.responseCode !in 200..299) return ToolResult.error("Tavily HTTP ${conn.responseCode}: " +
                    (conn.errorStream?.bufferedReader()?.readText()?.take(300) ?: ""))
                val o = JSONObject(conn.inputStream.bufferedReader().readText())
                val a = o.optJSONArray("results") ?: JSONArray()
                val sb = StringBuilder()
                for (i in 0 until a.length()) {
                    val r = a.getJSONObject(i)
                    sb.append("${i + 1}. ${r.optString("title")}\n${r.optString("url")}\n${r.optString("content").take(500)}\n\n")
                }
                return ToolResult.text(sb.toString().ifEmpty { "没有结果" })
            }
        })
        reg.register(object : Tool {
            override val name = "web_fetch"
            override val description = "抓取一个公开网页并返回纯文本（截断到约 15000 字）。"
            override val parameters = obj("url" to t("string", "http(s) 链接"), required = listOf("url"))
            override fun execute(args: JSONObject, signal: CancelSignal): ToolResult {
                val url = args.getString("url")
                if (!url.startsWith("http://") && !url.startsWith("https://")) return ToolResult.error("只支持 http(s) 链接")
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 20_000; conn.readTimeout = 30_000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) InkAgent/0.4")
                if (conn.responseCode !in 200..299) return ToolResult.error("HTTP ${conn.responseCode}")
                val raw = conn.inputStream.bufferedReader().use { it.readText() }
                val text = if ((conn.contentType ?: "").contains("html")) htmlToText(raw) else raw
                return ToolResult.text(text.take(15_000))
            }
        })
    }

    private fun htmlToText(html: String): String = html
        .replace(Regex("(?is)<(script|style|noscript|svg)[^>]*>.*?</\\1>"), " ")
        .replace(Regex("(?i)<br\\s*/?>|</(p|div|li|h[1-6]|tr)>"), "\n")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex("\\n\\s*\\n+"), "\n\n")
        .trim()
}
