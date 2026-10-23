package com.inkagent.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import com.inkagent.agent.Session
import com.inkagent.agent.SessionEvent
import com.inkagent.llm.Block
import com.inkagent.llm.Chunk
import org.json.JSONArray
import org.json.JSONObject

/**
 * The agent panel (header, transcript, composer) as one WebView, laid out after dsh's chat UI:
 * work steps grouped per step and folded behind "已完成，用时 …" per the presentation policy.
 */
@SuppressLint("SetJavaScriptEnabled")
class AgentPanel(context: Context, private val host: Host) {
    interface Host {
        fun onSend(text: String): Boolean
        fun onRemoveAttachment(index: Int)
        /** Single tap on a tray thumbnail → the image editor. */
        fun onEditAttachment(index: Int) {}
        fun onOpenModel()
        fun onOpenSessions()
        fun onNewSession()
        fun onOpenSettings()
        fun onStop()
        fun currentPage(): Int
        fun onCollapse()
        fun onToggleMode()
        fun listSessions(): String
        fun onOpenSession(id: String)
        fun onPinSession(id: String, pinned: Boolean)
        fun onRenameSession(id: String, title: String)
        fun onDeleteSession(id: String)
        fun onConfirmResult(id: String, ok: Boolean)
        /** 「框选截屏」 (overlay window only). */
        fun onCapture() {}
        /** Composer focus changed (the overlay window becomes focusable for the IME). */
        fun onInputFocus(focused: Boolean) {}
    }

    val view = WebView(context)
    private val main = Handler(Looper.getMainLooper())
    private var loaded = false
    private val queue = ArrayList<String>()
    private val pendingText = StringBuilder()
    private val pendingReason = StringBuilder()
    private var flushScheduled = false

