package com.inkagent

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

class MainActivity : Activity() {

    private lateinit var ink: InkView
    private lateinit var chatList: LinearLayout
    private lateinit var chatScroll: ScrollView
    private lateinit var input: EditText
    private lateinit var preview: ImageView
    private lateinit var previewBox: LinearLayout
    private lateinit var sendBtn: Button
    private val toolButtons = HashMap<InkView.Tool, Button>()

    private val ui = Handler(Looper.getMainLooper())
    private val history = ArrayList<JSONObject>()
    private var pendingImage: Bitmap? = null
    private var busy = false

    private val prefs by lazy { getSharedPreferences("llm", Context.MODE_PRIVATE) }

    private val presets = linkedMapOf(
        "自定义" to "",
        "OpenAI" to "https://api.openai.com/v1",
        "DeepSeek" to "https://api.deepseek.com/v1",
        "通义千问 DashScope" to "https://dashscope.aliyuncs.com/compatible-mode/v1",
        "智谱 GLM" to "https://open.bigmodel.cn/api/paas/v4",
        "Moonshot Kimi" to "https://api.moonshot.cn/v1",
        "OpenRouter" to "https://openrouter.ai/api/v1",
        "Cline / ClinePass" to "https://api.cline.bot/api/v1",
        "本地 Ollama" to "http://192.168.1.100:11434/v1",
    )

