package com.inkagent.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Fullscreen 「框选截屏」 selector over a captured screen frame. Lasso by default; a long-press (hold still)
 * switches to rectangle, as in the notes 选择 tool (the mode chip also toggles / long-presses to rectangle).
 * [onResult] gets the crop (outside a lasso filled white) on 确定, or null on 取消.
 * All selection geometry is kept in screen pixels, which is the captured frame's pixel space.
 */
@SuppressLint("ViewConstructor")
class CaptureSelectView(ctx: Context, private val shot: Bitmap, private val onResult: (Bitmap?) -> Unit) : FrameLayout(ctx) {
    private val d = resources.displayMetrics.density
    private fun dp(v: Float) = (v * d)
    var rectMode = false
        private set
    private val pts = ArrayList<Float>() // x0,y0,x1,y1,… (screen px)
    private var rect: RectF? = null
    var hasSelection = false
        private set
    private var done = false

    val surface = Surface()
    val modeBtn: TextView
    val cancelBtn: TextView
    val okBtn: TextView

    init {
        setBackgroundColor(Color.BLACK)
        addView(surface, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        val hint = TextView(ctx).apply {
            text = "拖动圈出要截取的区域 · 长按切换矩形"
            setTextColor(Color.WHITE); textSize = 14f
            setPadding(dp(14f).toInt(), dp(6f).toInt(), dp(14f).toInt(), dp(6f).toInt())
            background = GradientDrawable().apply { cornerRadius = dp(16f); setColor(0xB3202124.toInt()) }
        }
        addView(hint, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(48f).toInt() })
        val bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6f).toInt(), dp(6f).toInt(), dp(6f).toInt(), dp(6f).toInt())
            background = GradientDrawable().apply { cornerRadius = dp(24f); setColor(0xE6202124.toInt()) }
        }
        fun btn(t: String, desc: String, accent: Boolean = false) = TextView(ctx).apply {
            text = t; contentDescription = desc; textSize = 15f; gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            minWidth = dp(76f).toInt(); minHeight = dp(40f).toInt()
            setPadding(dp(16f).toInt(), 0, dp(16f).toInt(), 0)
            background = GradientDrawable().apply { cornerRadius = dp(20f); setColor(if (accent) 0xFF4D6BFE.toInt() else 0x33FFFFFF) }
        }
        modeBtn = btn("套索", "切换框选方式")
        cancelBtn = btn("取消", "取消框选")
        okBtn = btn("确定", "确认框选", accent = true)
        val gap = dp(6f).toInt()
        bar.addView(modeBtn, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = gap })
        bar.addView(cancelBtn, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = gap })
        bar.addView(okBtn, LinearLayout.LayoutParams(-2, -2))
        addView(bar, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(64f).toInt() })
        modeBtn.setOnClickListener { setRect(!rectMode) }
        modeBtn.setOnLongClickListener { setRect(true); it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); true }
        cancelBtn.setOnClickListener { finish(false) }
        okBtn.setOnClickListener { if (hasSelection) finish(true) }
        updateUi()
    }

    private fun setRect(r: Boolean) {
        if (rectMode == r) return
        rectMode = r
        Toast.makeText(context, if (r) "已切换到矩形框选" else "已切换到套索框选", Toast.LENGTH_SHORT).show()
        updateUi()
    }

    private fun updateUi() {
        modeBtn.text = if (rectMode) "矩形" else "套索"
        okBtn.alpha = if (hasSelection) 1f else 0.45f
        surface.invalidate()
    }

    fun finish(ok: Boolean) {
        if (done) return
        done = true
        onResult(if (ok) crop() else null)
    }

    /** Selection bounds in screen px, or null. */
    fun selectionBounds(): RectF? {
        if (!hasSelection) return null
        rect?.let { return RectF(it) }
        val p = lassoPath() ?: return null
        val b = RectF(); p.computeBounds(b, true); return b
    }

    private fun lassoPath(): Path? {
        if (pts.size < 6) return null
        val p = Path(); p.moveTo(pts[0], pts[1])
        var i = 2; while (i < pts.size) { p.lineTo(pts[i], pts[i + 1]); i += 2 }
        p.close(); return p
    }

    private fun selPath(): Path? = rect?.let { Path().apply { addRect(it, Path.Direction.CW) } } ?: lassoPath()

    private fun crop(): Bitmap? {
        val b = selectionBounds() ?: return null
        val l = max(0, b.left.toInt()); val t = max(0, b.top.toInt())
        val r = min(shot.width, kotlin.math.ceil(b.right.toDouble()).toInt()); val bt = min(shot.height, kotlin.math.ceil(b.bottom.toDouble()).toInt())
        if (r - l < 2 || bt - t < 2) return null
        val out = Bitmap.createBitmap(r - l, bt - t, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.WHITE)
        if (rect != null) {
            c.drawBitmap(shot, Rect(l, t, r, bt), Rect(0, 0, r - l, bt - t), null)
        } else {
            val p = lassoPath() ?: return null
            p.offset(-l.toFloat(), -t.toFloat())
            val sh = BitmapShader(shot, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            sh.setLocalMatrix(Matrix().apply { setTranslate(-l.toFloat(), -t.toFloat()) })
            c.drawPath(p, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = sh; style = Paint.Style.FILL })
        }
        return out
    }

    inner class Surface : View(context) {
        private val loc = IntArray(2)
        private val scrim = Paint().apply { color = 0x73000000 }
        private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(3f); color = Color.WHITE }
        private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = dp(2f); color = 0xFF4D6BFE.toInt()
            pathEffect = DashPathEffect(floatArrayOf(dp(8f), dp(6f)), 0f)
        }
        private val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
        private var downX = 0f; private var downY = 0f
        private var moved = false
        private var gestureRect = false
        private val longPress = Runnable {
            if (!moved) {
                gestureRect = true
                setRect(true)
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                rect = RectF(downX, downY, downX, downY); pts.clear(); invalidate()
            }
        }

        override fun onDraw(canvas: Canvas) {
            getLocationOnScreen(loc)
            canvas.save()
            canvas.translate(-loc[0].toFloat(), -loc[1].toFloat())
            canvas.drawBitmap(shot, 0f, 0f, null)
            val p = selPath()
            canvas.save()
            if (p != null) canvas.clipOutPath(p)
            canvas.drawPaint(scrim)
            canvas.restore()
            if (p != null) { canvas.drawPath(p, edge); canvas.drawPath(p, dash) }
            canvas.restore()
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(ev: MotionEvent): Boolean {
            getLocationOnScreen(loc)
            val x = ev.x + loc[0]; val y = ev.y + loc[1]
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = x; downY = y; moved = false
                    gestureRect = rectMode
                    pts.clear(); rect = null; hasSelection = false
                    if (gestureRect) rect = RectF(x, y, x, y) else { pts.add(x); pts.add(y) }
                    if (!rectMode) postDelayed(longPress, 450)
                    updateUi()
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!moved && hypot(x - downX, y - downY) > slop) { moved = true; removeCallbacks(longPress) }
                    if (!moved) return true
                    if (gestureRect) rect = RectF(min(downX, x), min(downY, y), max(downX, x), max(downY, y))
                    else { pts.add(x); pts.add(y) }
                    invalidate()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    removeCallbacks(longPress)
                    if (ev.actionMasked == MotionEvent.ACTION_UP && moved) {
                        if (gestureRect) rect = RectF(min(downX, x), min(downY, y), max(downX, x), max(downY, y))
                        else { pts.add(x); pts.add(y) }
                    }
                    val min = dp(12f)
                    hasSelection = if (gestureRect) rect?.let { it.width() > min && it.height() > min } == true
                    else lassoPath()?.let { val b = RectF(); it.computeBounds(b, true); b.width() > min && b.height() > min && pts.size >= 6 } == true
                    if (!hasSelection) { pts.clear(); rect = null }
                    updateUi()
                }
            }
            return true
        }
    }
}
