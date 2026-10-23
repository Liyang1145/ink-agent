package com.inkagent

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.inkagent.agent.*
import com.inkagent.llm.*
import com.inkagent.ui.AgentPanel
import com.inkagent.ui.Rasterizer
import com.xnotes.ui.Editor
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Static hooks the xnotes editor calls into (selection → agent tray). */
object AgentBridge {
    @Volatile var controller: AgentController? = null
    fun attachPng(bytes: ByteArray) { controller?.addAttachmentPng(bytes) }
}

/**
 * Owns the agent side of InkAgent: settings, global sessions, the agent loop and its tools, the
 * dsh-style panel WebView, the settings screen, and the debug hooks the emulator test drives.
 * The editor (xnotes) is reached through [editorRef]; the panel's placement is Compose's job (AgentShell).
 */
class AgentController(private val activity: Activity, private val editorRef: () -> Editor?) {
    val settings = Settings(activity)
    private val store = SessionStore(File(activity.filesDir, "sessions"))
    private val attachDir = File(activity.filesDir, "attachments")
    private val ui = Handler(Looper.getMainLooper())
    val raster = Rasterizer(activity) // before any other WebView: it enables whole-document drawing
    val panel: AgentPanel
    private val tools = ToolRegistry()
    private val registry = AiRegistry(File(activity.filesDir, "ai_items.json"))
    lateinit var agent: Agent
        private set
    private val attachments = ArrayList<Block.Image>()

    /** Compose-observed panel state. */
    var collapsed by mutableStateOf(settings.panelCollapsed)
        private set
    var widthDp by mutableStateOf(settings.panelWidthDp)

    private class PendingConfirm(val latch: CountDownLatch) { @Volatile var ok = false }
    private val confirms = HashMap<String, PendingConfirm>()


    private fun systemPrompt(): String {
        val user = settings.systemPrompt
        val env = "你运行在平板笔记应用 InkAgent 里，可以用工具查看和修改用户当前打开的笔记页面（view_page、list_pages、canvas_write_latex、canvas_draw_svg、canvas_remove），以及管理模型服务商（manage_provider）。用户消息里的 @N 表示引用第 N 页。"
        return if (user.isBlank()) env else "$user\n\n$env"
    }

    // ---------------- panel placement (read by AgentShell) ----------------
    fun updateCollapsed(b: Boolean) { collapsed = b; settings.panelCollapsed = b }
    fun commitWidth() { settings.panelWidthDp = widthDp }

    // ---------------- agent events → UI ----------------
    private val listener = object : AgentListener {
        override fun onStatus(running: Boolean) { ui.post { panel.setRunning(running); updateHeader(); pushStats() } }
        override fun onChunk(chunk: Chunk) { ui.post { panel.chunk(chunk) } }
        override fun onEvent(ev: SessionEvent) {
            ui.post {
                panel.event(ev, true)
                if (ev.type == "user/message") updateHeader()
                if (ev.type == "assistant/message" || ev.type == "turn/end") pushStats()
            }
        }
    }

    /** Footer numbers like dsh: this turn, the whole session, context use, ¥. */
    fun pushStats() {
        val s = agent.session
        val all = s.stats()
        val lastTurn = s.events.indexOfLast { it.type == "turn/start" }
        val turn = if (lastTurn >= 0) s.stats(lastTurn) else null
        fun u(st: Session.Stats) = JSONObject().put("input", st.usage.input).put("output", st.usage.output)
            .put("cacheHit", st.usage.cacheHit).put("cost", if (st.calls > 0 && (st.cost > 0 || st.costKnown)) st.cost else JSONObject.NULL).put("known", st.costKnown)
        val o = JSONObject().put("calls", all.calls).put("session", u(all)).put("context", all.lastInput)
            .put("window", settings.contextWindow(settings.model))
        if (turn != null && turn.calls > 0) o.put("turn", u(turn))
        panel.setStats(o.toString())
    }