    private val defaultSystem = "你是一个数学学习助手。用户会发来他手写内容的截图（通常是数学公式或解题过程）。" +
        "请先用 LaTeX 写出你识别到的内容，再给出检查和建议：指出错误的步骤、给出提示或讲解。回答用中文，简洁清晰。"

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        // ---------- left: canvas + toolbar ----------
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setBackgroundColor(Color.rgb(245, 246, 248))
            gravity = Gravity.CENTER_VERTICAL
        }
        fun toolBtn(label: String, t: InkView.Tool) = Button(this).apply {
            text = label; isAllCaps = false
            setOnClickListener { selectTool(t) }
            toolButtons[t] = this
        }
        toolbar.addView(toolBtn("✏️ 笔", InkView.Tool.PEN))
        toolbar.addView(toolBtn("🧽 橡皮", InkView.Tool.ERASER))
        toolbar.addView(toolBtn("⭕ 圈选", InkView.Tool.LASSO))
        toolbar.addView(Button(this).apply { text = "↶ 撤销"; isAllCaps = false; setOnClickListener { ink.undo() } })
        toolbar.addView(Button(this).apply { text = "清空"; isAllCaps = false; setOnClickListener { confirmClear() } })
        val fingerBox = CheckBox(this).apply {
            text = "手指书写"
            isChecked = prefs.getBoolean("finger", false)
            setOnCheckedChangeListener { _, c -> ink.fingerCanDraw = c; prefs.edit().putBoolean("finger", c).apply() }
        }
        toolbar.addView(fingerBox)
        val toolbarScroll = HorizontalScrollView(this).apply { addView(toolbar) }
        left.addView(toolbarScroll, LinearLayout.LayoutParams(-1, -2))

        ink = InkView(this)
        ink.fingerCanDraw = fingerBox.isChecked
        ink.onLassoSelected = { bmp -> onSelection(bmp) }
        left.addView(ink, LinearLayout.LayoutParams(-1, 0, 1f))

        // ---------- right: agent panel ----------
        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(250, 250, 252))
            setPadding(dp(8), dp(6), dp(8), dp(8))
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "Agent"; textSize = 18f; setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(Button(this).apply { text = "新对话"; isAllCaps = false; setOnClickListener { newChat() } })
        header.addView(Button(this).apply { text = "⚙ 设置"; isAllCaps = false; setOnClickListener { showSettings() } })
        right.addView(header)

        chatList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        chatScroll = ScrollView(this).apply { addView(chatList) }
        right.addView(chatScroll, LinearLayout.LayoutParams(-1, 0, 1f))

        previewBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; visibility = View.GONE
            setPadding(0, dp(4), 0, dp(4))
        }
        preview = ImageView(this).apply {
            adjustViewBounds = true; maxHeight = dp(110); scaleType = ImageView.ScaleType.FIT_START
            background = border(Color.rgb(30, 120, 255))
        }
        previewBox.addView(preview, LinearLayout.LayoutParams(0, -2, 1f))
        previewBox.addView(Button(this).apply { text = "✕"; setOnClickListener { clearPending() } })
        right.addView(previewBox)

        val inputRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM }
        input = EditText(this).apply {
            hint = "圈选手写内容，或直接输入问题…"; maxLines = 5
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        inputRow.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        sendBtn = Button(this).apply { text = "发送"; setOnClickListener { send() } }
        inputRow.addView(sendBtn)
        right.addView(inputRow)

        root.addView(left, LinearLayout.LayoutParams(0, -1, 3f))
        root.addView(View(this).apply { setBackgroundColor(Color.rgb(220, 222, 228)) }, LinearLayout.LayoutParams(dp(1), -1))
        root.addView(right, LinearLayout.LayoutParams(0, -1, 2f))
        setContentView(root)

        selectTool(InkView.Tool.PEN)
        if (prefs.getString("baseUrl", "").isNullOrBlank()) {
            addBubble("system", "欢迎！先点右上角「⚙ 设置」填写 Base URL、API Key 和模型名（需支持图片输入的模型）。\n用笔写字，切到「⭕ 圈选」圈住内容即可发给 Agent。手指拖动可平移画布。")
        }
    }

    private fun border(color: Int) = GradientDrawable().apply { setStroke(dp(1), color); cornerRadius = dp(6).toFloat() }

    private fun selectTool(t: InkView.Tool) {
        ink.tool = t
        toolButtons.forEach { (k, b) -> b.setTextColor(if (k == t) Color.rgb(30, 120, 255) else Color.DKGRAY)
            b.setTypeface(null, if (k == t) Typeface.BOLD else Typeface.NORMAL) }
    }

    private fun confirmClear() {
        AlertDialog.Builder(this).setMessage("清空整张画布？（可撤销）")
            .setPositiveButton("清空") { _, _ -> ink.clearAll() }.setNegativeButton("取消", null).show()
    }

    private fun onSelection(bmp: Bitmap) {
        pendingImage = bmp
        preview.setImageBitmap(bmp)
        previewBox.visibility = View.VISIBLE
        if (prefs.getBoolean("autoSend", true) && !busy) send()
        else Toast.makeText(this, "已选中，点发送", Toast.LENGTH_SHORT).show()
    }

    private fun clearPending() { pendingImage = null; previewBox.visibility = View.GONE }

    private fun newChat() {
        if (busy) return
        history.clear(); chatList.removeAllViews(); clearPending()
    }

    private fun config() = LlmConfig(
        baseUrl = prefs.getString("baseUrl", "") ?: "",
        apiKey = prefs.getString("apiKey", "") ?: "",
        model = prefs.getString("model", "") ?: "",
        systemPrompt = prefs.getString("system", defaultSystem) ?: defaultSystem,
        temperature = (prefs.getString("temp", "0.3") ?: "0.3").toDoubleOrNull() ?: 0.3,
    )

    private fun send() {
        if (busy) return
        val cfg = config()
        if (cfg.baseUrl.isBlank() || cfg.model.isBlank()) { showSettings(); return }
        val img = pendingImage
        var text = input.text.toString().trim()
        if (img == null && text.isEmpty()) return
        if (text.isEmpty()) text = prefs.getString("defaultAsk", "请识别我圈中的手写内容，并检查、给出建议。") ?: ""

        val msg = JSONObject().put("role", "user")
        if (img != null) {
            val b64 = encode(img)
            val parts = JSONArray()
                .put(JSONObject().put("type", "text").put("text", text))
                .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/png;base64,$b64")))
            msg.put("content", parts)
        } else msg.put("content", text)

        addBubble("user", text, img)
        input.setText("")
        clearPending()
        history.add(msg)

        val replyView = addBubble("assistant", "…")
        val sb = StringBuilder()
        busy = true; sendBtn.isEnabled = false
        Thread {
            try {
                val full = LlmClient.chat(cfg, history) { piece ->
                    sb.append(piece)
                    val snap = sb.toString()
                    ui.post { replyView.text = snap; scrollDown() }
                }
                history.add(JSONObject().put("role", "assistant").put("content", full))
                ui.post { if (full.isEmpty()) replyView.text = "（空回复）" }
            } catch (e: Exception) {
                history.removeAt(history.size - 1)
                ui.post { replyView.text = "出错了：${e.message}"; replyView.setTextColor(Color.rgb(200, 40, 40)) }
            } finally {
                ui.post { busy = false; sendBtn.isEnabled = true; scrollDown() }
            }
        }.start()
    }

    private fun encode(bmp: Bitmap): String {
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun addBubble(role: String, text: String, img: Bitmap? = null): TextView {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(when (role) { "user" -> Color.rgb(222, 236, 255); "assistant" -> Color.WHITE; else -> Color.rgb(255, 248, 225) })
                if (role == "assistant") setStroke(dp(1), Color.rgb(228, 230, 235))
            }
        }
        if (img != null) box.addView(ImageView(this).apply {
            setImageBitmap(img); adjustViewBounds = true; maxHeight = dp(140); scaleType = ImageView.ScaleType.FIT_START
        })
        val tv = TextView(this).apply {
            this.text = text; textSize = 15f; setTextColor(Color.rgb(30, 30, 35)); setTextIsSelectable(true)
        }
        box.addView(tv)
        val lp = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) }
        chatList.addView(box, lp)
        scrollDown()
        return tv
    }

    private fun scrollDown() = chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }

    private fun showSettings() {
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(8)) }
        fun label(t: String) = TextView(this).apply { text = t; setPadding(0, dp(10), 0, 0); setTextColor(Color.GRAY) }
        fun field(key: String, def: String, hintText: String, password: Boolean = false, multi: Boolean = false) =
            EditText(this).apply {
                setText(prefs.getString(key, def)); hint = hintText
                inputType = when {
                    password -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    multi -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                }
                if (multi) { minLines = 3; maxLines = 8 }
            }

        val base = field("baseUrl", "", "https://api.openai.com/v1")
        form.addView(label("服务商预设"))
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, presets.keys.toList())
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            var first = true
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (first) { first = false; return }
                val url = presets.values.toList()[pos]
                if (url.isNotEmpty()) base.setText(url)
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        form.addView(spinner)
        form.addView(label("Base URL（OpenAI 兼容，到 /v1 为止）")); form.addView(base)
        val key = field("apiKey", "", "sk-...", password = true)
        form.addView(label("API Key")); form.addView(key)
        val model = field("model", "", "例如 gpt-4o / qwen-vl-max / glm-4v-plus")
        form.addView(label("模型名（需支持图片输入）")); form.addView(model)
        val temp = field("temp", "0.3", "0.3")
        form.addView(label("Temperature")); form.addView(temp)
        val ask = field("defaultAsk", "请识别我圈中的手写内容，并检查、给出建议。", "", multi = true)
        form.addView(label("圈选后默认提问")); form.addView(ask)
        val sys = field("system", defaultSystem, "", multi = true)
        form.addView(label("System Prompt（Agent 角色）")); form.addView(sys)
        val auto = CheckBox(this).apply { text = "圈选后自动发送"; isChecked = prefs.getBoolean("autoSend", true) }
        form.addView(auto)

        val scroll = ScrollView(this).apply { addView(form) }
        AlertDialog.Builder(this).setTitle("LLM 设置").setView(scroll)
            .setPositiveButton("保存") { _, _ ->
                prefs.edit()
                    .putString("baseUrl", base.text.toString().trim())
                    .putString("apiKey", key.text.toString().trim())
                    .putString("model", model.text.toString().trim())
                    .putString("temp", temp.text.toString().trim())
                    .putString("defaultAsk", ask.text.toString())
                    .putString("system", sys.text.toString())
                    .putBoolean("autoSend", auto.isChecked)
                    .apply()
                Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("测试连接") { _, _ ->
                prefs.edit().putString("baseUrl", base.text.toString().trim()).putString("apiKey", key.text.toString().trim())
                    .putString("model", model.text.toString().trim()).apply()
                testConnection()
            }
            .setNegativeButton("取消", null).show()
    }

    private fun testConnection() {
        val cfg = config().copy(systemPrompt = "")
        val tv = addBubble("system", "正在测试连接…")
        Thread {
            try {
                val r = LlmClient.chat(cfg, listOf(JSONObject().put("role", "user").put("content", "回复两个字：你好"))) {}
                ui.post { tv.text = "✅ 连接成功：$r" }
            } catch (e: Exception) {
                ui.post { tv.text = "❌ 连接失败：${e.message}" }
            }
        }.start()
    }
}
