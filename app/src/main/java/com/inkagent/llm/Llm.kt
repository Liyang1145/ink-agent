package com.inkagent.llm

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

// ---------------------------------------------------------------------------
// Conversation vocabulary (ported from dsh packages/llm/llm: Message / ContentBlock / StreamChunk)
// ---------------------------------------------------------------------------

sealed class Block {
    data class Text(val text: String) : Block()
    data class Reasoning(val text: String, val signature: String? = null) : Block()
    data class ToolCall(val id: String, val name: String, val arguments: String) : Block()
    /** An image stored as a PNG file in app storage. */
    data class Image(val path: String, val width: Int, val height: Int) : Block()

    fun toJson(): JSONObject = when (this) {
        is Text -> JSONObject().put("type", "text").put("text", text)
        is Reasoning -> JSONObject().put("type", "reasoning").put("text", text).also { if (signature != null) it.put("signature", signature) }
        is ToolCall -> JSONObject().put("type", "tool-call").put("id", id).put("name", name).put("arguments", arguments)
        is Image -> JSONObject().put("type", "image").put("path", path).put("width", width).put("height", height)
    }

    companion object {
        fun fromJson(o: JSONObject): Block = when (o.getString("type")) {
            "text" -> Text(o.getString("text"))
            "reasoning" -> Reasoning(o.getString("text"), if (o.has("signature")) o.getString("signature") else null)
            "tool-call" -> ToolCall(o.getString("id"), o.getString("name"), o.getString("arguments"))
            "image" -> Image(o.getString("path"), o.optInt("width"), o.optInt("height"))
            else -> Text(o.toString())
        }
        fun listToJson(blocks: List<Block>) = JSONArray().also { a -> blocks.forEach { a.put(it.toJson()) } }
        fun listFromJson(a: JSONArray) = (0 until a.length()).map { fromJson(a.getJSONObject(it)) }
    }
}

enum class Role { SYSTEM, USER, ASSISTANT, TOOL }

data class Message(
    val role: Role,
    val content: List<Block>,
    val toolCallId: String? = null,
    val toolName: String? = null,
    val isError: Boolean = false,
)

data class ToolSchema(val name: String, val description: String, val parameters: JSONObject)

/** Streaming protocol, simplified from dsh StreamChunk. */
sealed class Chunk {
    data class TextDelta(val text: String) : Chunk()
    data class ReasoningDelta(val text: String) : Chunk()
    data class ToolCallStart(val id: String, val name: String) : Chunk()
    data class ToolArgsDelta(val id: String, val delta: String) : Chunk()
}

enum class Finish { STOP, TOOL_CALLS, MAX_TOKENS, OTHER }

data class Generated(val content: List<Block>, val finish: Finish)

// ---------------------------------------------------------------------------
// Provider configuration (dsh: provider route + model + reasoningEffort + maxTokens, no temperature)
// ---------------------------------------------------------------------------

enum class Protocol(val label: String) {
    ANTHROPIC_MESSAGES("Anthropic Messages（DeepSeek 默认）"),
    OPENAI_CHAT("OpenAI Chat Completions"),
}

data class Provider(
    val id: String,
    val name: String,
    val protocol: Protocol,
    val baseUrl: String,
    val apiKey: String,
    val models: List<String>,
) {
    /** Reasoning-effort ids the route understands; first is "provider default". */
    fun efforts(): List<String> = when (protocol) {
        Protocol.ANTHROPIC_MESSAGES -> listOf("off", "low", "high", "max")
        Protocol.OPENAI_CHAT -> listOf("default", "low", "medium", "high")
    }

    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("protocol", protocol.name)
        .put("baseUrl", baseUrl).put("apiKey", apiKey).put("models", JSONArray(models))

    companion object {
        fun fromJson(o: JSONObject) = Provider(
            o.getString("id"), o.getString("name"),
            runCatching { Protocol.valueOf(o.getString("protocol")) }.getOrDefault(Protocol.OPENAI_CHAT),
            o.getString("baseUrl"), o.optString("apiKey"),
            o.optJSONArray("models")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
        )
    }
}

data class CallConfig(
    val provider: Provider,
    val model: String,
    val reasoningEffort: String?,
    val maxTokens: Int?,
)

class LlmException(msg: String) : Exception(msg)

/** Cancellation token shared between the agent and the adapter. */
class CancelSignal {
    @Volatile var cancelled = false
        private set
    @Volatile private var conn: HttpURLConnection? = null
    fun bind(c: HttpURLConnection?) { conn = c }
    fun cancel() { cancelled = true; runCatching { conn?.disconnect() } }
    fun throwIfCancelled() { if (cancelled) throw InterruptedException("cancelled") }
}