    private val host = object : AgentPanel.Host {
        override fun onSend(text: String) = send(text)
        override fun onRemoveAttachment(index: Int) { if (index in attachments.indices) { attachments.removeAt(index); renderTray() } }
        override fun onOpenModel() = modelDialog()
        override fun onOpenSessions() { panel.refreshSessions() }
        override fun onNewSession() = switchTo(store.create())
        override fun onOpenSettings() = openSettings()
        override fun onStop() = agent.cancel()
        override fun currentPage() = (editorRef()?.agentCurrentPage() ?: 0) + 1
        override fun onCollapse() = updateCollapsed(true)
        override fun listSessions(): String {
            val a = JSONArray()
            store.list().filter { it.hasContent || it.id == agent.session.id }.forEach {
                a.put(JSONObject().put("id", it.id).put("title", it.title).put("time", it.lastTime).put("pinned", it.pinned))
            }
            return JSONObject().put("current", agent.session.id).put("list", a).toString()
        }
        override fun onOpenSession(id: String) { if (id != agent.session.id) switchTo(store.open(id)) }
        override fun onPinSession(id: String, pinned: Boolean) {
            (if (id == agent.session.id) agent.session else store.open(id)).setPinned(pinned); panel.refreshSessions()
        }
        override fun onRenameSession(id: String, title: String) {
            (if (id == agent.session.id) agent.session else store.open(id)).rename(title); panel.refreshSessions(); updateHeader()
        }
        override fun onDeleteSession(id: String) {
            if (id == agent.session.id && agent.running) { toast("先停止当前回答"); return }
            store.delete(id)
            if (id == agent.session.id) switchTo(store.create())
            panel.refreshSessions()
        }
        override fun onConfirmResult(id: String, ok: Boolean) {
            val p = synchronized(confirms) { confirms.remove(id) } ?: return
            p.ok = ok; p.latch.countDown(); panel.resolveConfirm(id, ok)
        }
    }

    /** Called on the agent thread by provider tools: show a confirm card and wait for the user's answer. */
    private fun confirmBlocking(title: String, lines: List<Pair<String, String>>): Boolean {
        val id = "c" + System.nanoTime().toString(36)
        val p = PendingConfirm(CountDownLatch(1))
        synchronized(confirms) { confirms[id] = p }
        ui.post { if (collapsed) updateCollapsed(false); panel.showConfirm(id, title, lines) }
        val answered = p.latch.await(10, TimeUnit.MINUTES)
        if (!answered) { synchronized(confirms) { confirms.remove(id) }; ui.post { panel.resolveConfirm(id, false) } }
        return answered && p.ok
    }

    // ---------------- sending ----------------
    private fun send(raw: String): Boolean {
        val text = raw.trim()
        val content = ArrayList<Block>()
        content.addAll(attachments)
        val ed = editorRef()
        if (ed != null && ed.noteOpen) Regex("@(\\d+)").findAll(text).map { it.groupValues[1].toInt() }.distinct().forEach { n ->
            val bmp = ed.agentRenderPage(n - 1, 1600) ?: return@forEach
            content.add(Block.Text("[第${n}页]"))
            content.add(savePng(attachDir, bmp, "ref"))
        }
        if (text.isNotEmpty()) content.add(Block.Text(text))
        if (content.isEmpty()) return false
        attachments.clear(); renderTray()
        val msg = UserInput(content)
        if (agent.running) agent.steer(msg) else agent.followup(msg)
        return true
    }

    fun addAttachmentPng(bytes: ByteArray) {
        ui.post {
            attachments.add(savePngBytes(attachDir, bytes, "sel"))
            renderTray()
            if (collapsed) updateCollapsed(false)
        }
    }

    private fun renderTray() { panel.setAttachments(attachments.map { it.path }) }

    // ---------------- sessions ----------------
    private fun updateHeader() {
        panel.setTitle(agent.session.title)
        panel.setModel(settings.label())
    }

    private fun switchTo(s: Session) {
        if (agent.running) { toast("先停止当前回答"); return }
        agent.session = s; settings.sessionId = s.id
        panel.replay(s); updateHeader(); pushStats()
    }

