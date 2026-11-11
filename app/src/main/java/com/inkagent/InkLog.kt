package com.inkagent

import java.util.concurrent.Executors

/**
 * Handwriting diagnostics (v0.6.19): every pen/finger contact, every way ink can leave the page
 * (eraser by toolbar / pen side button / pen tail / finger, scribble-to-erase, undo, agent edits,
 * document swaps) and the front-buffer wet-ink handovers go to AppLog under tag "Ink", written off the
 * main thread so the pen never waits on disk. A watchdog compares the page's item count at every
 * pen-down with the count at the last pen-up and flags any drop no logged removal explains.
 */
object InkLog {
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "inklog").apply { isDaemon = true } }

    /** Bumped by every logged removal path; the watchdog checks it moved when ink went missing. */
    @Volatile var removals = 0L

    fun add(msg: String) {
        val t = android.os.SystemClock.uptimeMillis()
        io.execute { AppLog.i("Ink", "[$t] $msg") }
    }

    fun removed(how: String, n: Int) {
        removals++
        add("REMOVE how=$how n=$n")
    }

    fun tool(t: Int): String = when (t) {
        android.view.MotionEvent.TOOL_TYPE_STYLUS -> "pen"
        android.view.MotionEvent.TOOL_TYPE_FINGER -> "finger"
        android.view.MotionEvent.TOOL_TYPE_ERASER -> "penTail"
        android.view.MotionEvent.TOOL_TYPE_MOUSE -> "mouse"
        else -> "tool$t"
    }
}
