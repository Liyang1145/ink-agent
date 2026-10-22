package com.inkagent.agent

import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import com.inkagent.llm.Block
import com.inkagent.llm.CancelSignal
import com.inkagent.note.AiObject
import com.inkagent.note.Notebook
import com.inkagent.note.PageView
import com.inkagent.ui.Rasterizer
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
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

private fun obj(vararg props: Pair<String, JSONObject>, required: List<String> = emptyList()): JSONObject {
    val p = JSONObject(); props.forEach { p.put(it.first, it.second) }
    return JSONObject().put("type", "object").put("properties", p).put("required", JSONArray(required))
}
private fun t(type: String, desc: String) = JSONObject().put("type", type).put("description", desc)

/** px (CSS) on the raster → page units. */
private const val PX_TO_PAGE = 0.75f

class CanvasTools(
    private val pageView: PageView,
    private val notebook: Notebook,
    private val raster: Rasterizer,
    private val attachDir: File,
) {
    private fun pageIndex(args: JSONObject): Int {
        val n = if (args.has("page")) args.getInt("page") - 1 else onMain { pageView.pageIndex }
        if (n !in notebook.pages.indices) throw IllegalArgumentException("没有第 ${n + 1} 页（共 ${notebook.pages.size} 页）")
        return n
    }

    /** Free spot below everything on the page. */
    private fun nextFreeY(idx: Int): Float {
        val p = notebook.pages[idx]
        var bottom = 40f
        p.strokes.forEach { bottom = max(bottom, it.bounds.bottom) }
        p.ai.forEach { bottom = max(bottom, it.y + it.h) }
        return bottom + 30f
    }

    private fun place(idx: Int, kind: String, source: String, args: JSONObject, r: Rasterizer.Result, scale: Float): String {
        val w = r.cssWidth * PX_TO_PAGE * scale
        val h = w * r.bitmap.height / r.bitmap.width.coerceAtLeast(1)
        val x = if (args.has("x")) args.getDouble("x").toFloat() else 60f
        val y = if (args.has("y")) args.getDouble("y").toFloat() else nextFreeY(idx)
        val png = savePng(notebook.aiDir, r.bitmap, kind)
        val id = "ai" + System.currentTimeMillis().toString(36)
        onMain {
            val page = notebook.pages[idx]
            pageView.snapshot(page)
            if (y + h > page.height) page.height = y + h + 40f
            if (x + w > page.width) page.width = x + w + 40f
            page.ai.add(AiObject(id, kind, source, x, y, w, h, png.path))
            if (pageView.pageIndex != idx) pageView.goTo(idx) else pageView.invalidate()
            notebook.save()
        }
        return "已放到第 ${idx + 1} 页 AI 图层：id=$id，位置 (${x.toInt()}, ${y.toInt()})，尺寸 ${w.toInt()}x${h.toInt()}（页面单位，A4 宽 1000）"
    }

    fun register(reg: ToolRegistry, tavilyKey: () -> String) {
        reg.register(object : Tool {
            override val name = "list_pages"
            override val description = "列出笔记本所有页面：尺寸、手写笔画数量和范围、AI 图层上的对象，以及用户当前所在页。页面坐标单位：A4 宽 1000、高 1414，原点在左上角。"
            override val parameters = obj()
            override fun execute(args: JSONObject, signal: CancelSignal): ToolResult {
                val cur = onMain { pageView.pageIndex }
                val s = StringBuilder("共 ${notebook.pages.size} 页，用户当前在第 ${cur + 1} 页。\n")
                onMain { notebook.pages.forEachIndexed { i, p -> s.append(p.describe(i)).append('\n') } }
                return ToolResult.text(s.toString())
            }
        })
        reg.register(object : Tool {
            override val name = "view_page"
            override val description = "查看画布：把某一页（默认用户当前页）渲染成图片返回，包括手写笔迹和 AI 图层。可用 region 只看局部以获得更清晰的细节。"
            override val parameters = obj(
                "page" to t("integer", "页码，从 1 开始；省略表示当前页"),
                "region" to JSONObject().put("type", "object").put("description", "可选局部区域（页面单位）")
                    .put("properties", JSONObject().put("x", t("number", "")).put("y", t("number", ""))
                        .put("width", t("number", "")).put("height", t("number", ""))),
            )
            override fun execute(args: JSONObject, signal: CancelSignal): ToolResult {
                val idx = pageIndex(args)
                val bmp = onMain {
                    val p = notebook.pages[idx]
                    val reg = args.optJSONObject("region")
                    val rect = if (reg != null) RectF(reg.optDouble("x", 0.0).toFloat(), reg.optDouble("y", 0.0).toFloat(),
                        (reg.optDouble("x", 0.0) + reg.optDouble("width", p.width.toDouble())).toFloat(),
                        (reg.optDouble("y", 0.0) + reg.optDouble("height", p.height.toDouble())).toFloat())
                    else RectF(0f, 0f, p.width, p.height)
                    Notebook.render(p, rect, maxSide = 1600, showAi = true)
                }
                val img = savePng(attachDir, bmp, "page")
                return ToolResult(listOf(Block.Text("第 ${idx + 1} 页的画面（${bmp.width}x${bmp.height}px）"), img))
            }
        })
        reg.register(object : Tool {
            override val name = "canvas_write_latex"
            override val description = "在画布的 AI 图层上写一段排版好的 LaTeX 公式（KaTeX 渲染，蓝色）。用空行分隔可写多行公式。不传坐标时自动放在该页已有内容的下方。"
            override val parameters = obj(
                "latex" to t("string", "LaTeX 源码，不要带 $ 或 \\[ \\] 定界符"),
                "page" to t("integer", "页码，从 1 开始；省略表示当前页"),
                "x" to t("number", "左上角 x（页面单位）"),
                "y" to t("number", "左上角 y（页面单位）"),
                "scale" to t("number", "缩放，默认 1（约等于正常手写大小）"),
                required = listOf("latex"),
            )
            override fun execute(args: JSONObject, signal: CancelSignal): ToolResult {
                val idx = pageIndex(args)
                val src = args.getString("latex")
                val r = raster.render("latex", src)
                val msg = place(idx, "latex", src, args, r, args.optDouble("scale", 1.0).toFloat())
                return ToolResult(listOf(Block.Text(msg + "\n渲染结果如图："), savePng(attachDir, r.bitmap, "latex")))
            }
        })
        reg.register(object : Tool {
            override val name = "canvas_draw_svg"
            override val description = "在画布的 AI 图层上画一张 SVG 图（函数图像、几何图形、示意图等）。传完整的 <svg> 元素，建议带 viewBox；用 width 指定在页面上的宽度。"
            override val parameters = obj(
                "svg" to t("string", "完整 SVG 源码"),
                "page" to t("integer", "页码，从 1 开始；省略表示当前页"),
                "x" to t("number", "左上角 x（页面单位）"),
                "y" to t("number", "左上角 y（页面单位）"),
                "width" to t("number", "页面上的宽度（页面单位，A4 宽 1000），默认 500"),
                required = listOf("svg"),
            )
            override fun execute(args: JSONObject, signal: CancelSignal): ToolResult {
                val idx = pageIndex(args)
                val src = args.getString("svg")
                val widthPage = args.optDouble("width", 500.0).toFloat()
                val r = raster.render("svg", src, (widthPage / PX_TO_PAGE).toInt())
                val msg = place(idx, "svg", src, args, r, 1f)
                return ToolResult(listOf(Block.Text(msg + "\n渲染结果如图："), savePng(attachDir, r.bitmap, "svg")))
            }
        })
        reg.register(object : Tool {
            override val name = "canvas_remove"
            override val description = "删除 AI 图层上的对象：传 id 删除一个，或 all=true 清空该页 AI 图层。不会动用户的手写笔迹。"
            override val parameters = obj(
                "id" to t("string", "对象 id"),
                "all" to t("boolean", "清空该页所有 AI 对象"),
                "page" to t("integer", "页码，从 1 开始；省略表示当前页"),
            )
            override fun execute(args: JSONObject, signal: CancelSignal): ToolResult {
                val idx = pageIndex(args)
                return onMain {
                    val p = notebook.pages[idx]
                    pageView.snapshot(p)
                    val n = if (args.optBoolean("all")) { val c = p.ai.size; p.ai.clear(); c }
                    else { val id = args.optString("id"); val before = p.ai.size; p.ai.removeAll { it.id == id }; before - p.ai.size }
                    pageView.invalidate(); notebook.save()
                    ToolResult.text("删除了 $n 个对象")
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
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) InkAgent/0.2")
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
