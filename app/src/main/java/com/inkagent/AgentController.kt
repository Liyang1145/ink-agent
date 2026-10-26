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
import androidx.compose.runtime.mutableFloatStateOf
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
    /** The in-app home of the panel WebView (AgentShell composes this); the overlay window borrows the WebView. */
    val panelSlot = FrameLayout(activity)
    /** True while the panel WebView lives in the cross-app overlay window (OverlayService). */
    var overlayActive = false
        private set
    private val tools = ToolRegistry()
    private val registry = AiRegistry(File(activity.filesDir, "ai_items.json"))
    lateinit var agent: Agent
        private set
    private val attachments = ArrayList<Block.Image>()

    /** Compose-observed agent window state (see AgentShell): mode = ball | window | split. */
    var mode by mutableStateOf(settings.agentMode.takeIf { it in MODES } ?: "window")
        private set
    val collapsed: Boolean get() = mode == "ball"
    /** Floating window geometry in dp (-1 = not placed yet; AgentShell picks a default). */
    var winX by mutableFloatStateOf(settings.winX)
    var winY by mutableFloatStateOf(settings.winY)
    var winW by mutableFloatStateOf(settings.winW)
    var winH by mutableFloatStateOf(settings.winH)
    var ballRight by mutableStateOf(settings.ballRight)
    var ballY by mutableFloatStateOf(settings.ballY)
    var splitLand by mutableFloatStateOf(settings.splitLand)
    var splitPort by mutableFloatStateOf(settings.splitPort)
    /** Debug/test: last laid-out sizes (px) reported by AgentShell. */
    var notesSizePx = intArrayOf(0, 0)
    var panelRectPx = intArrayOf(0, 0, 0, 0)

    private class PendingConfirm(val latch: CountDownLatch) { @Volatile var ok = false }
    private val confirms = HashMap<String, PendingConfirm>()


    private var canvasTools: CanvasTools? = null

    private fun systemPrompt(): String {
        val user = settings.systemPrompt
        val env = canvasTools?.envPrompt() ?: "你运行在平板笔记应用 InkAgent 里，可以用工具查看和修改用户当前打开的笔记页面（view_page、list_pages、canvas_plot、canvas_write_latex、canvas_draw_svg、canvas_remove），以及管理模型服务商（manage_provider）。用户消息里的 @N 表示引用第 N 页。"
        return if (user.isBlank()) env else "$user\n\n$env"
    }

    // ---------------- panel placement (read by AgentShell) ----------------
    fun changeMode(m: String) {
        if (m !in MODES || m == mode) return
        mode = m; settings.agentMode = m
        panel.setWindowMode(m)
    }
    /** Minimise to the ball, or reopen as the floating window. */
    fun updateCollapsed(b: Boolean) = changeMode(if (b) "ball" else if (mode == "ball") "window" else mode)
    fun commitWindow() { settings.winX = winX; settings.winY = winY; settings.winW = winW; settings.winH = winH }
    fun commitBall() { settings.ballRight = ballRight; settings.ballY = ballY }
    fun commitSplit() { settings.splitLand = splitLand; settings.splitPort = splitPort }

    // ---------------- cross-app overlay (OverlayService) ----------------
    /** Move the one panel WebView (same page, same session) into the overlay window. */
    fun attachPanelToOverlay(parent: ViewGroup) {
        val v = panel.view
        (v.parent as? ViewGroup)?.removeView(v)
        parent.addView(v, ViewGroup.LayoutParams(-1, -1))
        overlayActive = true
        panel.setWindowMode("overlay")
    }

    /** Give the panel WebView back to the app (its slot in AgentShell). */
    fun returnPanelFromOverlay() {
        if (!overlayActive) return
        val v = panel.view
        (v.parent as? ViewGroup)?.removeView(v)
        panelSlot.addView(v, ViewGroup.LayoutParams(-1, -1))
        overlayActive = false
        panel.setWindowMode(mode)
    }

    /** Settings toggle 「在其他应用上显示悬浮球」. Returns "ok", or "need_permission" when the system screen was opened. */
    fun setOverlayEnabled(on: Boolean): String {
        settings.overlayEnabled = on
        if (!on) { com.inkagent.overlay.OverlayService.stop(activity); return "ok" }
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            activity.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 7301)
        if (!com.inkagent.overlay.OverlayService.canDraw(activity)) {
            runCatching {
                activity.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:" + activity.packageName)))
            }
            return "need_permission"
        }
        com.inkagent.overlay.OverlayService.start(activity)
        return "ok"
    }

    /** MainActivity.onResume: (re)start the overlay service when enabled and permitted (e.g. back from the permission screen). */
    fun onActivityResumed() {
        if (settings.overlayEnabled && com.inkagent.overlay.OverlayService.canDraw(activity)) com.inkagent.overlay.OverlayService.start(activity)
        settingsWeb?.evaluateJavascript("(function(){try{var n=JSON.parse(Settings.getState());if(S){S.overlayEnabled=n.overlayEnabled;S.overlayPermission=n.overlayPermission;syncOverlay();}}catch(e){}})()", null)
    }

    private fun openAppThen(action: (() -> Unit)?) {
        com.inkagent.overlay.OverlayService.instance?.openApp()
        if (action != null) ui.postDelayed({ action() }, 800)
    }

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
        override fun onEditAttachment(index: Int) = openImageEditor(index)
        override fun onOpenModel() { if (overlayActive) openAppThen { modelDialog() } else modelDialog() }
        override fun onOpenSessions() { panel.refreshSessions() }
        override fun onNewSession() = switchTo(store.create())
        override fun onOpenSettings() { if (overlayActive) openAppThen { openSettings() } else openSettings() }
        override fun onStop() = agent.cancel()
        override fun currentPage() = (editorRef()?.agentCurrentPage() ?: 0) + 1
        override fun onCollapse() {
            if (overlayActive) com.inkagent.overlay.OverlayService.instance?.collapseWindow() else updateCollapsed(true)
        }
        override fun onToggleMode() {
            if (overlayActive) openAppThen(null) else changeMode(if (mode == "split") "window" else "split")
        }
        override fun onCapture() { com.inkagent.overlay.OverlayService.instance?.startCapture() ?: toast("请先在设置里开启「在其他应用上显示悬浮球」") }
        override fun onInputFocus(focused: Boolean) { com.inkagent.overlay.OverlayService.instance?.onInputFocus(focused) }
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
        ui.post { if (collapsed && !overlayActive) updateCollapsed(false); panel.showConfirm(id, title, lines) }
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
            if (collapsed && !overlayActive) updateCollapsed(false)
        }
    }

    private fun renderTray() { panel.setAttachments(attachments.map { it.path }) }

    // ---------------- attachment image editor (QQ-style) ----------------
    private var editorView: com.inkagent.ui.ImageEditorView? = null
    private var editorInOverlay = false
    private var editorBack: androidx.activity.OnBackPressedCallback? = null
    var lastClipUri = ""; private set
    var lastSavedUri = ""; private set

    /** Single tap on a tray thumbnail: open that image fullscreen in the editor (in the app, or over other apps). */
    fun openImageEditor(index: Int) {
        if (editorView != null) return
        val target = attachments.getOrNull(index) ?: return
        val bmp = runCatching { android.graphics.BitmapFactory.decodeFile(target.path) }.getOrNull()
        if (bmp == null) { toast("无法打开这张图片"); return }
        val inOverlay = overlayActive && com.inkagent.overlay.OverlayService.instance != null
        val ctx: android.content.Context = if (inOverlay) com.inkagent.overlay.OverlayService.instance!!.uiContext() else activity
        lateinit var ev: com.inkagent.ui.ImageEditorView
        ev = com.inkagent.ui.ImageEditorView(ctx, bmp, object : com.inkagent.ui.ImageEditorView.Host {
            override fun onReplace(png: ByteArray) {
                val img = savePngBytes(attachDir, png, "edit")
                val i = attachments.indexOf(target)
                if (i >= 0) attachments[i] = img else attachments.add(img)
                renderTray(); closeImageEditor(); toast("已替换原附件")
            }
            override fun onAddNew(png: ByteArray) {
                attachments.add(savePngBytes(attachDir, png, "edit"))
                renderTray(); closeImageEditor(); toast("已添加为新附件")
            }
            override fun onCopy(png: ByteArray): String = copyImageToClipboard(png)
            override fun onSaveToGallery(png: ByteArray): String = saveImageToGallery(png)
            override fun onClose() = closeImageEditor()
            override fun toast(s: String) = this@AgentController.toast(s)
        })
        editorView = ev; editorInOverlay = inOverlay
        if (inOverlay) {
            com.inkagent.overlay.OverlayService.instance!!.showEditor(ev)
        } else {
            val decor = activity.window.decorView as ViewGroup
            decor.addView(ev, ViewGroup.LayoutParams(-1, -1))
            (activity as? androidx.activity.ComponentActivity)?.let { ca ->
                val cb = object : androidx.activity.OnBackPressedCallback(true) { override fun handleOnBackPressed() { editorView?.handleBack() } }
                ca.onBackPressedDispatcher.addCallback(cb); editorBack = cb
            }
        }
    }

    fun closeImageEditor() {
        val ev = editorView ?: return
        editorView = null
        editorBack?.remove(); editorBack = null
        if (editorInOverlay) com.inkagent.overlay.OverlayService.instance?.removeEditor(ev)
        (ev.parent as? ViewGroup)?.removeView(ev)
    }

    private fun copyImageToClipboard(png: ByteArray): String {
        val dir = File(activity.cacheDir, "clipboard").apply { mkdirs() }
        dir.listFiles()?.filter { it.name.startsWith("edit-") }?.forEach { it.delete() }
        val f = File(dir, "edit-${System.currentTimeMillis()}.png").apply { writeBytes(png) }
        val uri = androidx.core.content.FileProvider.getUriForFile(activity, activity.packageName + ".fileprovider", f)
        val cm = activity.getSystemService(android.content.ClipboardManager::class.java)
        cm.setPrimaryClip(android.content.ClipData.newUri(activity.contentResolver, "InkAgent 图片", uri))
        lastClipUri = uri.toString()
        return "已复制到剪贴板"
    }

    private fun saveImageToGallery(png: ByteArray): String {
        val name = "InkAgent_" + java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date()) + ".png"
        val cr = activity.contentResolver
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val cv = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name)
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/InkAgent")
                put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = cr.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv) ?: return "保存失败"
            cr.openOutputStream(uri)?.use { it.write(png) } ?: return "保存失败"
            cv.clear(); cv.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
            cr.update(uri, cv, null, null)
            lastSavedUri = uri.toString()
        } else {
            if (activity.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                if (!overlayActive) activity.requestPermissions(arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 7302)
                return "需要存储权限，授权后再点一次「保存到相册」"
            }
            @Suppress("DEPRECATION")
            val dir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES), "InkAgent").apply { mkdirs() }
            val f = File(dir, name).apply { writeBytes(png) }
            android.media.MediaScannerConnection.scanFile(activity, arrayOf(f.path), arrayOf("image/png"), null)
            lastSavedUri = f.path
        }
        return "已保存到相册（Pictures/InkAgent）"
    }

    /** Debug (TEST_INPUT add_test_attachment): a small synthetic image in the tray. */
    fun addTestAttachment() {
        val b = android.graphics.Bitmap.createBitmap(640, 400, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(b); c.drawColor(Color.WHITE)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF333333.toInt(); textSize = 48f }
        c.drawText("x² − 4 = 0", 60f, 140f, p)
        p.style = android.graphics.Paint.Style.STROKE; p.strokeWidth = 4f; c.drawLine(60f, 200f, 560f, 200f, p)
        attachments.add(savePng(attachDir, b, "test")); b.recycle(); renderTray()
    }

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
    private var settingsWeb: WebView? = null

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
            .put("pricing", settings.pricingJson).put("defaultPricing", Settings.DEFAULT_PRICING)
            .put("overlayEnabled", settings.overlayEnabled).put("overlayPermission", com.inkagent.overlay.OverlayService.canDraw(activity)).toString()
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
            @JavascriptInterface fun setOverlay(on: Boolean): String = onMain { setOverlayEnabled(on) }
            @JavascriptInterface fun close() { ui.post { settingsDialog?.dismiss() } }
        }, "Settings")
        web.loadUrl("file:///android_asset/settings.html")
        val d = Dialog(activity, android.R.style.Theme_Material_Light_NoActionBar)
        d.setContentView(web, ViewGroup.LayoutParams(-1, -1))
        d.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        d.setOnDismissListener { settingsDialog = null; settingsWeb = null; updateHeader(); web.destroy() }
        settingsWeb = web
        settingsDialog = d
        d.show()
    }

    // Runs last: every property above (host, listener, …) is initialised by now.
    init {
        panel = AgentPanel(activity, host)
        panelSlot.addView(panel.view, ViewGroup.LayoutParams(-1, -1))
        canvasTools = CanvasTools(editorRef, raster, attachDir, registry).also { it.register(tools) { settings.tavilyKey } }
        ProviderTools(settings, ::confirmBlocking) { ui.post { updateHeader() } }.register(tools)
        val session = settings.sessionId?.let { store.open(it) } ?: store.create().also { settings.sessionId = it.id }
        agent = Agent(session, tools, { settings.callConfig() }, { systemPrompt() }, listener) { m, u -> settings.cost(m, u) }
        panel.setPolicy(settings.transcriptMode, settings.collapseTiming)
        panel.setWindowMode(mode)
        panel.replay(session)
        updateHeader(); pushStats()
        if (settings.provider()?.apiKey.isNullOrBlank()) panel.notice("先点右上角设置，给服务商填 API 密钥（默认 DeepSeek）。也可以把密钥和 API 地址直接发给我，我来帮你配置。")
        AgentBridge.controller = this
        onActivityResumed()
    }

    // ---------------- debug hooks for tests/e2e.py ----------------
    fun applyTestExtras(i: android.content.Intent?) {
        if (activity.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        i ?: return
        val testBase = i.getStringExtra("test_base")
        i.getStringExtra("test_key")?.let { key ->
            if (testBase != null) {
                // e2e: an OpenAI-compatible gateway (e.g. ClinePass) as its own provider
                val p = com.inkagent.llm.Provider("test", i.getStringExtra("test_provider") ?: "Test", com.inkagent.llm.Protocol.OPENAI_CHAT,
                    testBase, key, listOfNotNull(i.getStringExtra("test_model")))
                settings.providers = settings.providers.filter { it.id != "test" } + p
                settings.providerId = "test"
            } else {
                settings.providers = settings.providers.map { if (it.id == "deepseek") it.copy(apiKey = key) else it }
                settings.providerId = "deepseek"
            }
        }
        i.getStringExtra("test_model")?.let { settings.model = it }
        i.getStringExtra("test_effort")?.let { settings.effort = it }
        i.getStringExtra("test_mode")?.let { settings.transcriptMode = it; panel.setPolicy(it, settings.collapseTiming); panel.replay(agent.session) }
        if (i.hasExtra("test_collapsed")) updateCollapsed(i.getBooleanExtra("test_collapsed", false))
        i.getStringExtra("test_agent_mode")?.let { changeMode(it) }
        if (i.hasExtra("test_overlay")) setOverlayEnabled(i.getBooleanExtra("test_overlay", false))
        if (i.getBooleanExtra("test_settings", false)) ui.postDelayed({ openSettings() }, 500)
        if (i.getBooleanExtra("test_sessions", false)) ui.postDelayed({ panel.openSessions() }, 500)
        updateHeader(); pushStats()
    }

    /** Debug (TEST_STATE): ask agent.html for its state and log it as one JSON line under [tag]. */
    fun logTestState(tag: String, slim: Boolean = false) {
        val js = "(function(){var s=document.getElementById('side');var l=document.getElementById('log');" +
            "return JSON.stringify({jsVisible:window.innerWidth>0&&window.innerHeight>0&&document.visibilityState==='visible'," +
            "sidebarOpen:!!(s&&s.classList.contains('open')),messageCount:l?l.querySelectorAll('.user,.md').length:0," +
            "userCount:l?l.querySelectorAll('.user').length:0,turnCount:l?l.querySelectorAll('.turn').length:0,innerWidth:window.innerWidth," +
            "layout:(typeof layoutState==='function'?layoutState():null),trayCount:document.querySelectorAll('#tray .att').length," +
            "dataMode:document.body.getAttribute('data-mode'),rects:(function(){var o={};['input','modeBtn','capBtn','send'].forEach(function(id){var e=document.getElementById(id);" +
            "if(e){var r=e.getBoundingClientRect();o[id]=[r.left,r.top,r.width,r.height,getComputedStyle(e).display!=='none'&&r.width>0];}});return o;})()," +
            "trayRects:Array.prototype.map.call(document.querySelectorAll('#tray .att img'),function(e){var r=e.getBoundingClientRect();return [r.left,r.top,r.width,r.height];})});})()"
        panel.evalForTest(js) { raw ->
            val o = runCatching { JSONObject(JSONArray("[$raw]").getString(0)) }.getOrElse { JSONObject().put("raw", raw) }
            val v = panel.view
            val visible = (overlayActive || !collapsed) && v.isShown && v.width > 0 && v.height > 0 && o.optBoolean("jsVisible", false)
            o.put("panelVisible", visible).put("collapsed", collapsed).put("viewWidth", v.width).put("viewHeight", v.height)
            o.put("mode", mode).put("notesWidth", notesSizePx[0]).put("notesHeight", notesSizePx[1])
                .put("panelX", panelRectPx[0]).put("panelY", panelRectPx[1]).put("panelW", panelRectPx[2]).put("panelH", panelRectPx[3])
                .put("splitLand", splitLand.toDouble()).put("splitPort", splitPort.toDouble())
            val loc = IntArray(2); v.getLocationOnScreen(loc)
            o.put("overlayActive", overlayActive).put("attachments", attachments.size).put("viewScreenX", loc[0]).put("viewScreenY", loc[1])
                .put("density", activity.resources.displayMetrics.density.toDouble())
            com.inkagent.overlay.OverlayService.instance?.let { o.put("overlay", it.debugJson()) }
            val ai = JSONArray()
            attachments.forEach { a ->
                val f = File(a.path)
                val md5 = runCatching { java.security.MessageDigest.getInstance("MD5").digest(f.readBytes()).joinToString("") { "%02x".format(it) } }.getOrDefault("")
                ai.put(JSONObject().put("name", f.name).put("size", f.length()).put("w", a.width).put("h", a.height).put("md5", md5))
            }
            o.put("attachInfo", ai)
            o.put("editor", editorView?.debugJson()?.put("host", if (editorInOverlay) "overlay" else "app") ?: JSONObject().put("open", false))
            o.put("lastClipUri", lastClipUri).put("lastSavedUri", lastSavedUri)
            runCatching {
                val clip = activity.getSystemService(android.content.ClipboardManager::class.java).primaryClip
                if (clip != null && clip.itemCount > 0) o.put("clip", JSONObject().put("mime", clip.description.getMimeType(0)).put("uri", clip.getItemAt(0).uri?.toString() ?: ""))
            }
            if (!slim) runCatching {
                val ed = editorRef()
                if (ed != null) o.put("selectRect", ed.selectRect).put("tool", ed.tool.name)
                if (ed != null && ed.noteOpen) {
                    val idx = ed.agentCurrentPage()
                    val page = ed.pageAt(idx)
                    if (page != null) {
                        val items = JSONArray()
                        page.items.forEach { it2 ->
                            val b = it2.bounds()
                            items.put(JSONObject().put("kind", it2.kind).put("x", b.x).put("y", b.y).put("w", b.w).put("h", b.h))
                        }
                        o.put("page", JSONObject().put("index", idx).put("width", page.width).put("height", page.height).put("items", items))
                    }
                }
            }
            android.util.Log.i(tag, o.toString())
        }
    }

    companion object { val MODES = setOf("ball", "window", "split") }

    private fun toast(s: String) = Toast.makeText(activity, s, Toast.LENGTH_SHORT).show()
}
