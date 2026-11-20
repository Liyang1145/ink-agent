package com.inkagent.library

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import com.inkagent.LibraryBridge

/**
 * Share / "open with" target: WeChat, QQ, a file manager or a browser hands a file, several
 * files or a piece of text to InkAgent, and it lands in the library's 收件箱.
 * (Android does not let one app read another's chat history; sharing is the supported way in.)
 */
class ImportActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val lib = LibraryBridge.userLib(this)
        val src = sourceName()
        val i = intent
        val uris = ArrayList<Uri>()
        when (i.action) {
            Intent.ACTION_SEND -> @Suppress("DEPRECATION") (i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))?.let { uris.add(it) }
            Intent.ACTION_SEND_MULTIPLE -> @Suppress("DEPRECATION") i.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.addAll(it) }
            Intent.ACTION_VIEW -> i.data?.let { uris.add(it) }
        }
        if (uris.isEmpty()) i.clipData?.let { c -> for (k in 0 until c.itemCount) c.getItemAt(k).uri?.let { uris.add(it) } }
        val text = i.getStringExtra(Intent.EXTRA_TEXT)
        val subject = i.getStringExtra(Intent.EXTRA_SUBJECT)
        Thread {
            lib.ensure()
            val inbox = lib.resolveFolder("收件箱", create = true) ?: ""
            val tags = listOfNotNull(src.takeIf { it != "其他应用" })
            var n = 0; var lastId: String? = null
            uris.forEach { u ->
                runCatching { lib.importUri(u, inbox, tags, src) }.onSuccess { n++; lastId = it.id }
                    .onFailure { com.inkagent.AppLog.w("Library", "share import failed $u", it) }
            }
            if (uris.isEmpty() && !text.isNullOrBlank()) {
                val url = Regex("https?://\\S+").find(text)?.value.orEmpty()
                val title = subject?.takeIf { it.isNotBlank() } ?: text.lineSequence().first().take(40)
                lastId = lib.addText(title, text, inbox, tags, src, url).id; n++
            }
            runOnUiThread {
                Toast.makeText(this, if (n > 0) "已存入资料库 · 收件箱（$n 项）" else "没有能导入的内容", Toast.LENGTH_SHORT).show()
                if (n > 0) startActivity(Intent(this, LibraryActivity::class.java).putExtra("folder", inbox)
                    .apply { if (n == 1) lastId?.let { putExtra("item", it) } }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                finish()
            }
        }.start()
    }

    private fun sourceName(): String {
        val pkg = runCatching { referrer?.host }.getOrNull() ?: callingPackage ?: ""
        return when {
            pkg.startsWith("com.tencent.mm") -> "微信"
            pkg.startsWith("com.tencent.mobileqq") || pkg.startsWith("com.tencent.tim") -> "QQ"
            pkg.contains("browser") || pkg.contains("chrome") -> "浏览器"
            pkg.isNotEmpty() -> "其他应用"
            else -> "其他应用"
        }
    }
}
