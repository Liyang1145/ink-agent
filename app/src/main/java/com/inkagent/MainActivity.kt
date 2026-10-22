package com.inkagent

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import com.inkagent.agent.*
import com.inkagent.llm.*
import com.inkagent.note.Notebook
import com.inkagent.note.PageView
import com.inkagent.ui.AgentPanel
import com.inkagent.ui.Rasterizer
import java.io.File

class MainActivity : Activity() {

    private lateinit var settings: Settings
    private lateinit var notebook: Notebook
    private lateinit var pageView: PageView
    private lateinit var raster: Rasterizer
    private lateinit var panel: AgentPanel
    private lateinit var store: SessionStore
    private lateinit var agent: Agent
    private val tools = ToolRegistry()

    private lateinit var aiToggle: Button
    private val toolButtons = HashMap<PageView.Tool, Button>()

    private val attachments = ArrayList<Block.Image>()
    private val ui = Handler(Looper.getMainLooper())
    private val attachDir by lazy { File(filesDir, "attachments") }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        notebook = Notebook(File(filesDir, "notebook"))
        store = SessionStore(File(filesDir, "sessions"))

        val root = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        // ================= left: canvas =================
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(4), dp(6), dp(4)); setBackgroundColor(Color.rgb(245, 246, 248))
        }
        fun btn(label: String, onClick: () -> Unit) = Button(this).apply {
            text = label; isAllCaps = false; minWidth = 0; minimumWidth = 0; setPadding(dp(10), 0, dp(10), 0)
            setOnClickListener { onClick() }
        }
        fun toolBtn(label: String, t: PageView.Tool) = btn(label) { selectTool(t) }.also { toolButtons[t] = it }
        bar.addView(toolBtn("✏️ 笔", PageView.Tool.PEN))
        bar.addView(toolBtn("🧽 橡皮", PageView.Tool.ERASER))
        bar.addView(toolBtn("⭕ 圈选", PageView.Tool.LASSO))
        bar.addView(btn("↶") { pageView.undo() })
        bar.addView(btn("◀") { pageView.goTo(pageView.pageIndex - 1) })
        bar.addView(btn("▶") { pageView.goTo(pageView.pageIndex + 1) })
        bar.addView(btn("＋页") { pageView.addPage() })
        bar.addView(btn("⤓加长") { pageView.extend(true) })
        bar.addView(btn("⤑加宽") { pageView.extend(false) })
        aiToggle = btn("AI层:显示") { pageView.showAi = !pageView.showAi; updateAiToggle() }
        bar.addView(aiToggle)
        bar.addView(btn("撤AI") { pageView.undoLastAi() })
        bar.addView(btn("更多") { moreMenu() })
        bar.addView(CheckBox(this).apply {
            text = "手指书写"; isChecked = settings.fingerDraw
            setOnCheckedChangeListener { _, c -> pageView.fingerCanDraw = c; settings.fingerDraw = c }
        })
        left.addView(HorizontalScrollView(this).apply { addView(bar) }, LinearLayout.LayoutParams(-1, -2))

        val canvasFrame = FrameLayout(this)
        raster = Rasterizer(this)
        canvasFrame.addView(raster.view, FrameLayout.LayoutParams(dp(900), dp(900)))
        pageView = PageView(this, notebook)
        pageView.fingerCanDraw = settings.fingerDraw
        pageView.onLassoSelected = { addAttachment(it) }
        pageView.onContentChanged = { scheduleSave() }
        canvasFrame.addView(pageView, FrameLayout.LayoutParams(-1, -1))
        left.addView(canvasFrame, LinearLayout.LayoutParams(-1, 0, 1f))

        // ================= right: agent panel (dsh-style WebView) =================
        panel = AgentPanel(this, panelHost)
        val right = panel.view
        root.addView(left, LinearLayout.LayoutParams(0, -1, 3f))
        root.addView(View(this).apply { setBackgroundColor(Color.rgb(220, 222, 228)) }, LinearLayout.LayoutParams(dp(1), -1))
        root.addView(right, LinearLayout.LayoutParams(0, -1, 2f))
        setContentView(root)

        // ================= agent =================
        CanvasTools(pageView, notebook, raster, attachDir).register(tools) { settings.tavilyKey }
        val session = settings.sessionId?.let { store.open(it) } ?: store.create().also { settings.sessionId = it.id }
        agent = Agent(session, tools, { settings.callConfig() }, { settings.systemPrompt }, listener)
        panel.setPolicy(settings.transcriptMode, settings.collapseTiming)
        panel.replay(session)

        selectTool(PageView.Tool.PEN)
        updateAiToggle(); updateHeader()
        pageView.post { pageView.goTo(0) }
        applyTestExtras()
        if (settings.provider()?.apiKey.isNullOrBlank()) {
            panel.notice("先点右上角设置，给服务商填 API Key（默认 DeepSeek）。")
        }
    }

    // ---------------- agent events → UI ----------------
    private val listener = object : AgentListener {
        override fun onStatus(running: Boolean) { ui.post { panel.setRunning(running); updateHeader() } }
        override fun onChunk(chunk: Chunk) { ui.post { panel.chunk(chunk) } }
        override fun onEvent(ev: SessionEvent) {
            ui.post { panel.event(ev, true); if (ev.type == "user/message") updateHeader() }
        }
    }

    private val panelHost = object : AgentPanel.Host {
        override fun onSend(text: String) = send(text)
        override fun onRemoveAttachment(index: Int) {
            if (index in attachments.indices) { attachments.removeAt(index); renderTray() }
        }
        override fun onOpenModel() = modelDialog()
        override fun onOpenSessions() = sessionsDialog()
        override fun onNewSession() = newSession()
        override fun onOpenSettings() = settingsDialog()
        override fun onStop() = agent.cancel()
        override fun currentPage() = pageView.pageIndex + 1
    }

    // ---------------- sending ----------------
    private fun send(raw: String): Boolean {
        val text = raw.trim()
        val content = ArrayList<Block>()
        content.addAll(attachments)
        // @N page references → attach a render of that page (dsh-style reference)
        Regex("@(\\d+)").findAll(text).map { it.groupValues[1].toInt() }.distinct().forEach { n ->
            val p = notebook.pages.getOrNull(n - 1) ?: return@forEach
            val bmp = Notebook.render(p, RectF(0f, 0f, p.width, p.height), maxSide = 1600)
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

    private fun addAttachment(bmp: Bitmap) {
        attachments.add(savePng(attachDir, bmp, "lasso"))
        renderTray()
    }

    private fun renderTray() { panel.setAttachments(attachments.map { it.path }) }

    // ---------------- canvas helpers ----------------
    private fun selectTool(t: PageView.Tool) {
        pageView.tool = t
        toolButtons.forEach { (k, b) ->
            b.setTextColor(if (k == t) Color.rgb(30, 120, 255) else Color.DKGRAY)
            b.setTypeface(null, if (k == t) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    private fun updateAiToggle() { aiToggle.text = if (pageView.showAi) "AI层:显示" else "AI层:隐藏" }

    private val saveRunnable = Runnable { notebook.save() }
    private fun scheduleSave() { ui.removeCallbacks(saveRunnable); ui.postDelayed(saveRunnable, 800) }

    override fun onPause() { super.onPause(); ui.removeCallbacks(saveRunnable); notebook.save() }

    private fun moreMenu() {
        val items = arrayOf("清空本页手写", "清空本页 AI 图层", "删除本页", "导出调试日志")
        AlertDialog.Builder(this).setItems(items) { _, w ->
            when (w) {
                0 -> confirm("清空本页手写？（可撤销）") { pageView.clearInk() }
                1 -> confirm("清空本页 AI 图层？（可撤销）") { pageView.clearAi() }
                2 -> confirm("删除第 ${pageView.pageIndex + 1} 页？不可撤销") {
                    if (notebook.pages.size > 1) { notebook.pages.removeAt(pageView.pageIndex); notebook.save() }
                    else notebook.pages[0].let { it.strokes.clear(); it.ai.clear() }
                    pageView.goTo(pageView.pageIndex.coerceAtMost(notebook.pages.size - 1))
                }
                3 -> exportLogs()
            }
        }.show()
    }

    /** Zip the current session log, notebook, recent attachments and logcat into Downloads. */
    private fun exportLogs() {
        notebook.save()
        Thread {
            try {
                val name = "InkAgent-log-${java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())}.zip"
                val bytes = java.io.ByteArrayOutputStream()
                java.util.zip.ZipOutputStream(bytes).use { z ->
                    fun put(n: String, data: ByteArray) { z.putNextEntry(java.util.zip.ZipEntry(n)); z.write(data); z.closeEntry() }
                    val info = StringBuilder()
                    info.append("version=${packageManager.getPackageInfo(packageName, 0).versionName}\n")
                    info.append("device=${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} sdk=${android.os.Build.VERSION.SDK_INT}\n")
                    info.append("density=${resources.displayMetrics.density} fontScale=${resources.configuration.fontScale}\n")
                    info.append("model=${settings.label()}\nsystemPromptChars=${settings.systemPrompt.length}\n")
                    settings.providers.forEach { info.append("provider ${it.name} ${it.protocol} ${it.baseUrl} keySet=${it.apiKey.isNotBlank()} models=${it.models}\n") }
                    put("info.txt", info.toString().toByteArray())
                    File(filesDir, "sessions/${agent.session.id}.jsonl").takeIf { it.exists() }?.let { put("session.jsonl", it.readBytes()) }
                    File(filesDir, "notebook/notebook.json").takeIf { it.exists() }?.let { put("notebook.json", it.readBytes()) }
                    (File(filesDir, "notebook/ai").listFiles() ?: emptyArray()).sortedByDescending { it.lastModified() }.take(20)
                        .forEach { put("ai/${it.name}", it.readBytes()) }
                    (attachDir.listFiles() ?: emptyArray()).sortedByDescending { it.lastModified() }.take(20)
                        .forEach { put("attachments/${it.name}", it.readBytes()) }
                    val log = try {
                        Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time", "-t", "3000")).inputStream.readBytes()
                    } catch (e: Exception) { "logcat failed: ${e.message}".toByteArray() }
                    put("logcat.txt", log)
                }
                val where: String
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    val values = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Download/")
                    }
                    val uri = contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: throw IllegalStateException("无法写入下载目录")
                    contentResolver.openOutputStream(uri)!!.use { it.write(bytes.toByteArray()) }
                    where = "下载/Download/$name"
                } else {
                    val f = File(getExternalFilesDir(null), name); f.writeBytes(bytes.toByteArray()); where = f.absolutePath
                }
                ui.post { AlertDialog.Builder(this).setMessage("已导出到：$where\n（不含 API Key）").setPositiveButton("好", null).show() }
            } catch (e: Exception) { ui.post { toast("导出失败：${e.message}") } }
        }.start()
    }

    private fun confirm(msg: String, action: () -> Unit) {
        AlertDialog.Builder(this).setMessage(msg).setPositiveButton("确定") { _, _ -> action() }.setNegativeButton("取消", null).show()
    }

    // ---------------- sessions ----------------
    private fun updateHeader() {
        panel.setTitle(agent.session.title)
        panel.setModel(settings.label())
    }

    private fun switchTo(s: Session) {
        if (agent.running) { toast("先停止当前回答"); return }
        agent.session = s; settings.sessionId = s.id
        panel.replay(s); updateHeader()
    }

    private fun newSession() = switchTo(store.create())

    private fun sessionsDialog() {
        val list = store.list().filter { it.events.isNotEmpty() || it.id == agent.session.id }
        val labels = list.map { (if (it.id == agent.session.id) "● " else "") + it.title }.toTypedArray()
        val dlg = AlertDialog.Builder(this).setTitle("会话（长按删除）")
            .setItems(labels) { _, w -> switchTo(list[w]) }
            .setPositiveButton("新对话") { _, _ -> newSession() }
            .setNegativeButton("关闭", null).create()
        dlg.setOnShowListener {
            dlg.listView.setOnItemLongClickListener { _, _, pos, _ ->
                val s = list[pos]
                confirm("删除会话「${s.title}」？") {
                    store.delete(s.id)
                    if (s.id == agent.session.id) newSession()
                    dlg.dismiss()
                }
                true
            }
        }
        dlg.show()
    }

    // ---------------- model selection (dsh ui-model-selection) ----------------
    private fun modelDialog() {
        val providers = settings.providers
        if (providers.isEmpty()) { settingsDialog(); return }
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(8)) }
        fun label(t: String) = TextView(this).apply { text = t; setTextColor(Color.GRAY); setPadding(0, dp(8), 0, 0) }
        val pSpin = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, providers.map { it.name }) }
        val modelEdit = AutoCompleteTextView(this).apply { threshold = 0; setText(settings.model); hint = "模型 id" }
        val effSpin = Spinner(this)
        val maxTok = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER; hint = "留空 = 默认"; setText(settings.maxTokens?.toString() ?: "") }
        fun refresh(p: Provider, keepModel: Boolean) {
            modelEdit.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, p.models))
            if (!keepModel) modelEdit.setText(p.models.firstOrNull() ?: "")
            val eff = p.efforts()
            effSpin.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, eff)
            effSpin.setSelection(eff.indexOf(settings.effort).coerceAtLeast(0))
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
        form.addView(label("推理强度 reasoningEffort")); form.addView(effSpin)
        form.addView(label("maxTokens")); form.addView(maxTok)
        AlertDialog.Builder(this).setTitle("选择模型").setView(form)
            .setPositiveButton("使用") { _, _ ->
                val p = providers[pSpin.selectedItemPosition]
                settings.providerId = p.id
                settings.model = modelEdit.text.toString().trim()
                settings.effort = effSpin.selectedItem?.toString() ?: p.efforts().first()
                settings.maxTokens = maxTok.text.toString().toIntOrNull()
                updateHeader()
            }
            .setNeutralButton("管理服务商") { _, _ -> providersDialog() }
            .setNegativeButton("取消", null).show()
    }

    // ---------------- settings ----------------
    private fun settingsDialog() {
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(8)) }
        fun label(t: String) = TextView(this).apply { text = t; setTextColor(Color.GRAY); setPadding(0, dp(10), 0, 0) }
        val sys = EditText(this).apply {
            setText(settings.systemPrompt); hint = "留空即无系统提示词"; minLines = 4; maxLines = 12
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE; gravity = Gravity.TOP
        }
        val tav = EditText(this).apply {
            setText(settings.tavilyKey); hint = "tvly-…"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        form.addView(Button(this).apply { text = "管理服务商与 API Key"; isAllCaps = false; setOnClickListener { providersDialog() } })
        form.addView(Button(this).apply { text = "选择模型：" + settings.label(); isAllCaps = false; setOnClickListener { modelDialog() } })
        val modes = listOf("compact" to "简洁", "standard" to "标准", "detailed" to "详细", "verbose" to "完全展开")
        val modeSpin = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, modes.map { it.second })
            setSelection(modes.indexOfFirst { it.first == settings.transcriptMode }.coerceAtLeast(0))
        }
        val timings = listOf("completion" to "回答结束后", "next-input" to "下次有新消息时")
        val timingSpin = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, timings.map { it.second })
            setSelection(timings.indexOfFirst { it.first == settings.collapseTiming }.coerceAtLeast(0))
        }
        form.addView(label("工作步骤展示 · 选择希望看到多少工具调用细节")); form.addView(modeSpin)
        form.addView(label("工作步骤收起时机")); form.addView(timingSpin)
        form.addView(label("System Prompt")); form.addView(sys)
        form.addView(label("Tavily API Key（联网搜索，免费 1000 次/月）")); form.addView(tav)
        AlertDialog.Builder(this).setTitle("设置").setView(ScrollView(this).apply { addView(form) })
            .setPositiveButton("保存") { _, _ ->
                settings.systemPrompt = sys.text.toString()
                settings.tavilyKey = tav.text.toString().trim()
                settings.transcriptMode = modes[modeSpin.selectedItemPosition].first
                settings.collapseTiming = timings[timingSpin.selectedItemPosition].first
                panel.setPolicy(settings.transcriptMode, settings.collapseTiming)
                panel.replay(agent.session)
                updateHeader()
            }
            .setNegativeButton("取消", null).show()
    }

    private fun providersDialog() {
        val providers = settings.providers.toMutableList()
        val names = providers.map { "${it.name}  (${it.models.size} 个模型)" } + "＋ 添加服务商"
        AlertDialog.Builder(this).setTitle("服务商（点一下编辑）")
            .setItems(names.toTypedArray()) { _, w -> editProvider(providers.getOrNull(w)) }
            .setNegativeButton("关闭", null).show()
    }

    private fun editProvider(existing: Provider?) {
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(8)) }
        fun label(t: String) = TextView(this).apply { text = t; setTextColor(Color.GRAY); setPadding(0, dp(8), 0, 0) }
        val name = EditText(this).apply { setText(existing?.name ?: ""); hint = "名称" }
        val proto = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, Protocol.values().map { it.label })
            setSelection((existing?.protocol ?: Protocol.OPENAI_CHAT).ordinal)
        }
        val base = EditText(this).apply { setText(existing?.baseUrl ?: ""); hint = "https://…"; inputType = InputType.TYPE_TEXT_VARIATION_URI }
        val key = EditText(this).apply {
            setText(existing?.apiKey ?: ""); hint = "API Key"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val models = EditText(this).apply {
            setText(existing?.models?.joinToString("\n") ?: ""); hint = "每行一个模型 id"; minLines = 3
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        form.addView(label("名称")); form.addView(name)
        form.addView(label("协议")); form.addView(proto)
        form.addView(label("Base URL")); form.addView(base)
        form.addView(label("API Key")); form.addView(key)
        form.addView(label("模型列表")); form.addView(models)
        form.addView(Button(this).apply {
            text = "从服务商获取模型列表"; isAllCaps = false
            setOnClickListener {
                val p = build(existing, name, proto, base, key, models)
                Thread {
                    try {
                        val list = Llm.listModels(p)
                        ui.post { models.setText(list.joinToString("\n")); toast("获取到 ${list.size} 个模型") }
                    } catch (e: Exception) { ui.post { toast("获取失败：${e.message}") } }
                }.start()
            }
        })
        val b = AlertDialog.Builder(this).setTitle(if (existing == null) "添加服务商" else "编辑服务商")
            .setView(ScrollView(this).apply { addView(form) })
            .setPositiveButton("保存") { _, _ ->
                val p = build(existing, name, proto, base, key, models)
                val list = settings.providers.toMutableList()
                val i = list.indexOfFirst { it.id == p.id }
                if (i >= 0) list[i] = p else list.add(p)
                settings.providers = list
                if (settings.provider()?.id == p.id && settings.model.isBlank()) settings.model = p.models.firstOrNull() ?: ""
                updateHeader()
            }
            .setNegativeButton("取消", null)
        if (existing != null) b.setNeutralButton("删除") { _, _ ->
            settings.providers = settings.providers.filter { it.id != existing.id }
            updateHeader()
        }
        b.show()
    }

    private fun build(existing: Provider?, name: EditText, proto: Spinner, base: EditText, key: EditText, models: EditText) = Provider(
        existing?.id ?: ("p" + System.currentTimeMillis().toString(36)),
        name.text.toString().trim().ifEmpty { "未命名" },
        Protocol.values()[proto.selectedItemPosition],
        base.text.toString().trim(), key.text.toString().trim(),
        models.text.toString().split('\n', ',').map { it.trim() }.filter { it.isNotEmpty() },
    )

    /** Debug-build hooks used by the emulator end-to-end test (tests/e2e.py). */
    private fun applyTestExtras() {
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val i = intent ?: return
        i.getStringExtra("test_key")?.let { key ->
            settings.providers = settings.providers.map { if (it.id == "deepseek") it.copy(apiKey = key) else it }
            settings.providerId = "deepseek"
        }
        i.getStringExtra("test_model")?.let { settings.model = it }
        i.getStringExtra("test_effort")?.let { settings.effort = it }
        i.getStringExtra("test_mode")?.let { settings.transcriptMode = it; panel.setPolicy(it, settings.collapseTiming) }
        if (i.hasExtra("test_finger")) { settings.fingerDraw = i.getBooleanExtra("test_finger", false); pageView.fingerCanDraw = settings.fingerDraw }
        i.getStringExtra("test_tool")?.let { selectTool(PageView.Tool.valueOf(it)) }
        updateHeader()
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent); setIntent(intent); applyTestExtras()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
