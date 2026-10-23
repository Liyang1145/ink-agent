package com.inkagent.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.inkagent.AgentBridge
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.hypot

/**
 * 「在其他应用上显示悬浮球」: a foreground service (specialUse; + mediaProjection once screen capture is granted)
 * that, while InkAgent itself is in the background, shows
 *  - a draggable, edge-snapping floating ball (TYPE_APPLICATION_OVERLAY), and on tap
 *  - an overlay agent window (drag by its bar, resize by its corners) hosting the very same agent panel WebView
 *    as the app (one live session: the WebView is moved between the app and this window, never duplicated),
 *  - 「框选截屏」: one frame through a cached MediaProjection grant → fullscreen lasso/rectangle selector →
 *    PNG crop added to the composer's attachments (never sent automatically).
 * Debug builds also listen for com.inkagent.TEST_OVERLAY (tests/e2e.py).
 */
class OverlayService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var ui: Context
    private lateinit var wm: WindowManager
    private val st by lazy { com.inkagent.Settings(this) }
    private fun dp(v: Float) = (v * resources.displayMetrics.density).toInt()

    // ball
    private var ball: View? = null
    private var ballLp: WindowManager.LayoutParams? = null
    private var ballAnim: ValueAnimator? = null
    // window
    private var win: OverlayRoot? = null
    private var winLp: WindowManager.LayoutParams? = null
    private var content: FrameLayout? = null
    private var windowOpen = false
    private var winFocusable = false
    private var inputFocused = false
    // capture
    private var projection: MediaProjection? = null
    private var vd: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private val imgLock = Any()
    private var latest: Image? = null
    private val capThread = HandlerThread("ink-capture").also { it.start() }
    private val capHandler = Handler(capThread.looper)
    private var capturing = false
    private var awaitingConsent = false
    private var selector: CaptureSelectView? = null
    private var consentRequests = 0
    private var captures = 0
    private var lastCrop = intArrayOf(0, 0)
    private var lastError = ""
    private var testReceiver: BroadcastReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        ui = if (Build.VERSION.SDK_INT >= 30) {
            val disp = getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
            createDisplayContext(disp).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        } else this
        wm = ui.getSystemService(WindowManager::class.java)
        startFg(false)
        registerTestHooks()
        refresh()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startFg(projection != null)
        refresh()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        instance = null
        testReceiver?.let { runCatching { unregisterReceiver(it) } }
        cancelSelector()
        closeWindow(back = true)
        removeBall()
        releaseProjection()
        capThread.quitSafely()
        super.onDestroy()
    }

    // ---------------- foreground notification ----------------
    private fun startFg(withProjection: Boolean) {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "悬浮球", NotificationManager.IMPORTANCE_MIN))
        val open = PendingIntent.getActivity(this, 0, appIntent(), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("InkAgent 悬浮球已开启")
            .setContentText(if (withProjection) "可在任意界面提问、框选截屏（已获截屏授权）" else "切到其他应用时可点悬浮球提问、框选截屏")
            .setOngoing(true).setContentIntent(open).setPriority(NotificationCompat.PRIORITY_MIN).build()
        val type = when {
            Build.VERSION.SDK_INT >= 34 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                (if (withProjection) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0)
            Build.VERSION.SDK_INT >= 29 -> if (withProjection) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0
            else -> 0
        }
        ServiceCompat.startForeground(this, NOTIF_ID, n, type)
    }

    private fun appIntent() = Intent(this, com.xnotes.MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)

    fun openApp() {
        runCatching { startActivity(appIntent()) }.onFailure { toast("无法打开 InkAgent：${it.message}") }
    }

    // ---------------- visibility rules ----------------
    /** Ball only while InkAgent is in the background and neither the window nor a capture is up. */
    private fun refresh() {
        if (appForeground) { removeBall(); return }
        if (!windowOpen && !capturing && !awaitingConsent) showBall() else ball?.visibility = View.INVISIBLE
    }

    private fun onAppForegroundChanged(fg: Boolean) {
        if (fg) {
            if (selector != null) cancelSelector()
            if (editorWin != null) { AgentBridge.controller?.closeImageEditor(); editorWin?.let { removeEditor(it) } }
            if (windowOpen) {
                closeWindow(back = true)
                // keep the conversation in view in the app (in-app ball → in-app window)
                AgentBridge.controller?.let { if (it.mode == "ball") it.changeMode("window") }
            }
        }
        refresh()
    }

    /** The activity (and its controller) went away: give the panel back and show only the ball. */
    fun onControllerGone() {
        if (selector != null) cancelSelector()
        editorWin?.let { removeEditor(it) }
        if (windowOpen) closeWindow(back = true)
        refresh()
    }

    // ---------------- screen ----------------
    private fun screen(): Point = if (Build.VERSION.SDK_INT >= 30) {
        val b = wm.maximumWindowMetrics.bounds; Point(b.width(), b.height())
    } else Point().also { @Suppress("DEPRECATION") wm.defaultDisplay.getRealSize(it) }

    private fun overlayType() = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    // ---------------- the floating ball ----------------
    private val ballSize get() = dp(58f) + 2 * dp(5f)
    private val ballMargin get() = dp(8f)

    @SuppressLint("ClickableViewAccessibility")
    private fun showBall() {
        if (ball == null) {
            val root = FrameLayout(ui).apply { setPadding(dp(5f), dp(5f), dp(5f), dp(5f)); clipToPadding = false }
            val face = TextView(ui).apply {
                text = "AI"; setTextColor(Color.WHITE); textSize = 20f; gravity = Gravity.CENTER
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF4D6BFE.toInt()); setStroke(dp(2f), Color.WHITE) }
                elevation = dp(4f).toFloat()
                contentDescription = "InkAgent 悬浮球"
            }
            root.addView(face, FrameLayout.LayoutParams(dp(58f), dp(58f)))
            val sc = screen()
            val lp = WindowManager.LayoutParams(ballSize, ballSize, overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.START
                x = if (st.ovBallRight) sc.x - ballSize - ballMargin else ballMargin
                y = clampBallY(if (st.ovBallY >= 0) st.ovBallY else (sc.y * 0.6f).toInt())
                title = "InkAgentOverlayBall"
                windowAnimations = 0
            }
            val slop = ViewConfiguration.get(ui).scaledTouchSlop
            var downRawX = 0f; var downRawY = 0f; var startX = 0; var startY = 0; var dragging = false
            face.setOnTouchListener { _, ev ->
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        ballAnim?.cancel()
                        downRawX = ev.rawX; downRawY = ev.rawY; startX = lp.x; startY = lp.y; dragging = false
                        face.animate().scaleX(0.92f).scaleY(0.92f).setDuration(80).start()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = ev.rawX - downRawX; val dy = ev.rawY - downRawY
                        if (!dragging && hypot(dx, dy) > slop) dragging = true
                        if (dragging) {
                            val s = screen()
                            lp.x = (startX + dx).toInt().coerceIn(0, s.x - ballSize)
                            lp.y = clampBallY((startY + dy).toInt())
                            runCatching { wm.updateViewLayout(root, lp) }
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        face.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                        if (dragging) snapBall() else if (ev.actionMasked == MotionEvent.ACTION_UP) openWindow()
                    }
                }
                true
            }
            ball = root; ballLp = lp
        }
        val v = ball!!
        v.visibility = View.VISIBLE
        if (!v.isAttachedToWindow && v.parent == null) runCatching { wm.addView(v, ballLp) }.onFailure { lastError = "ball: ${it.message}"; Log.w(TAG, "ball add failed", it) }
        else {
            // re-clamp after a rotation
            val lp = ballLp!!; val s = screen()
            lp.x = if (st.ovBallRight) s.x - ballSize - ballMargin else ballMargin
            lp.y = clampBallY(lp.y)
            runCatching { wm.updateViewLayout(v, lp) }
        }
    }

    private fun clampBallY(y: Int): Int = y.coerceIn(dp(32f), (screen().y - ballSize - dp(56f)).coerceAtLeast(dp(32f)))

    private fun snapBall() {
        val v = ball ?: return; val lp = ballLp ?: return
        val s = screen()
        val right = lp.x + ballSize / 2 > s.x / 2
        st.ovBallRight = right; st.ovBallY = lp.y
        val target = if (right) s.x - ballSize - ballMargin else ballMargin
        ballAnim = ValueAnimator.ofInt(lp.x, target).apply {
            duration = 220
            addUpdateListener { a -> lp.x = a.animatedValue as Int; if (v.isAttachedToWindow) runCatching { wm.updateViewLayout(v, lp) } }
            start()
        }
    }

    private fun removeBall() {
        ballAnim?.cancel()
        ball?.let { if (it.isAttachedToWindow) runCatching { wm.removeViewImmediate(it) } }
    }

    // ---------------- the overlay agent window ----------------
    /** Root of the overlay window: focusable only while the user works in it (IME), released on outside touch / back. */
    inner class OverlayRoot(c: Context) : FrameLayout(c) {
        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) { setWinFocusable(false, blur = true); return false }
            if (ev.actionMasked == MotionEvent.ACTION_DOWN) setWinFocusable(true)
            return super.dispatchTouchEvent(ev)
        }
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) setWinFocusable(false, blur = true)
                return true
            }
            return super.dispatchKeyEvent(event)
        }
        override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
            super.onWindowFocusChanged(hasWindowFocus)
            if (hasWindowFocus && inputFocused) showIme()
        }
    }

    private fun setWinFocusable(f: Boolean, blur: Boolean = false) {
        val v = win ?: return; val lp = winLp ?: return
        if (blur && !f) { inputFocused = false; AgentBridge.controller?.panel?.blurInput() }
        if (winFocusable == f) return
        winFocusable = f
        lp.flags = if (f) lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            else lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        if (v.isAttachedToWindow) runCatching { wm.updateViewLayout(v, lp) }
    }

    /** agent.html composer focus (Native.inputFocus). */
    fun onInputFocus(f: Boolean) {
        if (!windowOpen) return
        inputFocused = f
        if (f) {
            setWinFocusable(true)
            // the tap that focused the composer may have landed before the window took focus: (re)ask for the IME
            for (t in longArrayOf(150, 450, 900)) main.postDelayed({ if (inputFocused && windowOpen) showIme() }, t)
        }
    }

    private fun showIme() {
        val pv = AgentBridge.controller?.panel?.view ?: return
        if (pv.parent == null || !winFocusable || !pv.hasWindowFocus()) return
        if (Build.VERSION.SDK_INT >= 30 && pv.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) == true) return
        pv.requestFocus()
        ui.getSystemService(InputMethodManager::class.java)?.showSoftInput(pv, 0)
    }

    private class Grip(c: Context, val left: Boolean) : View(c) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF9AA0AA.toInt(); strokeWidth = 1.6f * c.resources.displayMetrics.density; strokeCap = Paint.Cap.ROUND }
        override fun onDraw(canvas: Canvas) {
            val d = resources.displayMetrics.density
            val s = width.toFloat(); val inset = s * 0.62f
            for (k in 1..3) {
                val o = k * 4.5f * d
                if (!left) canvas.drawLine(inset - o, inset - 2f, inset - 2f, inset - o, p)
                else canvas.drawLine(s - inset + o, inset - 2f, s - inset + 2f, inset - o, p)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun ensureWindow() {
        if (win != null) return
        val sh = dp(8f)
        val root = OverlayRoot(ui).apply { setPadding(sh, sh, sh, sh); clipToPadding = false }
        val card = LinearLayout(ui).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { cornerRadius = dp(14f).toFloat(); setColor(Color.WHITE); setStroke(1, 0xFFDADDE2.toInt()) }
            elevation = dp(8f).toFloat()
            clipToOutline = true
        }
        val bar = FrameLayout(ui).apply { setBackgroundColor(0xFFF6F7F9.toInt()); contentDescription = "拖动浮窗" }
        bar.addView(View(ui).apply { background = GradientDrawable().apply { cornerRadius = dp(3f).toFloat(); setColor(0xFFC4C8CF.toInt()) } },
            FrameLayout.LayoutParams(dp(44f), dp(5f), Gravity.CENTER))
        card.addView(bar, LinearLayout.LayoutParams(-1, dp(26f)))
        val body = FrameLayout(ui).apply { setBackgroundColor(Color.WHITE) }
        card.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(card, FrameLayout.LayoutParams(-1, -1))
        val gs = dp(30f)
        val gripR = Grip(ui, false).apply { contentDescription = "调整悬浮窗大小" }
        val gripL = Grip(ui, true).apply { contentDescription = "调整悬浮窗左下角" }
        root.addView(gripR, FrameLayout.LayoutParams(gs, gs, Gravity.BOTTOM or Gravity.END))
        root.addView(gripL, FrameLayout.LayoutParams(gs, gs, Gravity.BOTTOM or Gravity.START))

        val s = screen()
        val defW = minOf(dp(440f), (s.x * 0.9f).toInt()); val defH = minOf(dp(700f), (s.y * 0.72f).toInt())
        val w = (if (st.ovWinW > 0) st.ovWinW else defW).coerceIn(minW(), s.x)
        val h = (if (st.ovWinH > 0) st.ovWinH else defH).coerceIn(minH(), s.y)
        val lp = WindowManager.LayoutParams(w, h, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (if (st.ovWinX >= 0) st.ovWinX else s.x - w - dp(16f)).coerceIn(0, (s.x - w).coerceAtLeast(0))
            y = (if (st.ovWinY >= 0) st.ovWinY else dp(56f)).coerceIn(0, (s.y - h).coerceAtLeast(0))
            title = "InkAgentOverlayWindow"
            windowAnimations = 0
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        }
        winFocusable = false
        val slop = ViewConfiguration.get(ui).scaledTouchSlop
        // move by the bar
        var rx = 0f; var ry = 0f; var sx = 0; var sy = 0; var sw = 0; var sh2 = 0
        bar.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> { rx = ev.rawX; ry = ev.rawY; sx = lp.x; sy = lp.y }
                MotionEvent.ACTION_MOVE -> {
                    val sc = screen()
                    lp.x = (sx + ev.rawX - rx).toInt().coerceIn(0, (sc.x - lp.width).coerceAtLeast(0))
                    lp.y = (sy + ev.rawY - ry).toInt().coerceIn(0, (sc.y - lp.height).coerceAtLeast(0))
                    runCatching { wm.updateViewLayout(root, lp) }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> saveWin()
            }
            true
        }
        fun resizer(left: Boolean) = View.OnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> { rx = ev.rawX; ry = ev.rawY; sx = lp.x; sw = lp.width; sh2 = lp.height }
                MotionEvent.ACTION_MOVE -> {
                    val sc = screen()
                    val dx = (ev.rawX - rx).toInt(); val dy = (ev.rawY - ry).toInt()
                    if (abs(dx) < slop / 2 && abs(dy) < slop / 2) return@OnTouchListener true
                    if (left) {
                        val nx = (sx + dx).coerceIn(0, sx + sw - minW())
                        lp.width = sw + (sx - nx); lp.x = nx
                    } else lp.width = (sw + dx).coerceIn(minW(), sc.x - lp.x)
                    lp.height = (sh2 + dy).coerceIn(minH(), sc.y - lp.y)
                    runCatching { wm.updateViewLayout(root, lp) }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> saveWin()
            }
            true
        }
        gripR.setOnTouchListener(resizer(false))
        gripL.setOnTouchListener(resizer(true))
        win = root; winLp = lp; content = body
    }

    private fun minW() = dp(300f)
    private fun minH() = dp(320f)
    private fun saveWin() { winLp?.let { st.ovWinX = it.x; st.ovWinY = it.y; st.ovWinW = it.width; st.ovWinH = it.height } }

    fun openWindow() {
        val ctrl = AgentBridge.controller
        if (ctrl == null) { toast("正在打开 InkAgent…"); openApp(); return }
        if (appForeground) return
        ensureWindow()
        val root = win!!
        ctrl.attachPanelToOverlay(content!!)
        windowOpen = true
        root.visibility = View.VISIBLE
        if (root.parent == null) runCatching { wm.addView(root, winLp) }.onFailure { lastError = "window: ${it.message}"; Log.w(TAG, "window add failed", it) }
        refresh()
    }

    /** 最小化: back to the ball (the panel returns to the app's own slot). */
    fun collapseWindow() {
        closeWindow(back = true)
        refresh()
    }

    private fun closeWindow(back: Boolean) {
        val root = win
        setWinFocusable(false, blur = true)
        if (back) AgentBridge.controller?.returnPanelFromOverlay()
        if (root != null && root.parent != null) runCatching { wm.removeViewImmediate(root) }
        windowOpen = false
    }

    // ---------------- attachment image editor over other apps ----------------
    private var editorWin: View? = null
    fun uiContext(): Context = ui

    /** Fullscreen, focusable (text tool IME, back key) overlay window for the attachment editor. */
    fun showEditor(v: View) {
        removeEditor(editorWin ?: v)
        setWinFocusable(false, blur = true)
        val lp = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, overlayType(),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.OPAQUE).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "InkAgentImageEditor"
            windowAnimations = 0
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        }
        editorWin = v
        runCatching { wm.addView(v, lp) }.onFailure { lastError = "editor: ${it.message}"; editorWin = null; Log.w(TAG, "editor add failed", it) }
    }

    fun removeEditor(v: View) {
        if (editorWin === v) editorWin = null
        if (v.parent != null) runCatching { wm.removeViewImmediate(v) }
    }

    // ---------------- 框选截屏 ----------------
    fun startCapture() {
        if (capturing || awaitingConsent || selector != null) return
        if (projection == null) {
            awaitingConsent = true; consentRequests++
            hideForCapture()
            runCatching {
                startActivity(Intent(this, CaptureConsentActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS))
            }.onFailure { awaitingConsent = false; lastError = "consent: ${it.message}"; toast("无法请求截屏授权"); restoreAfterCapture() }
            return
        }
        doCapture()
    }

    private fun handleConsent(code: Int, data: Intent?) {
        awaitingConsent = false
        if (code != Activity.RESULT_OK || data == null) { lastError = "consent denied"; toast("没有获得截屏授权"); restoreAfterCapture(); return }
        try {
            startFg(true) // mediaProjection type must be active before getMediaProjection (Android 14)
            val mpm = getSystemService(MediaProjectionManager::class.java)
            val p = mpm.getMediaProjection(code, data) ?: throw IllegalStateException("no projection")
            p.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { main.post { releaseProjection(); startFg(false) } }
            }, main)
            projection = p
        } catch (e: Exception) {
            lastError = "projection: ${e.message}"; Log.w(TAG, "projection failed", e)
            toast("截屏授权失败：${e.message}"); restoreAfterCapture(); return
        }
        doCapture()
    }

    private fun releaseProjection() {
        runCatching { vd?.release() }; vd = null
        synchronized(imgLock) { runCatching { latest?.close() }; latest = null }
        runCatching { reader?.close() }; reader = null
        val p = projection; projection = null
        runCatching { p?.stop() }
    }

    private fun hideForCapture() {
        win?.visibility = View.INVISIBLE
        ball?.visibility = View.INVISIBLE
    }

    private fun restoreAfterCapture() {
        capturing = false
        if (appForeground) { refresh(); return }
        if (windowOpen) win?.visibility = View.VISIBLE
        refresh()
    }

    private fun doCapture() {
        val p = projection ?: return
        capturing = true
        try {
            val s = screen(); val dpi = resources.displayMetrics.densityDpi
            var r = reader
            if (r == null || r.width != s.x || r.height != s.y) {
                synchronized(imgLock) { runCatching { latest?.close() }; latest = null }
                runCatching { r?.close() }
                r = ImageReader.newInstance(s.x, s.y, PixelFormat.RGBA_8888, 3)
                r.setOnImageAvailableListener({ rd ->
                    synchronized(imgLock) {
                        runCatching { latest?.close() }; latest = null
                        latest = runCatching { rd.acquireLatestImage() }.getOrNull()
                    }
                }, capHandler)
                reader = r
                val v = vd
                if (v == null) vd = p.createVirtualDisplay("InkAgentCapture", s.x, s.y, dpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, null)
                else { v.resize(s.x, s.y, dpi); v.surface = r.surface }
            } else vd?.surface = r.surface
        } catch (e: Exception) {
            lastError = "capture: ${e.message}"; Log.w(TAG, "capture setup failed", e)
            toast("截屏失败：${e.message}"); releaseProjection(); restoreAfterCapture(); return
        }
        synchronized(imgLock) { runCatching { latest?.close() }; latest = null }
        hideForCapture()
        main.postDelayed({ grab(0) }, 450)
    }

    private fun grab(attempt: Int) {
        val bmp = synchronized(imgLock) { latest?.let { runCatching { toBitmap(it) }.getOrNull() } }
        if (bmp == null && attempt < 5) { main.postDelayed({ grab(attempt + 1) }, 300); return }
        runCatching { vd?.surface = null }
        if (bmp == null) { lastError = "no frame"; toast("截屏失败：没有拿到画面"); restoreAfterCapture(); return }
        captures++
        showSelector(bmp)
    }

    private fun toBitmap(img: Image): Bitmap {
        val pl = img.planes[0]
        val ps = pl.pixelStride; val rs = pl.rowStride
        val w = img.width; val h = img.height
        val full = Bitmap.createBitmap(rs / ps, h, Bitmap.Config.ARGB_8888)
        val buf = pl.buffer; buf.rewind()
        full.copyPixelsFromBuffer(buf)
        return if (full.width == w) full else Bitmap.createBitmap(full, 0, 0, w, h).also { full.recycle() }
    }

    private fun showSelector(bmp: Bitmap) {
        val v = CaptureSelectView(ui, bmp) { crop -> onSelectorDone(crop) }
        val lp = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, overlayType(),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "InkAgentCapture"
            windowAnimations = 0
            if (Build.VERSION.SDK_INT >= 30) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                fitInsetsTypes = 0; fitInsetsSides = 0 // cover the whole display, under the system bars
            }
            else if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        v.isFocusableInTouchMode = true
        v.setOnKeyListener { _, code, ev -> if (code == KeyEvent.KEYCODE_BACK && ev.action == KeyEvent.ACTION_UP) { v.finish(false); true } else code == KeyEvent.KEYCODE_BACK }
        selector = v
        runCatching { wm.addView(v, lp); v.requestFocus() }.onFailure {
            lastError = "selector: ${it.message}"; selector = null; bmp.recycle(); restoreAfterCapture()
        }
    }

    private fun onSelectorDone(crop: Bitmap?) {
        val v = selector
        selector = null
        if (v != null && v.parent != null) runCatching { wm.removeViewImmediate(v) }
        if (crop != null) {
            lastCrop = intArrayOf(crop.width, crop.height)
            val bos = ByteArrayOutputStream()
            crop.compress(Bitmap.CompressFormat.PNG, 100, bos)
            crop.recycle()
            val ctrl = AgentBridge.controller
            if (ctrl != null) ctrl.addAttachmentPng(bos.toByteArray()) else toast("InkAgent 未在运行，截图未加入")
        }
        restoreAfterCapture()
    }

    private fun cancelSelector() { selector?.finish(false) }

    // ---------------- debug (TEST_OVERLAY, debuggable builds only) ----------------
    fun debugJson(): JSONObject {
        val o = JSONObject().put("running", true).put("appForeground", appForeground)
        val b = JSONObject()
        val bv = ball
        val bShown = bv != null && bv.isAttachedToWindow && bv.visibility == View.VISIBLE
        b.put("shown", bShown)
        if (bv != null && bv.isAttachedToWindow) {
            val l = IntArray(2); bv.getLocationOnScreen(l)
            b.put("x", l[0] + bv.width / 2).put("y", l[1] + bv.height / 2).put("size", bv.width).put("right", st.ovBallRight)
        }
        o.put("ball", b)
        val w = JSONObject().put("open", windowOpen).put("focusable", winFocusable).put("inputFocused", inputFocused)
        val wv = win
        if (wv != null && wv.isAttachedToWindow) {
            val l = IntArray(2); wv.getLocationOnScreen(l)
            w.put("shown", wv.visibility == View.VISIBLE).put("x", l[0]).put("y", l[1]).put("w", wv.width).put("h", wv.height)
        } else w.put("shown", false)
        o.put("window", w)
        val c = JSONObject().put("shown", selector != null).put("capturing", capturing).put("awaitingConsent", awaitingConsent)
            .put("projection", projection != null).put("consentRequests", consentRequests).put("captures", captures)
            .put("lastCrop", JSONArray().put(lastCrop[0]).put(lastCrop[1]))
        selector?.let { s ->
            c.put("mode", if (s.rectMode) "rect" else "lasso").put("hasSelection", s.hasSelection)
            s.selectionBounds()?.let { r -> c.put("sel", JSONArray().put(r.left.toInt()).put(r.top.toInt()).put(r.right.toInt()).put(r.bottom.toInt())) }
            fun ctr(v: View): JSONArray { val l = IntArray(2); v.getLocationOnScreen(l); return JSONArray().put(l[0] + v.width / 2).put(l[1] + v.height / 2) }
            c.put("modeBtn", ctr(s.modeBtn)).put("cancelBtn", ctr(s.cancelBtn)).put("okBtn", ctr(s.okBtn))
        }
        o.put("capture", c).put("error", lastError)
        val sc = screen(); o.put("screen", JSONArray().put(sc.x).put(sc.y))
        return o
    }

    private fun registerTestHooks() {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                i ?: return
                main.post { runCatching { testOp(i) }.onFailure { Log.w(TAG, "test op failed", it) } }
            }
        }
        ContextCompat.registerReceiver(this, r, IntentFilter(ACTION_TEST), ContextCompat.RECEIVER_EXPORTED)
        testReceiver = r
    }

    private fun testOp(i: Intent) {
        when (i.getStringExtra("op")) {
            "state" -> {
                val ov = debugJson()
                val ctrl = AgentBridge.controller
                if (ctrl == null) Log.i(STATE_TAG, JSONObject().put("overlay", ov).put("controller", false).toString())
                else ctrl.logTestState(STATE_TAG, slim = true)
            }
            "open" -> openWindow()
            "ball" -> collapseWindow()
            "capture" -> startCapture()
            "cancel" -> cancelSelector()
            "confirm" -> selector?.let { if (it.hasSelection) it.finish(true) }
            "drag" -> {
                val s = selector ?: return
                val pts = (i.getStringExtra("pts") ?: return).split(';').mapNotNull { p ->
                    val xy = p.split(','); if (xy.size < 2) null else (xy[0].trim().toFloatOrNull() ?: return@mapNotNull null) to (xy[1].trim().toFloatOrNull() ?: return@mapNotNull null)
                }
                if (pts.size < 2) return
                injectDrag(s.surface, pts, i.getIntExtra("hold", 0).toLong())
            }
        }
    }

    /** Debug: one finger gesture into [v] (screen coordinates), optionally holding still [hold] ms first (long-press). */
    private fun injectDrag(v: View, pts: List<Pair<Float, Float>>, hold: Long) {
        val loc = IntArray(2); v.getLocationOnScreen(loc)
        val props = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER })
        val down = SystemClock.uptimeMillis() + 5
        pts.forEachIndexed { k, (sx, sy) ->
            val t = down + (if (k == 0) 0 else hold) + 16L * k
            val action = when (k) { 0 -> MotionEvent.ACTION_DOWN; pts.lastIndex -> MotionEvent.ACTION_UP; else -> MotionEvent.ACTION_MOVE }
            main.postAtTime({
                val coords = arrayOf(MotionEvent.PointerCoords().apply { x = sx - loc[0]; y = sy - loc[1]; pressure = 1f; size = 0.05f })
                val ev = MotionEvent.obtain(down, t, action, 1, props, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
                try { v.dispatchTouchEvent(ev) } finally { ev.recycle() }
            }, t)
        }
    }

    private fun toast(s: String) = main.post { Toast.makeText(this, s, Toast.LENGTH_SHORT).show() }

    companion object {
        const val TAG = "InkOverlay"
        const val STATE_TAG = "InkAgentOverlay"
        const val ACTION_TEST = "com.inkagent.TEST_OVERLAY"
        private const val CHANNEL = "overlay"
        private const val NOTIF_ID = 4710
        @Volatile var instance: OverlayService? = null
            private set
        /** Set by MainActivity (onStart/onStop). The overlay shows only while InkAgent itself is in the background. */
        @Volatile var appForeground = true
            private set

        fun canDraw(c: Context) = android.provider.Settings.canDrawOverlays(c)
        fun start(c: Context) {
            if (!canDraw(c)) return
            runCatching { ContextCompat.startForegroundService(c, Intent(c, OverlayService::class.java)) }
                .onFailure { Log.w(TAG, "start failed", it) }
        }
        fun stop(c: Context) { c.stopService(Intent(c, OverlayService::class.java)) }
        fun setAppForeground(fg: Boolean) {
            appForeground = fg
            instance?.onAppForegroundChanged(fg)
        }
        fun onConsentResult(code: Int, data: Intent?) {
            val s = instance ?: return
            s.main.post { s.handleConsent(code, data) }
        }
    }
}
