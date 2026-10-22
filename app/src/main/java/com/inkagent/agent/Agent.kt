package com.inkagent.agent

import com.inkagent.llm.*
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

// ---------------------------------------------------------------------------
// Tool registry (dsh core/tools: model-facing schema + execute)
// ---------------------------------------------------------------------------

class ToolResult(val content: List<Block>, val isError: Boolean = false) {
    companion object {
        fun text(t: String) = ToolResult(listOf(Block.Text(t)))
        fun error(t: String) = ToolResult(listOf(Block.Text(t)), true)
    }
}

interface Tool {
    val name: String
    val description: String
    val parameters: JSONObject
    fun execute(args: JSONObject, signal: CancelSignal): ToolResult
    fun schema() = ToolSchema(name, description, parameters)
}

class ToolRegistry {
    private val tools = LinkedHashMap<String, Tool>()
    fun register(t: Tool) { tools[t.name] = t }
    fun schemas() = tools.values.map { it.schema() }
    fun execute(name: String, rawArgs: String, signal: CancelSignal): ToolResult {
        val tool = tools[name] ?: return ToolResult.error("未知工具：$name")
        val args = try { if (rawArgs.isBlank()) JSONObject() else JSONObject(rawArgs) }
            catch (e: Exception) { return ToolResult.error("参数不是合法 JSON：${e.message}") }
        return try { tool.execute(args, signal) } catch (e: InterruptedException) { throw e }
            catch (e: Exception) { ToolResult.error("工具执行出错：${e.message ?: e.javaClass.simpleName}") }
    }
}

// ---------------------------------------------------------------------------
// Agent loop (dsh core/agent-loop): inbox → turn → steps → stream → tools → next step
// ---------------------------------------------------------------------------

/** Identified user input, like dsh UserMessage. */
class UserInput(val content: List<Block>, val id: String = "m" + counter.incrementAndGet()) {
    companion object { private val counter = AtomicInteger(0) }
}

interface AgentListener {
    fun onStatus(running: Boolean) {}
    fun onEvent(ev: SessionEvent) {}
    fun onStepStart(turn: Int, step: Int) {}
    fun onChunk(chunk: Chunk) {}
    fun onError(message: String) {}
}

class Agent(
    var session: Session,
    private val tools: ToolRegistry,
    private val callConfig: () -> CallConfig?,
    private val systemPrompt: () -> String,
    private val listener: AgentListener,
) {
    /** dsh Inbox: prompts awaiting their own turn, and steering for the next step. */
    private val nextTurn = ArrayDeque<UserInput>()
    private val nextStep = ArrayDeque<UserInput>()
    private val lock = Object()

    @Volatile var running = false
        private set
    @Volatile private var signal: CancelSignal? = null
    private var maxStepsPerTurn = 40

    /** Queue an ordinary follow-up turn and wake the driver. */
    fun followup(input: UserInput) { synchronized(lock) { nextTurn.addLast(input) }; wake() }

    /** Steer the nearest step: an idle agent starts a turn; a running one consumes it at its next step boundary. */
    fun steer(input: UserInput) { synchronized(lock) { nextStep.addLast(input) }; wake() }

    /** Clear pending input and abort the active turn. */
    fun cancel() {
        synchronized(lock) { nextTurn.clear(); nextStep.clear() }
        signal?.cancel()
    }

    private fun wake() {
        synchronized(lock) { if (running) return; running = true }
        listener.onStatus(true)
        Thread({ drive() }, "agent-driver").start()
    }

    private fun emit(type: String, data: JSONObject) = listener.onEvent(session.append(type, data))

    private fun drive() {
        try {
            while (true) {
                val first: UserInput = synchronized(lock) {
                    nextTurn.removeFirstOrNull() ?: nextStep.removeFirstOrNull()
                } ?: break
                runTurn(first)
            }
        } finally {
            synchronized(lock) { running = false }
            listener.onStatus(false)
            // input that raced the shutdown
            val more = synchronized(lock) { nextTurn.isNotEmpty() || nextStep.isNotEmpty() }
            if (more) wake()
        }
    }

    private fun runTurn(first: UserInput) {
        val sig = CancelSignal(); signal = sig
        val turn = session.nextTurn()
        emit("turn/start", JSONObject().put("turn", turn))
        var claimed = mutableListOf(first)
        var reason = "completed"
        var step = 0
        var errorText: String? = null
        try {
            while (true) {
                step++
                // pre-step: drain steering into this step's batch
                synchronized(lock) { while (nextStep.isNotEmpty()) claimed.add(nextStep.removeFirst()) }
                emit("step/start", JSONObject().put("turn", turn).put("step", step))
                listener.onStepStart(turn, step)
                for (m in claimed) emit("user/message", JSONObject().put("id", m.id).put("steer", step > 1).put("content", Block.listToJson(m.content)))
                claimed = mutableListOf()

                val cfg = callConfig() ?: throw LlmException("还没有配置模型：请在设置里添加服务商并选择模型")
                emit("request/header", JSONObject().put("provider", cfg.provider.name).put("model", cfg.model)
                    .put("effort", cfg.reasoningEffort ?: ""))
                val gen = Llm.adapter(cfg.provider).generate(cfg, systemPrompt(), session.deriveMessages(),
                    tools.schemas(), sig) { listener.onChunk(it) }
                sig.throwIfCancelled()
                emit("assistant/message", JSONObject().put("turn", turn).put("step", step)
                    .put("content", Block.listToJson(gen.content)).put("finish", gen.finish.name))

                val calls = gen.content.filterIsInstance<Block.ToolCall>()
                for (c in calls) {
                    sig.throwIfCancelled()
                    val r = tools.execute(c.name, c.arguments, sig)
                    emit("tool/result", JSONObject().put("turn", turn).put("step", step).put("callId", c.id)
                        .put("name", c.name).put("content", Block.listToJson(r.content)).put("isError", r.isError))
                }
                emit("step/end", JSONObject().put("turn", turn).put("step", step))

                if (gen.finish == Finish.MAX_TOKENS && calls.isEmpty()) { reason = "max-tokens"; break }
                val steering = synchronized(lock) { nextStep.isNotEmpty() }
                if (calls.isEmpty() && !steering) break
                if (step >= maxStepsPerTurn) { reason = "max-steps"; break }
            }
        } catch (e: InterruptedException) {
            reason = "aborted"
        } catch (e: Exception) {
            if (sig.cancelled) reason = "aborted"
            else { reason = "error"; android.util.Log.e("InkAgent", "turn failed", e); errorText = e.message; listener.onError(e.message ?: e.javaClass.simpleName) }
        } finally {
            val endData = JSONObject().put("turn", turn).put("reason", reason)
            if (errorText != null) endData.put("error", errorText)
            emit("turn/end", endData)
            signal = null
        }
    }
}
