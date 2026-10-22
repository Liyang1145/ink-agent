package com.inkagent.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Handler
import android.os.Looper
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
    private var pending: ((Int, Int, Float) -> Unit)? = null
    private val lock = Object()

    init {
        WebView.enableSlowWholeDocumentDraw()
        view = WebView(context)
        view.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        view.setBackgroundColor(Color.TRANSPARENT)
        view.settings.javaScriptEnabled = true
        view.settings.allowFileAccess = true
        view.isVerticalScrollBarEnabled = false
        view.addJavascriptInterface(object {
            @JavascriptInterface fun done(w: Int, h: Int, dpr: Float) { main.post { pending?.invoke(w, h, dpr) } }
        }, "Raster")
        view.webViewClient = object : WebViewClient() {
            override fun onPageFinished(v: WebView?, url: String?) { ready.countDown() }
        }
        view.loadUrl("file:///android_asset/raster.html")
    }

    /** Result in CSS px (pre-dpr) so callers can map to page units. */
    class Result(val bitmap: Bitmap, val cssWidth: Int, val cssHeight: Int)

    /** Blocking; must be called off the main thread. */
    fun render(kind: String, source: String, widthCssPx: Int = 0): Result {
        synchronized(lock) {
            if (!ready.await(15, TimeUnit.SECONDS)) throw IllegalStateException("渲染器未就绪")
            val latch = CountDownLatch(1)
            var out: Result? = null
            var err: Throwable? = null
            main.post {
                pending = { w, h, dpr ->
                    pending = null
                    try {
                        val pw = (w * dpr).toInt().coerceIn(1, 4096)
                        val ph = (h * dpr).toInt().coerceIn(1, 4096)
                        if (view.width < pw || view.height < ph) {
                            view.layoutParams = view.layoutParams.apply { width = maxOf(view.width, pw); height = maxOf(view.height, ph) }
                            view.measure(View.MeasureSpec.makeMeasureSpec(maxOf(view.width, pw), View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(maxOf(view.height, ph), View.MeasureSpec.EXACTLY))
                            view.layout(view.left, view.top, view.left + maxOf(view.width, pw), view.top + maxOf(view.height, ph))
                        }
                        val bmp = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888)
                        val c = Canvas(bmp)
                        c.translate(-view.scrollX.toFloat(), -view.scrollY.toFloat())
                        view.draw(c)
                        out = Result(bmp, w, h)
                    } catch (t: Throwable) { err = t }
                    latch.countDown()
                }
                val js = "render(${JSONObject.quote(kind)}, ${JSONObject.quote(source)}, $widthCssPx)"
                view.evaluateJavascript(js, null)
            }
            if (!latch.await(20, TimeUnit.SECONDS)) { main.post { pending = null }; throw IllegalStateException("渲染超时") }
            err?.let { throw it }
            return out!!
        }
    }
}