interface Adapter {
    fun generate(cfg: CallConfig, system: String, history: List<Message>, tools: List<ToolSchema>,
                 signal: CancelSignal, onChunk: (Chunk) -> Unit): Generated
}

object Llm {
    fun adapter(p: Provider): Adapter = when (p.protocol) {
        Protocol.ANTHROPIC_MESSAGES -> AnthropicMessagesAdapter
        Protocol.OPENAI_CHAT -> OpenAiChatAdapter
    }

    fun imageBase64(path: String): String = Base64.encodeToString(File(path).readBytes(), Base64.NO_WRAP)

    internal fun post(url: String, headers: Map<String, String>, body: JSONObject, signal: CancelSignal): HttpURLConnection {
        signal.throwIfCancelled()
        val conn = URL(url).openConnection() as HttpURLConnection
        signal.bind(conn)
        conn.requestMethod = "POST"
        conn.connectTimeout = 30_000
        conn.readTimeout = 300_000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", "text/event-stream")
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        if (code !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw LlmException("HTTP $code: ${err.take(1200)}")
        }
        return conn
    }

    /** Iterate SSE `data:` payloads (and the preceding `event:` name). */
    internal fun sse(conn: HttpURLConnection, signal: CancelSignal, onEvent: (String?, String) -> Boolean) {
        BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { r ->
            var event: String? = null
            val data = StringBuilder()
            while (true) {
                signal.throwIfCancelled()
                val line = r.readLine() ?: break
                if (line.isEmpty()) {
                    if (data.isNotEmpty()) {
                        if (!onEvent(event, data.toString())) return
                        data.setLength(0)
                    }
                    event = null
                    continue
                }
                when {
                    line.startsWith("event:") -> event = line.substring(6).trim()
                    line.startsWith("data:") -> { if (data.isNotEmpty()) data.append('\n'); data.append(line.substring(5).trimStart()) }
                }
            }
            if (data.isNotEmpty()) onEvent(event, data.toString())
        }
    }

    /** GET {base}/models to fill a provider's model list. */
    fun listModels(p: Provider): List<String> {
        val base = p.baseUrl.trim().trimEnd('/')
        val url = when (p.protocol) {
            Protocol.ANTHROPIC_MESSAGES -> (if (base.endsWith("/v1")) base else "$base/v1") + "/models"
            Protocol.OPENAI_CHAT -> "$base/models"
        }
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 20_000; conn.readTimeout = 30_000
        if (p.protocol == Protocol.ANTHROPIC_MESSAGES) {
            conn.setRequestProperty("x-api-key", p.apiKey); conn.setRequestProperty("anthropic-version", "2023-06-01")
        }
        conn.setRequestProperty("Authorization", "Bearer ${p.apiKey}")
        if (conn.responseCode !in 200..299) throw LlmException("HTTP ${conn.responseCode}")
        val o = JSONObject(conn.inputStream.bufferedReader().readText())
        val a = o.optJSONArray("data") ?: return emptyList()
        return (0 until a.length()).map { a.getJSONObject(it).getString("id") }
    }
}

// ---------------------------------------------------------------------------
// Anthropic Messages adapter — port of dsh llm-deepseek serialize.ts / translate.ts
// ---------------------------------------------------------------------------

object AnthropicMessagesAdapter : Adapter {
    private fun root(base: String): String {
        val b = base.trim().trimEnd('/')
        return if (b.endsWith("/v1")) b else "$b/v1"
    }

    private fun input(blocks: List<Block>): JSONArray {
        val a = JSONArray()
        for (b in blocks) when (b) {
            is Block.Text -> if (b.text.isNotEmpty()) a.put(JSONObject().put("type", "text").put("text", b.text))
            is Block.Image -> {
                a.put(JSONObject().put("type", "text").put("text", "Image ${File(b.path).name}; ${b.width}x${b.height}px."))
                a.put(JSONObject().put("type", "image").put("source", JSONObject()
                    .put("type", "base64").put("media_type", "image/png").put("data", Llm.imageBase64(b.path))))
            }
            else -> {}
        }
        return a
    }

    private fun toolInput(raw: String): JSONObject = try { JSONObject(raw) } catch (_: Exception) { JSONObject() }

