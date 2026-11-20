package com.inkagent.library

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.inkagent.LibraryBridge
import org.json.JSONArray
import org.json.JSONObject

/** Full-screen library (assets/library.html): built-in notes/papers plus the user's own folders, tags and imports. */
class LibraryActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var user: UserLibrary
    private var importFolder = ""
    private var seenVersion = -1L
    private var loaded = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.allowFileAccess = true
        web.settings.textZoom = 100
        val lib = LibraryBridge.library ?: Library(applicationContext).also { LibraryBridge.library = it }
        user = LibraryBridge.userLib(this)
        val start = JSONObject().apply {
            intent.getStringExtra("folder")?.let { put("folder", it) }
            intent.getStringExtra("item")?.let { put("item", it) }
            if (intent.getBooleanExtra("import", false)) put("import", true)
        }
        fun ids(j: String): List<String> = runCatching { JSONArray(j).let { a -> List(a.length()) { a.optString(it) } } }.getOrDefault(emptyList())
        web.addJavascriptInterface(object {
            @JavascriptInterface fun catalog(): String = lib.catalog().toString()
            @JavascriptInterface fun user(): String { seenVersion = user.version; return user.catalogJson() }
            @JavascriptInterface fun start(): String = start.toString()
            @JavascriptInterface fun set(id: String): String = lib.setJson(id) ?: "null"
            @JavascriptInterface fun note(id: String): String = lib.noteText(id) ?: ""
            @JavascriptInterface fun text(id: String): String = user.text(id) ?: ""
            @JavascriptInterface fun imageData(id: String): String {
                val f = user.fileOf(id) ?: return ""
                val mime = synchronized(user) { user.items[id]?.mime } ?: "image/png"
                return "data:$mime;base64," + Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
            }
            @JavascriptInterface fun mkdir(name: String, parent: String): String = user.createFolder(name, parent).id
            @JavascriptInterface fun renameFolder(id: String, name: String) = user.renameFolder(id, name)
            @JavascriptInterface fun moveFolder(id: String, parent: String): Boolean = user.moveFolder(id, parent)
            @JavascriptInterface fun deleteFolder(id: String) = user.deleteFolder(id)
            @JavascriptInterface fun rename(id: String, title: String) = user.rename(id, title)
            @JavascriptInterface fun move(idsJson: String, folder: String) { user.move(ids(idsJson), folder) }
            @JavascriptInterface fun tag(idsJson: String, add: String, remove: String) { user.tag(ids(idsJson), ids(add), ids(remove)) }
            @JavascriptInterface fun del(idsJson: String) { user.delete(ids(idsJson)) }
            @JavascriptInterface fun importFiles(folder: String) { runOnUiThread { pickFiles(folder) } }
            @JavascriptInterface fun openFile(id: String) { runOnUiThread { openExternal(id) } }
            @JavascriptInterface fun askItem(id: String) = askItems(JSONArray().put(id).toString())
            @JavascriptInterface fun askItems(idsJson: String) {
                val list = ids(idsJson).mapNotNull { id -> synchronized(user) { user.items[id] }?.let { "《${it.title}》（id $id）" } }
                if (list.isEmpty()) return
                runOnUiThread { LibraryBridge.compose?.invoke("请看资料库里的 ${list.joinToString("、")}，"); finish() }
            }
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
        web.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) { loaded = true }
        }
        setContentView(web)
        web.loadUrl("file:///android_asset/library.html")
    }

    override fun onResume() {
        super.onResume()
        // The agent may have reorganised the library while we were away.
        if (loaded && seenVersion >= 0 && user.version != seenVersion) web.evaluateJavascript("reload()", null)
    }

    private fun pickFiles(folder: String) {
        importFolder = folder
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/pdf", "image/*", "text/*", "application/vnd.openxmlformats-officedocument.*", "application/msword", "*/*"))
        try { @Suppress("DEPRECATION") startActivityForResult(i, REQ_IMPORT) } catch (e: Exception) { Toast.makeText(this, "无法打开文件选择器", Toast.LENGTH_SHORT).show() }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_IMPORT || resultCode != RESULT_OK || data == null) return
        val uris = buildList { data.clipData?.let { c -> for (k in 0 until c.itemCount) add(c.getItemAt(k).uri) }; if (isEmpty()) data.data?.let { add(it) } }
        val folder = importFolder
        Thread {
            var n = 0
            uris.forEach { u -> runCatching { user.importUri(u, folder, emptyList(), "本机导入"); n++ }.onFailure { com.inkagent.AppLog.w("Library", "import failed", it) } }
            runOnUiThread { web.evaluateJavascript("onImported($n, ${JSONObject.quote(folder)})", null) }
        }.start()
    }

    private fun openExternal(id: String) {
        val f = user.fileOf(id) ?: run { Toast.makeText(this, "这项资料没有文件", Toast.LENGTH_SHORT).show(); return }
        val mime = synchronized(user) { user.items[id]?.mime }.orEmpty().ifBlank { "*/*" }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
        if (mime == "application/pdf") {
            // PDFs open in our own editor (as an annotatable note).
            val i = Intent(Intent.ACTION_VIEW).setClassName(this, "com.xnotes.MainActivity").setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            runCatching { startActivity(i) }.onFailure { Toast.makeText(this, "打不开这个 PDF", Toast.LENGTH_SHORT).show() }
            return
        }
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { startActivity(Intent.createChooser(i, "打开方式")) }.onFailure { Toast.makeText(this, "没有能打开它的应用", Toast.LENGTH_SHORT).show() }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        web.evaluateJavascript("back()") { r -> if (r != "true") finish() }
    }

    companion object { private const val REQ_IMPORT = 5101 }
}
