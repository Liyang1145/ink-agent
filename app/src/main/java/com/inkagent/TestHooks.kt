package com.inkagent

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import androidx.core.content.ContextCompat

/**
 * Debug-only broadcast hooks driven by tests/e2e.py. Registered only in debuggable builds.
 *  - com.inkagent.TEST_INPUT  --es pts "x1,y1;x2,y2;..." [--es tool stylus|finger]
 *    synthesizes one gesture (DOWN, MOVE…, UP; +16 ms per point) into the window's decor view.
 *    Or --es text_b64 <base64> to fill the agent composer.
 *  - com.inkagent.TEST_STATE  logs {panelVisible, sidebarOpen, messageCount} under tag InkAgentState.
 */
class TestHooks(private val activity: Activity, private val agent: () -> AgentController?) {
    private val main = Handler(Looper.getMainLooper())
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_INPUT -> main.post { inject(intent) }
                ACTION_STATE -> main.post {
                    val a = agent()
                    if (a == null) Log.i(STATE_TAG, "{\"error\":\"no agent\"}") else a.logTestState(STATE_TAG)
                }
            }
        }
    }

    fun register() {
        if (activity.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val f = IntentFilter().apply { addAction(ACTION_INPUT); addAction(ACTION_STATE) }
        ContextCompat.registerReceiver(activity, receiver, f, ContextCompat.RECEIVER_EXPORTED)
        registered = true
        Log.i(TAG, "test hooks registered")
    }

    fun unregister() {
        if (registered) runCatching { activity.unregisterReceiver(receiver) }
        registered = false
    }

    private fun inject(intent: Intent) {
        // Optional: --es text_b64 <base64 utf-8> fills the agent composer (for Chinese prompts).
        intent.getStringExtra("text_b64")?.let { b ->
            val t = runCatching { String(android.util.Base64.decode(b, android.util.Base64.DEFAULT), Charsets.UTF_8) }.getOrNull()
            if (t != null) { agent()?.panel?.setComposerForTest(t); Log.i(TAG, "TEST_INPUT text ${t.length} chars") }
        }
        val raw = intent.getStringExtra("pts") ?: return
        val pts = raw.split(';').mapNotNull { p ->
            val xy = p.split(',')
            if (xy.size < 2) null else (xy[0].trim().toFloatOrNull() ?: return@mapNotNull null) to (xy[1].trim().toFloatOrNull() ?: return@mapNotNull null)
        }
        if (pts.isEmpty()) return
        val finger = intent.getStringExtra("tool") == "finger"
        val decor = activity.window.decorView
        val loc = IntArray(2).also { decor.getLocationOnScreen(it) }
        val props = arrayOf(MotionEvent.PointerProperties().apply {
            id = 0
            toolType = if (finger) MotionEvent.TOOL_TYPE_FINGER else MotionEvent.TOOL_TYPE_STYLUS
        })
        val source = if (finger) InputDevice.SOURCE_TOUCHSCREEN else (InputDevice.SOURCE_STYLUS or InputDevice.SOURCE_TOUCHSCREEN)
        val down = SystemClock.uptimeMillis() + 5
        // A single point still makes a gesture: DOWN then UP at the same spot.
        val seq = if (pts.size == 1) listOf(pts[0], pts[0]) else pts
        Log.i(TAG, "TEST_INPUT ${seq.size} pts tool=${if (finger) "finger" else "stylus"} first=${seq.first()} last=${seq.last()} decor@${loc[0]},${loc[1]}")
        seq.forEachIndexed { i, (sx, sy) ->
            val t = down + 16L * i
            val action = when (i) {
                0 -> MotionEvent.ACTION_DOWN
                seq.lastIndex -> MotionEvent.ACTION_UP
                else -> MotionEvent.ACTION_MOVE
            }
            main.postAtTime({
                val coords = arrayOf(MotionEvent.PointerCoords().apply {
                    x = sx - loc[0]; y = sy - loc[1]
                    pressure = if (action == MotionEvent.ACTION_UP) 0f else 0.6f
                    size = 0.05f
                })
                val ev = MotionEvent.obtain(down, t, action, 1, props, coords, 0, 0, 1f, 1f, 0, 0, source, 0)
                try { decor.dispatchTouchEvent(ev) } finally { ev.recycle() }
            }, t)
        }
    }

    companion object {
        const val ACTION_INPUT = "com.inkagent.TEST_INPUT"
        const val ACTION_STATE = "com.inkagent.TEST_STATE"
        const val TAG = "InkAgentTest"
        const val STATE_TAG = "InkAgentState"
    }
}