    override fun generate(cfg: CallConfig, system: String, history: List<Message>, tools: List<ToolSchema>,
                          signal: CancelSignal, onChunk: (Chunk) -> Unit): Generated {
        val messages = JSONArray()
        var lastRole: String? = null
        var last: JSONArray? = null
        for (m in history) {
            if (m.role == Role.SYSTEM) continue
            val content: JSONArray = when (m.role) {
                Role.ASSISTANT -> JSONArray().also { a ->
                    for (b in m.content) when (b) {
                        is Block.Text -> if (b.text.isNotEmpty()) a.put(JSONObject().put("type", "text").put("text", b.text))
                        is Block.Reasoning -> a.put(JSONObject().put("type", "thinking").put("thinking", b.text)
                            .also { if (b.signature != null) it.put("signature", b.signature) })
                        is Block.ToolCall -> a.put(JSONObject().put("type", "tool_use").put("id", b.id).put("name", b.name).put("input", toolInput(b.arguments)))
                        else -> {}
                    }
                }
                Role.TOOL -> JSONArray().put(JSONObject().put("type", "tool_result").put("tool_use_id", m.toolCallId)
                    .put("content", input(m.content)).also { if (m.isError) it.put("is_error", true) })
                else -> input(m.content)
            }
            if (content.length() == 0 && m.role != Role.TOOL) continue
            val wireRole = if (m.role == Role.ASSISTANT) "assistant" else "user"
            if (wireRole == lastRole && last != null) {
                for (i in 0 until content.length()) last.put(content.get(i))
            } else {
                last = content; lastRole = wireRole
                messages.put(JSONObject().put("role", wireRole).put("content", content))
            }
        }
        // tool_result blocks must lead their user message (dsh serialize.ts reorder)
        for (i in 0 until messages.length()) {
            val msg = messages.getJSONObject(i)
            if (msg.getString("role") != "user") continue
            val c = msg.getJSONArray("content")
            val results = JSONArray(); val rest = JSONArray()
            for (j in 0 until c.length()) { val b = c.getJSONObject(j); if (b.optString("type") == "tool_result") results.put(b) else rest.put(b) }
            for (j in 0 until rest.length()) results.put(rest.get(j))
            msg.put("content", results)
        }

        val effort = cfg.reasoningEffort ?: "high"
        val body = JSONObject()
            .put("model", cfg.model).put("stream", true).put("messages", messages)
            .put("max_tokens", cfg.maxTokens ?: 32_000)
            .put("thinking", JSONObject().put("type", if (effort == "off") "disabled" else "enabled"))
        if (effort != "off") body.put("output_config", JSONObject().put("effort", effort))
        if (system.isNotBlank()) body.put("system", system)
        if (tools.isNotEmpty()) body.put("tools", JSONArray().also { a ->
            tools.forEach { t -> a.put(JSONObject().put("name", t.name).put("description", t.description).put("input_schema", t.parameters)) }
        })

        val conn = Llm.post("${root(cfg.provider.baseUrl)}/messages", mapOf(
            "x-api-key" to cfg.provider.apiKey.trim(),
            "anthropic-version" to "2023-06-01",
        ), body, signal)

        class Open(val type: String, var text: StringBuilder = StringBuilder(), var sig: StringBuilder = StringBuilder(),
                   val id: String = "", val name: String = "", var json: StringBuilder = StringBuilder())
        val blocks = sortedMapOf<Int, Open>()
        var finish = Finish.OTHER
        Llm.sse(conn, signal) { _, data ->
            val ev = try { JSONObject(data) } catch (_: Exception) { return@sse true }
            when (ev.optString("type")) {
                "content_block_start" -> {
                    val idx = ev.getInt("index"); val cb = ev.getJSONObject("content_block")
                    when (cb.optString("type")) {
                        "text" -> blocks[idx] = Open("text").also { it.text.append(cb.optString("text")); if (it.text.isNotEmpty()) onChunk(Chunk.TextDelta(it.text.toString())) }
                        "thinking" -> blocks[idx] = Open("thinking").also { it.text.append(cb.optString("thinking")) }
                        "tool_use" -> {
                            val o = Open("tool_use", id = cb.getString("id"), name = cb.getString("name"))
                            blocks[idx] = o; onChunk(Chunk.ToolCallStart(o.id, o.name))
                        }
                    }
                }
                "content_block_delta" -> {
                    val b = blocks[ev.getInt("index")] ?: return@sse true
                    val d = ev.getJSONObject("delta")
                    when (d.optString("type")) {
                        "text_delta" -> { val t = d.optString("text"); b.text.append(t); onChunk(Chunk.TextDelta(t)) }
                        "thinking_delta" -> { val t = d.optString("thinking"); b.text.append(t); onChunk(Chunk.ReasoningDelta(t)) }
                        "signature_delta" -> b.sig.append(d.optString("signature"))
                        "input_json_delta" -> { val t = d.optString("partial_json"); b.json.append(t); onChunk(Chunk.ToolArgsDelta(b.id, t)) }
                    }
                }
                "message_delta" -> when (ev.optJSONObject("delta")?.optString("stop_reason")) {
                    "end_turn", "stop_sequence" -> finish = Finish.STOP
                    "tool_use" -> finish = Finish.TOOL_CALLS
                    "max_tokens" -> finish = Finish.MAX_TOKENS
                }
                "message_stop" -> return@sse false
                "error" -> throw LlmException(ev.optJSONObject("error")?.optString("message") ?: data)
            }
            true
        }
        conn.disconnect()
        val content = blocks.values.map { b ->
            when (b.type) {
                "thinking" -> Block.Reasoning(b.text.toString(), b.sig.toString().ifEmpty { null })
                "tool_use" -> Block.ToolCall(b.id, b.name, b.json.toString().ifEmpty { "{}" })
                else -> Block.Text(b.text.toString())
            }
        }
        return Generated(content, finish)
    }
}

