package com.inkagent.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import com.inkagent.agent.Session
import com.inkagent.llm.Block
import org.json.JSONArray
import org.json.JSONObject

/** Chat transcript rendered in a WebView (Markdown + KaTeX, dsh ui-primitives rules). */
@SuppressLint("SetJavaScriptEnabled")
class ChatView(context: Context) {
    val view = WebView(context)
    private val main = Handler(Looper.getMainLooper())
    private var loaded = false
    private val queue = ArrayList<String>()

    // delta coalescing
    private val pendingText = LinkedHashMap<String, StringBuilder>()
    private val pendingReason = LinkedHashMap<String, StringBuilder>()
    private var flushScheduled = false

    init {
        view.settings.javaScriptEnabled = true
        view.settings.allowFileAccess = true
        @Suppress("DEPRECATION")
        view.settings.allowFileAccessFromFileURLs = true
        view.webViewClient = object : WebViewClient() {
            override fun onPageFinished(v: WebView?, url: String?) {
                loaded = true
                queue.forEach { view.evaluateJavascript(it, null) }
                queue.clear()
            }
        }
        view.loadUrl("file:///android_asset/chat.html")
    }

    private fun q(s: String?) = if (s == null) "null" else JSONObject.quote(s)

    private fun js(code: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { js(code) }; return }
        if (!loaded) queue.add(code) else view.evaluateJavascript(code, null)
    }

    private fun flush() {
        flushScheduled = false
        for ((id, sb) in pendingReason) if (sb.isNotEmpty()) js("appendReasoning(${q(id)}, ${q(sb.toString())})")
        pendingReason.clear()
        for ((id, sb) in pendingText) if (sb.isNotEmpty()) js("appendText(${q(id)}, ${q(sb.toString())})")
        pendingText.clear()
    }

    private fun schedule() { if (!flushScheduled) { flushScheduled = true; main.postDelayed({ flush() }, 50) } }

    fun appendText(id: String, t: String) { pendingText.getOrPut(id) { StringBuilder() }.append(t); schedule() }
    fun appendReasoning(id: String, t: String) { pendingReason.getOrPut(id) { StringBuilder() }.append(t); schedule() }
    fun settle(id: String) { flush(); js("settle(${q(id)})") }
    fun toolStart(id: String, callId: String, name: String) { flush(); js("toolStart(${q(id)}, ${q(callId)}, ${q(name)})") }
    fun toolArgs(callId: String, d: String) { js("toolArgs(${q(callId)}, ${q(d)})") }
    fun toolResult(callId: String, text: String, isError: Boolean, imgPath: String?) =
        js("toolResult(${q(callId)}, ${q(text)}, $isError, ${q(imgPath?.let { "file://$it" })})")
    fun notice(t: String, err: Boolean = false) { flush(); js("notice(${q(t)}, $err)") }
    fun clear() { pendingText.clear(); pendingReason.clear(); js("clearAll()") }

    fun addUser(content: List<Block>) {
        val text = content.filterIsInstance<Block.Text>().joinToString("\n") { it.text }
        val imgs = JSONArray()
        content.filterIsInstance<Block.Image>().forEach { imgs.put("file://" + it.path) }
        js("addUser(null, ${q(text)}, $imgs)")
    }

    /** Re-render a stored session from its event log. */
    fun replay(session: Session) {
        clear()
        var n = 0
        for (e in session.events) when (e.type) {
            "user/message" -> addUser(Block.listFromJson(e.data.getJSONArray("content")))
            "assistant/message" -> {
                val id = "h${n++}"
                for (b in Block.listFromJson(e.data.getJSONArray("content"))) when (b) {
                    is Block.Reasoning -> js("appendReasoning(${q(id)}, ${q(b.text)})")
                    is Block.Text -> js("appendText(${q(id)}, ${q(b.text)})")
                    is Block.ToolCall -> { js("toolStart(${q(id)}, ${q(b.id)}, ${q(b.name)})"); js("toolArgs(${q(b.id)}, ${q(b.arguments)})") }
                    else -> {}
                }
                js("settle(${q(id)})")
            }
            "tool/result" -> {
                val blocks = Block.listFromJson(e.data.getJSONArray("content"))
                toolResult(e.data.getString("callId"), blocks.filterIsInstance<Block.Text>().joinToString("\n") { it.text },
                    e.data.optBoolean("isError"), blocks.filterIsInstance<Block.Image>().firstOrNull()?.path)
            }
            "turn/end" -> when (e.data.optString("reason")) {
                "aborted" -> notice("已停止")
                "error" -> notice("这一轮出错结束：" + e.data.optString("error"), true)
                "max-steps" -> notice("达到单轮步数上限")
                "max-tokens" -> notice("达到输出长度上限")
            }
        }
    }
}
