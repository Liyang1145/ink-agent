package com.inkagent.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import com.inkagent.AgentBridge
import java.io.ByteArrayOutputStream

/** Invisible trampoline: system image picker → PNG attachment in the agent tray. */
class PickImageActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        val i = Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        try { @Suppress("DEPRECATION") startActivityForResult(Intent.createChooser(i, "选择图片"), REQ) } catch (e: Exception) { finish() }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ && resultCode == RESULT_OK && data != null) {
            val uris = buildList {
                data.clipData?.let { c -> for (k in 0 until c.itemCount) add(c.getItemAt(k).uri) }
                if (isEmpty()) data.data?.let { add(it) }
            }
            var n = 0
            uris.take(6).forEach { u ->
                runCatching {
                    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, o) }
                    var s = 1; while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= 2048) s *= 2
                    val b = contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) } ?: return@runCatching
                    val k = minOf(1.0, 2048.0 / maxOf(b.width, b.height))
                    val sb = if (k < 1) Bitmap.createScaledBitmap(b, (b.width * k).toInt(), (b.height * k).toInt(), true) else b
                    val bytes = ByteArrayOutputStream().use { sb.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() }
                    AgentBridge.attachPng(bytes); n++
                }
            }
            if (uris.isNotEmpty() && n == 0) Toast.makeText(this, "无法读取所选图片", Toast.LENGTH_SHORT).show()
        }
        finish()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }

    companion object { private const val REQ = 4712 }
}