// ---------------------------------------------------------------------------
// OpenAI Chat Completions adapter (other providers, like dsh llm-pi-ai "openai-completions")
// ---------------------------------------------------------------------------

object OpenAiChatAdapter : Adapter {
    private fun userParts(blocks: List<Block>): Any {
        val hasImage = blocks.any { it is Block.Image }
        if (!hasImage) return blocks.filterIsInstance<Block.Text>().joinToString("\n") { it.text }
        val a = JSONArray()
        for (b in blocks) when (b) {
            is Block.Text -> if (b.text.isNotEmpty()) a.put(JSONObject().put("type", "text").put("text", b.text))
            is Block.Image -> a.put(JSONObject().put("type", "image_url").put("image_url",
                JSONObject().put("url", "data:image/png;base64," + Llm.imageBase64(b.path))))
            else -> {}
        }
        return a
    }

    override fun generate(cfg: CallConfig, system: String, history: List<Message>, tools: List<ToolSchema>,
                          signal: CancelSignal, onChunk: (Chunk) -> Unit): Generated {
        val isDeepSeek = cfg.provider.baseUrl.contains("deepseek")
        val msgs = JSONArray()
        if (system.isNotBlank()) msgs.put(JSONObject().put("role", "system").put("content", system))
        val pendingImages = ArrayList<Block>()
        fun flushImages() {
            if (pendingImages.isEmpty()) return
            val parts = ArrayList<Block>(); parts.add(Block.Text("（以下是上面工具调用返回的图片）")); parts.addAll(pendingImages)
            msgs.put(JSONObject().put("role", "user").put("content", userParts(parts)))
            pendingImages.clear()
        }
        for (m in history) when (m.role) {
            Role.SYSTEM -> {}
            Role.USER -> { flushImages(); msgs.put(JSONObject().put("role", "user").put("content", userParts(m.content))) }
            Role.ASSISTANT -> {
                flushImages()
                val o = JSONObject().put("role", "assistant")
                val text = m.content.filterIsInstance<Block.Text>().joinToString("") { it.text }
                o.put("content", if (text.isEmpty()) JSONObject.NULL else text)
                val calls = m.content.filterIsInstance<Block.ToolCall>()
                if (calls.isNotEmpty()) o.put("tool_calls", JSONArray().also { a ->
                    calls.forEach { c -> a.put(JSONObject().put("id", c.id).put("type", "function")
                        .put("function", JSONObject().put("name", c.name).put("arguments", c.arguments))) }
                })
                if (isDeepSeek) {
                    val r = m.content.filterIsInstance<Block.Reasoning>().joinToString("") { it.text }
                    if (r.isNotEmpty()) o.put("reasoning_content", r)
                }
                msgs.put(o)
            }
            Role.TOOL -> {
                // Chat Completions tool messages are text-only: images ride in a follow-up user message
                val text = m.content.filterIsInstance<Block.Text>().joinToString("\n") { it.text }
                val imgs = m.content.filterIsInstance<Block.Image>()
                msgs.put(JSONObject().put("role", "tool").put("tool_call_id", m.toolCallId)
                    .put("content", text.ifEmpty { if (imgs.isNotEmpty()) "（图片已附在下一条消息）" else "(empty)" }))
                pendingImages.addAll(imgs)
            }
        }
        flushImages()

        val body = JSONObject().put("model", cfg.model).put("stream", true).put("messages", msgs)
        if (cfg.maxTokens != null) body.put("max_tokens", cfg.maxTokens)
        val effort = cfg.reasoningEffort
        if (effort != null && effort != "default") body.put("reasoning_effort", effort)
        if (tools.isNotEmpty()) body.put("tools", JSONArray().also { a ->
            tools.forEach { t -> a.put(JSONObject().put("type", "function").put("function",
                JSONObject().put("name", t.name).put("description", t.description).put("parameters", t.parameters))) }
        })
        val base = cfg.provider.baseUrl.trim().trimEnd('/')
        val url = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
        val conn = Llm.post(url, mapOf("Authorization" to "Bearer ${cfg.provider.apiKey.trim()}"), body, signal)

        val text = StringBuilder(); val reasoning = StringBuilder()
        class Call(var id: String, var name: String, val args: StringBuilder = StringBuilder())
        val calls = sortedMapOf<Int, Call>()
        var finish = Finish.OTHER
        val ctype = conn.contentType ?: ""
        if (!ctype.contains("event-stream")) {
            val o = JSONObject(conn.inputStream.bufferedReader().readText())
            val msg = o.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
            val t = msg.optString("content", ""); if (t.isNotEmpty() && t != "null") { text.append(t); onChunk(Chunk.TextDelta(t)) }
            msg.optJSONArray("tool_calls")?.let { a -> for (i in 0 until a.length()) {
                val c = a.getJSONObject(i); val f = c.getJSONObject("function")
                calls[i] = Call(c.getString("id"), f.getString("name"), StringBuilder(f.optString("arguments", "{}")))
            } }
            finish = if (calls.isNotEmpty()) Finish.TOOL_CALLS else Finish.STOP
        } else Llm.sse(conn, signal) { _, data ->
            if (data == "[DONE]") return@sse false
            val o = try { JSONObject(data) } catch (_: Exception) { return@sse true }
            val choices = o.optJSONArray("choices") ?: return@sse true
            if (choices.length() == 0) return@sse true
            val ch = choices.getJSONObject(0)
            val d = ch.optJSONObject("delta")
            if (d != null) {
                val r = d.optString("reasoning_content", "").ifEmpty { d.optString("reasoning", "") }
                if (r.isNotEmpty() && r != "null") { reasoning.append(r); onChunk(Chunk.ReasoningDelta(r)) }
                val t = d.optString("content", "")
                if (t.isNotEmpty() && t != "null") { text.append(t); onChunk(Chunk.TextDelta(t)) }
                d.optJSONArray("tool_calls")?.let { a -> for (i in 0 until a.length()) {
                    val tc = a.getJSONObject(i); val idx = tc.optInt("index", i)
                    val f = tc.optJSONObject("function")
                    val call = calls.getOrPut(idx) { Call(tc.optString("id", "call_$idx"), f?.optString("name", "") ?: "") }
                    if (tc.has("id") && tc.getString("id").isNotEmpty()) call.id = tc.getString("id")
                    if (f != null && f.optString("name").isNotEmpty() && call.name.isEmpty()) { call.name = f.getString("name") }
                    if (f != null && f.optString("name").isNotEmpty() && call.args.isEmpty()) onChunk(Chunk.ToolCallStart(call.id, call.name))
                    val ad = f?.optString("arguments", "") ?: ""
                    if (ad.isNotEmpty()) { call.args.append(ad); onChunk(Chunk.ToolArgsDelta(call.id, ad)) }
                } }
            }
            when (ch.optString("finish_reason")) {
                "stop" -> finish = Finish.STOP
                "tool_calls" -> finish = Finish.TOOL_CALLS
                "length" -> finish = Finish.MAX_TOKENS
            }
            true
        }
        conn.disconnect()
        val content = ArrayList<Block>()
        if (reasoning.isNotEmpty()) content.add(Block.Reasoning(reasoning.toString()))
        if (text.isNotEmpty()) content.add(Block.Text(text.toString()))
        calls.values.forEach { content.add(Block.ToolCall(it.id, it.name, it.args.toString().ifEmpty { "{}" })) }
        if (calls.isNotEmpty() && finish != Finish.MAX_TOKENS) finish = Finish.TOOL_CALLS
        return Generated(content, finish)
    }
}
