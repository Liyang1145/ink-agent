package com.inkagent

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class LlmConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val systemPrompt: String,
    val temperature: Double,
)

/** Minimal OpenAI-compatible Chat Completions client with SSE streaming. */
object LlmClient {

    /**
     * messages: already-built OpenAI message objects (role/content).
     * onDelta is called with each content chunk; returns the full reply.
     */
    fun chat(cfg: LlmConfig, messages: List<JSONObject>, onDelta: (String) -> Unit): String {
        var base = cfg.baseUrl.trim().trimEnd('/')
        val endpoint = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
        val arr = JSONArray()
        if (cfg.systemPrompt.isNotBlank()) {
            arr.put(JSONObject().put("role", "system").put("content", cfg.systemPrompt))
        }
        messages.forEach { arr.put(it) }
        val body = JSONObject()
            .put("model", cfg.model.trim())
            .put("messages", arr)
            .put("stream", true)
            .put("temperature", cfg.temperature)

        val conn = URL(endpoint).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 30_000
        conn.readTimeout = 180_000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", "text/event-stream")
        if (cfg.apiKey.isNotBlank()) conn.setRequestProperty("Authorization", "Bearer ${cfg.apiKey.trim()}")
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

        val code = conn.responseCode
        if (code !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw RuntimeException("HTTP $code: ${err.take(800)}")
        }
        val sb = StringBuilder()
        val ctype = conn.contentType ?: ""
        BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
            if (!ctype.contains("event-stream")) {
                // Server ignored stream=true: parse a normal JSON response
                val text = reader.readText()
                val content = JSONObject(text).getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").optString("content", "")
                onDelta(content); return content
            }
            while (true) {
                val line = reader.readLine() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]") break
                if (data.isEmpty()) continue
                try {
                    val obj = JSONObject(data)
                    val choices = obj.optJSONArray("choices") ?: continue
                    if (choices.length() == 0) continue
                    val delta = choices.getJSONObject(0).optJSONObject("delta") ?: continue
                    val piece = delta.optString("content", "")
                    if (piece.isNotEmpty() && piece != "null") { sb.append(piece); onDelta(piece) }
                } catch (_: Exception) { }
            }
        }
        conn.disconnect()
        return sb.toString()
    }
}
