package com.inkagent

import android.content.Context
import com.inkagent.llm.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Home-screen study suggestions (Hark-style proactive cards). While the agent is idle, a one-shot
 * model call reads the recent conversations, notes and library additions and proposes a few
 * concrete next steps; each card carries the prompt that starts it as a new conversation.
 * Cached in filesDir/home_suggestions.json; regenerated when stale and something new happened.
 */
object Suggestions {
    data class Card(val id: String, val kind: String, val title: String, val body: String, val prompt: String, val action: String)

    private const val STALE_MS = 3 * 3600_000L
    @Volatile var busy = false; private set
    @Volatile private var lastError = ""

    private fun file(ctx: Context) = File(ctx.filesDir, "home_suggestions.json")

    fun load(ctx: Context): List<Card> {
        val o = runCatching { JSONObject(file(ctx).readText()) }.getOrNull() ?: return starters()
        val dismissed = o.optJSONArray("dismissed")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet()
        val arr = o.optJSONArray("items") ?: return starters()
        val list = (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::parse) }.filter { it.id !in dismissed }
        return list.ifEmpty { if (o.optLong("generated") > 0) emptyList() else starters() }
    }

    fun generatedAt(ctx: Context): Long = runCatching { JSONObject(file(ctx).readText()).optLong("generated") }.getOrDefault(0L)

    fun dismiss(ctx: Context, id: String) {
        val f = file(ctx)
        val o = runCatching { JSONObject(f.readText()) }.getOrNull() ?: JSONObject().put("items", JSONArray(starters().map(::toJson)))
        val d = o.optJSONArray("dismissed") ?: JSONArray().also { o.put("dismissed", it) }
        d.put(id)
        runCatching { f.writeText(o.toString()) }
    }

    /** Regenerate in the background when stale (or [force]) and the agent is idle; [done] runs on the calling thread pool. */
    fun refresh(ctx: Context, force: Boolean, done: (Boolean) -> Unit) {
        val ctrl = AgentBridge.controller
        val cfg = ctrl?.settings?.callConfig()
        if (cfg == null || busy || ctrl.agentBusy()) { done(false); return }
        val f = file(ctx)
        val old = runCatching { JSONObject(f.readText()) }.getOrNull()
        val context = runCatching { ctrl.suggestionContext() }.getOrDefault("")
        val fp = context.hashCode().toString()
        if (!force && old != null) {
            val age = System.currentTimeMillis() - old.optLong("generated")
            if (age < STALE_MS || old.optString("fp") == fp) { done(false); return }
        }
        if (context.isBlank() && !force) { done(false); return }
        busy = true
        Thread({
            var ok = false
            try {
                val ask = "现在是 ${java.text.SimpleDateFormat("yyyy-MM-dd EEEE HH:mm", java.util.Locale.CHINA).format(java.util.Date())}。\n" +
                    "下面是这位同学最近在 InkAgent 里的学习痕迹：\n\n$context\n\n" +
                    "请给出 3 条学习建议卡片。只输出 JSON 数组，不要别的文字。每项字段：\n" +
                    "kind（2-4 字类别，如 复习、练习、查漏、整理、拓展、休息），title（不超过 16 字，具体，带上真实的知识点名），" +
                    "body（不超过 50 字，说清为什么现在做、做什么，基于上面的痕迹，不要空话），" +
                    "action（按钮文字，2-4 字，如 开始、出题、讲一遍、整理），" +
                    "prompt（用户点按钮后以他的口吻发给 AI 助手的话，完整、具体，30-120 字；可以让助手出题写到笔记上、讲解并配动画、整理资料到资料库等）。"
                val sys = "你是一位细心的数学学习教练，熟悉数学分析、高等代数和大学生数学竞赛。你根据学生最近的对话、笔记和资料给出少而准的下一步建议。中文，口吻像朋友提醒，不说教。"
                val c2 = cfg.copy(maxTokens = maxOf(cfg.maxTokens ?: 0, 1500))
                val gen = Llm.adapter(c2.provider).generate(c2, sys, listOf(Message(Role.USER, listOf(Block.Text(ask)))), emptyList(), CancelSignal()) { }
                val text = gen.content.filterIsInstance<Block.Text>().joinToString("") { it.text }
                val start = text.indexOf('['); val end = text.lastIndexOf(']')
                if (start < 0 || end <= start) throw IllegalStateException("建议不是 JSON：" + text.take(200))
                val arr = JSONArray(text.substring(start, end + 1))
                val items = JSONArray()
                val stamp = System.currentTimeMillis().toString(36)
                for (i in 0 until minOf(arr.length(), 4)) {
                    val o = arr.optJSONObject(i) ?: continue
                    if (o.optString("title").isBlank() || o.optString("prompt").isBlank()) continue
                    items.put(o.put("id", "$stamp-$i"))
                }
                if (items.length() == 0) throw IllegalStateException("没有有效建议")
                f.writeText(JSONObject().put("generated", System.currentTimeMillis()).put("fp", fp).put("model", c2.model).put("items", items).toString())
                lastError = ""; ok = true
                AppLog.i("Suggest", "generated ${items.length()} cards with ${c2.model}")
            } catch (e: Exception) {
                lastError = e.message ?: e.javaClass.simpleName
                AppLog.w("Suggest", "generate failed", e)
            } finally {
                busy = false
                done(ok)
            }
        }, "home-suggest").start()
    }

    fun error() = lastError

    private fun parse(o: JSONObject) = Card(
        o.optString("id").ifBlank { o.optString("title") }, o.optString("kind", "建议"), o.optString("title"), o.optString("body"),
        o.optString("prompt"), o.optString("action").ifBlank { "开始" },
    )

    private fun toJson(c: Card) = JSONObject().put("id", c.id).put("kind", c.kind).put("title", c.title).put("body", c.body).put("prompt", c.prompt).put("action", c.action)

    /** Shown before any model is configured or anything has been studied. */
    fun starters() = listOf(
        Card("s-1", "上手", "拍一道题问 AI", "在笔记上写下或拍下一道题，圈出来点「加入 Agent」，它会在页面上一步步写出解答。",
            "我刚开始用 InkAgent。请用三句话告诉我怎么在笔记上圈题问你、怎么让你把解答写到页面上。", "看看"),
        Card("s-2", "练习", "来一套等价无穷小", "求极限最常见的坑是加减里直接替换。先做 4 道题热身，写在笔记上。",
            "给我出 4 道用等价无穷小和泰勒展开求极限的题，由易到难，写到当前笔记上，每题下面留出作答空白。", "出题"),
        Card("s-3", "动画", "看懂一致收敛", "用动画看 f_n(x)=x^n 在 [0,1] 上为什么不一致收敛，比读定义直观得多。",
            "用动画给我讲讲 f_n(x)=x^n 在 [0,1] 上逐点收敛但不一致收敛，配一个可以拖动 n 的滑块。", "讲一遍"),
    )
}
