package com.inkagent.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Off-screen KaTeX / SVG renderer for the AI layer. Lives behind the page view
 * (fully covered), renders HTML, then captures its pixels with View.draw().
 */
@SuppressLint("SetJavaScriptEnabled")
class Rasterizer(context: Context) {
    val view: WebView
    private val main = Handler(Looper.getMainLooper())
    private var ready = CountDownLatch(1)
    private var pending: ((Int, Int, Float, String) -> Unit)? = null
    private val lock = Object()

    private val appCtx = context.applicationContext
    private val debuggable = context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
    private var dumpN = 0

    init {
        WebView.enableSlowWholeDocumentDraw()
        view = WebView(context)
        view.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        view.setBackgroundColor(Color.TRANSPARENT)
        view.settings.javaScriptEnabled = true
        view.settings.allowFileAccess = true
        // no font boosting / text zoom: CJK text must paint at the size the layout measured
        view.settings.textZoom = 100
        @Suppress("DEPRECATION") run { view.settings.layoutAlgorithm = android.webkit.WebSettings.LayoutAlgorithm.NORMAL }
        view.settings.useWideViewPort = false
        view.settings.loadWithOverviewMode = false
        view.isVerticalScrollBarEnabled = false
        view.addJavascriptInterface(object {
            @JavascriptInterface fun done(w: Int, h: Int, dpr: Float, diag: String) { main.post { pending?.invoke(w, h, dpr, diag) } }
        }, "Raster")
        view.webViewClient = object : WebViewClient() {
            override fun onPageFinished(v: WebView?, url: String?) { ready.countDown() }
        }
        view.loadUrl("file:///android_asset/raster.html")
    }

    /** Bounding box of pixels that are not transparent and not near-white. */
    private fun cropToContent(b: Bitmap): Bitmap {
        val w = b.width; val h = b.height
        val px = IntArray(w * h); b.getPixels(px, 0, w, 0, 0, w, h)
        var l = w; var t = h; var r = -1; var bt = -1
        for (y in 0 until h) { val row = y * w
            for (x in 0 until w) { val p = px[row + x]
                val a = p ushr 24
                if (a < 10) continue
                val rr = (p shr 16) and 0xff; val gg = (p shr 8) and 0xff; val bb = p and 0xff
                if (rr > 245 && gg > 245 && bb > 245) continue
                if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > bt) bt = y
            } }
        if (r < 0) throw IllegalStateException("渲染结果为空（公式可能有语法错误）")
        val pad = 6
        l = (l - pad).coerceAtLeast(0); t = (t - pad).coerceAtLeast(0)
        r = (r + pad).coerceAtMost(w - 1); bt = (bt + pad).coerceAtMost(h - 1)
        return Bitmap.createBitmap(b, l, t, r - l + 1, bt - t + 1)
    }

    /**
     * Result in CSS px (pre-dpr) so callers can map to page units. [notes]: automatic fixes the renderer made
     * (e.g. viewBox widened so nothing is clipped); [warnings]: problems worth a fix (overlapping labels…);
     * [contentFrac]: drawn width ÷ the SVG's own viewBox width (≈1 when the drawing fills its viewBox).
     */
    class Result(val bitmap: Bitmap, val cssWidth: Int, val cssHeight: Int,
                 val notes: List<String> = emptyList(), val warnings: List<String> = emptyList(), val contentFrac: Double = 1.0,
                 /** smallest text height ÷ drawn width (0 = no text) */ val minTextFrac: Double = 0.0,
                 /** marks: [x, y, w, h] of the drawn box in page units */ val box: DoubleArray? = null)

    /** Blocking; must be called off the main thread. */
    fun render(kind: String, source: String, widthCssPx: Int = 0): Result {
        synchronized(lock) {
            if (!ready.await(15, TimeUnit.SECONDS)) throw IllegalStateException("渲染器未就绪")
            val latch = CountDownLatch(1)
            var out: Result? = null
            var err: Throwable? = null
            main.post {
                pending = { w, h, dpr, diagRaw ->
                    pending = null
                    try {
                        val diag = runCatching { JSONObject(diagRaw) }.getOrElse { JSONObject() }
                        if (diag.has("error")) throw IllegalArgumentException(diag.getString("error"))
                        fun list(k: String) = diag.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
                        val vw = view.width.coerceAtLeast(1); val vh = view.height.coerceAtLeast(1)
                        val full = Bitmap.createBitmap(vw, vh, Bitmap.Config.ARGB_8888)
                        val c = Canvas(full)
                        c.translate(-view.scrollX.toFloat(), -view.scrollY.toFloat())
                        view.draw(c)
                        val cropped = cropToContent(full)
                        Log.i("InkRaster", "kind=$kind css=${w}x$h view=${vw}x$vh crop=${cropped.width}x${cropped.height}")
                        if (debuggable) runCatching {
                            val dir = java.io.File(appCtx.getExternalFilesDir(null), "raster").apply { mkdirs() }
                            java.io.File(dir, "%03d_%s.png".format(++dumpN, kind)).outputStream().use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
                            java.io.File(dir, "%03d_%s.txt".format(dumpN, kind)).writeText("css=${w}x$h fit=$dpr density=${appCtx.resources.displayMetrics.density} view=${vw}x$vh crop=${cropped.width}x${cropped.height}\n$source")
                        }
                        out = Result(cropped, w, h, list("notes"), list("warnings"), diag.optDouble("contentFrac", 1.0), diag.optDouble("minTextFrac", 0.0),
                            diag.optJSONArray("box")?.let { a -> DoubleArray(4) { a.getDouble(it) } })
                    } catch (t: Throwable) { Log.e("InkRaster", "capture failed", t); err = t }
                    latch.countDown()
                }
                val dens = appCtx.resources.displayMetrics.density.coerceAtLeast(1f)
                val js = "render(${JSONObject.quote(kind)}, ${JSONObject.quote(source)}, $widthCssPx, ${(view.width / dens).toInt()}, ${(view.height / dens).toInt()})"
                view.evaluateJavascript(js, null)
            }
            if (!latch.await(20, TimeUnit.SECONDS)) { main.post { pending = null }; throw IllegalStateException("渲染超时") }
            err?.let { throw it }
            return out!!
        }
    }
}