    // ---------------- model selection (dsh ui-model-selection) ----------------
    private fun dp(v: Int) = (v * activity.resources.displayMetrics.density).toInt()

    private fun modelDialog() {
        val providers = settings.providers
        if (providers.isEmpty()) { openSettings(); return }
        val form = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(8)) }
        fun label(t: String) = TextView(activity).apply { text = t; setTextColor(Color.GRAY); setPadding(0, dp(8), 0, 0) }
        val pSpin = Spinner(activity).apply { adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, providers.map { it.name }) }
        val modelEdit = AutoCompleteTextView(activity).apply { threshold = 0; setText(settings.model); hint = "模型 ID" }
        val effSpin = Spinner(activity)
        val maxTok = EditText(activity).apply { inputType = InputType.TYPE_CLASS_NUMBER; hint = "留空使用提供商默认值"; setText(settings.maxTokens?.toString() ?: "") }
        var effIds = listOf<String>()
        fun refresh(p: Provider, keepModel: Boolean) {
            modelEdit.setAdapter(ArrayAdapter(activity, android.R.layout.simple_dropdown_item_1line, p.models))
            if (!keepModel) modelEdit.setText(p.models.firstOrNull() ?: "")
            effIds = p.efforts()
            effSpin.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, effIds.map { Settings.EFFORT_LABEL[it] ?: it })
            effSpin.setSelection(effIds.indexOf(settings.effort).coerceAtLeast(0))
        }
        val startIdx = providers.indexOfFirst { it.id == settings.providerId }.coerceAtLeast(0)
        pSpin.setSelection(startIdx)
        refresh(providers[startIdx], true)
        pSpin.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            var first = true
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { refresh(providers[pos], first); first = false }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        modelEdit.setOnClickListener { modelEdit.showDropDown() }
        form.addView(label("服务商")); form.addView(pSpin)
        form.addView(label("模型")); form.addView(modelEdit)
        form.addView(label("推理强度")); form.addView(effSpin)
        form.addView(label("最大输出 token 数")); form.addView(maxTok)
        AlertDialog.Builder(activity).setTitle("选择模型").setView(form)
            .setPositiveButton("使用") { _, _ ->
                val p = providers[pSpin.selectedItemPosition]
                settings.providerId = p.id
                settings.model = modelEdit.text.toString().trim()
                settings.effort = effIds.getOrNull(effSpin.selectedItemPosition) ?: p.efforts().first()
                settings.maxTokens = maxTok.text.toString().toIntOrNull()
                updateHeader(); pushStats()
            }
            .setNeutralButton("管理服务商") { _, _ -> openSettings() }
            .setNegativeButton("取消", null).show()
    }

    // ---------------- settings screen (dsh ui-settings-models port, WebView) ----------------
    private var settingsDialog: Dialog? = null

    private fun stateJson(): String {
        val ps = JSONArray()
        settings.providers.forEach { p ->
            ps.put(JSONObject().put("id", p.id).put("name", p.name).put("protocol", p.protocol.name).put("baseUrl", p.baseUrl)
                .put("keySet", p.apiKey.isNotBlank()).put("keyMasked", maskKey(p.apiKey)).put("models", JSONArray(p.models))
                .put("efforts", JSONArray(p.efforts())).put("status", settings.providerStatus(p.id)))
        }
        val labels = JSONObject(); Settings.EFFORT_LABEL.forEach { (k, v) -> labels.put(k, v) }
        return JSONObject().put("providers", ps).put("providerId", settings.providerId).put("model", settings.model)
            .put("effort", settings.effort).put("maxTokens", settings.maxTokens ?: JSONObject.NULL).put("effortLabels", labels)
            .put("transcriptMode", settings.transcriptMode).put("collapseTiming", settings.collapseTiming)
            .put("systemPrompt", settings.systemPrompt).put("tavilySet", settings.tavilyKey.isNotBlank()).put("tavilyMasked", maskKey(settings.tavilyKey))
            .put("pricing", settings.pricingJson).put("defaultPricing", Settings.DEFAULT_PRICING).toString()
    }

    private fun providerFrom(o: JSONObject): Provider {
        val old = settings.providers.firstOrNull { it.id == o.optString("id") }
        val a = o.optJSONArray("models")
        return Provider(
            old?.id ?: ("p" + System.currentTimeMillis().toString(36)),
            o.optString("name").trim().ifEmpty { o.optString("baseUrl").substringAfter("://").substringBefore('/') },
            runCatching { Protocol.valueOf(o.optString("protocol")) }.getOrDefault(Protocol.OPENAI_CHAT),
            o.optString("baseUrl").trim(),
            o.optString("newKey").trim().ifEmpty { old?.apiKey ?: "" },
            a?.let { (0 until it.length()).map { i -> it.getString(i) } } ?: old?.models ?: emptyList(),
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun openSettings() {
        if (settingsDialog?.isShowing == true) return
        val web = WebView(activity)
        web.settings.javaScriptEnabled = true
        web.settings.textZoom = 100
        web.webChromeClient = WebChromeClient()
        web.addJavascriptInterface(object {
            @JavascriptInterface fun getState(): String = onMain { stateJson() }
            @JavascriptInterface fun defaultModels(id: String): String = JSONArray(Settings.defaultModels(id)).toString()
            @JavascriptInterface fun saveProvider(json: String): String = onMain {
                runCatching {
                    val p = providerFrom(JSONObject(json))
                    if (!p.baseUrl.startsWith("http")) return@onMain "请输入合法的 HTTP 或 HTTPS 地址。"
                    val list = settings.providers.toMutableList()
                    val i = list.indexOfFirst { it.id == p.id }
                    if (i >= 0) list[i] = p else list.add(p)
                    settings.providers = list
                    if (settings.provider()?.id == p.id && settings.model.isBlank()) settings.model = p.models.firstOrNull() ?: ""
                    updateHeader()
                    Thread { runCatching { Llm.listModels(p); settings.setProviderStatus(p.id, "ok") }.onFailure { settings.setProviderStatus(p.id, "error") } }.start()
                    "ok"
                }.getOrElse { "保存失败：${it.message}" }
            }
            @JavascriptInterface fun deleteProvider(id: String) { onMain { settings.providers = settings.providers.filter { it.id != id }; updateHeader() } }
            @JavascriptInterface fun fetchModels(json: String, cb: String) {
                val p = onMain { providerFrom(JSONObject(json)) }
                Thread {
                    val r = runCatching { Llm.listModels(p) }
                    if (settings.providers.any { it.id == p.id }) settings.setProviderStatus(p.id, if (r.isSuccess) "ok" else "error")
                    ui.post {
                        val err = r.exceptionOrNull()?.let { JSONObject.quote(it.message ?: "失败") } ?: "null"
                        val data = r.getOrNull()?.let { JSONObject.quote(JSONArray(it).toString()) } ?: "null"
                        web.evaluateJavascript("onModels(${JSONObject.quote(cb)}, $err, $data)", null)
                    }
                }.start()
            }
            @JavascriptInterface fun saveDefault(json: String): String = onMain {
                val o = JSONObject(json)
                settings.providerId = o.optString("providerId", settings.providerId)
                settings.model = o.optString("model", settings.model)
                settings.effort = o.optString("effort", settings.effort)
                settings.maxTokens = o.optString("maxTokens").toIntOrNull()
                updateHeader(); pushStats(); "ok"
            }
            @JavascriptInterface fun saveGeneral(json: String): String = onMain {
                val o = JSONObject(json)
                if (o.has("pricing")) {
                    if (runCatching { JSONObject(o.getString("pricing")) }.isFailure) return@onMain "价格表不是合法的 JSON"
                    settings.pricingJson = o.getString("pricing")
                }
                if (o.has("transcriptMode")) settings.transcriptMode = o.getString("transcriptMode")
                if (o.has("collapseTiming")) settings.collapseTiming = o.getString("collapseTiming")
                if (o.has("systemPrompt")) settings.systemPrompt = o.getString("systemPrompt")
                if (o.has("tavilyKey")) settings.tavilyKey = o.getString("tavilyKey").trim()
                if (o.has("transcriptMode") || o.has("collapseTiming")) {
                    panel.setPolicy(settings.transcriptMode, settings.collapseTiming); panel.replay(agent.session)
                }
                pushStats(); "ok"
            }
            @JavascriptInterface fun close() { ui.post { settingsDialog?.dismiss() } }
        }, "Settings")
        web.loadUrl("file:///android_asset/settings.html")
        val d = Dialog(activity, android.R.style.Theme_Material_Light_NoActionBar)
        d.setContentView(web, ViewGroup.LayoutParams(-1, -1))
        d.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        d.setOnDismissListener { settingsDialog = null; updateHeader(); web.destroy() }
        settingsDialog = d
        d.show()
    }

    // Runs last: every property above (host, listener, …) is initialised by now.
    init {
        panel = AgentPanel(activity, host)
        CanvasTools(editorRef, raster, attachDir, registry).register(tools) { settings.tavilyKey }
        ProviderTools(settings, ::confirmBlocking) { ui.post { updateHeader() } }.register(tools)
        val session = settings.sessionId?.let { store.open(it) } ?: store.create().also { settings.sessionId = it.id }
        agent = Agent(session, tools, { settings.callConfig() }, { systemPrompt() }, listener) { m, u -> settings.cost(m, u) }
        panel.setPolicy(settings.transcriptMode, settings.collapseTiming)
        panel.replay(session)
        updateHeader(); pushStats()
        if (settings.provider()?.apiKey.isNullOrBlank()) panel.notice("先点右上角设置，给服务商填 API 密钥（默认 DeepSeek）。也可以把密钥和 API 地址直接发给我，我来帮你配置。")
        AgentBridge.controller = this
    }

    // ---------------- debug hooks for tests/e2e.py ----------------
    fun applyTestExtras(i: android.content.Intent?) {
        if (activity.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        i ?: return
        i.getStringExtra("test_key")?.let { key ->
            settings.providers = settings.providers.map { if (it.id == "deepseek") it.copy(apiKey = key) else it }
            settings.providerId = "deepseek"
        }
        i.getStringExtra("test_model")?.let { settings.model = it }
        i.getStringExtra("test_effort")?.let { settings.effort = it }
        i.getStringExtra("test_mode")?.let { settings.transcriptMode = it; panel.setPolicy(it, settings.collapseTiming); panel.replay(agent.session) }
        if (i.hasExtra("test_collapsed")) updateCollapsed(i.getBooleanExtra("test_collapsed", false))
        if (i.getBooleanExtra("test_settings", false)) ui.postDelayed({ openSettings() }, 500)
        if (i.getBooleanExtra("test_sessions", false)) ui.postDelayed({ panel.openSessions() }, 500)
        updateHeader(); pushStats()
    }

    /** Debug (TEST_STATE): ask agent.html for its state and log it as one JSON line under [tag]. */
    fun logTestState(tag: String) {
        val js = "(function(){var s=document.getElementById('side');var l=document.getElementById('log');" +
            "return JSON.stringify({jsVisible:window.innerWidth>0&&window.innerHeight>0&&document.visibilityState==='visible'," +
            "sidebarOpen:!!(s&&s.classList.contains('open')),messageCount:l?l.querySelectorAll('.user,.md').length:0," +
            "userCount:l?l.querySelectorAll('.user').length:0,turnCount:l?l.querySelectorAll('.turn').length:0,innerWidth:window.innerWidth});})()"
        panel.evalForTest(js) { raw ->
            val o = runCatching { JSONObject(JSONArray("[$raw]").getString(0)) }.getOrElse { JSONObject().put("raw", raw) }
            val v = panel.view
            val visible = !collapsed && v.isShown && v.width > 0 && v.height > 0 && o.optBoolean("jsVisible", false)
            o.put("panelVisible", visible).put("collapsed", collapsed).put("viewWidth", v.width).put("viewHeight", v.height)
            android.util.Log.i(tag, o.toString())
        }
    }

    private fun toast(s: String) = Toast.makeText(activity, s, Toast.LENGTH_SHORT).show()
}
