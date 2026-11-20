package com.inkagent.agent

import com.inkagent.llm.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Sub-agents: the main agent can dispatch a self-contained sub-task (an explanation animation, a drawing on
 * another page, a search) to a child agent that runs concurrently with its own session and the same tools
 * (minus dispatching). Progress shows in the parent transcript as a card (subagent/start|tool|anim|end
 * events appended to the parent session); the child's final reply goes back to the main agent — collected by
 * wait_agents if it is waiting, otherwise injected as a steering message (a busy main agent reads it at its
 * next step, an idle one starts a new turn with it).
 */
class SubAgents(
    private val dir: File,
    private val parentTools: ToolRegistry,
    private val callConfig: () -> CallConfig?,
    private val systemPrompt: () -> String,
    private val pricer: (String, Usage) -> Double?,
    private val parent: () -> Agent,
    /** A parent-session event was appended (the UI shows it when that session is the current one). */
    private val onParentEvent: (Session, SessionEvent) -> Unit,
) {
    class Run(val id: String, val title: String, val task: String, val parentSession: Session) {
        @Volatile var done = false
        @Volatile var result = ""
        @Volatile var error: String? = null
        @Volatile var delivered = false
        @Volatile var agent: Agent? = null
        @Volatile var lastText = ""
        var usage = Usage.ZERO
        var cost = 0.0
        var costKnown = true
        var steps = 0
        val started = System.currentTimeMillis()
    }

    private val lock = Object()
    private val runs = LinkedHashMap<String, Run>()
    private val waiting = HashSet<String>()
    private val seq = AtomicInteger(0)
    private val childTools by lazy { parentTools.without(setOf("spawn_agent", "wait_agents", "manage_provider")) }

    fun register(reg: ToolRegistry) { reg.register(SpawnTool()); reg.register(WaitTool()) }

    fun liveIds(): Set<String> = synchronized(lock) { runs.values.filter { !it.done }.map { it.id }.toSet() }
    fun running(): Int = synchronized(lock) { runs.values.count { !it.done } }

    fun cancelAll() { synchronized(lock) { runs.values.filter { !it.done }.mapNotNull { it.agent } }.forEach { it.cancel() } }
    fun cancel(id: String) { synchronized(lock) { runs[id] }?.agent?.cancel() }

    private fun emit(run: Run, type: String, data: JSONObject) {
        val ev = run.parentSession.append(type, data.put("id", run.id))
        onParentEvent(run.parentSession, ev)
    }

    private fun spawn(title: String, task: String): Run {
        val parentSession = parent().session
        val id = "sub-" + seq.incrementAndGet()
        val run = Run(id, title, task, parentSession)
        val session = Session("${parentSession.id}_$id", File(dir, "${parentSession.id}_${id}_${System.currentTimeMillis().toString(36)}.jsonl"))
        val listener = object : AgentListener {
            override fun onEvent(ev: SessionEvent) {
                when (ev.type) {
                    "assistant/message" -> {
                        run.steps++
                        Usage.fromJson(ev.data.optJSONObject("usage"))?.let { u ->
                            run.usage += u
                            if (ev.data.has("cost")) run.cost += ev.data.getDouble("cost") else run.costKnown = false
                        }
                        val content = Block.listFromJson(ev.data.getJSONArray("content"))
                        val text = content.filterIsInstance<Block.Text>().joinToString("") { it.text }.trim()
                        if (text.isNotEmpty()) run.lastText = text
                        for (c in content.filterIsInstance<Block.ToolCall>()) {
                            if (c.name == "show_animation") emit(run, "subagent/anim", JSONObject().put("callId", c.id).put("args", c.arguments))
                            else emit(run, "subagent/tool", JSONObject().put("name", c.name).put("args", c.arguments.take(1500)))
                        }
                    }
                    "turn/end" -> finish(run, ev.data.optString("reason"), ev.data.optString("error").ifEmpty { null })
                }
            }
            override fun onError(message: String) { run.error = message }
        }
        val sys = {
            systemPrompt() + "\n\n" + SUB_ROLE
        }
        val child = Agent(session, childTools, callConfig, sys, listener, pricer)
        run.agent = child
        synchronized(lock) { runs[id] = run }
        emit(run, "subagent/start", JSONObject().put("title", title).put("task", task))
        com.inkagent.AppLog.i("SubAgent", "spawn $id 「$title」: ${task.take(200)}")
        child.followup(UserInput(listOf(Block.Text("【子任务：$title】\n$task"))))
        return run
    }

    private fun finish(run: Run, reason: String, error: String?) {
        if (run.done) return
        run.result = run.lastText
        run.error = when (reason) {
            "completed" -> null
            "aborted" -> "已停止"
            "max-steps" -> null.also { run.result = run.result.ifEmpty { "（达到步数上限，未写总结）" } }
            else -> error ?: run.error ?: reason
        }
        val d = JSONObject().put("title", run.title).put("result", run.result).put("steps", run.steps)
            .put("ms", System.currentTimeMillis() - run.started)
        run.error?.let { d.put("error", it) }
        if (run.steps > 0) { d.put("usage", run.usage.toJson()); if (run.costKnown) d.put("cost", run.cost) }
        emit(run, "subagent/end", d)
        com.inkagent.AppLog.i("SubAgent", "${run.id} end reason=$reason steps=${run.steps} error=${run.error}")
        val inject = synchronized(lock) {
            run.done = true
            lock.notifyAll()
            if (run.id in waiting || reason == "aborted") false else { run.delivered = true; true }
        }
        if (!inject) return
        val p = parent()
        val msg = UserInput(listOf(Block.Text(report(run) + "\n\n（这是系统自动送回的子智能体结果，不是用户的新消息。用户已在对话里看到它的进度卡片" +
            "和它做的动画/画布内容。请接着完成你的任务：需要的话结合这个结果继续讲解或收尾，不要重复它已经做的事。）")), kind = "subagent")
        if (p.session === run.parentSession) p.steer(msg)
        else { // the user switched conversations meanwhile: leave the result in its own conversation for later
            val ev = run.parentSession.append("user/message", JSONObject().put("id", msg.id).put("kind", "subagent")
                .put("content", Block.listToJson(msg.content)))
            onParentEvent(run.parentSession, ev)
        }
    }

    private fun report(r: Run): String {
        val head = "【子智能体 ${r.id}「${r.title}」" + (if (r.error != null) "失败：${r.error}" else "已完成") + "，用了 ${r.steps} 步】"
        return head + if (r.result.isNotBlank()) "\n<结果>\n${r.result}\n</结果>" else "\n（没有文字结果）"
    }

    private inner class SpawnTool : Tool {
        override val name = "spawn_agent"
        override val description = "派出一个子智能体，并发完成一项独立子任务，你不用等它，可以继续做别的（单线推进太慢时用）。适合：专门制作一个讲解动画（show_animation）、在另一页画图或出题、搜索整理资料、验算另一种解法。\n" +
            "子智能体看不到当前对话和图片，task 必须自包含：题目原文/已知条件/要讲清的点/做成什么样/写到哪一页。它能用除派遣以外的全部工具；它的进度卡片和动画用户能直接看到。\n" +
            "完成后结果会自动送回给你（你还在忙就在下一步收到；你已经回复完就会开启新一轮，那时接着讲解或收尾）。必须拿到结果才能继续时用 wait_agents。\n" +
            "注意：一步就能做完的小事自己做，不要派；不要让两个智能体同时往同一页的同一区域写；同时最多 $MAX 个。派出后简短告诉用户你分了哪些工。"
        override val parameters = obj(
            "title" to t("string", "子任务名（几个字，显示在卡片上），如「画旋转体动画」"),
            "task" to t("string", "完整、自包含的任务说明"),
            required = listOf("title", "task"),
        )
        override fun execute(args: JSONObject, signal: CancelSignal): ToolResult {
            val task = args.optString("task").trim()
            if (task.isEmpty()) return ToolResult.error("task 不能为空")
            if (running() >= MAX) return ToolResult.error("已有 $MAX 个子智能体在运行，等它们完成（wait_agents）后再派，或者自己做。")
            val title = args.optString("title").trim().ifEmpty { task.take(10) }.take(24)
            val r = spawn(title, task)
            return ToolResult.text("已派出子智能体 ${r.id}「$title」，正在后台并发运行。它完成后结果会自动送回给你；现在继续做你自己的部分。")
        }
    }

    private inner class WaitTool : Tool {
        override val name = "wait_agents"
        override val description = "等待子智能体完成并取回它们的结果。ids 省略 = 本对话里所有还没交回结果的子智能体。timeout 秒（默认 120，最多 300），超时后还没完成的会在完成时自动送回。"
        override val parameters = obj(
            "ids" to JSONObject().put("type", "array").put("items", JSONObject().put("type", "string")).put("description", "子智能体 id，如 [\"sub-1\"]"),
            "timeout" to t("number", "最多等多少秒"),
        )
        override fun execute(args: JSONObject, signal: CancelSignal): ToolResult {
            val session = parent().session
            val want: List<Run> = synchronized(lock) {
                val ids = args.optJSONArray("ids")?.let { a -> List(a.length()) { a.optString(it) } }
                if (ids != null && ids.isNotEmpty()) ids.mapNotNull { runs[it] }
                else runs.values.filter { it.parentSession === session && !it.delivered }
            }
            if (want.isEmpty()) return ToolResult.text("没有需要等待的子智能体（结果可能已经自动送回过了）。")
            val timeout = (args.optDouble("timeout", 120.0).takeIf { !it.isNaN() } ?: 120.0).coerceIn(5.0, 300.0)
            val until = System.currentTimeMillis() + (timeout * 1000).toLong()
            synchronized(lock) { want.forEach { waiting.add(it.id) } }
            try {
                synchronized(lock) {
                    while (want.any { !it.done }) {
                        val left = until - System.currentTimeMillis()
                        if (left <= 0 || signal.cancelled) break
                        lock.wait(minOf(left, 300))
                    }
                }
            } finally {
                synchronized(lock) { want.forEach { waiting.remove(it.id) } }
            }
            signal.throwIfCancelled()
            val sb = StringBuilder()
            synchronized(lock) {
                for (r in want) {
                    if (r.done) {
                        if (r.delivered) sb.append("【${r.id}「${r.title}」的结果之前已经送回过】\n\n")
                        else { r.delivered = true; sb.append(report(r)).append("\n\n") }
                    } else sb.append("【${r.id}「${r.title}」还在运行，完成后会自动送回】\n\n")
                }
            }
            return ToolResult.text(sb.toString().trim())
        }
    }

    companion object {
        const val MAX = 4
        private const val SUB_ROLE = """【你是子智能体】主智能体把下面这一项子任务交给你独立完成。
- 用户能在对话里看到你的进度卡片，以及你做的动画、写到画布上的内容；但你的文字回复只交给主智能体，不直接给用户看。
- 只做这一项任务，做完就停。不要寒暄、不要向用户提问（没人会回答），信息不够就做合理假设并在结果里说明。
- 你不能再派子智能体。
- 最后一条回复是交给主智能体的总结（300 字以内）：做了什么、产出在哪里（动画标题 / 第几页 / 对象 id）、关键结论、需要主智能体注意或接着讲的地方。"""
    }
}
