package com.inkagent.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * QQ-style fullscreen editor for one composer attachment: 画笔 / 荧光笔 / 矩形框 / 圈 / 箭头 / 文字 / 马赛克,
 * 撤销 / 重做, two-finger zoom & pan. A plain view so it can live both in the activity (added over the decor)
 * and in a TYPE_APPLICATION_OVERLAY window (the cross-app overlay). It never sends anything: the host decides
 * what 替换原附件 / 添加为新附件 / 复制到剪贴板 / 保存到相册 do with the exported PNG.
 * All annotation geometry is kept in image pixels.
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class ImageEditorView(ctx: Context, private val src: Bitmap, private val host: Host) : FrameLayout(ctx) {
    interface Host {
        fun onReplace(png: ByteArray)
        fun onAddNew(png: ByteArray)
        /** Returns a short result line for the toast. */
        fun onCopy(png: ByteArray): String
        fun onSaveToGallery(png: ByteArray): String
        fun onClose()
        fun toast(s: String)
    }

    private val d = resources.displayMetrics.density
    private fun dp(v: Float) = (v * d).toInt()

    // ---------------- model ----------------
    private sealed class Op {
        class Stroke(val path: Path, val pts: ArrayList<PointF>, val color: Int, val width: Float, val marker: Boolean) : Op()
        class Shape(val kind: String, var a: PointF, var b: PointF, val color: Int, val width: Float) : Op()
        class Text(val at: PointF, val text: String, val color: Int, val size: Float) : Op()
        class Mosaic(val path: Path, val width: Float) : Op()
    }
    private val ops = ArrayList<Op>()
    private val redo = ArrayList<Op>()
    private var live: Op? = null

    var tool = "pen"; private set
    private var color = COLORS[0].second
    private var markerColor = COLORS[1].second
    private var widthIdx = 1
    var lastAction = ""; private set
    private var busy = false

    private val mosaicPaint: Paint by lazy {
        val block = max(6, min(src.width, src.height) / 40)
        val small = Bitmap.createScaledBitmap(src, max(1, src.width / block), max(1, src.height / block), true)
        val sh = BitmapShader(small, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        sh.setLocalMatrix(Matrix().apply { setScale(src.width / small.width.toFloat(), src.height / small.height.toFloat()) })
        Paint().apply { shader = sh; isFilterBitmap = false; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    }

    // ---------------- views ----------------
    val canvasView = EditCanvas(ctx)
    private val toolButtons = LinkedHashMap<String, LinearLayout>()
    private val colorDots = ArrayList<View>()
    private val widthDots = ArrayList<View>()
    private val colorRow = LinearLayout(ctx)
    private val widthRow = LinearLayout(ctx)
    private val optionRow = LinearLayout(ctx)
    private val undoBtn: LinearLayout
    private val redoBtn: LinearLayout
    private val textBar = LinearLayout(ctx)
    private val textInput = EditText(ctx)
    private var textAt: PointF? = null
    private val moreMenu = LinearLayout(ctx)
    /** Named controls (label → view) for the debug state the emulator test reads. */
    private val named = LinkedHashMap<String, View>()

    init {
        setBackgroundColor(0xFF0E0F11.toInt())
        isClickable = true
        isFocusableInTouchMode = true
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        addView(col, LayoutParams(-1, -1))

        // top bar: 编辑图片 (取消 lives bottom-left, QQ-style)
        val top = FrameLayout(ctx).apply { setBackgroundColor(0xFF17181B.toInt()) }
        top.addView(TextView(ctx).apply { text = "编辑图片"; setTextColor(Color.WHITE); textSize = 16f; typeface = Typeface.DEFAULT_BOLD },
            FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        col.addView(top, LinearLayout.LayoutParams(-1, dp(50f)))

        // inline text entry (文字 tool), at the top so the IME never covers it
        textBar.apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFF202226.toInt()); setPadding(dp(12f), dp(6f), dp(8f), dp(6f)); visibility = View.GONE
        }
        textInput.apply {
            hint = "输入文字"; setHintTextColor(0xFF8A8F98.toInt()); setTextColor(Color.WHITE); textSize = 16f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE; maxLines = 3
            imeOptions = EditorInfo.IME_ACTION_DONE
            background = GradientDrawable().apply { cornerRadius = dp(8f).toFloat(); setColor(0xFF2C2F35.toInt()) }
            setPadding(dp(10f), dp(6f), dp(10f), dp(6f))
            contentDescription = "输入文字"
        }
        textBar.addView(textInput, LinearLayout.LayoutParams(0, -2, 1f))
        val textCancel = textButton("取消", 0xFFBFC3CA.toInt(), 14f).apply { setOnClickListener { endText(false) } }
        val textOk = pill("完成", true).apply { setOnClickListener { endText(true) } }
        textBar.addView(textCancel, LinearLayout.LayoutParams(-2, dp(40f)))
        textBar.addView(textOk, LinearLayout.LayoutParams(-2, dp(36f)).apply { marginStart = dp(4f) })
        col.addView(textBar, LinearLayout.LayoutParams(-1, -2))

        // image
        val stage = FrameLayout(ctx)
        stage.addView(canvasView, FrameLayout.LayoutParams(-1, -1))
        col.addView(stage, LinearLayout.LayoutParams(-1, 0, 1f))

        // bottom panel
        val bottom = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFF17181B.toInt()); setPadding(0, dp(4f), 0, dp(8f)) }
        // options (colours · widths)
        optionRow.apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(dp(12f), dp(4f), dp(12f), dp(2f)) }
        colorRow.orientation = LinearLayout.HORIZONTAL; colorRow.gravity = Gravity.CENTER_VERTICAL
        COLORS.forEach { (name, c) ->
            val v = ColorDot(ctx, c).apply { contentDescription = name; setOnClickListener { pickColor(c) } }
            colorDots.add(v); named[name] = v
            colorRow.addView(v, LinearLayout.LayoutParams(dp(40f), dp(40f)))
        }
        optionRow.addView(colorRow)
        optionRow.addView(View(ctx).apply { setBackgroundColor(0xFF3A3D43.toInt()) }, LinearLayout.LayoutParams(dp(1f), dp(22f)).apply { marginStart = dp(14f); marginEnd = dp(14f) })
        widthRow.orientation = LinearLayout.HORIZONTAL; widthRow.gravity = Gravity.CENTER_VERTICAL
        WIDTH_NAMES.forEachIndexed { i, name ->
            val v = WidthDot(ctx, i).apply { contentDescription = name; setOnClickListener { pickWidth(i) } }
            widthDots.add(v); named[name] = v
            widthRow.addView(v, LinearLayout.LayoutParams(dp(40f), dp(40f)))
        }
        optionRow.addView(widthRow)
        bottom.addView(optionRow, LinearLayout.LayoutParams(-1, -2))

        // tools
        val tools = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(dp(8f), 0, dp(8f), 0) }
        TOOLS.forEach { (id, label) ->
            val b = toolButton(id, label).apply { setOnClickListener { pickTool(id) } }
            toolButtons[id] = b; named[label] = b
            tools.addView(b, LinearLayout.LayoutParams(dp(62f), dp(56f)))
        }
        tools.addView(View(ctx).apply { setBackgroundColor(0xFF3A3D43.toInt()) }, LinearLayout.LayoutParams(dp(1f), dp(28f)).apply { marginStart = dp(6f); marginEnd = dp(6f) })
        undoBtn = toolButton("undo", "撤销").apply { setOnClickListener { undo() } }
        redoBtn = toolButton("redo", "重做").apply { setOnClickListener { redoOp() } }
        named["撤销"] = undoBtn; named["重做"] = redoBtn
        tools.addView(undoBtn, LinearLayout.LayoutParams(dp(56f), dp(56f)))
        tools.addView(redoBtn, LinearLayout.LayoutParams(dp(56f), dp(56f)))
        bottom.addView(HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false; isFillViewport = true; addView(tools, ViewGroup.LayoutParams(-1, -2)) },
            LinearLayout.LayoutParams(-1, -2))
        bottom.addView(View(ctx).apply { setBackgroundColor(0xFF26282C.toInt()) }, LinearLayout.LayoutParams(-1, dp(1f)).apply { topMargin = dp(4f); bottomMargin = dp(8f) })

        // actions: 取消 … ⋯ 复制到剪贴板 添加为新附件 [替换原附件]
        val actions = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12f), 0, dp(12f), 0) }
        val cancel = textButton("取消", 0xFFBFC3CA.toInt(), 15f).apply { setOnClickListener { close() } }
        actions.addView(cancel, LinearLayout.LayoutParams(-2, dp(44f)))
        actions.addView(View(ctx), LinearLayout.LayoutParams(0, 1, 1f))
        val more = textButton("•••", 0xFFE6E6E6.toInt(), 13f).apply {
            contentDescription = "更多操作"; setOnClickListener { toggleMore() }
            minWidth = dp(44f)
        }
        val copy = pill("复制到剪贴板", false).apply { setOnClickListener { act("copy") } }
        val add = pill("添加为新附件", false).apply { setOnClickListener { act("add") } }
        val replace = pill("替换原附件", true).apply { setOnClickListener { act("replace") } }
        named["取消"] = cancel; named["更多操作"] = more; named["复制到剪贴板"] = copy; named["添加为新附件"] = add; named["替换原附件"] = replace
        actions.addView(more, LinearLayout.LayoutParams(-2, dp(44f)).apply { marginEnd = dp(6f) })
        actions.addView(copy, LinearLayout.LayoutParams(-2, dp(40f)).apply { marginEnd = dp(8f) })
        actions.addView(add, LinearLayout.LayoutParams(-2, dp(40f)).apply { marginEnd = dp(8f) })
        actions.addView(replace, LinearLayout.LayoutParams(-2, dp(40f)))
        bottom.addView(HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false; isFillViewport = true; addView(actions, ViewGroup.LayoutParams(-1, -2)) },
            LinearLayout.LayoutParams(-1, -2))
        col.addView(bottom, LinearLayout.LayoutParams(-1, -2))

        // ⋯ menu (保存到相册), floating above the action row
        moreMenu.apply {
            orientation = LinearLayout.VERTICAL; visibility = View.GONE; elevation = dp(8f).toFloat()
            background = GradientDrawable().apply { cornerRadius = dp(10f).toFloat(); setColor(0xFF2A2C31.toInt()); setStroke(1, 0xFF3A3D43.toInt()) }
            setPadding(0, dp(4f), 0, dp(4f))
        }
        val save = textButton("保存到相册", Color.WHITE, 15f).apply {
            setPadding(dp(18f), 0, dp(18f), 0); gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setOnClickListener { moreMenu.visibility = View.GONE; act("save") }
        }
        named["保存到相册"] = save
        moreMenu.addView(save, LinearLayout.LayoutParams(-1, dp(44f)))
        addView(moreMenu, LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply { bottomMargin = dp(62f); marginEnd = dp(12f) })

        setOnApplyWindowInsetsListener { v, ins ->
            if (Build.VERSION.SDK_INT >= 30) {
                val b = ins.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                v.setPadding(b.left, b.top, b.right, b.bottom)
            } else {
                @Suppress("DEPRECATION") v.setPadding(ins.systemWindowInsetLeft, ins.systemWindowInsetTop, ins.systemWindowInsetRight, ins.systemWindowInsetBottom)
            }
            ins
        }
        refreshChrome()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        current = this
        requestApplyInsets()
        requestFocus()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (current === this) current = null
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) handleBack()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    /** Back: close the text bar / ⋯ menu first, then leave the editor (discarding edits, like 取消). */
    fun handleBack() {
        when {
            textBar.visibility == View.VISIBLE -> endText(false)
            moreMenu.visibility == View.VISIBLE -> moreMenu.visibility = View.GONE
            else -> close()
        }
    }

    private fun close() {
        hideIme()
        lastAction = "cancel"
        host.onClose()
    }

    // ---------------- chrome ----------------
    private fun textButton(t: String, c: Int, size: Float) = TextView(context).apply {
        text = t; setTextColor(c); textSize = size; gravity = Gravity.CENTER
        setPadding(dp(12f), 0, dp(12f), 0); contentDescription = t
        background = GradientDrawable().apply { cornerRadius = dp(8f).toFloat(); setColor(Color.TRANSPARENT) }
        isClickable = true
    }

    private fun pill(t: String, primary: Boolean) = TextView(context).apply {
        text = t; textSize = 14f; gravity = Gravity.CENTER; contentDescription = t; isClickable = true
        setPadding(dp(16f), 0, dp(16f), 0); maxLines = 1
        if (primary) {
            setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply { cornerRadius = dp(20f).toFloat(); setColor(ACCENT) }
        } else {
            setTextColor(0xFFE6E6E6.toInt())
            background = GradientDrawable().apply { cornerRadius = dp(20f).toFloat(); setColor(0xFF2A2C31.toInt()); setStroke(dp(1f), 0xFF44474E.toInt()) }
        }
    }

    private fun toolButton(id: String, label: String) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; isClickable = true; contentDescription = label
        addView(ToolIcon(context, id), LinearLayout.LayoutParams(dp(26f), dp(26f)))
        addView(TextView(context).apply { text = label; textSize = 11f; gravity = Gravity.CENTER; maxLines = 1; tag = "label" },
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(2f) })
        background = GradientDrawable().apply { cornerRadius = dp(10f).toFloat(); setColor(Color.TRANSPARENT) }
    }

    private fun refreshChrome() {
        toolButtons.forEach { (id, b) ->
            val on = id == tool
            (b.background as GradientDrawable).setColor(if (on) 0x332F7BF6 else Color.TRANSPARENT)
            (b.getChildAt(0) as ToolIcon).tint = if (on) ACCENT_LIGHT else 0xFFE6E6E6.toInt()
            (b.getChildAt(1) as TextView).setTextColor(if (on) ACCENT_LIGHT else 0xFFBFC3CA.toInt())
            b.isSelected = on
        }
        fun dim(b: LinearLayout, enabled: Boolean) {
            b.isEnabled = enabled; b.alpha = if (enabled) 1f else 0.38f
            (b.getChildAt(0) as ToolIcon).tint = 0xFFE6E6E6.toInt()
            (b.getChildAt(1) as TextView).setTextColor(0xFFBFC3CA.toInt())
        }
        dim(undoBtn, ops.isNotEmpty()); dim(redoBtn, redo.isNotEmpty())
        val c = if (tool == "marker") markerColor else color
        colorDots.forEachIndexed { i, v -> (v as ColorDot).selectedDot = COLORS[i].second == c; v.invalidate() }
        widthDots.forEachIndexed { i, v -> (v as WidthDot).selectedDot = i == widthIdx; v.invalidate() }
        colorRow.visibility = if (tool == "mosaic") View.GONE else View.VISIBLE
        optionRow.getChildAt(1).visibility = colorRow.visibility
    }

    private fun pickTool(id: String) {
        if (textBar.visibility == View.VISIBLE) endText(true)
        moreMenu.visibility = View.GONE
        tool = id; refreshChrome()
    }
    private fun pickColor(c: Int) { if (tool == "marker") markerColor = c else color = c; refreshChrome() }
    private fun pickWidth(i: Int) { widthIdx = i; refreshChrome() }
    private fun toggleMore() { moreMenu.visibility = if (moreMenu.visibility == View.VISIBLE) View.GONE else View.VISIBLE }

    private fun undo() { if (ops.isNotEmpty()) { redo.add(ops.removeAt(ops.lastIndex)); canvasView.invalidate(); refreshChrome() } }
    private fun redoOp() { if (redo.isNotEmpty()) { ops.add(redo.removeAt(redo.lastIndex)); canvasView.invalidate(); refreshChrome() } }
    private fun commit(op: Op) { ops.add(op); redo.clear(); refreshChrome() }

    // ---------------- 文字 ----------------
    private fun beginText(at: PointF) {
        textAt = at
        textInput.setText("")
        textBar.visibility = View.VISIBLE
        textInput.requestFocus()
        post { context.getSystemService(InputMethodManager::class.java)?.showSoftInput(textInput, 0) }
    }

    private fun endText(ok: Boolean) {
        val at = textAt
        val s = textInput.text.toString().trimEnd()
        if (ok && at != null && s.isNotEmpty()) {
            val size = TEXT_SP[widthIdx] * d / canvasView.scale()
            commit(Op.Text(at, s, color, size))
            canvasView.invalidate()
        }
        textAt = null
        textBar.visibility = View.GONE
        hideIme()
        requestFocus()
    }

    private fun hideIme() {
        context.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(windowToken, 0)
    }

    /** Debug/test: put [s] as a text annotation at image point (x, y), as if typed with the 文字 tool. */
    fun addTextForTest(s: String, x: Float, y: Float) {
        commit(Op.Text(PointF(x, y), s, color, TEXT_SP[widthIdx] * d / canvasView.scale())); canvasView.invalidate()
    }

    // ---------------- export ----------------
    fun render(): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.WHITE)
        c.drawBitmap(src, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        ops.forEach { drawOp(c, it) }
        return out
    }

    private fun act(what: String) {
        if (busy) return
        moreMenu.visibility = View.GONE
        if (textBar.visibility == View.VISIBLE) endText(true)
        busy = true
        try {
            val bmp = render()
            val bos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
            bmp.recycle()
            val png = bos.toByteArray()
            lastAction = what
            when (what) {
                "replace" -> { hideIme(); host.onReplace(png) }
                "add" -> { hideIme(); host.onAddNew(png) }
                "copy" -> host.toast(host.onCopy(png))
                "save" -> host.toast(host.onSaveToGallery(png))
            }
        } catch (e: Throwable) {
            host.toast("操作失败：${e.message ?: e.javaClass.simpleName}")
        } finally { busy = false }
    }

    // ---------------- drawing ----------------
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }

    private fun drawOp(c: Canvas, op: Op) {
        when (op) {
            is Op.Stroke -> {
                strokePaint.color = if (op.marker) (op.color and 0x00FFFFFF) or 0x66000000 else op.color
                strokePaint.strokeWidth = op.width
                strokePaint.strokeCap = if (op.marker) Paint.Cap.SQUARE else Paint.Cap.ROUND
                if (op.pts.size == 1) {
                    fillPaint.color = strokePaint.color
                    c.drawCircle(op.pts[0].x, op.pts[0].y, op.width / 2, fillPaint)
                } else c.drawPath(op.path, strokePaint)
                strokePaint.strokeCap = Paint.Cap.ROUND
            }
            is Op.Shape -> {
                strokePaint.color = op.color; strokePaint.strokeWidth = op.width
                val r = RectF(min(op.a.x, op.b.x), min(op.a.y, op.b.y), max(op.a.x, op.b.x), max(op.a.y, op.b.y))
                when (op.kind) {
                    "rect" -> c.drawRect(r, strokePaint)
                    "oval" -> c.drawOval(r, strokePaint)
                    "arrow" -> {
                        val ang = atan2(op.b.y - op.a.y, op.b.x - op.a.x)
                        val len = hypot(op.b.x - op.a.x, op.b.y - op.a.y)
                        val head = min(max(op.width * 4.5f, 14f), len * 0.6f)
                        val bx = op.b.x - cos(ang) * head * 0.7f; val by = op.b.y - sin(ang) * head * 0.7f
                        c.drawLine(op.a.x, op.a.y, bx, by, strokePaint)
                        val p = Path()
                        p.moveTo(op.b.x, op.b.y)
                        p.lineTo(op.b.x - cos(ang - 0.45f) * head, op.b.y - sin(ang - 0.45f) * head)
                        p.lineTo(op.b.x - cos(ang + 0.45f) * head, op.b.y - sin(ang + 0.45f) * head)
                        p.close()
                        fillPaint.color = op.color
                        c.drawPath(p, fillPaint)
                    }
                }
            }
            is Op.Text -> {
                textPaint.color = op.color; textPaint.textSize = op.size
                val fm = textPaint.fontMetrics
                var y = op.at.y - fm.ascent
                op.text.split('\n').forEach { line -> c.drawText(line, op.at.x, y, textPaint); y += fm.descent - fm.ascent }
            }
            is Op.Mosaic -> { mosaicPaint.strokeWidth = op.width; c.drawPath(op.path, mosaicPaint) }
        }
    }

    /** The image stage: fit + two-finger zoom/pan; one finger (or the pen) draws with the current tool. */
    inner class EditCanvas(c: Context) : View(c) {
        private val m = Matrix()
        private val inv = Matrix()
        private var fit = 1f
        private var placed = false
        private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
        // gesture
        private var transforming = false
        private var startM = Matrix()
        private var startDist = 1f
        private var startMid = PointF()
        private var downScreen = PointF()

        fun scale(): Float { val v = FloatArray(9); m.getValues(v); return v[Matrix.MSCALE_X] }

        /** On-screen rect of the image (screen px). */
        fun imageScreenRect(): RectF {
            val r = RectF(0f, 0f, src.width.toFloat(), src.height.toFloat()); m.mapRect(r)
            val l = IntArray(2); getLocationOnScreen(l); r.offset(l[0].toFloat(), l[1].toFloat()); return r
        }

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { if (w > 0 && h > 0) fitImage() }

        private fun fitImage() {
            val pad = dp(16f).toFloat()
            val aw = width - 2 * pad; val ah = height - 2 * pad
            if (aw <= 0 || ah <= 0) return
            fit = min(aw / src.width, ah / src.height).coerceAtMost(4f)
            m.setScale(fit, fit)
            m.postTranslate((width - src.width * fit) / 2, (height - src.height * fit) / 2)
            placed = true
            invalidate()
        }

        private fun toImage(x: Float, y: Float): PointF {
            m.invert(inv); val p = floatArrayOf(x, y); inv.mapPoints(p); return PointF(p[0], p[1])
        }

        override fun onDraw(c: Canvas) {
            if (!placed) return
            c.save(); c.concat(m)
            fillPaint.color = Color.WHITE
            c.drawRect(0f, 0f, src.width.toFloat(), src.height.toFloat(), fillPaint)
            c.drawBitmap(src, 0f, 0f, bmpPaint)
            c.clipRect(0f, 0f, src.width.toFloat(), src.height.toFloat())
            ops.forEach { drawOp(c, it) }
            live?.let { drawOp(c, it) }
            c.restore()
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    moreMenu.visibility = View.GONE
                    transforming = false
                    downScreen = PointF(e.x, e.y)
                    if (textBar.visibility == View.VISIBLE && tool != "text") endText(true)
                    startOp(toImage(e.x, e.y))
                }
                MotionEvent.ACTION_POINTER_DOWN -> if (e.pointerCount == 2) {
                    live = null // a second finger turns the gesture into zoom/pan
                    transforming = true
                    startM.set(m)
                    startDist = max(1f, hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1)))
                    startMid = PointF((e.getX(0) + e.getX(1)) / 2, (e.getY(0) + e.getY(1)) / 2)
                    invalidate()
                }
                MotionEvent.ACTION_MOVE -> {
                    if (transforming) {
                        if (e.pointerCount >= 2) {
                            val dist = max(1f, hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1)))
                            val mid = PointF((e.getX(0) + e.getX(1)) / 2, (e.getY(0) + e.getY(1)) / 2)
                            m.set(startM)
                            var k = dist / startDist
                            val s0 = FloatArray(9).also { startM.getValues(it) }[Matrix.MSCALE_X]
                            k = k.coerceIn(fit * 0.8f / s0, fit * 10f / s0)
                            m.postScale(k, k, startMid.x, startMid.y)
                            m.postTranslate(mid.x - startMid.x, mid.y - startMid.y)
                            invalidate()
                        }
                    } else moveOp(toImage(e.x, e.y))
                }
                MotionEvent.ACTION_UP -> {
                    if (transforming) settle() else endOp(toImage(e.x, e.y), hypot(e.x - downScreen.x, e.y - downScreen.y) < dp(8f))
                    transforming = false
                }
                MotionEvent.ACTION_CANCEL -> { live = null; transforming = false; settle() }
            }
            return true
        }

        /** After zoom/pan: no smaller than fit; keep the image on the stage (centred when smaller). */
        private fun settle() {
            if (scale() < fit) { m.postScale(fit / scale(), fit / scale(), width / 2f, height / 2f) }
            val r = RectF(0f, 0f, src.width.toFloat(), src.height.toFloat()); m.mapRect(r)
            var dx = 0f; var dy = 0f
            if (r.width() <= width) dx = width / 2f - r.centerX() else if (r.left > 0) dx = -r.left else if (r.right < width) dx = width - r.right
            if (r.height() <= height) dy = height / 2f - r.centerY() else if (r.top > 0) dy = -r.top else if (r.bottom < height) dy = height - r.bottom
            m.postTranslate(dx, dy)
            invalidate()
        }

        private fun lineWidth(): Float {
            val base = when (tool) { "marker" -> MARKER_DP[widthIdx]; "mosaic" -> MOSAIC_DP[widthIdx]; else -> PEN_DP[widthIdx] }
            return base * d / scale()
        }

        private fun startOp(p: PointF) {
            live = when (tool) {
                "pen", "marker" -> Op.Stroke(Path().apply { moveTo(p.x, p.y) }, arrayListOf(p), if (tool == "marker") markerColor else color, lineWidth(), tool == "marker")
                "rect", "oval", "arrow" -> Op.Shape(tool, p, PointF(p.x, p.y), color, lineWidth())
                "mosaic" -> Op.Mosaic(Path().apply { moveTo(p.x, p.y); lineTo(p.x + 0.1f, p.y) }, lineWidth())
                else -> null
            }
            invalidate()
        }

        private fun moveOp(p: PointF) {
            when (val o = live) {
                is Op.Stroke -> { val q = o.pts.last(); o.path.quadTo(q.x, q.y, (q.x + p.x) / 2, (q.y + p.y) / 2); o.pts.add(p) }
                is Op.Shape -> o.b = p
                is Op.Mosaic -> o.path.lineTo(p.x, p.y)
                else -> {}
            }
            invalidate()
        }

        private fun endOp(p: PointF, tap: Boolean) {
            if (tool == "text") {
                if (tap && p.x >= 0 && p.y >= 0 && p.x <= src.width && p.y <= src.height) beginText(p)
                return
            }
            val o = live ?: return
            live = null
            when (o) {
                is Op.Stroke -> { o.path.lineTo(p.x, p.y); o.pts.add(p); commit(o) }
                is Op.Shape -> { o.b = p; if (hypot(o.b.x - o.a.x, o.b.y - o.a.y) * scale() >= dp(6f)) commit(o) }
                is Op.Mosaic -> { o.path.lineTo(p.x, p.y); commit(o) }
                else -> {}
            }
            invalidate()
        }
    }

    // ---------------- small drawn widgets ----------------
    private class ColorDot(c: Context, val color: Int) : View(c) {
        var selectedDot = false
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        init { isClickable = true }
        override fun onDraw(cv: Canvas) {
            val d = resources.displayMetrics.density
            val cx = width / 2f; val cy = height / 2f
            val r = (if (selectedDot) 11f else 8.5f) * d
            p.style = Paint.Style.FILL; p.color = color
            cv.drawCircle(cx, cy, r, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = (if (selectedDot) 2.5f else 1.5f) * d
            p.color = if (selectedDot) Color.WHITE else 0x99FFFFFF.toInt()
            cv.drawCircle(cx, cy, r, p)
        }
    }

    private class WidthDot(c: Context, val idx: Int) : View(c) {
        var selectedDot = false
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        init { isClickable = true }
        override fun onDraw(cv: Canvas) {
            val d = resources.displayMetrics.density
            val cx = width / 2f; val cy = height / 2f
            if (selectedDot) { p.style = Paint.Style.FILL; p.color = 0x332F7BF6; cv.drawCircle(cx, cy, 15f * d, p) }
            p.style = Paint.Style.FILL; p.color = if (selectedDot) ACCENT_LIGHT else 0xFFE6E6E6.toInt()
            cv.drawCircle(cx, cy, floatArrayOf(2.5f, 4.5f, 7f)[idx] * d, p)
        }
    }

    private class ToolIcon(c: Context, val kind: String) : View(c) {
        var tint = Color.WHITE
            set(v) { field = v; invalidate() }
        private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        override fun onDraw(cv: Canvas) {
            val s = width / 24f
            p.color = tint; p.strokeWidth = 1.8f * s; p.style = Paint.Style.STROKE; p.alpha = 255
            fun x(v: Float) = v * s
            when (kind) {
                "pen" -> {
                    val path = Path().apply { moveTo(x(5f), x(19f)); lineTo(x(6f), x(15f)); lineTo(x(16f), x(5f)); lineTo(x(19f), x(8f)); lineTo(x(9f), x(18f)); close() }
                    cv.drawPath(path, p); cv.drawLine(x(14f), x(7f), x(17f), x(10f), p)
                }
                "marker" -> {
                    val path = Path().apply { moveTo(x(8f), x(15f)); lineTo(x(15f), x(4f)); lineTo(x(20f), x(7f)); lineTo(x(13f), x(18f)); close() }
                    cv.drawPath(path, p)
                    p.strokeWidth = 3.5f * s; p.alpha = 140; cv.drawLine(x(3f), x(21f), x(12f), x(21f), p)
                }
                "rect" -> cv.drawRoundRect(RectF(x(4f), x(6f), x(20f), x(18f)), x(1.5f), x(1.5f), p)
                "oval" -> cv.drawOval(RectF(x(3.5f), x(5.5f), x(20.5f), x(18.5f)), p)
                "arrow" -> {
                    cv.drawLine(x(5f), x(19f), x(18f), x(6f), p)
                    cv.drawLine(x(18f), x(6f), x(11f), x(6.5f), p); cv.drawLine(x(18f), x(6f), x(17.5f), x(13f), p)
                }
                "text" -> {
                    cv.drawLine(x(5f), x(6f), x(19f), x(6f), p); cv.drawLine(x(12f), x(6f), x(12f), x(19f), p)
                    cv.drawLine(x(9f), x(19f), x(15f), x(19f), p)
                }
                "mosaic" -> {
                    p.style = Paint.Style.FILL
                    for (i in 0..2) for (j in 0..2) {
                        p.alpha = if ((i + j) % 2 == 0) 255 else 90
                        cv.drawRect(x(4f + i * 5.5f), x(4f + j * 5.5f), x(9f + i * 5.5f), x(9f + j * 5.5f), p)
                    }
                }
                "undo", "redo" -> {
                    cv.save()
                    if (kind == "redo") cv.scale(-1f, 1f, width / 2f, height / 2f)
                    val path = Path().apply { moveTo(x(8f), x(9f)); lineTo(x(15f), x(9f)); cubicTo(x(21f), x(9f), x(21f), x(19f), x(15f), x(19f)); lineTo(x(9f), x(19f)) }
                    cv.drawPath(path, p)
                    cv.drawLine(x(8f), x(9f), x(11.5f), x(5.5f), p); cv.drawLine(x(8f), x(9f), x(11.5f), x(12.5f), p)
                    cv.restore()
                }
            }
        }
    }

    // ---------------- debug (TEST_STATE) ----------------
    fun debugJson(): JSONObject {
        val o = JSONObject().put("open", isAttachedToWindow).put("tool", tool).put("ops", ops.size).put("redo", redo.size)
            .put("lastAction", lastAction).put("textBar", textBar.visibility == View.VISIBLE).put("moreMenu", moreMenu.visibility == View.VISIBLE)
            .put("image", JSONArray().put(src.width).put(src.height)).put("scale", canvasView.scale().toDouble())
        val r = canvasView.imageScreenRect()
        o.put("imgRect", JSONArray().put(r.left.toInt()).put(r.top.toInt()).put(r.right.toInt()).put(r.bottom.toInt()))
        val b = JSONObject()
        named.forEach { (k, v) ->
            if (v.isShown && v.width > 0) {
                val l = IntArray(2); v.getLocationOnScreen(l)
                b.put(k, JSONArray().put(l[0] + v.width / 2).put(l[1] + v.height / 2).put(v.width).put(v.height))
            }
        }
        o.put("buttons", b)
        val l = IntArray(2); getLocationOnScreen(l)
        o.put("root", JSONArray().put(l[0]).put(l[1]).put(width).put(height))
        return o
    }

    companion object {
        @Volatile var current: ImageEditorView? = null
            private set
        const val ACCENT = 0xFF2F7BF6.toInt()
        const val ACCENT_LIGHT = 0xFF5B9BFF.toInt()
        val COLORS = listOf("红色" to 0xFFF23C3C.toInt(), "黄色" to 0xFFFFC400.toInt(), "蓝色" to 0xFF2F7BF6.toInt(), "黑色" to 0xFF111111.toInt())
        val WIDTH_NAMES = listOf("细", "中", "粗")
        val TOOLS = listOf("pen" to "画笔", "marker" to "荧光笔", "rect" to "矩形框", "oval" to "圈", "arrow" to "箭头", "text" to "文字", "mosaic" to "马赛克")
        private val PEN_DP = floatArrayOf(2f, 4f, 7f)
        private val MARKER_DP = floatArrayOf(10f, 16f, 24f)
        private val MOSAIC_DP = floatArrayOf(16f, 28f, 44f)
        private val TEXT_SP = floatArrayOf(16f, 24f, 36f)
    }
}
