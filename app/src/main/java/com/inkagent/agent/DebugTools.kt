package com.inkagent.agent

import android.app.Activity
import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import com.inkagent.AppLog
import com.inkagent.llm.Block
import com.inkagent.llm.CancelSignal
import com.xnotes.ui.Editor
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * app_debug: lets the agent inspect the app itself when the user reports a problem — its own log,
 * device/layout state, recent errors in this session, and a screenshot of the whole app window
 * (what the user actually sees, including the agent panel). Read-only; never exposes API keys.
 */
class DebugTools(
    private val activity: Activity,
    private val editor: () -> Editor?,
    private val attachDir: File,
    private val session: () -> Session,
    private val info: () -> JSONObject,
) {
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

    fun register(reg: ToolRegistry) {
        reg.register(object : Tool {
            override val name = "app_debug"
            override val description = "检查 InkAgent 应用本身（用户反馈界面/功能有问题、或你怀疑工具行为不对时用）。action：" +
                "info=应用版本、设备、屏幕方向、面板模式、模型配置（不含密钥）、画布状态（页数、当前页、屏幕上可见的页、缩放、滚动方式）；" +
                "log=应用日志末尾（含网络错误与重试、界面 WebView 的 JS 报错、崩溃堆栈），可用 filter 过滤、lines 控制行数；" +
                "errors=本会话最近失败的轮次和出错的工具调用；" +
                "screenshot=整个应用窗口的截图（用户此刻看到的样子，包括笔记和你的面板）。只读，不会改动任何东西。"
            override val parameters = obj(
                "action" to JSONObject().put("type", "string").put("enum", JSONArray(listOf("info", "log", "errors", "screenshot"))),
                "lines" to t("integer", "log 返回的行数，默认 120，最多 600"),
                "filter" to t("string", "log 只保留包含该文字的行（不区分大小写），如 InkPanel、retry、Crash"),
                required = listOf("action"),
            )

            override fun execute(args: JSONObject, signal: CancelSignal): ToolResult = when (args.optString("action")) {
                "info" -> ToolResult.text(onMain { info() }.toString(2))
                "log" -> {
                    val n = args.optInt("lines", 120).coerceIn(10, 600)
                    val text = AppLog.tail(n, args.optString("filter").ifBlank { null })
                    ToolResult.text(if (text.isBlank()) "（日志为空或没有匹配的行）" else redact(text))
                }
                "errors" -> ToolResult.text(errors())
                "screenshot" -> shot()?.let { ToolResult(listOf(Block.Text("应用窗口截图 ${it.width}x${it.height}px（屏幕像素）"), it)) }
                    ?: ToolResult.error("截图失败")
                else -> ToolResult.error("未知 action，可选 info / log / errors / screenshot")
            }
        })
    }

    private fun errors(): String {
        val sb = StringBuilder()
        for (e in session().events.toList().takeLast(400)) {
            val d = e.data
            when {
                e.type == "turn/end" && d.optString("reason") != "completed" ->
                    sb.append("${fmt.format(Date(e.time))} 第${d.optInt("turn")}轮结束：${d.optString("reason")} ${d.optString("error").take(300)}\n")
                e.type == "tool/result" && d.optBoolean("isError") -> {
                    val txt = d.optJSONArray("content")?.optJSONObject(0)?.optString("text").orEmpty()
                    sb.append("${fmt.format(Date(e.time))} 工具 ${d.optString("name")} 出错：${txt.take(300)}\n")
                }
            }
        }
        return if (sb.isEmpty()) "本会话没有失败的轮次或出错的工具调用。" else sb.toString()
    }

    private fun shot(): Block.Image? {
        val ht = HandlerThread("app-debug-shot").apply { start() }
        try {
            var bmp: Bitmap? = null
            val latch = CountDownLatch(1)
            onMain {
                val win = activity.window
                val decor = win.decorView
                val b = Bitmap.createBitmap(decor.width.coerceAtLeast(1), decor.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                try {
                    android.view.PixelCopy.request(win, b, { r ->
                        if (r == android.view.PixelCopy.SUCCESS) bmp = b
                        latch.countDown()
                    }, Handler(ht.looper))
                } catch (e: Exception) {
                    runCatching { decor.draw(android.graphics.Canvas(b)); bmp = b }
                    latch.countDown()
                }
            }
            latch.await(5, TimeUnit.SECONDS)
            val full = bmp ?: return null
            // keep the image a sane size for the model
            val k = minOf(1.0, 1600.0 / maxOf(full.width, full.height))
            val out = if (k < 1.0) Bitmap.createScaledBitmap(full, (full.width * k).toInt(), (full.height * k).toInt(), true) else full
            return savePng(attachDir, out, "app")
        } catch (e: Exception) {
            AppLog.w("InkAgent", "app_debug screenshot failed", e); return null
        } finally { ht.quitSafely() }
    }

    private fun redact(t: String) = t
        .replace(Regex("(?i)(bearer\\s+)[A-Za-z0-9._\\-]{12,}"), "$1***")
        .replace(Regex("\\b(sk|tvly|ak|key)[-_][A-Za-z0-9_\\-]{12,}"), "$1-***")
}
