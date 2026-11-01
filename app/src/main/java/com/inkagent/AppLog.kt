package com.inkagent

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The app's own log file. Some ROMs (Honor/MagicOS) hand apps an empty `logcat`, so everything worth
 * knowing when debugging — agent errors, retries, WebView console messages, uncaught crashes — is
 * also appended here (files/applog.txt, rotated at ~1 MB). Exported with the session log and readable
 * by the agent through app_debug.
 */
object AppLog {
    private var file: File? = null
    private var crashFile: File? = null
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private const val MAX = 1_000_000L

    fun init(ctx: Context) {
        if (file != null) return
        file = File(ctx.filesDir, "applog.txt")
        crashFile = File(ctx.filesDir, "crash.txt")
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val sw = java.io.StringWriter(); e.printStackTrace(java.io.PrintWriter(sw))
                val text = "${fmt.format(Date())} thread=${t.name}\n$sw\n"
                crashFile?.appendText(text)
                write("E", "Crash", text)
            }
            prev?.uncaughtException(t, e)
        }
        write("I", "AppLog", "---- start ----")
    }

    @Synchronized
    private fun write(level: String, tag: String, msg: String) {
        val f = file ?: return
        runCatching {
            if (f.length() > MAX) { val old = File(f.parentFile, "applog.1.txt"); old.delete(); f.renameTo(old) }
            f.appendText("${fmt.format(Date())} $level/$tag: $msg\n")
        }
    }

    fun i(tag: String, msg: String) { android.util.Log.i(tag, msg); write("I", tag, msg) }
    fun w(tag: String, msg: String, t: Throwable? = null) { android.util.Log.w(tag, msg, t); write("W", tag, msg + (t?.let { " | $it" } ?: "")) }
    fun e(tag: String, msg: String, t: Throwable? = null) {
        android.util.Log.e(tag, msg, t)
        write("E", tag, msg + (t?.let { "\n" + android.util.Log.getStackTraceString(it).lines().take(12).joinToString("\n") } ?: ""))
    }

    /** Last [n] lines (current + previous rotation), optionally only those containing [filter]. */
    fun tail(n: Int, filter: String? = null): String {
        val f = file ?: return ""
        val lines = ArrayList<String>()
        File(f.parentFile, "applog.1.txt").takeIf { it.isFile }?.let { lines.addAll(it.readLines()) }
        if (f.isFile) lines.addAll(f.readLines())
        val sel = if (filter.isNullOrBlank()) lines else lines.filter { it.contains(filter, ignoreCase = true) }
        return sel.takeLast(n).joinToString("\n")
    }

    fun bytes(): ByteArray {
        val f = file ?: return ByteArray(0)
        val old = File(f.parentFile, "applog.1.txt")
        return (if (old.isFile) old.readBytes() else ByteArray(0)) + (if (f.isFile) f.readBytes() else ByteArray(0))
    }
}