    init {
        view.settings.javaScriptEnabled = true
        view.settings.allowFileAccess = true
        view.settings.textZoom = 100
        @Suppress("DEPRECATION")
        view.settings.allowFileAccessFromFileURLs = true
        view.addJavascriptInterface(object {
            @JavascriptInterface fun send(text: String): Boolean {
                var ok = false
                val latch = java.util.concurrent.CountDownLatch(1)
                main.post { ok = host.onSend(text); latch.countDown() }
                latch.await(2, java.util.concurrent.TimeUnit.SECONDS)
                return ok
            }
            @JavascriptInterface fun removeAttachment(i: Int) { main.post { host.onRemoveAttachment(i) } }
            @JavascriptInterface fun editAttachment(i: Int) { main.post { host.onEditAttachment(i) } }
            @JavascriptInterface fun openModel() { main.post { host.onOpenModel() } }
            @JavascriptInterface fun openSessions() { main.post { host.onOpenSessions() } }
            @JavascriptInterface fun newSession() { main.post { host.onNewSession() } }
            @JavascriptInterface fun openSettings() { main.post { host.onOpenSettings() } }
            @JavascriptInterface fun stop() { main.post { host.onStop() } }
            @JavascriptInterface fun currentPage(): Int = host.currentPage()
            @JavascriptInterface fun collapse() { main.post { host.onCollapse() } }
            @JavascriptInterface fun toggleMode() { main.post { host.onToggleMode() } }
            @JavascriptInterface fun listSessions(): String {
                var out = "[]"
                val latch = java.util.concurrent.CountDownLatch(1)
                main.post { out = runCatching { host.listSessions() }.getOrDefault("[]"); latch.countDown() }
                latch.await(3, java.util.concurrent.TimeUnit.SECONDS)
                return out
            }
            @JavascriptInterface fun openSession(id: String) { main.post { host.onOpenSession(id) } }
            @JavascriptInterface fun pinSession(id: String, pinned: Boolean) { main.post { host.onPinSession(id, pinned) } }
            @JavascriptInterface fun renameSession(id: String, title: String) { main.post { host.onRenameSession(id, title) } }
            @JavascriptInterface fun deleteSession(id: String) { main.post { host.onDeleteSession(id) } }
            @JavascriptInterface fun confirmResult(id: String, ok: Boolean) { main.post { host.onConfirmResult(id, ok) } }
            @JavascriptInterface fun capture() { main.post { host.onCapture() } }
            @JavascriptInterface fun inputFocus(f: Boolean) { main.post { host.onInputFocus(f) } }
        }, "Native")
        view.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                Log.i("InkPanel", "${m.messageLevel()} ${m.message()} @${m.lineNumber()}")
                return true
            }
        }
        view.webViewClient = object : WebViewClient() {
            override fun onPageFinished(v: WebView?, url: String?) {
                loaded = true
                queue.forEach { view.evaluateJavascript(it, null) }
                queue.clear()
                pushHeight(view.height)
            }
        }
        // Push the real view height into the page on every size change (see setViewH in agent.html).
        view.addOnLayoutChangeListener { v, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) pushHeight(v.height)
        }
        view.loadUrl("file:///android_asset/agent.html")
    }

    private var lastPushedH = -1
    private fun pushHeight(px: Int) {
        if (px <= 2) return
        val css = px / view.resources.displayMetrics.density
        lastPushedH = px
        js("setViewH(${"%.1f".format(java.util.Locale.US, css)})")
    }

    private fun q(s: String?) = if (s == null) "null" else JSONObject.quote(s)

    private fun js(code: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { js(code) }; return }
        if (!loaded) queue.add(code) else view.evaluateJavascript(code, null)
    }

    /** Debug only: evaluate [code] in agent.html and hand its (JSON-encoded) result to [cb] on the main thread. */
    fun evalForTest(code: String, cb: (String) -> Unit) {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { evalForTest(code, cb) }; return }
        if (!loaded) { cb("\"{\\\"loaded\\\":false}\""); return }
        view.evaluateJavascript(code) { r -> cb(r ?: "null") }
    }

    /** Debug only: put [text] in the composer as if typed (adb `input text` cannot type Chinese). */
    fun setComposerForTest(text: String) =
        js("(function(t){var i=document.getElementById('input');i.value=t;i.dispatchEvent(new Event('input',{bubbles:true}));})(${q(text)})")

    private fun flush() {
        flushScheduled = false
        if (pendingReason.isNotEmpty()) { js("reasoning(${q(pendingReason.toString())})"); pendingReason.setLength(0) }
        if (pendingText.isNotEmpty()) { js("textDelta(${q(pendingText.toString())})"); pendingText.setLength(0) }
    }
    private fun schedule() { if (!flushScheduled) { flushScheduled = true; main.postDelayed({ flush() }, 50) } }

    // ---------------- composer state ----------------
    fun setPolicy(mode: String, timing: String) = js("setPolicy(${q(mode)}, ${q(timing)})")
    fun setRunning(b: Boolean) = js("setRunning($b)")
    fun setModel(t: String) = js("setModel(${q(t)})")
    fun setTitle(t: String) = js("setTitle(${q(t)})")
    fun setWindowMode(m: String) = js("setWindowMode(${q(m)})")
    fun blurInput() = js("(function(){var a=document.activeElement;if(a&&a.blur)a.blur();})()")
    fun setAttachments(paths: List<String>) {
        val a = JSONArray(); paths.forEach { a.put("file://$it") }
        js("setAttachments($a)")
    }
    fun notice(t: String, err: Boolean = false) { flush(); js("notice(${q(t)}, $err)") }
    fun setStats(json: String) = js("setStats($json)")
    fun refreshSessions() = js("refreshSessions()")
    fun openSessions() = js("openSide()")
    fun showConfirm(id: String, title: String, lines: List<Pair<String, String>>) {
        flush()
        val a = JSONArray(); lines.forEach { a.put(JSONArray().put(it.first).put(it.second)) }
        js("showConfirm(${q(id)}, ${q(title)}, $a)")
    }
    fun resolveConfirm(id: String, ok: Boolean) = js("resolveConfirm(${q(id)}, $ok)")

    // ---------------- live stream ----------------
    fun chunk(c: Chunk) = when (c) {
        is Chunk.TextDelta -> { if (pendingReason.isNotEmpty()) flush(); pendingText.append(c.text); schedule() }
        is Chunk.ReasoningDelta -> { if (pendingText.isNotEmpty()) flush(); pendingReason.append(c.text); schedule() }
        is Chunk.ToolCallStart -> { flush(); js("toolStart(${q(c.id)}, ${q(c.name)})") }
        is Chunk.ToolArgsDelta -> { flush(); js("toolArgs(${q(c.id)}, ${q(c.delta)})") }
    }

    /** Session events drive the transcript structure; live and replayed sessions share this path. */
    fun event(e: SessionEvent, live: Boolean) {
        flush()
        when (e.type) {
            "turn/start" -> js("turnStart(${e.time})")
            "step/start" -> js("stepStart()")
            "user/message" -> {
                val content = Block.listFromJson(e.data.getJSONArray("content"))
                val text = content.filterIsInstance<Block.Text>().joinToString("\n") { it.text }
                val imgs = JSONArray(); content.filterIsInstance<Block.Image>().forEach { imgs.put("file://" + it.path) }
                js("userMessage(${q(text)}, $imgs, ${e.data.optBoolean("steer")})")
            }
            "assistant/message" -> {
                val content = Block.listFromJson(e.data.getJSONArray("content"))
                if (!live) for (b in content) when (b) {
                    is Block.Reasoning -> if (b.text.isNotEmpty()) js("reasoning(${q(b.text)})")
                    is Block.Text -> if (b.text.isNotEmpty()) js("textDelta(${q(b.text)})")
                    is Block.ToolCall -> { js("toolStart(${q(b.id)}, ${q(b.name)})"); js("toolArgs(${q(b.id)}, ${q(b.arguments)})") }
                    else -> {}
                }
                js("assistantEnd(${content.any { it is Block.ToolCall }})")
            }
            "tool/result" -> {
                val blocks = Block.listFromJson(e.data.getJSONArray("content"))
                val text = blocks.filterIsInstance<Block.Text>().joinToString("\n") { it.text }
                val img = blocks.filterIsInstance<Block.Image>().firstOrNull()?.path?.let { "file://$it" }
                js("toolResult(${q(e.data.getString("callId"))}, ${q(text)}, ${e.data.optBoolean("isError")}, ${q(img)})")
            }
            "step/end" -> js("stepEnd()")
            "turn/end" -> js("turnEnd(${q(e.data.optString("reason"))}, ${q(e.data.optString("error"))}, ${e.time})")
        }
    }

    fun replay(session: Session) {
        pendingText.setLength(0); pendingReason.setLength(0)
        js("clearAll()")
        val evs = session.events
        evs.forEachIndexed { i, e -> event(e, false) }
        // a turn interrupted by an app kill has no turn/end
        val lastStart = evs.indexOfLast { it.type == "turn/start" }
        val lastEnd = evs.indexOfLast { it.type == "turn/end" }
        if (lastStart > lastEnd) js("turnEnd('aborted', '', ${evs.last().time})")
    }
}
