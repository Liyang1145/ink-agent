package com.inkagent.library

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.inkagent.LibraryBridge

/** Full-screen browser for the built-in library (assets/library.html). */
class LibraryActivity : Activity() {
    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.allowFileAccess = true
        web.settings.textZoom = 100
        val lib = LibraryBridge.library ?: Library(applicationContext).also { LibraryBridge.library = it }
        web.addJavascriptInterface(object {
            @JavascriptInterface fun catalog(): String = lib.catalog().toString()
            @JavascriptInterface fun set(id: String): String = lib.setJson(id) ?: "null"
            @JavascriptInterface fun note(id: String): String = lib.noteText(id) ?: ""
            @JavascriptInterface fun insert(id: String) { runOnUiThread { LibraryBridge.insert?.invoke(id); finish() } }
            @JavascriptInterface fun ask(text: String) { runOnUiThread { LibraryBridge.compose?.invoke(text); finish() } }
            @JavascriptInterface fun close() { runOnUiThread { finish() } }
        }, "Lib")
        web.webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onConsoleMessage(m: android.webkit.ConsoleMessage): Boolean {
                if (m.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.ERROR) com.inkagent.AppLog.w("Library", "${m.message()} @${m.lineNumber()}")
                return true
            }
        }
        setContentView(web)
        web.loadUrl("file:///android_asset/library.html")
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        web.evaluateJavascript("back()") { r -> if (r != "true") finish() }
    }
}
