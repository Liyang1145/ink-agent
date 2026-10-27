package com.inkagent.agent

import com.inkagent.llm.Block
import com.inkagent.llm.Message
import com.inkagent.llm.Role
import org.json.JSONObject
import java.io.File

/**
 * Append-only session log, ported from dsh core/session: the event log is the single
 * source of truth and the model-facing history is derived from it (deriveMessages()).
 * Persisted as JSONL, one event per line.
 */
class SessionEvent(val seq: Int, val time: Long, val type: String, val data: JSONObject) {
    fun toLine(): String = JSONObject().put("seq", seq).put("time", time).put("type", type).put("data", data).toString()

    companion object {
        fun parse(line: String): SessionEvent? = try {
            val o = JSONObject(line)
            SessionEvent(o.getInt("seq"), o.getLong("time"), o.getString("type"), o.getJSONObject("data"))
        } catch (_: Exception) { null }
    }
}

class Session(val id: String, private val file: File) {
    // appended on the agent thread, read on the UI thread (stats, transcript) → copy-on-write for safe iteration
    val events = java.util.concurrent.CopyOnWriteArrayList<SessionEvent>()
    var title: String = "新对话"
        private set
    var pinned: Boolean = false
        private set
    private var titleSet = false

    init {
        if (file.exists()) file.forEachLine { l -> SessionEvent.parse(l)?.let { events.add(it) } }
        events.filter { it.type == "session/meta" }.forEach { applyMeta(it.data) }
        if (!titleSet) deriveTitle()?.let { title = it }
    }

    private fun applyMeta(d: JSONObject) {
        if (d.has("title")) { title = d.getString("title"); titleSet = true }
        if (d.has("pinned")) pinned = d.getBoolean("pinned")
    }

    fun rename(t: String) { append("session/meta", JSONObject().put("title", t.trim().ifEmpty { "新对话" })) }
    fun setPinned(b: Boolean) { append("session/meta", JSONObject().put("pinned", b)) }

    /** Totals over the whole session: usage, ¥ cost (null if any priced call is unknown → partial), last prompt size. */
    class Stats(val usage: com.inkagent.llm.Usage, val cost: Double, val costKnown: Boolean, val lastInput: Int, val calls: Int, val turns: Int)

    @Synchronized
    fun stats(fromIndex: Int = 0): Stats {
        var u = com.inkagent.llm.Usage.ZERO; var cost = 0.0; var known = true; var last = 0; var calls = 0; var turns = 0
        for (i in fromIndex until events.size) {
            val e = events[i]
            if (e.type == "turn/start") turns++
            if (e.type != "assistant/message") continue
            val uu = com.inkagent.llm.Usage.fromJson(e.data.optJSONObject("usage")) ?: continue
            u += uu; calls++; last = uu.input
            if (e.data.has("cost")) cost += e.data.getDouble("cost") else known = false
        }
        return Stats(u, cost, known, last, calls, turns)
    }

    /** True when the session has any conversation content (not just meta events). */
    val hasContent: Boolean get() = events.any { it.type == "user/message" }

    val lastTime: Long get() = events.lastOrNull { it.type != "session/meta" }?.time ?: file.lastModified()

    @Synchronized
    fun append(type: String, data: JSONObject): SessionEvent {
        val ev = SessionEvent(events.size, System.currentTimeMillis(), type, data)
        events.add(ev)
        file.parentFile?.mkdirs()
        file.appendText(ev.toLine() + "\n")
        if (type == "session/meta") applyMeta(data)
        if (type == "user/message" && !titleSet) deriveTitle()?.let { title = it }
        return ev
    }

    private fun deriveTitle(): String? {
        val first = events.firstOrNull { it.type == "user/message" } ?: return null
        val blocks = Block.listFromJson(first.data.getJSONArray("content"))
        val t = blocks.filterIsInstance<Block.Text>().joinToString(" ") { it.text }.trim()
        return if (t.isNotEmpty()) t.take(24) else if (blocks.any { it is Block.Image }) "图片提问" else null
    }

    /** Next turn number: one more than the last turn/start. */
    fun nextTurn(): Int = (events.lastOrNull { it.type == "turn/start" }?.data?.optInt("turn") ?: 0) + 1

    /**
     * dsh deriveMessages(): user/message → user, assistant/message → assistant,
     * tool/result → tool. Steps aborted mid-stream leave no assistant/message, so
     * unanswered tool calls are repaired with an error result (dsh session/repair).
     */
    @Synchronized
    fun deriveMessages(): List<Message> {
        val out = ArrayList<Message>()
        val pending = LinkedHashMap<String, String>()
        fun repair() {
            for ((id, name) in pending) out.add(Message(Role.TOOL, listOf(Block.Text("工具调用被中断，没有结果。")), id, name, true))
            pending.clear()
        }
        for (e in events) when (e.type) {
            "user/message" -> { repair(); out.add(Message(Role.USER, Block.listFromJson(e.data.getJSONArray("content")))) }
            "assistant/message" -> {
                repair()
                val content = Block.listFromJson(e.data.getJSONArray("content"))
                content.filterIsInstance<Block.ToolCall>().forEach { pending[it.id] = it.name }
                out.add(Message(Role.ASSISTANT, content))
            }
            "tool/result" -> {
                val id = e.data.getString("callId")
                pending.remove(id)
                out.add(Message(Role.TOOL, Block.listFromJson(e.data.getJSONArray("content")), id,
                    e.data.optString("name"), e.data.optBoolean("isError")))
            }
        }
        repair()
        return out
    }
}

/** Global session store (dsh session list): one JSONL file per session. */
class SessionStore(private val dir: File) {
    init { dir.mkdirs() }

    fun list(): List<Session> = (dir.listFiles { f -> f.name.endsWith(".jsonl") } ?: emptyArray())
        .map { Session(it.name.removeSuffix(".jsonl"), it) }
        .sortedWith(compareByDescending<Session> { it.pinned }.thenByDescending { it.lastTime })

    fun open(id: String) = Session(id, File(dir, "$id.jsonl"))

    fun create(): Session {
        val id = "s" + System.currentTimeMillis().toString(36)
        return open(id)
    }

    fun delete(id: String) { File(dir, "$id.jsonl").delete() }
}
